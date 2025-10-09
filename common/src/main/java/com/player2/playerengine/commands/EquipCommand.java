package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.tasks.misc.EquipArmorTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

public class EquipCommand extends Command {
   public EquipCommand() throws CommandException {
      super("equip", "Equips items. Example; `equip iron_chestplate` equips an iron chestplate.",
            new Arg<>(ItemList.class, "[equippable_items]"));
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      ItemTarget[] items;
      if (parser.getArgUnits().length == 1) {
         String var4 = parser.getArgUnits()[0].toLowerCase();
         switch (var4) {
            case "leather":
               items = ItemTarget.of(ItemHelper.LEATHER_ARMORS);
               break;
            case "iron":
               items = ItemTarget.of(ItemHelper.IRON_ARMORS);
               break;
            case "gold":
               items = ItemTarget.of(ItemHelper.GOLDEN_ARMORS);
               break;
            case "diamond":
               items = ItemTarget.of(ItemHelper.DIAMOND_ARMORS);
               break;
            case "netherite":
               items = ItemTarget.of(ItemHelper.NETHERITE_ARMORS);
               break;
            case "chainmail":
               items = ItemTarget.of(ItemHelper.CHAINMAIL_ARMORS);
               break;
            default:
               items = parser.get(ItemList.class).items;
         }
      } else {
         items = parser.get(ItemList.class).items;
      }

      for (ItemTarget target : items) {
         for (Item item : target.getMatches()) {
            if (!(item instanceof ArmorItem)) {
               throw new CommandException("'" + item.toString().toUpperCase() + "' cannot be equipped!");
            }
         }
      }

      mod.runUserTask(new EquipArmorTask(items), () -> this.finish());
   }
}
