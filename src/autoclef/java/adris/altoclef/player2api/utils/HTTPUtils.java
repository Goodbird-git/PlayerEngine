package adris.altoclef.player2api.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

public class HTTPUtils {
   private static final String BASE_URL = "http://127.0.0.1:4315";

   public static Map<String, JsonElement> sendRequest(String endpoint, boolean postRequest, JsonObject requestBody,
         @Nullable Map<String, String> extraHeaders)
         throws Exception {
      URL url = new URI(BASE_URL + endpoint).toURL();
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod(postRequest ? "POST" : "GET");
      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      connection.setRequestProperty("Accept", "application/json; charset=utf-8");
      if (extraHeaders != null) {
         for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
         }
      }

      if (postRequest && requestBody != null) {
         connection.setDoOutput(true);

         try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
         } catch (Throwable v) {
         }
      }

      JsonObject jsonResponse = getJsonObject(connection);
      Map<String, JsonElement> responseMap = new HashMap<>();

      for (Entry<String, JsonElement> entry : jsonResponse.entrySet()) {
         responseMap.put(entry.getKey(), entry.getValue());
      }

      return responseMap;
   }

   private static JsonObject getJsonObject(HttpURLConnection connection) throws IOException {
      int responseCode = connection.getResponseCode();
      if (responseCode != 200) {
         throw new IOException("HTTP " + responseCode + ": " + connection.getResponseMessage());
      } else {
         BufferedReader reader = new BufferedReader(
               new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
         StringBuilder response = new StringBuilder();

         String line;
         while ((line = reader.readLine()) != null) {
            response.append(line);
         }

         reader.close();
         return JsonParser.parseString(response.toString()).getAsJsonObject();
      }
   }
}
