/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package altoclef.tasks.resources;

import altoclef.AltoClefController;
import altoclef.Debug;
import altoclef.multiversion.ToolMaterialVer;
import altoclef.multiversion.blockpos.BlockPosVer;
import altoclef.tasks.AbstractDoToClosestObjectTask;
import altoclef.tasks.ResourceTask;
import altoclef.tasks.construction.DestroyBlockTask;
import altoclef.tasks.movement.PickupDroppedItemTask;
import altoclef.tasksystem.Task;
import altoclef.util.ItemTarget;
import altoclef.util.MiningRequirement;
import altoclef.util.helpers.StorageHelper;
import altoclef.util.helpers.WorldHelper;
import altoclef.util.progresscheck.MovementProgressChecker;
import altoclef.util.slots.CursorSlot;
import altoclef.util.slots.PlayerSlot;
import altoclef.util.time.TimerGame;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class MineAndCollectTask extends ResourceTask {
   private final Block[] blocksToMine;
   private final MiningRequirement requirement;
   private final TimerGame cursorStackTimer = new TimerGame(3.0);
   private final MineAndCollectTask.MineOrCollectTask subtask;

   public MineAndCollectTask(ItemTarget[] itemTargets, Block[] blocksToMine, MiningRequirement requirement) {
      super(itemTargets);
      this.requirement = requirement;
      this.blocksToMine = blocksToMine;
      this.subtask = new MineAndCollectTask.MineOrCollectTask(this.blocksToMine, itemTargets);
   }

   public MineAndCollectTask(ItemTarget[] blocksToMine, MiningRequirement requirement) {
      this(blocksToMine, itemTargetToBlockList(blocksToMine), requirement);
   }

   public MineAndCollectTask(ItemTarget target, Block[] blocksToMine, MiningRequirement requirement) {
      this(new ItemTarget[]{target}, blocksToMine, requirement);
   }

   public MineAndCollectTask(Item item, int count, Block[] blocksToMine, MiningRequirement requirement) {
      this(new ItemTarget(item, count), blocksToMine, requirement);
   }

   public static Block[] itemTargetToBlockList(ItemTarget[] targets) {
      List<Block> result = new ArrayList<>(targets.length);

      for (ItemTarget target : targets) {
         for (Item item : target.getMatches()) {
            Block block = Block.byItem(item);
            if (block != null && !WorldHelper.isAir(block)) {
               result.add(block);
            }
         }
      }

      return result.toArray(Block[]::new);
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
      mod.getBehaviour().addProtectedItems(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
      this.subtask.resetSearch();
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return true;
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (!StorageHelper.miningRequirementMet(mod, this.requirement)) {
         return new SatisfyMiningRequirementTask(this.requirement);
      } else {
         if (this.subtask.isMining()) {
            this.makeSureToolIsEquipped(mod);
         }

         return (Task)(this.subtask.wasWandering() && this.isInWrongDimension(mod) && !mod.getBlockScanner().anyFound(this.blocksToMine)
            ? this.getToCorrectDimensionTask(mod)
            : this.subtask);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof MineAndCollectTask task ? Arrays.equals((Object[])task.blocksToMine, (Object[])this.blocksToMine) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Mine And Collect";
   }

   private void makeSureToolIsEquipped(AltoClefController mod) {
      if (this.cursorStackTimer.elapsed() && !mod.getFoodChain().needsToEat()) {
         assert this.controller.getPlayer() != null;

         ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot(this.controller);
         if (cursorStack != null && !cursorStack.isEmpty()) {
            Item item = cursorStack.getItem();
            if (item.isCorrectToolForDrops(mod.getWorld().getBlockState(this.subtask.miningPos()))) {
               Item currentlyEquipped = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
               if (item instanceof DiggerItem) {
                  if (currentlyEquipped instanceof DiggerItem currentPick) {
                     DiggerItem swapPick = (DiggerItem)item;
                     if (ToolMaterialVer.getMiningLevel(swapPick) > ToolMaterialVer.getMiningLevel(currentPick)) {
                        mod.getSlotHandler().forceEquipSlot(this.controller, CursorSlot.SLOT);
                     }
                  } else {
                     mod.getSlotHandler().forceEquipSlot(this.controller, CursorSlot.SLOT);
                  }
               }
            }
         }

         this.cursorStackTimer.reset();
      }
   }

   public static class MineOrCollectTask extends AbstractDoToClosestObjectTask<Object> {
      private final Block[] blocks;
      private final ItemTarget[] targets;
      private final Set<BlockPos> blacklist = new HashSet<>();
      private final MovementProgressChecker progressChecker = new MovementProgressChecker();
      private final Task pickupTask;
      private BlockPos miningPos;

      public MineOrCollectTask(Block[] blocks, ItemTarget[] targets) {
         this.blocks = blocks;
         this.targets = targets;
         this.pickupTask = new PickupDroppedItemTask(targets, true);
      }

      @Override
      protected Vec3 getPos(AltoClefController mod, Object obj) {
         if (obj instanceof BlockPos b) {
            return WorldHelper.toVec3d(b);
         } else if (obj instanceof ItemEntity item) {
            return item.position();
         } else {
            throw new UnsupportedOperationException(
               "Shouldn't try to get the position of object " + obj + " of type " + (obj != null ? obj.getClass().toString() : "(null object)")
            );
         }
      }

      @Override
      protected Optional<Object> getClosestTo(AltoClefController mod, Vec3 pos) {
         Tuple<Double, Optional<BlockPos>> closestBlock = getClosestBlock(mod, pos, this.blocks);
         Tuple<Double, Optional<ItemEntity>> closestDrop = getClosestItemDrop(mod, pos, this.targets);
         double blockSq = (Double)closestBlock.getA();
         double dropSq = (Double)closestDrop.getA();
         if (mod.getExtraBaritoneSettings().isInteractionPaused()) {
            return ((Optional)closestDrop.getB()).map(Object.class::cast);
         } else {
            return dropSq <= blockSq ? ((Optional)closestDrop.getB()).map(Object.class::cast) : ((Optional)closestBlock.getB()).map(Object.class::cast);
         }
      }

      public static Tuple<Double, Optional<ItemEntity>> getClosestItemDrop(AltoClefController mod, Vec3 pos, ItemTarget... items) {
         Optional<ItemEntity> closestDrop = Optional.empty();
         if (mod.getEntityTracker().itemDropped(items)) {
            closestDrop = mod.getEntityTracker().getClosestItemDrop(pos, items);
         }

         return new Tuple(closestDrop.<Double>map(itemEntity -> itemEntity.distanceToSqr(pos) + 10.0).orElse(Double.POSITIVE_INFINITY), closestDrop);
      }

      public static Tuple<Double, Optional<BlockPos>> getClosestBlock(AltoClefController mod, Vec3 pos, Block... blocks) {
         Optional<BlockPos> closestBlock = mod.getBlockScanner()
            .getNearestBlock(pos, check -> mod.getBlockScanner().isUnreachable(check) ? false : WorldHelper.canBreak(mod, check), blocks);
         return new Tuple(closestBlock.<Double>map(blockPos -> BlockPosVer.getSquaredDistance(blockPos, pos)).orElse(Double.POSITIVE_INFINITY), closestBlock);
      }

      @Override
      protected Vec3 getOriginPos(AltoClefController mod) {
         return mod.getPlayer().position();
      }

      @Override
      protected Task onTick() {
         AltoClefController mod = this.controller;
         if (mod.getBaritone().getPathingBehavior().isPathing()) {
            this.progressChecker.reset();
         }

         if (this.miningPos != null && !this.progressChecker.check(mod)) {
            mod.getBaritone().getPathingBehavior().forceCancel();
            Debug.logMessage("Failed to mine block. Suggesting it may be unreachable.");
            mod.getBlockScanner().requestBlockUnreachable(this.miningPos, 2);
            this.blacklist.add(this.miningPos);
            this.miningPos = null;
            this.progressChecker.reset();
         }

         return super.onTick();
      }

      @Override
      protected Task getGoalTask(Object obj) {
         if (!(obj instanceof BlockPos newPos)) {
            if (obj instanceof ItemEntity) {
               this.miningPos = null;
               return this.pickupTask;
            } else {
               throw new UnsupportedOperationException(
                  "Shouldn't try to get the goal from object " + obj + " of type " + (obj != null ? obj.getClass().toString() : "(null object)")
               );
            }
         } else {
            if (this.miningPos == null || !this.miningPos.equals(newPos)) {
               this.progressChecker.reset();
            }

            this.miningPos = newPos;
            return new DestroyBlockTask(this.miningPos);
         }
      }

      @Override
      protected boolean isValid(AltoClefController mod, Object obj) {
         if (obj instanceof BlockPos b) {
            return mod.getBlockScanner().isBlockAtPosition(b, this.blocks) && WorldHelper.canBreak(this.controller, b);
         } else if (!(obj instanceof ItemEntity drop)) {
            return false;
         } else {
            Item item = drop.getItem().getItem();
            if (this.targets != null) {
               for (ItemTarget target : this.targets) {
                  if (target.matches(item)) {
                     return true;
                  }
               }
            }

            return false;
         }
      }

      @Override
      protected void onStart() {
         this.progressChecker.reset();
         this.miningPos = null;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return !(other instanceof MineAndCollectTask.MineOrCollectTask task)
            ? false
            : Arrays.equals((Object[])task.blocks, (Object[])this.blocks) && Arrays.equals((Object[])task.targets, (Object[])this.targets);
      }

      @Override
      protected String toDebugString() {
         return "Mining or Collecting";
      }

      public boolean isMining() {
         return this.miningPos != null;
      }

      public BlockPos miningPos() {
         return this.miningPos;
      }
   }
}
