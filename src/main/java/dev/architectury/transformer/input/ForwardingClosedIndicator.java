package dev.architectury.transformer.input;

import java.io.IOException;
import java.io.UncheckedIOException;

public interface ForwardingClosedIndicator extends ClosedIndicator {
    ClosedIndicator parent() throws IOException;
    
    @Override
    default boolean isClosed() {
        try {
            return parent().isClosed();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    @Override
    default void close() throws IOException {
        parent().close();
    }
}
