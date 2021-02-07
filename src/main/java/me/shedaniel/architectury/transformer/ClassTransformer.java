package me.shedaniel.architectury.transformer;

import org.objectweb.asm.tree.ClassNode;

@FunctionalInterface
public interface ClassTransformer {
    ClassNode transform(ClassNode clazz, ClassAdder classAdder) throws Exception;
}
