package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.multiversion.item.ItemVer;
import com.player2.playerengine.tasks.CraftInInventoryTask;
import com.player2.playerengine.tasks.container.CraftInTableTask;
import com.player2.playerengine.tasks.container.SmeltInSmokerTask;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.SmeltTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.time.TimerGame;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

public class CollectFoodTask extends Task {
   public static final CollectFoodTask.CookableFoodTarget[] COOKABLE_FOODS = new CollectFoodTask.CookableFoodTarget[]{
      new CollectFoodTask.CookableFoodTarget("beef", Cow.class),
      new CollectFoodTask.CookableFoodTarget("porkchop", Pig.class),
      new CollectFoodTask.CookableFoodTarget("chicken", Chicken.class),
      new CollectFoodTask.CookableFoodTarget("mutton", Sheep.class),
      new CollectFoodTask.CookableFoodTarget("rabbit", Rabbit.class)
   };
   public static final Item[] ITEMS_TO_PICK_UP = new Item[]{
      Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.BREAD, Items.BAKED_POTATO
   };
   public static final CollectFoodTask.CropTarget[] CROPS = new CollectFoodTask.CropTarget[]{
      new CollectFoodTask.CropTarget(Items.WHEAT, Blocks.WHEAT), new CollectFoodTask.CropTarget(Items.CARROT, Blocks.CARROTS)
   };
   private final double unitsNeeded;
   private final TimerGame checkNewOptionsTimer = new TimerGame(10.0);
   private Task currentResourceTask = null;

   public CollectFoodTask(double unitsNeeded) {
      this.unitsNeeded = unitsNeeded;
   }

   @Override
   protected void onStart() {
      this.controller.getBehaviour().push();
      this.controller.getBehaviour().addProtectedItems(ITEMS_TO_PICK_UP);
      this.controller.getBehaviour().addProtectedItems(Items.HAY_BLOCK, Items.SWEET_BERRIES);
   }

