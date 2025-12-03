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

package altoclef.eventbus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe event bus for publishing and subscribing to events.
 * Uses CopyOnWriteArrayList to allow safe iteration during publish
 * while subscriptions may be added/removed from other threads.
 */
public class EventBus {
   private static final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Subscription<?>>> topics = new ConcurrentHashMap<>();

   public static <T> void publish(T event) {
      Class<?> type = event.getClass();
      CopyOnWriteArrayList<Subscription<?>> subscribers = topics.get(type);

      if (subscribers != null) {
         for (Subscription<?> subRaw : subscribers) {
            try {
               if (subRaw.shouldDelete()) {
                  // CopyOnWriteArrayList allows safe removal during iteration
                  subscribers.remove(subRaw);
               } else {
                  @SuppressWarnings("unchecked")
                  Subscription<T> sub = (Subscription<T>) subRaw;
                  sub.accept(event);
               }
            } catch (ClassCastException e) {
               System.err.println("TRIED PUBLISHING MISMAPPED EVENT: " + event);
               e.printStackTrace();
            }
         }
      }
   }

   public static <T> Subscription<T> subscribe(Class<T> type, Consumer<T> consumeEvent) {
      Subscription<T> sub = new Subscription<>(consumeEvent);
      // computeIfAbsent is atomic - ensures thread-safe initialization
      topics.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(sub);
      return sub;
   }

   public static <T> void unsubscribe(Subscription<T> subscription) {
      if (subscription != null) {
         subscription.delete();
      }
   }
}
