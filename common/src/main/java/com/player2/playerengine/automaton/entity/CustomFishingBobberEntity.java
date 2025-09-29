/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.entity;

import com.player2.playerengine.PlayerEngine;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class CustomFishingBobberEntity extends ThrowableProjectile {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final RandomSource velocityRandom = RandomSource.create();
   private boolean caughtFish;
   private int outOfOpenWaterTicks;
   private static final int MAX_TIME_OUT_OF_WATER = 10;
   private static final EntityDataAccessor<Integer> HOOK_ENTITY_ID = SynchedEntityData.defineId(CustomFishingBobberEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> CAUGHT_FISH = SynchedEntityData.defineId(CustomFishingBobberEntity.class, EntityDataSerializers.BOOLEAN);
   private int removalTimer;
   private int hookCountdown;
   private int waitCountdown;
   private int fishTravelCountdown;
   private float fishAngle;
   private boolean inOpenWater = true;
   @Nullable
   private Entity hookedEntity;
   private CustomFishingBobberEntity.State state = CustomFishingBobberEntity.State.FLYING;
   private final int luckOfTheSeaLevel;
   private final int lureLevel;

   public CustomFishingBobberEntity(EntityType<? extends CustomFishingBobberEntity> type, Level world, int luckOfTheSeaLevel, int lureLevel) {
      super(type, world);
      this.luckOfTheSeaLevel = Math.max(0, luckOfTheSeaLevel);
      this.lureLevel = Math.max(0, lureLevel);
   }

   public CustomFishingBobberEntity(EntityType<? extends CustomFishingBobberEntity> entityType, Level world) {
      this(entityType, world, 0, 0);
   }

   public CustomFishingBobberEntity(LivingEntity thrower, Level world, int luckOfTheSeaLevel, int lureLevel) {
      this(PlayerEngine.FISHING_BOBBER, world, luckOfTheSeaLevel, lureLevel);
      this.setOwner(thrower);
      float f = thrower.getXRot();
      float g = thrower.getYRot();
      float h = Mth.cos(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
      float i = Mth.sin(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
      float j = -Mth.cos(-f * (float) (Math.PI / 180.0));
      float k = Mth.sin(-f * (float) (Math.PI / 180.0));
      double d = thrower.getX() - i * 0.3;
      double e = thrower.getEyeY();
      double l = thrower.getZ() - h * 0.3;
      this.moveOrInterpolateTo(new Vec3(d, e, l), g, f);
      Vec3 vec3d = new Vec3(-i, Mth.clamp(-(k / j), -5.0F, 5.0F), -h);
      double m = vec3d.length();
      vec3d = vec3d.multiply(
         0.6 / m + this.random.triangle(0.5, 0.0103365), 0.6 / m + this.random.triangle(0.5, 0.0103365), 0.6 / m + this.random.triangle(0.5, 0.0103365)
      );
      this.setDeltaMovement(vec3d);
      this.setYRot((float)(Mth.atan2(vec3d.x, vec3d.z) * 180.0F / (float)Math.PI));
      this.setXRot((float)(Mth.atan2(vec3d.y, vec3d.horizontalDistance()) * 180.0F / (float)Math.PI));
      this.yRotO = this.getYRot();
      this.xRotO = this.getXRot();
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      builder.define(HOOK_ENTITY_ID, 0);
      builder.define(CAUGHT_FISH, false);
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
      if (HOOK_ENTITY_ID.equals(data)) {
         int i = (Integer)this.getEntityData().get(HOOK_ENTITY_ID);
         this.hookedEntity = i > 0 ? this.level().getEntity(i - 1) : null;
      }

      if (CAUGHT_FISH.equals(data)) {
         this.caughtFish = (Boolean)this.getEntityData().get(CAUGHT_FISH);
         if (this.caughtFish) {
            this.setDeltaMovement(this.getDeltaMovement().x, -0.4F * Mth.nextFloat(this.velocityRandom, 0.6F, 1.0F), this.getDeltaMovement().z);
         }
      }

      super.onSyncedDataUpdated(data);
   }

   public boolean shouldRenderAtSqrDistance(double distance) {
      double d = 64.0;
      return distance < 4096.0;
   }

   public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
   }

   public void tick() {
      this.velocityRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level().getGameTime());
      baseTick();
      LivingEntity playerEntity = this.getPlayerOwner();
      if (playerEntity == null) {
         this.discard();
      } else if (this.level().isClientSide || !this.removeIfInvalid(playerEntity)) {
         if (this.onGround()) {
            this.removalTimer++;
            if (this.removalTimer >= 1200) {
               this.discard();
               return;
            }
         } else {
            this.removalTimer = 0;
         }

         float f = 0.0F;
         BlockPos blockPos = this.blockPosition();
         FluidState fluidState = this.level().getFluidState(blockPos);
         if (fluidState.is(FluidTags.WATER)) {
            f = fluidState.getHeight(this.level(), blockPos);
         }

         boolean bl = f > 0.0F;
         if (this.state == CustomFishingBobberEntity.State.FLYING) {
            if (this.hookedEntity != null) {
               this.setDeltaMovement(Vec3.ZERO);
               this.state = CustomFishingBobberEntity.State.HOOKED_IN_ENTITY;
               return;
            }

            if (bl) {
               this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 0.2, 0.3));
               this.state = CustomFishingBobberEntity.State.BOBBING;
               return;
            }

            this.checkForCollision();
         } else {
            if (this.state == CustomFishingBobberEntity.State.HOOKED_IN_ENTITY) {
               if (this.hookedEntity != null) {
                  if (!this.hookedEntity.isRemoved() && this.hookedEntity.level().dimension() == this.level().dimension()) {
                     this.setPos(this.hookedEntity.getX(), this.hookedEntity.getY(0.8), this.hookedEntity.getZ());
                  } else {
                     this.updateHookedEntityId((Entity)null);
                     this.state = CustomFishingBobberEntity.State.FLYING;
                  }
               }

               return;
            }

            if (this.state == CustomFishingBobberEntity.State.BOBBING) {
               Vec3 vec3d = this.getDeltaMovement();
               double d = this.getY() + vec3d.y - blockPos.getY() - f;
               if (Math.abs(d) < 0.01) {
                  d += Math.signum(d) * 0.1;
               }

               this.setDeltaMovement(vec3d.x * 0.9, vec3d.y - d * this.random.nextFloat() * 0.2, vec3d.z * 0.9);
               if (this.hookCountdown <= 0 && this.fishTravelCountdown <= 0) {
                  this.inOpenWater = true;
               } else {
                  this.inOpenWater = this.inOpenWater && this.outOfOpenWaterTicks < 10 && this.isOpenOrWaterAround(blockPos);
               }

               if (bl) {
                  this.outOfOpenWaterTicks = Math.max(0, this.outOfOpenWaterTicks - 1);
                  if (this.caughtFish) {
                     this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.1 * this.velocityRandom.nextFloat() * this.velocityRandom.nextFloat(), 0.0));
                  }

                  if (!this.level().isClientSide) {
                     this.tickFishingLogic(blockPos);
                  }
               } else {
                  this.outOfOpenWaterTicks = Math.min(10, this.outOfOpenWaterTicks + 1);
               }
            }
         }

         if (!fluidState.is(FluidTags.WATER)) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.03, 0.0));
         }

         this.move(MoverType.SELF, this.getDeltaMovement());
         this.updateRotation();
         if (this.state == CustomFishingBobberEntity.State.FLYING && (this.onGround() || this.horizontalCollision)) {
            this.setDeltaMovement(Vec3.ZERO);
         }

         double e = 0.92;
         this.setDeltaMovement(this.getDeltaMovement().scale(0.92));
         this.reapplyPosition();
      }
   }

   private boolean removeIfInvalid(LivingEntity player) {
      ItemStack itemStack = player.getMainHandItem();
      ItemStack itemStack2 = player.getOffhandItem();
      boolean bl = itemStack.is(Items.FISHING_ROD);
      boolean bl2 = itemStack2.is(Items.FISHING_ROD);
      if (!player.isRemoved() && player.isAlive() && (bl || bl2) && !(this.distanceToSqr(player) > 1024.0)) {
         return false;
      } else {
         this.discard();
         return true;
      }
   }

   private void checkForCollision() {
      HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
      this.onHit(hitResult);
   }

   protected boolean canHitEntity(Entity entity) {
      return super.canHitEntity(entity) || entity.isAlive() && entity instanceof ItemEntity;
   }

   protected void onHitEntity(EntityHitResult entityHitResult) {
      super.onHitEntity(entityHitResult);
      if (!this.level().isClientSide) {
         this.updateHookedEntityId(entityHitResult.getEntity());
      }
   }

   protected void onHitBlock(BlockHitResult blockHitResult) {
      super.onHitBlock(blockHitResult);
      this.setDeltaMovement(this.getDeltaMovement().normalize().scale(blockHitResult.distanceTo(this)));
   }

   private void updateHookedEntityId(@Nullable Entity entity) {
      this.hookedEntity = entity;
      this.getEntityData().set(HOOK_ENTITY_ID, entity == null ? 0 : entity.getId() + 1);
   }

   private void tickFishingLogic(BlockPos pos) {
      ServerLevel serverWorld = (ServerLevel)this.level();
      int i = 1;
      BlockPos blockPos = pos.above();
      if (this.random.nextFloat() < 0.25F && this.level().isRainingAt(blockPos)) {
         i++;
      }

      if (this.random.nextFloat() < 0.5F && !this.level().canSeeSky(blockPos)) {
         i--;
      }

      if (this.hookCountdown > 0) {
         this.hookCountdown--;
         if (this.hookCountdown <= 0) {
            this.waitCountdown = 0;
            this.fishTravelCountdown = 0;
            this.getEntityData().set(CAUGHT_FISH, false);
         }
      } else if (this.fishTravelCountdown > 0) {
         this.fishTravelCountdown -= i;
         if (this.fishTravelCountdown > 0) {
            this.fishAngle = this.fishAngle + (float)this.random.triangle(0.0, 9.188);
            float f = this.fishAngle * (float) (Math.PI / 180.0);
            float g = Mth.sin(f);
            float h = Mth.cos(f);
            double d = this.getX() + g * this.fishTravelCountdown * 0.1F;
            double e = Mth.floor(this.getY()) + 1.0F;
            double j = this.getZ() + h * this.fishTravelCountdown * 0.1F;
            BlockState blockState = serverWorld.getBlockState(BlockPos.containing(d, e - 1.0, j));
            if (blockState.is(Blocks.WATER)) {
               if (this.random.nextFloat() < 0.15F) {
                  serverWorld.sendParticles(ParticleTypes.BUBBLE, d, e - 0.1F, j, 1, g, 0.1, h, 0.0);
               }

               float k = g * 0.04F;
               float l = h * 0.04F;
               serverWorld.sendParticles(ParticleTypes.FISHING, d, e, j, 0, l, 0.01, -k, 1.0);
               serverWorld.sendParticles(ParticleTypes.FISHING, d, e, j, 0, -l, 0.01, k, 1.0);
            }
         } else {
            this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            double m = this.getY() + 0.5;
            serverWorld.sendParticles(
               ParticleTypes.BUBBLE, this.getX(), m, this.getZ(), (int)(1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0, this.getBbWidth(), 0.2F
            );
            serverWorld.sendParticles(
               ParticleTypes.FISHING, this.getX(), m, this.getZ(), (int)(1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0, this.getBbWidth(), 0.2F
            );
            this.hookCountdown = Mth.nextInt(this.random, 20, 40);
            this.getEntityData().set(CAUGHT_FISH, true);
         }
      } else if (this.waitCountdown > 0) {
         this.waitCountdown -= i;
         float f = 0.15F;
         if (this.waitCountdown < 20) {
            f += (20 - this.waitCountdown) * 0.05F;
         } else if (this.waitCountdown < 40) {
            f += (40 - this.waitCountdown) * 0.02F;
         } else if (this.waitCountdown < 60) {
            f += (60 - this.waitCountdown) * 0.01F;
         }

         if (this.random.nextFloat() < f) {
            float g = Mth.nextFloat(this.random, 0.0F, 360.0F) * (float) (Math.PI / 180.0);
            float h = Mth.nextFloat(this.random, 25.0F, 60.0F);
            double d = this.getX() + Mth.sin(g) * h * 0.1;
            double e = Mth.floor(this.getY()) + 1.0F;
            double j = this.getZ() + Mth.cos(g) * h * 0.1;
            BlockState blockState = serverWorld.getBlockState(BlockPos.containing(d, e - 1.0, j));
            if (blockState.is(Blocks.WATER)) {
               serverWorld.sendParticles(ParticleTypes.SPLASH, d, e, j, 2 + this.random.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
            }
         }

         if (this.waitCountdown <= 0) {
            this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
            this.fishTravelCountdown = Mth.nextInt(this.random, 20, 80);
         }
      } else {
         this.waitCountdown = Mth.nextInt(this.random, 100, 600);
         this.waitCountdown = this.waitCountdown - this.lureLevel * 20 * 5;
      }
   }

   private boolean isOpenOrWaterAround(BlockPos pos) {
      CustomFishingBobberEntity.PositionType positionType = CustomFishingBobberEntity.PositionType.INVALID;

      for (int i = -1; i <= 2; i++) {
         CustomFishingBobberEntity.PositionType positionType2 = this.getPositionType(pos.offset(-2, i, -2), pos.offset(2, i, 2));
         switch (positionType2) {
            case INVALID:
               return false;
            case ABOVE_WATER:
               if (positionType == CustomFishingBobberEntity.PositionType.INVALID) {
                  return false;
               }
               break;
            case INSIDE_WATER:
               if (positionType == CustomFishingBobberEntity.PositionType.ABOVE_WATER) {
                  return false;
               }
         }

         positionType = positionType2;
      }

      return true;
   }

   private CustomFishingBobberEntity.PositionType getPositionType(BlockPos start, BlockPos end) {
      return BlockPos.betweenClosedStream(start, end)
         .map(this::getPositionType)
         .reduce((positionType, positionType2) -> positionType == positionType2 ? positionType : CustomFishingBobberEntity.PositionType.INVALID)
         .orElse(CustomFishingBobberEntity.PositionType.INVALID);
   }

   private CustomFishingBobberEntity.PositionType getPositionType(BlockPos pos) {
      BlockState blockState = this.level().getBlockState(pos);
      if (!blockState.isAir() && !blockState.is(Blocks.LILY_PAD)) {
         FluidState fluidState = blockState.getFluidState();
         return fluidState.is(FluidTags.WATER) && fluidState.isSource() && blockState.getCollisionShape(this.level(), pos).isEmpty()
            ? CustomFishingBobberEntity.PositionType.INSIDE_WATER
            : CustomFishingBobberEntity.PositionType.INVALID;
      } else {
         return CustomFishingBobberEntity.PositionType.ABOVE_WATER;
      }
   }

   public boolean isInOpenWater() {
      return this.inOpenWater;
   }

   public void addAdditionalSaveData(CompoundTag nbt) {
   }

   public void readAdditionalSaveData(CompoundTag nbt) {
   }

   public int use(ItemStack usedItem) {
      LivingEntity playerEntity = this.getPlayerOwner();
      if (!this.level().isClientSide && playerEntity != null && !this.removeIfInvalid(playerEntity)) {
         int i = 0;
         if (this.hookedEntity != null) {
            this.pullHookedEntity(this.hookedEntity);
            this.level().broadcastEntityEvent(this, (byte)31);
            i = this.hookedEntity instanceof ItemEntity ? 3 : 5;
         } else if (this.hookCountdown > 0) {
            LootParams lootContextParameterSet = new Builder((ServerLevel)this.level())
               .withParameter(LootContextParams.ORIGIN, this.position())
               .withParameter(LootContextParams.TOOL, usedItem)
               .withParameter(LootContextParams.THIS_ENTITY, this)
               .withLuck(this.luckOfTheSeaLevel)
               .create(LootContextParamSets.FISHING);
            LootTable lootTable = this.level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);

            for (ItemStack itemStack : lootTable.getRandomItems(lootContextParameterSet)) {
               ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemStack);
               double d = playerEntity.getX() - this.getX();
               double e = playerEntity.getY() - this.getY();
               double f = playerEntity.getZ() - this.getZ();
               double g = 0.1;
               itemEntity.setDeltaMovement(d * 0.1, e * 0.1 + Math.sqrt(Math.sqrt(d * d + e * e + f * f)) * 0.08, f * 0.1);
               this.level().addFreshEntity(itemEntity);
               playerEntity.level()
                  .addFreshEntity(
                     new ExperienceOrb(
                        playerEntity.level(), playerEntity.getX(), playerEntity.getY() + 0.5, playerEntity.getZ() + 0.5, this.random.nextInt(6) + 1
                     )
                  );
               if (itemStack.is(ItemTags.FISHES)) {
               }
            }

            i = 1;
         }

         if (this.onGround()) {
            i = 2;
         }

         this.discard();
         return i;
      } else {
         return 0;
      }
   }

   public void handleEntityEvent(byte status) {
      if (status == 31 && this.level().isClientSide && this.hookedEntity instanceof LivingEntity) {
         this.pullHookedEntity(this.hookedEntity);
      }

      super.handleEntityEvent(status);
   }

   protected void pullHookedEntity(Entity entity) {
      Entity entity2 = this.getOwner();
      if (entity2 != null) {
         Vec3 vec3d = new Vec3(entity2.getX() - this.getX(), entity2.getY() - this.getY(), entity2.getZ() - this.getZ()).scale(0.1);
         entity.setDeltaMovement(entity.getDeltaMovement().add(vec3d));
      }
   }

   protected MovementEmission getMovementEmission() {
      return MovementEmission.NONE;
   }

   public void remove(RemovalReason reason) {
      this.setPlayerFishHook(null);
      super.remove(reason);
   }

   public void onClientRemoval() {
      this.setPlayerFishHook(null);
   }

   public void setOwner(@Nullable Entity entity) {
      super.setOwner(entity);
      this.setPlayerFishHook(this);
   }

   private void setPlayerFishHook(@Nullable CustomFishingBobberEntity fishingBobber) {
      LivingEntity playerEntity = this.getPlayerOwner();
      if (playerEntity != null) {
      }
   }

   @Nullable
   public LivingEntity getPlayerOwner() {
      Entity entity = this.getOwner();
      return entity instanceof LivingEntity ? (LivingEntity)entity : null;
   }

   @Nullable
   public Entity getHookedEntity() {
      return this.hookedEntity;
   }

   public boolean canChangeDimensions() {
      return false;
   }

   public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
      Entity entity2 = this.getOwner();
      return new ClientboundAddEntityPacket(this, entity, entity2 == null ? this.getId() : entity2.getId());
   }

   public void recreateFromPacket(ClientboundAddEntityPacket packet) {
      super.recreateFromPacket(packet);
      if (this.getPlayerOwner() == null) {
         int i = packet.getData();
         LOGGER.error("Failed to recreate fishing hook on client. {} (id: {}) is not a valid owner.", this.level().getEntity(i), i);
         this.kill((ServerLevel) level());
      }
   }

   static enum PositionType {
      ABOVE_WATER,
      INSIDE_WATER,
      INVALID;
   }

   static enum State {
      FLYING,
      HOOKED_IN_ENTITY,
      BOBBING;
   }
}
