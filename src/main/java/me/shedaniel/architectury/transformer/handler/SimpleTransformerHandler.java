/*
 * This file is licensed under the MIT License, part of architectury-transformer.
 * Copyright (c) 2020, 2021 shedaniel
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

package me.shedaniel.architectury.transformer.handler;

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.input.OpenedOutputInterface;
import me.shedaniel.architectury.transformer.input.OutputInterface;
import me.shedaniel.architectury.transformer.transformers.ClasspathProvider;
import me.shedaniel.architectury.transformer.transformers.base.AssetEditTransformer;
import me.shedaniel.architectury.transformer.transformers.base.ClassEditTransformer;
import me.shedaniel.architectury.transformer.transformers.base.TinyRemapperTransformer;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import me.shedaniel.architectury.transformer.util.Logger;
import me.shedaniel.architectury.transformer.util.LoggerFilter;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class SimpleTransformerHandler implements TransformHandler {
    protected ClasspathProvider classpath;
    protected TransformerContext context;
    protected boolean closed = false;
    
    public SimpleTransformerHandler(ClasspathProvider classpath, TransformerContext context) throws Exception {
        this.classpath = classpath.logging();
        this.context = context;
    }
    
    @Override
    public void handle(String input, OutputInterface output, List<Transformer> transformers) throws Exception {
        if (closed) throw new IllegalStateException("Cannot transform when the handler is closed already!");
        Logger.debug("Remapping from " + input + " to " + output + " with " + transformers.size() + " transformer(s) on " + getClass().getName());
        List<IMappingProvider> mappingProviders = new ArrayList<>();
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
    
    private void remapTR(List<IMappingProvider> mappingProviders, String input, OutputInterface output) throws Exception {
        TinyRemapper remapper = getRemapper(mappingProviders);
        
        Path inputTmp = copyClassesToTmpJar(output);
        Path outputTmp = Files.createTempFile(null, ".jar");
        inputTmp.toFile().deleteOnExit();
        outputTmp.toFile().deleteOnExit();
        Files.deleteIfExists(outputTmp);
        
        LoggerFilter.replaceSystemOut();
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputTmp).build()) {
            outputConsumer.addNonClassFiles(inputTmp, NonClassCopyMode.UNCHANGED, null);
            remapper.readInputs(inputTmp);
            remapper.apply(outputConsumer);
            debugRemapper(remapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remap " + input + " to " + output, e);
        } finally {
            try (OpenedOutputInterface outputTmpInterface = OpenedOutputInterface.ofJar(outputTmp)) {
                outputTmpInterface.copyTo(output);
            } finally {
                closeRemapper(remapper);
                
                Files.deleteIfExists(inputTmp);
                Files.deleteIfExists(outputTmp);
            }
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
    
    private static Path copyClassesToTmpJar(OutputInterface output) throws IOException {
        Path tmpJar = Files.createTempFile(null, ".jar");
        Files.deleteIfExists(tmpJar);
        tmpJar.toFile().deleteOnExit();
        try (OpenedOutputInterface outputInterface = OpenedOutputInterface.ofJar(tmpJar)) {
            output.copyTo(path -> path.endsWith(".class"), outputInterface);
        }
        return tmpJar;
    }
    
    protected TinyRemapper getRemapper(List<IMappingProvider> providers) throws Exception {
        TinyRemapper.Builder builder = TinyRemapper.newRemapper();
        for (IMappingProvider provider : providers) {
            builder.withMappings(provider);
        }
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
