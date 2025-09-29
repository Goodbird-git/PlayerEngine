package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import dev.architectury.event.events.common.ChunkEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;

public class SimpleChunkTracker {
   private final PlayerEngineController mod;
   private final Set<ChunkPos> loaded = new HashSet<>();

   public SimpleChunkTracker(PlayerEngineController mod) {
      this.mod = mod;
      ChunkEvent.LOAD_DATA.register((chunk, level, data) -> this.onLoad(chunk.getPos()));
      ChunkEvent.SAVE_DATA.register((chunk, level, data) -> this.onUnload(chunk.getPos()));
   }

   private void onLoad(ChunkPos pos) {
      this.loaded.add(pos);
   }

   private void onUnload(ChunkPos pos) {
      this.loaded.remove(pos);
   }

   public boolean isChunkLoaded(ChunkPos pos) {
      return !(this.mod.getWorld().getChunk(pos.x, pos.z) instanceof EmptyLevelChunk);
   }

   public boolean isChunkLoaded(BlockPos pos) {
      return this.isChunkLoaded(new ChunkPos(pos));
   }

   public List<ChunkPos> getLoadedChunks() {
      List<ChunkPos> result = new ArrayList<>(this.loaded);
      return result.stream().filter(this::isChunkLoaded).distinct().collect(Collectors.toList());
   }

   public boolean scanChunk(ChunkPos chunk, Predicate<BlockPos> onBlockStop) {
      if (!this.isChunkLoaded(chunk)) {
         return false;
      } else {
         int bottomY = this.mod.getWorld().getMinY();
         int topY = this.mod.getWorld().getMaxY();

         for (int xx = chunk.getMinBlockX(); xx <= chunk.getMaxBlockX(); xx++) {
            for (int yy = bottomY; yy <= topY; yy++) {
               for (int zz = chunk.getMinBlockZ(); zz <= chunk.getMaxBlockZ(); zz++) {
                  if (onBlockStop.test(new BlockPos(xx, yy, zz))) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   public void scanChunk(ChunkPos chunk, Consumer<BlockPos> onBlock) {
      this.scanChunk(chunk, block -> {
         onBlock.accept(block);
         return false;
      });
   }

   public void reset(PlayerEngineController mod) {
      Debug.logInternal("CHUNKS RESET");
      this.loaded.clear();
   }
}
