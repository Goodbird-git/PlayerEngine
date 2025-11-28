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

package com.player2.playerengine.automaton.api.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

public class LivingEntityHungerManager {
   private int foodLevel = 20;
   private float foodSaturationLevel = 20.0F;
   private float exhaustion;
   private int foodTickTimer;
   private int prevFoodLevel = 20;

   public void add(int food, float saturationModifier) {
      this.foodLevel = Math.min(food + this.foodLevel, 20);
      this.foodSaturationLevel = Math.min(this.foodSaturationLevel + food * saturationModifier * 2.0F, (float)this.foodLevel);
   }

   public void eat(Item item) {
      if (item.components().has(DataComponents.FOOD)) {
         FoodProperties foodComponent = item.components().get(DataComponents.FOOD);
         this.add(foodComponent.nutrition(), foodComponent.saturation());
      }
   }

   public void update(LivingEntity player) {
      Difficulty difficulty = player.level().getDifficulty();
      this.prevFoodLevel = this.foodLevel;
      if (this.exhaustion > 4.0F) {
         this.exhaustion -= 4.0F;
         if (this.foodSaturationLevel > 0.0F) {
            this.foodSaturationLevel = Math.max(this.foodSaturationLevel - 1.0F, 0.0F);
         } else if (difficulty != Difficulty.PEACEFUL) {
            this.foodLevel = Math.max(this.foodLevel - 1, 0);
         }
      }

      boolean bl = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
      if (bl && this.foodSaturationLevel > 0.0F && this.canFoodHeal(player) && this.foodLevel >= 20) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 10) {
            float f = Math.min(this.foodSaturationLevel, 6.0F);
            player.heal(f / 6.0F);
            this.addExhaustion(f);
            this.foodTickTimer = 0;
         }
      } else if (bl && this.foodLevel >= 18 && this.canFoodHeal(player)) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 80) {
            player.heal(1.0F);
            this.addExhaustion(6.0F);
            this.foodTickTimer = 0;
         }
      } else if (this.foodLevel <= 0) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 80) {
            if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
               player.hurt(player.damageSources().starve(), 1.0F);
            }

            this.foodTickTimer = 0;
         }
      } else {
         this.foodTickTimer = 0;
      }
   }

   public void readNbt(CompoundTag nbt) {
      if (nbt.contains("foodLevel", 99)) {
         this.foodLevel = nbt.getInt("foodLevel");
         this.foodTickTimer = nbt.getInt("foodTickTimer");
         this.foodSaturationLevel = nbt.getFloat("foodSaturationLevel");
         this.exhaustion = nbt.getFloat("foodExhaustionLevel");
      }
   }

   public void writeNbt(CompoundTag nbt) {
      nbt.putInt("foodLevel", this.foodLevel);
      nbt.putInt("foodTickTimer", this.foodTickTimer);
      nbt.putFloat("foodSaturationLevel", this.foodSaturationLevel);
      nbt.putFloat("foodExhaustionLevel", this.exhaustion);
   }

   public int getFoodLevel() {
      return this.foodLevel;
   }

   public int getPrevFoodLevel() {
      return this.prevFoodLevel;
   }

   public boolean isNotFull() {
      return this.foodLevel < 20;
   }

   public void addExhaustion(float exhaustion) {
      this.exhaustion = Math.min(this.exhaustion + exhaustion, 40.0F);
   }

   public float getExhaustion() {
      return this.exhaustion;
   }

   public float getSaturationLevel() {
      return this.foodSaturationLevel;
   }

   public void setFoodLevel(int foodLevel) {
      this.foodLevel = foodLevel;
   }

   public void setSaturationLevel(float saturationLevel) {
      this.foodSaturationLevel = saturationLevel;
   }

   public void setExhaustion(float exhaustion) {
      this.exhaustion = exhaustion;
   }

   public boolean canFoodHeal(LivingEntity entity) {
      return entity.getHealth() > 0.0F && entity.getHealth() < entity.getMaxHealth();
   }
}
