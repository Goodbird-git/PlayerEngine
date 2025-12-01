package com.player2.playerengine.trackers;

import com.player2.playerengine.util.Debug;
import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.events.PlayerCollidedWithEntityEvent;
import com.player2.playerengine.mixins.PersistentProjectileEntityAccessor;
import com.player2.playerengine.trackers.blacklisting.EntityLocateBlacklist;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.baritone.CachedProjectile;
import com.player2.playerengine.util.helpers.BaritoneHelper;
import com.player2.playerengine.util.helpers.EntityHelper;
import com.player2.playerengine.util.helpers.ProjectileHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

public class EntityTracker extends Tracker {
   private final HashMap<Item, List<ItemEntity>> itemDropLocations = new HashMap<>();
   private final HashMap<Class, List<Entity>> entityMap = new HashMap<>();
   private final List<Entity> closeEntities = new ArrayList<>();
   private final List<LivingEntity> hostiles = new ArrayList<>();
   private final List<CachedProjectile> projectiles = new ArrayList<>();
   private final HashMap<String, Player> playerMap = new HashMap<>();
   private final HashMap<String, Vec3> playerLastCoordinates = new HashMap<>();
   private final EntityLocateBlacklist entityBlacklist = new EntityLocateBlacklist();
   private final HashMap<LivingEntity, List<Entity>> entitiesCollidingWithPlayerAccumulator = new HashMap<>();
   private final HashMap<LivingEntity, HashSet<Entity>> entitiesCollidingWithPlayer = new HashMap<>();

   public EntityTracker(TrackerManager manager) {
      super(manager);
      EventBus.subscribe(PlayerCollidedWithEntityEvent.class, evt -> this.registerPlayerCollision(evt.player, evt.other));
   }

   private static Class squashType(Class<?> type) {
      return Player.class.isAssignableFrom(type) ? Player.class : type;
   }

   private void registerPlayerCollision(LivingEntity player, Entity entity) {
      if (!this.entitiesCollidingWithPlayerAccumulator.containsKey(player)) {
         this.entitiesCollidingWithPlayerAccumulator.put(player, new ArrayList<>());
      }

      this.entitiesCollidingWithPlayerAccumulator.get(player).add(entity);
   }

   public boolean isCollidingWithPlayer(LivingEntity player, Entity entity) {
      return this.entitiesCollidingWithPlayer.containsKey(player) && this.entitiesCollidingWithPlayer.get(player).contains(entity);
   }

   public boolean isCollidingWithPlayer(Entity entity) {
      return this.isCollidingWithPlayer(this.mod.getPlayer(), entity);
   }

   public Optional<ItemEntity> getClosestItemDrop(Item... items) {
      return this.getClosestItemDrop(this.mod.getPlayer().position(), items);
   }

   public Optional<ItemEntity> getClosestItemDrop(Vec3 position, Item... items) {
      return this.getClosestItemDrop(position, entity -> true, items);
   }

   public Optional<ItemEntity> getClosestItemDrop(Vec3 position, ItemTarget... items) {
      return this.getClosestItemDrop(position, entity -> true, items);
   }

   public Optional<ItemEntity> getClosestItemDrop(Predicate<ItemEntity> acceptPredicate, Item... items) {
      return this.getClosestItemDrop(this.mod.getPlayer().position(), acceptPredicate, items);
   }

   public Optional<ItemEntity> getClosestItemDrop(Vec3 position, Predicate<ItemEntity> acceptPredicate, Item... items) {
      this.ensureUpdated();
      ItemTarget[] tempTargetList = new ItemTarget[items.length];

      for (int i = 0; i < items.length; i++) {
         tempTargetList[i] = new ItemTarget(items[i], 9999999);
      }

      return this.getClosestItemDrop(position, acceptPredicate, tempTargetList);
   }
   private Optional<ItemEntity> getClosestItemDropWithoutItemTarget(Vec3 position, Predicate<ItemEntity> acceptPredicate) {
    Collection<Item> items = new ArrayList<>(this.itemDropLocations.keySet());

    ItemEntity closestEntity = null;
    float minCost = Float.POSITIVE_INFINITY;

    for (Item item : items) {
        if (this.itemDropped(item)) {
            Collection<ItemEntity> entities = this.itemDropLocations.get(item);
            if (entities == null) continue;
            for (ItemEntity entity : entities) {
                if (!this.entityBlacklist.unreachable(entity)
                        && entity.getItem().getItem().equals(item)
                        && acceptPredicate.test(entity)) {
                    float cost = (float) BaritoneHelper.calculateGenericHeuristic(position, entity.position());
                    if (cost < minCost) {
                        minCost = cost;
                        closestEntity = entity;
                    }
                }
            }
        }
    }

    return Optional.ofNullable(closestEntity);
   }

