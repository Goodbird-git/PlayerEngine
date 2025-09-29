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

package com.player2.playerengine.automaton.process;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.automaton.api.process.IBaritoneProcess;
import com.player2.playerengine.automaton.api.process.PathingCommand;
import com.player2.playerengine.automaton.api.process.PathingCommandType;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.RotationUtils;
import com.player2.playerengine.automaton.entity.CustomFishingBobberEntity;
import com.player2.playerengine.automaton.pathing.movement.MovementHelper;
import com.player2.playerengine.automaton.utils.BaritoneProcessHelper;
import java.util.Optional;
import java.util.stream.StreamSupport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class FishingProcess extends BaritoneProcessHelper implements IBaritoneProcess {
   private boolean active = false;
   private FishingProcess.State currentState = FishingProcess.State.IDLE;
   private BlockPos fishingSpot = null;
   private CustomFishingBobberEntity bobber = null;
   private int timeoutTicks = 0;

   public FishingProcess(Baritone baritone) {
      super(baritone);
   }

   public void fish() {
      this.active = true;
      this.currentState = FishingProcess.State.IDLE;
      this.fishingSpot = null;
      this.bobber = null;
      this.timeoutTicks = 0;
      this.logDirect("Fishing process started.");
   }

   @Override
   public boolean isActive() {
      return this.active;
   }

   @Override
   public void onLostControl() {
      this.active = false;
      this.bobber = null;
   }

   @Override
   public String displayName0() {
      return "Fishing: " + this.currentState.toString();
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (this.findFishingRodSlot() == -1) {
         this.onLostControl();
         return null;
      } else if (calcFailed) {
         this.onLostControl();
         return null;
      } else {
         switch (this.currentState) {
            case IDLE:
               return this.handleIdleState();
            case WALKING_TO_WATER:
               return this.handleWalkingState();
            case PREPARING_TO_CAST:
               this.handlePreparingToCastState();
               break;
            case CASTING:
               this.handleCastingState();
               break;
            case WAITING_FOR_BITE:
               this.handleWaitingForBiteState();
               break;
            case REELING_IN:
               this.handleReelingInState();
               break;
            case WAITING_FOR_ITEMS:
            case RECAST_DELAY:
               this.handleDelayStates();
         }

         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      }
   }

   private PathingCommand handleIdleState() {
      Optional<BlockPos> spot = this.findWaterSpot();
      if (spot.isPresent()) {
         this.fishingSpot = spot.get().above();
         this.currentState = FishingProcess.State.WALKING_TO_WATER;
         return new PathingCommand(new GoalBlock(this.fishingSpot), PathingCommandType.SET_GOAL_AND_PATH);
      } else {
         this.onLostControl();
         return null;
      }
   }

   private PathingCommand handleWalkingState() {
      Goal goal = new GoalBlock(this.fishingSpot);
      if (goal.isInGoal(this.ctx.feetPos())) {
         this.currentState = FishingProcess.State.PREPARING_TO_CAST;
         this.timeoutTicks = 0;
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      } else {
         return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
      }
   }

   private void handlePreparingToCastState() {
      BlockPos waterBlock = this.findAdjacentWater(this.fishingSpot.below());
      if (waterBlock == null) {
         this.onLostControl();
      } else {
         Rotation rotation = RotationUtils.calcRotationFromVec3d(this.ctx.headPos(), Vec3.atCenterOf(waterBlock));
         this.baritone.getLookBehavior().updateTarget(rotation, true);
         if (this.ctx.entityRotations().isReallyCloseTo(rotation)) {
            this.currentState = FishingProcess.State.CASTING;
         }
      }
   }

   private void handleCastingState() {
      this.equipFishingRod();
      this.useFishingRod(this.ctx.world(), this.ctx.entity(), InteractionHand.MAIN_HAND);
      this.currentState = FishingProcess.State.WAITING_FOR_BITE;
      this.timeoutTicks = 0;
   }

   private void handleWaitingForBiteState() {
      if (this.bobber == null || !this.bobber.isAlive() || this.timeoutTicks < 60) {
         this.bobber = this.findOurBobber();
         if (this.bobber == null || this.timeoutTicks < 60) {
            this.timeoutTicks++;
            if (this.timeoutTicks > 70) {
               this.currentState = FishingProcess.State.RECAST_DELAY;
            }

            return;
         }
      }

      if (this.bobber.getDeltaMovement().y < -0.04) {
         this.currentState = FishingProcess.State.REELING_IN;
      } else {
         this.timeoutTicks++;
         if (this.timeoutTicks > 1200) {
            this.currentState = FishingProcess.State.RECAST_DELAY;
         }
      }
   }

   private void handleReelingInState() {
      this.equipFishingRod();
      this.useFishingRod(this.ctx.world(), this.ctx.entity(), InteractionHand.MAIN_HAND);
      this.bobber = null;
      this.currentState = FishingProcess.State.WAITING_FOR_ITEMS;
      this.timeoutTicks = 20;
   }

   private void handleDelayStates() {
      this.timeoutTicks--;
      if (this.timeoutTicks <= 0) {
         if (this.currentState == FishingProcess.State.WAITING_FOR_ITEMS) {
            this.currentState = FishingProcess.State.RECAST_DELAY;
            this.timeoutTicks = 10;
         } else {
            this.currentState = FishingProcess.State.PREPARING_TO_CAST;
         }
      }
   }

   private Optional<BlockPos> findWaterSpot() {
      BlockPos center = this.ctx.feetPos();

      for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
         if (this.ctx.world().getBlockState(pos).is(Blocks.WATER)
            && this.ctx.world().getBlockState(pos.above()).isAir()
            && this.ctx.world().getBlockState(pos.above(2)).isAir()) {
            for (Direction dir : Plane.HORIZONTAL) {
               BlockPos standPos = pos.relative(dir);
               if (MovementHelper.canWalkOn(this.ctx, standPos)
                  && this.ctx.world().getBlockState(standPos.above()).isAir()
                  && this.ctx.world().getBlockState(standPos.above(2)).isAir()) {
                  return Optional.of(standPos.immutable());
               }
            }
         }
      }

      return Optional.empty();
   }

   private BlockPos findAdjacentWater(BlockPos position) {
      for (Direction dir : Plane.HORIZONTAL) {
         if (this.ctx.world().getBlockState(position.relative(dir)).is(Blocks.WATER)) {
            return position.relative(dir);
         }
      }

      return null;
   }

   private int findFishingRodSlot() {
      LivingEntityInventory inv = this.ctx.inventory();

      for (int i = 0; i < 9; i++) {
         if (inv.getItem(i).getItem() instanceof FishingRodItem) {
            return i;
         }
      }

      return -1;
   }

   private void equipFishingRod() {
      int slot = this.findFishingRodSlot();
      if (slot != -1) {
         this.ctx.inventory().selectedSlot = slot;
      }
   }

   @Nullable
   private CustomFishingBobberEntity findOurBobber() {
      return (CustomFishingBobberEntity) StreamSupport.stream(this.ctx.world().getAllEntities().spliterator(), false)
         .filter(e -> e instanceof CustomFishingBobberEntity)
         .filter(e -> ((CustomFishingBobberEntity)e).getPlayerOwner() == this.ctx.entity())
         .findFirst()
         .orElse(null);
   }

   public InteractionResult useFishingRod(Level world, LivingEntity user, InteractionHand hand) {
      ItemStack itemStack = user.getItemInHand(hand);
      CustomFishingBobberEntity bobber = this.findOurBobber();
      if (bobber != null) {
         if (!world.isClientSide) {
            int i = bobber.use(itemStack);
            itemStack.hurtAndBreak(i, user, EquipmentSlot.MAINHAND);
         }

         world.playSound(
            null,
            user.getX(),
            user.getY(),
            user.getZ(),
            SoundEvents.FISHING_BOBBER_RETRIEVE,
            SoundSource.NEUTRAL,
            1.0F,
            0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F)
         );
         user.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
      } else {
         world.playSound(
            null,
            user.getX(),
            user.getY(),
            user.getZ(),
            SoundEvents.FISHING_BOBBER_THROW,
            SoundSource.NEUTRAL,
            0.5F,
            0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F)
         );
         if (!world.isClientSide) {
            int i = (int)(EnchantmentHelper.getFishingTimeReduction((ServerLevel) world, itemStack, user) * 20.0F);
            int j = EnchantmentHelper.getFishingLuckBonus((ServerLevel) world, itemStack, user);
            world.addFreshEntity(new CustomFishingBobberEntity(user, world, j, i));
         }

         user.gameEvent(GameEvent.ITEM_INTERACT_START);
      }

      return InteractionResult.SUCCESS;
   }

   private static enum State {
      IDLE,
      WALKING_TO_WATER,
      PREPARING_TO_CAST,
      CASTING,
      WAITING_FOR_BITE,
      REELING_IN,
      WAITING_FOR_ITEMS,
      RECAST_DELAY;
   }
}
