package me.shedaniel.architectury.transformer.transformers.base.edit;

public interface TransformerContext {
    void appendArgument(String... args);
    
    boolean canModifyAssets();
    
    boolean canAppendArgument();
}
