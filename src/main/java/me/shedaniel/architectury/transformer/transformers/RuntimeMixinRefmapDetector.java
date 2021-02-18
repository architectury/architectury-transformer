package me.shedaniel.architectury.transformer.transformers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.shedaniel.architectury.transformer.Transform;
import me.shedaniel.architectury.transformer.transformers.base.AssetEditTransformer;
import me.shedaniel.architectury.transformer.transformers.base.edit.AssetEditSink;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import me.shedaniel.architectury.transformer.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

public class RuntimeMixinRefmapDetector implements AssetEditTransformer {
    @Override
    public void doEdit(TransformerContext context, AssetEditSink sink) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        sink.handle((path, bytes) -> {
            String trimmedPath = Transform.stripLoadingSlash(path);
            if (trimmedPath.endsWith(".json") && !trimmedPath.contains("/") && !trimmedPath.contains("\\")) {
                Logger.debug("Checking whether " + path + " is a mixin config.");
                try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes))) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        boolean hasMixins = json.has("mixins") && json.get("mixins").isJsonArray();
                        boolean hasClient = json.has("client") && json.get("client").isJsonArray();
                        boolean hasServer = json.has("server") && json.get("server").isJsonArray();
                        if (json.has("package") && json.has("refmap") && (hasMixins || hasClient || hasServer)) {
                            Logger.error("Mixin Config [%s] contains 'refmap', please remove it so it works in development environment!", trimmedPath);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }
}
