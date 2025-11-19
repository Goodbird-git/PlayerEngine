package com.player2.playerengine.player2api.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AudioUtils {
    private static final String WEB_API_URL = "https://api.player2.game";

    public static void streamAudio(String clientId, String token, String text, double speed, String[] voiceIds) {
        HttpURLConnection connection = null;
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("text", text);
            requestBody.addProperty("speed", speed);
            requestBody.addProperty("audio_format", "wav");
            JsonArray voiceIdsArray = new JsonArray();
            for (String id : voiceIds) {
                voiceIdsArray.add(id);
            }
            requestBody.add("voice_ids", voiceIdsArray);

            URL url = new URL(WEB_API_URL + "/v1/tts/stream");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "audio/wav");

            connection.setRequestProperty("player2-game-key", clientId);
            connection.setRequestProperty("Authorization", "Bearer " + token);

            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try (InputStream inputStream = connection.getInputStream();
                    AudioInputStream audioStream = AudioSystem
                            .getAudioInputStream(new BufferedInputStream(inputStream))) {

                AudioFormat format = audioStream.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                try (SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(info)) {
                    sourceDataLine.open(format);
                    sourceDataLine.start();

                    byte[] buffer = new byte[4096];
                    int bytesRead = 0;
                    while ((bytesRead = audioStream.read(buffer)) != -1) {
                        sourceDataLine.write(buffer, 0, bytesRead);
                    }

                    sourceDataLine.drain();
                    sourceDataLine.stop();
                }
            }
        } catch (Exception e) {
            System.err.println("Error during TTS streaming: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
