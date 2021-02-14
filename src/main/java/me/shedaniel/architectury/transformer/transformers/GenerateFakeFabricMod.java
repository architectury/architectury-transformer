package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.transformers.base.edit.AssetEditSink;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;

/**
 * Generates a fake fabric mod.
 */
public class GenerateFakeFabricMod extends AbstractFakeMod {
    @Override
    public void doEdit(TransformerContext context, AssetEditSink sink) throws Exception {
        String fakeModId = generateModId();
        sink.addFile("fabric.mod.json",
                "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"id\": \"" + fakeModId + "\",\n" +
                "  \"name\": \"Generated Mod (Please Ignore)\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"custom\": {\n" +
                "    \"fabric-loom:generated\": true\n" +
                "  }\n" +
                "}\n");
    }
}