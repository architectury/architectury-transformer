package dev.architectury.transformer.input;

import dev.architectury.transformer.util.ClosableChecker;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public abstract class BaseFileAccess extends ClosableChecker implements FileAccess {
    private final Map<String, byte[]> cache = new HashMap<>();
    private final boolean shouldCache;
    
    public BaseFileAccess(boolean shouldCache) {
        this.shouldCache = shouldCache;
    }
    
    protected void clearCache() {
        this.cache.clear();
    }
    
    @Override
    public void handle(Consumer<String> action) throws IOException {
        validateCloseState();
        try (Stream<String> stream = walk(null)) {
            stream.forEachOrdered(action);
        }
    }
    
    private byte[] cacheRead(String path) {
        if (this.shouldCache) {
            return cache.computeIfAbsent(path, this::_cacheRead);
        }
        
        return _cacheRead(path);
    }
    
    private byte[] _cacheRead(String path) {
        try {
            return read(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    @Override
    public void handle(BiConsumer<String, byte[]> action) throws IOException {
        validateCloseState();
        try (Stream<String> stream = walk(null)) {
            stream.forEachOrdered(path -> {
                action.accept(path, cacheRead(path));
            });
        }
    }
    
    @Override
    public boolean addFile(String path, byte[] bytes) throws IOException {
        validateCloseState();
        if (bytes == null) return false;
        write(path, bytes);
        clearCache();
        return true;
    }
    
    @Override
    public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        validateCloseState();
        
        if (exists(path)) {
            byte[] bytes = read(path);
            try {
                bytes = action.apply(bytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to modify " + path, e);
            }
            addFile(path, bytes);
            return bytes;
        }
        
        return null;
    }
    
    @Override
    public void close() throws IOException {
        closeAndValidate();
        clearCache();
    }
    
    protected abstract boolean exists(String path) throws IOException;
    
    protected abstract byte[] read(String path) throws IOException;
    
    protected abstract void write(String path, byte[] bytes) throws IOException;
    
    protected abstract Stream<String> walk(@Nullable String path) throws IOException;
}
