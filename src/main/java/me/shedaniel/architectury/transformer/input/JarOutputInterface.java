package me.shedaniel.architectury.transformer.input;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class JarOutputInterface implements OutputInterface {
    private final Path path;
    private final FileSystem fs;
    
    public JarOutputInterface(Path path) {
        this.path = path;
        Map<String, String> env = new HashMap<>();
        env.put("create", String.valueOf(Files.notExists(path)));
        try {
            this.fs = FileSystems.newFileSystem(new URI("jar:" + path.toUri()), env);
        } catch (IOException | URISyntaxException exception) {
            throw new RuntimeException(exception);
        }
    }
    
    @Override
    public void addFile(String path, byte[] bytes) throws IOException {
        Path p = fs.getPath(path);
        Path parent = p.normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(p, bytes, StandardOpenOption.CREATE);
    }
    
    @Override
    public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        Path fsPath = fs.getPath(path);
        
        if (Files.exists(fsPath)) {
            byte[] bytes = Files.readAllBytes(fsPath);
            try {
                bytes = action.apply(bytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to modify " + path, e);
            }
            addFile(path, bytes);
        }
    }
    
    @Override
    public void close() throws IOException {
        fs.close();
    }
    
    @Override
    public String toString() {
        return path.toString();
    }
}
