package dev.architectury.transformer.input;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface ForwardingFileView extends FileView, ForwardingClosedIndicator {
    @Override
    FileView parent() throws IOException;
    
    @Override
    default void handle(BiConsumer<String, byte[]> action) throws IOException {
        parent().handle(action);
    }
    
    @Override
    default void handle(Consumer<String> action) throws IOException {
        parent().handle(action);
    }
    
    @Override
    default void handle(Predicate<String> pathPredicate, BiConsumer<String, byte[]> action) throws IOException {
        parent().handle(pathPredicate, action);
    }
    
    @Override
    default void copyTo(FileAccess output) throws IOException {
        parent().copyTo(output);
    }
    
    @Override
    default void copyTo(Predicate<String> pathPredicate, FileAccess output) throws IOException {
        parent().copyTo(pathPredicate, output);
    }
    
    @Override
    default byte[] getFile(String path) throws IOException {
        return parent().getFile(path);
    }
    
    @Override
    default byte[] asZipFile() throws IOException {
        return parent().asZipFile();
    }
    
    @Override
    default MemoryFileAccess remember() throws IOException {
        return parent().remember();
    }
}
