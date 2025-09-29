package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.ITaskRequiresGrounded;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.EntityHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.util.time.TimerGame;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class ProjectileProtectionWallTask extends Task implements ITaskRequiresGrounded {
   private final PlayerEngineController mod;
   private final TimerGame waitForBlockPlacement = new TimerGame(2.0);
   private BlockPos targetPlacePos;

   public ProjectileProtectionWallTask(PlayerEngineController mod) {
      this.mod = mod;
   }

   @Override
   protected void onStart() {
      this.waitForBlockPlacement.forceElapse();
   }

   @Override
   protected Task onTick() {
      if (this.targetPlacePos != null && !WorldHelper.isSolidBlock(this.controller, this.targetPlacePos)) {
         Optional<Slot> slot = StorageHelper.getSlotWithThrowawayBlock(this.mod, true);
         if (slot.isPresent()) {
            this.place(this.targetPlacePos, InteractionHand.MAIN_HAND, slot.get().getInventorySlot());
            this.targetPlacePos = null;
            this.setDebugState(null);
         }

         return null;
      } else {
         Optional<Entity> sentity = this.mod
            .getEntityTracker()
            .getClosestEntity(
               (Predicate<Entity>)(e -> e instanceof Skeleton && EntityHelper.isAngryAtPlayer(this.mod, e) && ((Skeleton)e).getTicksUsingItem() > 8),
               Skeleton.class
            );
         if (sentity.isPresent()) {
            Vec3 playerPos = this.mod.getPlayer().position();
            Vec3 targetPos = sentity.get().position();
            Vec3 direction = playerPos.subtract(targetPos).normalize();
            double x = playerPos.x - 2.0 * direction.x;
            double y = playerPos.y + direction.y;
            double z = playerPos.z - 2.0 * direction.z;
            this.targetPlacePos = new BlockPos((int)x, (int)y + 1, (int)z);
            this.setDebugState("Placing at " + this.targetPlacePos.toString());
            this.waitForBlockPlacement.reset();
         }

         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      assert this.controller.getWorld() != null;

      Optional<Entity> entity = this.mod
         .getEntityTracker()
         .getClosestEntity(
            (Predicate<Entity>)(e -> e instanceof Skeleton && EntityHelper.isAngryAtPlayer(this.mod, e) && ((Skeleton)e).getTicksUsingItem() > 3),
            Skeleton.class
         );
      return this.targetPlacePos != null && WorldHelper.isSolidBlock(this.mod, this.targetPlacePos) || entity.isEmpty();
   }

   @Override
   protected boolean isEqual(Task other) {
      return true;
   }

   @Override
   protected String toDebugString() {
      return "Placing blocks to block projectiles";
   }

   public Direction getPlaceSide(BlockPos blockPos) {
      for (Direction side : Direction.values()) {
         BlockPos neighbor = blockPos.relative(side);
         BlockState state = this.mod.getWorld().getBlockState(neighbor);
         if (!state.isAir() && !isClickable(state.getBlock()) && state.getFluidState().isEmpty()) {
            return side;
         }
      }

      return null;
   }

   public boolean place(BlockPos blockPos, InteractionHand hand, int slot) {
      if (slot < 0 || slot > 8) {
         return false;
      } else if (!this.canPlace(blockPos)) {
         return false;
      } else {
         Vec3 hitPos = Vec3.atCenterOf(blockPos);
         Direction side = this.getPlaceSide(blockPos);
         if (side == null) {
            this.place(blockPos.below(), hand, slot);
            return false;
         } else {
            BlockPos neighbour = blockPos.relative(side);
            hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
            BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
            this.mod.getPlayer().setYRot((float)this.getYaw(hitPos));
            this.mod.getPlayer().setXRot((float)this.getPitch(hitPos));
            this.swap(slot);
            this.interact(bhr, hand);
            return true;
         }
      }
   }

   public static boolean isClickable(Block block) {
      return block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof ButtonBlock
         || block instanceof BasePressurePlateBlock
         || block instanceof BaseEntityBlock
         || block instanceof BedBlock
         || block instanceof FenceGateBlock
         || block instanceof DoorBlock
         || block instanceof NoteBlock
         || block instanceof TrapDoorBlock;
   }

   public void interact(BlockHitResult blockHitResult, InteractionHand hand) {
      boolean wasSneaking = this.mod.getPlayer().isShiftKeyDown();
      this.mod.getPlayer().setShiftKeyDown(false);
      InteractionResult result = this.mod
         .getBaritone()
         .getEntityContext()
         .playerController()
         .processRightClickBlock(this.mod.getPlayer(), this.mod.getWorld(), hand, blockHitResult);
      if (!result.consumesAction()) {
         this.mod.getPlayer().swing(hand);
      }

      this.mod.getPlayer().setShiftKeyDown(wasSneaking);
   }

   public boolean canPlace(BlockPos blockPos, boolean checkEntities) {
      if (blockPos == null) {
         return false;
      } else if (!Level.isInSpawnableBounds(blockPos) || !this.controller.getWorld().isInWorldBounds(blockPos)) {
         return false;
      } else {
         return !this.mod.getWorld().getBlockState(blockPos).canBeReplaced()
            ? false
            : !checkEntities || this.mod.getWorld().isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), blockPos, CollisionContext.empty());
      }
   }

   public boolean canPlace(BlockPos blockPos) {
      return this.canPlace(blockPos, true);
   }

   public boolean swap(int slot) {
      if (slot == this.mod.getBaritone().getEntityContext().inventory().selectedSlot) {
         return true;
      } else if (slot >= 0 && slot <= 8) {
         this.mod.getBaritone().getEntityContext().inventory().selectedSlot = slot;
         return true;
      } else {
         return false;
      }
   }

   public double getYaw(Vec3 pos) {
      return this.mod.getPlayer().getYRot()
         + Mth.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(pos.z() - this.mod.getPlayer().getZ(), pos.x() - this.mod.getPlayer().getX()))
               - 90.0F
               - this.mod.getPlayer().getYRot()
         );
   }

   public double getPitch(Vec3 pos) {
      double diffX = pos.x() - this.mod.getPlayer().getX();
      double diffY = pos.y() - this.mod.getPlayer().getY() + this.mod.getPlayer().getEyeHeight(this.mod.getPlayer().getPose());
      double diffZ = pos.z() - this.mod.getPlayer().getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return this.mod.getPlayer().getXRot() + Mth.wrapDegrees((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))) - this.mod.getPlayer().getXRot());
   }
}
