package adris.altoclef.player2api;

import java.util.Deque;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Event.InfoMessage;

public class AIPersistantData {
    // contains data relating to AI processing, only including data that is
    // permanent,
    // and persists across game state (not queue stuff)

    private ConversationHistory conversationHistory;
    private Character character;

    public AIPersistantData(AltoClefController mod, Character character) {
        String systemPrompt = Prompts.getAINPCSystemPrompt(this.character, mod.getCommandExecutor().allCommands());
        this.conversationHistory = new ConversationHistory(systemPrompt, character.name(), character.shortName());
        this.character = character;
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public Event getGreetingEvent() {
        String suffix = " IMPORTANT: SINCE THIS IS THE FIRST MESSAGE, DO NOT SEND A COMMAND!!";
        if (conversationHistory.isLoadedFromFile()) {
            return (new InfoMessage("You want to welcome user back." + suffix));
        } else {
            return (new InfoMessage(character.greetingInfo() + suffix));
        }
    }

    public void dumpEventQueueToConversationHistory(Deque<Event> eventQueue, Player2APIService player2apiService){
        while(!eventQueue.isEmpty()){
            Event event = eventQueue.poll();
            conversationHistory.addUserMessage(event.toString(), player2apiService);
        }
    }
    public ConversationHistory getConversationHistoryWrappedWithStatus(String worldStatus, String agentStatus, String altoClefDebugMsgs, Player2APIService player2apiService){
        return this.conversationHistory
                .copyThenWrapLatestWithStatus(worldStatus, agentStatus, altoClefDebugMsgs, player2apiService);
    }
    public void addAssistantMessage(String llmMessage, Player2APIService player2apiService){
        this.conversationHistory.addAssistantMessage(llmMessage, player2apiService);
    }
    public Character getCharacter(){
        return this.character;
    }
}
