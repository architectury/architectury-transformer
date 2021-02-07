package me.shedaniel.architectury.transformer;

import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;

public interface Transformer extends Serializable {
    void transform(Path input, Path output) throws Throwable;
    
    default void writeObject(ObjectOutputStream stream) {
    }
}
