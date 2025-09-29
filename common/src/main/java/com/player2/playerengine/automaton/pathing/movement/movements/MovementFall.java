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

package com.player2.playerengine.automaton.pathing.movement.movements;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.pathing.movement.MovementStatus;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.RotationUtils;
import com.player2.playerengine.automaton.api.utils.VecUtils;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.behavior.InventoryBehavior;
import com.player2.playerengine.automaton.pathing.movement.CalculationContext;
import com.player2.playerengine.automaton.pathing.movement.Movement;
import com.player2.playerengine.automaton.pathing.movement.MovementHelper;
import com.player2.playerengine.automaton.pathing.movement.MovementState;
import com.player2.playerengine.automaton.utils.pathing.MutableMoveResult;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;

public class MovementFall extends Movement {
   public MovementFall(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
      super(baritone, src, dest, buildPositionsToBreak(src, dest));
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      MovementDescend.cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.y != this.dest.y ? 1000000.0 : result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      Set<BetterBlockPos> set = new HashSet<>();
      set.add(this.src);

      for (int y = this.src.y - this.dest.y; y >= 0; y--) {
         set.add(this.dest.up(y));
      }

      return set;
   }

   private boolean willPlaceBucket() {
      CalculationContext context = new CalculationContext(this.baritone);
      MutableMoveResult result = new MutableMoveResult();
      return MovementDescend.dynamicFallCost(
         context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, 0.0, context.get(this.dest.x, this.src.y - 2, this.dest.z), result
      );
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else {
         BlockPos playerFeet = this.ctx.feetPos();
         Rotation toDest = RotationUtils.calcRotationFromVec3d(this.ctx.headPos(), VecUtils.getBlockPosCenter(this.dest), this.ctx.entityRotations());
         Rotation targetRotation = null;
         BlockState destState = this.ctx.world().getBlockState(this.dest);
         boolean isWater = destState.getFluidState().getType() instanceof WaterFluid;
         if (!isWater && this.willPlaceBucket() && !playerFeet.equals(this.dest)) {
            LivingEntityInventory inventory = this.ctx.inventory();
            if (inventory == null
               || !LivingEntityInventory.isValidHotbarIndex(InventoryBehavior.getSlotWithStack(inventory, PlayerEngine.WATER_BUCKETS))
               || this.ctx.world().dimensionType().ultraWarm()) {
               return state.setStatus(MovementStatus.UNREACHABLE);
            }

            if (this.ctx.entity().getY() - this.dest.getY() < this.ctx.playerController().getBlockReachDistance() && !this.ctx.entity().onGround()) {
               inventory.selectedSlot = InventoryBehavior.getSlotWithStack(inventory, PlayerEngine.WATER_BUCKETS);
               targetRotation = new Rotation(toDest.getYaw(), 90.0F);
               if (this.ctx.isLookingAt(this.dest) || this.ctx.isLookingAt(this.dest.down())) {
                  state.setInput(Input.CLICK_RIGHT, true);
               }
            }
         }

         if (targetRotation != null) {
            state.setTarget(new MovementState.MovementTarget(targetRotation, true));
         } else {
            state.setTarget(new MovementState.MovementTarget(toDest, false));
         }

         if (playerFeet.equals(this.dest) && (this.ctx.entity().getY() - playerFeet.getY() < 0.094 || isWater)) {
            if (!isWater) {
               return state.setStatus(MovementStatus.SUCCESS);
            }

            state.setInput(Input.JUMP, true);
            LivingEntityInventory inventoryx = this.ctx.inventory();
            if (inventoryx != null && LivingEntityInventory.isValidHotbarIndex(InventoryBehavior.getSlotWithStack(inventoryx, PlayerEngine.EMPTY_BUCKETS))) {
               inventoryx.selectedSlot = InventoryBehavior.getSlotWithStack(inventoryx, PlayerEngine.EMPTY_BUCKETS);
               if (this.ctx.entity().getDeltaMovement().y >= 0.0) {
                  return state.setInput(Input.CLICK_RIGHT, true);
               }

               return state;
            }

            if (this.ctx.entity().getDeltaMovement().y >= 0.0) {
               return state.setStatus(MovementStatus.SUCCESS);
            }
         }

         Vec3 destCenter = VecUtils.getBlockPosCenter(this.dest);
         if (Math.abs(this.ctx.entity().getX() + this.ctx.entity().getDeltaMovement().x - destCenter.x) > 0.1
            || Math.abs(this.ctx.entity().getZ() + this.ctx.entity().getDeltaMovement().z - destCenter.z) > 0.1) {
            if (!this.ctx.entity().onGround() && Math.abs(this.ctx.entity().getDeltaMovement().y) > 0.4) {
               state.setInput(Input.SNEAK, true);
            }

            state.setInput(Input.MOVE_FORWARD, true);
         }

         Vec3i avoid = Optional.ofNullable(this.avoid()).<Vec3i>map(Direction::getUnitVec3i).orElse(null);
         if (avoid == null) {
            avoid = this.src.subtract(this.dest);
         } else {
            double dist = Math.abs(avoid.getX() * (destCenter.x - avoid.getX() / 2.0 - this.ctx.entity().getX()))
               + Math.abs(avoid.getZ() * (destCenter.z - avoid.getZ() / 2.0 - this.ctx.entity().getZ()));
            if (dist < 0.6) {
               state.setInput(Input.MOVE_FORWARD, true);
            } else if (!this.ctx.entity().onGround()) {
               state.setInput(Input.SNEAK, false);
            }
         }

         if (targetRotation == null) {
            Vec3 destCenterOffset = new Vec3(destCenter.x + 0.125 * avoid.getX(), destCenter.y, destCenter.z + 0.125 * avoid.getZ());
            state.setTarget(
               new MovementState.MovementTarget(RotationUtils.calcRotationFromVec3d(this.ctx.headPos(), destCenterOffset, this.ctx.entityRotations()), false)
            );
         }

         if (this.ctx.world().getBlockState(playerFeet).is(Blocks.SCAFFOLDING) || this.ctx.world().getBlockState(playerFeet.below()).is(Blocks.SCAFFOLDING)) {
            state.setInput(Input.SNEAK, true);
         }

         return state;
      }
   }

   private Direction avoid() {
      for (int i = 0; i < 15; i++) {
         BlockState state = this.ctx.world().getBlockState(this.ctx.feetPos().down(i));
         if (state.getBlock() == Blocks.LADDER) {
            return (Direction)state.getValue(LadderBlock.FACING);
         }
      }

      return null;
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return this.ctx.feetPos().equals(this.src) || state.getStatus() != MovementStatus.RUNNING;
   }

   private static BetterBlockPos[] buildPositionsToBreak(BetterBlockPos src, BetterBlockPos dest) {
      int diffX = src.getX() - dest.getX();
      int diffZ = src.getZ() - dest.getZ();
      int diffY = src.getY() - dest.getY();
      BetterBlockPos[] toBreak = new BetterBlockPos[diffY + 2];

      for (int i = 0; i < toBreak.length; i++) {
         toBreak[i] = new BetterBlockPos(src.getX() - diffX, src.getY() + 1 - i, src.getZ() - diffZ);
      }

      return toBreak;
   }

   @Override
   protected boolean prepared(MovementState state) {
      if (state.getStatus() == MovementStatus.WAITING) {
         return true;
      } else {
         for (int i = 0; i < 4 && i < this.positionsToBreak.length; i++) {
            if (!MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[i])) {
               return super.prepared(state);
            }
         }

         return true;
      }
   }
}
