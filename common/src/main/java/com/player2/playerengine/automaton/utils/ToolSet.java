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

package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ToolSet {
   private final Map<Block, Double> breakStrengthCache = new HashMap<>();
   private final Function<Block, Double> backendCalculation;
   private final LivingEntity player;
   private final IBaritone baritone;

   public ToolSet(LivingEntity player) {
      this.player = player;
      this.baritone = IBaritone.KEY.get(player);
      if (this.baritone.settings().considerPotionEffects.get()) {
         double amplifier = this.potionAmplifier();
         Function<Double, Double> amplify = x -> amplifier * x;
         this.backendCalculation = amplify.compose(this::getBestDestructionTime);
      } else {
         this.backendCalculation = this::getBestDestructionTime;
      }
   }

   public double getStrVsBlock(BlockState state) {
      return this.breakStrengthCache.computeIfAbsent(state.getBlock(), this.backendCalculation);
   }

   private int getMaterialCost(ItemStack itemStack) {
      return itemStack.has(DataComponents.TOOL) ? 1 : -1;
   }

   public boolean hasSilkTouch(ItemStack stack) {
      return stack.get(DataComponents.ENCHANTMENTS).entrySet().stream().anyMatch(e->e.getKey().unwrapKey().get().equals(Enchantments.SILK_TOUCH));
   }

   public int getBestSlot(Block b, boolean preferSilkTouch) {
      return this.getBestSlot(b, preferSilkTouch, false);
   }

   public int getBestSlot(Block b, boolean preferSilkTouch, boolean pathingCalculation) {
      if (b.defaultBlockState().getBlock().defaultDestroyTime() == 0.0F) {
         return ((IInventoryProvider)this.player).getLivingInventory().selectedSlot;
      } else if (this.baritone.settings().disableAutoTool.get() && pathingCalculation) {
         return ((IInventoryProvider)this.player).getLivingInventory().selectedSlot;
      } else {
         int best = 0;
         double highestSpeed = Double.NEGATIVE_INFINITY;
         int lowestCost = Integer.MIN_VALUE;
         boolean bestSilkTouch = false;
         BlockState blockState = b.defaultBlockState();

         for (int i = 0; i < 9; i++) {
            ItemStack itemStack = ((IInventoryProvider)this.player).getLivingInventory().getItem(i);
            if ((this.baritone.settings().useSwordToMine.get() || !(itemStack.is(ItemTags.SWORDS)))
               && (!this.baritone.settings().itemSaver.get() || itemStack.getDamageValue() < itemStack.getMaxDamage() || itemStack.getMaxDamage() <= 1)) {
               double speed = calculateSpeedVsBlock(itemStack, blockState);
               boolean silkTouch = this.hasSilkTouch(itemStack);
               if (speed > highestSpeed) {
                  highestSpeed = speed;
                  best = i;
                  lowestCost = this.getMaterialCost(itemStack);
                  bestSilkTouch = silkTouch;
               } else if (speed == highestSpeed) {
                  int cost = this.getMaterialCost(itemStack);
                  if (cost < lowestCost && (silkTouch || !bestSilkTouch) || preferSilkTouch && !bestSilkTouch && silkTouch) {
                     highestSpeed = speed;
                     best = i;
                     lowestCost = cost;
                     bestSilkTouch = silkTouch;
                  }
               }
            }
         }

         return best;
      }
   }

   private double getBestDestructionTime(Block b) {
      ItemStack stack = ((IInventoryProvider)this.player).getLivingInventory().getItem(this.getBestSlot(b, false, true));
      return calculateSpeedVsBlock(stack, b.defaultBlockState()) * this.avoidanceMultiplier(b);
   }

   private double avoidanceMultiplier(Block b) {
      return this.baritone.settings().blocksToAvoidBreaking.get().contains(b.builtInRegistryHolder().value()) ? 0.1 : 1.0;
   }

   public static double calculateSpeedVsBlock(ItemStack item, BlockState state) {
      float hardness = state.getDestroySpeed(null, null);
      if (hardness < 0.0F) {
         return -1.0;
      } else {
         float speed = item.getDestroySpeed(state);
         if (speed > 1.0F) {
            int effLevel = item.get(DataComponents.ENCHANTMENTS).entrySet().stream().filter(e->e.getKey().unwrapKey().get().equals(Enchantments.EFFICIENCY)).findFirst().get().getIntValue();
            if (effLevel > 0 && !item.isEmpty()) {
               speed += effLevel * effLevel + 1;
            }
         }

         speed /= hardness;
         return state.requiresCorrectToolForDrops() && (item.isEmpty() || !item.isCorrectToolForDrops(state)) ? speed / 100.0F : speed / 30.0F;
      }
   }

   private double potionAmplifier() {
      double speed = 1.0;
      MobEffectInstance hasteEffect = this.player.getEffect(MobEffects.HASTE);
      if (hasteEffect != null) {
         speed *= 1.0 + (hasteEffect.getAmplifier() + 1) * 0.2;
      }

      MobEffectInstance fatigueEffect = this.player.getEffect(MobEffects.MINING_FATIGUE);
      if (fatigueEffect != null) {
         switch (fatigueEffect.getAmplifier()) {
            case 0:
               speed *= 0.3;
               break;
            case 1:
               speed *= 0.09;
               break;
            case 2:
               speed *= 0.0027;
               break;
            default:
               speed *= 8.1E-4;
         }
      }

      return speed;
   }
}
