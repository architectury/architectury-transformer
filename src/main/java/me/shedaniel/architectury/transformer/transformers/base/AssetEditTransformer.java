package me.shedaniel.architectury.transformer.transformers.base;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.transformers.base.edit.AssetEditSink;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;

public interface AssetEditTransformer extends Transformer {
    void doEdit(TransformerContext context, AssetEditSink sink) throws Exception;
}
