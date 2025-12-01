package com.player2.playerengine;

import com.player2.playerengine.commands.*;
import com.player2.playerengine.commands.*;
import com.player2.playerengine.commands.random.*;
import com.player2.playerengine.commands.base.CommandException;

public class PlayerEngineCommands {
   public static void init(PlayerEngineController controller) throws CommandException {
      controller.getCommandExecutor()
            .registerNewCommand(
                  new GetCommand(),
                  new EquipCommand(),
                  new BuildStructureCommand(),
                  new BodyLanguageCommand(),
                  new DepositCommand(),
                  new GotoCommand(),
                  new IdleCommand(),
                  new HeroCommand(),
                  new LocateStructureCommand(),
                  new StopCommand(),
                  new FoodCommand(),
                  new MeatCommand(),
                  new ReloadSettingsCommand(),
                  new ResetMemoryCommand(),
                  new GamerCommand(),
                  new FollowCommand(),
                  new GiveCommand(),
                  new ScanCommand(),
                  new AttackPlayerOrMobCommand(),
                  new SetAIBridgeEnabledCommand(),
                  new FarmCommand(),
                  new EatFoodCommand(),
                  new PickupDropsCommand(),
                  new FishCommand());
   }
}
