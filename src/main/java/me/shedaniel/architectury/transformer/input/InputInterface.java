package me.shedaniel.architectury.transformer.input;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.BiConsumer;

public interface InputInterface extends Closeable {
    void handle(BiConsumer<String, byte[]> action) throws IOException;
}