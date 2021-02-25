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

import me.shedaniel.architectury.transformer.agent.TransformerAgent;
import me.shedaniel.architectury.transformer.handler.SimpleTransformerHandler;
import me.shedaniel.architectury.transformer.handler.TinyRemapperPreparedTransformerHandler;
import me.shedaniel.architectury.transformer.handler.TransformHandler;
import me.shedaniel.architectury.transformer.input.*;
import me.shedaniel.architectury.transformer.transformers.BuiltinProperties;
import me.shedaniel.architectury.transformer.transformers.ClasspathProvider;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import me.shedaniel.architectury.transformer.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static me.shedaniel.architectury.transformer.Transform.formatDuration;

public class TransformerRuntime {
    public static final String RUNTIME_TRANSFORM_CONFIG = "architectury.runtime.transformer";
    public static final String MAIN_CLASS = "architectury.main.class";
    public static final String PROPERTIES = "architectury.properties";
    public static final Set<File> TRANSFORM_FILES = new HashSet<>();
    public static final Map<String, Map.Entry<List<Transformer>, DirectoryOutputInterface>> CLASSES_TO_TRANSFORM = new HashMap<>();
    
    private static boolean isDebugOutputEnabled() {
        return System.getProperty(BuiltinProperties.DEBUG_OUTPUT, "false").equals("true");
    }
    
    public static void main(String[] args) throws Throwable {
        Logger.info("Architectury Runtime " + TransformerRuntime.class.getPackage().getImplementationVersion());
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        Path propertiesPath = Paths.get(System.getProperty(PROPERTIES));
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(Files.readAllBytes(propertiesPath)));
        properties.forEach((o, o2) -> System.setProperty((String) o, (String) o2));
        
        doInstrumentationStuff();
        
