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

package me.shedaniel.architectury.transformer.transformers.classpath;

import me.shedaniel.architectury.transformer.Transform;
import me.shedaniel.architectury.transformer.transformers.ClasspathProvider;
import net.fabricmc.tinyremapper.FileSystemHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReadClasspathProviderImpl implements ReadClasspathProvider {
    private final ClasspathProvider provider;
    private byte[][] classpaths;
    
    public ReadClasspathProviderImpl(ClasspathProvider provider) {
        this.provider = provider;
    }
    
    @Override
    public byte[][] provide() {
        synchronized (this) {
            if (classpaths == null) {
                
                try {
                    Transform.logTime(() -> {
                        ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                        List<CompletableFuture<List<byte[]>>> futures = new ArrayList<>();
                        List<FileSystem> fsToClose = Collections.synchronizedList(new ArrayList<>());
                        
                        for (Path path : provider.provide()) {
                            futures.add(read(path, threadPool, fsToClose, true));
                        }
                        
                        CompletableFuture<List<byte[]>> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(unused -> futures
                                        .stream()
                                        .map(CompletableFuture::join)
                                        .flatMap(Collection::stream)
                                        .collect(Collectors.toList()));
                        
                        List<byte[]> bytes = future.get(60, TimeUnit.SECONDS);
                        classpaths = bytes.toArray(new byte[0][0]);
                        threadPool.awaitTermination(0, TimeUnit.SECONDS);
                    }, "Read classpath");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            return classpaths;
        }
    }
    
    private static CompletableFuture<List<byte[]>> read(Path path, ExecutorService service, List<FileSystem> fsToClose, boolean isParentLevel) {
        if (path.toString().endsWith(".class")) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return Collections.singletonList(Files.readAllBytes(path));
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
                return Collections.emptyList();
            }, service);
        } else if (isParentLevel && (path.toString().endsWith(".zip") || path.toString().endsWith(".jar"))) {
            try {
                URI uri = new URI("jar:" + path.toUri().toString());
                FileSystem fs = FileSystemHandler.open(uri);
                fsToClose.add(fs);
                List<CompletableFuture<List<byte[]>>> futures = new ArrayList<>();
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
