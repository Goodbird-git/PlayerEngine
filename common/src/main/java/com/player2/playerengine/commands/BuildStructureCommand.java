package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask;

import net.minecraft.core.BlockPos;

public class BuildStructureCommand extends Command {
    public BuildStructureCommand() throws CommandException {
        super("build_structure",
                "Agent can build any thing in Minecraft given a 3d coordinate, search query, and a description. The coordinates define the center bottom of the building. The search query comes first and should be a very brief query to search for a schematic that matches the build requirements. The description should be a longer (but still short) string generated to capture a clear and concise summary of the structure the user asked to be built. Agent does not need to collect materials to build the structure when using this function.\\n"
                        + //
                        "IMPORTANT: The first 3 arguments should be the coordinates. If the player you are talking to doesn't give any hints on where to build it, put in that player's position as the first 3 arguments, or some positional information. You MUST start with the coordinates to build at. If you don't know the player's position, then put your own position. \\n"
                        + //
                        "IMPORTANT: The latter 2 arguments should be surrounded by quotes. That is how we separate the search query and the description from each other.\n"
                        + //
                        " Example call would be `build_structure -305 406 72 \"gray modern house\" \"a gray modern house with a garden of roses in front of it.\"`",
                // new Arg<>(String.class, "arguments")
                new Arg<>(Integer.class, "x"),
                new Arg<>(Integer.class, "y"),
                new Arg<>(Integer.class, "x"),
                new Arg<>(String.class, "search_query"),
                new Arg<>(String.class, "description")
        );
    }

    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
        // String args = parser.get(String.class);
        int x = parser.get(Integer.class);
        int y = parser.get(Integer.class);
        int z = parser.get(Integer.class);
        String query = parser.get(String.class);
        String description = parser.get(String.class);
        System.out.println("ASDF ARGS: " +  x + ", " + y + ", " + z + ", " + query + ", " + description);
        mod.runUserTask(new BuildStructureTask(new BlockPos(x, y, z), query, description, mod), () -> {
            this.finish();
        });
    }
}