   public Optional<ItemEntity> getClosestItemDrop(Vec3 position, Predicate<ItemEntity> acceptPredicate, ItemTarget... targets) {
      this.ensureUpdated();
      if (targets.length == 0) {
         //Debug.logError("You asked for the drop position of zero items... Most likely a typo.");
         return this.getClosestItemDropWithoutItemTarget(position, acceptPredicate);
      } else if (!this.itemDropped(targets)) {
         return Optional.empty();
      } else {
         ItemEntity closestEntity = null;
         float minCost = Float.POSITIVE_INFINITY;

         for (ItemTarget target : targets) {
            for (Item item : target.getMatches()) {
               if (this.itemDropped(item)) {
                  for (ItemEntity entity : this.itemDropLocations.get(item)) {
                     if (!this.entityBlacklist.unreachable(entity) && entity.getItem().getItem().equals(item) && acceptPredicate.test(entity)) {
                        float cost = (float)BaritoneHelper.calculateGenericHeuristic(position, entity.position());
                        if (cost < minCost) {
                           minCost = cost;
                           closestEntity = entity;
                        }
                     }
                  }
               }
            }
         }

         return Optional.ofNullable(closestEntity);
      }
   }

   private Class[] parsePossiblyNullEntityTypes(Class... entityTypes) {
      return entityTypes == null ? this.entityMap.keySet().toArray(Class[]::new) : entityTypes;
   }

   public Optional<Entity> getClosestEntity(Class... entityTypes) {
      return this.getClosestEntity(this.mod.getPlayer().position(), entityTypes);
   }

   public Optional<Entity> getClosestEntity(Vec3 position, Class... entityTypes) {
      return this.getClosestEntity(position, entity -> true, entityTypes);
   }

   public Optional<Entity> getClosestEntity(Predicate<Entity> acceptPredicate, Class... entityTypes) {
      return this.getClosestEntity(this.mod.getPlayer().position(), acceptPredicate, entityTypes);
   }

   public Optional<Entity> getClosestEntity(Vec3 position, Predicate<Entity> acceptPredicate, Class... entityTypes) {
      entityTypes = this.parsePossiblyNullEntityTypes(entityTypes);
      Entity closestEntity = null;
      double minCost = Double.POSITIVE_INFINITY;

      for (Class toFind : entityTypes) {
         synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            if (this.entityMap.containsKey(toFind)) {
               for (Entity entity : this.entityMap.get(toFind)) {
                  if (!this.entityBlacklist.unreachable(entity) && entity.isAlive() && acceptPredicate.test(entity)) {
                     double cost = entity.distanceToSqr(position);
                     if (cost < minCost) {
                        minCost = cost;
                        closestEntity = entity;
                     }
                  }
               }
            }
         }
      }