        // We start our journey of achieving hell
        Path configPath = Paths.get(System.getProperty(RUNTIME_TRANSFORM_CONFIG));
        String configText = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        Map<Path, List<Transformer>> toTransform = Arrays.stream(configText.split(File.pathSeparator))
                .filter(s -> !s.trim().isEmpty())
                .map(s -> s.split("\\|"))
                .collect(Collectors.groupingBy(s -> Paths.get(s[0]),
                        Collectors.mapping(s -> {
                            try {
                                return (Transformer) Class.forName(s[1]).getConstructor().newInstance();
                            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        }, Collectors.toList())));
        
        UnaryOperator<String> stripLeadingSlash = Transform::stripLoadingSlash;
        AtomicInteger i = new AtomicInteger();
        Map<Path, DirectoryOutputInterface> debugOuts = new ConcurrentHashMap<>();
        for (Map.Entry<Path, List<Transformer>> entry : toTransform.entrySet()) {
            DirectoryOutputInterface debugOut = isDebugOutputEnabled() ? debugOuts.computeIfAbsent(entry.getKey(), key -> {
                try {
                    Path file = Paths.get(System.getProperty(BuiltinProperties.LOCATION, System.getProperty("user.dir"))).resolve(".architectury-transformer/debug-" + entry.getKey().getFileName().toString() + "-" + (i.incrementAndGet()));
                    Files.createDirectories(file);
                    return DirectoryOutputInterface.of(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }) : null;
            TRANSFORM_FILES.add(entry.getKey().toFile().getAbsoluteFile());
            if (Files.isDirectory(entry.getKey())) {
                Files.walk(entry.getKey()).forEach(path -> {
                    CLASSES_TO_TRANSFORM.put(stripLeadingSlash.apply(entry.getKey().relativize(path).toString()), new AbstractMap.SimpleEntry<>(entry.getValue(), debugOut));
                });
            } else {
                try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(entry.getKey())) {
                    inputInterface.handle(path -> {
                        String key = stripLeadingSlash.apply(path);
                        CLASSES_TO_TRANSFORM.put(stripLeadingSlash.apply(path), new AbstractMap.SimpleEntry<>(entry.getValue(), debugOut));
                    });
                }
            }
        }
        List<Path> tmpJars = new ArrayList<>();
        for (Map.Entry<Path, List<Transformer>> entry : toTransform.entrySet()) {
            DirectoryOutputInterface debugOut = debugOuts.get(entry.getKey());
            Path tmpJar = Files.createTempFile(null, ".jar");
            tmpJars.add(tmpJar);
            Files.deleteIfExists(tmpJar);
            Files.copy(entry.getKey(), tmpJar);
            try (OpenedOutputInterface outputInterface = OpenedOutputInterface.ofJar(tmpJar)) {
                try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(entry.getKey())) {
                    Logger.debug("Transforming " + entry.getValue().size() + " transformer(s) from " + entry.getKey().toString() + " to " + tmpJar.toString() + ": ");
                    for (Transformer transformer : entry.getValue()) {
                        Logger.debug(" - " + transformer.toString());
                    }
                    Transform.runTransformers(new TransformerContext() {
                        @Override
                        public void appendArgument(String... args) {
                            argsList.addAll(Arrays.asList(args));
                            Logger.debug("Appended Launch Argument: " + Arrays.toString(args));
                        }
                        
                        @Override
                        public boolean canModifyAssets() {
                            return false;
                        }
                        
                        @Override
                        public boolean canAppendArgument() {
                            return true;
                        }
                        
                        @Override
                        public boolean canAddClasses() {
                            return true;
                        }
                    }, ClasspathProvider.fromProperties().filter(path -> {
                        return !Objects.equals(entry.getKey().toFile().getAbsoluteFile(), path.toFile().getAbsoluteFile());
                    }), inputInterface, new AbstractOutputInterface(inputInterface) {
                        @Override
                        public boolean addFile(String path, byte[] bytes) throws IOException {
                            String s = stripLeadingSlash.apply(path);
                            return outputInterface.addFile(s, bytes) && (debugOut == null || debugOut.addFile(s, bytes));
                        }
                        
                        @Override
                        public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
                            byte[] bytes = outputInterface.modifyFile(stripLeadingSlash.apply(path), action);
                            
                            if (debugOut != null && bytes != null) {
                                debugOut.addFile(stripLeadingSlash.apply(path), bytes);
                            }
                            
                            return bytes;
                        }
    
                        @Override
                        public String toString() {
                            return outputInterface.toString();
                        }
    
                        @Override
                        public void close() throws IOException {
                            
                        }
                    }, entry.getValue());
                }
            }
            
            tmpJar.toFile().deleteOnExit();
            populateAddUrl().accept(tmpJar.toUri().toURL());
            
            new PathModifyListener(entry.getKey(), path -> {
                try (OpenedOutputInterface outputInterface = OpenedOutputInterface.ofJar(tmpJar)) {
                    Thread.sleep(4000);
                    Logger.info("Detected File Modification at " + path.getFileName().toString());
                    Map<String, byte[]> redefine = new HashMap<>();
                    try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(entry.getKey())) {
                        Logger.debug("Transforming " + entry.getValue().size() + " transformer(s) from " + entry.getKey().toString() + " to " + tmpJar.toString() + ": ");
                        for (Transformer transformer : entry.getValue()) {
                            Logger.debug(" - " + transformer.toString());
                        }
                        Transform.runTransformers(new TransformerContext() {
                            @Override
                            public void appendArgument(String... args) {
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
                                return false;
                            }
                        }, ClasspathProvider.fromProperties().filter(classpathPath -> {
                            return !Objects.equals(entry.getKey().toFile().getAbsoluteFile(), classpathPath.toFile().getAbsoluteFile());
                        }), inputInterface, new AbstractOutputInterface(inputInterface) {
                            @Override
                            public boolean addFile(String path, byte[] bytes) throws IOException {
                                String s = stripLeadingSlash.apply(path);
                                if (outputInterface.addFile(s, bytes) && (debugOut == null || debugOut.addFile(s, bytes))) {
                                    if (path.endsWith(".class")) {
                                        s = s.substring(0, s.length() - 6);
                                        redefine.put(s, bytes);
                                    }
    
                                    return true;
                                }
                                return false;
                            }
                            
                            @Override
                            public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
                                byte[] bytes = outputInterface.modifyFile(stripLeadingSlash.apply(path), action);
                                
                                if (bytes != null) {
                                    if (debugOut != null) {
                                        debugOut.addFile(stripLeadingSlash.apply(path), bytes);
                                    }
                                    
                                    if (path.endsWith(".class")) {
                                        String s = stripLeadingSlash.apply(path);
                                        s = s.substring(0, s.length() - 6);
                                        redefine.put(s, bytes);
                                    }
                                }
                                
                                return bytes;
                            }
    
                            @Override
                            public String toString() {
                                return inputInterface.toString();
                            }
                            
                            @Override
                            public void close() throws IOException {
                                
                            }
                        }, entry.getValue());
                    }
                    redefine.forEach((s, bytes) -> {
                        try {
                            if (TransformerAgent.getInstrumentation().isRedefineClassesSupported()) {
                                String name = s.replace('/', '.');
                                Iterator<Class> iterator = Arrays.stream(TransformerAgent.getInstrumentation().getAllLoadedClasses())
                                        .filter(a -> a.getClassLoader() != ClassLoader.getSystemClassLoader())
                                        .filter(a -> Objects.equals(a.getName(), name))
                                        .iterator();
                                for (Iterator<Class> it = iterator; it.hasNext(); ) {
                                    Class a = it.next();
                                    Logger.info("Redefining " + s);
                                    TransformerAgent.getInstrumentation().redefineClasses(new ClassDefinition(a, bytes));
                                }
                            }
                        } catch (ClassNotFoundException | UnmodifiableClassException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            });
        }
        
        List<String> cp = new ArrayList<>(Arrays.asList(System.getProperty("java.class.path", "").split(File.pathSeparator)));
        for (Path tmpJar : tmpJars) {
            cp.add(tmpJar.toAbsolutePath().toString());
        }
        System.setProperty("java.class.path", String.join(File.pathSeparator, cp));
        
        Path mainClassPath = Paths.get(System.getProperty(MAIN_CLASS));
        String mainClass = new String(Files.readAllBytes(mainClassPath), StandardCharsets.UTF_8);
        MethodHandle handle = MethodHandles.publicLookup().findStatic(Class.forName(mainClass), "main", MethodType.methodType(void.class, String[].class));
        handle.invokeExact((String[]) argsList.toArray(new String[0]));
    }
    
    private static void doInstrumentationStuff() {
        boolean prepare = true;
        TransformHandler handler;
        try {
            ClasspathProvider classpath = ClasspathProvider.fromProperties().filter(path -> {
                return !TRANSFORM_FILES.contains(path.toFile().getAbsoluteFile());
            });
            TransformerContext context = new TransformerContext() {
                @Override
                public void appendArgument(String... args) {
                }
                
                @Override
                public boolean canModifyAssets() {
                    return false;
                }
                
                @Override
                public boolean canAppendArgument() {
                    return true;
                }
                
                @Override
                public boolean canAddClasses() {
                    return false;
                }
            };
            if (prepare) {
                handler = new TinyRemapperPreparedTransformerHandler(classpath, context).asThreadLocked();
            } else {
                handler = new SimpleTransformerHandler(classpath, context).asThreadLocked();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Instrumentation instrumentation = TransformerAgent.getInstrumentation();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
                    throws IllegalClassFormatException {
                if (loader == ClassLoader.getSystemClassLoader()) return classfileBuffer;
                AtomicReference<byte[]> classBytes = new AtomicReference<>(classfileBuffer);
                Map.Entry<List<Transformer>, DirectoryOutputInterface> transformers = CLASSES_TO_TRANSFORM.get(className + ".class");
                if (transformers != null) {
                    try {
                        Transform.measureTime(() -> {
                            handler.handle(className + ".class", new OutputInterface() {
                                @Override
                                public boolean isClosed() {
                                    return false;
                                }
    
                                @Override
                                public void handle(Consumer<String> action) throws IOException {
                                    action.accept(className + ".class");
                                }
    
                                @Override
                                public void handle(BiConsumer<String, byte[]> action) throws IOException {
                                    action.accept(className + ".class", classBytes.get());
                                }
                                
                                @Override
                                public boolean addFile(String path, byte[] bytes) throws IOException {
                                    if (Transform.stripLoadingSlash(path).equals(className + ".class") && bytes != null) {
                                        classBytes.set(bytes);
                                        return true;
                                    }
                                    
                                    return false;
                                }
                                
                                @Override
                                public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
                                    if (Transform.stripLoadingSlash(path).equals(className + ".class")) {
                                        classBytes.set(action.apply(classBytes.get()));
                                        return classBytes.get();
                                    }
                                    
                                    return null;
                                }
    
                                @Override
                                public String toString() {
                                    return className + ".class";
                                }
                                
                                @Override
                                public void close() throws IOException {
                                    
                                }
                            }, transformers.getKey());
                        }, duration -> {
                            Logger.debug("Transformed " + className + " in " + formatDuration(duration));
                        });
                        if (transformers.getValue() != null) {
                            transformers.getValue().addFile(className + ".class", classBytes.get());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return classBytes.get();
            }
        }, instrumentation.isRedefineClassesSupported());
    }
    
    private static Consumer<URL> populateAddUrl() {
        // Java 8, we can just add via reflection
        if (ClassLoader.getSystemClassLoader() instanceof URLClassLoader) {
            return url -> {
                try {
                    Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    method.setAccessible(true);
                    method.invoke(ClassLoader.getSystemClassLoader(), url);
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            };
        }
        // Java 9 and above
        return url -> {
            try {
                TransformerAgent.getInstrumentation().appendToSystemClassLoaderSearch(new JarFile(url.getFile()));
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        };
    }
}
