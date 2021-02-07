package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.Transformer;

import java.util.UUID;

public abstract class AbstractFakeMod implements Transformer {
    protected String generateModId() {
        return "generated_" + UUID.randomUUID().toString().substring(0, 7);
    }
}
