package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.movement.BodyLanguageTask;

public class BodyLanguageCommand extends Command {
    public BodyLanguageCommand() throws CommandException {
        super("bodylang",
                "Perform some sort of dance/body language action. Action must be either `greeting`, `nod_head`, `shake_head`, `victory` ",
                new Arg<>(String.class, "bodyLanguage"));
    }

    @Override
    protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
        String bodyLanguage = parser.get(String.class);
        mod.runUserTask(new BodyLanguageTask(bodyLanguage), () -> {
            System.out.println("Body language done");
            this.finish();
        });
    }

}