   @Override
   protected Task onTick() {
      blackListChickenJockeys(this.controller);
      blacklistPillagerHayBales(this.controller);
      SmeltTarget toSmelt = this.getBestSmeltTarget(this.controller);
      if (toSmelt != null) {
         this.setDebugState("Smelting food");
         return new SmeltInSmokerTask(toSmelt);
      } else {
         if (this.checkNewOptionsTimer.elapsed()) {
            this.checkNewOptionsTimer.reset();
            this.currentResourceTask = null;
         }

         if (this.currentResourceTask != null
            && this.currentResourceTask.isActive()
            && !this.currentResourceTask.isFinished()
            && !this.currentResourceTask.thisOrChildAreTimedOut()) {
            return this.currentResourceTask;
         } else {
            double potentialFood = StorageHelper.calculateInventoryFoodScore(this.controller);
            if (potentialFood >= this.unitsNeeded) {
               if (this.controller.getItemStorage().getItemCount(Items.HAY_BLOCK) >= 1) {
                  this.setDebugState("Crafting wheat from hay");
                  this.currentResourceTask = new CraftInInventoryTask(
                     new RecipeTarget(Items.WHEAT, 9, CraftingRecipe.newShapedRecipe("wheat", new ItemTarget[]{new ItemTarget(Items.HAY_BLOCK, 1)}, 9))
                  );
                  return this.currentResourceTask;
               }

               if (this.controller.getItemStorage().getItemCount(Items.WHEAT) >= 3) {
                  this.setDebugState("Crafting bread");
                  this.currentResourceTask = new CraftInTableTask(
                     new RecipeTarget(Items.BREAD, 1, CraftingRecipe.newShapedRecipe("bread", new ItemTarget[]{new ItemTarget(Items.WHEAT, 3)}, 1))
                  );
                  return this.currentResourceTask;
               }
            }

            for (Item item : ITEMS_TO_PICK_UP) {
               if (this.controller.getEntityTracker().itemDropped(item)) {
                  this.setDebugState("Picking up high-value food: " + item.getDescriptionId());
                  this.currentResourceTask = new PickupDroppedItemTask(new ItemTarget(item), true);
                  return this.currentResourceTask;
               }
            }

            for (CollectFoodTask.CookableFoodTarget cookable : COOKABLE_FOODS) {
               if (this.controller.getEntityTracker().itemDropped(cookable.getRaw(), cookable.getCooked())) {
                  this.setDebugState("Picking up dropped meat");
                  this.currentResourceTask = new PickupDroppedItemTask(new ItemTarget(cookable.getRaw(), cookable.getCooked()), true);
                  return this.currentResourceTask;
               }
            }

            if (this.controller.getBlockScanner().anyFound(Blocks.HAY_BLOCK)) {
               this.setDebugState("Collecting hay bales");
               this.currentResourceTask = new MineAndCollectTask(new ItemTarget(Items.HAY_BLOCK, 9999), new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND);
               return this.currentResourceTask;
            } else {
               for (CollectFoodTask.CropTarget crop : CROPS) {
                  if (this.controller.getBlockScanner().anyFound(pos -> isCropMature(this.controller, pos, crop.cropBlock), crop.cropBlock)) {
                     this.setDebugState("Harvesting " + crop.cropItem.getDescriptionId());
                     this.currentResourceTask = new CollectCropTask(new ItemTarget(crop.cropItem, 9999), new Block[]{crop.cropBlock}, crop.cropItem);
                     return this.currentResourceTask;
                  }
               }

               Entity bestEntityToKill = this.getBestAnimalToKill(this.controller);
               if (bestEntityToKill != null) {
                  this.setDebugState("Killing " + bestEntityToKill.getType().getDescription().getString());
                  Item rawFood = Arrays.stream(COOKABLE_FOODS).filter(c -> c.mobToKill == bestEntityToKill.getClass()).findFirst().get().getRaw();
                  this.currentResourceTask = new KillAndLootTask(bestEntityToKill.getClass(), new ItemTarget(rawFood, 1));
                  return this.currentResourceTask;
               } else if (this.controller.getBlockScanner().anyFound(Blocks.SWEET_BERRY_BUSH)) {
                  this.setDebugState("Collecting sweet berries");
                  this.currentResourceTask = new MineAndCollectTask(
                     new ItemTarget(Items.SWEET_BERRIES, 9999), new Block[]{Blocks.SWEET_BERRY_BUSH}, MiningRequirement.HAND
                  );
                  return this.currentResourceTask;
               } else {
                  this.setDebugState("Searching for food source...");
                  return new TimeoutWanderTask();
               }
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   public boolean isFinished() {
      return StorageHelper.calculateInventoryFoodScore(this.controller) >= this.unitsNeeded;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof CollectFoodTask task ? task.unitsNeeded == this.unitsNeeded : false;
   }

   @Override
   protected String toDebugString() {
      return "Collecting " + this.unitsNeeded + " food units.";
   }

   private SmeltTarget getBestSmeltTarget(PlayerEngineController controller) {
      for (CollectFoodTask.CookableFoodTarget cookable : COOKABLE_FOODS) {
         int rawCount = controller.getItemStorage().getItemCount(cookable.getRaw());
         if (rawCount > 0) {
            return new SmeltTarget(new ItemTarget(cookable.getCooked(), rawCount), new ItemTarget(cookable.getRaw(), rawCount));
         }
      }

      return null;
   }

   private Entity getBestAnimalToKill(PlayerEngineController controller) {
      double bestScore = -1.0;
      Entity bestEntity = null;
      Predicate<Entity> notBaby = entity -> entity instanceof LivingEntity && !((LivingEntity)entity).isBaby();

      for (CollectFoodTask.CookableFoodTarget cookable : COOKABLE_FOODS) {
         if (controller.getEntityTracker().entityFound(cookable.mobToKill)) {
            Optional<Entity> nearest = controller.getEntityTracker().getClosestEntity(controller.getEntity().position(), notBaby, cookable.mobToKill);
            if (nearest.isPresent()) {
               double distanceSq = nearest.get().position().distanceToSqr(controller.getEntity().position());
               if (distanceSq != 0.0) {
                  double score = cookable.getCookedUnits() / distanceSq;
                  if (score > bestScore) {
                     bestScore = score;
                     bestEntity = nearest.get();
                  }
               }
            }
         }
      }

      return bestEntity;
   }

   public static void blackListChickenJockeys(PlayerEngineController controller) {
      for (Chicken chicken : controller.getEntityTracker().getTrackedEntities(Chicken.class)) {
         if (chicken.isVehicle()) {
            controller.getEntityTracker().requestEntityUnreachable(chicken);
         }
      }
   }

   private static void blacklistPillagerHayBales(PlayerEngineController controller) {
      for (BlockPos pos : controller.getBlockScanner().getKnownLocations(Blocks.HAY_BLOCK)) {
         if (controller.getWorld().getBlockState(pos.above()).is(Blocks.CARVED_PUMPKIN)) {
            controller.getBlockScanner().requestBlockUnreachable(pos, 0);
         }
      }
   }

   private static boolean isCropMature(PlayerEngineController controller, BlockPos pos, Block block) {
      if (!controller.getChunkTracker().isChunkLoaded(pos)) {
         return false;
      } else {
         return controller.getWorld().getBlockState(pos).getBlock() instanceof CropBlock crop ? crop.isMaxAge(controller.getWorld().getBlockState(pos)) : true;
      }
   }

   public static double calculateFoodPotential(PlayerEngineController mod) {
      double potentialFood = 0.0;

      for (ItemStack food : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
         potentialFood += CollectMeatTask.getFoodPotential(food);
      }

      int potentialBread = mod.getItemStorage().getItemCount(Items.WHEAT) / 3 + mod.getItemStorage().getItemCount(Items.HAY_BLOCK) * 3;
      return potentialFood + Objects.requireNonNull(ItemVer.getFoodComponent(Items.BREAD)).getHunger() * potentialBread;
   }

   public static class CookableFoodTarget {
      public final String rawFood;
      public final String cookedFood;
      public final Class<? extends Entity> mobToKill;

      public CookableFoodTarget(String rawFood, Class<? extends Entity> mobToKill) {
         this(rawFood, "cooked_" + rawFood, mobToKill);
      }

      public CookableFoodTarget(String rawFood, String cookedFood, Class<? extends Entity> mobToKill) {
         this.rawFood = rawFood;
         this.cookedFood = cookedFood;
         this.mobToKill = mobToKill;
      }

      public Item getRaw() {
         return TaskCatalogue.getItemMatches(this.rawFood)[0];
      }

      public Item getCooked() {
         return TaskCatalogue.getItemMatches(this.cookedFood)[0];
      }

      public int getCookedUnits() {
         return ItemVer.getFoodComponent(this.getCooked()).getHunger();
      }
   }

   public static class CropTarget {
      public final Item cropItem;
      public final Block cropBlock;

      public CropTarget(Item cropItem, Block cropBlock) {
         this.cropItem = cropItem;
         this.cropBlock = cropBlock;
      }
   }
}
