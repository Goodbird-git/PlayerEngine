package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.chains.MobDefenseChain;
import com.player2.playerengine.mixins.LivingEntityMixin;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.slots.PlayerSlot;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;

public abstract class AbstractKillEntityTask extends AbstractDoToEntityTask {
   private static final double OTHER_FORCE_FIELD_RANGE = 2.0;
   private static final double CONSIDER_COMBAT_RANGE = 10.0;

   protected AbstractKillEntityTask() {
      this(10.0, 2.0);
   }

   protected AbstractKillEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(combatGuardLowerRange, combatGuardLowerFieldRadius);
   }

   protected AbstractKillEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
   }

   public static Item bestWeapon(PlayerEngineController mod) {
      Item toolItem1 = null;
      List<ItemStack> invStacks = mod.getItemStorage().getItemStacksPlayerInventory(true);
      Item toolItem2 = MobDefenseChain.getBestWeapon(mod);
      if (toolItem2 != null) {
         return toolItem2;
      } else {
         Item item = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
         float bestDamage = Float.NEGATIVE_INFINITY;
         if (item.components().has(DataComponents.ATTRIBUTE_MODIFIERS)) {
            bestDamage = (float) item.components().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().filter((f)->f.attribute()== Attributes.ATTACK_DAMAGE).findFirst().get().modifier().amount();
         }

         for (ItemStack invStack : invStacks) {
            if (invStack.getItem().components().has(DataComponents.ATTRIBUTE_MODIFIERS)) {
               float itemDamage = (float) item.components().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().filter((f)->f.attribute()== Attributes.ATTACK_DAMAGE).findFirst().get().modifier().amount();
               if (itemDamage > bestDamage) {
                  toolItem1 = invStack.getItem();
                  bestDamage = itemDamage;
               }
            }
         }

         return toolItem1;
      }
   }

   public static boolean equipWeapon(PlayerEngineController mod) {
      Item bestWeapon = bestWeapon(mod);
      Item equipedWeapon = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
      if (bestWeapon != null && bestWeapon != equipedWeapon) {
         mod.getSlotHandler().forceEquipItem(bestWeapon);
         return true;
      } else {
         return false;
      }
   }

   @Override
   protected Task onEntityInteract(PlayerEngineController mod, Entity entity) {
      if (!equipWeapon(mod)) {
         float hitProg = this.getAttackCooldownProgress(mod.getPlayer(), 0.0F);
         if (hitProg >= 1.0F && (mod.getPlayer().onGround() || mod.getPlayer().getDeltaMovement().y() < 0.0 || mod.getPlayer().isInWater())) {
            LookHelper.lookAt(mod, entity.getEyePosition());
            mod.getControllerExtras().attack(entity);
         }
      }

      return null;
   }

   public float getAttackCooldownProgressPerTick(LivingEntity entity) {
      return 5.0F;
   }

   public float getAttackCooldownProgress(LivingEntity entity, float baseTime) {
      return Mth.clamp((((LivingEntityMixin)entity).getLastAttackedTicks() + baseTime) / this.getAttackCooldownProgressPerTick(entity), 0.0F, 1.0F);
   }
}
