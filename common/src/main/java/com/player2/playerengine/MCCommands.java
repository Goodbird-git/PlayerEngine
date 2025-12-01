package com.player2.playerengine;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;


import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.player2.playerengine.player2api.Player2APIService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.world.entity.player.Player;
import com.player2.playerengine.player2api.AgentSideEffects;
import net.minecraft.server.level.ServerPlayer;




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
                        .then(registerHelp()));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> registerHelp() {
        return Commands.literal("help")
                .executes(context -> {
                    LOGGER.info("help command");
                    Player player = context.getSource().getPlayer();
                    AgentSideEffects.broadcastChatToPlayer(player.level().getServer(), "help: TODO: fillin ", (ServerPlayer) player);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> registerRelog() {
        
        return Commands.literal("relog")
                .executes(context -> {
                    for (Player2APIService service : PlayerEngineController.staticAPIServices.values()) {
                        LOGGER.info("relog command");
                        String clientId = service.getClientId();
                        Player player = context.getSource().getPlayer();
                        AuthenticationManager.getInstance().invalidateToken(player, clientId);
                    }
                    return 1;
                });
    }
}