package com.player2.playerengine.control;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.chains.MobDefenseChain;
import com.player2.playerengine.mixins.LivingEntityMixin;
import com.player2.playerengine.multiversion.item.ItemVer;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StlHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.slots.PlayerSlot;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.behavior.PathingBehavior;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ThrownSplashPotion;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class KillAura {
   private final List<Entity> targets = new ArrayList<>();
   boolean shielding = false;
   private double forceFieldRange = Double.POSITIVE_INFINITY;
   private Entity forceHit = null;
   public boolean attackedLastTick = false;

   public static void equipWeapon(PlayerEngineController mod) {
      Item toolItem = MobDefenseChain.getBestWeapon(mod);
      if (toolItem != null) {
         mod.getSlotHandler().forceEquipItem(toolItem);
      }
   }

   public void tickStart() {
      this.targets.clear();
      this.forceHit = null;
      this.attackedLastTick = false;
   }

   public void applyAura(Entity entity) {
      this.targets.add(entity);
      if (entity instanceof LargeFireball) {
         this.forceHit = entity;
      }
   }

   public void setRange(double range) {
      this.forceFieldRange = range;
   }

   public void tickEnd(PlayerEngineController mod) {
      Optional<Entity> entities = this.targets.stream().min(StlHelper.compareValues(entity -> entity.distanceToSqr(mod.getPlayer())));
      if (entities.isPresent()
         && !mod.getEntityTracker().entityFound(AbstractThrownPotion.class)
         && (
            Double.isInfinite(this.forceFieldRange)
               || entities.get().distanceToSqr(mod.getPlayer()) < this.forceFieldRange * this.forceFieldRange
               || entities.get().distanceToSqr(mod.getPlayer()) < 40.0
         )
         && !mod.getMLGBucketChain().isFalling(mod)
         && mod.getMLGBucketChain().doneMLG()
         && !mod.getMLGBucketChain().isChorusFruiting()) {
         Slot offhandSlot = PlayerSlot.getOffhandSlot(mod.getInventory());
         Item offhandItem = StorageHelper.getItemStackInSlot(offhandSlot).getItem();
         if (entities.get().getClass() != Creeper.class
            && entities.get().getClass() != Hoglin.class
            && entities.get().getClass() != Zoglin.class
            && entities.get().getClass() != Warden.class
            && entities.get().getClass() != WitherBoss.class
            && (mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(mod, Items.SHIELD))
            && mod.getBaritone().getPathingBehavior().isSafeToCancel()) {
            LookHelper.lookAt(mod, entities.get().getEyePosition());
            ItemStack shieldSlot = StorageHelper.getItemStackInSlot(PlayerSlot.getOffhandSlot(mod.getInventory()));
            if (shieldSlot.getItem() != Items.SHIELD) {
               mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
            } else if (!WorldHelper.isSurroundedByHostiles(mod)) {
               this.startShielding(mod);
            }
         }

         this.performDelayedAttack(mod);
      } else {
         this.stopShielding(mod);
      }

      switch (mod.getModSettings().getForceFieldStrategy()) {
         case FASTEST:
            this.performFastestAttack(mod);
            break;
         case DELAY:
            this.performDelayedAttack(mod);
            break;
         case SMART:
            if (this.forceHit != null) {
               this.attack(mod, this.forceHit, true);
            } else if (!mod.getFoodChain().needsToEat()
               && !mod.getMLGBucketChain().isFalling(mod)
               && mod.getMLGBucketChain().doneMLG()
               && !mod.getMLGBucketChain().isChorusFruiting()) {
               this.performDelayedAttack(mod);
            }
      }
   }

   private void performDelayedAttack(PlayerEngineController mod) {
      if (!mod.getFoodChain().needsToEat()
         && !mod.getMLGBucketChain().isFalling(mod)
         && mod.getMLGBucketChain().doneMLG()
         && !mod.getMLGBucketChain().isChorusFruiting()) {
         if (this.forceHit != null) {
            this.attack(mod, this.forceHit, true);
         }

         if (this.targets.isEmpty()) {
            return;
         }

         Optional<Entity> toHit = this.targets.stream().min(StlHelper.compareValues(entity -> entity.distanceToSqr(mod.getPlayer())));
         if (mod.getPlayer() == null || this.getAttackCooldownProgress(mod.getPlayer(), 0.0F) < 1.0F) {
            return;
         }

         toHit.ifPresent(entity -> this.attack(mod, entity, true));
      }
   }

   public float getAttackCooldownProgressPerTick(LivingEntity entity) {
      return 5.0F;
   }

   public float getAttackCooldownProgress(LivingEntity entity, float baseTime) {
      return Mth.clamp((((LivingEntityMixin)entity).getLastAttackedTicks() + baseTime) / this.getAttackCooldownProgressPerTick(entity), 0.0F, 1.0F);
   }

   private void performFastestAttack(PlayerEngineController mod) {
      if (!mod.getFoodChain().needsToEat()
         && !mod.getMLGBucketChain().isFalling(mod)
         && mod.getMLGBucketChain().doneMLG()
         && !mod.getMLGBucketChain().isChorusFruiting()) {
         for (Entity entity : this.targets) {
            this.attack(mod, entity);
         }
      }
   }

   private void attack(PlayerEngineController mod, Entity entity) {
      this.attack(mod, entity, false);
   }

   private void attack(PlayerEngineController mod, Entity entity, boolean equipWeapon) {
      if (entity != null) {
         if (!(entity instanceof LargeFireball)) {
            double xAim = entity.getX();
            double yAim = entity.getY() + entity.getBbHeight() / 1.4;
            double zAim = entity.getZ();
            LookHelper.lookAt(mod, new Vec3(xAim, yAim, zAim));
         }

         if (Double.isInfinite(this.forceFieldRange)
            || entity.distanceToSqr(mod.getPlayer()) < this.forceFieldRange * this.forceFieldRange
            || entity.distanceToSqr(mod.getPlayer()) < 40.0) {
            if (entity instanceof LargeFireball) {
               mod.getControllerExtras().attack(entity);
            }

            boolean canAttack;
            if (equipWeapon) {
               equipWeapon(mod);
               canAttack = true;
            } else {
               canAttack = mod.getSlotHandler().forceDeequipHitTool();
            }

            if (canAttack && (mod.getPlayer().onGround() || mod.getPlayer().getDeltaMovement().y() < 0.0 || mod.getPlayer().isInWater())) {
               this.attackedLastTick = true;
               mod.getControllerExtras().attack(entity);
            }
         }
      }
   }

   public void startShielding(PlayerEngineController mod) {
      this.shielding = true;
      ((PathingBehavior)mod.getBaritone().getPathingBehavior()).requestPause();
      mod.getExtraBaritoneSettings().setInteractionPaused(true);
      if (!mod.getPlayer().isBlocking()) {
         ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory()));
         if (ItemVer.isFood(handItem)) {
            List<ItemStack> spaceSlots = mod.getItemStorage().getItemStacksPlayerInventory(false);
            if (!spaceSlots.isEmpty()) {
               for (ItemStack spaceSlot : spaceSlots) {
                  if (spaceSlot.isEmpty()) {
                     mod.getSlotHandler().clickSlot(PlayerSlot.getEquipSlot(mod.getInventory()), 0, ClickType.QUICK_MOVE);
                     return;
                  }
               }
            }

            Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
            garbage.ifPresent(slot -> mod.getSlotHandler().forceEquipItem(StorageHelper.getItemStackInSlot(slot).getItem()));
         }
      }

      mod.getInputControls().hold(Input.SNEAK);
      mod.getInputControls().hold(Input.CLICK_RIGHT);
   }

   public void stopShielding(PlayerEngineController mod) {
      if (this.shielding) {
         ItemStack cursor = StorageHelper.getItemStackInCursorSlot(mod);
         if (ItemVer.isFood(cursor)) {
            Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
            if (toMoveTo.isPresent()) {
               Slot garbageSlot = toMoveTo.get();
               mod.getSlotHandler().clickSlot(garbageSlot, 0, ClickType.PICKUP);
            }
         }

         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.CLICK_RIGHT);
         mod.getInputControls().release(Input.JUMP);
         mod.getExtraBaritoneSettings().setInteractionPaused(false);
         this.shielding = false;
      }
   }

   public boolean isShielding() {
      return this.shielding;
   }

   public static enum Strategy {
      OFF,
      FASTEST,
      DELAY,
      SMART;
   }
}
