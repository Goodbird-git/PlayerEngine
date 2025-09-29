package com.player2.playerengine.tasks;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.movement.SafeRandomShimmyTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.baritone.GoalAnd;
import com.player2.playerengine.util.baritone.GoalBlockSide;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.progresscheck.MovementProgressChecker;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalNear;
import com.player2.playerengine.automaton.api.pathing.goals.GoalTwoBlocks;
import com.player2.playerengine.automaton.api.process.ICustomGoalProcess;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;

public class InteractWithBlockTask extends Task {
   private final MovementProgressChecker moveChecker = new MovementProgressChecker();
   private final MovementProgressChecker stuckCheck = new MovementProgressChecker();
   private final ItemTarget toUse;
   private final Direction direction;
   private final BlockPos target;
   private final boolean walkInto;
   private final Vec3i interactOffset;
   private final Input interactInput;
   private final boolean shiftClick;
   private final TimerGame clickTimer = new TimerGame(5.0);
   private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5.0F, true);
   Block[] annoyingBlocks = new Block[]{
      Blocks.VINE,
      Blocks.NETHER_SPROUTS,
      Blocks.CAVE_VINES,
      Blocks.CAVE_VINES_PLANT,
      Blocks.TWISTING_VINES,
      Blocks.TWISTING_VINES_PLANT,
      Blocks.WEEPING_VINES_PLANT,
      Blocks.LADDER,
      Blocks.BIG_DRIPLEAF,
      Blocks.BIG_DRIPLEAF_STEM,
      Blocks.SMALL_DRIPLEAF,
      Blocks.TALL_GRASS,
      Blocks.GRASS_BLOCK,
      Blocks.SWEET_BERRY_BUSH
   };
   private Task unstuckTask = null;
   private InteractWithBlockTask.ClickResponse cachedClickStatus = InteractWithBlockTask.ClickResponse.CANT_REACH;
   private int waitingForClickTicks = 0;

   public InteractWithBlockTask(
      ItemTarget toUse, Direction direction, BlockPos target, Input interactInput, boolean walkInto, Vec3i interactOffset, boolean shiftClick
   ) {
      this.toUse = toUse;
      this.direction = direction;
      this.target = target;
      this.interactInput = interactInput;
      this.walkInto = walkInto;
      this.interactOffset = interactOffset;
      this.shiftClick = shiftClick;
   }

   public InteractWithBlockTask(ItemTarget toUse, Direction direction, BlockPos target, Input interactInput, boolean walkInto, boolean shiftClick) {
      this(toUse, direction, target, interactInput, walkInto, Vec3i.ZERO, shiftClick);
   }

   public InteractWithBlockTask(ItemTarget toUse, Direction direction, BlockPos target, boolean walkInto) {
      this(toUse, direction, target, Input.CLICK_RIGHT, walkInto, true);
   }

   public InteractWithBlockTask(ItemTarget toUse, BlockPos target, boolean walkInto, Vec3i interactOffset) {
      this(toUse, null, target, Input.CLICK_RIGHT, walkInto, interactOffset, true);
   }

   public InteractWithBlockTask(ItemTarget toUse, BlockPos target, boolean walkInto) {
      this(toUse, target, walkInto, Vec3i.ZERO);
   }

   public InteractWithBlockTask(ItemTarget toUse, BlockPos target) {
      this(toUse, target, false);
   }

   public InteractWithBlockTask(
      Item toUse, Direction direction, BlockPos target, Input interactInput, boolean walkInto, Vec3i interactOffset, boolean shiftClick
   ) {
      this(new ItemTarget(toUse, 1), direction, target, interactInput, walkInto, interactOffset, shiftClick);
   }

   public InteractWithBlockTask(Item toUse, Direction direction, BlockPos target, Input interactInput, boolean walkInto, boolean shiftClick) {
      this(new ItemTarget(toUse, 1), direction, target, interactInput, walkInto, shiftClick);
   }

   public InteractWithBlockTask(Item toUse, Direction direction, BlockPos target, boolean walkInto) {
      this(new ItemTarget(toUse, 1), direction, target, walkInto);
   }

   public InteractWithBlockTask(Item toUse, Direction direction, BlockPos target) {
      this(new ItemTarget(toUse, 1), direction, target, Input.CLICK_RIGHT, false, false);
   }

   public InteractWithBlockTask(Item toUse, BlockPos target, boolean walkInto, Vec3i interactOffset) {
      this(new ItemTarget(toUse, 1), target, walkInto, interactOffset);
   }

   public InteractWithBlockTask(Item toUse, Direction direction, BlockPos target, Vec3i interactOffset) {
      this(new ItemTarget(toUse, 1), direction, target, Input.CLICK_RIGHT, false, interactOffset, false);
   }

   public InteractWithBlockTask(Item toUse, BlockPos target, Vec3i interactOffset) {
      this(new ItemTarget(toUse, 1), null, target, Input.CLICK_RIGHT, false, interactOffset, false);
   }

   public InteractWithBlockTask(Item toUse, BlockPos target, boolean walkInto) {
      this(new ItemTarget(toUse, 1), target, walkInto);
   }

   public InteractWithBlockTask(Item toUse, BlockPos target) {
      this(new ItemTarget(toUse, 1), target);
   }

   public InteractWithBlockTask(BlockPos target, boolean shiftClick) {
      this(ItemTarget.EMPTY, null, target, Input.CLICK_RIGHT, false, shiftClick);
   }

   public InteractWithBlockTask(BlockPos target) {
      this(ItemTarget.EMPTY, null, target, Input.CLICK_RIGHT, false, false);
   }

   private static BlockPos[] generateSides(BlockPos pos) {
      return new BlockPos[]{
         pos.offset(1, 0, 0),
         pos.offset(-1, 0, 0),
         pos.offset(0, 0, 1),
         pos.offset(0, 0, -1),
         pos.offset(1, 0, -1),
         pos.offset(1, 0, 1),
         pos.offset(-1, 0, -1),
         pos.offset(-1, 0, 1)
      };
   }

   private static Goal createGoalForInteract(BlockPos target, int reachDistance, Direction interactSide, Vec3i interactOffset, boolean walkInto) {
      boolean sideMatters = interactSide != null;
      if (sideMatters) {
         Vec3i offs = interactSide.getUnitVec3i();
         if (offs.getY() == -1) {
            offs = offs.below();
         }

         target = target.offset(offs);
      }

      if (walkInto) {
         return new GoalTwoBlocks(target);
      } else if (sideMatters) {
         GoalBlockSide goalBlockSide = new GoalBlockSide(target, interactSide, 1.0);
         return new GoalAnd(goalBlockSide, new GoalNear(target.offset(interactOffset), reachDistance));
      } else {
         return new GoalTwoBlocks(target.above());
      }
   }

   private boolean isAnnoying(PlayerEngineController mod, BlockPos pos) {
      if (this.annoyingBlocks != null) {
         Block[] arrayOfBlock = this.annoyingBlocks;
         int i = arrayOfBlock.length;
         byte b = 0;
         if (b < i) {
            Block AnnoyingBlocks = arrayOfBlock[b];
            return mod.getWorld().getBlockState(pos).getBlock() == AnnoyingBlocks
               || mod.getWorld().getBlockState(pos).getBlock() instanceof DoorBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceGateBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FlowerBlock;
         }
      }

      return false;
   }

   private BlockPos stuckInBlock(PlayerEngineController mod) {
      BlockPos p = mod.getPlayer().blockPosition();
      if (this.isAnnoying(mod, p)) {
         return p;
      } else if (this.isAnnoying(mod, p.above())) {
         return p.above();
      } else {
         BlockPos[] toCheck = generateSides(p);

         for (BlockPos check : toCheck) {
            if (this.isAnnoying(mod, check)) {
               return check;
            }
         }

         BlockPos[] toCheckHigh = generateSides(p.above());

         for (BlockPos checkx : toCheckHigh) {
            if (this.isAnnoying(mod, checkx)) {
               return checkx;
            }
         }

         return null;
      }
   }

   private Task getFenceUnstuckTask() {
      return new SafeRandomShimmyTask();
   }

   @Override
   protected void onStart() {
      this.controller.getBaritone().getPathingBehavior().forceCancel();
      this.moveChecker.reset();
      this.stuckCheck.reset();
      this.wanderTask.resetWander();
      this.clickTimer.reset();
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.moveChecker.reset();
      }

      if (WorldHelper.isInNetherPortal(this.controller)) {
         if (!mod.getBaritone().getPathingBehavior().isPathing()) {
            this.setDebugState("Getting out from nether portal");
            mod.getInputControls().hold(Input.SNEAK);
            mod.getInputControls().hold(Input.MOVE_FORWARD);
            return null;
         }

         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.MOVE_BACK);
         mod.getInputControls().release(Input.MOVE_FORWARD);
      } else if (mod.getBaritone().getPathingBehavior().isPathing()) {
         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.MOVE_BACK);
         mod.getInputControls().release(Input.MOVE_FORWARD);
      }

      if (this.unstuckTask != null && this.unstuckTask.isActive() && !this.unstuckTask.isFinished() && this.stuckInBlock(mod) != null) {
         this.setDebugState("Getting unstuck from block.");
         this.stuckCheck.reset();
         mod.getBaritone().getCustomGoalProcess().onLostControl();
         mod.getBaritone().getExploreProcess().onLostControl();
         return this.unstuckTask;
      } else {
         if (!this.moveChecker.check(mod) || !this.stuckCheck.check(mod)) {
            BlockPos blockStuck = this.stuckInBlock(mod);
            if (blockStuck != null) {
               this.unstuckTask = this.getFenceUnstuckTask();
               return this.unstuckTask;
            }

            this.stuckCheck.reset();
         }

         this.cachedClickStatus = InteractWithBlockTask.ClickResponse.CANT_REACH;
         if (!ItemTarget.nullOrEmpty(this.toUse) && !StorageHelper.itemTargetsMet(mod, this.toUse)) {
            this.moveChecker.reset();
            this.clickTimer.reset();
            return TaskCatalogue.getItemTask(this.toUse);
         } else if (this.wanderTask.isActive() && !this.wanderTask.isFinished()) {
            this.moveChecker.reset();
            this.clickTimer.reset();
            return this.wanderTask;
         } else if (!this.moveChecker.check(mod)) {
            Debug.logMessage("Failed, blacklisting and wandering.");
            mod.getBlockScanner().requestBlockUnreachable(this.target);
            return this.wanderTask;
         } else {
            int reachDistance = 0;
            Goal moveGoal = createGoalForInteract(this.target, reachDistance, this.direction, this.interactOffset, this.walkInto);
            ICustomGoalProcess customGoalProcess = mod.getBaritone().getCustomGoalProcess();
            this.cachedClickStatus = this.rightClick(mod);
            switch ((InteractWithBlockTask.ClickResponse)Objects.requireNonNull(this.cachedClickStatus)) {
               case CANT_REACH:
                  this.setDebugState("Getting to our goal");
                  if (!customGoalProcess.isActive()) {
                     customGoalProcess.setGoalAndPath(moveGoal);
                  }

                  this.clickTimer.reset();
                  break;
               case WAIT_FOR_CLICK:
                  this.setDebugState("Waiting for click");
                  if (customGoalProcess.isActive()) {
                     customGoalProcess.onLostControl();
                  }

                  this.clickTimer.reset();
                  this.waitingForClickTicks++;
                  if (this.waitingForClickTicks % 25 == 0 && this.shiftClick) {
                     mod.getInputControls().hold(Input.SNEAK);
                     mod.log("trying to press shift");
                  }

                  if (this.waitingForClickTicks > 200) {
                     mod.log("trying to wander");
                     this.waitingForClickTicks = 0;
                     return this.wanderTask;
                  }
                  break;
               case CLICK_ATTEMPTED:
                  this.setDebugState("Clicking.");
                  if (customGoalProcess.isActive()) {
                     customGoalProcess.onLostControl();
                  }

                  if (this.clickTimer.elapsed()) {
                     this.clickTimer.reset();
                     return this.wanderTask;
                  }
            }

            return null;
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      PlayerEngineController mod = this.controller;
      mod.getBaritone().getPathingBehavior().forceCancel();
      mod.getInputControls().release(Input.SNEAK);
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof InteractWithBlockTask task) {
         if (task.direction == null != (this.direction == null)) {
            return false;
         } else if (task.direction != null && !task.direction.equals(this.direction)) {
            return false;
         } else if (task.toUse == null != (this.toUse == null)) {
            return false;
         } else if (task.toUse != null && !task.toUse.equals(this.toUse)) {
            return false;
         } else if (!task.target.equals(this.target)) {
            return false;
         } else {
            return !task.interactInput.equals(this.interactInput) ? false : task.walkInto == this.walkInto;
         }
      } else {
         return false;
      }
   }

   @Override
   protected String toDebugString() {
      return "Interact using " + this.toUse + " at " + this.target + " dir " + this.direction;
   }

   public InteractWithBlockTask.ClickResponse getClickStatus() {
      return this.cachedClickStatus;
   }

   private InteractWithBlockTask.ClickResponse rightClick(PlayerEngineController mod) {
      if (!mod.getExtraBaritoneSettings().isInteractionPaused() && !mod.getFoodChain().needsToEat() && !mod.getPlayer().isBlocking()) {
         ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot(this.controller);
         if (!cursorStack.isEmpty()) {
            Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
            if (moveTo.isPresent()) {
               mod.getSlotHandler().clickSlot(moveTo.get(), 0, ClickType.PICKUP);
               return InteractWithBlockTask.ClickResponse.WAIT_FOR_CLICK;
            } else if (ItemHelper.canThrowAwayStack(mod, cursorStack)) {
               mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
               return InteractWithBlockTask.ClickResponse.WAIT_FOR_CLICK;
            } else {
               Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
               if (garbage.isPresent()) {
                  mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
                  return InteractWithBlockTask.ClickResponse.WAIT_FOR_CLICK;
               } else {
                  mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
                  return InteractWithBlockTask.ClickResponse.WAIT_FOR_CLICK;
               }
            }
         } else {
            Optional<Rotation> reachable = this.getCurrentReach();
            if (reachable.isPresent()) {
               if (LookHelper.isLookingAt(mod, this.target)) {
                  if (this.toUse != null) {
                     mod.getSlotHandler().forceEquipItem(this.toUse, false);
                  } else {
                     mod.getSlotHandler().forceDeequipRightClickableItem();
                  }

                  mod.getInputControls().tryPress(this.interactInput);
                  if (mod.getInputControls().isHeldDown(this.interactInput)) {
                     if (this.shiftClick) {
                        mod.getInputControls().hold(Input.SNEAK);
                     }

                     return InteractWithBlockTask.ClickResponse.CLICK_ATTEMPTED;
                  }
               } else {
                  LookHelper.lookAt(this.controller, reachable.get());
               }

               return InteractWithBlockTask.ClickResponse.WAIT_FOR_CLICK;
            } else {
               if (this.shiftClick) {
                  mod.getInputControls().release(Input.SNEAK);
               }

               return InteractWithBlockTask.ClickResponse.CANT_REACH;
            }
         }
      } else {
         return InteractWithBlockTask.ClickResponse.WAIT_FOR_CLICK;
      }
   }

   public Optional<Rotation> getCurrentReach() {
      return LookHelper.getReach(this.controller, this.target, this.direction);
   }

   public static enum ClickResponse {
      CANT_REACH,
      WAIT_FOR_CLICK,
      CLICK_ATTEMPTED;
   }
}
