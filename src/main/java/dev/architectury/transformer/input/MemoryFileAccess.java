package dev.architectury.transformer.input;

import dev.architectury.transformer.Transform;
import org.jetbrains.annotations.Nullable;
import org.zeroturnaround.zip.commons.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MemoryFileAccess extends BaseFileAccess {
    private final Map<String, byte[]> data;
    
    protected MemoryFileAccess(Map<String, byte[]> data) {
        super(false);
        this.data = data;
    }
    
    public static MemoryFileAccess of() throws IOException {
        return of(new HashMap<>());
    }
    
    public static MemoryFileAccess of(Map<String, byte[]> data) throws IOException {
        return new MemoryFileAccess(data);
    }
    
    public static MemoryFileAccess ofZipFile(byte[] data) throws IOException {
        MemoryFileAccess anInterface = MemoryFileAccess.of();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!Transform.trimSlashes(e.getName()).isEmpty()) {
                    anInterface.addFile(e.getName(), IOUtils.toByteArray(zis));
                }
            }
        }
        return anInterface;
    }
    
    private String format(String path) {
        return Transform.trimSlashes(path);
    }
    
    @Override
    protected boolean exists(String path) {
        return data.containsKey(format(path));
    }
    
    @Override
    protected byte[] read(String path) throws IOException {
        byte[] bytes = data.get(format(path));
        if (bytes == null) throw new FileNotFoundException(path);
        return bytes;
    }
    
    @Override
    protected void write(String path, byte[] bytes) throws IOException {
        data.put(format(path), bytes);
    }
    
    @Override
    protected Stream<String> walk(@Nullable String path) throws IOException {
        Stream<String> stream = data.keySet().stream();
        if (path != null) {
            String trimSlashes = format(path) + "/";
            stream = stream.filter(s -> s.startsWith(trimSlashes));
        }
        return stream;
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        data.clear();
    }
}
