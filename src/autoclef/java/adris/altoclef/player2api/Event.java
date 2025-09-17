package adris.altoclef.player2api;

public sealed interface Event // tagged union basically of the below events
        permits Event.UserMessage, Event.CharacterMessage, Event.InfoMessage, Event.CommandInfoMessage {
    String message();

    public String getConversationHistoryString();

    public record UserMessage(String message, String userName) implements Event {
        public String getConversationHistoryString() {
            return String.format("User Message: [%s]: %s", userName, message);
        }

        public String toString() {
            return String.format("UserMessage(userName='%s', message='%s')", userName, message);
        }
    }

    public record CommandInfoMessage(String commandName, String message) implements Event {
        public String getConversationHistoryString() {
            return String.format("[CommandInfo]: %s", message);
        }

        public String toString() {
            return String.format("CommandInfoMessage(commandName='%s', message='%s')", commandName, message);
        }
    }

    public record InfoMessage(String message) implements Event {
        public String getConversationHistoryString() {
            return String.format("[Info]: %s", message);
        }

        public String toString() {
            return String.format("InfoMessage(message='%s')", message);
        }
    }

    public record CharacterMessage(String message, String command, AgentConversationData sendingCharacterData)
            implements Event {
        public String getConversationHistoryString() {
            return String.format("Other AI Message: [%s]: %s", sendingCharacterData.getName(), message);
        }

        public String toString() {
            return String.format("CharacterMessage(name='%s', message='%s', command='%s')",
                    sendingCharacterData.getName(), message, command);
        }

    }
}
