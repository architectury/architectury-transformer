package me.shedaniel.architectury.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.zeroturnaround.zip.commons.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.util.concurrent.TimeUnit.*;

public class Transform {
    public static void runTransformers(Path input, Path output, List<Transformer> transformers) throws IOException {
        List<Path> taskOutputs = new ArrayList<>();
        for (int i = 0; i < transformers.size(); i++) {
            taskOutputs.add(Files.createTempFile("architectury-plugin", "intermediate-" + i + ".jar"));
        }
        
        for (int index = 0; index < transformers.size(); index++) {
            Transformer transformer = transformers.get(index);
            Path i = index == 0 ? input : taskOutputs.get(index - 1);
            Path o = taskOutputs.get(index);
            
            Files.deleteIfExists(o);
            if (o.getParent() != null) Files.createDirectories(o.getParent());
            
            try {
                boolean skipped = false;
                long current = System.nanoTime();
                try {
                    transformer.transform(i, o);
                } catch (TransformerStepSkipped ignored) {
                    skipped = true;
                }
                if (index != 0) {
                    Files.deleteIfExists(i);
                }
                long finished = System.nanoTime();
                Duration duration = Duration.ofNanos(finished - current);
                if (skipped) {
                    System.out.println(":skipped transforming step " + (index + 1) + "/" + transformers.size() + " [" + transformer.getClass().getSimpleName() + "] in " + formatDuration(duration));
                } else {
                    System.out.println(":finished transforming step " + (index + 1) + "/" + transformers.size() + " [" + transformer.getClass().getSimpleName() + "] in "+ formatDuration(duration));
                }
            } catch (Throwable t) {
                throw new RuntimeException("Failed transformer step " + (index + 1) + "/" + transformers.size() + " [" + transformer.getClass().getSimpleName() + "]", t);
            }
        }
        
        Files.move(taskOutputs.get(taskOutputs.size() - 1), output, StandardCopyOption.REPLACE_EXISTING);
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
        
        if (input.toFile().getAbsolutePath().endsWith(".class")) {
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