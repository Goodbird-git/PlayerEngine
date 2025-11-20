package com.player2.playerengine.player2api.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.client.Minecraft;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class STTUtils {
    public static final Logger LOGGER = LogManager.getLogger();

    private static final ExecutorService sttThread = Executors.newSingleThreadExecutor();

    // state:
    public static volatile boolean isListening = false;
    private static volatile boolean recordingThreadRunning = false;
    private static TargetDataLine line;
    public static String clientId;
    private static ByteArrayOutputStream buffer;

    // audio format:
    private static final float SAMPLE_RATE = 16000f;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true,
            false);
    private static final int BYTES_PER_FRAME = AUDIO_FORMAT.getFrameSize(); // (2 for 16 bit mono)

    // queue for outgoing wav audio blobs
    private static final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();

    // current token used for sending; guarded by sttThread tasks (single-threaded)
    private static volatile String currentToken = null;

    // HTTP client shared
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // API base — change to actual host
    private static final String API_BASE = "https://api.player2.game/v1/stt/audio";

    public static void update() {
    }

    // called on mod init
    public static void onInitialize() {
        try {
            line = AudioSystem.getTargetDataLine(AUDIO_FORMAT);
            line.open(AUDIO_FORMAT);
            LOGGER.info("Audio line opened.");
        } catch (Exception e) {
            LOGGER.error("Failed to open audio line", e);
        }
    }

    // called externally when STT is active (i.e. keybind):
    public static void setIsListening(boolean v, String clientId) {
        isListening = v;
        STTUtils.clientId = clientId;
        if (isListening) {
            startRecording();
            initToken();
        } else {
            // if you want to immediately stop and flush, you can call connect(...)
            // externally
            // or rely on your other code to call connect when a token is available.
        }
    }

    private static synchronized void startRecording() {
        if (recordingThreadRunning) {
            return;
        }
        if (line == null) {
            LOGGER.error("Audio line not initialized. Call onInitialize() first.");
            return;
        }
        recordingThreadRunning = true;
        sttThread.execute(() -> {
            line.start();
            buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            while (isListening && line != null) {
                int read = line.read(chunk, 0, chunk.length);
                if (read > 0) {
                    LOGGER.info("STT-read {}", read);
                    buffer.write(chunk, 0, read);
                }
            }

            try {
                line.stop();
            } catch (Exception e) {
                LOGGER.info("STT: failed to stop line");
            }

            try {
                // Convert the raw PCM bytes in buffer to a WAV byte[] (in-memory)
                byte[] pcm = buffer.toByteArray();

                ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
                AudioInputStream ais = new AudioInputStream(bais, AUDIO_FORMAT, pcm.length / BYTES_PER_FRAME);

                // Write WAV to a ByteArrayOutputStream so we can post the bytes directly
                ByteArrayOutputStream wavOutBaos = new ByteArrayOutputStream();
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavOutBaos);
                byte[] wavBytes = wavOutBaos.toByteArray();

                // OPTIONAL: still write local file for debugging (comment out if not needed)
                try {
                    File wavFile = new File("recorded.wav");
                    try (FileOutputStream fos = new FileOutputStream(wavFile)) {
                        fos.write(wavBytes);
                    }
                    LOGGER.info("WAV saved at: " + wavFile.getAbsolutePath());
                } catch (Exception e) {
                    LOGGER.warn("Failed to write local WAV file (non-fatal): {}", e.toString());
                }

                // Instead of sending immediately (we might not have a token now),
                // enqueue WAV bytes. The sendToApi method will enqueue if token==null.
                sendToApi(wavBytes, null);
            } catch (Exception e) {
                LOGGER.error("STT: Failed creating wav bytes", e);
            } finally {
                LOGGER.info("STT: Recording thread exiting");
                recordingThreadRunning = false;
            }
        });
    }

    public static void shutdown() {
        isListening = false;
        try {
            if (line != null) {
                line.stop();
                line.close();
            }
        } catch (Exception ignored) {
        }
        sttThread.shutdownNow();
        LOGGER.info("STTUtils shutdown complete.");
    }

    /**
     * Called externally when you have a token and want to connect/send queued
     * audio.
     * This will set the current token and attempt to flush the queue immediately
     * (done on the sttThread).
     */
    public static void connect(String token) {
        if (token == null) {
            LOGGER.warn("connect called with null token; ignoring.");
            return;
        }
        // set token and flush queue on sttThread to avoid races
        sttThread.execute(() -> {
            currentToken = token;
            flushQueue();
        });
    }

    /**
     * Enqueue or send WAV bytes to API.
     *
     * Behavior:
     * - If token is null -> enqueue the wav bytes for later sending (by connect()).
     * - If token is provided -> attempt to send immediately (and also flush any
     * queued items first).
     *
     * Note: This method will run sending work on sttThread to keep ordering and
     * avoid concurrency issues.
     */
    public static void sendToApi(byte[] wavAudio, String token) {
        if (wavAudio == null || wavAudio.length == 0) {
            LOGGER.warn("sendToApi called with empty wavAudio");
            return;
        }

        // If caller provided a token, we will send immediately (but do all work on
        // sttThread).
        if (token != null) {
            sttThread.execute(() -> {
                // adopt token as currentToken for this session
                currentToken = token;
                // make sure queued items (if any) are sent before this one to preserve order
                flushQueue();
                Optional<String> result = doHttpPost(wavAudio, currentToken);
                if (result.isEmpty()) {
                    // re-enqueue for retry later
                    boolean offered = audioQueue.offer(wavAudio);
                    LOGGER.warn("Failed to send immediate wav; requeued: {}", offered);
                } else {
                    String msg = result.get();
                    if (!msg.isBlank()) {
                        onSTTMessageGenerated(msg);
                    }
                }
            });
            return;
        }

        // No token provided -> just queue for later
        boolean offered = audioQueue.offer(wavAudio);
        if (!offered) {
            LOGGER.error("Failed to enqueue wavAudio (queue.offer returned false)");
        } else {
            LOGGER.info("Enqueued wavAudio for later sending. queueSize={}", audioQueue.size());
        }
    }

    /**
     * Flush queue (send all queued items). Runs on sttThread.
     */
    private static void flushQueue() {
        if (currentToken == null) {
            LOGGER.info("No token available; will not flush queue.");
            return;
        }
        LOGGER.info("Flushing audio queue (size={})", audioQueue.size());
        byte[] item;
        while ((item = audioQueue.poll()) != null) {
            Optional<String> result = doHttpPost(item, currentToken);
            if (result.isEmpty()) {
                // if send failed, try to requeue (to the head would be ideal, but
                // LinkedBlockingQueue doesn't have addFirst)
                // we'll re-offer and stop processing to avoid spinning and losing ordering
                boolean requeued = audioQueue.offer(item);
                LOGGER.warn("Failed to send queued audio; requeued: {}", requeued);
                break; // stop flushing now, try later when connect() called again
            } else {
                String msg = result.get();
                if (!msg.isBlank()) {
                    onSTTMessageGenerated(msg);
                }
            }
        }
    }

    /**
     * Performs the actual HTTP POST to the API. Returns true if a successful 2xx
     * response was received.
     * This is synchronous and must be called from sttThread to avoid blocking other
     * threads.
     */
    private static Optional<String> doHttpPost(byte[] wavBytes, String token) {
        try {
            String uri = API_BASE + "?encoding=linear16&sample_rate=16000";
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(wavBytes));

            if (token != null && !token.isEmpty()) {
                b.header("Authorization", "Bearer " + token);
            }

            HttpRequest req = b.build();
            LOGGER.info("Sending {} bytes to STT API...", wavBytes.length);

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();

            LOGGER.info("STT API response: {} body-size={} (body first 200 chars: {})",
                    code,
                    resp.body() == null ? 0 : resp.body().length(),
                    resp.body() == null ? "" : resp.body().substring(0, Math.min(200, resp.body().length())));

            // 2xx = OK
            if (code >= 200 && code < 300 && resp.body() != null) {
                // Extract transcript field from JSON
                String transcript = extractTranscript(resp.body());
                return Optional.ofNullable(transcript);
            }

            LOGGER.warn("Non-2xx response from STT API: {}", code);
            return Optional.empty();

        } catch (HttpTimeoutException e) {
            LOGGER.warn("Timeout sending to STT API: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Exception sending to STT API", e);
            return Optional.empty();
        }
    }

    private static void initToken() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                Minecraft.getInstance().player.registryAccess());
        buf.writeUtf(clientId);
        Minecraft.getInstance().getConnection().send(
                NetworkManager.toPacket(
                        NetworkManager.Side.C2S,
                        ResourceLocation.fromNamespaceAndPath("playerengine", "request_stt"),
                        buf));
    }

    private static void onSTTMessageGenerated(String message) {

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                Minecraft.getInstance().player.registryAccess());
        buf.writeUtf(message);
        Minecraft.getInstance().getConnection().send(
                NetworkManager.toPacket(
                        NetworkManager.Side.C2S,
                        ResourceLocation.fromNamespaceAndPath("playerengine", "user_message"),
                        buf));
    }

    private static String extractTranscript(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("transcript")) {
                return obj.get("transcript").getAsString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}