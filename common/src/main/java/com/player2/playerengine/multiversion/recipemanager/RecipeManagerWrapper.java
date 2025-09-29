package com.player2.playerengine.multiversion.recipemanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

public class RecipeManagerWrapper {
   private final RecipeManager recipeManager;

   public static RecipeManagerWrapper of(RecipeManager recipeManager) {
      return recipeManager == null ? null : new RecipeManagerWrapper(recipeManager);
   }

   private RecipeManagerWrapper(RecipeManager recipeManager) {
      this.recipeManager = recipeManager;
   }

   public Collection<WrappedRecipeEntry> values() {
      List<WrappedRecipeEntry> result = new ArrayList<>();

      for (RecipeHolder<?> id : this.recipeManager.getRecipes().stream().toList()) {
         result.add(new WrappedRecipeEntry(id.id().location(), this.recipeManager.byKey(id.id()).get().value()));
      }

      return result;
   }
}
