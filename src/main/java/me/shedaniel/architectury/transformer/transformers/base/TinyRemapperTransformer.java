package me.shedaniel.architectury.transformer.transformers.base;

import me.shedaniel.architectury.transformer.Transformer;
import net.fabricmc.tinyremapper.IMappingProvider;

import java.util.List;

public interface TinyRemapperTransformer extends Transformer {
    List<IMappingProvider> collectMappings() throws Exception;
}
