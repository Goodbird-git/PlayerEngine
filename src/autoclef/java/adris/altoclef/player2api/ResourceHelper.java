package adris.altoclef.player2api;

import org.intellij.lang.annotations.Identifier;

import net.minecraft.resources.ResourceLocation;

public class ResourceHelper {
    public static final String MOD_ID = "playerengine";
    public static ResourceLocation getResourceLocation(String id) {
        return new ResourceLocation(MOD_ID, id);
    }

    
}
