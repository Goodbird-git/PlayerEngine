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

   // Thread-local recursion guard: prevents infinite loop when getDamageValue()
   // triggers ItemStack.copy -> ItemStack.<init> -> recalculateHash -> getDamageValue
   // (e.g. Silent Gear MainPartItem.getMaxDamage -> PartInstance -> ItemStack.copy)
   @Unique
   private static final ThreadLocal<Boolean> playerengine$inRecalc = ThreadLocal.withInitial(() -> Boolean.FALSE);

   @Shadow
   public abstract int getDamageValue();

   private void recalculateHash() {
      if (this.item == null) {
         this.baritoneHash = -1;
         return;
      }
      // If we're already inside recalculateHash on this thread, skip to avoid recursion.
      // This breaks the cycle: recalculateHash -> getDamageValue -> getMaxDamage ->
      // PartInstance -> ItemStack.copy -> ItemStack.<init> -> recalculateHash
      if (playerengine$inRecalc.get()) {
         this.baritoneHash = this.item.hashCode();
         return;
      }
      playerengine$inRecalc.set(Boolean.TRUE);
      try {
         this.baritoneHash = this.item.hashCode() + this.getDamageValue();
      } catch (Throwable t) {
         // Catches failure modes during ItemStack init:
         // - IllegalStateException: NeoForge config not loaded (e.g. ConstructionStick)
         // - RuntimeException: client-only class loaded on DEDICATED_SERVER
         // - StackOverflowError: any remaining deep recursion
         this.baritoneHash = this.item.hashCode();
      } finally {
         playerengine$inRecalc.set(Boolean.FALSE);
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
