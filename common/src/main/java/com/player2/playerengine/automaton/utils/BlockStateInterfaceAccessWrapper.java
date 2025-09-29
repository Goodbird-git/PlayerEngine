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

package com.player2.playerengine.automaton.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public final class BlockStateInterfaceAccessWrapper implements BlockGetter {
   private final BlockStateInterface bsi;

   BlockStateInterfaceAccessWrapper(BlockStateInterface bsi) {
      this.bsi = bsi;
   }

   @Nullable
   public BlockEntity getBlockEntity(BlockPos pos) {
      return null;
   }

   public BlockState getBlockState(BlockPos pos) {
      return this.bsi.get0(pos.getX(), pos.getY(), pos.getZ());
   }

   public FluidState getFluidState(BlockPos blockPos) {
      return this.getBlockState(blockPos).getFluidState();
   }

   public int getHeight() {
      return this.bsi.world.getHeight();
   }

   public int getMinY() {
      return this.bsi.world.getMinY();
   }
}
