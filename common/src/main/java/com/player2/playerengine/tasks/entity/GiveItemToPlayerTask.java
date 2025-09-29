package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.BotBehaviour;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.movement.FollowPlayerTask;
import com.player2.playerengine.tasks.movement.RunAwayFromPositionTask;
import com.player2.playerengine.tasks.squashed.CataloguedResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.util.time.TimerGame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class GiveItemToPlayerTask extends Task {
   private final String playerName;
   private final ItemTarget[] targets;
   private final CataloguedResourceTask resourceTask;
   private final List<ItemTarget> throwTarget = new ArrayList<>();
   private boolean droppingItems;
   private Task throwTask;
   private TimerGame throwTimeout = new TimerGame(0.4);

   public GiveItemToPlayerTask(String player, ItemTarget... targets) {
      this.playerName = player;
      this.targets = targets;
      this.resourceTask = TaskCatalogue.getSquashedItemTask(targets);
   }

   @Override
   protected void onStart() {
      this.droppingItems = false;
      this.throwTarget.clear();
      BotBehaviour botBehaviour = this.controller.getBehaviour();
      botBehaviour.push();
      botBehaviour.addProtectedItems(ItemTarget.getMatches(this.targets));
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (this.throwTask != null && this.throwTask.isActive() && !this.throwTask.isFinished()) {
         this.setDebugState("Throwing items");
         return this.throwTask;
      } else {
         Optional<Vec3> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(this.playerName);
         if (lastPos.isEmpty()) {
            String nearbyUsernames = String.join(",", mod.getEntityTracker().getAllLoadedPlayerUsernames());
            this.fail(
               "No user in render distance found with username \""
                  + this.playerName
                  + "\". Maybe this was a typo or there is a user with a similar name around? Nearby users: ["
                  + nearbyUsernames
                  + "]."
            );
            return null;
         } else {
            Vec3 targetPos = lastPos.get().add(0.0, 0.2F, 0.0);
            if (this.droppingItems) {
               this.setDebugState("Throwing items");
               if (!this.throwTimeout.elapsed()) {
                  return null;
               } else {
                  this.throwTimeout.reset();
                  LookHelper.lookAt(mod, targetPos);

                  for (int i = 0; i < this.throwTarget.size(); i++) {
                     ItemTarget target = this.throwTarget.get(i);
                     int neededToThrow = target.getTargetCount();
                     if (target.getTargetCount() > 0) {
                        Optional<Slot> has = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, target.getMatches()).stream().findFirst();
                        if (has.isPresent()) {
                           Slot slot = has.get();
                           ItemStack stack = StorageHelper.getItemStackInSlot(slot);
                           int amountToThrow = Math.min(neededToThrow, stack.getCount());
                           mod.getSlotHandler().forceEquipSlot(mod, slot);
                           mod.getPlayer().spawnAtLocation(mod.getWorld(), mod.getPlayer().getMainHandItem(), amountToThrow).setPickUpDelay(40);
                           mod.getInventory().setItem(mod.getInventory().selectedSlot, ItemStack.EMPTY);
                           this.throwTarget.set(i, new ItemTarget(target, neededToThrow - amountToThrow));
                           return null;
                        }
                     }
                  }

                  this.throwTimeout.forceElapse();
                  if (!targetPos.closerThan(mod.getPlayer().position(), 4.0)) {
                     mod.log("Finished giving items.");
                     this.stop();
                     return null;
                  } else {
                     return new RunAwayFromPositionTask(6.0, WorldHelper.toBlockPos(targetPos));
                  }
               }
            } else if (!StorageHelper.itemTargetsMet(mod, this.targets)) {
               this.setDebugState("Collecting resources...");
               return this.resourceTask;
            } else {
               if (targetPos.closerThan(mod.getPlayer().position(), 4.0)) {
                  if (!mod.getEntityTracker().isPlayerLoaded(this.playerName)) {
                     String nearbyUsernames = String.join(",", mod.getEntityTracker().getAllLoadedPlayerUsernames());
                     this.fail(
                        "Failed to get to player \""
                           + this.playerName
                           + "\". We moved to where we last saw them but now have no idea where they are. Nearby players: ["
                           + nearbyUsernames
                           + "]"
                     );
                     return null;
                  }

                  Player p = mod.getEntityTracker().getPlayerEntity(this.playerName).get();
                  if ((p.blockPosition().getY() <= mod.getPlayer().blockPosition().getY() || p.position().distanceTo(mod.getPlayer().position()) <= 0.5)
                     && LookHelper.seesPlayer(p, mod.getPlayer(), 6.0)) {
                     this.droppingItems = true;
                     this.throwTarget.addAll(Arrays.asList(this.targets));
                     this.throwTimeout.reset();
                  }
               }

               this.setDebugState("Going to player...");
               return new FollowPlayerTask(this.playerName, 0.5);
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof GiveItemToPlayerTask task) {
         return !task.playerName.equals(this.playerName) ? false : Arrays.equals((Object[])task.targets, (Object[])this.targets);
      } else {
         return false;
      }
   }

   @Override
   protected String toDebugString() {
      return "Giving items to " + this.playerName;
   }
}
