package me.shedaniel.architectury.transformer;

import me.shedaniel.architectury.transformer.util.Logger;

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