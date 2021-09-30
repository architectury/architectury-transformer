package dev.architectury.transformer;

import dev.architectury.transformer.input.AbstractFileAccess;
import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.util.HashUtils;

import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

public class RuntimeFileAccess extends AbstractFileAccess {
    private final Map<String, String> classRedefineCache;
    private final FileAccess out;
    private final FileAccess debugOut;
    
    public RuntimeFileAccess(Map<String, String> classRedefineCache, FileAccess out, FileAccess debugOut) {
        super(out);
        this.classRedefineCache = classRedefineCache;
        this.out = out;
        this.debugOut = debugOut;
    }
    
    @Override
    public boolean addFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        if (s.endsWith(".class")) {
            classRedefineCache.put(s.substring(0, s.length() - 6), HashUtils.sha256(bytes));
        }
        return out.addFile(s, bytes) && (debugOut == null || debugOut.addFile(s, bytes));
    }
    
    @Override
    public byte[] modifyFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        bytes = out.modifyFile(s, bytes);
        if (s.endsWith(".class") && bytes != null) {
            classRedefineCache.put(s.substring(0, s.length() - 6), HashUtils.sha256(bytes));
        }
        if (debugOut != null && bytes != null) {
            return debugOut.modifyFile(s, bytes);
        }
        return bytes;
    }
    
    @Override
    public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        String s = Transform.trimSlashes(path);
        byte[] bytes = out.modifyFile(s, action);
        if (s.endsWith(".class") && bytes != null) {
            classRedefineCache.put(s.substring(0, s.length() - 6), HashUtils.sha256(bytes));
        }
        if (debugOut != null && bytes != null) {
            return debugOut.modifyFile(s, bytes);
        }
        return bytes;
    }
    
    @Override
    public boolean deleteFile(String path) throws IOException {
        String s = Transform.trimSlashes(path);
        if (s.endsWith(".class")) {
            classRedefineCache.remove(s.substring(0, s.length() - 6));
        }
        return out.deleteFile(s) && (debugOut == null || debugOut.deleteFile(s));
    }
    
    @Override
    public String toString() {
        return out.toString();
    }
    
    @Override
    public void close() {
    }
}
