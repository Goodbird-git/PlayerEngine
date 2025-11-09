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

package com.player2.playerengine.automaton.api.utils;

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.cache.IWorldData;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.pathing.calc.Avoidance;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public interface IEntityContext {
   LivingEntity entity();

   default IBaritone baritone() {
      return IBaritone.KEY.get(this.entity());
   }

   @Nullable
   LivingEntityInventory inventory();

   @Nullable
   LivingEntityHungerManager hungerManager();

   IInteractionController playerController();

   ServerLevel world();

   default Iterable<Entity> worldEntities() {
      return this.world().getAllEntities();
   }

   default Stream<Entity> worldEntitiesStream() {
      return StreamSupport.stream(this.worldEntities().spliterator(), false);
   }

   void setAvoidanceFinder(@Nullable Supplier<List<Avoidance>> var1);

   List<Avoidance> listAvoidedAreas();

   IWorldData worldData();

   HitResult objectMouseOver();

   BetterBlockPos feetPos();

   default Vec3 feetPosAsVec() {
      return new Vec3(this.entity().getX(), this.entity().getY(), this.entity().getZ());
   }

   default Vec3 headPos() {
      return new Vec3(this.entity().getX(), this.entity().getY() + this.entity().getEyeHeight(), this.entity().getZ());
   }

   default Rotation entityRotations() {
      return new Rotation(this.entity().getYRot(), this.entity().getXRot());
   }

   default Optional<BlockPos> getSelectedBlock() {
      HitResult result = this.objectMouseOver();
      return result != null && result.getType() == Type.BLOCK ? Optional.of(((BlockHitResult)result).getBlockPos()) : Optional.empty();
   }

   default boolean isLookingAt(BlockPos pos) {
      return this.getSelectedBlock().equals(Optional.of(pos));
   }

   default void logDebug(String message) {
      if (BaritoneAPI.getGlobalSettings().chatDebug.get()) {
         LivingEntity entity = this.entity();
         if (entity instanceof Player) {
            ((Player)entity).displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), false);
         }

         if (BaritoneAPI.getGlobalSettings().syncWithOps.get()) {
            MinecraftServer server = this.world().getServer();

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               if (server.getPlayerList().isOp(new NameAndId(p.getGameProfile()))) {
                  IBaritone.KEY.get(p).logDirect(message);
               }
            }
         }
      }
   }
}
