package altoclef.player2api;

import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="automatone")
public class ForgeEventHandler {
    @SubscribeEvent
    public static void onChatMessage(ServerChatEvent evt){
        EventQueueManager.onUserChatMessage(new Event.UserMessage(evt.getMessage().getString(), evt.getUsername()));
    }
}
