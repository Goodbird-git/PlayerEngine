package com.player2.playerengine.tasks.misc;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.multiversion.blockpos.BlockPosVer;
import com.player2.playerengine.tasks.DoToClosestBlockTask;
import com.player2.playerengine.tasks.InteractWithBlockTask;
import com.player2.playerengine.tasks.construction.DestroyBlockTask;
import com.player2.playerengine.tasks.construction.PlaceStructureBlockTask;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.progresscheck.MovementProgressChecker;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.apache.commons.lang3.ArrayUtils;

public class PlaceBedAndSetSpawnTask extends Task {
   private final TimerGame regionScanTimer = new TimerGame(9.0);
   private final Vec3i BED_CLEAR_SIZE = new Vec3i(3, 2, 3);
   private final Vec3i[] BED_BOTTOM_PLATFORM = new Vec3i[]{
      new Vec3i(0, -1, 0),
      new Vec3i(1, -1, 0),
      new Vec3i(2, -1, 0),
      new Vec3i(0, -1, -1),
      new Vec3i(1, -1, -1),
      new Vec3i(2, -1, -1),
      new Vec3i(0, -1, 1),
      new Vec3i(1, -1, 1),
      new Vec3i(2, -1, 1)
   };
   private final Vec3i BED_PLACE_STAND_POS = new Vec3i(0, 0, 1);
   private final Vec3i BED_PLACE_POS = new Vec3i(1, 0, 1);
   private final Vec3i[] BED_PLACE_POS_OFFSET = new Vec3i[]{
      this.BED_PLACE_POS,
      this.BED_PLACE_POS.north(),
      this.BED_PLACE_POS.south(),
      this.BED_PLACE_POS.east(),
      this.BED_PLACE_POS.west(),
      this.BED_PLACE_POS.offset(-1, 0, 1),
      this.BED_PLACE_POS.offset(1, 0, 1),
      this.BED_PLACE_POS.offset(-1, 0, -1),
      this.BED_PLACE_POS.offset(1, 0, -1),
      this.BED_PLACE_POS.north(2),
      this.BED_PLACE_POS.south(2),
      this.BED_PLACE_POS.east(2),
      this.BED_PLACE_POS.west(2),
      this.BED_PLACE_POS.offset(-2, 0, 1),
      this.BED_PLACE_POS.offset(-2, 0, 2),
      this.BED_PLACE_POS.offset(2, 0, 1),
      this.BED_PLACE_POS.offset(2, 0, 2),
      this.BED_PLACE_POS.offset(-2, 0, -1),
      this.BED_PLACE_POS.offset(-2, 0, -2),
      this.BED_PLACE_POS.offset(2, 0, -1),
      this.BED_PLACE_POS.offset(2, 0, -2)
   };
   private final Direction BED_PLACE_DIRECTION = Direction.UP;
   private final TimerGame bedInteractTimeout = new TimerGame(5.0);
   private final TimerGame inBedTimer = new TimerGame(1.0);
   private final MovementProgressChecker progressChecker = new MovementProgressChecker();
   private boolean stayInBed;
   private BlockPos currentBedRegion;
   private BlockPos currentStructure;
   private BlockPos currentBreak;
   private boolean spawnSet;
   private boolean sleepAttemptMade;
   private boolean wasSleeping;
   private BlockPos bedForSpawnPoint;

   public PlaceBedAndSetSpawnTask stayInBed() {
      Debug.logInternal("Stay in bed method called");
      this.stayInBed = true;
      Debug.logInternal("Setting stayInBed to true");
      return this;
   }

