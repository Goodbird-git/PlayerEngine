package com.player2.playerengine.mixins.baritone;

import com.player2.playerengine.PlayerEngine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Util.class})
public abstract class MixinUtil {

   @Unique
   private static void shutdownExecutor(ExecutorService p_137532_) {
      p_137532_.shutdown();

      boolean flag;
      try {
         flag = p_137532_.awaitTermination(3L, TimeUnit.SECONDS);
      } catch (InterruptedException var3) {
         flag = false;
      }

      if (!flag) {
         p_137532_.shutdownNow();
      }
   }

   @Inject(
      method = {"shutdownExecutors"},
      at = {@At("RETURN")}
   )
   private static void shutdownBaritoneExecutor(CallbackInfo ci) {
      shutdownExecutor(PlayerEngine.getExecutor());
   }
}
