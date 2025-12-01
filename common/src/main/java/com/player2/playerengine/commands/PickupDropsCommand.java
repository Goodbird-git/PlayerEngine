package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.util.ItemTarget;
public class PickupDropsCommand extends Command {
   public PickupDropsCommand() throws CommandException {
      super(
         "pickup_drops",
         "picks up all item drops nearby"
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      mod.runUserTask(new PickupDroppedItemTask(new ItemTarget[]{}, true), () -> this.finish());
   }
}

