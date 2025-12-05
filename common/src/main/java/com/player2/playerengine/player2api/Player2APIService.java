package com.player2.playerengine.player2api;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.manager.HeartbeatManager;
import com.player2.playerengine.player2api.utils.Player2HTTPUtils;
import com.player2.playerengine.player2api.utils.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Player2APIService {
   private static final Logger LOGGER = LogManager.getLogger();

   private String clientId;
   private PlayerEngineController controller;

   private static MinecraftServer server;

   public String getClientId() {
      return clientId;
   }

   public Player2APIService(PlayerEngineController controller, String clientId) {
      this.clientId = clientId;
      this.controller = controller;
   }

   public JsonObject completeConversation(ConversationHistory conversationHistory) throws Exception {
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSON()) {
         messagesArray.add(msg);
      }
      String lastMessageForDebug = conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1)
            .toString();

      requestBody.add("messages", messagesArray);
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
            "/v1/chat/completions", true, requestBody);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               String content = messageObject.get("content").getAsString();
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
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
      String lastMessageForDebug = conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1)
            .toString();
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
            "/v1/chat/completions", true, requestBody);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
               return messageObject.get("content").getAsString();
            }
         }
      }

      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   public void textToSpeech(String message, Character character, Consumer<Map<String, JsonElement>> onFinish) {
      try {
         RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
               controller.getOwner().registryAccess());

         buf.writeUtf(clientId);
         buf.writeUtf(Player2HTTPUtils.awaitToken(controller.getOwner(), clientId));
         buf.writeUtf(message);
         buf.writeDouble(1);
         buf.writeVarInt(character.voiceIds().length);
         for (String id : character.voiceIds()) {
            buf.writeUtf(id);
         }

         ((ServerPlayer) controller.getOwner()).connection.send(NetworkManager.toPacket(NetworkManager.Side.S2C,
               ResourceLocation.fromNamespaceAndPath("playerengine", "stream_tts"), buf));
         onFinish.accept(null);
      } catch (Exception var9) {
      }
   }

   // public void textToSpeech(String message, Character character,
   // Consumer<Map<String, JsonElement>> onFinish) {
   // try {
   // JsonObject requestBody = new JsonObject();
   // requestBody.addProperty("speed", 1);
   // requestBody.addProperty("text", message);
   // requestBody.addProperty("audio_format", "mp3");
   // JsonArray voiceIdsArray = new JsonArray();
   //
   // for (String voiceId : character.voiceIds()) {
   // voiceIdsArray.add(voiceId);
   // }
   //
   // requestBody.add("voice_ids", voiceIdsArray);
   // LOGGER.info("TTS request w/ msg={}", message);
   // Map<String, JsonElement> responseMap =
   // Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,"/v1/tts/speak",
   // true, requestBody);
   // onFinish.accept(responseMap);
   // } catch (Exception var9) {
   // }
   // }

   public void startSTT() {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("timeout", 180);

      try {
         Player2HTTPUtils.sendRequest(controller.getOwner(), clientId, "/v1/stt/start", true, requestBody);
      } catch (Exception var3) {
         System.err.println("[Player2APIService/startSTT]: Error" + var3.getMessage());
      }
   }

   public String stopSTT() {
      try {
         Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
               "/v1/stt/stop", true, null);
         if (!responseMap.containsKey("text")) {
            throw new Exception("Could not find key 'text' in response");
         } else {
            return responseMap.get("text").getAsString();
         }
      } catch (Exception var2) {
         return var2.getMessage();
      }
   }

   public void trySendHeartbeat() {
      if (HeartbeatManager.shouldHeartbeat(controller.getOwnerUsername(), clientId)) {
         sendHeartbeat();
         HeartbeatManager.storeHeartbeatTime(controller.getOwnerUsername(), clientId);
      }
   }

   public void sendHeartbeat() {
      try {
         System.out.println("Sending Heartbeat " + clientId);
         Player2HTTPUtils.sendRequest(controller.getOwner(), clientId, "/v1/health", false, null);
         System.out.println("Heartbeat Successful");
      } catch (Exception var2) {
         System.err.printf("Heartbeat Fail: %s", var2.getMessage());
      }
   }

   /**
    * Search for schematics given a query
    * 
    * @return List of schematics matching the query
    */
   public List<JsonObject> searchSchematics(String query) {
      try {
         JsonObject requestBody = new JsonObject();
         requestBody.addProperty("query", query);
         requestBody.addProperty("max_results", 10);

         Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId, "/v1/minecraft/schematics/search", true, requestBody);

         JsonElement resultsJsonElement = responseMap.get("result");
         if (resultsJsonElement != null && resultsJsonElement.isJsonArray()) {
            JsonArray resultsJsonArray = resultsJsonElement.getAsJsonArray();

            List<JsonObject> schematics = new ArrayList<>();
            for (JsonElement voiceElement : resultsJsonArray) {
               JsonObject voiceObject = voiceElement.getAsJsonObject();
               schematics.add(voiceObject);
            }
            return schematics;
         } else {
            System.err.println(
                  "No result field array in response with keys: [" + String.join(",", responseMap.keySet()) + "]");
         }
      } catch (Exception e) {
         System.err.println("Search schematics request failed: " + e.getMessage());
      }
      return Collections.emptyList();
   }

   /**
    * Get schematic binary given a schematic ID
    * 
    * @return Schematic binary data
    */
   public String getSchematicBinary(String schematicId) {
      try {
         Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId, "/v1/schematics/" + schematicId, false, null);
         JsonElement dataJsonElement = responseMap.get("data");
         if (dataJsonElement != null && dataJsonElement.isJsonPrimitive()) {
            return dataJsonElement.getAsString();
         } else {
            System.err.println(
                  "No data field primitive in response with keys: [" + String.join(",", responseMap.keySet()) + "]");
         }
      } catch (Exception e) {
         System.err.println("Get schematic binary request failed: " + e.getMessage());
      }
      return "";
   }
}