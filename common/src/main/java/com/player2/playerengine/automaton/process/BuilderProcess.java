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

package com.player2.playerengine.automaton.process;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.automaton.api.pathing.goals.GoalComposite;
import com.player2.playerengine.automaton.api.pathing.goals.GoalGetToBlock;
import com.player2.playerengine.automaton.api.process.IBuilderProcess;
import com.player2.playerengine.automaton.api.process.PathingCommand;
import com.player2.playerengine.automaton.api.process.PathingCommandType;
import com.player2.playerengine.automaton.api.schematic.FillSchematic;
import com.player2.playerengine.automaton.api.schematic.ISchematic;
import com.player2.playerengine.automaton.api.schematic.IStaticSchematic;
import com.player2.playerengine.automaton.api.schematic.format.ISchematicFormat;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;
import com.player2.playerengine.automaton.api.utils.RayTraceUtils;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.RotationUtils;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.pathing.movement.CalculationContext;
import com.player2.playerengine.automaton.pathing.movement.Movement;
import com.player2.playerengine.automaton.pathing.movement.MovementHelper;
import com.player2.playerengine.automaton.utils.BaritoneProcessHelper;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import com.player2.playerengine.automaton.utils.PathingCommandContext;
import com.player2.playerengine.automaton.utils.schematic.MapArtSchematic;
import com.player2.playerengine.automaton.utils.schematic.SchematicSystem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BuilderProcess extends BaritoneProcessHelper implements IBuilderProcess {
   private HashSet<BetterBlockPos> incorrectPositions;
   private LongOpenHashSet observedCompleted;
   private String name;
   private ISchematic realSchematic;
   private ISchematic schematic;
   private Vec3i origin;
   private int ticks;
   private boolean paused;
   private int layer;
   private int numRepeats;
   private List<BlockState> approxPlaceable;

   public BuilderProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void build(String name, ISchematic schematic, Vec3i origin) {
      this.name = name;
      this.schematic = schematic;
      this.realSchematic = null;
      int x = origin.getX();
      int y = origin.getY();
      int z = origin.getZ();
      if (this.baritone.settings().schematicOrientationX.get()) {
         x += schematic.widthX();
      }

      if (this.baritone.settings().schematicOrientationY.get()) {
         y += schematic.heightY();
      }

      if (this.baritone.settings().schematicOrientationZ.get()) {
         z += schematic.lengthZ();
      }

      this.origin = new Vec3i(x, y, z);
      this.paused = false;
      this.layer = this.baritone.settings().startAtLayer.get();
      this.numRepeats = 0;
      this.observedCompleted = new LongOpenHashSet();
   }

   @Override
   public void resume() {
      this.paused = false;
   }

   @Override
   public void pause() {
      this.paused = true;
   }

   @Override
   public boolean isPaused() {
      return this.paused;
   }

   @Override
   public boolean build(String name, File schematic, Vec3i origin) {
      Optional<ISchematicFormat> format = SchematicSystem.INSTANCE.getByFile(schematic);
      if (format.isEmpty()) {
         return false;
      } else {
         ISchematic parsed;
         try {
            parsed = format.get().parse(new FileInputStream(schematic));
         } catch (Exception var7) {
            PlayerEngine.LOGGER.error(var7);
            return false;
         }

         if (this.baritone.settings().mapArtMode.get()) {
            parsed = new MapArtSchematic((IStaticSchematic)parsed);
         }

         this.build(name, parsed, origin);
         return true;
      }
   }

   @Override
   public void buildOpenSchematic() {
      this.logDirect("Schematica is not present");
   }

   @Override
   public void clearArea(BlockPos corner1, BlockPos corner2) {
      BlockPos origin = new BlockPos(
         Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()), Math.min(corner1.getZ(), corner2.getZ())
      );
      int widthX = Math.abs(corner1.getX() - corner2.getX()) + 1;
      int heightY = Math.abs(corner1.getY() - corner2.getY()) + 1;
      int lengthZ = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
      this.build("clear area", new FillSchematic(widthX, heightY, lengthZ, new BlockOptionalMeta(this.baritone.getEntityContext().world(), Blocks.AIR)), origin);
   }

   @Override
   public List<BlockState> getApproxPlaceable() {
      return new ArrayList<>(this.approxPlaceable);
   }

   @Override
   public boolean isActive() {
      return this.schematic != null;
   }

   public BlockState placeAt(int x, int y, int z, BlockState current) {
      if (!this.isActive()) {
         return null;
      } else if (!this.schematic.inSchematic(x - this.origin.getX(), y - this.origin.getY(), z - this.origin.getZ(), current)) {
         return null;
      } else {
         BlockState state = this.schematic.desiredState(x - this.origin.getX(), y - this.origin.getY(), z - this.origin.getZ(), current, this.approxPlaceable);
         return state.getBlock() instanceof AirBlock ? null : state;
      }
   }

   private Optional<Tuple<BetterBlockPos, Rotation>> toBreakNearPlayer(BuilderProcess.BuilderCalculationContext bcc) {
      BetterBlockPos center = this.ctx.feetPos();
      BetterBlockPos pathStart = this.baritone.getPathingBehavior().pathStart();

      for (int dx = -5; dx <= 5; dx++) {
         for (int dy = this.baritone.settings().breakFromAbove.get() ? -1 : 0; dy <= 5; dy++) {
            for (int dz = -5; dz <= 5; dz++) {
               int x = center.x + dx;
               int y = center.y + dy;
               int z = center.z + dz;
               if (dy != -1 || x != pathStart.x || z != pathStart.z) {
                  BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
                  if (desired != null) {
                     BlockState curr = bcc.bsi.get0(x, y, z);
                     if (!(curr.getBlock() instanceof AirBlock)
                        && curr.getBlock() != Blocks.WATER
                        && curr.getBlock() != Blocks.LAVA
                        && !this.valid(curr, desired, false)) {
                        BetterBlockPos pos = new BetterBlockPos(x, y, z);
                        Optional<Rotation> rot = RotationUtils.reachable(this.ctx.entity(), pos, this.ctx.playerController().getBlockReachDistance());
                        if (rot.isPresent()) {
                           return Optional.of(new Tuple(pos, rot.get()));
                        }
                     }
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   private Optional<BuilderProcess.Placement> searchForPlacables(BuilderProcess.BuilderCalculationContext bcc, List<BlockState> desirableOnHotbar) {
      BetterBlockPos center = this.ctx.feetPos();

      for (int dx = -5; dx <= 5; dx++) {
         for (int dy = -5; dy <= 1; dy++) {
            for (int dz = -5; dz <= 5; dz++) {
               int x = center.x + dx;
               int y = center.y + dy;
               int z = center.z + dz;
               BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
               if (desired != null) {
                  BlockState curr = bcc.bsi.get0(x, y, z);
                  if (MovementHelper.isReplaceable(x, y, z, curr, bcc.bsi)
                     && !this.valid(curr, desired, false)
                     && (dy != 1 || !(bcc.bsi.get0(x, y + 1, z).getBlock() instanceof AirBlock))) {
                     desirableOnHotbar.add(desired);
                     Optional<BuilderProcess.Placement> opt = this.possibleToPlace(desired, x, y, z, bcc.bsi);
                     if (opt.isPresent()) {
                        return opt;
                     }
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   public boolean placementPlausible(BlockPos pos, BlockState state) {
      VoxelShape voxelshape = state.getCollisionShape(this.ctx.world(), pos);
      return voxelshape.isEmpty() || this.ctx.world().isUnobstructed(null, voxelshape.move(pos.getX(), pos.getY(), pos.getZ()));
   }

   private Optional<BuilderProcess.Placement> possibleToPlace(BlockState toPlace, int x, int y, int z, BlockStateInterface bsi) {
      for (Direction against : Direction.values()) {
         BetterBlockPos placeAgainstPos = new BetterBlockPos(x, y, z).offset(against);
         BlockState placeAgainstState = bsi.get0(placeAgainstPos);
         if (!MovementHelper.isReplaceable(placeAgainstPos.x, placeAgainstPos.y, placeAgainstPos.z, placeAgainstState, bsi)
            && toPlace.canSurvive(this.ctx.world(), new BetterBlockPos(x, y, z))
            && this.placementPlausible(new BetterBlockPos(x, y, z), toPlace)) {
            AABB aabb = placeAgainstState.getShape(this.ctx.world(), placeAgainstPos).bounds();

            for (Vec3 placementMultiplier : aabbSideMultipliers(against)) {
               double placeX = placeAgainstPos.x + aabb.minX * placementMultiplier.x + aabb.maxX * (1.0 - placementMultiplier.x);
               double placeY = placeAgainstPos.y + aabb.minY * placementMultiplier.y + aabb.maxY * (1.0 - placementMultiplier.y);
               double placeZ = placeAgainstPos.z + aabb.minZ * placementMultiplier.z + aabb.maxZ * (1.0 - placementMultiplier.z);
               Rotation rot = RotationUtils.calcRotationFromVec3d(
                  RayTraceUtils.inferSneakingEyePosition(this.ctx.entity()), new Vec3(placeX, placeY, placeZ), this.ctx.entityRotations()
               );
               HitResult result = RayTraceUtils.rayTraceTowards(this.ctx.entity(), rot, this.ctx.playerController().getBlockReachDistance(), true);
               if (result != null
                  && result.getType() == Type.BLOCK
                  && ((BlockHitResult)result).getBlockPos().equals(placeAgainstPos)
                  && ((BlockHitResult)result).getDirection() == against.getOpposite()) {
                  OptionalInt hotbar = this.hasAnyItemThatWouldPlace(toPlace, result, rot);
                  if (hotbar.isPresent()) {
                     return Optional.of(new BuilderProcess.Placement(hotbar.getAsInt(), placeAgainstPos, against.getOpposite(), rot));
                  }
               }
            }
         }
      }

      return Optional.empty();
   }

   private OptionalInt hasAnyItemThatWouldPlace(BlockState desired, HitResult result, Rotation rot) {
      if (this.ctx.entity() instanceof IInventoryProvider provider) {
         LivingEntity var12 = this.ctx.entity();

         for (int i = 0; i < 9; i++) {
            ItemStack stack = (ItemStack)provider.getLivingInventory().main.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
               float originalYaw = var12.getYRot();
               float originalPitch = var12.getXRot();
               var12.setYRot(rot.getYaw());
               var12.setXRot(rot.getPitch());
               BlockPlaceContext meme = new BlockPlaceContext(
                  new UseOnContext(this.ctx.world(), null, InteractionHand.MAIN_HAND, stack, (BlockHitResult)result) {
                     public boolean isSecondaryUseActive() {
                        return false;
                     }
                  }
               );
               BlockState wouldBePlaced = ((BlockItem)stack.getItem()).getBlock().getStateForPlacement(meme);
               var12.setYRot(originalYaw);
               var12.setXRot(originalPitch);
               if (wouldBePlaced != null && meme.canPlace() && this.valid(wouldBePlaced, desired, true)) {
                  return OptionalInt.of(i);
               }
            }
         }

         return OptionalInt.empty();
      } else {
         return OptionalInt.empty();
      }
   }

   private static Vec3[] aabbSideMultipliers(Direction side) {
      switch (side) {
         case UP:
            return new Vec3[]{new Vec3(0.5, 1.0, 0.5), new Vec3(0.1, 1.0, 0.5), new Vec3(0.9, 1.0, 0.5), new Vec3(0.5, 1.0, 0.1), new Vec3(0.5, 1.0, 0.9)};
         case DOWN:
            return new Vec3[]{new Vec3(0.5, 0.0, 0.5), new Vec3(0.1, 0.0, 0.5), new Vec3(0.9, 0.0, 0.5), new Vec3(0.5, 0.0, 0.1), new Vec3(0.5, 0.0, 0.9)};
         case NORTH:
         case SOUTH:
         case EAST:
         case WEST:
            double x = side.getStepX() == 0 ? 0.5 : (1 + side.getStepX()) / 2.0;
            double z = side.getStepZ() == 0 ? 0.5 : (1 + side.getStepZ()) / 2.0;
            return new Vec3[]{new Vec3(x, 0.25, z), new Vec3(x, 0.75, z)};
         default:
            throw new IllegalStateException();
      }
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      return this.onTick(calcFailed, isSafeToCancel, 0);
   }

   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, int recursions) {
      if (recursions > 1000) {
         return new PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH);
      } else {
         LivingEntityInventory inventory = this.ctx.inventory();
         if (inventory == null) {
            this.schematic = null;
            return null;
         } else {
            this.approxPlaceable = this.approxPlaceable(36);
            if (this.baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)) {
               this.ticks = 5;
            } else {
               this.ticks--;
            }

            this.baritone.getInputOverrideHandler().clearAllKeys();
            if (this.paused) {
               return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            } else {
               if (this.baritone.settings().buildInLayers.get()) {
                  if (this.realSchematic == null) {
                     this.realSchematic = this.schematic;
                  }

                  final ISchematic realSchematic = this.realSchematic;
                  final int minYInclusive;
                  final int maxYInclusive;
                  if (this.baritone.settings().layerOrder.get()) {
                     maxYInclusive = realSchematic.heightY() - 1;
                     minYInclusive = realSchematic.heightY() - this.layer;
                  } else {
                     maxYInclusive = this.layer - 1;
                     minYInclusive = 0;
                  }

                  this.schematic = new ISchematic() {
                     @Override
                     public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
                        return realSchematic.desiredState(x, y, z, current, BuilderProcess.this.approxPlaceable);
                     }

                     @Override
                     public boolean inSchematic(int x, int y, int z, BlockState currentState) {
                        return ISchematic.super.inSchematic(x, y, z, currentState)
                           && y >= minYInclusive
                           && y <= maxYInclusive
                           && realSchematic.inSchematic(x, y, z, currentState);
                     }

                     @Override
                     public void reset() {
                        realSchematic.reset();
                     }

                     @Override
                     public int widthX() {
                        return realSchematic.widthX();
                     }

                     @Override
                     public int heightY() {
                        return realSchematic.heightY();
                     }

                     @Override
                     public int lengthZ() {
                        return realSchematic.lengthZ();
                     }
                  };
               }

               BuilderProcess.BuilderCalculationContext bcc = new BuilderProcess.BuilderCalculationContext();
               if (!this.recalc(bcc)) {
                  if (this.baritone.settings().buildInLayers.get() && this.layer < this.realSchematic.heightY()) {
                     this.logDirect("Starting layer " + this.layer);
                     this.layer++;
                     return this.onTick(calcFailed, isSafeToCancel, recursions + 1);
                  } else {
                     Vec3i repeat = this.baritone.settings().buildRepeat.get();
                     int max = this.baritone.settings().buildRepeatCount.get();
                     this.numRepeats++;
                     if (!repeat.equals(new Vec3i(0, 0, 0)) && (max == -1 || this.numRepeats < max)) {
                        this.layer = 0;
                        this.origin = new BlockPos(this.origin).offset(repeat);
                        if (!this.baritone.settings().buildRepeatSneaky.get()) {
                           this.schematic.reset();
                        }

                        this.logDirect("Repeating build in vector " + repeat + ", new origin is " + this.origin);
                        return this.onTick(calcFailed, isSafeToCancel, recursions + 1);
                     } else {
                        this.onLostControl();
                        return null;
                     }
                  }
               } else {
                  if (this.baritone.settings().distanceTrim.get()) {
                     this.trim();
                  }

                  Optional<Tuple<BetterBlockPos, Rotation>> toBreak = this.toBreakNearPlayer(bcc);
                  if (toBreak.isPresent() && isSafeToCancel && this.ctx.entity().onGround()) {
                     Rotation rot = (Rotation)toBreak.get().getB();
                     BetterBlockPos pos = (BetterBlockPos)toBreak.get().getA();
                     this.baritone.getLookBehavior().updateTarget(rot, true);
                     MovementHelper.switchToBestToolFor(this.ctx, bcc.get(pos));
                     if (this.ctx.entity().isShiftKeyDown()) {
                        this.baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                     }

                     if (this.ctx.isLookingAt(pos) || this.ctx.entityRotations().isReallyCloseTo(rot)) {
                        this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                     }

                     return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                  } else {
                     List<BlockState> desirableOnHotbar = new ArrayList<>();
                     Optional<BuilderProcess.Placement> toPlace = this.searchForPlacables(bcc, desirableOnHotbar);
                     if (toPlace.isPresent() && isSafeToCancel && this.ctx.entity().onGround() && this.ticks <= 0) {
                        Rotation rotx = toPlace.get().rot;
                        this.baritone.getLookBehavior().updateTarget(rotx, true);
                        inventory.selectedSlot = toPlace.get().hotbarSelection;
                        this.baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                        if (this.ctx.isLookingAt(toPlace.get().placeAgainst)
                              && ((BlockHitResult)this.ctx.objectMouseOver()).getDirection().equals(toPlace.get().side)
                           || this.ctx.entityRotations().isReallyCloseTo(rotx)) {
                           this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                        }

                        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                     } else {
                        if (this.baritone.settings().allowInventory.get()) {
                           ArrayList<Integer> usefulSlots = new ArrayList<>();
                           List<BlockState> noValidHotbarOption = new ArrayList<>();

                           label152:
                           for (BlockState desired : desirableOnHotbar) {
                              for (int i = 0; i < 9; i++) {
                                 if (this.valid(this.approxPlaceable.get(i), desired, true)) {
                                    usefulSlots.add(i);
                                    continue label152;
                                 }
                              }

                              noValidHotbarOption.add(desired);
                           }

                           label138:
                           for (int ix = 9; ix < 36; ix++) {
                              for (BlockState desired : noValidHotbarOption) {
                                 if (this.valid(this.approxPlaceable.get(ix), desired, true)) {
                                    this.baritone.getInventoryBehavior().attemptToPutOnHotbar(ix, usefulSlots::contains, inventory);
                                    break label138;
                                 }
                              }
                           }
                        }

                        Goal goal = this.assemble(bcc, this.approxPlaceable.subList(0, 9));
                        if (goal == null) {
                           goal = this.assemble(bcc, this.approxPlaceable, true);
                           if (goal == null) {
                              if (this.baritone.settings().skipFailedLayers.get()
                                 && this.baritone.settings().buildInLayers.get()
                                 && this.layer < this.realSchematic.heightY()) {
                                 this.logDirect("Skipping layer that I cannot construct! Layer #" + this.layer);
                                 this.layer++;
                                 return this.onTick(calcFailed, isSafeToCancel, recursions + 1);
                              }

                              this.logDirect("Unable to do it. Pausing. resume to resume, cancel to cancel");
                              this.paused = true;
                              return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                           }
                        }

                        return new PathingCommandContext(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH, bcc);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean recalc(BuilderProcess.BuilderCalculationContext bcc) {
      if (this.incorrectPositions == null) {
         this.incorrectPositions = new HashSet<>();
         this.fullRecalc(bcc);
         if (this.incorrectPositions.isEmpty()) {
            return false;
         }
      }

      this.recalcNearby(bcc);
      if (this.incorrectPositions.isEmpty()) {
         this.fullRecalc(bcc);
      }

      return !this.incorrectPositions.isEmpty();
   }

   private void trim() {
      HashSet<BetterBlockPos> copy = new HashSet<>(this.incorrectPositions);
      copy.removeIf(pos -> pos.distSqr(this.ctx.entity().blockPosition()) > 200.0);
      if (!copy.isEmpty()) {
         this.incorrectPositions = copy;
      }
   }

   private void recalcNearby(BuilderProcess.BuilderCalculationContext bcc) {
      BetterBlockPos center = this.ctx.feetPos();
      int radius = this.baritone.settings().builderTickScanRadius.get();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               int x = center.x + dx;
               int y = center.y + dy;
               int z = center.z + dz;
               BlockState desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z));
               if (desired != null) {
                  BetterBlockPos pos = new BetterBlockPos(x, y, z);
                  if (this.valid(bcc.bsi.get0(x, y, z), desired, false)) {
                     this.incorrectPositions.remove(pos);
                     this.observedCompleted.add(BetterBlockPos.longHash(pos));
                  } else {
                     this.incorrectPositions.add(pos);
                     this.observedCompleted.remove(BetterBlockPos.longHash(pos));
                  }
               }
            }
         }
      }
   }

   private void fullRecalc(BuilderProcess.BuilderCalculationContext bcc) {
      this.incorrectPositions = new HashSet<>();

      for (int y = 0; y < this.schematic.heightY(); y++) {
         for (int z = 0; z < this.schematic.lengthZ(); z++) {
            for (int x = 0; x < this.schematic.widthX(); x++) {
               int blockX = x + this.origin.getX();
               int blockY = y + this.origin.getY();
               int blockZ = z + this.origin.getZ();
               BlockState current = bcc.bsi.get0(blockX, blockY, blockZ);
               if (this.schematic.inSchematic(x, y, z, current)) {
                  if (bcc.bsi.worldContainsLoadedChunk(blockX, blockZ)) {
                     if (this.valid(bcc.bsi.get0(blockX, blockY, blockZ), this.schematic.desiredState(x, y, z, current, this.approxPlaceable), false)) {
                        this.observedCompleted.add(BetterBlockPos.longHash(blockX, blockY, blockZ));
                     } else {
                        this.incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                        this.observedCompleted.remove(BetterBlockPos.longHash(blockX, blockY, blockZ));
                        if (this.incorrectPositions.size() > this.baritone.settings().incorrectSize.get()) {
                           return;
                        }
                     }
                  } else if (!this.observedCompleted.contains(BetterBlockPos.longHash(blockX, blockY, blockZ))) {
                     this.incorrectPositions.add(new BetterBlockPos(blockX, blockY, blockZ));
                     if (this.incorrectPositions.size() > this.baritone.settings().incorrectSize.get()) {
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private Goal assemble(BuilderProcess.BuilderCalculationContext bcc, List<BlockState> approxPlaceable) {
      return this.assemble(bcc, approxPlaceable, false);
   }

   private Goal assemble(BuilderProcess.BuilderCalculationContext bcc, List<BlockState> approxPlaceable, boolean logMissing) {
      List<BetterBlockPos> placeable = new ArrayList<>();
      List<BetterBlockPos> breakable = new ArrayList<>();
      List<BetterBlockPos> sourceLiquids = new ArrayList<>();
      List<BetterBlockPos> flowingLiquids = new ArrayList<>();
      Map<BlockState, Integer> missing = new HashMap<>();
      this.incorrectPositions.forEach(pos -> {
         BlockState state = bcc.bsi.get0(pos);
         if (state.getBlock() instanceof AirBlock) {
            BlockState desired = bcc.getSchematic(pos.x, pos.y, pos.z, state);
            if (desired != null && approxPlaceable.stream().anyMatch(placeableState -> placeableState.getBlock() == desired.getBlock())) {
               placeable.add(pos);
            } else {
               missing.put(desired, 1 + missing.getOrDefault(desired, 0));
            }
         } else if (state.getBlock() instanceof LiquidBlock) {
            if (!MovementHelper.possiblyFlowing(state)) {
               sourceLiquids.add(pos);
            } else {
               flowingLiquids.add(pos);
            }
         } else {
            breakable.add(pos);
         }
      });
      List<Goal> toBreak = new ArrayList<>();
      breakable.forEach(pos -> toBreak.add(this.breakGoal(pos, bcc)));
      List<Goal> toPlace = new ArrayList<>();
      placeable.forEach(pos -> {
         if (!placeable.contains(pos.down()) && !placeable.contains(pos.down(2))) {
            toPlace.add(this.placementGoal(pos, bcc));
         }
      });
      sourceLiquids.forEach(pos -> toPlace.add(new GoalBlock(pos.up())));
      if (!toPlace.isEmpty()) {
         return new BuilderProcess.JankyGoalComposite(new GoalComposite(toPlace.toArray(new Goal[0])), new GoalComposite(toBreak.toArray(new Goal[0])));
      } else if (toBreak.isEmpty()) {
         if (logMissing && !missing.isEmpty()) {
            this.logDirect("Missing materials for at least:");
            this.logDirect(missing.entrySet().stream().map(e -> String.format("%sx %s", e.getValue(), e.getKey())).collect(Collectors.joining("\n")));
         }

         if (logMissing && !flowingLiquids.isEmpty()) {
            this.logDirect("Unreplaceable liquids at at least:");
            this.logDirect(flowingLiquids.stream().map(p -> String.format("%s %s %s", p.x, p.y, p.z)).collect(Collectors.joining("\n")));
         }

         return null;
      } else {
         return new GoalComposite(toBreak.toArray(new Goal[0]));
      }
   }

   private Goal placementGoal(BlockPos pos, BuilderProcess.BuilderCalculationContext bcc) {
      if (!(this.ctx.world().getBlockState(pos).getBlock() instanceof AirBlock)) {
         return new BuilderProcess.GoalPlace(pos);
      } else {
         boolean allowSameLevel = !(this.ctx.world().getBlockState(pos.above()).getBlock() instanceof AirBlock);
         BlockState current = this.ctx.world().getBlockState(pos);

         for (Direction facing : Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP) {
            if (bcc.canPlaceAgainst(pos.relative(facing)) && this.placementPlausible(pos, bcc.getSchematic(pos.getX(), pos.getY(), pos.getZ(), current))) {
               return new BuilderProcess.GoalAdjacent(pos, pos.relative(facing), allowSameLevel);
            }
         }

         return new BuilderProcess.GoalPlace(pos);
      }
   }

   private Goal breakGoal(BlockPos pos, BuilderProcess.BuilderCalculationContext bcc) {
      return (Goal)(this.baritone.settings().goalBreakFromAbove.get()
            && bcc.bsi.get0(pos.above()).getBlock() instanceof AirBlock
            && bcc.bsi.get0(pos.above(2)).getBlock() instanceof AirBlock
         ? new BuilderProcess.JankyGoalComposite(new BuilderProcess.GoalBreak(pos), new GoalGetToBlock(pos.above()) {
            @Override
            public boolean isInGoal(int x, int y, int z) {
               return y <= this.y && (x != this.x || y != this.y || z != this.z) ? super.isInGoal(x, y, z) : false;
            }
         })
         : new BuilderProcess.GoalBreak(pos));
   }

   @Override
   public void onLostControl() {
      this.incorrectPositions = null;
      this.name = null;
      this.schematic = null;
      this.realSchematic = null;
      this.layer = this.baritone.settings().startAtLayer.get();
      this.numRepeats = 0;
      this.paused = false;
      this.observedCompleted = null;
   }

   @Override
   public String displayName0() {
      return this.paused ? "Builder Paused" : "Building " + this.name;
   }

   private List<BlockState> approxPlaceable(int size) {
      List<BlockState> result = new ArrayList<>();

      for (int i = 0; i < size; i++) {
         ItemStack stack = (ItemStack)((IInventoryProvider)this.ctx.entity()).getLivingInventory().main.get(i);
         if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
            BlockState placementState = null;
            try {
                placementState = ((BlockItem) stack.getItem())
                        .getBlock()
                        .getStateForPlacement(
                                new BlockPlaceContext(
                                        new UseOnContext(
                                                this.ctx.world(),
                                                null,
                                                InteractionHand.MAIN_HAND,
                                                stack,
                                                new BlockHitResult(
                                                        new Vec3(this.ctx.entity().getX(), this.ctx.entity().getY(), this.ctx.entity().getZ()), Direction.UP, this.ctx.feetPos(), false
                                                )
                                        ) {
                                            public boolean isSecondaryUseActive() {
                                                return false;
                                            }
                                        }
                                )
                        );
            } catch (Exception ignored){}
            if (placementState != null) {
               result.add(placementState);
            } else {
               result.add(Blocks.AIR.defaultBlockState());
            }
         } else {
            result.add(Blocks.AIR.defaultBlockState());
         }
      }

      return result;
   }

   private boolean valid(BlockState current, BlockState desired, boolean itemVerify) {
      if (desired == null) {
         return true;
      } else if (current.getBlock() instanceof LiquidBlock && this.baritone.settings().okIfWater.get()) {
         return true;
      } else if (current.isAir() && desired.isAir()) {
         return true;
      } else if (current.isAir() && desired.is(this.baritone.settings().okIfAir.get())) {
         return true;
      } else if (desired.isAir() && current.is(this.baritone.settings().buildIgnoreBlocks.get())) {
         return true;
      } else {
         return !current.isAir() && this.baritone.settings().buildIgnoreExisting.get() && !itemVerify ? true : current.getBlock() == desired.getBlock();
      }
   }

   public class BuilderCalculationContext extends CalculationContext {
      private final List<BlockState> placeable = BuilderProcess.this.approxPlaceable(9);
      private final ISchematic schematic;
      private final int originX;
      private final int originY;
      private final int originZ;

      public BuilderCalculationContext() {
         super(BuilderProcess.this.baritone, true);
         this.schematic = BuilderProcess.this.schematic;
         this.originX = BuilderProcess.this.origin.getX();
         this.originY = BuilderProcess.this.origin.getY();
         this.originZ = BuilderProcess.this.origin.getZ();
         this.jumpPenalty += 10.0;
         this.backtrackCostFavoringCoefficient = 1.0;
      }

      private BlockState getSchematic(int x, int y, int z, BlockState current) {
         return this.schematic.inSchematic(x - this.originX, y - this.originY, z - this.originZ, current)
            ? this.schematic.desiredState(x - this.originX, y - this.originY, z - this.originZ, current, BuilderProcess.this.approxPlaceable)
            : null;
      }

      @Override
      public double costOfPlacingAt(int x, int y, int z, BlockState current) {
         if (this.isProtected(x, y, z)) {
            return 1000000.0;
         } else {
            BlockState sch = this.getSchematic(x, y, z, current);
            if (sch != null) {
               if (sch.getBlock() instanceof AirBlock) {
                  return this.placeBlockCost * 2.0;
               } else if (this.placeable.contains(sch)) {
                  return 0.0;
               } else {
                  return !this.hasThrowaway ? 1000000.0 : this.placeBlockCost * 3.0;
               }
            } else {
               return this.hasThrowaway ? this.placeBlockCost : 1000000.0;
            }
         }
      }

      @Override
      public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
         if (this.allowBreak && !this.isProtected(x, y, z)) {
            BlockState sch = this.getSchematic(x, y, z, current);
            if (sch == null) {
               return 1.0;
            } else if (sch.getBlock() instanceof AirBlock) {
               return 1.0;
            } else {
               return BuilderProcess.this.valid(this.bsi.get0(x, y, z), sch, false) ? this.baritone.settings().breakCorrectBlockPenaltyMultiplier.get() : 1.0;
            }
         } else {
            return 1000000.0;
         }
      }
   }

   public static class GoalAdjacent extends GoalGetToBlock {
      private boolean allowSameLevel;
      private BlockPos no;

      public GoalAdjacent(BlockPos pos, BlockPos no, boolean allowSameLevel) {
         super(pos);
         this.no = no;
         this.allowSameLevel = allowSameLevel;
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         if (x == this.x && y == this.y && z == this.z) {
            return false;
         } else if (x == this.no.getX() && y == this.no.getY() && z == this.no.getZ()) {
            return false;
         } else if (!this.allowSameLevel && y == this.y - 1) {
            return false;
         } else {
            return y < this.y - 1 ? false : super.isInGoal(x, y, z);
         }
      }

      @Override
      public double heuristic(int x, int y, int z) {
         return this.y * 100 + super.heuristic(x, y, z);
      }
   }

   public static class GoalBreak extends GoalGetToBlock {
      public GoalBreak(BlockPos pos) {
         super(pos);
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return y > this.y ? false : super.isInGoal(x, y, z);
      }
   }

   public static class GoalPlace extends GoalBlock {
      public GoalPlace(BlockPos placeAt) {
         super(placeAt.above());
      }

      @Override
      public double heuristic(int x, int y, int z) {
         return this.y * 100 + super.heuristic(x, y, z);
      }
   }

   public static class JankyGoalComposite implements Goal {
      private final Goal primary;
      private final Goal fallback;

      public JankyGoalComposite(Goal primary, Goal fallback) {
         this.primary = primary;
         this.fallback = fallback;
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return this.primary.isInGoal(x, y, z) || this.fallback.isInGoal(x, y, z);
      }

      @Override
      public double heuristic(int x, int y, int z) {
         return this.primary.heuristic(x, y, z);
      }

      @Override
      public String toString() {
         return "JankyComposite Primary: " + this.primary + " Fallback: " + this.fallback;
      }
   }

   public static class Placement {
      private final int hotbarSelection;
      private final BlockPos placeAgainst;
      private final Direction side;
      private final Rotation rot;

      public Placement(int hotbarSelection, BlockPos placeAgainst, Direction side, Rotation rot) {
         this.hotbarSelection = hotbarSelection;
         this.placeAgainst = placeAgainst;
         this.side = side;
         this.rot = rot;
      }
   }
}
