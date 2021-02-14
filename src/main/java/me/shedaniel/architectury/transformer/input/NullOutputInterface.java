package me.shedaniel.architectury.transformer.input;

import java.io.IOException;
import java.util.function.UnaryOperator;

public class NullOutputInterface implements OutputInterface {
    @Override
    public void addFile(String path, byte[] bytes) throws IOException {
        
    }
    
    @Override
    public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        
    }
    
    @Override
    public void close() throws IOException {
        
    }
}
