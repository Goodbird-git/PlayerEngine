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

package altoclef.trackers.storage;

import altoclef.trackers.Tracker;
import altoclef.trackers.TrackerManager;
import altoclef.util.helpers.ItemHelper;
import altoclef.util.slots.Slot;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class InventorySubTracker extends Tracker {
   private final Map<Item, List<Integer>> itemToSlotPlayer = new HashMap<>();
   private final Map<Item, Integer> itemCountsPlayer = new HashMap<>();

   public InventorySubTracker(TrackerManager manager) {
      super(manager);
   }

   public int getItemCount(Item... items) {
      this.ensureUpdated();
      int result = 0;
      ItemStack cursorStack = this.mod.getSlotHandler().getCursorStack();

      for (Item item : items) {
         if (cursorStack.is(item)) {
            result += cursorStack.getCount();
         }

         result += this.itemCountsPlayer.getOrDefault(item, 0);
      }

      return result;
   }

   public boolean hasItem(Item... items) {
      this.ensureUpdated();
      ItemStack cursorStack = this.mod.getSlotHandler().getCursorStack();

      for (Item item : items) {
         if (cursorStack.is(item)) {
            return true;
         }

         if (this.itemCountsPlayer.containsKey(item)) {
            return true;
         }
      }

      return false;
   }

   public List<Slot> getSlotsWithItemsPlayerInventory(boolean includeArmor, Item... items) {
      this.ensureUpdated();
      List<Slot> result = new ArrayList<>();
      LivingEntityInventory inventory = ((IInventoryProvider)this.mod.getEntity()).getLivingInventory();

      for (Item item : items) {
         if (this.itemToSlotPlayer.containsKey(item)) {
            for (Integer index : this.itemToSlotPlayer.get(item)) {
               result.add(new Slot(inventory.main, index));
            }
         }
      }

      if (includeArmor) {
         for (int i = 0; i < inventory.armor.size(); i++) {
            ItemStack stack = (ItemStack)inventory.armor.get(i);
            if (Arrays.stream(items).anyMatch(stack::is)) {
               result.add(new Slot(inventory.armor, i));
            }
         }
      }

      ItemStack offhandStack = (ItemStack)inventory.offHand.get(0);
      if (Arrays.stream(items).anyMatch(offhandStack::is)) {
         result.add(new Slot(inventory.offHand, 0));
      }

      return result;
   }

   public List<ItemStack> getInventoryStacks() {
      this.ensureUpdated();
      LivingEntityInventory inventory = ((IInventoryProvider)this.mod.getEntity()).getLivingInventory();
      List<ItemStack> stacks = new ArrayList<>();
      stacks.addAll(inventory.main);
      stacks.addAll(inventory.armor);
      stacks.addAll(inventory.offHand);
      return stacks;
   }

   public List<Slot> getSlotsThatCanFit(ItemStack item, boolean acceptPartial) {
      this.ensureUpdated();
      List<Slot> result = new ArrayList<>();
      LivingEntityInventory inventory = ((IInventoryProvider)this.mod.getEntity()).getLivingInventory();
      if (item.isStackable()) {
         for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stackInSlot = (ItemStack)inventory.main.get(i);
            if (ItemHelper.canStackTogether(item, stackInSlot)) {
               int roomLeft = stackInSlot.getMaxStackSize() - stackInSlot.getCount();
               if (acceptPartial || roomLeft >= item.getCount()) {
                  result.add(new Slot(inventory.main, i));
               }
            }
         }
      }

      for (int ix = 0; ix < inventory.main.size(); ix++) {
         if (((ItemStack)inventory.main.get(ix)).isEmpty()) {
            result.add(new Slot(inventory.main, ix));
         }
      }

      return result;
   }

   public boolean hasEmptySlot() {
      this.ensureUpdated();
      return this.itemCountsPlayer.getOrDefault(Items.AIR, 0) > 0;
   }

   @Override
   protected void updateState() {
      this.reset();
      LivingEntityInventory inventory = ((IInventoryProvider)this.mod.getEntity()).getLivingInventory();
      if (inventory != null) {
         for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = (ItemStack)inventory.main.get(i);
            this.registerItem(stack, i, inventory.main);
         }

         for (int i = 0; i < inventory.armor.size(); i++) {
            ItemStack stack = (ItemStack)inventory.armor.get(i);
            this.registerItem(stack, i, inventory.armor);
         }

         for (int i = 0; i < inventory.offHand.size(); i++) {
            ItemStack stack = (ItemStack)inventory.offHand.get(i);
            this.registerItem(stack, i, inventory.offHand);
         }
      }
   }

   private void registerItem(ItemStack stack, int index, NonNullList<ItemStack> inventory) {
      Item item = stack.isEmpty() ? Items.AIR : stack.getItem();
      int count = stack.getCount();
      this.itemCountsPlayer.put(item, this.itemCountsPlayer.getOrDefault(item, 0) + count);
      if (inventory instanceof NonNullList) {
         this.itemToSlotPlayer.computeIfAbsent(item, k -> new ArrayList<>()).add(index);
      }
   }

   @Override
   protected void reset() {
      this.itemToSlotPlayer.clear();
      this.itemCountsPlayer.clear();
   }
}
