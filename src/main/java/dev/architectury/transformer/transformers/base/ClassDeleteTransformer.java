package dev.architectury.transformer.transformers.base;

import dev.architectury.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;

public interface ClassDeleteTransformer extends Transformer {
    boolean shouldDelete(String name, ClassNode node);
}
