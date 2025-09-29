package com.player2.playerengine.control;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.events.BlockBreakingCancelEvent;
import com.player2.playerengine.eventbus.events.BlockBreakingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class PlayerExtraController {
   private final PlayerEngineController mod;
   private BlockPos blockBreakPos;

   public PlayerExtraController(PlayerEngineController mod) {
      this.mod = mod;
      EventBus.subscribe(BlockBreakingEvent.class, evt -> this.onBlockBreak(evt.blockPos));
      EventBus.subscribe(BlockBreakingCancelEvent.class, evt -> this.onBlockStopBreaking());
   }

   private void onBlockBreak(BlockPos pos) {
      this.blockBreakPos = pos;
   }

   private void onBlockStopBreaking() {
      this.blockBreakPos = null;
   }

   public BlockPos getBreakingBlockPos() {
      return this.blockBreakPos;
   }

   public boolean isBreakingBlock() {
      return this.blockBreakPos != null;
   }

   public boolean inRange(Entity entity) {
      return this.mod.getPlayer().closerThan(entity, this.mod.getModSettings().getEntityReachRange());
   }

   public void attack(Entity entity) {
      if (this.inRange(entity)) {
         this.mod.getPlayer().doHurtTarget((ServerLevel) entity.level(), entity);
         this.mod.getPlayer().swing(InteractionHand.MAIN_HAND);
      }
   }
}
