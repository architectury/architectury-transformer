package me.shedaniel.architectury.transformer.input;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.UnaryOperator;

public interface OutputInterface extends Closeable {
    void addFile(String path, byte[] bytes) throws IOException;
    
    void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException;
}
