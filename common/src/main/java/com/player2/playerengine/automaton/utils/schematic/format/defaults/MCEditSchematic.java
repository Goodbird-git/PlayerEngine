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

package com.player2.playerengine.automaton.utils.schematic.format.defaults;

import com.player2.playerengine.automaton.utils.schematic.StaticSchematic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class MCEditSchematic extends StaticSchematic {
   public MCEditSchematic(CompoundTag schematic) {
      String type = schematic.getString("Materials").get();
      if (!type.equals("Alpha")) {
         throw new IllegalStateException("bad schematic " + type);
      } else {
         this.x = schematic.getInt("Width").get();
         this.y = schematic.getInt("Height").get();
         this.z = schematic.getInt("Length").get();
         byte[] blocks = schematic.getByteArray("Blocks").get();
         byte[] additional = null;
         if (schematic.contains("AddBlocks")) {
            byte[] addBlocks = schematic.getByteArray("AddBlocks").get();
            additional = new byte[addBlocks.length * 2];

            for (int i = 0; i < addBlocks.length; i++) {
               additional[i * 2 + 0] = (byte)(addBlocks[i] >> 4 & 15);
               additional[i * 2 + 1] = (byte)(addBlocks[i] >> 0 & 15);
            }
         }

         this.states = new BlockState[this.x][this.z][this.y];

         for (int y = 0; y < this.y; y++) {
            for (int z = 0; z < this.z; z++) {
               for (int x = 0; x < this.x; x++) {
                  int blockInd = (y * this.z + z) * this.x + x;
                  int blockID = blocks[blockInd] & 255;
                  if (additional != null) {
                     blockID |= additional[blockInd] << 8;
                  }

                  Block block = (Block)BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(ItemIdFix.getItem(blockID))).get().value();
                  this.states[x][z][y] = block.defaultBlockState();
               }
            }
         }
      }
   }
}
