package com.player2.playerengine.util.helpers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.multiversion.item.ItemVer;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.Pair;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.utils.ToolSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class StorageHelper {
   public static ItemStack getItemStackInSlot(Slot slot) {
      return slot != null && !slot.equals(Slot.UNDEFINED) ? slot.getStack() : ItemStack.EMPTY;
   }

   public static ItemStack getItemStackInCursorSlot(PlayerEngineController controller) {
      return controller.getSlotHandler().getCursorStack();
   }

   public static boolean isArmorEquipped(PlayerEngineController controller, Item... any) {
      for (Item item : any) {
         if (item.components().has(DataComponents.EQUIPPABLE)) {
            ItemStack equippedStack = controller.getEntity().getItemBySlot(item.components().get(DataComponents.EQUIPPABLE).slot());
            if (equippedStack.is(item)) {
               return true;
            }
         } else if (item instanceof ShieldItem) {
            ItemStack equippedStack = controller.getEntity().getItemBySlot(EquipmentSlot.OFFHAND);
            if (equippedStack.is(item)) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean isArmorEquippedAll(PlayerEngineController controller, Item... all) {
      return Arrays.stream(all).allMatch(item -> isArmorEquipped(controller, item));
   }

   public static boolean isItemInOffhand(PlayerEngineController controller, Item item) {
      return controller.getEntity().getItemBySlot(EquipmentSlot.OFFHAND).is(item);
   }

   public static boolean isEquipped(PlayerEngineController controller, Item... items) {
      LivingEntityInventory inv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
      return Arrays.stream(items).anyMatch(item -> inv.getMainHandStack().is(item));
   }

   public static MiningRequirement getCurrentMiningRequirement(PlayerEngineController controller) {
      MiningRequirement[] order = new MiningRequirement[]{MiningRequirement.DIAMOND, MiningRequirement.IRON, MiningRequirement.STONE, MiningRequirement.WOOD};

      for (MiningRequirement check : order) {
         if (miningRequirementMet(controller, check)) {
            return check;
         }
      }

      return MiningRequirement.HAND;
   }

   private static boolean h(PlayerEngineController controller, boolean inventoryOnly, Item... items) {
      return inventoryOnly ? controller.getItemStorage().hasItemInventoryOnly(items) : controller.getItemStorage().hasItem(items);
   }

   private static boolean miningRequirementMetInner(PlayerEngineController controller, boolean inventoryOnly, MiningRequirement requirement) {
      return switch (requirement) {
         case HAND -> true;
         case WOOD -> h(controller, inventoryOnly, Items.WOODEN_PICKAXE)
            || h(controller, inventoryOnly, Items.STONE_PICKAXE)
            || h(controller, inventoryOnly, Items.IRON_PICKAXE)
            || h(controller, inventoryOnly, Items.GOLDEN_PICKAXE)
            || h(controller, inventoryOnly, Items.DIAMOND_PICKAXE)
            || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         case STONE -> h(controller, inventoryOnly, Items.STONE_PICKAXE)
            || h(controller, inventoryOnly, Items.IRON_PICKAXE)
            || h(controller, inventoryOnly, Items.GOLDEN_PICKAXE)
            || h(controller, inventoryOnly, Items.DIAMOND_PICKAXE)
            || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         case IRON -> h(controller, inventoryOnly, Items.IRON_PICKAXE)
            || h(controller, inventoryOnly, Items.GOLDEN_PICKAXE)
            || h(controller, inventoryOnly, Items.DIAMOND_PICKAXE)
            || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         case DIAMOND -> h(controller, inventoryOnly, Items.DIAMOND_PICKAXE) || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         default -> {
            Debug.logError("You missed a spot");
            yield false;
         }
      };
   }

   public static boolean miningRequirementMet(PlayerEngineController controller, MiningRequirement requirement) {
      return miningRequirementMetInner(controller, false, requirement);
   }

   public static boolean miningRequirementMetInventory(PlayerEngineController controller, MiningRequirement requirement) {
      return miningRequirementMetInner(controller, true, requirement);
   }

   public static Optional<Slot> getBestToolSlot(PlayerEngineController controller, BlockState state) {
      int bestSlot = new ToolSet(controller.getPlayer()).getBestSlot(state.getBlock(), false);
      return Optional.of(new Slot(controller.getInventory().main, bestSlot));
   }

   public static boolean shouldSaveStack(PlayerEngineController controller, Block block, ItemStack stack) {
      return false;
   }

   public static Optional<Slot> getGarbageSlot(PlayerEngineController controller) {
      LivingEntityInventory inventory = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
      int bestScore = Integer.MIN_VALUE;
      Slot bestSlot = null;

      for (int i = 0; i < inventory.main.size(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty() && ItemHelper.canThrowAwayStack(controller, stack)) {
            int score = 0;
            if (controller.getModSettings().isThrowaway(stack.getItem())) {
               score += 1000;
            }

            if (stack.getCount() < stack.getMaxStackSize()) {
               score += 100;
            }

            score -= stack.getCount();
            if (score > bestScore) {
               bestScore = score;
               bestSlot = new Slot(inventory.main, i);
            }
         }
      }

      return Optional.ofNullable(bestSlot);
   }

   public static int getBuildingMaterialCount(PlayerEngineController controller) {
      return controller.getItemStorage()
         .getItemCount(
            Arrays.stream(controller.getModSettings().getThrowawayItems(controller, true))
               .filter(item -> item instanceof BlockItem && !item.equals(Items.GRAVEL) && !item.equals(Items.SAND))
               .toArray(Item[]::new)
         );
   }

   public static boolean itemTargetsMet(PlayerEngineController controller, ItemTarget... targetsToMeet) {
      return Arrays.stream(targetsToMeet).allMatch(target -> controller.getItemStorage().getItemCount(target.getMatches()) >= target.getTargetCount());
   }

   public static boolean itemTargetsMetInventory(PlayerEngineController controller, ItemTarget... targetsToMeet) {
      return Arrays.stream(targetsToMeet)
         .allMatch(target -> controller.getItemStorage().getItemCountInventoryOnly(target.getMatches()) >= target.getTargetCount());
   }

   public static boolean hasRecipeMaterialsOrTarget(PlayerEngineController controller, RecipeTarget... targets) {
      HashMap<Item, Pair<ItemTarget, Integer>> required = new HashMap<>();

      for (RecipeTarget target : targets) {
         int craftsNeeded = (int)Math.ceil(
            (double)(target.getTargetCount() - controller.getItemStorage().getItemCount(target.getOutputItem())) / target.getRecipe().outputCount()
         );
         if (craftsNeeded > 0) {
            for (ItemTarget ingredient : target.getRecipe().getSlots()) {
               if (ingredient != null && !ingredient.isEmpty()) {
                  Item item = ingredient.getMatches()[0];
                  required.put(
                     item,
                     new Pair<>(ingredient, required.getOrDefault(item, new Pair<>(ingredient, 0)).getRight() + ingredient.getTargetCount() * craftsNeeded)
                  );
               }
            }
         }
      }

      for (Item item : required.keySet()) {
         Pair<ItemTarget, Integer> req = required.get(item);
         if (controller.getItemStorage().getItemCount(req.getLeft()) < req.getRight()) {
            return false;
         }
      }

      return true;
   }

   public static int calculateInventoryFoodScore(PlayerEngineController controller) {
      int result = 0;

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         if (ItemVer.isFood(stack)) {
            result += Objects.requireNonNull(ItemVer.getFoodComponent(stack.getItem())).getHunger() * stack.getCount();
         }
      }

      return result;
   }

   public static double calculateInventoryFuelCount(PlayerEngineController controller) {
      double result = 0.0;

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         if (controller.getModSettings().isSupportedFuel(stack.getItem())) {
            result += ItemHelper.getFuelAmount(controller.getWorld(), stack.getItem()) * stack.getCount();
         }
      }

      return result;
   }

   public static ItemTarget[] getAllInventoryItemsAsTargets(PlayerEngineController controller, Predicate<Slot> accept) {
      HashMap<Item, Integer> counts = new HashMap<>();
      LivingEntityInventory inv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();

      for (int i = 0; i < inv.main.size(); i++) {
         Slot slot = new Slot(inv.main, i);
         if (accept.test(slot)) {
            ItemStack stack = getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
               counts.put(stack.getItem(), counts.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      }

      Slot offhandSlot = new Slot(inv.offHand, 0);
      if (accept.test(offhandSlot)) {
         ItemStack stack = getItemStackInSlot(offhandSlot);
         if (!stack.isEmpty()) {
            counts.put(stack.getItem(), counts.getOrDefault(stack.getItem(), 0) + stack.getCount());
         }
      }

      for (int ix = 0; ix < inv.armor.size(); ix++) {
         Slot slot = new Slot(inv.armor, ix);
         if (accept.test(slot)) {
            ItemStack stack = getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
               counts.put(stack.getItem(), counts.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      }

      ItemTarget[] results = new ItemTarget[counts.size()];
      int ixx = 0;

      for (Item item : counts.keySet()) {
         results[ixx++] = new ItemTarget(item, counts.get(item));
      }

      return results;
   }

   public static int getNumberOfThrowawayBlocks(PlayerEngineController mod) {
      int totalBlockThrowaways = 0;
      if (!mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod)).isEmpty()) {
         for (Slot slot : mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod))) {
            ItemStack stack = getItemStackInSlot(slot);
            if (ItemHelper.canThrowAwayStack(mod, stack) && stack.getItem() instanceof BlockItem) {
               totalBlockThrowaways += stack.getCount();
            }
         }
      }

      return totalBlockThrowaways;
   }

   public static Optional<Slot> getSlotWithThrowawayBlock(PlayerEngineController mod, boolean limitToHotbar) {
      List<Slot> throwawayBlockItems = new ArrayList<>();
      int totalBlockThrowaways = 0;
      if (!mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod)).isEmpty()) {
         for (Slot slot : mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod))) {
            if (!Slot.isCursor(slot) && (!limitToHotbar || slot.getInventorySlot() <= 8 && slot.getInventorySlot() >= 0)) {
               ItemStack stack = getItemStackInSlot(slot);
               if (ItemHelper.canThrowAwayStack(mod, stack) && stack.getItem() instanceof BlockItem) {
                  totalBlockThrowaways += stack.getCount();
                  throwawayBlockItems.add(slot);
               }
            }
         }
      }

      if (!throwawayBlockItems.isEmpty()) {
         Iterator var7 = throwawayBlockItems.iterator();
         if (var7.hasNext()) {
            Slot throwawayBlockItem = (Slot)var7.next();
            return Optional.ofNullable(throwawayBlockItem);
         }
      }

      return Optional.empty();
   }
}
