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

package me.shedaniel.architectury.transformer;

import me.shedaniel.architectury.transformer.handler.SimpleTransformerHandler;
import me.shedaniel.architectury.transformer.input.InputInterface;
import me.shedaniel.architectury.transformer.input.OpenedOutputInterface;
import me.shedaniel.architectury.transformer.input.OutputInterface;
import me.shedaniel.architectury.transformer.transformers.BuiltinProperties;
import me.shedaniel.architectury.transformer.transformers.ClasspathProvider;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import me.shedaniel.architectury.transformer.transformers.classpath.ReadClasspathProvider;
import me.shedaniel.architectury.transformer.util.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.zeroturnaround.zip.commons.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.util.concurrent.TimeUnit.*;

public class Transform {
    public static String getUniqueIdentifier() {
        return System.getProperty(BuiltinProperties.UNIQUE_IDENTIFIER);
    }
    
    public static boolean isInjectInjectables() {
        return System.getProperty(BuiltinProperties.INJECT_INJECTABLES, "true").equals("true");
    }
    
    public static String[] getClasspath() {
        return System.getProperty(BuiltinProperties.COMPILE_CLASSPATH, "true").split(File.pathSeparator);
    }
    
    public static void runTransformers(Path input, Path output, List<Transformer> transformers) throws Exception {
        TransformerContext context = new TransformerContext() {
            @Override
            public void appendArgument(String... args) {
                throw new IllegalStateException();
            }
            
            @Override
            public boolean canModifyAssets() {
                return true;
            }
            
            @Override
            public boolean canAppendArgument() {
                return false;
            }
            
            @Override
            public boolean canAddClasses() {
                return true;
            }
        };
        ClasspathProvider classpath = ClasspathProvider.fromProperties().filter(path -> {
            return !Objects.equals(input.toFile().getAbsoluteFile(), path.toFile().getAbsoluteFile());
        });
        Logger.debug("Transforming " + transformers.size() + " transformer(s) from " + input.toString() + " to " + output.toString() + ": ");
        for (Transformer transformer : transformers) {
            Logger.debug(" - " + transformer.toString());
        }
        logTime(() -> {
            if (Files.isDirectory(input)) {
                copyDirectory(input, output);
                try (OpenedOutputInterface outputInterface = OpenedOutputInterface.ofDirectory(output)) {
                    runTransformers(context, classpath, input.toString(), outputInterface, transformers);
                }
            } else {
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                try (OpenedOutputInterface outputInterface = OpenedOutputInterface.ofJar(output)) {
                    runTransformers(context, classpath, input.toString(), outputInterface, transformers);
                }
            }
        }, "Transformed jar with " + transformers.size() + " transformer(s)");
    }
    
    public static void runTransformers(TransformerContext context, ClasspathProvider classpath, InputInterface input, OutputInterface output, List<Transformer> transformers)
            throws Exception {
        runTransformers(context, ReadClasspathProvider.of(classpath), input, output, transformers);
    }
    
    public static void runTransformers(TransformerContext context, ClasspathProvider classpath, String input, OutputInterface output, List<Transformer> transformers)
            throws Exception {
        runTransformers(context, ReadClasspathProvider.of(classpath), input, output, transformers);
    }
    
    public static void runTransformers(TransformerContext context, ReadClasspathProvider classpath, InputInterface input, OutputInterface output, List<Transformer> transformers)
            throws Exception {
        runTransformers(context, classpath, input.toString(), output, transformers);
    }
    
    public static void runTransformers(TransformerContext context, ReadClasspathProvider classpath, String input, OutputInterface output, List<Transformer> transformers)
            throws Exception {
        try (SimpleTransformerHandler handler = new SimpleTransformerHandler(classpath, context)) {
            handler.handle(input, output, transformers);
        }
    }
    
    public static void logTime(DoThing doThing, String task) throws Exception {
        measureTime(doThing, (duration) -> {
            Logger.info(task + " in " + formatDuration(duration));
        });
    }
    
    public static void measureTime(DoThing doThing, Consumer<Duration> measured) throws Exception {
        long current = System.nanoTime();
        doThing.doThing();
        long finished = System.nanoTime();
        Duration duration = Duration.ofNanos(finished - current);
        measured.accept(duration);
    }
    
    public static String stripLoadingSlash(String string) {
        if (string.startsWith(File.separator)) {
            return string.substring(1);
        } else if (string.startsWith("/")) {
            return string.substring(1);
        }
        return string;
    }
    
    @FunctionalInterface
    public interface DoThing {
        void doThing() throws Exception;
    }
    
    /*
     * Copyright (C) 2008 The Guava Authors
     *
     * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
     * in compliance with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software distributed under the License
     * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
     * or implied. See the License for the specific language governing permissions and limitations under
     * the License.
     */
    public static String formatDuration(Duration duration) {
        long nanos = duration.toNanos();
        
        TimeUnit unit = chooseUnit(nanos);
        double value = (double) nanos / NANOSECONDS.convert(1, unit);
        
        return formatCompact4Digits(value) + " " + abbreviate(unit);
    }
    