   @Override
   protected void onStart() {
      PlayerEngineController mod = this.controller;
      mod.getBehaviour().push();
      this.progressChecker.reset();
      this.currentBedRegion = null;
      mod.getBehaviour()
         .avoidBlockPlacing(
            pos -> {
               if (this.currentBedRegion == null) {
                  return false;
               } else {
                  BlockPos start = this.currentBedRegion;
                  BlockPos end = this.currentBedRegion.offset(this.BED_CLEAR_SIZE);
                  return start.getX() <= pos.getX()
                     && pos.getX() < end.getX()
                     && start.getZ() <= pos.getZ()
                     && pos.getZ() < end.getZ()
                     && start.getY() <= pos.getY()
                     && pos.getY() < end.getY();
               }
            }
         );
      mod.getBehaviour().avoidBlockBreaking((Predicate<BlockPos>)(pos -> {
         if (this.currentBedRegion != null) {
            for (Vec3i baseOffs : this.BED_BOTTOM_PLATFORM) {
               BlockPos base = this.currentBedRegion.offset(baseOffs);
               if (base.equals(pos)) {
                  return true;
               }
            }
         }

         return mod.getWorld() != null ? mod.getWorld().getBlockState(pos).getBlock() instanceof BedBlock : false;
      }));
      this.spawnSet = false;
      this.sleepAttemptMade = false;
      this.wasSleeping = false;
      Debug.logInternal("Started onStart() method");
      Debug.logInternal("Current bed region: " + this.currentBedRegion);
      Debug.logInternal("Spawn set: " + this.spawnSet);
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (!this.progressChecker.check(mod) && this.currentBedRegion != null) {
         this.progressChecker.reset();
         Debug.logMessage("Searching new bed region.");
         this.currentBedRegion = null;
      }

      if (WorldHelper.isInNetherPortal(this.controller)) {
         this.setDebugState("We are in nether portal. Wandering");
         this.currentBedRegion = null;
         return new TimeoutWanderTask();
      } else if (WorldHelper.getCurrentDimension(this.controller) != Dimension.OVERWORLD) {
         this.setDebugState("Going to the overworld first.");
         return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
      } else if (mod.getBlockScanner()
         .anyFound(
            blockPosx -> WorldHelper.canReach(this.controller, blockPosx)
                  && blockPosx.closerToCenterThan(mod.getPlayer().position(), 40.0)
                  && mod.getItemStorage().hasItem(ItemHelper.BED)
               || WorldHelper.canReach(this.controller, blockPosx) && !mod.getItemStorage().hasItem(ItemHelper.BED),
            ItemHelper.itemsToBlocks(ItemHelper.BED)
         )) {
         this.setDebugState("Going to bed to sleep...");
         return new DoToClosestBlockTask(
            toSleepIn -> {
               boolean closeEnough = toSleepIn.closerThan(
                  new Vec3i((int)mod.getPlayer().position().x, (int)mod.getPlayer().position().y, (int)mod.getPlayer().position().z), 3.0
               );
               if (closeEnough) {
                  Vec3 centerBed = new Vec3(toSleepIn.getX() + 0.5, toSleepIn.getY() + 0.2, toSleepIn.getZ() + 0.5);
                  BlockHitResult hit = LookHelper.raycast(mod.getPlayer(), centerBed, 6.0);
                  closeEnough = false;
                  if (hit.getType() != Type.MISS) {
                     BlockPos p = hit.getBlockPos();
                     if (ArrayUtils.contains(ItemHelper.itemsToBlocks(ItemHelper.BED), mod.getWorld().getBlockState(p).getBlock())) {
                        closeEnough = true;
                     }
                  }
               }

               this.bedForSpawnPoint = WorldHelper.getBedHead(this.controller, toSleepIn);
               if (this.bedForSpawnPoint == null) {
                  this.bedForSpawnPoint = toSleepIn;
               }

               if (!closeEnough) {
                  try {
                     Direction face = (Direction)mod.getWorld().getBlockState(toSleepIn).getValue(BedBlock.FACING);
                     Direction side = face.getClockWise();
                     return new GetToBlockTask(this.bedForSpawnPoint.offset(side.getUnitVec3i()));
                  } catch (IllegalArgumentException var7) {
                  }
               } else {
                  this.inBedTimer.reset();
               }

               if (closeEnough) {
                  this.inBedTimer.reset();
               }

               this.progressChecker.reset();
               return new InteractWithBlockTask(this.bedForSpawnPoint);
            },
            ItemHelper.itemsToBlocks(ItemHelper.BED)
         );
      } else if (mod.getPlayer().isInWater() && mod.getItemStorage().hasItem(ItemHelper.BED)) {
         this.setDebugState("We are in water. Wandering");
         this.currentBedRegion = null;
         return new TimeoutWanderTask();
      } else {
         if (this.currentBedRegion != null) {
            for (Vec3i BedPlacePos : this.BED_PLACE_POS_OFFSET) {
               Block getBlock = mod.getWorld().getBlockState(this.currentBedRegion.offset(BedPlacePos)).getBlock();
               if (getBlock instanceof BedBlock) {
                  mod.getBlockScanner().addBlock(getBlock, this.currentBedRegion.offset(BedPlacePos));
                  break;
               }
            }
         }

         if (!mod.getItemStorage().hasItem(ItemHelper.BED)) {
            this.setDebugState("Getting a bed first");
            return TaskCatalogue.getItemTask("bed", 1);
         } else {
            if (this.currentBedRegion == null && this.regionScanTimer.elapsed()) {
               Debug.logMessage("Rescanning for nearby bed place position...");
               this.regionScanTimer.reset();
               this.currentBedRegion = this.locateBedRegion(mod, mod.getPlayer().blockPosition());
            }

            if (this.currentBedRegion == null) {
               this.setDebugState("Searching for spot to place bed, wandering...");
               return new TimeoutWanderTask();
            } else {
               for (Vec3i baseOffs : this.BED_BOTTOM_PLATFORM) {
                  BlockPos blockPos = this.currentBedRegion.offset(baseOffs);
                  if (!WorldHelper.isSolidBlock(this.controller, blockPos)) {
                     this.currentStructure = blockPos;
                     break;
                  }
               }

               label105:
               for (int dx = 0; dx < this.BED_CLEAR_SIZE.getX(); dx++) {
                  for (int dz = 0; dz < this.BED_CLEAR_SIZE.getZ(); dz++) {
                     for (int dy = 0; dy < this.BED_CLEAR_SIZE.getY(); dy++) {
                        BlockPos toClear = this.currentBedRegion.offset(dx, dy, dz);
                        if (WorldHelper.isSolidBlock(this.controller, toClear)) {
                           this.currentBreak = toClear;
                           break label105;
                        }
                     }
                  }
               }

               if (this.currentStructure != null) {
                  if (!WorldHelper.isSolidBlock(this.controller, this.currentStructure)) {
                     this.setDebugState("Placing structure for bed");
                     return new PlaceStructureBlockTask(this.currentStructure);
                  }

                  this.currentStructure = null;
               }

               if (this.currentBreak != null) {
                  if (WorldHelper.isSolidBlock(this.controller, this.currentBreak)) {
                     this.setDebugState("Clearing region for bed");
                     return new DestroyBlockTask(this.currentBreak);
                  }

                  this.currentBreak = null;
               }

               BlockPos toStand = this.currentBedRegion.offset(this.BED_PLACE_STAND_POS);
               if (!mod.getPlayer().blockPosition().equals(toStand)) {
                  return new GetToBlockTask(toStand);
               } else {
                  BlockPos toPlace = this.currentBedRegion.offset(this.BED_PLACE_POS);
                  if (mod.getWorld().getBlockState(toPlace.relative(this.BED_PLACE_DIRECTION)).getBlock() instanceof BedBlock) {
                     this.setDebugState("Waiting to rescan + find bed that we just placed. Should be almost instant.");
                     this.progressChecker.reset();
                     return null;
                  } else {
                     this.setDebugState("Placing bed...");
                     this.setDebugState("Filling in Portal");
                     if (!this.progressChecker.check(mod)) {
                        mod.getBaritone().getPathingBehavior().cancelEverything();
                        mod.getBaritone().getPathingBehavior().forceCancel();
                        mod.getBaritone().getExploreProcess().onLostControl();
                        mod.getBaritone().getCustomGoalProcess().onLostControl();
                        this.progressChecker.reset();
                     }

                     if (this.thisOrChildSatisfies(
                        task -> task instanceof InteractWithBlockTask intr
                           ? intr.getClickStatus() == InteractWithBlockTask.ClickResponse.CLICK_ATTEMPTED
                           : false
                     )) {
                        mod.getInputControls().tryPress(Input.MOVE_BACK);
                     }

                     return new InteractWithBlockTask(
                        new ItemTarget("bed", 1), this.BED_PLACE_DIRECTION, toPlace.relative(this.BED_PLACE_DIRECTION.getOpposite()), false
                     );
                  }
               }
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
      Debug.logInternal("Tracking stopped for beds");
      Debug.logInternal("Behaviour popped");
      Debug.logInternal("Unsubscribed from respawn point set message");
      Debug.logInternal("Unsubscribed from respawn failure message");
   }

   @Override
   protected boolean isEqual(Task other) {
      boolean isSameTask = other instanceof PlaceBedAndSetSpawnTask;
      if (!isSameTask) {
         Debug.logInternal("Tasks are not of the same type");
      }

      return isSameTask;
   }

   @Override
   protected String toDebugString() {
      return "Placing a bed nearby + resetting spawn point";
   }

   @Override
   public boolean isFinished() {
      if (WorldHelper.getCurrentDimension(this.controller) != Dimension.OVERWORLD) {
         Debug.logInternal("Can't place spawnpoint/sleep in a bed unless we're in the overworld!");
         return true;
      } else {
         boolean isSleeping = this.controller.getPlayer().isSleeping();
         boolean timerElapsed = this.inBedTimer.elapsed();
         boolean isFinished = this.spawnSet && !isSleeping && timerElapsed;
         Debug.logInternal("isSleeping: " + isSleeping);
         Debug.logInternal("timerElapsed: " + timerElapsed);
         Debug.logInternal("isFinished: " + isFinished);
         return isFinished;
      }
   }

   public BlockPos getBedSleptPos() {
      Debug.logInternal("Fetching bed slept position");
      return this.bedForSpawnPoint;
   }

   public boolean isSpawnSet() {
      Debug.logInternal("Checking if spawn is set");
      return this.spawnSet;
   }

   private BlockPos locateBedRegion(PlayerEngineController mod, BlockPos origin) {
      int SCAN_RANGE = 10;
      BlockPos closestGood = null;
      double closestDist = Double.POSITIVE_INFINITY;

      for (int x = origin.getX() - 10; x < origin.getX() + 10; x++) {
         for (int z = origin.getZ() - 10; z < origin.getZ() + 10; z++) {
            for (int y = origin.getY() - 10; y < origin.getY() + 10; y++) {
               BlockPos attemptPos = new BlockPos(x, y, z);
               double distance = BlockPosVer.getSquaredDistance(attemptPos, mod.getPlayer().position());
               Debug.logInternal("Checking position: " + attemptPos);
               if (distance > closestDist) {
                  Debug.logInternal("Skipping position: " + attemptPos);
               } else if (this.isGoodPosition(mod, attemptPos)) {
                  Debug.logInternal("Found good position: " + attemptPos);
                  closestGood = attemptPos;
                  closestDist = distance;
               }
            }
         }
      }

      return closestGood;
   }

   private boolean isGoodPosition(PlayerEngineController mod, BlockPos pos) {
      BlockPos BED_CLEAR_SIZE = new BlockPos(2, 1, 2);

      for (int x = 0; x < BED_CLEAR_SIZE.getX(); x++) {
         for (int y = 0; y < BED_CLEAR_SIZE.getY(); y++) {
            for (int z = 0; z < BED_CLEAR_SIZE.getZ(); z++) {
               BlockPos checkPos = pos.offset(x, y, z);
               if (!this.isGoodToPlaceInsideOrClear(mod, checkPos)) {
                  Debug.logInternal("Not a good position: " + checkPos);
                  return false;
               }
            }
         }
      }

      Debug.logInternal("Good position");
      return true;
   }

   private boolean isGoodToPlaceInsideOrClear(PlayerEngineController mod, BlockPos pos) {
      Vec3i[] CHECK = new Vec3i[]{
         new Vec3i(0, 0, 0), new Vec3i(-1, 0, 0), new Vec3i(1, 0, 0), new Vec3i(0, 1, 0), new Vec3i(0, -1, 0), new Vec3i(0, 0, 1), new Vec3i(0, 0, -1)
      };

      for (Vec3i offset : CHECK) {
         BlockPos newPos = pos.offset(offset);
         if (!this.isGoodAsBorder(mod, newPos)) {
            Debug.logInternal("Not good as border: " + newPos);
            return false;
         }
      }

      Debug.logInternal("Good to place inside or clear");
      return true;
   }

   private boolean isGoodAsBorder(PlayerEngineController mod, BlockPos pos) {
      boolean isSolid = WorldHelper.isSolidBlock(this.controller, pos);
      Debug.logInternal("isSolid: " + isSolid);
      if (isSolid) {
         boolean canBreak = WorldHelper.canBreak(this.controller, pos);
         Debug.logInternal("canBreak: " + canBreak);
         return canBreak;
      } else {
         boolean isAir = WorldHelper.isAir(this.controller, pos);
         Debug.logInternal("isAir: " + isAir);
         return isAir;
      }
   }
}
