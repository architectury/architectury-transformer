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

package dev.architectury.transformer;

import dev.architectury.transformer.util.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.Consumer;

public class PathModifyListener extends Thread {
    private final Path path;
    private final Consumer<Path> listener;
    
    public PathModifyListener(Path path, Consumer<Path> listener) {
        this.path = path;
        this.listener = listener;
        setDaemon(true);
        start();
    }
    
    @Override
    public void run() {
        try {
            Logger.info("Listening at " + path);
            WatchService watcher = path.getFileSystem().newWatchService();
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                WatchKey watchKey = path.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    WatchKey wk = watchService.take();
                    for (WatchEvent<?> event : wk.pollEvents()) {
                        Path changed = (Path) event.context();
                        if (Objects.equals(changed.toString(), path.getFileName().toString())) {
                            listener.accept(path);
                            break;
                        }
                    }
                    
                    wk.reset();
                    Thread.sleep(1000);
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}