package com.player2.playerengine.player2api.auth;

import com.google.gson.JsonObject;
import com.player2.playerengine.player2api.utils.HTTPUtils;
import com.player2.playerengine.player2api.utils.HttpApiException;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.*;

public class AuthenticationManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String WEB_API_URL = "https://api.player2.game";
    private static final String LOCAL_API_URL = "http://127.0.0.1:4315";

    private static final AuthenticationManager INSTANCE = new AuthenticationManager();

    private static final ExecutorService authExecutor = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService pollingExecutor = Executors.newSingleThreadScheduledExecutor();

    private final Map<AuthKey, CompletableFuture<String>> ongoingAuths = new ConcurrentHashMap<>();
    private final Map<AuthKey, ScheduledFuture<?>> pollingTasks = new ConcurrentHashMap<>();

    public static AuthenticationManager getInstance() {
        return INSTANCE;
    }

    public static ExecutorService getExecutor() {
        return authExecutor;
    }

    public static ExecutorService getPollingExecutor() {
        return pollingExecutor;
    }


    public void invalidateToken(Player player, String clientId) {
        AuthKey authKey = new AuthKey(player.getUUID(), clientId);
        LOGGER.info("Invalidating token for {}", authKey);

        TokenStorage.storeToken(player.getName().getString(), clientId, "");
        authenticate(player, clientId);
    }

    public void checkAuth(Player player, String clientId) {
        AuthKey authKey = new AuthKey(player.getUUID(), clientId);
        String username = player.getName().getString();
        try {
            LOGGER.info("Attempting local login check for {}", authKey);
            Map<String, com.google.gson.JsonElement> response = HTTPUtils.sendRequest(LOCAL_API_URL, "/v1/login/web/" + clientId, true, new JsonObject(), null);
            String p2Key = response.get("p2Key").getAsString();
            if (p2Key != null) {
                LOGGER.info("Detected relogin for {}", authKey);
                if (TokenStorage.getToken(username, clientId).isEmpty()) {
                    player.sendSystemMessage(Component.literal("Authentication for mod '" + clientId + "' successful!"));
                }
//                    else {
//                        player.sendSystemMessage(Component.literal("Reauthentication for mod '" + clientId + "' successful!"));
//                    }
                TokenStorage.storeToken(username, clientId, p2Key);
            }
        } catch (Exception e) {
            LOGGER.warn("Local login check for {} failed. Error: {}", authKey, e.getMessage());
        }
    }

    public CompletableFuture<String> authenticate(Player player, String clientId) {
        AuthKey authKey = new AuthKey(player.getUUID(), clientId);
        String username = player.getName().getString();

        if (ongoingAuths.containsKey(authKey)) {
            return ongoingAuths.get(authKey);
        }

        String storedToken = TokenStorage.getToken(username, clientId);
        if (!storedToken.isEmpty()) {
            LOGGER.info("Found stored token for {}", authKey);
            return CompletableFuture.completedFuture(storedToken);
        }

        CompletableFuture<String> authFuture = new CompletableFuture<>();
        ongoingAuths.put(authKey, authFuture);

        authExecutor.submit(() -> {
            try {
                try {
                    LOGGER.info("Attempting local login for {}", authKey);
                    Map<String, com.google.gson.JsonElement> response = HTTPUtils.sendRequest(LOCAL_API_URL, "/v1/login/web/" + clientId, true, new JsonObject(), null);
                    String p2Key = response.get("p2Key").getAsString();
                    if (p2Key != null) {
                        LOGGER.info("Local login successful for {}", authKey);
                        completeAuth(player, clientId, p2Key, authFuture);
                        return;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Local login for {} failed, proceeding to web auth. Error: {}", authKey, e.getMessage());
                }

                LOGGER.info("Starting web device flow for {}", authKey);
                JsonObject deviceCodeRequestBody = new JsonObject();
                deviceCodeRequestBody.addProperty("client_id", clientId);

                Map<String, com.google.gson.JsonElement> deviceCodeResponse = HTTPUtils.sendRequest(WEB_API_URL, "/v1/login/device/new", true, deviceCodeRequestBody, null);

                String deviceCode = deviceCodeResponse.get("deviceCode").getAsString();
                String verificationUriComplete = deviceCodeResponse.get("verificationUriComplete").getAsString();
                int interval = deviceCodeResponse.get("interval").getAsInt();

                player.sendSystemMessage(Component.literal(String.format("To use AI features from mod '%s', please authorize here: %s", clientId, verificationUriComplete)).withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, verificationUriComplete))));

                startPolling(player, clientId, deviceCode, interval, authFuture);

            } catch (Exception e) {
                LOGGER.error("Authentication failed for {}", authKey, e);
                player.sendSystemMessage(Component.literal("Authentication process for mod '" + clientId + "' failed."));
                authFuture.completeExceptionally(e);
                ongoingAuths.remove(authKey);
            }
        });

        return authFuture;
    }

    private void startPolling(Player player, String clientId, String deviceCode, int interval, CompletableFuture<String> authFuture) {
        AuthKey authKey = new AuthKey(player.getUUID(), clientId);

        ScheduledFuture<?> pollingTask = pollingExecutor.scheduleAtFixedRate(() -> {
            try {
                JsonObject tokenRequestBody = new JsonObject();
                tokenRequestBody.addProperty("client_id", clientId);
                tokenRequestBody.addProperty("device_code", deviceCode);
                tokenRequestBody.addProperty("grant_type", "urn:ietf:params:oauth:grant-type:device_code");

                Map<String, com.google.gson.JsonElement> response = HTTPUtils.sendRequest(WEB_API_URL, "/v1/login/device/token", true, tokenRequestBody, null);

                String p2Key = response.get("p2Key").getAsString();
                if (p2Key != null) {
                    LOGGER.info("Device flow polling successful for {}", authKey);
                    completeAuth(player, clientId, p2Key, authFuture);
                }
            } catch (HttpApiException e) {
                if (!e.getMessage().contains("authorization_pending")) {
                    LOGGER.error("Error during polling for {}", authKey, e);
                    authFuture.completeExceptionally(e);
                    stopPolling(authKey);
                }
            } catch (Exception e) {
                LOGGER.error("Unexpected error during polling for {}", authKey, e);
                authFuture.completeExceptionally(e);
                stopPolling(authKey);
            }
        }, 0, interval, TimeUnit.SECONDS);

        pollingTasks.put(authKey, pollingTask);
    }

    private void completeAuth(Player player, String clientId, String token, CompletableFuture<String> authFuture) {
        AuthKey authKey = new AuthKey(player.getUUID(), clientId);
        String username = player.getName().getString();

        TokenStorage.storeToken(username, clientId, token);
        player.sendSystemMessage(Component.literal("Authentication for mod '" + clientId + "' successful!"));
        authFuture.complete(token);
        stopPolling(authKey);
        ongoingAuths.remove(authKey);
    }

    private void stopPolling(AuthKey authKey) {
        ScheduledFuture<?> task = pollingTasks.remove(authKey);
        if (task != null) {
            task.cancel(true);
        }
    }
}