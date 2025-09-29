package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.entity.ShearSheepTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.Arrays;
import java.util.HashSet;

import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class CollectWoolTask extends ResourceTask {
   private final int count;
   private final HashSet<DyeColor> colors;
   private final Item[] wools;

   public CollectWoolTask(DyeColor[] colors, int count) {
      super(new ItemTarget(ItemHelper.WOOL, count));
      this.colors = new HashSet<>(Arrays.asList(colors));
      this.count = count;
      this.wools = getWoolColorItems(colors);
   }

   public CollectWoolTask(DyeColor color, int count) {
      this(new DyeColor[]{color}, count);
   }

   public CollectWoolTask(int count) {
      this(DyeColor.values(), count);
   }

   private static Item[] getWoolColorItems(DyeColor[] colors) {
      Item[] result = new Item[colors.length];

      for (int i = 0; i < result.length; i++) {
         result[i] = ItemHelper.getColorfulItems(colors[i]).wool;
      }

      return result;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      Block[] woolBlocks = ItemHelper.itemsToBlocks(this.wools);
      if (mod.getBlockScanner().anyFound(woolBlocks)) {
         return new MineAndCollectTask(new ItemTarget(this.wools), woolBlocks, MiningRequirement.HAND);
      } else if (this.isInWrongDimension(mod) && !mod.getEntityTracker().entityFound(Sheep.class)) {
         return this.getToCorrectDimensionTask(mod);
      } else {
         return (Task)(mod.getItemStorage().hasItem(Items.SHEARS)
            ? new ShearSheepTask()
            : new KillAndLootTask(
               Sheep.class,
               entity -> !(entity instanceof Sheep sheep) ? false : this.colors.contains(sheep.getColor()) && !sheep.isSheared(),
               new ItemTarget(this.wools, this.count)
            ));
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectWoolTask && ((CollectWoolTask)other).count == this.count;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect " + this.count + " wool.";
   }
}
