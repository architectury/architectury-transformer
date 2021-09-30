package dev.architectury.transformer;

import dev.architectury.transformer.input.AbstractFileAccess;
import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.util.HashUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class RuntimeReloadFileAccess extends AbstractFileAccess {
    private final Map<String, String> classRedefineCache;
    private final Map<String, byte[]> redefine;
    private final FileAccess out;
    private final FileAccess debugOut;
    
    public RuntimeReloadFileAccess(Map<String, String> classRedefineCache, Map<String, byte[]> redefine, FileAccess out, FileAccess debugOut) {
        super(out);
        this.classRedefineCache = classRedefineCache;
        this.redefine = redefine;
        this.out = out;
        this.debugOut = debugOut;
    }
    
    @Override
    public boolean addFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        if (out.addFile(s, bytes) && (debugOut == null || debugOut.addFile(s, bytes))) {
            if (path.endsWith(".class")) {
                s = s.substring(0, s.length() - 6);
                String sha256 = HashUtils.sha256(bytes);
                if (!Objects.equals(classRedefineCache.get(s), sha256)) {
                    classRedefineCache.put(s, sha256);
                    redefine.put(s, bytes);
                }
            }
            
            return true;
        }
        return false;
    }
    
    @Override
    public byte[] modifyFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        bytes = out.modifyFile(s, bytes);
        if (s.endsWith(".class") && bytes != null) {
            String sha256 = HashUtils.sha256(bytes);
            if (!Objects.equals(classRedefineCache.get(s), sha256)) {
                String className = s.substring(0, s.length() - 6);
                classRedefineCache.put(className, sha256);
                redefine.put(className, bytes);
            }
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
            String sha256 = HashUtils.sha256(bytes);
            if (!Objects.equals(classRedefineCache.get(s), sha256)) {
                String className = s.substring(0, s.length() - 6);
                classRedefineCache.put(className, sha256);
                redefine.put(className, bytes);
            }
        }
        if (debugOut != null && bytes != null) {
            return debugOut.modifyFile(s, bytes);
        }
        return bytes;
    }
    
    @Override
    public boolean deleteFile(String path) throws IOException {
        String s = Transform.trimSlashes(path);
        if (out.deleteFile(s) && (debugOut == null || debugOut.deleteFile(s))) {
            if (path.endsWith(".class")) {
                s = s.substring(0, s.length() - 6);
                classRedefineCache.remove(s);
                redefine.remove(s);
            }
            
            return true;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return out.toString();
    }
    
    @Override
    public void close() {
    }
}
