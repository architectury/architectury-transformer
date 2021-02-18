package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.Transform;
import me.shedaniel.architectury.transformer.util.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

@FunctionalInterface
public interface ClasspathProvider {
    static ClasspathProvider fromProperties() {
        return () -> Stream.of(Transform.getClasspath())
                .map(Paths::get)
                .filter(Files::exists)
                .toArray(Path[]::new);
    }
    
    Path[] provide();
    
    default ClasspathProvider filter(Predicate<Path> filter) {
        return () -> Arrays.stream(provide())
                .filter(filter)
                .toArray(Path[]::new);
    }
    
    default ClasspathProvider logging() {
        return () -> {
            Path[] paths = provide();
            Logger.debug("Provided " + paths.length + " classpath jar(s):");
            for (Path path : paths) {
                Logger.debug(" - " + path.toString());
            }
            return paths;
        };
    }
}
