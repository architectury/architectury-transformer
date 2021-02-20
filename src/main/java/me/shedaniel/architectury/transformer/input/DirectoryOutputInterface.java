package me.shedaniel.architectury.transformer.input;

import me.shedaniel.architectury.transformer.util.ClosableChecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.UnaryOperator;

public class DirectoryOutputInterface extends ClosableChecker implements OutputInterface {
    private final Path root;
    
    public DirectoryOutputInterface(Path root) {
        this.root = root;
    }
    
    @Override
    public void addFile(String path, byte[] bytes) throws IOException {
        validateCloseState();
        Path p = root.resolve(path);
        Path parent = p.normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(p, bytes, StandardOpenOption.CREATE);
    }
    
    @Override
    public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        validateCloseState();
        Path file = root.resolve(path);
        
        if (Files.exists(file)) {
            byte[] bytes = Files.readAllBytes(file);
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
        closeAndValidate();
    }
    
    @Override
    public String toString() {
        return root.toString();
    }
}
