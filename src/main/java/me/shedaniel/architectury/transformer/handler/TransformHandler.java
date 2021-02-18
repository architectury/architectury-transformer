package me.shedaniel.architectury.transformer.handler;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.input.InputInterface;
import me.shedaniel.architectury.transformer.input.OutputInterface;

import java.io.Closeable;
import java.util.List;

public interface TransformHandler extends Closeable {
    default TransformHandler asThreadLocked() {
        return new ThreadLockedTransformHandler(this);
    }
    
    void handle(InputInterface input, OutputInterface output,  List<Transformer> transformers) throws Exception;
}
