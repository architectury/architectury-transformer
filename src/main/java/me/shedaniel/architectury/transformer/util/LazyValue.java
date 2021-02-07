package me.shedaniel.architectury.transformer.util;

import java.util.function.Supplier;

public class LazyValue<T> implements Supplier<T> {
    private Supplier<T> supplier;
    private T value;
    
    public LazyValue(Supplier<T> supplier) {
        this.supplier = supplier;
    }
    
    @Override
    public T get() {
        if (supplier != null) {
            value = supplier.get();
            supplier = null;
        }
        
        return value;
    }
}
