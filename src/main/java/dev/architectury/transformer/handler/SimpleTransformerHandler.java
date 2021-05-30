/*
 * This file is licensed under the MIT License, part of architectury-transformer.
 * Copyright (c) 2020, 2021 architectury
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.architectury.transformer.handler;

import dev.architectury.tinyremapper.IMappingProvider;
import dev.architectury.tinyremapper.TinyRemapper;
import dev.architectury.transformer.Transformer;
import dev.architectury.transformer.input.OutputInterface;
import dev.architectury.transformer.transformers.base.AssetEditTransformer;
import dev.architectury.transformer.transformers.base.ClassEditTransformer;
import dev.architectury.transformer.transformers.base.TinyRemapperTransformer;
import dev.architectury.transformer.transformers.base.edit.TransformerContext;
import dev.architectury.transformer.transformers.classpath.ReadClasspathProvider;
import dev.architectury.transformer.util.Logger;
import dev.architectury.transformer.util.LoggerFilter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class SimpleTransformerHandler implements TransformHandler {
    protected ReadClasspathProvider classpath;
    protected TransformerContext context;
    protected boolean closed = false;
    
    public SimpleTransformerHandler(ReadClasspathProvider classpath, TransformerContext context) throws Exception {
        this.classpath = classpath;
        this.context = context;
    }
    
    @Override
    public void handle(String input, OutputInterface output, List<Transformer> transformers) throws Exception {
        if (closed) throw new IllegalStateException("Cannot transform when the handler is closed already!");
        Logger.debug("Remapping from " + input + " to " + output + " with " + transformers.size() + " transformer(s) on " + getClass().getName());
        Set<IMappingProvider> mappingProviders = new HashSet<>();
        Function<String, UnaryOperator<ClassNode>>[] classTransformer = new Function[]{null};
        
        for (Transformer transformer : transformers) {
            if (transformer instanceof ClassEditTransformer) {
                if (classTransformer[0] == null) {
                    classTransformer[0] = path -> node -> ((ClassEditTransformer) transformer).doEdit(path, node);
                } else {
                    Function<String, UnaryOperator<ClassNode>>[] tmp = new Function[]{classTransformer[0]};
                    classTransformer[0] = path -> node -> ((ClassEditTransformer) transformer).doEdit(path, tmp[0].apply(path).apply(node));
                }
            }
            if (transformer instanceof TinyRemapperTransformer) {
                mappingProviders.addAll(((TinyRemapperTransformer) transformer).collectMappings());
            }
        }
        
        if (mappingProviders.size() > 0) {
            Logger.debug("Remapping with " + mappingProviders.size() + " mapping provider(s):");
            for (IMappingProvider provider : mappingProviders) {
                Logger.debug(" - " + provider);
            }
            remapTR(mappingProviders, input, output);
        }
        
        if (classTransformer[0] != null) {
            output.modifyFiles(path -> path.endsWith(".class"), (path, bytes) -> {
                ClassReader reader = new ClassReader(bytes);
                if ((reader.getAccess() & Opcodes.ACC_MODULE) == 0) {
                    ClassNode node = new ClassNode(Opcodes.ASM8);
                    reader.accept(node, ClassReader.EXPAND_FRAMES);
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    classTransformer[0].apply(path).apply(node).accept(writer);
                    return writer.toByteArray();
                }
                return bytes;
            });
        }
        
        for (Transformer transformer : transformers) {
            if (transformer instanceof AssetEditTransformer) {
                try {
                    ((AssetEditTransformer) transformer).doEdit(context, output);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        }
    }
    
    private void remapTR(Set<IMappingProvider> mappingProviders, String input, OutputInterface output) throws Exception {
        TinyRemapper remapper = getRemapper(mappingProviders);
        
        LoggerFilter.replaceSystemOut();
        try {
            List<byte[]> classes = new ArrayList<>();
            output.handle((path, bytes) -> {
                if (path.endsWith(".class")) {
                    classes.add(bytes);
                }
            });
            remapper.readInputs(classes.toArray(new byte[][]{}));
            remapper.apply((path, bytes) -> {
                try {
                    output.addClass(path, bytes);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
            debugRemapper(remapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remap " + input + " to " + output, e);
        } finally {
            closeRemapper(remapper);
        }
    }
    
    private void debugRemapper(TinyRemapper remapper) throws Exception {
        Field classMapField = remapper.getClass().getDeclaredField("classMap");
        classMapField.setAccessible(true);
        Logger.debug("Remapping Classes:");
        ((Map<String, String>) classMapField.get(remapper)).forEach((from, to) -> {
            Logger.debug(from + " -> " + to);
        });
    }
    
    protected TinyRemapper getRemapper(Set<IMappingProvider> providers) throws Exception {
        TinyRemapper.Builder builder = TinyRemapper.newRemapper();
        builder.threads(Runtime.getRuntime().availableProcessors());
        for (IMappingProvider provider : providers) {
            builder.withMappings(provider);
        }
        builder.skipConflictsChecking(true);
        builder.logUnknownInvokeDynamic(false);
        TinyRemapper remapper = builder.build();
        
        remapper.readClassPath(classpath.provide());
        return remapper;
    }
    
    protected void closeRemapper(TinyRemapper remapper) throws Exception {
        remapper.finish();
    }
    
    @Override
    public void close() throws IOException {
        this.context = null;
        this.classpath = null;
        this.closed = true;
    }
}
