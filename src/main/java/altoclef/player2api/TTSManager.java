package altoclef.player2api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.server.MinecraftServer;

public class TTSManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TTScharactersPerSecond = 25; // approx how fast (characters/sec) does the TTS talk
    private static boolean TTSLocked = false;
    private static long estimatedEndTime = 0;
    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor();
    private static MinecraftServer server = null;

    private static void setEstimatedEndTime(String message) {
        server.execute(() -> {
            int waitTimeSec = (int) Math.ceil(message.length() / (double) TTScharactersPerSecond) + 1;
            
            LOGGER.info("TTSManager/ waiting time={} (sec) for message={}", waitTimeSec, message);

            long waitNanos = TimeUnit.SECONDS.toNanos(waitTimeSec); 
            estimatedEndTime = System.nanoTime() +  waitNanos;
        });
    }

    public static void TTS(String message, Character character, Player2APIService player2apiService) {
        server.execute(() -> {
            TTSLocked = true;
            LOGGER.info("Locking TTS based on msg={}", message);
            estimatedEndTime = Long.MAX_VALUE;
            
            ttsThread.submit(() -> {
                player2apiService.textToSpeech(message, character, (_unusedMap) -> {
                    setEstimatedEndTime(message);
                });
            });
        });
    }

    public static boolean isLocked() {
        return TTSLocked;
    }

    public static void injectOnTick(MinecraftServer server) {
        // release lock if we think we have finished.
        if(TTSManager.server == null){
            TTSManager.server = server;
        }
        server.execute(() -> {
            if ((System.nanoTime() > estimatedEndTime) && TTSLocked) {
                LOGGER.info("TTS releasing lock");
                TTSLocked = false;
            }
        });
    }
}