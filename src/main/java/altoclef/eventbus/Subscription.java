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

import java.util.function.Consumer;

public class Subscription<T> {
   private final Consumer<T> callback;
   private volatile boolean shouldDelete;

   public Subscription(Consumer<T> callback) {
      this.callback = callback;
   }

   public void accept(T event) {
      this.callback.accept(event);
   }

   public void delete() {
      this.shouldDelete = true;
   }

   public boolean shouldDelete() {
      return this.shouldDelete;
   }
}
