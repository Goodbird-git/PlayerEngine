package com.player2.playerengine.tasks.container;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.tasks.movement.GetCloseToBlockTask;
import com.player2.playerengine.tasks.resources.CollectRecipeCataloguedResourcesTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class CraftInTableTask extends ResourceTask {
   private final RecipeTarget[] targets;
   private BlockPos craftingTablePos = null;
   private final TimerGame craftTimer = new TimerGame(2.0);
   private boolean isCrafting = false;

   public CraftInTableTask(RecipeTarget[] targets) {
      super(extractItemTargets(targets));
      this.targets = targets;
   }

   public CraftInTableTask(RecipeTarget target) {
      this(new RecipeTarget[]{target});
   }

   private static ItemTarget[] extractItemTargets(RecipeTarget[] recipeTargets) {
      return Arrays.stream(recipeTargets).map(t -> new ItemTarget(t.getOutputItem(), t.getTargetCount())).toArray(ItemTarget[]::new);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController controller) {
      controller.getBehaviour().push();

      for (RecipeTarget target : this.targets) {
         for (ItemTarget ingredient : target.getRecipe().getSlots()) {
            if (ingredient != null && !ingredient.isEmpty()) {
               controller.getBehaviour().addProtectedItems(ingredient.getMatches());
            }
         }
      }
   }

   @Override
   protected Task onResourceTick(PlayerEngineController controller) {
      boolean allDone = Arrays.stream(this.targets)
         .allMatch(targetx -> controller.getItemStorage().getItemCount(targetx.getOutputItem()) >= targetx.getTargetCount());
      if (allDone) {
         return null;
      } else if (!StorageHelper.hasRecipeMaterialsOrTarget(controller, this.targets)) {
         this.setDebugState("Collecting ingredients");
         return new CollectRecipeCataloguedResourcesTask(false, this.targets);
      } else {
         if (this.craftingTablePos == null || !controller.getWorld().getBlockState(this.craftingTablePos).is(Blocks.CRAFTING_TABLE)) {
            Optional<BlockPos> nearestTable = controller.getBlockScanner().getNearestBlock(Blocks.CRAFTING_TABLE);
            if (nearestTable.isPresent()) {
               this.craftingTablePos = nearestTable.get();
               this.setDebugState("Found crafting table: " + this.craftingTablePos.toShortString());
            } else {
               this.craftingTablePos = null;
               this.setDebugState("Crafting table not found.");
            }
         }

         if (this.craftingTablePos == null) {
            if (controller.getItemStorage().hasItem(Items.CRAFTING_TABLE)) {
               this.setDebugState("Placing crafting table.");
               return new PlaceBlockNearbyTask(Blocks.CRAFTING_TABLE);
            } else {
               this.setDebugState("Obtaining crafting table.");
               return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
            }
         } else if (!this.craftingTablePos
            .closerThan(
               new Vec3i((int)controller.getEntity().position().x, (int)controller.getEntity().position().y, (int)controller.getEntity().position().z), 3.5
            )) {
            this.setDebugState("Going to crafting table at: " + this.craftingTablePos.toShortString());
            return new GetCloseToBlockTask(this.craftingTablePos);
         } else {
            this.setDebugState("Crafting...");
            if (!this.isCrafting) {
               this.craftTimer.reset();
               this.isCrafting = true;
            }

            if (!this.craftTimer.elapsed()) {
               return null;
            } else {
               for (RecipeTarget target : this.targets) {
                  int currentAmount = controller.getItemStorage().getItemCount(target.getOutputItem());
                  if (currentAmount < target.getTargetCount()) {
                     int craftsNeeded = (int)Math.ceil((double)(target.getTargetCount() - currentAmount) / target.getRecipe().outputCount());

                     for (int i = 0; i < craftsNeeded; i++) {
                        if (!StorageHelper.hasRecipeMaterialsOrTarget(
                           controller, new RecipeTarget(target.getOutputItem(), target.getRecipe().outputCount(), target.getRecipe())
                        )) {
                           Debug.logWarning("Not enough ingredients to craft, even though the check passed. Aborting.");
                           return new CollectRecipeCataloguedResourcesTask(false, this.targets);
                        }

                        LivingEntityInventory inventory = ((IInventoryProvider)controller.getEntity()).getLivingInventory();

                        for (ItemTarget ingredient : target.getRecipe().getSlots()) {
                           if (ingredient != null && !ingredient.isEmpty()) {
                              inventory.remove(stack -> ingredient.matches(stack.getItem()), ingredient.getTargetCount(), inventory);
                           }
                        }

                        ItemStack result = new ItemStack(target.getOutputItem(), target.getRecipe().outputCount());
                        inventory.insertStack(result);
                        controller.getItemStorage().registerSlotAction();
                     }
                  }
               }

               controller.getEntity().swing(InteractionHand.MAIN_HAND);
               return null;
            }
         }
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController controller, Task interruptTask) {
      controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftInTableTask task ? Arrays.equals((Object[])task.targets, (Object[])this.targets) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Craft on table: " + Arrays.toString(Arrays.stream(this.targets).map(t -> t.getOutputItem().getDescriptionId()).toArray());
   }

   public RecipeTarget[] getRecipeTargets() {
      return this.targets;
   }
}
