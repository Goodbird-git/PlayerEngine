package com.player2.playerengine.player2api.auth;

import com.player2.playerengine.automaton.utils.DirUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TokenStorage {
    private static final Path PATH = DirUtil.getGameDir().resolve("playerengine_token_storage.dat");
    private static final TokenStorage INSTANCE = new TokenStorage();
    private CompoundTag tokensStored = new CompoundTag();

    private TokenStorage() {
        load();
    }

    private String makeKey(String username, String clientId) {
        return username + ":" + clientId;
    }

    public static String getToken(String username, String clientId) {
        return getInstance().tokensStored.getString(getInstance().makeKey(username, clientId));
    }

    static void storeToken(String username, String clientId, String token) {
        System.out.println("[TokenStorage]: Storing token for player " + username + " and client " + clientId);
        getInstance().tokensStored.putString(getInstance().makeKey(username, clientId), token);
        getInstance().save();
    }

    public static void clearAllTokens() {
        System.out.println("[TokenStorage]: Clearing all tokens to force reauth");
        getInstance().tokensStored = new CompoundTag();
        getInstance().save();
    }

    private void load() {
        if (Files.exists(PATH)) {
            try {
                tokensStored = NbtIo.readCompressed(PATH.toFile().toPath(), NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void save() {
        System.out.println("[TokenStorage]: save() called");
        try {
            NbtIo.writeCompressed(tokensStored, PATH.toFile().toPath());
            System.out.println("[TokenStorage]: Writing to file...");
        } catch (IOException e) {
            System.err.println("[TokenStorage]: Writing to file FAILED");
            e.printStackTrace();
        }
    }

    private static TokenStorage getInstance() {
        return INSTANCE;
    }
}