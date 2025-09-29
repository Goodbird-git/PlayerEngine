package com.player2.playerengine.util.helpers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.multiversion.MethodWrapper;
import com.player2.playerengine.multiversion.world.WorldVer;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.automaton.pathing.movement.CalculationContext;
import com.player2.playerengine.automaton.pathing.movement.MovementHelper;
import com.player2.playerengine.automaton.process.MineProcess;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface WorldHelper {
   static Vec3 toVec3d(BlockPos pos) {
      return pos == null ? null : new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
   }

   static Vec3 toVec3d(Vec3i pos) {
      return new Vec3(pos.getX(), pos.getY(), pos.getZ());
   }

   static Vec3i toVec3i(Vec3 pos) {
      return new Vec3i((int)pos.x(), (int)pos.y(), (int)pos.z());
   }

   static BlockPos toBlockPos(Vec3 pos) {
      return new BlockPos((int)pos.x(), (int)pos.y(), (int)pos.z());
   }

   static boolean isSourceBlock(PlayerEngineController controller, BlockPos pos, boolean onlyAcceptStill) {
      Level world = controller.getWorld();
      BlockState s = world.getBlockState(pos);
      if (s.getBlock() instanceof LiquidBlock) {
         if (!s.getFluidState().isSource() && onlyAcceptStill) {
            return false;
         } else {
            int level = s.getFluidState().getAmount();
            BlockState above = world.getBlockState(pos.above());
            return above.getBlock() instanceof LiquidBlock ? false : level == 8;
         }
      } else {
         return false;
      }
   }

   static double distanceXZSquared(Vec3 from, Vec3 to) {
      Vec3 delta = to.subtract(from);
      return delta.x * delta.x + delta.z * delta.z;
   }

   static double distanceXZ(Vec3 from, Vec3 to) {
      return Math.sqrt(distanceXZSquared(from, to));
   }

   static boolean inRangeXZ(Vec3 from, Vec3 to, double range) {
      return distanceXZSquared(from, to) < range * range;
   }

   static boolean inRangeXZ(BlockPos from, BlockPos to, double range) {
      return inRangeXZ(toVec3d(from), toVec3d(to), range);
   }

   static boolean inRangeXZ(Entity entity, Vec3 to, double range) {
      return inRangeXZ(entity.position(), to, range);
   }

   static boolean inRangeXZ(Entity entity, BlockPos to, double range) {
      return inRangeXZ(entity, toVec3d(to), range);
   }

   static boolean inRangeXZ(Entity entity, Entity to, double range) {
      return inRangeXZ(entity, to.position(), range);
   }

   static Dimension getCurrentDimension(PlayerEngineController controller) {
      Level world = controller.getWorld();
      if (world == null) {
         return Dimension.OVERWORLD;
      } else if (world.dimensionType().ultraWarm()) {
         return Dimension.NETHER;
      } else {
         return world.dimensionType().natural() ? Dimension.OVERWORLD : Dimension.END;
      }
   }

   static boolean isSolidBlock(PlayerEngineController controller, BlockPos pos) {
      Level world = controller.getWorld();
      return world.getBlockState(pos).isRedstoneConductor(world, pos);
   }

   static BlockPos getBedHead(PlayerEngineController controller, BlockPos posWithBed) {
      Level world = controller.getWorld();
      BlockState state = world.getBlockState(posWithBed);
      if (state.getBlock() instanceof BedBlock) {
         Direction facing = (Direction)state.getValue(BedBlock.FACING);
         return ((BedPart)world.getBlockState(posWithBed).getValue(BedBlock.PART)).equals(BedPart.HEAD) ? posWithBed : posWithBed.relative(facing);
      } else {
         return null;
      }
   }

   static BlockPos getBedFoot(PlayerEngineController controller, BlockPos posWithBed) {
      Level world = controller.getWorld();
      BlockState state = world.getBlockState(posWithBed);
      if (state.getBlock() instanceof BedBlock) {
         Direction facing = (Direction)state.getValue(BedBlock.FACING);
         return ((BedPart)world.getBlockState(posWithBed).getValue(BedBlock.PART)).equals(BedPart.FOOT)
            ? posWithBed
            : posWithBed.relative(facing.getOpposite());
      } else {
         return null;
      }
   }

   static int getGroundHeight(PlayerEngineController controller, int x, int z) {
      Level world = controller.getWorld();

      for (int y = world.getMaxY(); y >= world.getMinY(); y--) {
         BlockPos check = new BlockPos(x, y, z);
         if (isSolidBlock(controller, check)) {
            return y;
         }
      }

      return -1;
   }

   static BlockPos getADesertTemple(PlayerEngineController controller) {
      Level world = controller.getWorld();
      List<BlockPos> stonePressurePlates = controller.getBlockScanner().getKnownLocations(Blocks.STONE_PRESSURE_PLATE);
      if (!stonePressurePlates.isEmpty()) {
         for (BlockPos pos : stonePressurePlates) {
            if (world.getBlockState(pos).getBlock() == Blocks.STONE_PRESSURE_PLATE
               && world.getBlockState(pos.below()).getBlock() == Blocks.CUT_SANDSTONE
               && world.getBlockState(pos.below(2)).getBlock() == Blocks.TNT) {
               return pos;
            }
         }
      }

      return null;
   }

   static boolean isUnopenedChest(PlayerEngineController controller, BlockPos pos) {
      return controller.getItemStorage().getContainerAtPosition(pos).isEmpty();
   }

   static int getGroundHeight(PlayerEngineController controller, int x, int z, Block... groundBlocks) {
      Level world = controller.getWorld();
      Set<Block> possibleBlocks = new HashSet<>(Arrays.asList(groundBlocks));

      for (int y = world.getMaxY(); y >= world.getMinY(); y--) {
         BlockPos check = new BlockPos(x, y, z);
         if (possibleBlocks.contains(world.getBlockState(check).getBlock())) {
            return y;
         }
      }

      return -1;
   }

   static boolean canBreak(PlayerEngineController controller, BlockPos pos) {
      boolean prevInteractionPaused = controller.getExtraBaritoneSettings().isInteractionPaused();
      controller.getExtraBaritoneSettings().setInteractionPaused(false);
      boolean canBreak = controller.getWorld().getBlockState(pos).getDestroySpeed(controller.getWorld(), pos) >= 0.0F
         && !controller.getExtraBaritoneSettings().shouldAvoidBreaking(pos)
         && MineProcess.plausibleToBreak(new CalculationContext(controller.getBaritone()), pos)
         && canReach(controller, pos);
      controller.getExtraBaritoneSettings().setInteractionPaused(prevInteractionPaused);
      return canBreak;
   }

   static boolean isInNetherPortal(PlayerEngineController controller) {
      LivingEntity player = controller.getPlayer();
      return player == null ? false : player.portalProcess.isInsidePortalThisTick();
   }

   static boolean canPlace(PlayerEngineController controller, BlockPos pos) {
      return !controller.getExtraBaritoneSettings().shouldAvoidPlacingAt(pos) && canReach(controller, pos);
   }

   static boolean canReach(PlayerEngineController controller, BlockPos pos) {
      return controller.getModSettings().shouldAvoidOcean()
            && controller.getPlayer().getY() > 47.0
            && controller.getChunkTracker().isChunkLoaded(pos)
            && isOcean(controller.getWorld().getBiome(pos))
            && pos.getY() < 64
            && getGroundHeight(controller, pos.getX(), pos.getZ(), Blocks.WATER) > pos.getY()
         ? false
         : !controller.getBlockScanner().isUnreachable(pos);
   }

   static boolean isOcean(Holder<Biome> b) {
      return WorldVer.isBiome(b, Biomes.OCEAN)
         || WorldVer.isBiome(b, Biomes.COLD_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_COLD_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_FROZEN_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_LUKEWARM_OCEAN)
         || WorldVer.isBiome(b, Biomes.LUKEWARM_OCEAN)
         || WorldVer.isBiome(b, Biomes.WARM_OCEAN)
         || WorldVer.isBiome(b, Biomes.FROZEN_OCEAN);
   }

   static boolean isAir(PlayerEngineController controller, BlockPos pos) {
      return controller.getBlockScanner().isBlockAtPosition(pos, Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR);
   }

   static boolean isAir(Block block) {
      return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
   }

   static boolean isInteractableBlock(PlayerEngineController controller, BlockPos pos) {
      Block block = controller.getWorld().getBlockState(pos).getBlock();
      return block instanceof ChestBlock
         || block instanceof EnderChestBlock
         || block instanceof CraftingTableBlock
         || block instanceof AbstractFurnaceBlock
         || block instanceof LoomBlock
         || block instanceof CartographyTableBlock
         || block instanceof EnchantingTableBlock
         || block instanceof RedStoneOreBlock
         || block instanceof BarrelBlock;
   }

   static boolean isInsidePlayer(PlayerEngineController controller, BlockPos pos) {
      return pos.closerToCenterThan(controller.getPlayer().position(), 2.0);
   }

   static Iterable<BlockPos> getBlocksTouchingPlayer(LivingEntity player) {
      return getBlocksTouchingBox(player.getBoundingBox());
   }

   static Iterable<BlockPos> getBlocksTouchingBox(AABB box) {
      BlockPos min = new BlockPos((int)box.minX, (int)box.minY, (int)box.minZ);
      BlockPos max = new BlockPos((int)box.maxX, (int)box.maxY, (int)box.maxZ);
      return scanRegion(min, max);
   }

   static Iterable<BlockPos> scanRegion(BlockPos start, BlockPos end) {
      return () -> new Iterator<BlockPos>() {
         int x = start.getX();
         int y = start.getY();
         int z = start.getZ();

         @Override
         public boolean hasNext() {
            return this.y <= end.getY() && this.z <= end.getZ() && this.x <= end.getX();
         }

         public BlockPos next() {
            BlockPos result = new BlockPos(this.x, this.y, this.z);
            this.x++;
            if (this.x > end.getX()) {
               this.x = start.getX();
               this.z++;
               if (this.z > end.getZ()) {
                  this.z = start.getZ();
                  this.y++;
               }
            }

            return result;
         }
      };
   }

   static boolean fallingBlockSafeToBreak(PlayerEngineController controller, BlockPos pos) {
      BlockStateInterface bsi = new BlockStateInterface(controller.getBaritone().getEntityContext());
      Level clientWorld = controller.getWorld();
      if (clientWorld == null) {
         throw new AssertionError();
      } else {
         while (isFallingBlock(controller, pos)) {
            if (MovementHelper.avoidBreaking(bsi, pos.getX(), pos.getY(), pos.getZ(), clientWorld.getBlockState(pos), controller.getBaritoneSettings())) {
               return false;
            }

            pos = pos.above();
         }

         return true;
      }
   }

   static boolean isFallingBlock(PlayerEngineController controller, BlockPos pos) {
      Level clientWorld = controller.getWorld();
      if (clientWorld == null) {
         throw new AssertionError();
      } else {
         return clientWorld.getBlockState(pos).getBlock() instanceof FallingBlock;
      }
   }

   static Entity getSpawnerEntity(PlayerEngineController controller, BlockPos pos) {
      Level world = controller.getWorld();
      BlockState state = world.getBlockState(pos);
      return state.getBlock() instanceof SpawnerBlock && world.getBlockEntity(pos) instanceof SpawnerBlockEntity blockEntity
         ? MethodWrapper.getRenderedEntity(blockEntity.getSpawner(), world, pos)
         : null;
   }

   static boolean isChest(PlayerEngineController controller, BlockPos block) {
      Block b = controller.getWorld().getBlockState(block).getBlock();
      return isChest(b);
   }

   static boolean isChest(Block b) {
      return b instanceof ChestBlock || b instanceof EnderChestBlock;
   }

   static boolean isBlock(PlayerEngineController controller, BlockPos pos, Block block) {
      return controller.getWorld().getBlockState(pos).getBlock() == block;
   }

   static boolean canSleep(PlayerEngineController controller) {
      Level world = controller.getWorld();
      if (world != null) {
         if (world.isThundering() && world.isRaining()) {
            return true;
         } else {
            int time = getTimeOfDay(controller);
            return 12542 <= time && time <= 23992;
         }
      } else {
         return false;
      }
   }

   static int getTimeOfDay(PlayerEngineController controller) {
      Level world = controller.getWorld();
      return world != null ? (int)(world.getDayTime() % 24000L) : 0;
   }

   static boolean isVulnerable(LivingEntity player) {
      int armor = player.getArmorValue();
      float health = player.getHealth();
      if (armor <= 15 && health < 3.0F) {
         return true;
      } else {
         return armor < 10 && health < 10.0F ? true : armor < 5 && health < 18.0F;
      }
   }

   static boolean isSurroundedByHostiles(PlayerEngineController controller) {
      List<LivingEntity> hostiles = controller.getEntityTracker().getHostiles();
      return isSurrounded(controller, hostiles);
   }

   static boolean isSurrounded(PlayerEngineController controller, List<LivingEntity> entities) {
      LivingEntity player = controller.getPlayer();
      BlockPos playerPos = player.blockPosition();
      int MIN_SIDES_TO_SURROUND = 2;
      List<Direction> uniqueSides = new ArrayList<>();

      for (Entity entity : entities) {
         if (entity.closerThan(player, 8.0)) {
            BlockPos entityPos = entity.blockPosition();
            double angle = calculateAngle(playerPos, entityPos);
            boolean isUnique = !uniqueSides.contains(getHorizontalDirectionFromYaw(angle));
            if (isUnique) {
               uniqueSides.add(getHorizontalDirectionFromYaw(angle));
            }
         }
      }

      return uniqueSides.size() >= 2;
   }

   private static double calculateAngle(BlockPos origin, BlockPos target) {
      double translatedX = target.getX() - origin.getX();
      double translatedZ = target.getZ() - origin.getZ();
      double angleRad = Math.atan2(translatedZ, translatedX);
      double angleDeg = Math.toDegrees(angleRad);
      angleDeg -= 90.0;
      if (angleDeg < 0.0) {
         angleDeg += 360.0;
      }

      return angleDeg;
   }

   private static Direction getHorizontalDirectionFromYaw(double yaw) {
      yaw %= 360.0;
      if (yaw < 0.0) {
         yaw += 360.0;
      }

      if ((!(yaw >= 45.0) || !(yaw < 135.0)) && (!(yaw >= -315.0) || !(yaw < -225.0))) {
         if ((!(yaw >= 135.0) || !(yaw < 225.0)) && (!(yaw >= -225.0) || !(yaw < -135.0))) {
            return (!(yaw >= 225.0) || !(yaw < 315.0)) && (!(yaw >= -135.0) || !(yaw < -45.0)) ? Direction.SOUTH : Direction.EAST;
         } else {
            return Direction.NORTH;
         }
      } else {
         return Direction.WEST;
      }
   }
}
