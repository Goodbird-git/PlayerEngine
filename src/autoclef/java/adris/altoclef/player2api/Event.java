package adris.altoclef.player2api;

public sealed interface Event // tagged union basically of the below events
        permits Event.UserMessage, Event.CharacterMessage, Event.InfoMessage {
    String message();

    public record UserMessage(String message, String userName) implements Event {
        public String getConversationHistoryString(){
            return String.format("Other Player Message: [%s]: %s", userName, message);
        }
        public String toString(){
            return String.format("UserMessage(userName='%s', message='%s')");
        }
    }

    public record InfoMessage(String message) implements Event{
        public String getConversationHistoryString(){
            return String.format("Info: %s", message);
        }
        public String toString(){
            return getConversationHistoryString();
        }
    }

    public record CharacterMessage(String message, String command, EventQueueData sendingCharacterData) implements Event {
        public String getConversationHistoryString(){
            return String.format("Other Agent Message: [%s]: %s", sendingCharacterData.getUsername(), message);
        }
        public String toString(){
            return String.format("CharacterMessage(name='%s', message='%s', command='%s')", sendingCharacterData.getUsername(), message, command);
        }
    }
}