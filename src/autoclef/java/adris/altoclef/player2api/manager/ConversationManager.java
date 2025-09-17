package adris.altoclef.player2api.manager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import adris.altoclef.player2api.AgentSideEffects;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.Event;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.AgentConversationData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Event.UserMessage;
import adris.altoclef.player2api.status.StatusUtils;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ChatMessage;
import net.minecraft.server.MinecraftServer;

public class ConversationManager {
    public static final Logger LOGGER = LogManager.getLogger();

    public static class Lock {
        public static boolean waitingForResponseLock = false; // prevents conversation processing before onLLMResponse
                                                              // called

        public static boolean isConversationLocked() {
            return waitingForResponseLock || TTSManager.isLocked();
        }
    }

    public static ConcurrentHashMap<UUID, AgentConversationData> queueData = new ConcurrentHashMap<>();
    public static final float messagePassingMaxDistance = 64; // let messages between entities pass iff <= this maximum
    private static boolean hasInit = false;

    public static void init() {
        if (!hasInit) {
            hasInit = true;
            // unused but need to keep this so subscribes to events
            // TODO: figure out what to do w. fabric here:
            ServerMessageEvents.CHAT_MESSAGE.register((ChatMessage) (evt, senderEntity, params) -> {
                String message = evt.signedContent();
                String sender = senderEntity.getName().getString();
                ConversationManager.onUserChatMessage(new UserMessage(message, sender));
            });
        }
    }

    private static List<LLMCompleter> llmCompleters = List.of(new LLMCompleter());

    // ## Utils
    public static AgentConversationData getOrCreateEventQueueData(AltoClefController mod) {
        return queueData.computeIfAbsent(mod.getPlayer().getUUID(), k -> {
            LOGGER.info(
                    "EventQueueManager/getOrCreateEventQueueData: creating new queue data for entId={}",
                    mod.getPlayer().getStringUUID());
            return new AgentConversationData(mod);
        });
    }

    private static Stream<AgentConversationData> filterQueueData(Predicate<AgentConversationData> pred) {
        return queueData.values().stream().filter(pred);
    }

    private static Stream<AgentConversationData> getCloseDataByUUID(UUID sender) {
        return filterQueueData(data -> data.getDistance(sender) < messagePassingMaxDistance);
    }

    // ## Callbacks (need to register these externally)

    // register when a user sends a chat message
    public static void onUserChatMessage(UserMessage msg) {
        LOGGER.info("User message event={}", msg);
        // will add to entities close to the user:
        filterQueueData(d -> isCloseToPlayer(d, msg.userName())).forEach(data -> {
            data.onEvent(msg);
        });
    }

    public static void onUpdateCommandStatus(AgentConversationData d, String commandName, String status) {
        LOGGER.info("Command status event cmdName={}, status={}", commandName, status);
        d.onEvent(new Event.CommandInfoMessage(
                commandName,
                String.format("Command feedback: While running command='%s', status got updated to status='%s'",
                        commandName, status)));
    }

    // register when an AI character messages
    public static void onAICharacterMessage(Event.CharacterMessage msg, UUID senderId) {
        UUID sendingUUID = msg.sendingCharacterData().getUUID();
        getCloseDataByUUID(sendingUUID).filter(data -> !(data.getUUID().equals(senderId)))
                .forEach(data -> {
                    LOGGER.info("onCharMsg/ msg={}, sender={}, running onCharMsg for ={}", msg.message(), senderId,
                            data.getName());
                    data.onAICharacterMessage(msg);
                });
    }

    private static void process(Consumer<Event.CharacterMessage> onCharacterEvent, Consumer<String> onErrEvent) {
        Optional<AgentConversationData> dataToProcess = queueData.values().stream().filter(data -> {
            return data.getPriority() != 0;
        }).max(Comparator.comparingLong(AgentConversationData::getPriority));
        llmCompleters.stream().filter(LLMCompleter::isAvailible).forEach(completer -> {
            dataToProcess.ifPresent(data -> {
                data.process(onCharacterEvent, onErrEvent, completer);
            });
        });
    }

    // side effects are here:
    public static void injectOnTick(MinecraftServer server) {
        if (!hasInit) {
            init();
        }

        Consumer<Event.CharacterMessage> onCharacterEvent = (data) -> {
            AgentSideEffects.onEntityMessage(server, data);
        };
        Consumer<String> onErrEvent = (errMsg) -> {
            AgentSideEffects.onError(server, errMsg);
        };

        if (!Lock.isConversationLocked()) {
            process(onCharacterEvent, onErrEvent);
        }

        TTSManager.injectOnTick(server);
    }

    public static void sendGreeting(AltoClefController mod, Character character) {
        LOGGER.info("Sending greeting character={}", character);
        AgentConversationData data = getOrCreateEventQueueData(mod);
        data.onGreeting();
    }

    public static void resetMemory(AltoClefController mod) {
        mod.getAIPersistantData().clearHistory();
    }

    private static boolean isCloseToPlayer(AgentConversationData data, String userName) {
        return StatusUtils.getDistanceToUsername(data.getMod(), userName) < messagePassingMaxDistance;
    }
}