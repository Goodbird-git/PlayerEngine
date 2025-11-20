package com.player2.playerengine;

import com.player2.playerengine.player2api.utils.AudioUtils;
import com.player2.playerengine.automaton.KeepName;
import com.player2.playerengine.automaton.client.CustomFishingBobberRenderer;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

@KeepName
public final class PlayerEngineClient {
   public static boolean enabledTTS = true;
   public static void onInitializeClient() {
      EntityRendererRegistry.register(PlayerEngine.FISHING_BOBBER, CustomFishingBobberRenderer::new);

      NetworkManager.registerReceiver(NetworkManager.Side.S2C, new ResourceLocation("playerengine", "stream_tts"), (buf, context) -> {
         String clientId = buf.readUtf();
         String token = buf.readUtf();
         String text = buf.readUtf();
         double speed = buf.readDouble();
         int voiceIdCount = buf.readVarInt();
         String[] voiceIds = new String[voiceIdCount];
         for (int i = 0; i < voiceIdCount; i++) {
            voiceIds[i] = buf.readUtf();
         }

         CompletableFuture.runAsync(() -> {
            AudioUtils.streamAudio(clientId, token, text, speed, voiceIds);
         });
      });
   }
}
