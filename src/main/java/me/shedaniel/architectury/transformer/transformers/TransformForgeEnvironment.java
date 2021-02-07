package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.util.LoggerFilter;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

public class TransformForgeEnvironment implements Transformer {
    @Override
    public void transform(Path input, Path output) throws Throwable {
        TinyRemapper.Builder builder = TinyRemapper.newRemapper()
                .withMappings(remapEnvironment())
                .skipLocalVariableMapping(true);
        
        mapMixin(builder);
        
        Path[] classpath = Stream.of(RemapInjectables.getClasspath())
                .map(Paths::get)
                .toArray(Path[]::new);
        
        TinyRemapper remapper = builder.build();
        LoggerFilter.replaceSystemOut();
        
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
            outputConsumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, null);
            remapper.readClassPath(classpath);
            remapper.readInputs(input);
            remapper.apply(outputConsumer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remap " + input + " to " + output, e);
        } finally {
            remapper.finish();
        }
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
    
    private void mapMixin(TinyRemapper.Builder remapperBuilder) throws IOException {
        Path srgMappingsPath = Paths.get(System.getProperty(BuiltinProperties.MAPPINGS_WITH_SRG));
        TinyTree srg;
        try (BufferedReader reader = Files.newBufferedReader(srgMappingsPath)) {
            srg = TinyMappingFactory.loadWithDetection(reader);
        }
        
        for (String path : BuiltinProperties.MIXIN_MAPPINGS.split(File.pathSeparator)) {
            File mixinMapFile = Paths.get(path).toFile();
            if (mixinMapFile.exists()) {
                remapperBuilder.withMappings(sink -> {
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
    }
}