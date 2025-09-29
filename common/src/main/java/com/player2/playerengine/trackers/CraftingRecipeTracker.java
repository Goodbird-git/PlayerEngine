package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.multiversion.recipemanager.RecipeManagerWrapper;
import com.player2.playerengine.multiversion.recipemanager.WrappedRecipeEntry;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.RecipeTarget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

public class CraftingRecipeTracker extends Tracker {
   private final HashMap<Item, List<CraftingRecipe>> itemRecipeMap = new HashMap<>();
   private final HashMap<CraftingRecipe, ItemStack> recipeResultMap = new HashMap<>();
   private boolean shouldRebuild = true;

   public CraftingRecipeTracker(TrackerManager manager) {
      super(manager);
   }

   public List<CraftingRecipe> getRecipeForItem(Item item) {
      this.ensureUpdated();
      if (!this.hasRecipeForItem(item)) {
         this.mod.logWarning("trying to access recipe for unknown item: " + item);
         return null;
      } else {
         return this.itemRecipeMap.get(item);
      }
   }

   public CraftingRecipe getFirstRecipeForItem(Item item) {
      this.ensureUpdated();
      if (!this.hasRecipeForItem(item)) {
         this.mod.logWarning("trying to access recipe for unknown item: " + item);
         return null;
      } else {
         return this.itemRecipeMap.get(item).get(0);
      }
   }

   public List<RecipeTarget> getRecipeTarget(Item item, int targetCount) {
      this.ensureUpdated();
      List<RecipeTarget> targets = new ArrayList<>();

      for (CraftingRecipe recipe : this.getRecipeForItem(item)) {
         targets.add(new RecipeTarget(item, targetCount, recipe));
      }

      return targets;
   }

   public RecipeTarget getFirstRecipeTarget(Item item, int targetCount) {
      this.ensureUpdated();
      return new RecipeTarget(item, targetCount, this.getFirstRecipeForItem(item));
   }

   public boolean hasRecipeForItem(Item item) {
      this.ensureUpdated();
      return this.itemRecipeMap.containsKey(item);
   }

   public ItemStack getRecipeResult(CraftingRecipe recipe) {
      this.ensureUpdated();
      if (!this.hasRecipe(recipe)) {
         this.mod.logWarning("Trying to get result for unknown recipe: " + recipe);
         return null;
      } else {
         ItemStack result = this.recipeResultMap.get(recipe);
         return new ItemStack(result.getItem(), result.getCount());
      }
   }

   public boolean hasRecipe(CraftingRecipe recipe) {
      this.ensureUpdated();
      return this.recipeResultMap.containsKey(recipe);
   }

   @Override
   protected void updateState() {
      if (this.shouldRebuild) {
         if (PlayerEngineController.inGame()) {
            RecipeManagerWrapper recipeManager = RecipeManagerWrapper.of(this.mod.getWorld().recipeAccess());

            for (WrappedRecipeEntry recipe : recipeManager.values()) {
               Recipe<?> recipe1 = recipe.value();
               if (recipe1 instanceof CraftingRecipe) {
                  net.minecraft.world.item.crafting.CraftingRecipe craftingRecipe = (net.minecraft.world.item.crafting.CraftingRecipe)recipe1;
                  if (!(craftingRecipe instanceof CustomRecipe)) {
                     ItemStack result = new ItemStack(craftingRecipe.assemble(null, null).getItem(), craftingRecipe.assemble(null, null).getCount());
                     Item[][] altoclefRecipeItems = getShapedCraftingRecipe(craftingRecipe.placementInfo().ingredients());
                     CraftingRecipe altoclefRecipe = CraftingRecipe.newShapedRecipe(altoclefRecipeItems, result.getCount());
                     if (this.itemRecipeMap.containsKey(result.getItem())) {
                        this.itemRecipeMap.get(result.getItem()).add(altoclefRecipe);
                     } else {
                        List<CraftingRecipe> recipes = new ArrayList<>();
                        recipes.add(altoclefRecipe);
                        this.itemRecipeMap.put(result.getItem(), recipes);
                     }

                     this.recipeResultMap.put(altoclefRecipe, result);
                  }
               }
            }

            this.itemRecipeMap.replaceAll((k, v) -> Collections.unmodifiableList((List<? extends CraftingRecipe>)v));
            this.shouldRebuild = false;
         }
      }
   }

   private static Item[][] getShapedCraftingRecipe(List<Ingredient> ingredients) {
      Item[][] result = new Item[9][];
      int x = 0;

      for (Ingredient ingredient : ingredients) {
         Item[] items = ingredient.items().map(Holder::value).distinct().toArray(Item[]::new);

         if (items.length != 0) {
            Item[] var10000 = new Item[]{items[0]};
            result[x] = new Item[1];
         } else {
            result[x] = null;
         }

         x++;
      }

      return result;
   }

   @Override
   protected void reset() {
      this.shouldRebuild = true;
      this.itemRecipeMap.clear();
      this.recipeResultMap.clear();
   }

   @Override
   protected boolean isDirty() {
      return this.shouldRebuild;
   }
}
