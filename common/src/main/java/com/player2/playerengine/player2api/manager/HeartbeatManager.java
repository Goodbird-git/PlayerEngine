package com.player2.playerengine.player2api.manager;

import net.minecraft.nbt.CompoundTag;

public class HeartbeatManager {
    private static final HeartbeatManager INSTANCE = new HeartbeatManager();
    private CompoundTag tokensStored = new CompoundTag();

    private String makeKey(String username, String clientId) {
        return username + ":" + clientId;
    }

    public static boolean shouldHeartbeat(String username, String clientId){
        long now = System.nanoTime();
        return now - getLastTime(username, clientId) > 60_000_000_000L;
    }

    static long getLastTime(String username, String clientId) {
        return getInstance().tokensStored.getLong(getInstance().makeKey(username, clientId)).get();
    }

    public static void storeHeartbeatTime(String username, String clientId) {
        getInstance().tokensStored.putLong(getInstance().makeKey(username, clientId), System.nanoTime());
    }

    private static HeartbeatManager getInstance() {
        return INSTANCE;
    }
}