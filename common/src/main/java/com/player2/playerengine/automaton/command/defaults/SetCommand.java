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

package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.Settings;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidTypeException;
import com.player2.playerengine.automaton.api.command.helpers.Paginator;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import com.player2.playerengine.automaton.api.utils.SettingsUtil;
import com.player2.playerengine.automaton.command.argument.ArgConsumer;
import com.player2.playerengine.automaton.utils.SettingsLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent.Action;

public class SetCommand extends Command {
   public SetCommand() {
      super("set", "setting", "settings");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      String arg = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : "list";
      Settings settings;
      boolean global;
      if (Arrays.asList("g", "global").contains(arg.toLowerCase(Locale.ROOT))) {
         settings = BaritoneAPI.getGlobalSettings();
         arg = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : arg;
         global = true;
      } else {
         settings = baritone.settings();
         global = false;
      }

      if (Arrays.asList("s", "save").contains(arg)) {
         SettingsLoader.save(settings);
         this.logDirect(source, "Settings saved");
      } else {
         boolean viewModified = Arrays.asList("m", "mod", "modified").contains(arg);
         boolean viewAll = Arrays.asList("all", "l", "list").contains(arg);
         boolean paginate = viewModified || viewAll;
         if (paginate) {
            String search = args.hasAny() && args.peekAsOrNull(Integer.class) == null ? args.getString() : "";
            args.requireMax(1);
            List<? extends Settings.Setting<?>> toPaginate = (viewModified ? SettingsUtil.modifiedSettings(settings) : settings.allSettings)
               .stream()
               .filter(s -> !s.getName().equals("logger"))
               .filter(s -> s.getName().toLowerCase(Locale.US).contains(search.toLowerCase(Locale.US)))
               .sorted((s1, s2) -> String.CASE_INSENSITIVE_ORDER.compare(s1.getName(), s2.getName()))
               .collect(Collectors.toList());
            Paginator.paginate(
               args,
               new Paginator<>(source, toPaginate),
               () -> this.logDirect(
                  source,
                  !search.isEmpty()
                     ? String.format("All %ssettings containing the string '%s':", viewModified ? "modified " : "", search)
                     : String.format("All %ssettings:", viewModified ? "modified " : "")
               ),
               settingx -> {
                  MutableComponent typeComponent = Component.literal(String.format(" (%s)", SettingsUtil.settingTypeToString(settingx)));
                  typeComponent.setStyle(typeComponent.getStyle().applyFormat(ChatFormatting.DARK_GRAY));
                  MutableComponent hoverComponent = Component.literal("");
                  hoverComponent.setStyle(hoverComponent.getStyle().applyFormat(ChatFormatting.GRAY));
                  hoverComponent.append(settingx.getName());
                  hoverComponent.append(String.format("\nType: %s", SettingsUtil.settingTypeToString(settingx)));
                  hoverComponent.append(String.format("\n\nValue:\n%s", SettingsUtil.settingValueToString(settingx)));
                  hoverComponent.append(String.format("\n\nDefault Value:\n%s", SettingsUtil.settingDefaultToString(settingx)));
                  String commandSuggestion = "/automatone " + String.format("set %s%s ", global ? "global " : "", settingx.getName());
                  MutableComponent component = Component.literal(settingx.getName());
                  component.setStyle(component.getStyle().applyFormat(ChatFormatting.GRAY));
                  component.append(typeComponent);
                  component.setStyle(
                     component.getStyle()
                        .withHoverEvent(new HoverEvent.ShowText(hoverComponent))
                        .withClickEvent(new ClickEvent.SuggestCommand(commandSuggestion))
                  );
                  return component;
               },
               "/automatone set " + arg + " " + search
            );
         } else {
            args.requireMax(1);
            boolean resetting = arg.equalsIgnoreCase("reset");
            boolean toggling = arg.equalsIgnoreCase("toggle");
            boolean doingSomething = resetting || toggling;
            if (resetting) {
               if (!args.hasAny()) {
                  this.logDirect(source, "Please specify 'all' as an argument to reset to confirm you'd really like to do this");
                  this.logDirect(source, "ALL settings will be reset. Use the 'set modified' or 'modified' commands to see what will be reset.");
                  this.logDirect(source, "Specify a setting name instead of 'all' to only reset one setting");
               } else if (args.peekString().equalsIgnoreCase("all")) {
                  SettingsUtil.modifiedSettings(settings).forEach(Settings.Setting::reset);
                  this.logDirect(source, "All settings have been reset to their default values");
                  SettingsLoader.save(settings);
                  return;
               }
            }

            if (toggling) {
               args.requireMin(1);
            }

            String settingName = doingSomething ? args.getString() : arg;
            Settings.Setting<?> setting = settings.allSettings.stream().filter(s -> s.getName().equalsIgnoreCase(settingName)).findFirst().orElse(null);
            if (setting == null) {
               throw new CommandInvalidTypeException(args.consumed(), "a valid setting");
            } else {
               if (!doingSomething && !args.hasAny()) {
                  this.logDirect(source, String.format("Value of setting %s:", setting.getName()));
                  this.logDirect(source, SettingsUtil.settingValueToString(setting));
               } else {
                  String oldValue = SettingsUtil.settingValueToString(setting);
                  if (resetting) {
                     setting.reset();
                  } else if (toggling) {
                     if (setting.getValueClass() != Boolean.class) {
                        throw new CommandInvalidTypeException(args.consumed(), "a toggleable setting", "some other setting");
                     }
                     @SuppressWarnings("unchecked") Settings.Setting<Boolean> toggle = (Settings.Setting<Boolean>) setting;
                     toggle.set(!toggle.get());
                     this.logDirect(source, String.format("Toggled setting %s to %s", setting.getName(), setting.get()));
                  } else {
                     String newValue = args.getString();

                     try {
                        SettingsUtil.parseAndApply(settings, arg, newValue);
                     } catch (Throwable var19) {
                        PlayerEngine.LOGGER.error(var19);
                        throw new CommandInvalidTypeException(args.consumed(), "a valid value", var19);
                     }
                  }

                  if (!toggling) {
                     this.logDirect(
                        source,
                        String.format("Successfully %s %s to %s", resetting ? "reset" : "set", setting.getName(), SettingsUtil.settingValueToString(setting))
                     );
                  }

                  MutableComponent oldValueComponent = Component.literal(String.format("Old value: %s", oldValue));
                  oldValueComponent.setStyle(
                     oldValueComponent.getStyle()
                        .applyFormat(ChatFormatting.GRAY)
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to set the setting back to this value")))
                        .withClickEvent(
                           new ClickEvent.RunCommand(
                              "/automatone " + String.format("set %s %s", setting.getName(), oldValue)
                           )
                        )
                  );
                  this.logDirect(source, new Component[]{oldValueComponent});
               }

               SettingsLoader.save(settings);
            }
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      Settings settings = ((ArgConsumer)args).getBaritone().settings();
      if (args.hasAny()) {
         String arg = args.getString();
         if (Arrays.asList("g", "global").contains(arg.toLowerCase(Locale.ROOT))) {
            if (!args.hasAny()) {
               return new TabCompleteHelper()
                  .addSettings(settings)
                  .sortAlphabetically()
                  .prepend("list", "modified", "reset", "toggle", "save")
                  .filterPrefix(arg)
                  .stream();
            }

            arg = args.getString();
         }

         if (args.hasExactlyOne() && !Arrays.asList("s", "save").contains(args.peekString().toLowerCase(Locale.ROOT))) {
            if (arg.equalsIgnoreCase("reset")) {
               return new TabCompleteHelper().addModifiedSettings(settings).prepend("all").filterPrefix(args.getString()).stream();
            }

            if (arg.equalsIgnoreCase("toggle")) {
               return new TabCompleteHelper().addToggleableSettings(settings).filterPrefix(args.getString()).stream();
            }

            Settings.Setting<?> setting = settings.byLowerName.get(arg.toLowerCase(Locale.US));
            if (setting != null) {
               if (setting.getType() == Boolean.class) {
                  TabCompleteHelper helper = new TabCompleteHelper();
                  if ((Boolean)setting.get()) {
                     helper.append("true", "false");
                  } else {
                     helper.append("false", "true");
                  }

                  return helper.filterPrefix(args.getString()).stream();
               }

               return Stream.of(SettingsUtil.settingValueToString(setting));
            }
         } else if (!args.hasAny()) {
            return new TabCompleteHelper()
               .addSettings(settings)
               .sortAlphabetically()
               .prepend("list", "modified", "reset", "toggle", "save")
               .prepend("global")
               .filterPrefix(arg)
               .stream();
         }
      }

      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "View or change settings";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Using the set command, you can manage all of Baritone's settings. Almost every aspect is controlled by these settings - go wild!",
         "",
         "Usage:",
         "> set - Same as `set list`",
         "> set list [page] - View all settings",
         "> set modified [page] - View modified settings",
         "> set <setting> - View the current value of a setting",
         "> set <setting> <value> - Set the value of a setting",
         "> set reset all - Reset ALL SETTINGS to their defaults",
         "> set reset <setting> - Reset a setting to its default",
         "> set toggle <setting> - Toggle a boolean setting",
         "> set save - Save all settings (this is automatic tho)"
      );
   }
}
