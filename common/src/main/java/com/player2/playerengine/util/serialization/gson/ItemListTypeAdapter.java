package com.player2.playerengine.util.serialization.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ItemListTypeAdapter extends TypeAdapter<List<Item>> {

    @Override
    public void write(JsonWriter out, List<Item> items) throws IOException {
        if (items == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (Item item : items) {
            String key = ItemHelper.trimItemName(item.getDescriptionId());
            out.value(key);
        }
        out.endArray();
    }

    @Override
    public List<Item> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        List<Item> result = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            String itemKey = in.nextString();
            itemKey = ItemHelper.trimItemName(itemKey);
            ResourceLocation identifier = ResourceLocation.tryParse(itemKey);
            if (BuiltInRegistries.ITEM.containsKey(identifier)) {
                result.add(BuiltInRegistries.ITEM.get(identifier).get().value());
            } else {
                Debug.logWarning("Invalid item name: " + itemKey);
            }
        }
        in.endArray();
        return result;
    }
}