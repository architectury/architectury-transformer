/*
 * This file is licensed under the MIT License, part of architectury-transformer.
 * Copyright (c) 2020, 2021 shedaniel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
