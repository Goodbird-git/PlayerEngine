package com.player2.playerengine.tasks;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.tasks.resources.CollectRecipeCataloguedResourcesTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CraftInInventoryTask extends ResourceTask {
   private final RecipeTarget target;
   private final boolean collect;
   private final boolean ignoreUncataloguedSlots;
   private final TimerGame craftTimer = new TimerGame(2.0);
   private boolean isCrafting = false;

   public CraftInInventoryTask(RecipeTarget target, boolean collect, boolean ignoreUncataloguedSlots) {
      super(new ItemTarget(target.getOutputItem(), target.getTargetCount()));
      this.target = target;
      this.collect = collect;
      this.ignoreUncataloguedSlots = ignoreUncataloguedSlots;
      if (target.getRecipe().isBig()) {
         Debug.logError("CraftInInventoryTask was used for a 3x3 recipe. This is not supported. Use CraftInTableTask instead.");
      }
   }

   public CraftInInventoryTask(RecipeTarget target) {
      this(target, true, false);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController controller) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController controller) {
      int targetCount = this.target.getTargetCount();
      Item outputItem = this.target.getOutputItem();
      if (controller.getItemStorage().getItemCount(outputItem) >= targetCount) {
         return null;
      } else if (this.collect && !StorageHelper.hasRecipeMaterialsOrTarget(controller, this.target)) {
         this.setDebugState("Collecting ingredients for " + outputItem.getDescriptionId());
         return new CollectRecipeCataloguedResourcesTask(this.ignoreUncataloguedSlots, this.target);
      } else {
         this.setDebugState("Crafting " + outputItem.getDescriptionId());
         if (!this.isCrafting) {
            this.craftTimer.reset();
            this.isCrafting = true;
         }

         if (!this.craftTimer.elapsed()) {
            return null;
         } else {
            int craftsNeeded = (int)Math.ceil(
               (double)(targetCount - controller.getItemStorage().getItemCount(outputItem)) / this.target.getRecipe().outputCount()
            );
            if (craftsNeeded <= 0) {
               return null;
            } else {
               LivingEntityInventory inventory = ((IInventoryProvider)controller.getEntity()).getLivingInventory();

               for (int i = 0; i < craftsNeeded; i++) {
                  if (!StorageHelper.hasRecipeMaterialsOrTarget(
                     controller, new RecipeTarget(this.target.getOutputItem(), this.target.getRecipe().outputCount(), this.target.getRecipe())
                  )) {
                     Debug.logWarning(
                        "Failed to craft " + outputItem.getDescriptionId() + ", not enough ingredients even though we passed the initial check."
                     );
                     break;
                  }

                  for (ItemTarget ingredient : this.target.getRecipe().getSlots()) {
                     if (ingredient != null && !ingredient.isEmpty()) {
                        inventory.remove(stack -> ingredient.matches(stack.getItem()), ingredient.getTargetCount(), inventory);
                     }
                  }

                  ItemStack result = new ItemStack(this.target.getOutputItem(), this.target.getRecipe().outputCount());
                  inventory.insertStack(result);
                  controller.getItemStorage().registerSlotAction();
               }

               controller.getEntity().swing(InteractionHand.MAIN_HAND);
               return null;
            }
         }
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController controller, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftInInventoryTask task ? task.target.equals(this.target) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Craft in inventory: " + this.target.getOutputItem().getDescriptionId();
   }

   public RecipeTarget getRecipeTarget() {
      return this.target;
   }
}
