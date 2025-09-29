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

package com.player2.playerengine.automaton.utils.schematic.format.defaults;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.utils.schematic.StaticSchematic;
import com.player2.playerengine.automaton.utils.type.VarInt;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class SpongeSchematic extends StaticSchematic {
   public SpongeSchematic(CompoundTag nbt) {
      this.x = nbt.getInt("Width").get();
      this.y = nbt.getInt("Height").get();
      this.z = nbt.getInt("Length").get();
      this.states = new BlockState[this.x][this.z][this.y];
      Int2ObjectArrayMap<BlockState> palette = new Int2ObjectArrayMap();
      CompoundTag paletteTag = nbt.getCompound("Palette").get();

      for (String tag : paletteTag.keySet()) {
         int index = paletteTag.getInt(tag).get();
         SpongeSchematic.SerializedBlockState serializedState = SpongeSchematic.SerializedBlockState.getFromString(tag);
         if (serializedState == null) {
            throw new IllegalArgumentException("Unable to parse palette tag");
         }

         BlockState state = serializedState.deserialize();
         if (state == null) {
            throw new IllegalArgumentException("Unable to deserialize palette tag");
         }

         palette.put(index, state);
      }

      byte[] rawBlockData = nbt.getByteArray("BlockData").get();
      int[] blockData = new int[this.x * this.y * this.z];
      int offset = 0;

      for (int i = 0; i < blockData.length; i++) {
         if (offset >= rawBlockData.length) {
            throw new IllegalArgumentException("No remaining bytes in BlockData for complete schematic");
         }

         VarInt varInt = VarInt.read(rawBlockData, offset);
         blockData[i] = varInt.getValue();
         offset += varInt.getSize();
      }

      for (int y = 0; y < this.y; y++) {
         for (int z = 0; z < this.z; z++) {
            for (int x = 0; x < this.x; x++) {
               int indexx = (y * this.z + z) * this.x + x;
               BlockState state = (BlockState)palette.get(blockData[indexx]);
               if (state == null) {
                  throw new IllegalArgumentException("Invalid Palette Index " + indexx);
               }

               this.states[x][z][y] = state;
            }
         }
      }
   }

   private static final class SerializedBlockState {
      private static final Pattern REGEX = Pattern.compile("(?<location>(\\w+:)?\\w+)(\\[(?<properties>(\\w+=\\w+,?)+)])?");
      private final ResourceLocation resourceLocation;
      private final Map<String, String> properties;
      private BlockState blockState;

      private SerializedBlockState(ResourceLocation resourceLocation, Map<String, String> properties) {
         this.resourceLocation = resourceLocation;
         this.properties = properties;
      }

      private BlockState deserialize() {
         if (this.blockState == null) {
            Block block = (Block)BuiltInRegistries.BLOCK.get(this.resourceLocation).get().value();
            this.blockState = block.defaultBlockState();
            this.properties.keySet().stream().sorted(String::compareTo).forEachOrdered(key -> {
               Property<?> property = block.getStateDefinition().getProperty(key);
               if (property != null) {
                  this.blockState = setPropertyValue(this.blockState, property, this.properties.get(key));
               }
            });
         }

         return this.blockState;
      }

      private static SpongeSchematic.SerializedBlockState getFromString(String s) {
         Matcher m = REGEX.matcher(s);
         if (!m.matches()) {
            return null;
         } else {
            try {
               String location = m.group("location");
               String properties = m.group("properties");
               ResourceLocation resourceLocation = ResourceLocation.tryParse(location);
               Map<String, String> propertiesMap = new HashMap<>();
               if (properties != null) {
                  for (String property : properties.split(",")) {
                     String[] split = property.split("=");
                     propertiesMap.put(split[0], split[1]);
                  }
               }

               return new SpongeSchematic.SerializedBlockState(resourceLocation, propertiesMap);
            } catch (Exception var11) {
               PlayerEngine.LOGGER.error(var11);
               return null;
            }
         }
      }

      private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String value) {
         Optional<T> parsed = property.getValue(value);
         if (parsed.isPresent()) {
            return (BlockState)state.setValue(property, parsed.get());
         } else {
            throw new IllegalArgumentException("Invalid value for property " + property);
         }
      }
   }
}
