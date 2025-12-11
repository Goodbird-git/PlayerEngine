package com.player2.playerengine.chains;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.control.KillAura;
import com.player2.playerengine.multiversion.item.ItemVer;
import com.player2.playerengine.tasks.construction.ProjectileProtectionWallTask;
import com.player2.playerengine.tasks.entity.KillEntitiesTask;
import com.player2.playerengine.tasks.movement.CustomBaritoneGoalTask;
import com.player2.playerengine.tasks.movement.DodgeProjectilesTask;
import com.player2.playerengine.tasks.movement.RunAwayFromCreepersTask;
import com.player2.playerengine.tasks.movement.RunAwayFromHostilesTask;
import com.player2.playerengine.tasks.speedrun.DragonBreathTracker;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.util.baritone.CachedProjectile;
import com.player2.playerengine.util.helpers.BaritoneHelper;
import com.player2.playerengine.util.helpers.EntityHelper;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.ProjectileHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.slots.PlayerSlot;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.behavior.PathingBehavior;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class MobDefenseChain extends SingleTaskChain {
   private static final double DANGER_KEEP_DISTANCE = 30.0;
   private static final double CREEPER_KEEP_DISTANCE = 10.0;
   private static final double ARROW_KEEP_DISTANCE_HORIZONTAL = 2.0;
   private static final double ARROW_KEEP_DISTANCE_VERTICAL = 10.0;
   private static final double SAFE_KEEP_DISTANCE = 8.0;
   private static final List<Class<? extends Entity>> ignoredMobs = List.of(
      Warden.class,
      WitherBoss.class,
      EnderMan.class,
      Blaze.class,
      WitherSkeleton.class,
      Hoglin.class,
      Zoglin.class,
      PiglinBrute.class,
      Vindicator.class,
      MagmaCube.class
   );
   private static boolean shielding = false;
   private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
   private final KillAura killAura = new KillAura();
   private Entity targetEntity;
   private boolean doingFunkyStuff = false;
   private boolean wasPuttingOutFire = false;
   private CustomBaritoneGoalTask runAwayTask;
   private float prevHealth = 20.0F;
   private boolean needsChangeOnAttack = false;
   private Entity lockedOnEntity = null;
   private float cachedLastPriority;

   public MobDefenseChain(TaskRunner runner) {
      super(runner);
   }

   public static double getCreeperSafety(Vec3 pos, Creeper creeper) {
      double distance = creeper.distanceToSqr(pos);
      float fuse = creeper.getSwelling(1.0F);
      return fuse <= 0.001F ? distance : distance * 0.2;
   }

   private static void startShielding(PlayerEngineController mod) {
      shielding = true;
      ((PathingBehavior)mod.getBaritone().getPathingBehavior()).requestPause();
      mod.getExtraBaritoneSettings().setInteractionPaused(true);
      if (!mod.getPlayer().isBlocking()) {
         ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory()));
         if (ItemVer.isFood(handItem)) {
            for (ItemStack spaceSlot : mod.getItemStorage().getItemStacksPlayerInventory(false)) {
               if (spaceSlot.isEmpty()) {
                  mod.getSlotHandler().clickSlot(PlayerSlot.getEquipSlot(mod.getInventory()), 0, ClickType.QUICK_MOVE);
                  return;
               }
            }

            Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
            garbage.ifPresent(slot -> mod.getSlotHandler().forceEquipItem(StorageHelper.getItemStackInSlot(slot).getItem()));
         }
      }

      mod.getInputControls().hold(Input.SNEAK);
      mod.getInputControls().hold(Input.CLICK_RIGHT);
   }

   private static int getDangerousnessScore(List<LivingEntity> toDealWithList) {
      int numberOfProblematicEntities = toDealWithList.size();

      for (LivingEntity toDealWith : toDealWithList) {
         if (toDealWith instanceof EnderMan || toDealWith instanceof Slime || toDealWith instanceof Blaze) {
            numberOfProblematicEntities++;
         } else if (toDealWith instanceof Drowned && toDealWith.getAllSlots() == Items.TRIDENT) {
            numberOfProblematicEntities += 5;
         }
      }

      return numberOfProblematicEntities;
   }

   @Override
   public float getPriority() {
      if(this.controller.getShouldDefendFromHostiles()){
         return 0.0F;
      }
      this.cachedLastPriority = this.getPriorityInner();
      if (this.getCurrentTask() == null) {
         this.cachedLastPriority = 0.0F;
      }

      this.prevHealth = this.controller.getPlayer().getHealth();
      return this.cachedLastPriority;
   }

   private void stopShielding(PlayerEngineController mod) {
      if (shielding) {
         ItemStack cursor = StorageHelper.getItemStackInCursorSlot(this.controller);
         if (ItemVer.isFood(cursor)) {
            Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
            if (toMoveTo.isPresent()) {
               Slot garbageSlot = toMoveTo.get();
               mod.getSlotHandler().clickSlot(garbageSlot, 0, ClickType.PICKUP);
            }
         }

         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.CLICK_RIGHT);
         mod.getExtraBaritoneSettings().setInteractionPaused(false);
         shielding = false;
      }
   }

   public boolean isShielding() {
      return shielding || this.killAura.isShielding();
   }

   private boolean escapeDragonBreath(PlayerEngineController mod) {
      this.dragonBreathTracker.updateBreath(mod);

      for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(mod.getPlayer())) {
         if (this.dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
            return true;
         }
      }

      return false;
   }

   private float getPriorityInner() {
      if (!PlayerEngineController.inGame()) {
         return Float.NEGATIVE_INFINITY;
      } else {
         PlayerEngineController mod = this.controller;
         if (!mod.getModSettings().isMobDefense()) {
            return Float.NEGATIVE_INFINITY;
         } else if (mod.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            return Float.NEGATIVE_INFINITY;
         } else {
            if (this.needsChangeOnAttack && (mod.getPlayer().getHealth() < this.prevHealth || this.killAura.attackedLastTick)) {
               this.needsChangeOnAttack = false;
            }

            BlockPos fireBlock = this.isInsideFireAndOnFire(mod);
            if (fireBlock != null) {
               this.putOutFire(mod, fireBlock);
               this.wasPuttingOutFire = true;
            } else {
               mod.getBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
               this.wasPuttingOutFire = false;
            }

            Optional<Entity> universallyDangerous = this.getUniversallyDangerousMob(mod);
            if (universallyDangerous.isPresent() && mod.getPlayer().getHealth() <= 10.0F) {
               this.runAwayTask = new RunAwayFromHostilesTask(30.0, true);
               this.runAwayTask.controller = this.controller;
               this.setTask(this.runAwayTask);
               return 70.0F;
            } else {
               this.doingFunkyStuff = false;
               Slot offhandSlot = PlayerSlot.getOffhandSlot(mod.getInventory());
               Item offhandItem = StorageHelper.getItemStackInSlot(offhandSlot).getItem();
               Creeper blowingUp = this.getClosestFusingCreeper(mod);
               if (blowingUp != null && blowingUp.distanceTo(mod.getEntity()) <= 16.0F) {
                  if (mod.getFoodChain().needsToEat() && !(mod.getPlayer().getHealth() < 9.0F)
                     || !hasShield(mod)
                     || mod.getEntityTracker().entityFound(ThrownPotion.class)
                     || !mod.getBaritone().getPathingBehavior().isSafeToCancel()
                     || !(blowingUp.getSwelling(blowingUp.getSwellDir()) > 0.5)) {
                     this.doingFunkyStuff = true;
                     this.runAwayTask = new RunAwayFromCreepersTask(10.0);
                     this.runAwayTask.controller = this.controller;
                     this.setTask(this.runAwayTask);
                     return 50.0F + blowingUp.getSwelling(1.0F) * 50.0F;
                  }

                  LookHelper.lookAt(mod, blowingUp.getEyePosition());
                  ItemStack shieldSlot = StorageHelper.getItemStackInSlot(offhandSlot);
                  if (shieldSlot.getItem() != Items.SHIELD) {
                     mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                  } else {
                     startShielding(mod);
                  }
               }

               synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                  if (mod.getModSettings().isDodgeProjectiles()
                     && hasShield(mod)
                     && mod.getBaritone().getPathingBehavior().isSafeToCancel()
                     && !mod.getEntityTracker().entityFound(ThrownPotion.class)
                     && this.isProjectileClose(mod)) {
                     ItemStack shieldSlot = StorageHelper.getItemStackInSlot(new Slot(mod.getInventory().offHand, 0));
                     if (shieldSlot.getItem() != Items.SHIELD) {
                        mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                     } else {
                        startShielding(mod);
                     }

                     return 60.0F;
                  }

                  if (blowingUp == null && !this.isProjectileClose(mod)) {
                     this.stopShielding(mod);
                  }
               }

               if (!mod.getFoodChain().needsToEat()
                  && !mod.getMLGBucketChain().isFalling(mod)
                  && mod.getMLGBucketChain().doneMLG()
                  && !mod.getMLGBucketChain().isChorusFruiting()) {
                  this.doForceField(mod);
                  if (mod.getPlayer().getHealth() <= 10.0F && !hasShield(mod)) {
                     if (StorageHelper.getNumberOfThrowawayBlocks(mod) > 0
                        && !mod.getFoodChain().needsToEat()
                        && mod.getModSettings().isDodgeProjectiles()
                        && this.isProjectileClose(mod)) {
                        this.doingFunkyStuff = true;
                        this.setTask(new ProjectileProtectionWallTask(mod));
                        return 65.0F;
                     }

                     if (this.isProjectileClose(mod)) {
                        this.runAwayTask = new DodgeProjectilesTask(2.0, 10.0);
                        this.runAwayTask.controller = this.controller;
                        this.setTask(this.runAwayTask);
                        return 65.0F;
                     }
                  }

                  if (!this.isInDanger(mod)
                     || this.escapeDragonBreath(mod)
                     || mod.getFoodChain().isShouldStop()
                     || this.targetEntity != null && !WorldHelper.isSurroundedByHostiles(mod)) {
                     if (mod.getModSettings().shouldDealWithAnnoyingHostiles()) {
                        List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();
                        List<LivingEntity> toDealWithList = new ArrayList<>();
                        synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                           for (LivingEntity hostile : hostiles) {
                              if (hostile != mod.getEntity()) {
                                 boolean isRangedOrPoisonous = hostile instanceof Skeleton
                                    || hostile instanceof Witch
                                    || hostile instanceof Pillager
                                    || hostile instanceof Piglin
                                    || hostile instanceof Stray
                                    || hostile instanceof CaveSpider;
                                 int annoyingRange = 10;
                                 if (isRangedOrPoisonous) {
                                    annoyingRange = 20;
                                    if (!hasShield(mod)) {
                                       annoyingRange = 35;
                                    }
                                 }

                                 if (hostile.closerThan(mod.getPlayer(), annoyingRange) && LookHelper.seesPlayer(hostile, mod.getPlayer(), annoyingRange)) {
                                    boolean isIgnored = false;

                                    for (Class<? extends Entity> ignored : ignoredMobs) {
                                       if (ignored.isInstance(hostile)) {
                                          isIgnored = true;
                                          break;
                                       }
                                    }

                                    if (isIgnored) {
                                       if (mod.getPlayer().getHealth() <= 10.0F) {
                                          toDealWithList.add(hostile);
                                       }
                                    } else {
                                       toDealWithList.add(hostile);
                                    }
                                 }
                              }
                           }
                        }

                        toDealWithList.sort(Comparator.comparingDouble(entity -> mod.getPlayer().distanceTo(entity)));
                        if (!toDealWithList.isEmpty()) {
                           TieredItem bestWeapon = getBestWeapon(mod);
                           int armor = mod.getPlayer().getArmorValue();
                           float damage = bestWeapon == null ? 0.0F : bestWeapon.getTier().getAttackDamageBonus() + 1.0F;
                           int shield = hasShield(mod) && bestWeapon != null ? 3 : 0;
                           int canDealWith = (int)Math.ceil(armor * 3.6 / 20.0 + damage * 0.8 + shield);
                           if (canDealWith < getDangerousnessScore(toDealWithList) && !this.needsChangeOnAttack) {
                              this.runAwayTask = new RunAwayFromHostilesTask(30.0, true);
                              this.runAwayTask.controller = this.controller;
                              this.setTask(this.runAwayTask);
                              return 80.0F;
                           }

                           if (!(this.mainTask instanceof KillEntitiesTask)) {
                              this.needsChangeOnAttack = true;
                           }

                           this.runAwayTask = null;
                           Entity toKill = (Entity)toDealWithList.get(0);
                           this.lockedOnEntity = toKill;
                           this.setTask(new KillEntitiesTask(toKill.getClass()));
                           return 65.0F;
                        }
                     }

                     if (this.runAwayTask != null && !this.runAwayTask.isFinished()) {
                        this.setTask(this.runAwayTask);
                        return this.cachedLastPriority;
                     } else {
                        this.runAwayTask = null;
                        if (this.needsChangeOnAttack && this.lockedOnEntity != null && this.lockedOnEntity.isAlive()) {
                           this.setTask(new KillEntitiesTask(this.lockedOnEntity.getClass()));
                           return 65.0F;
                        } else {
                           this.needsChangeOnAttack = false;
                           this.lockedOnEntity = null;
                           return 0.0F;
                        }
                     }
                  } else {
                     this.runAwayTask = new RunAwayFromHostilesTask(30.0, true);
                     this.runAwayTask.controller = this.controller;
                     this.setTask(this.runAwayTask);
                     return 70.0F;
                  }
               } else {
                  this.killAura.stopShielding(mod);
                  this.stopShielding(mod);
                  return Float.NEGATIVE_INFINITY;
               }
            }
         }
      }
   }

   private static boolean hasShield(PlayerEngineController mod) {
      return mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(mod, Items.SHIELD);
   }

   public static TieredItem getBestWeapon(PlayerEngineController mod) {
      Item[] WEAPONS = new Item[]{
         Items.NETHERITE_SWORD,
         Items.NETHERITE_AXE,
         Items.DIAMOND_SWORD,
         Items.DIAMOND_AXE,
         Items.IRON_SWORD,
         Items.IRON_AXE,
         Items.GOLDEN_SWORD,
         Items.GOLDEN_AXE,
         Items.STONE_SWORD,
         Items.STONE_AXE,
         Items.WOODEN_SWORD,
         Items.WOODEN_AXE
      };
      TieredItem bestSword = null;

      for (Item item : WEAPONS) {
         if (mod.getItemStorage().hasItem(item)) {
            bestSword = (TieredItem)item;
            break;
         }
      }

      return bestSword;
   }

   private BlockPos isInsideFireAndOnFire(PlayerEngineController mod) {
      boolean onFire = mod.getPlayer().isOnFire();
      if (!onFire) {
         return null;
      } else {
         BlockPos p = mod.getPlayer().blockPosition();
         BlockPos[] toCheck = new BlockPos[]{
            p,
            p.offset(1, 0, 0),
            p.offset(1, 0, -1),
            p.offset(0, 0, -1),
            p.offset(-1, 0, -1),
            p.offset(-1, 0, 0),
            p.offset(-1, 0, 1),
            p.offset(0, 0, 1),
            p.offset(1, 0, 1)
         };

         for (BlockPos check : toCheck) {
            Block b = mod.getWorld().getBlockState(check).getBlock();
            if (b instanceof BaseFireBlock) {
               return check;
            }
         }

         return null;
      }
   }

   private void putOutFire(PlayerEngineController mod, BlockPos pos) {
      Optional<Rotation> reach = LookHelper.getReach(mod, pos);
      if (reach.isPresent()) {
         IBaritone b = mod.getBaritone();
         if (LookHelper.isLookingAt(mod, pos)) {
            ((PathingBehavior)b.getPathingBehavior()).requestPause();
            b.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            return;
         }

         LookHelper.lookAt(this.controller, reach.get());
      }
   }

   private void doForceField(PlayerEngineController mod) {
      this.killAura.tickStart();
      List<Entity> entities = mod.getEntityTracker().getCloseEntities();

      try {
         for (Entity entity : entities) {
            if (entity != mod.getPlayer()) {
               boolean shouldForce = false;
               if (!mod.getBehaviour().shouldExcludeFromForcefield(entity)) {
                  if (entity instanceof Mob) {
                     if (EntityHelper.isProbablyHostileToPlayer(mod, entity) && LookHelper.seesPlayer(entity, mod.getPlayer(), 10.0)) {
                        shouldForce = true;
                     }
                  } else if (entity instanceof LargeFireball) {
                     shouldForce = true;
                  }

                  if (shouldForce) {
                     this.killAura.applyAura(entity);
                  }
               }
            }
         }
      } catch (Exception var6) {
         var6.printStackTrace();
      }

      this.killAura.tickEnd(mod);
   }

   private Creeper getClosestFusingCreeper(PlayerEngineController mod) {
      double worstSafety = Double.POSITIVE_INFINITY;
      Creeper target = null;

      try {
         for (Creeper creeper : mod.getEntityTracker().getTrackedEntities(Creeper.class)) {
            if (creeper != null && !(creeper.getSwelling(1.0F) < 0.04)) {
               double safety = getCreeperSafety(mod.getPlayer().position(), creeper);
               if (safety < worstSafety) {
                  target = creeper;
               }
            }
         }

         return target;
      } catch (ArrayIndexOutOfBoundsException | NullPointerException | ConcurrentModificationException var10) {
         Debug.logWarning("Weird Exception caught and ignored while scanning for creepers: " + var10.getMessage());
         return target;
      }
   }

   private boolean isProjectileClose(PlayerEngineController mod) {
      List<CachedProjectile> projectiles = mod.getEntityTracker().getProjectiles();

      try {
         for (CachedProjectile projectile : projectiles) {
            if (projectile.position.distanceToSqr(mod.getPlayer().position()) < 150.0) {
               boolean isGhastBall = projectile.projectileType == LargeFireball.class;
               if (isGhastBall) {
                  Optional<Entity> ghastBall = mod.getEntityTracker().getClosestEntity(LargeFireball.class);
                  Optional<Entity> ghast = mod.getEntityTracker().getClosestEntity(Ghast.class);
                  if (ghastBall.isPresent() && ghast.isPresent() && this.runAwayTask == null && mod.getBaritone().getPathingBehavior().isSafeToCancel()) {
                     ((PathingBehavior)mod.getBaritone().getPathingBehavior()).requestPause();
                     LookHelper.lookAt(mod, ghast.get().getEyePosition());
                  }

                  return false;
               }

               if (projectile.projectileType != DragonFireball.class) {
                  if (projectile.projectileType == Arrow.class
                     || projectile.projectileType == SpectralArrow.class
                     || projectile.projectileType == SmallFireball.class) {
                     LivingEntity clientPlayerEntity = mod.getPlayer();
                     if (clientPlayerEntity.distanceToSqr(projectile.position) < clientPlayerEntity.distanceToSqr(projectile.position.add(projectile.velocity))
                        )
                      {
                        continue;
                     }
                  }

                  Vec3 expectedHit = ProjectileHelper.calculateArrowClosestApproach(projectile, mod.getPlayer().position());
                  Vec3 delta = mod.getPlayer().position().subtract(expectedHit);
                  double horizontalDistanceSq = delta.x * delta.x + delta.z * delta.z;
                  double verticalDistance = Math.abs(delta.y);
                  if (horizontalDistanceSq < 4.0 && verticalDistance < 10.0) {
                     if (mod.getBaritone().getPathingBehavior().isSafeToCancel() && hasShield(mod)) {
                        ((PathingBehavior)mod.getBaritone().getPathingBehavior()).requestPause();
                        LookHelper.lookAt(mod, projectile.position.add(0.0, 0.3, 0.0));
                     }

                     return true;
                  }
               }
            }
         }
      } catch (ConcurrentModificationException var12) {
         Debug.logWarning(var12.getMessage());
      }

      for (Skeleton skeleton : mod.getEntityTracker().getTrackedEntities(Skeleton.class)) {
         if (!(skeleton.distanceTo(mod.getPlayer()) > 10.0F) && skeleton.hasLineOfSight(mod.getPlayer()) && skeleton.getTicksUsingItem() > 15) {
            return true;
         }
      }

      return false;
   }

   private Optional<Entity> getUniversallyDangerousMob(PlayerEngineController mod) {
      Class<?>[] dangerousMobs = new Class[]{
         Warden.class, WitherBoss.class, WitherSkeleton.class, Hoglin.class, Zoglin.class, PiglinBrute.class, Vindicator.class
      };
      double range = 6.0;

      for (Class<?> dangerous : dangerousMobs) {
         Optional<Entity> entity = mod.getEntityTracker().getClosestEntity(dangerous);
         if (entity.isPresent() && entity.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, entity.get())) {
            return entity;
         }
      }

      return Optional.empty();
   }

   private boolean isInDanger(PlayerEngineController mod) {
      boolean witchNearby = mod.getEntityTracker().entityFound(Witch.class);
      double safeKeepDistance = 8.0;
      float health = mod.getPlayer().getHealth();
      if (health <= 10.0F && witchNearby) {
         safeKeepDistance = 30.0;
      }

      if (mod.getPlayer().hasEffect(MobEffects.WITHER) || mod.getPlayer().hasEffect(MobEffects.POISON) && witchNearby) {
         safeKeepDistance = 30.0;
      }

      if (WorldHelper.isVulnerable(mod.getPlayer())) {
         try {
            LivingEntity player = mod.getPlayer();
            List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();
            synchronized (BaritoneHelper.MINECRAFT_LOCK) {
               for (Entity entity : hostiles) {
                  if (entity.closerThan(player, safeKeepDistance)
                     && !mod.getBehaviour().shouldExcludeFromForcefield(entity)
                     && EntityHelper.isAngryAtPlayer(mod, entity)
                     && entity != mod.getPlayer()) {
                     return true;
                  }
               }
            }
         } catch (Exception var13) {
            Debug.logWarning("Weird multithread exception. Will fix later. " + var13.getMessage());
         }
      }

      return false;
   }

   public void setTargetEntity(Entity entity) {
      this.targetEntity = entity;
   }

   public void resetTargetEntity() {
      this.targetEntity = null;
   }

   public void setForceFieldRange(double range) {
      this.killAura.setRange(range);
   }

   public void resetForceField() {
      this.killAura.setRange(Double.POSITIVE_INFINITY);
   }

   public boolean isDoingAcrobatics() {
      return this.doingFunkyStuff;
   }

   public boolean isPuttingOutFire() {
      return this.wasPuttingOutFire;
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   protected void onTaskFinish(PlayerEngineController mod) {
   }

   @Override
   public String getName() {
      return "Mob Defense";
   }
}
