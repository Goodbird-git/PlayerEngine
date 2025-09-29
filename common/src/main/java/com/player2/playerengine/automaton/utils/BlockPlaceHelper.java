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

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class BlockPlaceHelper {
   private final IEntityContext ctx;
   private int rightClickTimer;

   BlockPlaceHelper(IEntityContext playerContext) {
      this.ctx = playerContext;
   }

   public void tick(boolean rightClickRequested) {
      if (this.rightClickTimer > 0) {
         this.rightClickTimer--;
      } else {
         HitResult mouseOver = this.ctx.objectMouseOver();
         boolean isRowingBoat = this.ctx.entity().getVehicle() != null && this.ctx.entity().getVehicle() instanceof Boat;
         if (rightClickRequested && this.ctx.entity() instanceof IInventoryProvider && !isRowingBoat && mouseOver != null && mouseOver.getType() == Type.BLOCK) {
            this.rightClickTimer = BaritoneAPI.getGlobalSettings().rightClickSpeed.get();
            LivingEntity player = this.ctx.entity();

            for (InteractionHand hand : InteractionHand.values()) {
               InteractionResult actionResult = this.ctx.playerController().processRightClickBlock(player, this.ctx.world(), hand, (BlockHitResult)mouseOver);
               if (actionResult.consumesAction()) {
                  if (!actionResult.consumesAction()) {
                     player.swing(hand);
                  }

                  return;
               }

               if (!player.getItemInHand(hand).isEmpty() && this.ctx.playerController().processRightClick(player, this.ctx.world(), hand).consumesAction()) {
                  return;
               }
            }
         }
      }
   }
}
