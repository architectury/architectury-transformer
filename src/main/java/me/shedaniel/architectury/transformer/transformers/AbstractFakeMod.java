package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.transformers.base.AssetEditTransformer;

import java.util.UUID;

public abstract class AbstractFakeMod implements AssetEditTransformer {
    protected String generateModId() {
        return "generated_" + UUID.randomUUID().toString().substring(0, 7);
    }
}
