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

package com.player2.playerengine.automaton.api.utils;

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.Settings;
import java.awt.Color;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public class SettingsUtil {
   public static List<Settings.Setting<?>> modifiedSettings(Settings settings) {
      List<Settings.Setting<?>> modified = new ArrayList<>();

      for (Settings.Setting<?> setting : settings.allSettings) {
         if (setting.get() == null) {
            System.err.println("NULL SETTING?" + setting.getName());
         } else if (!setting.getName().equals("logger") && setting.get() != setting.defaultValue()) {
            modified.add(setting);
         }
      }

      return modified;
   }

   public static String settingTypeToString(Settings.Setting<?> setting) {
      return setting.getType().getTypeName().replaceAll("(?:\\w+\\.)+(\\w+)", "$1");
   }

   public static <T> String settingValueToString(Settings.Setting<T> setting, T value) throws IllegalArgumentException {
      SettingsUtil.Parser io = SettingsUtil.Parser.getParser(setting.getType());
      if (io == null) {
         throw new IllegalStateException("Missing " + setting.getValueClass() + " " + setting.getName());
      } else {
         return io.toString(new SettingsUtil.ParserContext(setting), value);
      }
   }

   public static <T> String settingValueToString(Settings.Setting<T> setting) throws IllegalArgumentException {
      return settingValueToString(setting, setting.get());
   }

   public static <T> String settingDefaultToString(Settings.Setting<T> setting) throws IllegalArgumentException {
      return settingValueToString(setting, setting.defaultValue());
   }

   public static String maybeCensor(int coord) {
      return BaritoneAPI.getGlobalSettings().censorCoordinates.get() ? "<censored>" : Integer.toString(coord);
   }

   public static String settingToString(Settings.Setting<?> setting) throws IllegalStateException {
      return setting.getName().equals("logger") ? "logger" : setting.getName() + " " + settingValueToString(setting);
   }

   public static void parseAndApply(Settings settings, String settingName, String settingValue) throws IllegalStateException, NumberFormatException {
      Settings.Setting<?> setting = settings.byLowerName.get(settingName);
      if (setting == null) {
         throw new IllegalStateException("No setting by that name");
      } else {
         parseAndApply(setting, settingValue);
      }
   }

   private static <T> void parseAndApply(Settings.Setting<T> setting, String settingValue) {
      Class<T> intendedType = setting.getValueClass();
      SettingsUtil.Parser ioMethod = SettingsUtil.Parser.getParser(setting.getType());
      T parsed = (T)ioMethod.parse(new SettingsUtil.ParserContext(setting), settingValue);
      if (!intendedType.isInstance(parsed)) {
         throw new IllegalStateException(
            ioMethod + " parser returned incorrect type, expected " + intendedType + " got " + parsed + " which is " + parsed.getClass()
         );
      } else {
         setting.set(parsed);
      }
   }

   private static enum Parser {
      DOUBLE(Double.class, Double::parseDouble),
      BOOLEAN(Boolean.class, Boolean::parseBoolean),
      INTEGER(Integer.class, Integer::parseInt),
      FLOAT(Float.class, Float::parseFloat),
      LONG(Long.class, Long::parseLong),
      STRING(String.class, String::new),
      DIRECTION(Direction.class, d -> Direction.valueOf(d.toUpperCase(Locale.ROOT))),
      COLOR(
         Color.class,
         str -> new Color(Integer.parseInt(str.split(",")[0]), Integer.parseInt(str.split(",")[1]), Integer.parseInt(str.split(",")[2])),
         color -> color.getRed() + "," + color.getGreen() + "," + color.getBlue()
      ),
      VEC3I(
         Vec3i.class,
         str -> new Vec3i(Integer.parseInt(str.split(",")[0]), Integer.parseInt(str.split(",")[1]), Integer.parseInt(str.split(",")[2])),
         vec -> vec.getX() + "," + vec.getY() + "," + vec.getZ()
      ),
      BLOCK(Block.class, str -> BlockUtils.stringToBlockRequired(str.trim()), BlockUtils::blockToString),
      ITEM(
         Item.class, str -> (Item)BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(str.trim())).get().value(), item -> BuiltInRegistries.ITEM.getResourceKey(item).toString()
      ),
      TAG {
         @Override
         public Object parse(SettingsUtil.ParserContext context, String raw) {
            Type type = ((ParameterizedType)context.getSetting().getType()).getActualTypeArguments()[0];
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (type == Block.class) {
               return TagKey.create(Registries.BLOCK, id);
            } else if (type == Item.class) {
               return TagKey.create(Registries.ITEM, id);
            } else if (type == EntityType.class) {
               return TagKey.create(Registries.ENTITY_TYPE, id);
            } else if (type == Fluid.class) {
               return TagKey.create(Registries.FLUID, id);
            } else {
               throw new IllegalArgumentException();
            }
         }

         @Override
         public String toString(SettingsUtil.ParserContext context, Object value) {
            return ((TagKey)value).location().toString();
         }

         @Override
         public boolean accepts(Type type) {
            return TagKey.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
         }
      },
      LIST {
         @Override
         public Object parse(SettingsUtil.ParserContext context, String raw) {
            Type type = ((ParameterizedType)context.getSetting().getType()).getActualTypeArguments()[0];
            SettingsUtil.Parser parser = SettingsUtil.Parser.getParser(type);
            return Stream.of(raw.split(",")).map(s -> parser.parse(context, s)).collect(Collectors.toList());
         }

         @Override
         public String toString(SettingsUtil.ParserContext context, Object value) {
            Type type = ((ParameterizedType)context.getSetting().getType()).getActualTypeArguments()[0];
            SettingsUtil.Parser parser = SettingsUtil.Parser.getParser(type);
            return ((List<?>)value).stream().map(o -> parser.toString(context, o)).collect(Collectors.joining(","));
         }

         @Override
         public boolean accepts(Type type) {
            return List.class.isAssignableFrom(TypeUtils.resolveBaseClass(type));
         }
      };

      private final Class<?> cla$$;
      private final Function<String, Object> parser;
      private final Function<Object, String> toString;

      private Parser() {
         this.cla$$ = null;
         this.parser = null;
         this.toString = null;
      }

      private <T> Parser(Class<T> cla$$, Function<String, T> parser) {
         this(cla$$, parser, Object::toString);
      }

      private <T> Parser(Class<T> cla$$, Function<String, T> parser, Function<T, String> toString) {
         this.cla$$ = cla$$;
         this.parser = parser::apply;
         this.toString = x -> toString.apply(cla$$.cast(x));
      }

      public Object parse(SettingsUtil.ParserContext context, String raw) {
         Object parsed = this.parser.apply(raw);
         Objects.requireNonNull(parsed);
         return parsed;
      }

      public String toString(SettingsUtil.ParserContext context, Object value) {
         return this.toString.apply(value);
      }

      public boolean accepts(Type type) {
         return type instanceof Class && this.cla$$.isAssignableFrom((Class<?>)type);
      }

      public static SettingsUtil.Parser getParser(Type type) {
         return Stream.of(values()).filter(parser -> parser.accepts(type)).findFirst().orElse(null);
      }
   }

   private static class ParserContext {
      private final Settings.Setting<?> setting;

      private ParserContext(Settings.Setting<?> setting) {
         this.setting = setting;
      }

      private Settings.Setting<?> getSetting() {
         return this.setting;
      }
   }
}
