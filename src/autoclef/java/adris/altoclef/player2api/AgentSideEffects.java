package adris.altoclef.player2api;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.CommandExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class AgentSideEffects {
    private static final Logger LOGGER = LogManager.getLogger();


    public sealed interface CommandExecutionStopReason
            permits CommandExecutionStopReason.Cancelled,
            CommandExecutionStopReason.Finished,
            CommandExecutionStopReason.Error {
        String commandName();

        record Cancelled(String commandName) implements CommandExecutionStopReason {
        }

        record Finished(String commandName) implements CommandExecutionStopReason {
        }

        record Error(String commandName, String errMsg) implements CommandExecutionStopReason {
        }
    }

    public static void onEntityMessage(MinecraftServer server, Event.CharacterMessage characterMessage) {
        // message part:
        if (characterMessage.message() != null) {
            EventQueueData sendingCharacterData = characterMessage.sendingCharacterData();
                        String message = String.format("<%s> %s", sendingCharacterData, characterMessage.message());
            for(ServerPlayer player : server.getPlayerList().getPlayers()){
                // if you are an owner, or close, send to player.
                // if(sendingCharacterData.isOwner(player.getUUID()) || isClose(sendingCharacterData, player)  ){
                    broadcastChatToPlayer(server, message, player);
                // }
            }
            TTSManager.TTS(message, sendingCharacterData.getCharacter(), sendingCharacterData.getPlayer2apiService());
            EventQueueManager.onAICharacterMessage(characterMessage, characterMessage.sendingCharacterData().getUUID());
        }

        // command part:
        if (characterMessage.command() != null) {
            onCommandListGenerated(characterMessage.sendingCharacterData().getMod(), characterMessage.command(),
                    characterMessage.sendingCharacterData()::onCommandFinish);
        }
    }

    public static void onError(MinecraftServer server, String errMsg) {
        LOGGER.error(errMsg);
    }

    public static void onCommandListGenerated(AltoClefController mod, String command,
            Consumer<CommandExecutionStopReason> onStop) {
        CommandExecutor cmdExecutor = mod.getCommandExecutor();
        String commandWithPrefix = cmdExecutor.isClientCommand(command) ? command
                : (cmdExecutor.getCommandPrefix() + command);
        if (commandWithPrefix.equals("@stop")) {
            mod.isStopping = true;
        } else {
            mod.isStopping = false;
        }
        cmdExecutor.execute(commandWithPrefix, () -> {
            if (mod.isStopping) {
                System.out.printf(
                        "[AgentSideEffects/AgentSideEffects]: (%s) was cancelled. Not adding finish event to queue.",
                        commandWithPrefix);
                // Other canceled logic here
                onStop.accept(new CommandExecutionStopReason.Cancelled(commandWithPrefix));
            } else {
                onStop.accept(new CommandExecutionStopReason.Finished(commandWithPrefix));
            }
        }, (err) -> {
            onStop.accept(new CommandExecutionStopReason.Error(commandWithPrefix, err.getMessage()));
        });
    }

    private static void broadcastChatToPlayer(MinecraftServer server, String message, ServerPlayer player){
        player.displayClientMessage(Component.literal(message), false);
    }

}
