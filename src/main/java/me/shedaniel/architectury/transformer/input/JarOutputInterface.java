package me.shedaniel.architectury.transformer.input;

import me.shedaniel.architectury.transformer.util.ClosableChecker;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class JarOutputInterface extends ClosableChecker implements OutputInterface {
    private final Path path;
    private boolean shouldCloseFs;
    private FileSystem fs;
    
    public JarOutputInterface(Path path) {
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
    public void addFile(String path, byte[] bytes) throws IOException {
        validateCloseState();
        Path p = getFS().getPath(path);
        Path parent = p.normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(p, bytes, StandardOpenOption.CREATE);
    }
    
    @Override
    public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        validateCloseState();
        Path fsPath = getFS().getPath(path);
        
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
        closeAndValidate();
        if (shouldCloseFs) {
            fs.close();
        }
    }
    
    private FileSystem getFS() {
        closeAndValidate();
        if (!shouldCloseFs && !fs.isOpen()) {
            try {
                Map<String, String> env = new HashMap<>();
                env.put("create", String.valueOf(Files.notExists(path)));
                URI uri = new URI("jar:" + path.toUri());
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
