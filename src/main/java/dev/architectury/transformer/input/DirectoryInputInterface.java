/*
 * This file is licensed under the MIT License, part of architectury-transformer.
 * Copyright (c) 2020, 2021 architectury
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

package dev.architectury.transformer.input;

import dev.architectury.transformer.util.ClosableChecker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class DirectoryInputInterface extends ClosableChecker implements InputInterface {
    private static final WeakHashMap<Path, DirectoryInputInterface> INTERFACES = new WeakHashMap<>();
    protected final Path root;
    protected final Map<Path, byte[]> cache = new HashMap<>();
    
    protected DirectoryInputInterface(Path root) {
        this.root = root;
    }
    
    public static DirectoryInputInterface of(Path root) throws IOException {
        synchronized (INTERFACES) {
            if (INTERFACES.containsKey(root)) {
                return INTERFACES.get(root);
            }
            
            for (Map.Entry<Path, DirectoryInputInterface> entry : INTERFACES.entrySet()) {
                if (Files.isSameFile(entry.getKey(), root)) {
                    return entry.getValue();
                }
            }
            
            DirectoryInputInterface inputInterface = new DirectoryInputInterface(root);
            INTERFACES.put(root, inputInterface);
            return inputInterface;
        }
    }
    
    @Override
    public void handle(Consumer<String> action) throws IOException {
        validateCloseState();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.forEachOrdered(path -> {
                if (Files.isDirectory(path)) return;
                action.accept(path.toString());
            });
        }
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
        cache.clear();
    }
    
    @Override
    public void close() throws IOException {
        closeAndValidate();
        cache.clear();
        INTERFACES.remove(root, this);
    }
    
    @Override
    public String toString() {
        return root.toString();
    }
}
