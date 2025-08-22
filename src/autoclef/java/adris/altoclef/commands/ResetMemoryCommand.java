package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.player2api.EventQueueManager;

public class ResetMemoryCommand extends Command {
   public ResetMemoryCommand() {
      super("resetmemory", "Reset the memory, does not stop the agent, can ONLY be run by the user (NOT the agent).");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      EventQueueManager.resetMemory(mod);
      this.finish();
   }
}
