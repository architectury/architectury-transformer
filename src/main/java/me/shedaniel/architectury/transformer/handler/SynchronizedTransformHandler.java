package me.shedaniel.architectury.transformer.handler;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.input.InputInterface;
import me.shedaniel.architectury.transformer.input.OutputInterface;

import java.io.IOException;
import java.util.List;

public class SynchronizedTransformHandler implements TransformHandler {
    private final TransformHandler parent;
    
    SynchronizedTransformHandler(TransformHandler parent) {
        this.parent = parent;
    }
    
    @Override
    public TransformHandler asSynchronized() {
        return this;
    }
    
    @Override
    public void handle(InputInterface input, OutputInterface output, List<Transformer> transformers) throws Exception {
        synchronized (this.parent) {
            this.parent.handle(input, output, transformers);
        }
    }
    
    @Override
    public void close() throws IOException {
        synchronized (this.parent) {
            this.parent.close();
        }
    }
}
