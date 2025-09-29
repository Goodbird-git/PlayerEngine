package com.player2.playerengine.mixins;

import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.events.PlayerDamageEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public class PlayerDamageMixin {
   @Inject(
      method = {"hurtServer"},
      at = {@At("HEAD")}
   )
   public void applyDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
      EventBus.publish(new PlayerDamageEvent((LivingEntity)(Object)this, source, amount));
   }
}
