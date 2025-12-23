package com.player2.playerengine;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.AgentSideEffects;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.player2.playerengine.player2api.manager.ConversationManager;

import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
public class MCCommands {

    public static final Logger LOGGER = LogManager.getLogger(PlayerEngine.MOD_NAME);

    public static void onInit() {
        LifecycleEvent.SERVER_STARTING.register(server -> {
            LOGGER.info("Server starting, registering MC commands");
            register(server);
        });
    }

    public static void register(MinecraftServer server) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        registerFromDispatch(dispatcher);
    }

    private static void registerFromDispatch(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("playerengine")
                        .then(registerRelog())
                        .then(registerSummon())
                        .then(registerHelp()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> registerHelp() {
        return Commands.literal("help")
                .executes(context -> {
                    LOGGER.info("help command");
                    Player player = context.getSource().getPlayerOrException();
                    String message = """
                        Help: here are the following
                        - 'playerengine relog': 
                        - 'help': displays this help
                        - 'tpto <username>': teleports you to AI 
                        - 'list': lists AI usernames
                    """;
                    // Your AgentSideEffects.broadcastChatToPlayer call (keeps original behavior)
                    AgentSideEffects.broadcastChatToPlayer(player.level().getServer(), message, (ServerPlayer) player);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> registerRelog() {
        return Commands.literal("relog")
                .executes(context -> {
                    for (Player2APIService service : PlayerEngineController.staticAPIServices.values()) {
                        LOGGER.info("relog command");
                        String clientId = service.getClientId();
                        Player player = context.getSource().getPlayerOrException();
                        AuthenticationManager.getInstance().invalidateToken(player, clientId);
                    }
                    return 1;
                });
    }

   private static final SuggestionProvider<CommandSourceStack> NPC_SUGGEST = (context, builder) -> {
        CommandSourceStack src = context.getSource();
        Player player = src.getPlayerOrException();
        UUID ownerUUID = player.getUUID();

        String remaining = builder.getRemaining().toLowerCase();

        for (AgentConversationData data : ConversationManager.getDataByOwner(ownerUUID)) {
            String name = data.getName();

            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }; 

    private static LiteralArgumentBuilder<CommandSourceStack> registerSummon() {
        return Commands.literal("tpto")
                .then(Commands.argument("username", StringArgumentType.string())
                        .suggests(NPC_SUGGEST)
                        .executes(context -> {
                            String username = StringArgumentType.getString(context, "username");
                            Player owner = context.getSource().getPlayerOrException();
                            UUID ownerUUID = owner.getUUID();
                            LOGGER.info("tpto command: {} {}", username, ownerUUID);
                            for (AgentConversationData data : ConversationManager.getDataByOwner(ownerUUID)) {
                                LOGGER.info("looking for name {}", data.getName());
                                if(data.getName().equals(username)){
                                    LOGGER.info("Match on username: {}", username);
                                    AgentSideEffects.teleportOwnerTo(data);
                                }
                            }
                            return 1;
                        }));
    }

}
