package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ShearSheepTask extends AbstractDoToEntityTask {
   public ShearSheepTask() {
      super(0.0, -1.0, -1.0);
   }

   @Override
   protected boolean isSubEqual(AbstractDoToEntityTask other) {
      return other instanceof ShearSheepTask;
   }

   @Override
   protected Task onEntityInteract(PlayerEngineController mod, Entity entity) {
      if (!mod.getItemStorage().hasItem(Items.SHEARS)) {
         Debug.logWarning("Failed to shear sheep because you have no shears.");
         return null;
      } else {
         if (mod.getSlotHandler().forceEquipItem(Items.SHEARS)) {
            ((Sheep)entity).shear((ServerLevel) entity.level(), SoundSource.PLAYERS, new ItemStack(Items.SHEARS));
            mod.getPlayer().getMainHandItem().hurtAndBreak(1, mod.getPlayer(), EquipmentSlot.MAINHAND);
         }

         return null;
      }
   }

   @Override
   protected Optional<Entity> getEntityTarget(PlayerEngineController mod) {
      return mod.getEntityTracker()
         .getClosestEntity(
            mod.getPlayer().position(), entity -> !(entity instanceof Sheep sheep) ? false : sheep.readyForShearing() && !sheep.isSheared(), Sheep.class
         );
   }

   @Override
   protected String toDebugString() {
      return "Shearing Sheep";
   }
}
