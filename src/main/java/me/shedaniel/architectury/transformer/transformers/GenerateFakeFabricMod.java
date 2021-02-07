package me.shedaniel.architectury.transformer.transformers;

import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a fake fabric mod.
 */
public class GenerateFakeFabricMod extends AbstractFakeMod {
    @Override
    public void transform(Path input, Path output) throws Throwable {
        Files.copy(input, output);
        String fakeModId = generateModId();
        ZipUtil.addOrReplaceEntries(output.toFile(), new ZipEntrySource[]{
                new ByteSource("fabric.mod.json",
                        ("{\n" +
                         "  \"schemaVersion\": 1,\n" +
                         "  \"id\": \"" + fakeModId + "\",\n" +
                         "  \"name\": \"Generated Mod (Please Ignore)\",\n" +
                         "  \"version\": \"1.0.0\",\n" +
                         "  \"custom\": {\n" +
                         "    \"fabric-loom:generated\": true\n" +
                         "  }\n" +
                         "}\n").getBytes(StandardCharsets.UTF_8))
        });
    }
}