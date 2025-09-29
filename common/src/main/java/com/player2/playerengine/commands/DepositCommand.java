package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.tasks.container.StoreInAnyContainerTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class DepositCommand extends Command {
   private static final int NEARBY_RANGE = 20;
   private static final Block[] VALID_CONTAINERS = Stream.concat(
         Arrays.stream(new Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL}), Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.SHULKER_BOXES))
      )
      .toArray(Block[]::new);

   public DepositCommand() throws CommandException {
      super(
         "deposit",
         "Deposit our items to a nearby chest, making a chest if one doesn't exist. Pass no arguments to depisot ALL items. Examples: `deposit` deposits ALL items, `deposit diamond 2` deposits 2 diamonds.",
         new Arg<>(ItemList.class, "items (empty for ALL non gear items)", null, 0, false)
      );
   }

   public static ItemTarget[] getAllNonEquippedOrToolItemsAsTarget(PlayerEngineController mod) {
      return StorageHelper.getAllInventoryItemsAsTargets(mod, slot -> {
         if (slot.getInventory().size() == 4) {
            return false;
         } else {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
               Item item = stack.getItem();
               return !(stack.has(DataComponents.TOOL));
            } else {
               return false;
            }
         }
      });
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      ItemList itemList = parser.get(ItemList.class);
      if (itemList != null) {
         Map<String, Integer> countsLeftover = new HashMap<>();

         for (ItemTarget itemTarget : itemList.items) {
            String name = itemTarget.getCatalogueName();
            countsLeftover.put(name, countsLeftover.getOrDefault(name, 0) + itemTarget.getTargetCount());
         }

         for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
            ItemStack stack = mod.getInventory().getItem(i);
            if (!stack.isEmpty()) {
               String name = ItemHelper.stripItemName(stack.getItem());
               int count = stack.getCount();
               if (countsLeftover.containsKey(name)) {
                  countsLeftover.put(name, countsLeftover.get(name) - count);
                  if (countsLeftover.get(name) <= 0) {
                     countsLeftover.remove(name);
                  }
               }
            }
         }

         if (countsLeftover.size() != 0) {
            String leftover = String.join(",", countsLeftover.entrySet().stream().map(e -> e.getKey() + " x " + e.getValue().toString()).toList());
            mod.log("Insuffucient items in inventory to deposit. We still need: " + leftover + ".");
            this.finish();
            return;
         }
      }

      ItemTarget[] items;
      if (itemList == null) {
         items = getAllNonEquippedOrToolItemsAsTarget(mod);
      } else {
         items = itemList.items;
      }

      mod.runUserTask(new StoreInAnyContainerTask(false, items), () -> this.finish());
   }
}
