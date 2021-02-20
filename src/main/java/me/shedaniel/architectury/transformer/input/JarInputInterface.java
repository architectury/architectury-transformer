package me.shedaniel.architectury.transformer.input;

import me.shedaniel.architectury.transformer.util.ClosableChecker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class JarInputInterface extends ClosableChecker implements InputInterface {
    private final Path path;
    private boolean shouldCloseFs;
    private FileSystem fs;
    private final Map<Path, byte[]> cache = new HashMap<>();
    
    public JarInputInterface(Path path) {
        this.path = path;
        Map<String, String> env = new HashMap<>();
        env.put("create", String.valueOf(Files.notExists(path)));
        
        try {
            URI uri = new URI("jar:" + path.toUri());
            FileSystem fs;
            boolean shouldCloseFs = false;
            
            try {
                fs = FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException exception) {
                fs = FileSystems.newFileSystem(uri, env);
                shouldCloseFs = true;
            }
            
            this.fs = fs;
            this.shouldCloseFs = shouldCloseFs;
        } catch (IOException | URISyntaxException exception) {
            throw new RuntimeException(exception);
        }
    }
    
    @Override
    public void handle(BiConsumer<String, byte[]> action) throws IOException {
        validateCloseState();
        for (Path root : getFS().getRootDirectories()) {
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
        closeAndValidate();
        if (shouldCloseFs) {
            fs.close();
        }
        cache.clear();
    }
    
    private FileSystem getFS() {
        closeAndValidate();
        if (!shouldCloseFs && !fs.isOpen()) {
            try {
                Map<String, String> env = new HashMap<>();
                env.put("create", String.valueOf(Files.notExists(path)));
                URI uri = new URI("jar:" + path.toUri());
                cache.clear();
                fs = FileSystems.newFileSystem(uri, env);
                shouldCloseFs = true;
                return fs;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (!fs.isOpen()) {
            throw new ClosedFileSystemException();
        }
        return fs;
    }
    
    @Override
    public String toString() {
        return path.toString();
    }
}
