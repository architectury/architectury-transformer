package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.transformers.base.TinyRemapperTransformer;
import net.fabricmc.tinyremapper.IMappingProvider;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Remap architectury injectables calls to the injected classes.
 */
public class RemapInjectables implements TinyRemapperTransformer {
    public static final String expectPlatform = "Lme/shedaniel/architectury/ExpectPlatform;";
    public static final String expectPlatformNew = "Lme/shedaniel/architectury/annotations/ExpectPlatform;";
    
    @Override
    public List<IMappingProvider> collectMappings() throws Exception {
        if (isInjectInjectables()) {
            return Collections.singletonList(sink -> {
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
            });
        }
        return Collections.emptyList();
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
}