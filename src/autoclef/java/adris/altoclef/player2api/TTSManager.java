package adris.altoclef.player2api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class TTSManager {
    private static final Logger LOGGER = LogManager.getLogger(); 
    private static final int TTScharactersPerSecond = 25; // approx how fast (characters/sec) does the TTS talk
    private static boolean TTSLocked = false;
    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor();
    private static long estimatedEndTime = 0;

    private static void setEstimatedEndTime(String message) {
        int waitTimeSec = (message.length() / TTScharactersPerSecond) + 1; // 1 second for buffer
        LOGGER.info("TTSManager/ waiting time={} (sec) for message={}", waitTimeSec, message);
        estimatedEndTime = System.nanoTime() + waitTimeSec * 1_000_000_000;
    }

    public static void TTS(String message, Character character, Player2APIService player2apiService) {
        TTSLocked = true;
        ttsThread.submit(() -> {
            player2apiService.textToSpeech(message, character, (_unusedMap)-> {
                setEstimatedEndTime(message);
            } );
        });
    }

    public static boolean isLocked(){
        return TTSLocked;
    }

    public static void injectOnTick() {
        // release lock if we think we have finished.
        if(System.nanoTime() > estimatedEndTime){
            TTSLocked = false;
        }
    }
}