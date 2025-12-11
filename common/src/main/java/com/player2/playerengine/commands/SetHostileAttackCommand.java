package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;

public class SetHostileAttackCommand extends Command {
   public SetHostileAttackCommand() throws CommandException {
      super(
         "set_attack_hostiles",
         "will disable automatic attacking of hostiles. Only use when the user tells you to stop attacking/etc.",
         new Arg<>(SetHostileAttackCommand.ToggleState.class, "onOrOff")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      SetHostileAttackCommand.ToggleState toggle = parser.get(SetHostileAttackCommand.ToggleState.class);
      switch (toggle) {
         case ON:
            Debug.logMessage(
               "Enabling attack hostiles! You will now automatically attack nearby hostiles"
            );
            mod.setChatClefEnabled(true);
            break;
         case OFF:
            Debug.logMessage("Disabling attack hostiles! You will NOT attack nearby hostiles automatically.");
            mod.setChatClefEnabled(false);
      }

      this.finish();
   }

   public static enum ToggleState {
      ON,
      OFF;
   }
}
