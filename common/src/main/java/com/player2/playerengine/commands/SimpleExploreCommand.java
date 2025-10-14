package com.player2.playerengine.commands;

import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.movement.SimpleExploreTask;
import com.player2.playerengine.PlayerEngineController;

public class SimpleExploreCommand extends Command {
    public SimpleExploreCommand() throws CommandException {
        super("explore",
                "explores surrrounding area");
    }

    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
        mod.runUserTask(new SimpleExploreTask(), () -> {
            System.out.println("Simple explore task done");
            this.finish();
        });
    }

}