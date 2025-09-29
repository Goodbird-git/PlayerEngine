package com.player2.playerengine.multiversion.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class WorldVer {
   public static boolean isBiomeAtPos(Level world, ResourceKey<Biome> biome, BlockPos pos) {
      Holder<Biome> b = world.getBiome(pos);
      return b.is(biome);
   }

   public static boolean isBiome(Holder<Biome> biome1, ResourceKey<Biome> biome2) {
      return biome1.is(biome2);
   }

   public static int getBottomY(Level world) {
      return world.getMinY();
   }

   public static int getTopY(Level world) {
      return world.getMaxY();
   }

   private static boolean isOutOfHeightLimit(Level world, BlockPos pos) {
      return world.isOutsideBuildHeight(pos);
   }
}
