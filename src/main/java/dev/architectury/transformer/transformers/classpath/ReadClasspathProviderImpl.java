/*
 * This file is licensed under the MIT License, part of architectury-transformer.
 * Copyright (c) 2020, 2021, 2022 architectury
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

package dev.architectury.transformer.transformers.classpath;

import dev.architectury.tinyremapper.FileSystemHandler;
import dev.architectury.transformer.Transform;
import dev.architectury.transformer.transformers.ClasspathProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReadClasspathProviderImpl implements ReadClasspathProvider {
    private final ClasspathProvider provider;
    private Map<String, Integer> map = new HashMap<>();
    private byte[][] classpaths;
    
    public ReadClasspathProviderImpl(ClasspathProvider provider) {
        this.provider = provider;
    }
    
    @Override
    public byte[][] provide() {
        synchronized (this) {
            if (classpaths == null) {
                map.clear();
                try {
                    Transform.logTime(() -> {
                        ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                        List<CompletableFuture<List<Map.Entry<String, byte[]>>>> futures = new ArrayList<>();
                        List<FileSystem> fsToClose = Collections.synchronizedList(new ArrayList<>());
                        
                        for (Path path : provider.provide()) {
                            futures.add(read(path, threadPool, fsToClose, true));
                        }
                        
                        CompletableFuture<List<Map.Entry<String, byte[]>>> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(unused -> futures
                                        .stream()
                                        .map(CompletableFuture::join)
                                        .flatMap(Collection::stream)
                                        .collect(Collectors.toList()));
                        
                        List<Map.Entry<String, byte[]>> bytes = future.get(60, TimeUnit.SECONDS);
                        int[] i = {0};
                        classpaths = bytes.stream().peek(entry -> {
                            map.put(Transform.trimLeadingSlash(entry.getKey()), i[0]++);
                        }).map(Map.Entry::getValue).toArray(byte[][]::new);
                        threadPool.awaitTermination(0, TimeUnit.SECONDS);
                        
                        for (FileSystem system : fsToClose) {
                            FileSystemHandler.close(system);
                        }
                    }, "Read classpath");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            return classpaths;
        }
    }
    
    @Override
    public int indexOf(String type) {
        provide();
        return map.getOrDefault(type, -1);
    }
    
    private CompletableFuture<List<Map.Entry<String, byte[]>>> read(Path path, ExecutorService service, List<FileSystem> fsToClose, boolean isParentLevel) {
        if (path.toString().endsWith(".class")) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    String s = path.toString();
                    return Collections.singletonList(new AbstractMap.SimpleImmutableEntry<>(s.substring(0, s.length() - 6), bytes));
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
                return Collections.emptyList();
            }, service);
        } else if (isParentLevel && (path.toString().endsWith(".zip") || path.toString().endsWith(".jar"))) {
            try {
                URI uri = new URI("jar:" + path.toUri());
                FileSystem fs = FileSystemHandler.open(uri);
                fsToClose.add(fs);
                List<CompletableFuture<List<Map.Entry<String, byte[]>>>> futures = new ArrayList<>();
                Files.walk(fs.getPath("/")).forEach(f -> {
                    futures.add(read(f, service, fsToClose, false));
                });
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(unused -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
