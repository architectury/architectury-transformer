package dev.architectury.transformer.input;

import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface ForwardingFileAccess extends FileAccess, ForwardingFileView {
    @Override
    FileAccess parent() throws IOException;
    
    @Override
    default boolean addFile(String path, byte[] bytes) throws IOException {
        return parent().addFile(path, bytes);
    }
    
    @Override
    default byte[] modifyFile(String path, byte[] bytes) throws IOException {
        return parent().modifyFile(path, bytes);
    }
    
    @Override
    default byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        return parent().modifyFile(path, action);
    }
    
    @Override
    default boolean deleteFile(String path) throws IOException {
        return parent().deleteFile(path);
    }
    
    @Override
    default void modifyFiles(Predicate<String> pathPredicate, BiFunction<String, byte[], byte[]> action) throws IOException {
        parent().modifyFiles(pathPredicate, action);
    }
    
    @Override
    default void modifyFiles(BiFunction<String, byte[], byte[]> action) throws IOException {
        parent().modifyFiles(action);
    }
    
    @Override
    default boolean addFile(String path, String text) throws IOException {
        return parent().addFile(path, text);
    }
    
    @Override
    default boolean addClass(String path, byte[] bytes) throws IOException {
        return parent().addClass(path, bytes);
    }
    
    @Override
    default void deleteFiles(BiPredicate<String, byte[]> filePredicate) throws IOException {
        parent().deleteFiles(filePredicate);
    }
    
    @Override
    default void deleteFiles(Predicate<String> pathPredicate) throws IOException {
        parent().deleteFiles(pathPredicate);
    }
    
    @Override
    default boolean deleteClass(String path) throws IOException {
        return parent().deleteClass(path);
    }
    
    @Override
    default byte[] getFile(String path) throws IOException {
        return parent().getFile(path);
    }
}
