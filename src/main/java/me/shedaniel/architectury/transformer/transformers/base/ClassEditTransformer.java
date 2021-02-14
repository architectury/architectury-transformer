package me.shedaniel.architectury.transformer.transformers.base;

import me.shedaniel.architectury.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;

public interface ClassEditTransformer extends Transformer {
    ClassNode doEdit(String name, ClassNode node);
}
