package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.events.BlockPlaceEvent;
import com.player2.playerengine.multiversion.blockpos.BlockPosVer;
import com.player2.playerengine.trackers.blacklisting.WorldLocateBlacklist;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.helpers.BaritoneHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.time.TimerGame;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class BlockScanner {
   private static final boolean LOG = false;
   private static final int RESCAN_TICK_DELAY = 80;
   private static final int CACHED_POSITIONS_PER_BLOCK = 40;
   private final PlayerEngineController mod;
   private final TimerGame rescanTimer = new TimerGame(1.0);
   private final HashMap<Block, HashSet<BlockPos>> trackedBlocks = new HashMap<>();
   private final HashMap<Block, HashSet<BlockPos>> scannedBlocks = new HashMap<>();
   private final HashMap<ChunkPos, Long> scannedChunks = new HashMap<>();
   private final WorldLocateBlacklist blacklist = new WorldLocateBlacklist();
   private HashMap<Block, HashSet<BlockPos>> cachedScannedBlocks = new HashMap<>();
   private Dimension scanDimension = Dimension.OVERWORLD;
   private Level scanWorld = null;
   private boolean scanning = false;
   private boolean forceStop = false;

   public BlockScanner(PlayerEngineController mod) {
      this.mod = mod;
      EventBus.subscribe(BlockPlaceEvent.class, evt -> this.addBlock(evt.blockState.getBlock(), evt.blockPos));
   }

   public void addBlock(Block block, BlockPos pos) {
      if (!this.isBlockAtPosition(pos, block)) {
         Debug.logInternal("INVALID SET: " + block + " " + pos);
      } else {
         if (this.trackedBlocks.containsKey(block)) {
            this.trackedBlocks.get(block).add(pos);
         } else {
            HashSet<BlockPos> set = new HashSet<>();
            set.add(pos);
            this.trackedBlocks.put(block, set);
         }
      }
   }

   public void requestBlockUnreachable(BlockPos pos, int allowedFailures) {
      this.blacklist.blackListItem(this.mod, pos, allowedFailures);
   }

   public void requestBlockUnreachable(BlockPos pos) {
      this.blacklist.blackListItem(this.mod, pos, 4);
   }

   public boolean isUnreachable(BlockPos pos) {
      return this.blacklist.unreachable(pos);
   }

   public List<BlockPos> getKnownLocationsIncludeUnreachable(Block... blocks) {
      List<BlockPos> locations = new LinkedList<>();

      for (Block block : blocks) {
         if (this.trackedBlocks.containsKey(block)) {
            locations.addAll(this.trackedBlocks.get(block));
         }
      }

      return locations;
   }

   public List<BlockPos> getKnownLocations(Block... blocks) {
      List<BlockPos> locations = this.getKnownLocationsIncludeUnreachable(blocks);
      locations.removeIf(this::isUnreachable);
      return locations;
   }

   public Optional<BlockPos> getNearestWithinRange(Vec3 pos, double range, Block... blocks) {
      Optional<BlockPos> nearest = this.getNearestBlock(pos, blocks);
      return !nearest.isEmpty() && !nearest.get().closerThan(new Vec3i((int)pos.x, (int)pos.y, (int)pos.z), range) ? Optional.empty() : nearest;
   }

   public Optional<BlockPos> getNearestWithinRange(BlockPos pos, double range, Block... blocks) {
      return this.getNearestWithinRange(new Vec3(pos.getX(), pos.getY(), pos.getZ()), range, blocks);
   }

   public boolean anyFound(Block... blocks) {
      return this.anyFound(block -> true, blocks);
   }

   public boolean anyFound(Predicate<BlockPos> isValidTest, Block... blocks) {
      for (Block block : blocks) {
         if (this.trackedBlocks.containsKey(block)) {
            for (BlockPos pos : this.trackedBlocks.get(block)) {
               if (isValidTest.test(pos) && this.mod.getWorld().getBlockState(pos).getBlock().equals(block) && !this.isUnreachable(pos)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public Optional<BlockPos> getNearestBlock(Block... blocks) {
      return this.getNearestBlock(this.mod.getPlayer().position().add(0.0, 0.6F, 0.0), blocks);
   }

   public Optional<BlockPos> getNearestBlock(Vec3 pos, Block... blocks) {
      return this.getNearestBlock(pos, p -> true, blocks);
   }

   public Optional<BlockPos> getNearestBlock(Predicate<BlockPos> isValidTest, Block... blocks) {
      return this.getNearestBlock(this.mod.getPlayer().position().add(0.0, 0.6F, 0.0), isValidTest, blocks);
   }

   public Optional<BlockPos> getNearestBlock(Vec3 pos, Predicate<BlockPos> isValidTest, Block... blocks) {
      Optional<BlockPos> closest = Optional.empty();

      for (Block block : blocks) {
         Optional<BlockPos> p = this.getNearestBlock(block, isValidTest, pos);
         if (p.isPresent()) {
            if (closest.isEmpty()) {
               closest = p;
            } else if (BaritoneHelper.calculateGenericHeuristic(pos, WorldHelper.toVec3d(closest.get()))
               > BaritoneHelper.calculateGenericHeuristic(pos, WorldHelper.toVec3d(p.get()))) {
               closest = p;
            }
         }
      }

      return closest;
   }

   public Optional<BlockPos> getNearestBlock(Block block, Vec3 fromPos) {
      return this.getNearestBlock(block, pos -> true, fromPos);
   }

   public Optional<BlockPos> getNearestBlock(Block block, Predicate<BlockPos> isValidTest, Vec3 fromPos) {
      BlockPos pos = null;
      double nearest = Double.POSITIVE_INFINITY;
      if (!this.trackedBlocks.containsKey(block)) {
         return Optional.empty();
      } else {
         for (BlockPos p : this.trackedBlocks.get(block)) {
            if (this.mod.getWorld().getBlockState(p).getBlock().equals(block) && isValidTest.test(p) && !this.isUnreachable(p)) {
               double dist = BaritoneHelper.calculateGenericHeuristic(fromPos, WorldHelper.toVec3d(p));
               if (dist < nearest) {
                  nearest = dist;
                  pos = p;
               }
            }
         }

         return pos != null ? Optional.of(pos) : Optional.empty();
      }
   }

   public boolean anyFoundWithinDistance(double distance, Block... blocks) {
      return this.anyFoundWithinDistance(this.mod.getPlayer().position().add(0.0, 0.6F, 0.0), distance, blocks);
   }

   public boolean anyFoundWithinDistance(Vec3 pos, double distance, Block... blocks) {
      Optional<BlockPos> blockPos = this.getNearestBlock(blocks);
      return blockPos.<Boolean>map(value -> value.closerThan(new Vec3i((int)pos.x, (int)pos.y, (int)pos.z), distance)).orElse(false);
   }

   public double distanceToClosest(Block... blocks) {
      return this.distanceToClosest(this.mod.getPlayer().position().add(0.0, 0.6F, 0.0), blocks);
   }

   public double distanceToClosest(Vec3 pos, Block... blocks) {
      Optional<BlockPos> blockPos = this.getNearestBlock(blocks);
      return blockPos.<Double>map(value -> Math.sqrt(BlockPosVer.getSquaredDistance(value, pos))).orElse(Double.POSITIVE_INFINITY);
   }

   public boolean isBlockAtPosition(BlockPos pos, Block... blocks) {
      if (this.isUnreachable(pos)) {
         return false;
      } else if (!this.mod.getChunkTracker().isChunkLoaded(pos)) {
         return false;
      } else {
         Level world = this.mod.getWorld();
         if (world == null) {
            return false;
         } else {
            try {
               for (Block block : blocks) {
                  if (world.isEmptyBlock(pos) && WorldHelper.isAir(block)) {
                     return true;
                  }

                  BlockState state = world.getBlockState(pos);
                  if (state.getBlock() == block) {
                     return true;
                  }
               }

               return false;
            } catch (NullPointerException var9) {
               return false;
            }
         }
      }
   }

   public void reset() {
      this.trackedBlocks.clear();
      this.scannedBlocks.clear();
      this.scannedChunks.clear();
      this.rescanTimer.forceElapse();
      this.blacklist.clear();
      this.forceStop = true;
   }

   public void tick() {
      if (this.mod.getWorld() != null && this.mod.getPlayer() != null) {
         this.scanCloseBlocks();
         if (this.rescanTimer.elapsed() && !this.scanning) {
            if (this.scanDimension == WorldHelper.getCurrentDimension(this.mod) && this.mod.getWorld() == this.scanWorld) {
               this.cachedScannedBlocks = new HashMap<>(this.scannedBlocks.size());

               for (Entry<Block, HashSet<BlockPos>> entry : this.scannedBlocks.entrySet()) {
                  this.cachedScannedBlocks.put(entry.getKey(), (HashSet<BlockPos>)entry.getValue().clone());
               }

               this.scanning = true;
               this.forceStop = false;
               new Thread(() -> {
                  try {
                     this.rescan(Integer.MAX_VALUE, Integer.MAX_VALUE);
                  } catch (Exception var5) {
                     var5.printStackTrace();
                  } finally {
                     this.rescanTimer.reset();
                     this.scanning = false;
                  }
               }).start();
            } else {
               this.reset();
               this.scanWorld = this.mod.getWorld();
               this.scanDimension = WorldHelper.getCurrentDimension(this.mod);
            }
         }
      }
   }

   private void scanCloseBlocks() {
      for (Entry<Block, HashSet<BlockPos>> entry : this.cachedScannedBlocks.entrySet()) {
         if (!this.trackedBlocks.containsKey(entry.getKey())) {
            this.trackedBlocks.put(entry.getKey(), new HashSet<>());
         }

         this.trackedBlocks.get(entry.getKey()).clear();
         this.trackedBlocks.get(entry.getKey()).addAll(entry.getValue());
      }

      HashMap<Block, HashSet<BlockPos>> map = new HashMap<>();
      BlockPos pos = this.mod.getPlayer().blockPosition();
      Level world = this.mod.getPlayer().level();

      for (int x = pos.getX() - 8; x <= pos.getX() + 8; x++) {
         for (int y = pos.getY() - 8; y < pos.getY() + 8; y++) {
            for (int z = pos.getZ() - 8; z <= pos.getZ() + 8; z++) {
               BlockPos p = new BlockPos(x, y, z);
               BlockState state = world.getBlockState(p);
               if (!world.getBlockState(p).isAir()) {
                  Block block = state.getBlock();
                  if (map.containsKey(block)) {
                     map.get(block).add(p);
                  } else {
                     HashSet<BlockPos> set = new HashSet<>();
                     set.add(p);
                     map.put(block, set);
                  }
               }
            }
         }
      }

      for (Entry<Block, HashSet<BlockPos>> entry : map.entrySet()) {
         this.getFirstFewPositions(entry.getValue(), this.mod.getPlayer().position());
         if (!this.trackedBlocks.containsKey(entry.getKey())) {
            this.trackedBlocks.put(entry.getKey(), new HashSet<>());
         }

         this.trackedBlocks.get(entry.getKey()).addAll(entry.getValue());
      }
   }

   private void rescan(int maxCount, int cutOffRadius) {
      long ms = System.currentTimeMillis();
      ChunkPos playerChunkPos = this.mod.getPlayer().chunkPosition();
      Vec3 playerPos = this.mod.getPlayer().position();
      HashSet<ChunkPos> visited = new HashSet<>();
      Queue<BlockScanner.Node> queue = new ArrayDeque<>();
      queue.add(new BlockScanner.Node(playerChunkPos, 0));

      while (!queue.isEmpty() && visited.size() < maxCount && !this.forceStop) {
         BlockScanner.Node node = queue.poll();
         if (node.distance <= cutOffRadius && !visited.contains(node.pos) && this.mod.getWorld().getChunkSource().hasChunk(node.pos.x, node.pos.z)) {
            boolean isPriorityChunk = this.getChunkDist(node.pos, playerChunkPos) <= 2;
            if (isPriorityChunk || !this.scannedChunks.containsKey(node.pos) || this.mod.getWorld().getGameTime() - this.scannedChunks.get(node.pos) >= 80L) {
               visited.add(node.pos);
               this.scanChunk(node.pos, playerChunkPos);
               queue.add(new BlockScanner.Node(new ChunkPos(node.pos.x + 1, node.pos.z + 1), node.distance + 1));
               queue.add(new BlockScanner.Node(new ChunkPos(node.pos.x - 1, node.pos.z + 1), node.distance + 1));
               queue.add(new BlockScanner.Node(new ChunkPos(node.pos.x - 1, node.pos.z - 1), node.distance + 1));
               queue.add(new BlockScanner.Node(new ChunkPos(node.pos.x + 1, node.pos.z - 1), node.distance + 1));
            }
         }
      }

      if (this.forceStop) {
         this.reset();
         this.forceStop = false;
      } else {
         Iterator<ChunkPos> iterator = this.scannedChunks.keySet().iterator();

         while (iterator.hasNext()) {
            ChunkPos pos = iterator.next();
            int distance = this.getChunkDist(pos, playerChunkPos);
            if (distance > cutOffRadius) {
               iterator.remove();
            }
         }

         for (HashSet<BlockPos> set : this.scannedBlocks.values()) {
            if (set.size() >= 40) {
               this.getFirstFewPositions(set, playerPos);
            }
         }
      }
   }

   private int getChunkDist(ChunkPos pos1, ChunkPos pos2) {
      return Math.abs(pos1.x - pos2.x) + Math.abs(pos1.z - pos2.z);
   }

   private void getFirstFewPositions(HashSet<BlockPos> set, Vec3 playerPos) {
      Queue<BlockPos> queue = new PriorityQueue<>(
         Comparator.comparingDouble(posx -> -BaritoneHelper.calculateGenericHeuristic(playerPos, WorldHelper.toVec3d(posx)))
      );

      for (BlockPos pos : set) {
         queue.add(pos);
         if (queue.size() > 40) {
            queue.poll();
         }
      }

      set.clear();

      for (int i = 0; i < 40 && !queue.isEmpty(); i++) {
         set.add(queue.poll());
      }
   }

   private void scanChunk(ChunkPos chunkPos, ChunkPos playerChunkPos) {
      Level world = this.mod.getWorld();
      LevelChunk chunk = this.mod.getWorld().getChunk(chunkPos.x, chunkPos.z);
      this.scannedChunks.put(chunkPos, world.getGameTime());
      boolean isPriorityChunk = this.getChunkDist(chunkPos, playerChunkPos) <= 2;

      for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
         for (int y = world.getMinY(); y < world.getMaxY(); y++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
               BlockPos p = new BlockPos(x, y, z);
               if (!this.isUnreachable(p) && !world.isOutsideBuildHeight(p)) {
                  BlockState state = chunk.getBlockState(p);
                  if (!state.isAir()) {
                     Block block = state.getBlock();
                     if (this.scannedBlocks.containsKey(block)) {
                        HashSet<BlockPos> set = this.scannedBlocks.get(block);
                        if (set.size() <= 30000 || isPriorityChunk) {
                           set.add(p);
                        }
                     } else {
                        HashSet<BlockPos> set = new HashSet<>();
                        set.add(p);
                        this.scannedBlocks.put(block, set);
                     }
                  }
               }
            }
         }
      }
   }

   private record Node(ChunkPos pos, int distance) {
   }
}
