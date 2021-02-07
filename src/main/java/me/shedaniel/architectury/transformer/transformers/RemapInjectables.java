package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.TransformerStepSkipped;
import me.shedaniel.architectury.transformer.util.LoggerFilter;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Remap architectury injectables calls to the injected classes.
 */
public class RemapInjectables implements Transformer {
    public static final String expectPlatform = "Lme/shedaniel/architectury/ExpectPlatform;";
    public static final String expectPlatformNew = "Lme/shedaniel/architectury/annotations/ExpectPlatform;";
    
    @Override
    public void transform(Path input, Path output) throws Throwable {
        if (isInjectInjectables()) {
            transformArchitecturyInjectables(input, output);
        } else {
            Files.copy(input, output);
            throw TransformerStepSkipped.INSTANCE;
        }
    }
    
    public static String getUniqueIdentifier() {
        return System.getProperty(BuiltinProperties.UNIQUE_IDENTIFIER);
    }
    
    public static boolean isInjectInjectables() {
        return System.getProperty(BuiltinProperties.INJECT_INJECTABLES, "true").equals("true");
    }
    
    public static String[] getClasspath() {
        return System.getProperty(BuiltinProperties.COMPILE_CLASSPATH, "true").split(File.pathSeparator);
    }
    
    private void transformArchitecturyInjectables(Path input, Path output) {
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(sink -> {
                    sink.acceptClass(
                            "me/shedaniel/architectury/targets/ArchitecturyTarget",
                            getUniqueIdentifier() + "/PlatformMethods"
                    );
                    sink.acceptMethod(
                            new IMappingProvider.Member(
                                    "me/shedaniel/architectury/targets/ArchitecturyTarget",
                                    "getCurrentTarget",
                                    "()Ljava/lang/String;"
                            ), "getModLoader"
                    );
                })
                .build();
        
        Path[] classpath = Stream.of(getClasspath())
                .map(Paths::get)
                .toArray(Path[]::new);
        
        LoggerFilter.replaceSystemOut();
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
            outputConsumer.addNonClassFiles(input, NonClassCopyMode.UNCHANGED, null);
            remapper.readClassPath(classpath);
            remapper.readInputs(input);
            remapper.apply(outputConsumer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remap " + input + " to " + output, e);
        } finally {
            remapper.finish();
        }
    }
}