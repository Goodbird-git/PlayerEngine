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

package com.player2.playerengine.automaton.utils.schematic.format;

import com.player2.playerengine.automaton.api.schematic.IStaticSchematic;
import com.player2.playerengine.automaton.api.schematic.format.ISchematicFormat;
import com.player2.playerengine.automaton.utils.schematic.format.defaults.MCEditSchematic;
import com.player2.playerengine.automaton.utils.schematic.format.defaults.SpongeSchematic;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.apache.commons.io.FilenameUtils;

public enum DefaultSchematicFormats implements ISchematicFormat {
   MCEDIT("schematic") {
      @Override
      public IStaticSchematic parse(InputStream input) throws IOException {
         return new MCEditSchematic(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
      }
   },
   SPONGE("schem") {
      @Override
      public IStaticSchematic parse(InputStream input) throws IOException {
         CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
         int version = nbt.getInt("Version").get();
         switch (version) {
            case 1:
            case 2:
               return new SpongeSchematic(nbt);
            default:
               throw new UnsupportedOperationException("Unsupported Version of a Sponge Schematic");
         }
      }
   };

   private final String extension;

   private DefaultSchematicFormats(String extension) {
      this.extension = extension;
   }

   @Override
   public boolean isFileType(File file) {
      return this.extension.equalsIgnoreCase(FilenameUtils.getExtension(file.getAbsolutePath()));
   }
}
