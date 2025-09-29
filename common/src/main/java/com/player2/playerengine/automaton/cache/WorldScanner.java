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

package com.player2.playerengine.automaton.cache;

import com.player2.playerengine.automaton.api.cache.IWorldScanner;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.BlockOptionalMetaLookup;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.utils.accessor.ServerChunkManagerAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public enum WorldScanner implements IWorldScanner {
   INSTANCE;

   public static final int SECTION_HEIGHT = 16;
   private static final int[] DEFAULT_COORDINATE_ITERATION_ORDER = IntStream.range(0, 16).toArray();

   @Override
   public List<BlockPos> scanChunkRadius(IEntityContext ctx, BlockOptionalMetaLookup filter, int max, int yLevelThreshold, int maxSearchRadius) {
      ArrayList<BlockPos> res = new ArrayList<>();
      if (filter.blocks().isEmpty()) {
         return res;
      } else {
         ServerChunkManagerAccessor chunkProvider = (ServerChunkManagerAccessor)ctx.world().getChunkSource();
         int maxSearchRadiusSq = maxSearchRadius * maxSearchRadius;
         int playerChunkX = ctx.feetPos().getX() >> 4;
         int playerChunkZ = ctx.feetPos().getZ() >> 4;
         int playerY = ctx.feetPos().getY();
         int playerYBlockStateContainerIndex = playerY >> 4;
         int[] coordinateIterationOrder = this.streamSectionY(ctx.world())
            .boxed()
            .sorted(Comparator.comparingInt(y -> Math.abs(y - playerYBlockStateContainerIndex)))
            .mapToInt(x -> x)
            .toArray();
         int searchRadiusSq = 0;
         boolean foundWithinY = false;

         while (true) {
            boolean allUnloaded = true;
            boolean foundChunks = false;

            for (int xoff = -searchRadiusSq; xoff <= searchRadiusSq; xoff++) {
               for (int zoff = -searchRadiusSq; zoff <= searchRadiusSq; zoff++) {
                  int distance = xoff * xoff + zoff * zoff;
                  if (distance == searchRadiusSq) {
                     foundChunks = true;
                     int chunkX = xoff + playerChunkX;
                     int chunkZ = zoff + playerChunkZ;
                     ChunkAccess chunk = chunkProvider.automatone$getChunkNow(chunkX, chunkZ);
                     if (chunk != null) {
                        allUnloaded = false;
                        if (this.scanChunkInto(chunkX << 4, chunkZ << 4, chunk, filter, res, max, yLevelThreshold, playerY, coordinateIterationOrder)) {
                           foundWithinY = true;
                        }
                     }
                  }
               }
            }

            if (allUnloaded && foundChunks || res.size() >= max && (searchRadiusSq > maxSearchRadiusSq || searchRadiusSq > 1 && foundWithinY)) {
               return res;
            }

            searchRadiusSq++;
         }
      }
   }

   @Override
   public List<BlockPos> scanChunk(IEntityContext ctx, BlockOptionalMetaLookup filter, ChunkPos pos, int max, int yLevelThreshold) {
      if (filter.blocks().isEmpty()) {
         return Collections.emptyList();
      } else {
         ServerChunkCache chunkProvider = ctx.world().getChunkSource();
         ChunkAccess chunk = chunkProvider.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
         int playerY = ctx.feetPos().getY();
         if (chunk instanceof LevelChunk && !((LevelChunk)chunk).isEmpty()) {
            ArrayList<BlockPos> res = new ArrayList<>();
            this.scanChunkInto(pos.x << 4, pos.z << 4, chunk, filter, res, max, yLevelThreshold, playerY, this.streamSectionY(ctx.world()).toArray());
            return res;
         } else {
            return Collections.emptyList();
         }
      }
   }

   private IntStream streamSectionY(ServerLevel world) {
      return IntStream.range(0, world.getHeight() / 16);
   }

   @Override
   public int repack(IEntityContext ctx) {
      return this.repack(ctx, 40);
   }

   @Override
   public int repack(IEntityContext ctx, int range) {
      ChunkSource chunkProvider = ctx.world().getChunkSource();
      BetterBlockPos playerPos = ctx.feetPos();
      int playerChunkX = playerPos.getX() >> 4;
      int playerChunkZ = playerPos.getZ() >> 4;
      int minX = playerChunkX - range;
      int minZ = playerChunkZ - range;
      int maxX = playerChunkX + range;
      int maxZ = playerChunkZ + range;
      int queued = 0;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            LevelChunk chunk = chunkProvider.getChunk(x, z, false);
            if (chunk != null && !chunk.isEmpty()) {
               queued++;
            }
         }
      }

      return queued;
   }

   private boolean scanChunkInto(
      int chunkX,
      int chunkZ,
      ChunkAccess chunk,
      BlockOptionalMetaLookup filter,
      Collection<BlockPos> result,
      int max,
      int yLevelThreshold,
      int playerY,
      int[] coordinateIterationOrder
   ) {
      LevelChunkSection[] chunkInternalStorageArray = chunk.getSections();
      boolean foundWithinY = false;
      if (chunkInternalStorageArray.length != coordinateIterationOrder.length) {
         throw new IllegalStateException(
            "Unexpected number of sections in chunk (expected " + coordinateIterationOrder.length + ", got " + chunkInternalStorageArray.length + ")"
         );
      } else {
         for (int yIndex = 0; yIndex < chunkInternalStorageArray.length; yIndex++) {
            int y0 = coordinateIterationOrder[yIndex];
            LevelChunkSection section = chunkInternalStorageArray[y0];
            if (section != null && !section.hasOnlyAir() && section.maybeHas(filter::has)) {
               int yReal = (y0 << 4) + chunk.getMinY();
               PalettedContainer<BlockState> bsc = section.getStates();

               for (int yy = 0; yy < 16; yy++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        BlockState state = (BlockState)bsc.get(x, yy, z);
                        if (filter.has(state)) {
                           int y = yReal | yy;
                           if (result.size() >= max) {
                              if (Math.abs(y - playerY) < yLevelThreshold) {
                                 foundWithinY = true;
                              } else if (foundWithinY) {
                                 return true;
                              }
                           }

                           result.add(new BlockPos(chunkX | x, y, chunkZ | z));
                        }
                     }
                  }
               }
            }
         }

         return foundWithinY;
      }
   }
}