    private static void copyDirectory(Path src, Path dest) throws IOException {
        if (Files.exists(dest)) {
            try (Stream<Path> walk = Files.walk(dest)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEachOrdered(sourcePath -> {
                try {
                    Files.copy(sourcePath, dest.resolve(src.relativize(sourcePath)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
    
    private static String formatCompact4Digits(double value) {
        return String.format(Locale.ROOT, "%.4g", value);
    }
    
    private static TimeUnit chooseUnit(long nanos) {
        if (DAYS.convert(nanos, NANOSECONDS) > 0) {
            return DAYS;
        }
        if (HOURS.convert(nanos, NANOSECONDS) > 0) {
            return HOURS;
        }
        if (MINUTES.convert(nanos, NANOSECONDS) > 0) {
            return MINUTES;
        }
        if (SECONDS.convert(nanos, NANOSECONDS) > 0) {
            return SECONDS;
        }
        if (MILLISECONDS.convert(nanos, NANOSECONDS) > 0) {
            return MILLISECONDS;
        }
        if (MICROSECONDS.convert(nanos, NANOSECONDS) > 0) {
            return MICROSECONDS;
        }
        return NANOSECONDS;
    }
    
    private static String abbreviate(TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return "ns";
            case MICROSECONDS:
                return "\u03bcs"; // μs
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "min";
            case HOURS:
                return "h";
            case DAYS:
                return "d";
            default:
                throw new AssertionError();
        }
    }
    
    public static void transform(Path input, Path output, ClassTransformer transformer) throws Exception {
        output.toFile().delete();
        
        if (!Files.exists(input)) {
            throw new FileNotFoundException(input.toString());
        }
        
        if (input.toFile().isDirectory()) {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(output))) {
                ClassAdder adder = (className, bytes) -> {
                    zipOutputStream.putNextEntry(new ZipEntry(className + ".class"));
                    zipOutputStream.write(bytes);
                    zipOutputStream.closeEntry();
                };
                
                for (Path path : Files.walk(input).toArray(Path[]::new)) {
                    byte[] allBytes = Files.readAllBytes(input);
                    ClassReader reader = new ClassReader(allBytes);
                    if ((reader.getAccess() & Opcodes.ACC_MODULE) == 0) {
                        ClassNode node = new ClassNode(Opcodes.ASM8);
                        reader.accept(node, ClassReader.EXPAND_FRAMES);
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        transformer.transform(node, adder).accept(writer);
                        allBytes = writer.toByteArray();
                    }
                    zipOutputStream.putNextEntry(new ZipEntry(path.relativize(input).toString()));
                    zipOutputStream.write(allBytes);
                    zipOutputStream.closeEntry();
                }
            }
        } else if (input.toFile().getAbsolutePath().endsWith(".class")) {
            byte[] allBytes = Files.readAllBytes(input);
            ClassReader reader = new ClassReader(allBytes);
            if ((reader.getAccess() & Opcodes.ACC_MODULE) == 0) {
                ClassNode node = new ClassNode(Opcodes.ASM8);
                reader.accept(node, ClassReader.EXPAND_FRAMES);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                transformer.transform(node, (name, bytes) -> {
                    File newClassFile = new File(output.toFile().getParentFile(), name + ".class");
                    newClassFile.delete();
                    newClassFile.getParentFile().mkdirs();
                    Files.write(newClassFile.toPath(), bytes, StandardOpenOption.CREATE);
                }).accept(writer);
                allBytes = writer.toByteArray();
            }
            Files.write(output, allBytes, StandardOpenOption.CREATE);
        } else {
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(output))) {
                try (ZipInputStream it = new ZipInputStream(Files.newInputStream(input))) {
                    ClassAdder adder = (className, bytes) -> {
                        zipOutputStream.putNextEntry(new ZipEntry(className + ".class"));
                        zipOutputStream.write(bytes);
                        zipOutputStream.closeEntry();
                    };
                    
                    while (true) {
                        ZipEntry entry = it.getNextEntry();
                        if (entry == null) break;
                        byte[] allBytes = IOUtils.toByteArray(it);
                        if (entry.getName().endsWith(".class")) {
                            ClassReader reader = new ClassReader(allBytes);
                            if ((reader.getAccess() & Opcodes.ACC_MODULE) == 0) {
                                ClassNode node = new ClassNode(Opcodes.ASM8);
                                reader.accept(node, ClassReader.EXPAND_FRAMES);
                                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                                transformer.transform(node, adder).accept(writer);
                                allBytes = writer.toByteArray();
                            }
                        }
                        zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                        zipOutputStream.write(allBytes);
                        zipOutputStream.closeEntry();
                    }
                }
            }
        }
    }
}