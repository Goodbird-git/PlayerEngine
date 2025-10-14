package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.cache.IWorldData;
import net.minecraft.world.phys.Vec3;

public class CacheTracker {
    PlayerEngineController mod;
    private long lastProcTime = 0;
    private final long updateTime = 1_000_000_000L; // 1 sec

    public CacheTracker(PlayerEngineController mod) {
        this.mod = mod;
    }

    private void addCurrentPosToCache() {
        IWorldData worldData = mod.getBaritone().getWorldProvider().getCurrentWorld();
        Vec3 curPos = mod.getPlayer().position();
        int x = (int) curPos.x();
        int z = (int) curPos.z();
        worldData.addBlockPosToCache(x, z);
    }

    public void tick() {
        long curTime = System.nanoTime();
        if (curTime < lastProcTime + updateTime) {
            return;
        }
        lastProcTime = System.nanoTime();
        addCurrentPosToCache();
    }

}
