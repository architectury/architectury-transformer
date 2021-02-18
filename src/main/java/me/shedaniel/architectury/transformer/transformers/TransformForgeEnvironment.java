package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.transformers.base.TinyRemapperTransformer;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TransformForgeEnvironment implements TinyRemapperTransformer {
    private TinyTree srg;
    private Map<String, IMappingProvider> mixinMappingCache = new HashMap<>();
    
    @Override
    public List<IMappingProvider> collectMappings() throws Exception {
        List<IMappingProvider> providers = mapMixin();
        providers.add(remapEnvironment());
        return providers;
    }
    
    private IMappingProvider remapEnvironment() {
        return sink -> {
            // Stop shadow plugin from relocating this
            // net/fabricmc/api
            String fabricLoaderApiPackage = new String(new byte[]{0x6e, 0x65, 0x74, 0x2f, 0x66, 0x61, 0x62, 0x72, 0x69, 0x63, 0x6d, 0x63, 0x2f, 0x61, 0x70, 0x69}, StandardCharsets.UTF_8);
            sink.acceptClass(fabricLoaderApiPackage + "/Environment", "net/minecraftforge/api/distmarker/OnlyIn");
            sink.acceptClass(fabricLoaderApiPackage + "/EnvType", "net/minecraftforge/api/distmarker/Dist");
            sink.acceptField(
                    new IMappingProvider.Member(fabricLoaderApiPackage + "/EnvType", "SERVER", "L" + fabricLoaderApiPackage + "/EnvType" + ";"),
                    "DEDICATED_SERVER"
            );
        };
    }
    
    private List<IMappingProvider> mapMixin() throws IOException {
        List<IMappingProvider> providers = new ArrayList<>();
        
        if (srg == null) {
            Path srgMappingsPath = Paths.get(System.getProperty(BuiltinProperties.MAPPINGS_WITH_SRG));
            try (BufferedReader reader = Files.newBufferedReader(srgMappingsPath)) {
                srg = TinyMappingFactory.loadWithDetection(reader);
            }
        }
        
        for (String path : System.getProperty(BuiltinProperties.MIXIN_MAPPINGS).split(File.pathSeparator)) {
            File mixinMapFile = Paths.get(path).toFile();
            if (mixinMapFile.exists()) {
                providers.add(mixinMappingCache.computeIfAbsent(path, p -> sink -> {
                    TinyUtils.createTinyMappingProvider(mixinMapFile.toPath(), "named", "intermediary").load(new IMappingProvider.MappingAcceptor() {
                        @Override
                        public void acceptClass(String srcName, String dstName) {
                            sink.acceptClass(dstName, srg.getClasses()
                                    .stream()
                                    .filter(it -> Objects.equals(it.getName("intermediary"), dstName))
                                    .findFirst()
                                    .map(it -> it.getName("srg"))
                                    .orElse(dstName)
                            );
                        }
                        
                        @Override
                        public void acceptMethod(IMappingProvider.Member method, String dstName) {
                            sink.acceptMethod(
                                    new IMappingProvider.Member(method.owner, dstName, method.desc),
                                    srg.getClasses()
                                            .stream()
                                            .flatMap(it -> it.getMethods().stream())
                                            .filter(it -> Objects.equals(it.getName("intermediary"), dstName))
                                            .findFirst()
                                            .map(it -> it.getName("srg"))
                                            .orElse(dstName)
                            );
                        }
                        
                        @Override
                        public void acceptField(IMappingProvider.Member field, String dstName) {
                            sink.acceptField(
                                    new IMappingProvider.Member(field.owner, dstName, field.desc),
                                    srg.getClasses()
                                            .stream()
                                            .flatMap(it -> it.getFields().stream())
                                            .filter(it -> Objects.equals(it.getName("intermediary"), dstName))
                                            .findFirst()
                                            .map(it -> it.getName("srg"))
                                            .orElse(dstName)
                            );
                        }
                        
                        @Override
                        public void acceptMethodArg(IMappingProvider.Member method, int lvIndex, String dstName) {
                            
                        }
                        
                        @Override
                        public void acceptMethodVar(IMappingProvider.Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
                            
                        }
                    });
                }));
            }
        }
        
        return providers;
    }
}