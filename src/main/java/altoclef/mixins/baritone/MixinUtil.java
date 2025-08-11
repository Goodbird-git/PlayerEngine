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

package altoclef.mixins.baritone;

import baritone.Automatone;
import java.util.concurrent.ExecutorService;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Util.class})
public abstract class MixinUtil {
   @Shadow
   private static void shutdownExecutor(ExecutorService service) {
   }

   @Inject(
      method = {"shutdownExecutors"},
      at = {@At("RETURN")}
   )
   private static void shutdownBaritoneExecutor(CallbackInfo ci) {
      shutdownExecutor(Automatone.getExecutor());
   }
}
