package com.player2.playerengine;

import com.player2.playerengine.commands.AttackPlayerOrMobCommand;
import com.player2.playerengine.commands.BodyLanguageCommand;
import com.player2.playerengine.commands.BuildStructureCommand;
import com.player2.playerengine.commands.DepositCommand;
import com.player2.playerengine.commands.EquipCommand;
import com.player2.playerengine.commands.FarmCommand;
import com.player2.playerengine.commands.FishCommand;
import com.player2.playerengine.commands.FollowCommand;
import com.player2.playerengine.commands.FoodCommand;
import com.player2.playerengine.commands.GamerCommand;
import com.player2.playerengine.commands.GetCommand;
import com.player2.playerengine.commands.GiveCommand;
import com.player2.playerengine.commands.GotoCommand;
import com.player2.playerengine.commands.HeroCommand;
import com.player2.playerengine.commands.IdleCommand;
import com.player2.playerengine.commands.LocateStructureCommand;
import com.player2.playerengine.commands.MeatCommand;
import com.player2.playerengine.commands.ReloadSettingsCommand;
import com.player2.playerengine.commands.ResetMemoryCommand;
import com.player2.playerengine.commands.SetAIBridgeEnabledCommand;
import com.player2.playerengine.commands.StopCommand;
import com.player2.playerengine.commands.random.ScanCommand;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.SimpleExploreCommand;

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
                  new SimpleExploreCommand(),
                  new FishCommand());
   }
}
