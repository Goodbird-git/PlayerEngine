package com.player2.playerengine.multiversion;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

public class ToolMaterialVer {
   public static HolderSet<Block> getMineableBlocks(Item item){
      for(Tool.Rule rule : item.components().get(DataComponents.TOOL).rules()){
         if(rule.correctForDrops().isPresent() && rule.correctForDrops().get()){
            return rule.blocks();
         }
      }
      return HolderSet.empty();
   }

   public static HolderSet<Block> getMiningLevel(ItemStack item) {
      return getMineableBlocks(item.getItem());
   }

   public static HolderSet<Block> getMiningLevel(Item material) {
     return getMineableBlocks(material);
   }
}
