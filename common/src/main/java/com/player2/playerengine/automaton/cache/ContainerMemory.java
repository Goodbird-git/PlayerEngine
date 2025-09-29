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

package com.player2.playerengine.automaton.cache;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.cache.IContainerMemory;
import com.player2.playerengine.automaton.api.cache.IRememberedInventory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.util.BlockPosUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ContainerMemory implements IContainerMemory {
   private final Map<BlockPos, ContainerMemory.RememberedInventory> inventories = new HashMap<>();

   public void read(ValueInput input) {
      try {
         ValueInput.ValueInputList nbtInventories = input.childrenList("inventories").get();

         for (ValueInput nbtEntry : nbtInventories) {
            BlockPos pos = BlockPosUtils.readBlockPos(nbtEntry,"pos").get();
            ContainerMemory.RememberedInventory rem = new ContainerMemory.RememberedInventory();
            rem.fromNbt(nbtEntry.list("content", ItemStack.CODEC).get());
            if (!rem.items.isEmpty()) {
               this.inventories.put(pos, rem);
            }
         }
      } catch (Exception var7) {
         PlayerEngine.LOGGER.error(var7);
         this.inventories.clear();
      }
   }

   public void toNbt(ValueOutput output) {
      if (BaritoneAPI.getGlobalSettings().containerMemory.get()) {
         ValueOutput.ValueOutputList list = output.childrenList("inventories");

         for (Entry<BlockPos, ContainerMemory.RememberedInventory> entry : this.inventories.entrySet()) {
            ValueOutput nbtEntry = list.addChild();
            nbtEntry.putIntArray("pos", BlockPosUtils.writeBlockPos(entry.getKey()));

           entry.getValue().toNbt(nbtEntry.list("content", ItemStack.CODEC));
         }
      }
   }

   public synchronized void setup(BlockPos pos, int windowId, int slotCount) {
      ContainerMemory.RememberedInventory inventory = this.inventories.computeIfAbsent(pos, x -> new ContainerMemory.RememberedInventory());
      inventory.windowId = windowId;
      inventory.size = slotCount;
   }

   public synchronized Optional<ContainerMemory.RememberedInventory> getInventoryFromWindow(int windowId) {
      return this.inventories.values().stream().filter(i -> i.windowId == windowId).findFirst();
   }

   public final synchronized ContainerMemory.RememberedInventory getInventoryByPos(BlockPos pos) {
      return this.inventories.get(pos);
   }

   @Override
   public final synchronized Map<BlockPos, IRememberedInventory> getRememberedInventories() {
      return new HashMap<>(this.inventories);
   }

   public static class RememberedInventory implements IRememberedInventory {
      private final List<ItemStack> items = new ArrayList<>();
      private int windowId;
      private int size;

      private RememberedInventory() {
      }

      @Override
      public final List<ItemStack> getContents() {
         return Collections.unmodifiableList(this.items);
      }

      @Override
      public final int getSize() {
         return this.size;
      }

      public void toNbt(ValueOutput.TypedOutputList<ItemStack> valueOutput) {
         for (ItemStack item : this.items) {
            valueOutput.add(item);
         }
      }

      public void fromNbt(ValueInput.TypedInputList<ItemStack> content) {
         for (ItemStack stack : content) {
            this.items.add(stack);
         }

         this.size = this.items.size();
         this.windowId = -1;
      }
   }
}
