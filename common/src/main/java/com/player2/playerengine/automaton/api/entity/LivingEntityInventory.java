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

package com.player2.playerengine.automaton.api.entity;

import net.minecraft.resources.ResourceLocation;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.player2.playerengine.PlayerEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LivingEntityInventory implements Container, Nameable {
   public static final Logger LOGGER = LogManager.getLogger(PlayerEngine.MOD_NAME);
   public static final int ITEM_USAGE_COOLDOWN = 5;
   public static final int MAIN_SIZE = 36;
   private static final int HOTBAR_SIZE = 9;
   public static final int OFF_HAND_SLOT = 40;
   public static final int NOT_FOUND = -1;
   public static final int[] ARMOR_SLOTS = new int[]{0, 1, 2, 3};
   public static final int[] HELMET_SLOTS = new int[]{3};
   public final NonNullList<ItemStack> main = NonNullList.withSize(36, ItemStack.EMPTY);
   public final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
   public final NonNullList<ItemStack> offHand = NonNullList.withSize(1, ItemStack.EMPTY);
   private final List<NonNullList<ItemStack>> combinedInventory = ImmutableList.of(this.main, this.armor, this.offHand);
   public int selectedSlot;
   public LivingEntity player;
   private int changeCount;

   public LivingEntityInventory(LivingEntity player) {
      this.player = player;
   }

   public ItemStack getMainHandStack() {
      return isValidHotbarIndex(this.selectedSlot) ? (ItemStack)this.main.get(this.selectedSlot) : ItemStack.EMPTY;
   }

   public static int getHotbarSize() {
      return 9;
   }

   private boolean canStackAddMore(ItemStack existingStack, ItemStack stack) {
      return !existingStack.isEmpty()
         && ItemStack.isSameItemSameComponents(existingStack, stack)
         && existingStack.isStackable()
         && existingStack.getCount() < existingStack.getMaxStackSize()
         && existingStack.getCount() < this.getMaxStackSize();
   }

   public int getEmptySlot() {
      for (int i = 0; i < this.main.size(); i++) {
         if (((ItemStack)this.main.get(i)).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   public void addPickBlock(ItemStack stack) {
      int i = this.getSlotWithStack(stack);
      if (isValidHotbarIndex(i)) {
         this.selectedSlot = i;
      } else if (i == -1) {
         this.selectedSlot = this.getSwappableHotbarSlot();
         if (!((ItemStack)this.main.get(this.selectedSlot)).isEmpty()) {
            int j = this.getEmptySlot();
            if (j != -1) {
               this.main.set(j, (ItemStack)this.main.get(this.selectedSlot));
            }
         }

         this.main.set(this.selectedSlot, stack);
      } else {
         this.swapSlotWithHotbar(i);
      }
   }

   public void swapSlotWithHotbar(int slot) {
      this.selectedSlot = this.getSwappableHotbarSlot();
      ItemStack itemStack = (ItemStack)this.main.get(this.selectedSlot);
      this.main.set(this.selectedSlot, (ItemStack)this.main.get(slot));
      this.main.set(slot, itemStack);
   }

   public static boolean isValidHotbarIndex(int slot) {
      return slot >= 0 && slot < 9;
   }

   public int getSlotWithStack(ItemStack stack) {
      for (int i = 0; i < this.main.size(); i++) {
         if (!((ItemStack)this.main.get(i)).isEmpty() && ItemStack.isSameItemSameComponents(stack, (ItemStack)this.main.get(i))) {
            return i;
         }
      }

      return -1;
   }

   public int indexOf(ItemStack stack) {
      for (int i = 0; i < this.main.size(); i++) {
         ItemStack itemStack = (ItemStack)this.main.get(i);
         if (!((ItemStack)this.main.get(i)).isEmpty()
            && ItemStack.isSameItemSameComponents(stack, (ItemStack)this.main.get(i))
            && !((ItemStack)this.main.get(i)).isDamaged()
            && !itemStack.isEnchanted()
            && !itemStack.has(DataComponents.CUSTOM_NAME)) {
            return i;
         }
      }

      return -1;
   }

   public int getSwappableHotbarSlot() {
      for (int i = 0; i < 9; i++) {
         int j = (this.selectedSlot + i) % 9;
         if (((ItemStack)this.main.get(j)).isEmpty()) {
            return j;
         }
      }

      for (int ix = 0; ix < 9; ix++) {
         int j = (this.selectedSlot + ix) % 9;
         if (!((ItemStack)this.main.get(j)).isEnchanted()) {
            return j;
         }
      }

      return this.selectedSlot;
   }

   public void scrollInHotbar(double scrollAmount) {
      int i = (int)Math.signum(scrollAmount);
      this.selectedSlot -= i;

      while (this.selectedSlot < 0) {
         this.selectedSlot += 9;
      }

      while (this.selectedSlot >= 9) {
         this.selectedSlot -= 9;
      }
   }

   public int remove(Predicate<ItemStack> shouldRemove, int maxCount, Container craftingInventory) {
      int i = 0;
      boolean bl = maxCount == 0;
      i += ContainerHelper.clearOrCountMatchingItems(this, shouldRemove, maxCount - i, bl);
      return i + ContainerHelper.clearOrCountMatchingItems(craftingInventory, shouldRemove, maxCount - i, bl);
   }

   private int addStack(ItemStack stack) {
      int i = this.getOccupiedSlotWithRoomForStack(stack);
      if (i == -1) {
         i = this.getEmptySlot();
      }

      return i == -1 ? stack.getCount() : this.addStack(i, stack);
   }

   private int addStack(int slot, ItemStack stack) {
      Item item = stack.getItem();
      int i = stack.getCount();
      ItemStack itemStack = this.getItem(slot);
      if (itemStack.isEmpty()) {
         itemStack = new ItemStack(item, 0);
          itemStack.applyComponents(stack.getComponents());

          this.setItem(slot, itemStack);
      }

      int j = i;
      if (i > itemStack.getMaxStackSize() - itemStack.getCount()) {
         j = itemStack.getMaxStackSize() - itemStack.getCount();
      }

      if (j > this.getMaxStackSize() - itemStack.getCount()) {
         j = this.getMaxStackSize() - itemStack.getCount();
      }

      if (j == 0) {
         return i;
      } else {
         i -= j;
         itemStack.grow(j);
         itemStack.setPopTime(5);
         return i;
      }
   }

   public int getOccupiedSlotWithRoomForStack(ItemStack stack) {
      if (this.canStackAddMore(this.getItem(this.selectedSlot), stack)) {
         return this.selectedSlot;
      } else if (this.canStackAddMore(this.getItem(40), stack)) {
         return 40;
      } else {
         for (int i = 0; i < this.main.size(); i++) {
            if (this.canStackAddMore((ItemStack)this.main.get(i), stack)) {
               return i;
            }
         }

         return -1;
      }
   }

   public void updateItems() {
      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         for (int i = 0; i < defaultedList.size(); i++) {
            if (!((ItemStack)defaultedList.get(i)).isEmpty()) {
               ((ItemStack)defaultedList.get(i)).inventoryTick(this.player.level(), this.player, i, this.selectedSlot == i);
            }
         }
      }
   }

   public boolean insertStack(ItemStack stack) {
      return this.insertStack(-1, stack);
   }

   public boolean insertStack(int slot, ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         try {
            if (stack.isDamaged()) {
               if (slot == -1) {
                  slot = this.getEmptySlot();
               }

               if (slot >= 0) {
                  this.main.set(slot, stack.copyAndClear());
                  ((ItemStack)this.main.get(slot)).setPopTime(5);
                  return true;
               } else {
                  return false;
               }
            } else {
               int i;
               do {
                  i = stack.getCount();
                  if (slot == -1) {
                     stack.setCount(this.addStack(stack));
                  } else {
                     stack.setCount(this.addStack(slot, stack));
                  }
               } while (!stack.isEmpty() && stack.getCount() < i);

               return stack.getCount() < i;
            }
         } catch (Throwable var6) {
            CrashReport crashReport = CrashReport.forThrowable(var6, "Adding item to inventory");
            CrashReportCategory crashReportSection = crashReport.addCategory("Item being added");
            crashReportSection.setDetail("Item ID", Item.getId(stack.getItem()));
            crashReportSection.setDetail("Item data", stack.getDamageValue());
            crashReportSection.setDetail("Item name", () -> stack.getHoverName().getString());
            throw new ReportedException(crashReport);
         }
      }
   }

   public ItemStack removeItem(int slot, int amount) {
      List<ItemStack> list = null;

      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         if (slot < defaultedList.size()) {
            list = defaultedList;
            break;
         }

         slot -= defaultedList.size();
      }

      return list != null && !list.get(slot).isEmpty() ? ContainerHelper.removeItem(list, slot, amount) : ItemStack.EMPTY;
   }

   public void removeOne(ItemStack stack) {
      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         for (int i = 0; i < defaultedList.size(); i++) {
            if (defaultedList.get(i) == stack) {
               defaultedList.set(i, ItemStack.EMPTY);
               break;
            }
         }
      }
   }

   public ItemStack removeItemNoUpdate(int slot) {
      NonNullList<ItemStack> defaultedList = null;

      for (NonNullList<ItemStack> defaultedList2 : this.combinedInventory) {
         if (slot < defaultedList2.size()) {
            defaultedList = defaultedList2;
            break;
         }

         slot -= defaultedList2.size();
      }

      if (defaultedList != null && !((ItemStack)defaultedList.get(slot)).isEmpty()) {
         ItemStack itemStack = (ItemStack)defaultedList.get(slot);
         defaultedList.set(slot, ItemStack.EMPTY);
         return itemStack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public void setItem(int slot, ItemStack stack) {
      NonNullList<ItemStack> defaultedList = null;

      for (NonNullList<ItemStack> defaultedList2 : this.combinedInventory) {
         if (slot < defaultedList2.size()) {
            defaultedList = defaultedList2;
            break;
         }

         slot -= defaultedList2.size();
      }

      if (defaultedList != null) {
         defaultedList.set(slot, stack);
      }
   }

   public float getBlockBreakingSpeed(BlockState block) {
      return ((ItemStack)this.main.get(this.selectedSlot)).getDestroySpeed(block);
   }

   private void writeItemTag(HolderLookup.Provider levelRegistryAccess, ItemStack stack, ListTag nbtList, int index){
      if(stack.isEmpty()){
         return;
      }
      LOGGER.info("Writing itemTag={}");
      CompoundTag itemTag = new CompoundTag();
      stack.save(levelRegistryAccess, itemTag);

      if (!itemTag.contains("id")) {
         ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (key != null) {
            itemTag.putString("id", key.toString());
            itemTag.putByte("Count", (byte) stack.getCount());
         } else{
            LOGGER.info("ERR writing item: key={}, itemTag={} ", key, itemTag);
         }
      }
      itemTag.putByte("Slot", (byte)index);
      nbtList.add(itemTag);
      LOGGER.info("Done writing itemTag={}");
   }

   public ListTag writeNbt(HolderLookup.Provider levelRegistryAccess, ListTag nbtList) {
      LOGGER.info("writeNBT inventory");
      for (int i = 0; i < this.main.size(); i++) {
         ItemStack stack = this.main.get(i);
         writeItemTag(levelRegistryAccess, stack, nbtList, i);
      }
   
      // armor
      for (int ix = 0; ix < this.armor.size(); ix++) {
         ItemStack stack = this.armor.get(ix);
         writeItemTag(levelRegistryAccess, stack, nbtList, ix + 100);
      }
   
      // offHand
      for (int ixx = 0; ixx < this.offHand.size(); ixx++) {
         ItemStack stack = this.offHand.get(ixx);
         writeItemTag(levelRegistryAccess, stack, nbtList, ixx + 150);
      }
   
      LOGGER.info("DONE writeNBT inventory");
      return nbtList;
   }
   public void readNbt(HolderLookup.Provider levelRegistryAccess, ListTag nbtList) {
      LOGGER.info("writeNBT inventory");
      this.main.clear();
      this.armor.clear();
      this.offHand.clear();
      
      for (int i = 0; i < nbtList.size(); i++) {
         CompoundTag nbtCompound = nbtList.getCompound(i);
         int j = nbtCompound.getByte("Slot") & 255;
         ItemStack itemStack = ItemStack.parseOptional(levelRegistryAccess, nbtCompound);
         if (!itemStack.isEmpty()) {
            LOGGER.info("Reading stack {}", itemStack);
            if (j >= 0 && j < this.main.size()) {
               this.main.set(j, itemStack);
            } else if (j >= 100 && j < this.armor.size() + 100) {
               this.armor.set(j - 100, itemStack);
            } else if (j >= 150 && j < this.offHand.size() + 150) {
               this.offHand.set(j - 150, itemStack);
            }
         }
      }
      LOGGER.info("DONE writeNBT inventory");
   }

   public int getContainerSize() {
      return this.main.size() + this.armor.size() + this.offHand.size();
   }

   public boolean isEmpty() {
      for (ItemStack itemStack : this.main) {
         if (!itemStack.isEmpty()) {
            return false;
         }
      }

      for (ItemStack itemStackx : this.armor) {
         if (!itemStackx.isEmpty()) {
            return false;
         }
      }

      for (ItemStack itemStackxx : this.offHand) {
         if (!itemStackxx.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public ItemStack getItem(int slot) {
      List<ItemStack> list = null;

      for (NonNullList<ItemStack> defaultedList : this.combinedInventory) {
         if (slot < defaultedList.size()) {
            list = defaultedList;
            break;
         }

         slot -= defaultedList.size();
      }

      return list == null ? ItemStack.EMPTY : list.get(slot);
   }

   public Component getName() {
      return Component.translatable("container.inventory");
   }

   public ItemStack getArmorStack(int slot) {
      return (ItemStack)this.armor.get(slot);
   }

   public void damageArmor(DamageSource damageSource, float amount, int[] slots) {
      if (!(amount <= 0.0F)) {
         amount /= 4.0F;
         if (amount < 1.0F) {
            amount = 1.0F;
         }

         for (int i : slots) {
            ItemStack itemStack = (ItemStack)this.armor.get(i);
            if ((!damageSource.is(DamageTypeTags.IS_FIRE) || !itemStack.getItem().components().has(DataComponents.FIRE_RESISTANT)) && itemStack.getItem() instanceof ArmorItem) {
               itemStack.hurtAndBreak((int)amount, this.player, player.getEquipmentSlotForItem(itemStack));
            }
         }
      }
   }

   public void dropAll() {
      for (List<ItemStack> list : this.combinedInventory) {
         for (int i = 0; i < list.size(); i++) {
            ItemStack itemStack = list.get(i);
            if (!itemStack.isEmpty()) {
               this.player.spawnAtLocation(itemStack);
               list.set(i, ItemStack.EMPTY);
            }
         }
      }
   }

   public void setChanged() {
      this.changeCount++;
   }

   public int getChangeCount() {
      return this.changeCount;
   }

   public boolean stillValid(Player player) {
      return this.player.isRemoved() ? false : !(player.distanceToSqr(this.player) > 64.0);
   }

   public boolean contains(ItemStack stack) {
      for (List<ItemStack> list : this.combinedInventory) {
         for (ItemStack itemStack : list) {
            if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack)) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean contains(TagKey<Item> key) {
      for (List<ItemStack> list : this.combinedInventory) {
         for (ItemStack itemStack : list) {
            if (!itemStack.isEmpty() && itemStack.is(key)) {
               return true;
            }
         }
      }

      return false;
   }

   public void clone(LivingEntityInventory other) {
      for (int i = 0; i < this.getContainerSize(); i++) {
         this.setItem(i, other.getItem(i));
      }

      this.selectedSlot = other.selectedSlot;
   }

   public void clearContent() {
      for (List<ItemStack> list : this.combinedInventory) {
         list.clear();
      }
   }

   public void populateRecipeFinder(StackedContents finder) {
      for (ItemStack itemStack : this.main) {
         finder.accountSimpleStack(itemStack);
      }
   }

   public ItemStack dropSelectedItem(boolean entireStack) {
      ItemStack itemStack = this.getMainHandStack();
      return itemStack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selectedSlot, entireStack ? itemStack.getCount() : 1);
   }
}
