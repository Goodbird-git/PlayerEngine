package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.Subscription;
import com.player2.playerengine.eventbus.events.BlockPlaceEvent;
import com.player2.playerengine.multiversion.blockpos.BlockPosVer;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.progresscheck.MovementProgressChecker;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.pathing.movement.MovementHelper;
import java.util.Arrays;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.apache.commons.lang3.ArrayUtils;

public class PlaceBlockNearbyTask extends Task {
   private final Block[] toPlace;
   private final MovementProgressChecker progressChecker = new MovementProgressChecker();
   private final TimeoutWanderTask wander = new TimeoutWanderTask(5.0F);
   private final TimerGame randomlookTimer = new TimerGame(0.25);
   private final Predicate<BlockPos> canPlaceHere;
   private BlockPos justPlaced;
   private BlockPos tryPlace;
   private Subscription<BlockPlaceEvent> onBlockPlaced;

   public PlaceBlockNearbyTask(Predicate<BlockPos> canPlaceHere, Block... toPlace) {
      this.toPlace = toPlace;
      this.canPlaceHere = canPlaceHere;
   }

   public PlaceBlockNearbyTask(Block... toPlace) {
      this(blockPos -> true, toPlace);
   }

   @Override
   protected void onStart() {
      this.progressChecker.reset();
      this.controller.getBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
      this.onBlockPlaced = EventBus.subscribe(BlockPlaceEvent.class, evt -> {
         if (ArrayUtils.contains(this.toPlace, evt.blockState.getBlock())) {
            this.stopPlacing();
         }
      });
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.progressChecker.reset();
      }

      ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot(this.controller);
      BlockPos current = this.getCurrentlyLookingBlockPlace(mod);
      if (current != null && this.canPlaceHere.test(current)) {
         this.setDebugState("Placing since we can...");
         if (mod.getSlotHandler().forceEquipItem(ItemHelper.blocksToItems(this.toPlace)) && this.place(mod, current)) {
            return null;
         }
      }

      if (this.wander.isActive() && !this.wander.isFinished()) {
         this.setDebugState("Wandering, will try to place again later.");
         this.progressChecker.reset();
         return this.wander;
      } else if (!this.progressChecker.check(mod)) {
         Debug.logMessage("Failed placing, wandering and trying again.");
         LookHelper.randomOrientation(this.controller);
         if (this.tryPlace != null) {
            mod.getBlockScanner().requestBlockUnreachable(this.tryPlace);
            this.tryPlace = null;
         }

         return this.wander;
      } else {
         if (this.tryPlace == null || !WorldHelper.canReach(this.controller, this.tryPlace)) {
            this.tryPlace = this.locateClosePlacePos(mod);
         }

         if (this.tryPlace != null) {
            this.setDebugState("Trying to place at " + this.tryPlace);
            this.justPlaced = this.tryPlace;
            return new PlaceBlockTask(this.tryPlace, this.toPlace);
         } else {
            if (this.randomlookTimer.elapsed()) {
               this.randomlookTimer.reset();
               LookHelper.randomOrientation(this.controller);
            }

            this.setDebugState("Wandering until we randomly place or find a good place spot.");
            return new TimeoutWanderTask();
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.stopPlacing();
      EventBus.unsubscribe(this.onBlockPlaced);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof PlaceBlockNearbyTask task ? Arrays.equals((Object[])task.toPlace, (Object[])this.toPlace) : false;
   }

   @Override
   protected String toDebugString() {
      return "Place " + Arrays.toString((Object[])this.toPlace) + " nearby";
   }

   @Override
   public boolean isFinished() {
      return this.justPlaced != null && ArrayUtils.contains(this.toPlace, this.controller.getWorld().getBlockState(this.justPlaced).getBlock());
   }

   public BlockPos getPlaced() {
      return this.justPlaced;
   }

   private BlockPos getCurrentlyLookingBlockPlace(PlayerEngineController mod) {
      if (Minecraft.getInstance().hitResult instanceof BlockHitResult bhit) {
         BlockPos bpos = bhit.getBlockPos();
         IEntityContext ctx = mod.getBaritone().getEntityContext();
         if (MovementHelper.canPlaceAgainst(ctx, bpos)) {
            BlockPos placePos = bhit.getBlockPos().offset(bhit.getDirection().getUnitVec3i());
            if (WorldHelper.isInsidePlayer(this.controller, placePos)) {
               return null;
            }

            if (WorldHelper.canPlace(this.controller, placePos)) {
               return placePos;
            }
         }
      }

      return null;
   }

   private boolean blockEquipped() {
      return StorageHelper.isEquipped(this.controller, ItemHelper.blocksToItems(this.toPlace));
   }

   private boolean place(PlayerEngineController mod, BlockPos targetPlace) {
      if (!mod.getExtraBaritoneSettings().isInteractionPaused() && this.blockEquipped()) {
         mod.getInputControls().hold(Input.SNEAK);
         HitResult mouseOver = Minecraft.getInstance().hitResult;
         if (mouseOver != null && mouseOver.getType() == Type.BLOCK) {
            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (((IInteractionManagerProvider)mod.getEntity())
                     .getInteractionManager()
                     .interactBlock(mod.getPlayer(), mod.getWorld(), mod.getPlayer().getMainHandItem(), hand, (BlockHitResult)mouseOver)
                  == InteractionResult.SUCCESS
               && mod.getPlayer().isShiftKeyDown()) {
               mod.getPlayer().swing(hand);
               this.justPlaced = targetPlace;
               Debug.logMessage("PRESSED");
               return true;
            } else {
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void stopPlacing() {
      this.controller.getInputControls().release(Input.SNEAK);
      this.controller.getBaritone().getBuilderProcess().onLostControl();
   }

   private BlockPos locateClosePlacePos(PlayerEngineController mod) {
      int range = 7;
      BlockPos best = null;
      double smallestScore = Double.POSITIVE_INFINITY;
      BlockPos start = mod.getPlayer().blockPosition().offset(-range, -range, -range);
      BlockPos end = mod.getPlayer().blockPosition().offset(range, range, range);

      for (BlockPos blockPos : WorldHelper.scanRegion(start, end)) {
         boolean solid = WorldHelper.isSolidBlock(this.controller, blockPos);
         boolean inside = WorldHelper.isInsidePlayer(this.controller, blockPos);
         if ((!solid || WorldHelper.canBreak(this.controller, blockPos))
            && this.canPlaceHere.test(blockPos)
            && WorldHelper.canReach(this.controller, blockPos)
            && WorldHelper.canPlace(this.controller, blockPos)) {
            boolean hasBelow = WorldHelper.isSolidBlock(this.controller, blockPos.below());
            double distSq = BlockPosVer.getSquaredDistance(blockPos, mod.getPlayer().position());
            double score = distSq + (solid ? 4 : 0) + (hasBelow ? 0 : 10) + (inside ? 3 : 0);
            if (score < smallestScore) {
               best = blockPos;
               smallestScore = score;
            }
         }
      }

      return best;
   }
}
