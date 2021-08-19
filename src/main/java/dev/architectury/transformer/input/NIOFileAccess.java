package dev.architectury.transformer.input;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class NIOFileAccess extends BaseFileAccess {
    public NIOFileAccess(boolean shouldCache) {
        super(shouldCache);
    }
    
    private Path resolve(String path) {
        return rootPath().resolve(path);
    }
    
    @Override
    protected boolean exists(String path) {
        return Files.exists(resolve(path));
    }
    
    @Override
    protected byte[] read(String path) throws IOException {
        return Files.readAllBytes(resolve(path));
    }
    
    @Override
    protected void write(String path, byte[] bytes) throws IOException {
        Path p = resolve(path);
        Path parent = p.normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(p, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public boolean deleteFile(String path) throws IOException {
        return Files.deleteIfExists(resolve(path));
    }

    @Override
    protected Stream<String> walk(@Nullable String path) throws IOException {
        return Files.walk(path == null ? rootPath() : resolve(path))
                .filter(((Predicate<Path>) Files::isDirectory).negate())
                .map(Path::toString);
    }
    
    protected abstract Path rootPath();
}