      return Optional.ofNullable(closestEntity);
   }

   public boolean itemDropped(Item... items) {
      this.ensureUpdated();

      for (Item item : items) {
         if (this.itemDropLocations.containsKey(item)) {
            for (ItemEntity entity : this.itemDropLocations.get(item)) {
               if (!this.entityBlacklist.unreachable(entity)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public boolean itemDropped(ItemTarget... targets) {
      this.ensureUpdated();

      for (ItemTarget target : targets) {
         if (this.itemDropped(target.getMatches())) {
            return true;
         }
      }

      return false;
   }

   public List<ItemEntity> getDroppedItems() {
      this.ensureUpdated();
      return this.itemDropLocations.values().stream().reduce(new ArrayList<>(), (result, drops) -> {
         result.addAll(drops);
         return result;
      });
   }

   public boolean entityFound(Predicate<Entity> shouldAccept, Class... types) {
      this.ensureUpdated();
      types = this.parsePossiblyNullEntityTypes(types);

      for (Class type : types) {
         synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            for (Entity entity : this.entityMap.getOrDefault(type, Collections.emptyList())) {
               if (shouldAccept.test(entity)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public boolean entityFound(Class... types) {
      return this.entityFound(check -> true, types);
   }

   public <T extends Entity> List<T> getTrackedEntities(Class<T> type) {
      this.ensureUpdated();
      if (!this.entityFound(type)) {
         return Collections.emptyList();
      } else {
         synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            return (List<T>)this.entityMap.get(type);
         }
      }
   }

   public List<Entity> getCloseEntities() {
      this.ensureUpdated();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return this.closeEntities;
      }
   }

   public List<CachedProjectile> getProjectiles() {
      this.ensureUpdated();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return this.projectiles;
      }
   }

   public List<LivingEntity> getHostiles() {
      this.ensureUpdated();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return this.hostiles;
      }
   }

   public boolean isPlayerLoaded(String name) {
      this.ensureUpdated();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return this.playerMap.containsKey(name);
      }
   }

   public List<String> getAllLoadedPlayerUsernames() {
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return new ArrayList<>(this.playerMap.keySet());
      }
   }

   public Optional<Vec3> getPlayerMostRecentPosition(String name) {
      this.ensureUpdated();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         return Optional.ofNullable(this.playerLastCoordinates.getOrDefault(name, null));
      }
   }

   public Optional<Player> getPlayerEntity(String name) {
      if (this.isPlayerLoaded(name)) {
         synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            return Optional.of(this.playerMap.get(name));
         }
      } else {
         return Optional.empty();
      }
   }

   public void requestEntityUnreachable(Entity entity) {
      this.entityBlacklist.blackListItem(this.mod, entity, 3);
   }

   public boolean isEntityReachable(Entity entity) {
      return !this.entityBlacklist.unreachable(entity);
   }

   @Override
   protected synchronized void updateState() {
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         this.itemDropLocations.clear();
         this.entityMap.clear();
         this.closeEntities.clear();
         this.projectiles.clear();
         this.hostiles.clear();
         this.playerMap.clear();
         if (this.mod.getWorld() != null) {
            this.entitiesCollidingWithPlayer.clear();

            for (Entry<LivingEntity, List<Entity>> collisions : this.entitiesCollidingWithPlayerAccumulator.entrySet()) {
               this.entitiesCollidingWithPlayer.put(collisions.getKey(), new HashSet<>());
               this.entitiesCollidingWithPlayer.get(collisions.getKey()).addAll(collisions.getValue());
            }

            this.entitiesCollidingWithPlayerAccumulator.clear();

            for (Entity entity : this.mod.getWorld().getAllEntities()) {
               Class<?> type = entity.getClass();
               type = squashType(type);
               if (entity != null && entity.isAlive() && (type != LivingEntity.class || !entity.equals(this.mod.getPlayer()))) {
                  if (!this.entityMap.containsKey(type)) {
                     this.entityMap.put(type, new ArrayList<>());
                  }

                  this.entityMap.get(type).add(entity);
                  if (this.mod.getControllerExtras().inRange(entity)) {
                     this.closeEntities.add(entity);
                  }

                  if (entity instanceof ItemEntity ientity) {
                     Item droppedItem = ientity.getItem().getItem();
                     if (ientity.onGround()
                        || ientity.isInWater()
                        || WorldHelper.isSolidBlock(this.mod, ientity.blockPosition().below(2))
                        || WorldHelper.isSolidBlock(this.mod, ientity.blockPosition().below(3))) {
                        if (!this.itemDropLocations.containsKey(droppedItem)) {
                           this.itemDropLocations.put(droppedItem, new ArrayList<>());
                        }

                        this.itemDropLocations.get(droppedItem).add(ientity);
                     }
                  }

                  if (entity instanceof Mob) {
                     if (EntityHelper.isAngryAtPlayer(this.mod, entity)) {
                        boolean closeEnough = entity.closerThan(this.mod.getPlayer(), 26.0);
                        if (closeEnough) {
                           this.hostiles.add((LivingEntity)entity);
                        }
                     }
                  } else if (entity instanceof Projectile projEntity) {
                     if (!this.mod.getBehaviour().shouldAvoidDodgingProjectile(entity)) {
                        CachedProjectile proj = new CachedProjectile();
                        boolean inGround = false;
                        if (entity instanceof AbstractArrow) {
                           inGround = ((PersistentProjectileEntityAccessor)entity).isInGround();
                        }

                        if (!(projEntity instanceof FishingHook)
                           && !(projEntity instanceof ThrownEnderpearl)
                           && !(projEntity instanceof ThrownExperienceBottle)
                           && !inGround) {
                           proj.position = projEntity.position();
                           proj.velocity = projEntity.getDeltaMovement();
                           proj.gravity = ProjectileHelper.hasGravity(projEntity) ? 0.05F : 0.0;
                           proj.projectileType = projEntity.getClass();
                           this.projectiles.add(proj);
                        }
                     }
                  } else if (entity instanceof Player player) {
                     String name = player.getName().getString();
                     this.playerMap.put(name, player);
                     this.playerLastCoordinates.put(name, player.position());
                  }
               }
            }
         }
      }
   }

   @Override
   protected void reset() {
      this.entityBlacklist.clear();
   }
}
