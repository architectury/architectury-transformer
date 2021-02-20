package me.shedaniel.architectury.transformer.util;

import java.io.Closeable;

public abstract class ClosableChecker implements Closeable {
    private boolean closed = false;
    
    protected void validateCloseState() {
        if (closed) {
            throw new IllegalStateException("Can't use this if this is closed already!");
        }
    }
    
    protected void closeAndValidate() {
        if (closed) {
            throw new IllegalStateException("Can't use this if this is closed already!");
        }
        closed = true;
    }
}
