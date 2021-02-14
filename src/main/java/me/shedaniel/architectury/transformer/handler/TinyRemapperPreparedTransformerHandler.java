package me.shedaniel.architectury.transformer.handler;

import me.shedaniel.architectury.transformer.Transform;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import net.fabricmc.tinyremapper.ClassInstance;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class TinyRemapperPreparedTransformerHandler extends SimpleTransformerHandler {
    private TinyRemapper remapper;
    
    public TinyRemapperPreparedTransformerHandler(TransformerContext context) throws Exception {
        super(context);
        prepare();
    }
    
    private void prepare() throws Exception {
        remapper = TinyRemapper.newRemapper().build();
        
        Path[] classpath = Stream.of(Transform.getClasspath())
                .map(Paths::get)
                .filter(Files::exists)
                .toArray(Path[]::new);
        
        remapper.readClassPath(classpath);
    }
    
    private void resetTR(Collection<IMappingProvider> mappingProviders) throws Exception {
        Field outputBufferField = remapper.getClass().getDeclaredField("outputBuffer");
        outputBufferField.setAccessible(true);
        outputBufferField.set(remapper, null);
        
        resetTRMap("classMap");
        resetTRMap("methodMap");
        resetTRMap("methodArgMap");
        resetTRMap("fieldMap");
        
        Field mappingProvidersField = remapper.getClass().getDeclaredField("mappingProviders");
        mappingProvidersField.setAccessible(true);
        ((Collection<IMappingProvider>) mappingProvidersField.get(remapper)).clear();
        ((Collection<IMappingProvider>) mappingProvidersField.get(remapper)).addAll(mappingProviders);
    }
    
    private void resetTRMap(String fieldName) throws Exception {
        Field field = remapper.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Map) field.get(remapper)).clear();
    }
    
    private Map<String, ClassInstance> tmpClasses;
    
    @Override
    public TinyRemapper getRemapper(List<IMappingProvider> providers) throws Exception {
        Field classesField = remapper.getClass().getDeclaredField("classes");
        classesField.setAccessible(true);
        tmpClasses = new HashMap<>((Map<String, ClassInstance>) classesField.get(remapper));
        resetTR(providers);
        return remapper;
    }
    
    @Override
    protected void closeRemapper(TinyRemapper remapper) throws Exception {
        Field classesField = remapper.getClass().getDeclaredField("classes");
        classesField.setAccessible(true);
        classesField.set(remapper, tmpClasses);
        tmpClasses = null;
        resetTR(Collections.emptyList());
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        if (this.remapper != null) {
            this.remapper.finish();
        }
        this.remapper = null;
    }
}
