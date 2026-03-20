package com.player2.playerengine.mixins.baritone;

import com.player2.playerengine.automaton.api.utils.accessor.IItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemStack.class})
public abstract class MixinItemStack implements IItemStack {
   @Shadow
   @Final
   private Item item;
   @Unique
   private int baritoneHash;

   @Shadow
   public abstract int getDamageValue();

   private void recalculateHash() {
      if (this.item == null) {
         this.baritoneHash = -1;
         return;
      }
      try {
         this.baritoneHash = this.item.hashCode() + this.getDamageValue();
      } catch (IllegalStateException e) {
         // Some mods (e.g. ConstructionStick) access NeoForge config values in
         // getMaxDamage() which may not be loaded yet during recipe deserialization.
         // Fall back to hash without damage value to avoid crashing the server.
         this.baritoneHash = this.item.hashCode();
      }
   }

   @Inject(
      method = {"<init>*"},
      at = {@At("RETURN")}
   )
   private void onInit(CallbackInfo ci) {
      this.recalculateHash();
   }

   @Inject(
      method = {"setDamageValue"},
      at = {@At("TAIL")}
   )
   private void onItemDamageSet(CallbackInfo ci) {
      this.recalculateHash();
   }

   @Override
   public int getBaritoneHash() {
      return this.baritoneHash;
   }
}
