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

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.cache.IWaypoint;
import com.player2.playerengine.automaton.api.cache.Waypoint;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.datatypes.ForWaypoints;
import com.player2.playerengine.automaton.api.command.datatypes.RelativeBlockPos;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidTypeException;
import com.player2.playerengine.automaton.api.command.helpers.Paginator;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class WaypointsCommand extends Command {
   public WaypointsCommand() {
      super("waypoints", "waypoint", "wp");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      WaypointsCommand.Action action = args.hasAny() ? WaypointsCommand.Action.getByName(args.getString()) : WaypointsCommand.Action.LIST;
      if (action == null) {
         throw new CommandInvalidTypeException(args.consumed(), "an action");
      } else {
         BiFunction<IWaypoint, WaypointsCommand.Action, Component> toComponent = (waypointx, _action) -> {
            MutableComponent component = Component.literal("");
            MutableComponent tagComponent = Component.literal(waypointx.getTag().name() + " ");
            tagComponent.setStyle(tagComponent.getStyle().applyFormat(ChatFormatting.GRAY));
            String name = waypointx.getName();
            MutableComponent nameComponent = Component.literal(!name.isEmpty() ? name : "<empty>");
            nameComponent.setStyle(nameComponent.getStyle().applyFormat(!name.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
            MutableComponent timestamp = Component.literal(" @ " + new Date(waypointx.getCreationTimestamp()));
            timestamp.setStyle(timestamp.getStyle().applyFormat(ChatFormatting.DARK_GRAY));
            component.append(tagComponent);
            component.append(nameComponent);
            component.append(timestamp);
            component.setStyle(
               component.getStyle()
                  .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to select")))
                  .withClickEvent(
                     new ClickEvent.RunCommand(
                        String.format(
                           "%s%s %s %s @ %d", "/automatone ", label, _action.names[0], waypointx.getTag().getName(), waypointx.getCreationTimestamp()
                        )
                     )
                  )
            );
            return component;
         };
         Function<IWaypoint, Component> transform = waypointx -> toComponent.apply(
            waypointx, action == WaypointsCommand.Action.LIST ? WaypointsCommand.Action.INFO : action
         );
         if (action == WaypointsCommand.Action.LIST) {
            IWaypoint.Tag tag = args.hasAny() ? IWaypoint.Tag.getByName(args.peekString()) : null;
            if (tag != null) {
               args.get();
            }

            IWaypoint[] waypoints = tag != null ? ForWaypoints.getWaypointsByTag(baritone, tag) : ForWaypoints.getWaypoints(baritone);
            if (waypoints.length <= 0) {
               args.requireMax(0);
               throw new CommandInvalidStateException(tag != null ? "No waypoints found by that tag" : "No waypoints found");
            }

            args.requireMax(1);
            Paginator.paginate(
               args,
               waypoints,
               () -> this.logDirect(source, tag != null ? String.format("All waypoints by tag %s:", tag.name()) : "All waypoints:"),
               transform,
               String.format("%s%s %s%s", "/automatone ", label, action.names[0], tag != null ? " " + tag.getName() : ""),
               source
            );
         } else if (action == WaypointsCommand.Action.SAVE) {
            IWaypoint.Tag tagx = IWaypoint.Tag.getByName(args.getString());
            if (tagx == null) {
               throw new CommandInvalidStateException(String.format("'%s' is not a tag ", args.consumedString()));
            }

            String name = args.hasAny() ? args.getString() : "";
            BetterBlockPos pos = args.hasAny()
               ? args.getDatatypePost(RelativeBlockPos.INSTANCE, baritone.getEntityContext().feetPos())
               : baritone.getEntityContext().feetPos();
            args.requireMax(0);
            IWaypoint waypoint = new Waypoint(name, tagx, pos);
            ForWaypoints.waypoints(baritone).addWaypoint(waypoint);
            MutableComponent component = Component.literal("Waypoint added: ");
            component.setStyle(component.getStyle().applyFormat(ChatFormatting.GRAY));
            component.append(toComponent.apply(waypoint, WaypointsCommand.Action.INFO));
            this.logDirect(source, new Component[]{component});
         } else if (action == WaypointsCommand.Action.CLEAR) {
            args.requireMax(1);
            IWaypoint.Tag tagx = IWaypoint.Tag.getByName(args.getString());
            IWaypoint[] waypoints = ForWaypoints.getWaypointsByTag(baritone, tagx);

            for (IWaypoint waypoint : waypoints) {
               ForWaypoints.waypoints(baritone).removeWaypoint(waypoint);
            }

            this.logDirect(source, String.format("Cleared %d waypoints", waypoints.length));
         } else {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            if (args.hasAny() && args.peekString().equals("@")) {
               args.requireExactly(2);
               args.get();
               long timestamp = args.getAs(Long.class);

               for (IWaypoint iWaypoint : waypoints) {
                  if (iWaypoint.getCreationTimestamp() == timestamp) {
                     waypoint = iWaypoint;
                     break;
                  }
               }

               if (waypoint == null) {
                  throw new CommandInvalidStateException("Timestamp was specified but no waypoint was found");
               }
            } else {
               switch (waypoints.length) {
                  case 0:
                     throw new CommandInvalidStateException("No waypoints found");
                  case 1:
                     waypoint = waypoints[0];
               }
            }

            if (waypoint == null) {
               args.requireMax(1);
               Paginator.paginate(
                  args,
                  waypoints,
                  () -> this.logDirect(source, "Multiple waypoints were found:"),
                  transform,
                  String.format("%s%s %s %s", "/automatone ", label, action.names[0], args.consumedString()),
                  source
               );
            } else if (action == WaypointsCommand.Action.INFO) {
               this.logDirect(source, new Component[]{transform.apply(waypoint)});
               this.logDirect(source, String.format("Position: %s", waypoint.getLocation()));
               MutableComponent deleteComponent = Component.literal("Click to delete this waypoint");
               deleteComponent.setStyle(
                  deleteComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent.RunCommand(
                           String.format("%s%s delete %s @ %d", "/automatone ", label, waypoint.getTag().getName(), waypoint.getCreationTimestamp())
                        )
                     )
               );
               MutableComponent goalComponent = Component.literal("Click to set goal to this waypoint");
               goalComponent.setStyle(
                  goalComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent.RunCommand(
                           String.format("%s%s goal %s @ %d", "/automatone ", label, waypoint.getTag().getName(), waypoint.getCreationTimestamp())
                        )
                     )
               );
               MutableComponent backComponent = Component.literal("Click to return to the waypoints list");
               backComponent.setStyle(
                  backComponent.getStyle()
                     .withClickEvent(
                        new ClickEvent.RunCommand(String.format("%s%s list", "/automatone ", label))
                     )
               );
               this.logDirect(source, new Component[]{deleteComponent});
               this.logDirect(source, new Component[]{goalComponent});
               this.logDirect(source, new Component[]{backComponent});
            } else if (action == WaypointsCommand.Action.DELETE) {
               ForWaypoints.waypoints(baritone).removeWaypoint(waypoint);
               this.logDirect(source, "That waypoint has successfully been deleted");
            } else if (action == WaypointsCommand.Action.GOAL) {
               Goal goal = new GoalBlock(waypoint.getLocation());
               baritone.getCustomGoalProcess().setGoal(goal);
               this.logDirect(source, String.format("Goal: %s", goal));
            } else if (action == WaypointsCommand.Action.GOTO) {
               Goal goal = new GoalBlock(waypoint.getLocation());
               baritone.getCustomGoalProcess().setGoalAndPath(goal);
               this.logDirect(source, String.format("Going to: %s", goal));
            }
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      if (args.hasAny()) {
         if (args.hasExactlyOne()) {
            return new TabCompleteHelper().append(WaypointsCommand.Action.getAllNames()).sortAlphabetically().filterPrefix(args.getString()).stream();
         }

         WaypointsCommand.Action action = WaypointsCommand.Action.getByName(args.getString());
         if (args.hasExactlyOne()) {
            if (action != WaypointsCommand.Action.LIST && action != WaypointsCommand.Action.SAVE && action != WaypointsCommand.Action.CLEAR) {
               return args.tabCompleteDatatype(ForWaypoints.INSTANCE);
            }

            return new TabCompleteHelper().append(IWaypoint.Tag.getAllNames()).sortAlphabetically().filterPrefix(args.getString()).stream();
         }

         if (args.has(3) && action == WaypointsCommand.Action.SAVE) {
            args.get();
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
         }
      }

      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Manage waypoints";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The waypoint command allows you to manage Baritone's waypoints.",
         "",
         "Waypoints can be used to mark positions for later. Waypoints are each given a tag and an optional name.",
         "",
         "Note that the info, delete, and goal commands let you specify a waypoint by tag. If there is more than one waypoint with a certain tag, then they will let you select which waypoint you mean.",
         "",
         "Usage:",
         "> wp [l/list] - List all waypoints.",
         "> wp <s/save> <tag> - Save your current position as an unnamed waypoint with the specified tag.",
         "> wp <s/save> <tag> <name> - Save the waypoint with the specified name.",
         "> wp <s/save> <tag> <name> <pos> - Save the waypoint with the specified name and position.",
         "> wp <i/info/show> <tag> - Show info on a waypoint by tag.",
         "> wp <d/delete> <tag> - Delete a waypoint by tag.",
         "> wp <g/goal> <tag> - Set a goal to a waypoint by tag.",
         "> wp <goto> <tag> - Set a goal to a waypoint by tag and start pathing."
      );
   }

   private static enum Action {
      LIST("list", "get", "l"),
      CLEAR("clear", "c"),
      SAVE("save", "s"),
      INFO("info", "show", "i"),
      DELETE("delete", "d"),
      GOAL("goal", "g"),
      GOTO("goto");

      private final String[] names;

      private Action(String... names) {
         this.names = names;
      }

      public static WaypointsCommand.Action getByName(String name) {
         for (WaypointsCommand.Action action : values()) {
            for (String alias : action.names) {
               if (alias.equalsIgnoreCase(name)) {
                  return action;
               }
            }
         }

         return null;
      }

      public static String[] getAllNames() {
         Set<String> names = new HashSet<>();

         for (WaypointsCommand.Action action : values()) {
            names.addAll(Arrays.asList(action.names));
         }

         return names.toArray(new String[0]);
      }
   }
}
