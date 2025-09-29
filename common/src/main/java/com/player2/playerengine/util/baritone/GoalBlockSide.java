package com.player2.playerengine.util.baritone;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

public class GoalBlockSide implements Goal {
   private final BlockPos block;
   private final Direction direction;
   private final double buffer;

   public GoalBlockSide(BlockPos block, Direction direction, double bufferDistance) {
      this.block = block;
      this.direction = direction;
      this.buffer = bufferDistance;
   }

   public GoalBlockSide(BlockPos block, Direction direction) {
      this(block, direction, 1.0);
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return this.getDistanceInRightDirection(x, y, z) > 0.0;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return Math.min(this.getDistanceInRightDirection(x, y, z), 0.0);
   }

   private double getDistanceInRightDirection(int x, int y, int z) {
      Vec3 delta = new Vec3(x, y, z).subtract(this.block.getX(), this.block.getY(), this.block.getZ());
      Vec3i dir = this.direction.getUnitVec3i();
      double dot = new Vec3(dir.getX(), dir.getY(), dir.getZ()).dot(delta);
      return dot - this.buffer;
   }
}
