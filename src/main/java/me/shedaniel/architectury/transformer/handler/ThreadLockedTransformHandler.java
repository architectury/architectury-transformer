package me.shedaniel.architectury.transformer.handler;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.input.InputInterface;
import me.shedaniel.architectury.transformer.input.OutputInterface;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadLockedTransformHandler implements TransformHandler {
    private final TransformHandler parent;
    private final ReentrantLock lock = new ReentrantLock();
    
    ThreadLockedTransformHandler(TransformHandler parent) {
        this.parent = parent;
    }
    
    @Override
    public TransformHandler asThreadLocked() {
        return this;
    }
    
    @Override
    public void handle(InputInterface input, OutputInterface output, List<Transformer> transformers) throws Exception {
        lock.lock();
        try {
            this.parent.handle(input, output, transformers);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            this.parent.close();
        } finally {
            lock.unlock();
        }
    }
}
