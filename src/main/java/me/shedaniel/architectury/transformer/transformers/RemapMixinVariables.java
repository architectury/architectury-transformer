package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.transformers.base.TinyRemapperTransformer;
import me.shedaniel.architectury.transformer.util.Logger;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemapMixinVariables implements TinyRemapperTransformer {
    private Map<String, IMappingProvider> mixinMappingCache = new HashMap<>();
    
    @Override
    public List<IMappingProvider> collectMappings() throws Exception {
        List<IMappingProvider> providers = new ArrayList<>();
        for (String path : System.getProperty(BuiltinProperties.MIXIN_MAPPINGS).split(File.pathSeparator)) {
            File mixinMapFile = Paths.get(path).toFile();
            if (mixinMapFile.exists()) {
                Logger.debug("Reading mixin mappings file: " + mixinMapFile.getAbsolutePath());
                providers.add(mixinMappingCache.computeIfAbsent(path, p ->
                        TinyUtils.createTinyMappingProvider(mixinMapFile.toPath(), "named", "intermediary"))
                );
            }
        }
        
        return providers;
    }
}