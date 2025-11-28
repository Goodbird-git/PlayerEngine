package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.misc.EatFoodTask;
import java.util.Optional;
import net.minecraft.world.item.Item;
import com.player2.playerengine.util.helpers.ItemHelper;
public class EatFoodCommand extends Command {
   public EatFoodCommand() throws CommandException {
      super("eat_food", "Eats food item from your inventory. ONLY CALL IF hunger < 20");
   }

   @Override
   protected void call(PlayerEngineController controller, ArgParser parser) throws CommandException {
      if(controller.getBaritone().getEntityContext().hungerManager().getFoodLevel() >= 20){
         throw new CommandException("Tried to call eatFood, but hunger was too high. You may not eat if hunger is full.");
      }

      Item[] items;

      if (parser.getArgUnits().length != 1) {
        return;
      }
      String itemNameAsString = parser.getArgUnits()[0].toLowerCase();
      Optional<Item> ma = ItemHelper.getItemFromString(itemNameAsString, ItemHelper.FOODS);
      if(!ma.isPresent()){
        return;
      }
      Item a = ma.get();
      controller.runUserTask(new EatFoodTask(a), () -> this.finish());
   }
}

