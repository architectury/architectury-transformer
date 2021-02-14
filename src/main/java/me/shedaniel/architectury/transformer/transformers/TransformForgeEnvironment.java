package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.transformers.base.TinyRemapperTransformer;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TransformForgeEnvironment implements TinyRemapperTransformer {
    @Override
    public List<IMappingProvider> collectMappings() throws Exception {
        List<IMappingProvider> providers = mapMixin();
        providers.add(remapEnvironment());
        return providers;
    }
    
    private IMappingProvider remapEnvironment() {
        return out -> {
            out.acceptClass("net/fabricmc/api/Environment", "net/minecraftforge/api/distmarker/OnlyIn");
            out.acceptClass("net/fabricmc/api/EnvType", "net/minecraftforge/api/distmarker/Dist");
            out.acceptField(
                    new IMappingProvider.Member("net/fabricmc/api/EnvType", "SERVER", "Lnet/fabricmc/api/EnvType;"),
                    "DEDICATED_SERVER"
            );
        };
    }
    
    private List<IMappingProvider> mapMixin() throws IOException {
        List<IMappingProvider> providers = new ArrayList<>();
        Path srgMappingsPath = Paths.get(System.getProperty(BuiltinProperties.MAPPINGS_WITH_SRG));
        TinyTree srg;
        try (BufferedReader reader = Files.newBufferedReader(srgMappingsPath)) {
            srg = TinyMappingFactory.loadWithDetection(reader);
        }
        
        for (String path : BuiltinProperties.MIXIN_MAPPINGS.split(File.pathSeparator)) {
            File mixinMapFile = Paths.get(path).toFile();
            if (mixinMapFile.exists()) {
                providers.add(sink -> {
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
                });
            }
        }
        
        return providers;
    }
}