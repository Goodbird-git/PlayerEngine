package com.player2.playerengine.util.helpers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.multiversion.DamageSourceWrapper;
import com.player2.playerengine.multiversion.MethodWrapper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class EntityHelper {
   public static final double ENTITY_GRAVITY = 0.08;

   public static boolean isAngryAtPlayer(PlayerEngineController mod, Entity mob) {
      boolean hostile = isProbablyHostileToPlayer(mod, mob);
      return !(mob instanceof Mob entity) ? hostile : hostile && entity.getTarget() == mod.getPlayer();
   }

   public static boolean isProbablyHostileToPlayer(PlayerEngineController mod, Entity entity) {
      if (entity instanceof Mob mob) {
         if (mob instanceof Slime slime) {
            return slime.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0;
         } else if (mob instanceof Piglin piglin) {
            return piglin.isAggressive() && !isTradingPiglin(mob) && piglin.isAdult();
         } else if (mob instanceof EnderMan enderman) {
            return enderman.isCreepy();
         } else {
            return mob instanceof ZombifiedPiglin zombifiedPiglin ? zombifiedPiglin.isAggressive() : mob.isAggressive() || mob instanceof Monster;
         }
      } else {
         return false;
      }
   }

   public static boolean isTradingPiglin(Entity entity) {
      if (entity instanceof Piglin pig) {
         for (ItemStack stack : new ItemStack[]{pig.getItemBySlot(EquipmentSlot.MAINHAND), pig.getItemBySlot(EquipmentSlot.OFFHAND)}) {
            if (stack.getItem().equals(Items.GOLD_INGOT)) {
               return true;
            }
         }
      }

      return false;
   }

   public static double calculateResultingPlayerDamage(LivingEntity player, DamageSource src, double damageAmount) {
      DamageSourceWrapper source = DamageSourceWrapper.of(src);
      if (player.isInvulnerableTo((ServerLevel) player.level(), src)) {
         return 0.0;
      } else {
         if (!source.bypassesArmor()) {
            damageAmount = MethodWrapper.getDamageLeft(
               player, damageAmount, src, (double)player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            );
         }

         if (!source.bypassesShield()) {
            if (player.hasEffect(MobEffects.ABSORPTION) && source.isOutOfWorld()) {
               float k = (player.getEffect(MobEffects.ABSORPTION).getAmplifier() + 1) * 5;
               float j = 25.0F - k;
               double f = damageAmount * j;
               damageAmount = Math.max(f / 25.0, 0.0);
            }

            if (damageAmount <= 0.0) {
               damageAmount = 0.0;
            } else {
               float k = EnchantmentHelper.getDamageProtection((ServerLevel) player.level(), player, src);
               if (k > 0.0F) {
                  damageAmount = CombatRules.getDamageAfterMagicAbsorb((float)damageAmount, k);
               }
            }
         }

         return Math.max(damageAmount - player.getAbsorptionAmount(), 0.0);
      }
   }
}
