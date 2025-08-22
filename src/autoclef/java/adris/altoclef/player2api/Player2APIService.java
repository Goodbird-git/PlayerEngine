package adris.altoclef.player2api;

import adris.altoclef.player2api.utils.CharacterUtils;
import adris.altoclef.player2api.utils.HTTPUtils;
import adris.altoclef.player2api.utils.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import java.net.HttpURLConnection;

public class Player2APIService {

   private String player2GameID;

   public Player2APIService(String player2GameID){
      this.player2GameID = player2GameID;
   }
   private Map<String, JsonElement> sendRequest(String endpoint, boolean postRequest, JsonObject requestBody) throws Exception{
      Map<String, String> headers = getHeaders(player2GameID);
      return HTTPUtils.sendRequest(endpoint, postRequest, requestBody, headers);
   }
   public JsonObject completeConversation(ConversationHistory conversationHistory) throws Exception {
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSON()) {
         messagesArray.add(msg);
      }

      requestBody.add("messages", messagesArray);
      Map<String, JsonElement> responseMap = sendRequest("/v1/chat/completions", true, requestBody);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               String content = messageObject.get("content").getAsString();
               return Utils.parseCleanedJson(content);
            }
         }
      }

      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   public String completeConversationToString(ConversationHistory conversationHistory) throws Exception {
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSON()) {
         messagesArray.add(msg);
      }

      requestBody.add("messages", messagesArray);
      Map<String, JsonElement> responseMap = sendRequest("/v1/chat/completions", true, requestBody);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               return messageObject.get("content").getAsString();
            }
         }
      }

      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   public Character getSelectedCharacter() {
      try {
         Map<String, JsonElement> responseMap = sendRequest(
               "/v1/selected_characters", false, null);
         return CharacterUtils.parseFirstCharacter(responseMap);
      } catch (Exception var2) {
         return CharacterUtils.DEFAULT_CHARACTER;
      }
   }

   public void textToSpeech(String message, Character character, Consumer<Map<String, JsonElement>> onFinish) {
      try {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("play_in_app", true);
         requestBody.addProperty("speed", 1);
         requestBody.addProperty("text", message);
         requestBody.addProperty("play_in_app", false);
         JsonArray voiceIdsArray = new JsonArray();

         for (String voiceId : character.voiceIds()) {
            voiceIdsArray.add(voiceId);
         }

         requestBody.add("voice_ids", voiceIdsArray);
         System.out.println("Sending TTS request: " + message);
         Map<String, JsonElement> responseMap = sendRequest("/v1/tts/speak", true, requestBody);
         onFinish.accept(responseMap);
      } catch (Exception var9) {
      }
   }

   public void startSTT() {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("timeout", 30);

      try {
         sendRequest("/v1/stt/start", true, requestBody);
      } catch (Exception var3) {
         System.err.println("[Player2APIService/startSTT]: Error" + var3.getMessage());
      }
   }

   public String stopSTT() {
      try {
         Map<String, JsonElement> responseMap = sendRequest("/v1/stt/stop", true, null);
         if (!responseMap.containsKey("text")) {
            throw new Exception("Could not find key 'text' in response");
         } else {
            return responseMap.get("text").getAsString();
         }
      } catch (Exception var2) {
         return var2.getMessage();
      }
   }

   public void sendHeartbeat() {
      try {
         System.out.println("Sending Heartbeat " + player2GameID);
         Map<String, JsonElement> responseMap = sendRequest("/v1/health", false, null);
         if (responseMap.containsKey("client_version")) {
            System.out.println("Heartbeat Successful");
         }
      } catch (Exception var2) {
         System.err.printf("Heartbeat Fail: %s", var2.getMessage());
      }
   }

   public void player2ProcessConnection(HttpURLConnection connection) {
      if (player2GameID != null) {
         connection.setRequestProperty("player2-game-key", player2GameID);
      }
   }

   public static Map<String, String> getHeaders(String player2Apikey){
      Map<String, String> headers = new HashMap<>();
      headers.put("player2-game-key", player2Apikey);
      return headers;
   }
}