package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.TransformerStepSkipped;
import org.zeroturnaround.zip.ZipUtil;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Removes {@code fabric.mod.json} from the jar.
 */
public class RemoveFabricModJson implements Transformer {
    @Override
    public void transform(Path input, Path output) throws Throwable {
        Files.copy(input, output);
        if (ZipUtil.containsEntry(output.toFile(), "fabric.mod.json")) {
            ZipUtil.removeEntry(output.toFile(), "fabric.mod.json");
        } else {
            throw TransformerStepSkipped.INSTANCE;
        }
    }
}