package com.player2.playerengine.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.Function;

/**
 * Fixes ConcurrentModificationException in Architectury EventFactory.
 *
 * Architectury API issue #653: EventFactory uses a plain ArrayList for event
 * listeners, which is not thread-safe. When a mod registers a listener during
 * event dispatch (e.g., during the first server tick), the iterator throws
 * ConcurrentModificationException.
 *
 * This mixin replaces the ArrayList with a SnapshotArrayList subclass that
 * returns snapshot-based iterators, making concurrent registration during
 * iteration safe. We must extend ArrayList because the field type is ArrayList,
 * not List.
 */
@Mixin(targets = "dev.architectury.event.EventFactory$EventImpl", remap = false)
public class MixinEventFactoryEventImpl<T> {

    @Shadow
    private ArrayList<T> listeners;

    /**
     * An ArrayList subclass whose iterators operate on a snapshot of the backing
     * array at the time of iterator creation. This means concurrent add/remove
     * during iteration won't throw ConcurrentModificationException.
     */
    public static class SnapshotArrayList<E> extends ArrayList<E> {
        @Override
        public Iterator<E> iterator() {
            // Return an iterator over a snapshot copy
            return new ArrayList<>(this).iterator();
        }

        @Override
        public ListIterator<E> listIterator() {
            return new ArrayList<>(this).listIterator();
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            return new ArrayList<>(this).listIterator(index);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void playerengine$replaceListenersWithSnapshotList(Function<?, ?> function, CallbackInfo ci) {
        // Replace the plain ArrayList with our SnapshotArrayList.
        // The snapshot iterator pattern means that iteration (event dispatch) sees
        // a frozen copy, while registration can safely modify the real list.
        this.listeners = new SnapshotArrayList<>();
    }
}
