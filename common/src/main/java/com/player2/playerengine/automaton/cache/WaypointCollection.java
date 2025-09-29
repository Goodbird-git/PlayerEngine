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

import com.player2.playerengine.automaton.api.cache.IWaypoint;
import com.player2.playerengine.automaton.api.cache.IWaypointCollection;
import com.player2.playerengine.automaton.api.cache.Waypoint;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.player2.playerengine.util.BlockPosUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class WaypointCollection implements IWaypointCollection {
   private final Map<IWaypoint.Tag, Set<IWaypoint>> waypoints = new EnumMap<>(
      Arrays.stream(IWaypoint.Tag.values()).collect(Collectors.toMap(Function.identity(), t -> new HashSet<>()))
   );

   WaypointCollection() {
   }

   public void readFromNBT(ValueInput input) {
      this.waypoints.clear();
      for (IWaypoint.Tag tag : IWaypoint.Tag.values()) {
         try {
            Set<IWaypoint> loadedWaypoints = new HashSet<>();
            ValueInput.ValueInputList waypointList = input.childrenList(tag.name()).get();
            for (ValueInput waypointNbt : waypointList) {
               String name = waypointNbt.getString("name").get();
               long creationTimestamp = waypointNbt.getLong("created").get();
               BetterBlockPos pos = new BetterBlockPos(BlockPosUtils.readBlockPos(waypointNbt, "pos").get());
               loadedWaypoints.add(new Waypoint(name, tag, pos, creationTimestamp));
            }
            this.waypoints.put(tag, loadedWaypoints);
         } catch (Exception e) {
         }
      }
   }

   public void writeToNBT(ValueOutput output) {
      for (Map.Entry<IWaypoint.Tag, Set<IWaypoint>> entry : this.waypoints.entrySet()) {
         ValueOutput.ValueOutputList list = output.childrenList(entry.getKey().name());
         for (IWaypoint waypoint : entry.getValue()) {
            ValueOutput waypointNbt = list.addChild();
            waypointNbt.putString("name", waypoint.getName());
            waypointNbt.putLong("created", waypoint.getCreationTimestamp());
            waypointNbt.putIntArray("pos", BlockPosUtils.writeBlockPos(waypoint.getLocation()));
         }
      }
   }
   @Override
   public void addWaypoint(IWaypoint waypoint) {
      this.waypoints.get(waypoint.getTag()).add(waypoint);
   }

   @Override
   public void removeWaypoint(IWaypoint waypoint) {
      this.waypoints.get(waypoint.getTag()).remove(waypoint);
   }

   @Override
   public IWaypoint getMostRecentByTag(IWaypoint.Tag tag) {
      return this.waypoints.get(tag).stream().min(Comparator.comparingLong(w -> -w.getCreationTimestamp())).orElse(null);
   }

   @Override
   public Set<IWaypoint> getByTag(IWaypoint.Tag tag) {
      return Collections.unmodifiableSet(this.waypoints.get(tag));
   }

   @Override
   public Set<IWaypoint> getAllWaypoints() {
      return this.waypoints.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
   }
}
