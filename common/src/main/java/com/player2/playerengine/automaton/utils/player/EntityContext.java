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

package com.player2.playerengine.automaton.utils.player;

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.cache.IWorldData;
import com.player2.playerengine.automaton.api.entity.IHungerManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.pathing.calc.Avoidance;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.IInteractionController;
import com.player2.playerengine.automaton.api.utils.RayTraceUtils;
import com.player2.playerengine.automaton.utils.accessor.ServerChunkManagerAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public class EntityContext implements IEntityContext {
   private final LivingEntity entity;
   @Nullable
   private Supplier<List<Avoidance>> avoidanceFinder;

   public EntityContext(LivingEntity entity) {
      this.entity = entity;
   }

   @Override
   public LivingEntity entity() {
      return this.entity;
   }

   @Nullable
   @Override
   public LivingEntityInventory inventory() {
      return this.entity instanceof IInventoryProvider ? ((IInventoryProvider)this.entity).getLivingInventory() : null;
   }

   @Nullable
   @Override
   public LivingEntityHungerManager hungerManager() {
      return this.entity instanceof IHungerManagerProvider ? ((IHungerManagerProvider)this.entity).getHungerManager() : null;
   }

   @Override
   public IInteractionController playerController() {
      return IInteractionController.KEY.get(this.entity);
   }

   @Override
   public ServerLevel world() {
      Level world = this.entity.level();
      if (world.isClientSide()) {
         throw new IllegalStateException();
      } else {
         return (ServerLevel)world;
      }
   }

   @Override
   public IWorldData worldData() {
      return BaritoneAPI.getProvider().getBaritone(this.entity).getEntityContext().worldData();
   }

   @Override
   public HitResult objectMouseOver() {
      return RayTraceUtils.rayTraceTowards(this.entity(), this.entityRotations(), this.playerController().getBlockReachDistance());
   }

   @Override
   public BetterBlockPos feetPos() {
      double x = this.entity().getX();
      double z = this.entity().getZ();
      BetterBlockPos feet = new BetterBlockPos(x, this.entity().getY() + 0.1251, z);
      ServerLevel world = this.world();
      if (world != null) {
         LevelChunk chunk = ((ServerChunkManagerAccessor)world.getChunkSource()).automatone$getChunkNow((int)x << 4, (int)z << 4);
         if (chunk != null && chunk.getBlockState(feet).getBlock() instanceof SlabBlock) {
            return feet.up();
         }
      }

      return feet;
   }

   private Stream<Entity> streamHostileEntities() {
      return this.worldEntitiesStream()
         .filter(entity -> entity instanceof Mob)
         .filter(entity -> !(entity instanceof Spider) || entity.getLightLevelDependentMagicValue() < 0.5)
         .filter(entity -> !(entity instanceof ZombifiedPiglin) || ((ZombifiedPiglin)entity).getLastHurtByMob() != null)
         .filter(entity -> !(entity instanceof EnderMan) || ((EnderMan)entity).isCreepy());
   }

   @Override
   public void setAvoidanceFinder(@Nullable Supplier<List<Avoidance>> avoidanceFinder) {
      this.avoidanceFinder = avoidanceFinder;
   }

   @Override
   public List<Avoidance> listAvoidedAreas() {
      if (!this.baritone().settings().avoidance.get()) {
         return Collections.emptyList();
      } else if (this.avoidanceFinder != null) {
         return this.avoidanceFinder.get();
      } else {
         List<Avoidance> res = new ArrayList<>();
         double mobCoeff = this.baritone().settings().mobAvoidanceCoefficient.get();
         if (mobCoeff != 1.0) {
            this.streamHostileEntities()
               .forEach(entity -> res.add(new Avoidance(entity.blockPosition(), mobCoeff, this.baritone().settings().mobAvoidanceRadius.get())));
         }

         return res;
      }
   }
}
