package me.shedaniel.architectury.transformer.transformers.base.edit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

public interface AssetEditSink {
    void handle(BiConsumer<String, byte[]> action) throws IOException;
    
    void addFile(String path, byte[] bytes) throws IOException;
    
    /**
     * Requires {@link TransformerContext#canModifyAssets()}
     */
    void transformFile(String path, UnaryOperator<byte[]> transformer) throws IOException;
    
    void dangerouslyTransformFile(String path, UnaryOperator<byte[]> transformer) throws IOException;
    
    default void addFile(String path, String text) throws IOException {
        addFile(path, text.getBytes(StandardCharsets.UTF_8));
    }
    
    default void addClass(String path, byte[] bytes) throws IOException {
        addFile(path + ".class", bytes);
    }
}
