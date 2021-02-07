package me.shedaniel.architectury.transformer;

@FunctionalInterface
public interface ClassAdder {
    void add(String className, byte[] bytes) throws Exception;
}
