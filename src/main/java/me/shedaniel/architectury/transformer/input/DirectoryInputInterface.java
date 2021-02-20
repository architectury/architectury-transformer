package me.shedaniel.architectury.transformer.input;

import me.shedaniel.architectury.transformer.util.ClosableChecker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class DirectoryInputInterface extends ClosableChecker implements InputInterface {
    private final Path root;
    private final Map<Path, byte[]> cache = new HashMap<>();
    
    public DirectoryInputInterface(Path root) {
        this.root = root;
    }
    
    @Override
    public void handle(BiConsumer<String, byte[]> action) throws IOException {
        validateCloseState();
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
    
    @Override
    public void close() throws IOException {
        closeAndValidate();
        cache.clear();
    }
    
    @Override
    public String toString() {
        return root.toString();
    }
}
