package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.container.SmeltInSmokerTask;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.SmeltTarget;
import com.player2.playerengine.util.time.TimerGame;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CollectMeatTask extends Task {
   public static final CollectFoodTask.CookableFoodTarget[] COOKABLE_MEATS = new CollectFoodTask.CookableFoodTarget[]{
      new CollectFoodTask.CookableFoodTarget("beef", Cow.class),
      new CollectFoodTask.CookableFoodTarget("porkchop", Pig.class),
      new CollectFoodTask.CookableFoodTarget("chicken", Chicken.class),
      new CollectFoodTask.CookableFoodTarget("mutton", Sheep.class),
      new CollectFoodTask.CookableFoodTarget("rabbit", Rabbit.class)
   };
   private static final double NEARBY_PICKUP_RADIUS = 15.0;
   private final double unitsNeeded;
   private final TimerGame checkNewOptionsTimer = new TimerGame(10.0);
   private Task currentResourceTask = null;

   public CollectMeatTask(double unitsNeeded) {
      this.unitsNeeded = unitsNeeded;
   }

   @Override
   protected void onStart() {
      this.controller.getBehaviour().push();

      for (CollectFoodTask.CookableFoodTarget meat : COOKABLE_MEATS) {
         this.controller.getBehaviour().addProtectedItems(meat.getRaw(), meat.getCooked());
      }
   }

   @Override
   protected Task onTick() {
      CollectFoodTask.blackListChickenJockeys(this.controller);
      double potentialFood = calculateFoodPotential(this.controller);
      if (potentialFood >= this.unitsNeeded) {
         SmeltTarget toSmelt = this.getBestSmeltTarget(this.controller);
         if (toSmelt != null) {
            this.setDebugState("Cooking meat");
            return new SmeltInSmokerTask(toSmelt);
         }
      }

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
         Item[] allMeats = Arrays.stream(COOKABLE_MEATS).flatMap(meat -> Stream.of(meat.getRaw(), meat.getCooked())).toArray(Item[]::new);
         Optional<ItemEntity> closestDrop = this.controller.getEntityTracker().getClosestItemDrop(this.controller.getPlayer().position(), allMeats);
         if (closestDrop.isPresent() && closestDrop.get().distanceTo(this.controller.getPlayer()) < 15.0) {
            this.setDebugState("Picking up nearby dropped meat");
            this.currentResourceTask = new PickupDroppedItemTask(new ItemTarget(allMeats, 9999), true);
            return this.currentResourceTask;
         } else {
            Entity bestEntityToKill = this.getBestAnimalToKill(this.controller);
            if (bestEntityToKill != null) {
               this.setDebugState("Hunting " + bestEntityToKill.getType().getDescription().getString());
               Item rawFood = Arrays.stream(COOKABLE_MEATS).filter(c -> c.mobToKill == bestEntityToKill.getClass()).findFirst().get().getRaw();
               this.currentResourceTask = new KillAndLootTask(bestEntityToKill.getClass(), new ItemTarget(rawFood, 1));
               return this.currentResourceTask;
            } else {
               this.setDebugState("Searching for animals...");
               return new TimeoutWanderTask();
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
      double currentFoodScore = 0.0;

      for (CollectFoodTask.CookableFoodTarget meat : COOKABLE_MEATS) {
         currentFoodScore += this.controller.getItemStorage().getItemCount(meat.getCooked()) * meat.getCookedUnits();
      }

      return currentFoodScore >= this.unitsNeeded;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof CollectMeatTask task ? task.unitsNeeded == this.unitsNeeded : false;
   }

   @Override
   protected String toDebugString() {
      return "Collecting " + this.unitsNeeded + " units of meat.";
   }

   private SmeltTarget getBestSmeltTarget(PlayerEngineController controller) {
      for (CollectFoodTask.CookableFoodTarget cookable : COOKABLE_MEATS) {
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

      for (CollectFoodTask.CookableFoodTarget cookable : COOKABLE_MEATS) {
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

   private static double calculateFoodPotential(PlayerEngineController controller) {
      double potentialFood = 0.0;

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         potentialFood += getFoodPotential(stack);
      }

      return potentialFood;
   }

   public static double getFoodPotential(ItemStack food) {
      if (food != null && !food.isEmpty()) {
         int count = food.getCount();

         for (CollectFoodTask.CookableFoodTarget cookable : COOKABLE_MEATS) {
            if (food.getItem() == cookable.getRaw()) {
               return (double)count * cookable.getCookedUnits();
            }

            if (food.getItem() == cookable.getCooked()) {
               return (double)count * cookable.getCookedUnits();
            }
         }

         return 0.0;
      } else {
         return 0.0;
      }
   }
}
