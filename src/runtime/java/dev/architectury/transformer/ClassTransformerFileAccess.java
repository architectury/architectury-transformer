package dev.architectury.transformer;

import dev.architectury.transformer.handler.TransformHandler;
import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.util.Logger;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static dev.architectury.transformer.Transform.formatDuration;

public class ClassTransformerFileAccess implements ClassFileTransformer {
    private final TransformHandler handler;
    private final Function<String, TransformerRuntime.ToTransformData> dataFunction;
    
    public ClassTransformerFileAccess(TransformHandler handler, Function<String, TransformerRuntime.ToTransformData> dataFunction) {
        this.handler = handler;
        this.dataFunction = dataFunction;
    }
    
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (loader == ClassLoader.getSystemClassLoader()) return classfileBuffer;
        AtomicReference<byte[]> classBytes = new AtomicReference<>(classfileBuffer);
        TransformerRuntime.ToTransformData data = dataFunction.apply(className + ".class");
        if (data != null) {
            List<Transformer> transformers = data.getTransformers();
            FileAccess originalSource = data.getOriginalSource();
            FileAccess debugOut = data.getDebugOut();
            
            try {
                Transform.measureTime(() -> {
                    handler.handle(className + ".class", new Access(className, classBytes, originalSource), transformers);
                }, duration -> {
                    Logger.debug("Transformed " + className + " in " + formatDuration(duration));
                });
                if (debugOut != null) {
                    debugOut.addFile(className + ".class", classBytes.get());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return classBytes.get();
    }
    
    public static class Access implements FileAccess {
        private final String className;
        private final AtomicReference<byte[]> classBytes;
        private final FileAccess originalSource;
    
        public Access(String className, AtomicReference<byte[]> classBytes, FileAccess originalSource) {
            this.className = className;
            this.classBytes = classBytes;
            this.originalSource = originalSource;
        }
        
        @Override
        public boolean isClosed() {
            return false;
        }
        
        @Override
        public void handle(Consumer<String> action) {
            action.accept(className + ".class");
        }
        
        @Override
        public void handle(BiConsumer<String, byte[]> action) {
            action.accept(className + ".class", classBytes.get());
        }
        
        @Override
        public boolean addFile(String path, byte[] bytes) throws IOException {
            if (Transform.trimSlashes(path).equals(className + ".class") && bytes != null) {
                classBytes.set(bytes);
                originalSource.addFile(path, bytes);
                return true;
            }
            
            return false;
        }
        
        @Override
        public byte[] modifyFile(String path, byte[] bytes) throws IOException {
            if (Transform.trimSlashes(path).equals(className + ".class")) {
                classBytes.set(bytes);
                originalSource.modifyFile(path, bytes);
                return bytes;
            }
            
            return null;
        }
        
        @Override
        public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
            if (Transform.trimSlashes(path).equals(className + ".class")) {
                classBytes.set(action.apply(classBytes.get()));
                originalSource.modifyFile(path, classBytes.get());
                return classBytes.get();
            }
            
            return null;
        }
        
        @Override
        public boolean deleteFile(String path) {
            return false;
        }
    
        @Override
        public byte[] getFile(String path) throws IOException {
            if (Transform.trimSlashes(path).equals(className + ".class")) {
                return classBytes.get();
            }
            
            return originalSource.getFile(path);
        }
    
        @Override
        public String toString() {
            return className + ".class";
        }
        
        @Override
        public void close() {
        }
    }
}
