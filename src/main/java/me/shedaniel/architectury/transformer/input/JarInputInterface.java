package me.shedaniel.architectury.transformer.input;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class JarInputInterface implements InputInterface {
    private final Path path;
    private final FileSystem fs;
    private final Map<Path, byte[]> cache = new HashMap<>();
    
    public JarInputInterface(Path path) {
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
    public void handle(BiConsumer<String, byte[]> action) throws IOException {
        for (Path root : fs.getRootDirectories()) {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.forEachOrdered(path -> {
                    if (Files.isDirectory(path)) return;
                    action.accept(path.toString(), cache.computeIfAbsent(path, p -> {
                        try {
                            return Files.readAllBytes(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }));
                });
            }
        }
    }
    
    @Override
    public void close() throws IOException {
        fs.close();
        cache.clear();
    }
    
    @Override
    public String toString() {
        return path.toString();
    }
}
