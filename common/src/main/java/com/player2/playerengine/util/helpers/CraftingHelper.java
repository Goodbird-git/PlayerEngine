package com.player2.playerengine.util.helpers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CraftingHelper {
   public static boolean canCraftItemNow(PlayerEngineController mod, Item item) {
      List<ItemStack> inventoryItems = new ArrayList<>();

      for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
         inventoryItems.add(new ItemStack(stack.getItem(), stack.getCount()));
      }

      for (CraftingRecipe recipe : mod.getCraftingRecipeTracker().getRecipeForItem(item)) {
         if (canCraftItemNow(mod, new ArrayList<>(inventoryItems), recipe, new HashSet<>())) {
            return true;
         }
      }

      return false;
   }

   private static boolean canCraftItemNow(PlayerEngineController mod, List<ItemStack> inventoryStacks, CraftingRecipe recipe, HashSet<Item> alreadyChecked) {
      if(mod.getCraftingRecipeTracker()==null || mod.getCraftingRecipeTracker().getRecipeResult(recipe)==null){
          return false;
      }
      Item recipeResult = mod.getCraftingRecipeTracker().getRecipeResult(recipe).getItem();
      if (alreadyChecked.contains(recipeResult)) {
         return false;
      } else {
         alreadyChecked.add(recipeResult);
         ItemTarget[] targets = recipe.getSlots();
         ItemTarget[] arrayOfItemTarget1 = targets;
         int i = targets.length;
         byte b = 0;

         label64:
         while (b < i) {
            ItemTarget itemTarget = arrayOfItemTarget1[b];
            if (itemTarget == ItemTarget.EMPTY) {
               b++;
            } else {
               for (Item item : itemTarget.getMatches()) {
                  for (ItemStack inventoryStack : inventoryStacks) {
                     if (inventoryStack.getItem() == item && inventoryStack.getCount() >= itemTarget.getTargetCount()) {
                        inventoryStack.setCount(inventoryStack.getCount() - itemTarget.getTargetCount());
                        continue label64;
                     }
                  }
               }

               for (Item item : itemTarget.getMatches()) {
                  if (mod.getCraftingRecipeTracker().hasRecipeForItem(item)) {
                     for (CraftingRecipe newRecipe : mod.getCraftingRecipeTracker().getRecipeForItem(item)) {
                        List<ItemStack> inventoryStacksCopy = new ArrayList<>(inventoryStacks);
                        if (canCraftItemNow(mod, inventoryStacksCopy, newRecipe, new HashSet<>(alreadyChecked))) {
                           inventoryStacks = inventoryStacksCopy;
                           ItemStack result = mod.getCraftingRecipeTracker().getRecipeResult(newRecipe);
                           result.setCount(result.getCount() - 1);
                           inventoryStacksCopy.add(result);
                           continue label64;
                        }
                     }
                  }
               }

               return false;
            }
         }

         return true;
      }
   }
}
