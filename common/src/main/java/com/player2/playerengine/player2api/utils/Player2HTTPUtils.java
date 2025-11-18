package com.player2.playerengine.player2api.utils;

import com.player2.playerengine.player2api.auth.AuthKey;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class Player2HTTPUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String WEB_API_URL = "https://api.player2.game";

    public static Map<String, JsonElement> sendRequest(Player player, String clientId, String endpoint,
            boolean postRequest, JsonObject requestBody) throws Exception {
        String token = awaitToken(player, clientId);
        Map<String, String> headers = getHeaders(clientId, token);

        try {
            return HTTPUtils.sendRequest(WEB_API_URL, endpoint, postRequest, requestBody, headers);
        } catch (HttpApiException e) {
            if (e.getStatusCode() == 401) {
                LOGGER.warn("Received 401 Unauthorized for {}. Invalidating token.",
                        new AuthKey(player.getUUID(), clientId));
                AuthenticationManager.getInstance().invalidateToken(player, clientId);
                throw new Exception("Token expired, re-authentication started.", e);
            } else if (e.getStatusCode() == 402) {
                LOGGER.warn("Insufficient AI power for user {}", player.getName().getString());
                throw new Exception("Insufficient AI Power. Go to player2.game to get more AI Power.");
            }
            throw e;
        }
    }

    private static Map<String, String> getHeaders(String clientId, String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("player2-game-key", clientId);
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    public static String awaitToken(Player player, String clientId) throws ExecutionException, InterruptedException {
        return AuthenticationManager.getInstance().authenticate(player, clientId).get();
    }
}
