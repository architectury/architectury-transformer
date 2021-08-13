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

package dev.architectury.transformer;

import dev.architectury.transformer.agent.TransformerAgent;
import dev.architectury.transformer.handler.SimpleTransformerHandler;
import dev.architectury.transformer.handler.TinyRemapperPreparedTransformerHandler;
import dev.architectury.transformer.handler.TransformHandler;
import dev.architectury.transformer.input.*;
import dev.architectury.transformer.transformers.BuiltinProperties;
import dev.architectury.transformer.transformers.ClasspathProvider;
import dev.architectury.transformer.transformers.base.edit.SimpleTransformerContext;
import dev.architectury.transformer.transformers.base.edit.TransformerContext;
import dev.architectury.transformer.transformers.classpath.ReadClasspathProvider;
import dev.architectury.transformer.transformers.properties.TransformersReader;
import dev.architectury.transformer.util.HashUtils;
import dev.architectury.transformer.util.Logger;
import dev.architectury.transformer.util.TransformerPair;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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

import static dev.architectury.transformer.Transform.formatDuration;

public class TransformerRuntime {
    public static final String RUNTIME_TRANSFORM_CONFIG = "architectury.runtime.transformer";
    public static final String MAIN_CLASS = "architectury.main.class";
    public static final String PROPERTIES = "architectury.properties";
    public static final Set<File> TRANSFORM_FILES = new HashSet<>();
    public static final Map<String, Map.Entry<List<Transformer>, DirectoryOutputInterface>> CLASSES_TO_TRANSFORM = new HashMap<>();
    private static ReadClasspathProvider classpathProvider;
    
    private static boolean isDebugOutputEnabled() {
        return System.getProperty(BuiltinProperties.DEBUG_OUTPUT, "false").equals("true");
    }
    
    public static void main(String[] args) throws Throwable {
        Logger.info("Architectury Runtime " + TransformerRuntime.class.getPackage().getImplementationVersion());
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        applyProperties();
        
        // We start our journey of achieving hell
        Path configPath = Paths.get(System.getProperty(RUNTIME_TRANSFORM_CONFIG));
        String configText = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        List<PathWithTransformersEntry> toTransform = parsePathWithTransformersEntries(configText);
        
        UnaryOperator<String> stripLeadingSlash = Transform::stripLoadingSlash;
        AtomicInteger i = new AtomicInteger();
        Map<Path, DirectoryOutputInterface> debugOuts = new ConcurrentHashMap<>();
        for (PathWithTransformersEntry entry : toTransform) {
            DirectoryOutputInterface debugOut = isDebugOutputEnabled() ? debugOuts.computeIfAbsent(entry.getPath(), key -> {
                try {
                    Path file = Paths.get(System.getProperty(BuiltinProperties.LOCATION, System.getProperty("user.dir"))).resolve(".architectury-transformer/debug-" + entry.getPath().getFileName().toString() + "-" + (i.incrementAndGet()));
                    Files.createDirectories(file);
                    return DirectoryOutputInterface.of(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }) : null;
            TRANSFORM_FILES.add(entry.toFile().getAbsoluteFile());
            if (Files.isDirectory(entry.getPath())) {
                Files.walk(entry.getPath()).forEach(path -> {
                    CLASSES_TO_TRANSFORM.put(stripLeadingSlash.apply(entry.getPath().relativize(path).toString()), new AbstractMap.SimpleEntry<>(entry.getTransformers(), debugOut));
                });
            } else {
                try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(entry.getPath())) {
                    inputInterface.handle(path -> {
                        String key = stripLeadingSlash.apply(path);
                        CLASSES_TO_TRANSFORM.put(stripLeadingSlash.apply(path), new AbstractMap.SimpleEntry<>(entry.getTransformers(), debugOut));
                    });
                }
            }
        }
        List<Path> tmpJars = new ArrayList<>();
        classpathProvider = ReadClasspathProvider.of(ClasspathProvider.fromProperties().filter(path -> {
            File file = path.toFile().getAbsoluteFile();
            for (PathWithTransformersEntry path1 : toTransform) {
                if (Objects.equals(path1.toFile().getAbsoluteFile(), file)) {
                    return false;
                }
            }
            return true;
        }));
        doInstrumentationStuff();
        for (PathWithTransformersEntry entry : toTransform) {
            Map<String, String> classRedefineCache = new HashMap<>();
            DirectoryOutputInterface debugOut = debugOuts.get(entry.getPath());
            Path tmpJar = Files.createTempFile(null, ".jar");
            tmpJars.add(tmpJar);
            Files.deleteIfExists(tmpJar);
            Files.copy(entry.getPath(), tmpJar);
            try (OpenedOutputInterface outputInterface = OpenedOutputInterface.ofJar(tmpJar)) {
                try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(entry.getPath())) {
                    Logger.debug("Transforming " + entry.getTransformers().size() + " transformer(s) from " + entry.getPath().toString() + " to " + tmpJar + ": ");
                    for (Transformer transformer : entry.getTransformers()) {
                        Logger.debug(" - " + transformer.toString());
                    }
                    Transform.runTransformers(new SimpleTransformerContext(a -> {
                        argsList.addAll(Arrays.asList(a));
                        Logger.debug("Appended Launch Argument: " + Arrays.toString(a));
                    }, false, true, true), classpathProvider, entry.getPath().toString(), new AbstractOutputInterface(inputInterface) {
                        @Override
                        public boolean addFile(String path, byte[] bytes) throws IOException {
                            String s = stripLeadingSlash.apply(path);
                            if (s.endsWith(".class")) {
                                classRedefineCache.put(s.substring(0, s.length() - 6), HashUtils.sha256(bytes));
                            }
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
                        public void close() {
                        }
                    }, entry.getTransformers());
                }
            }
            
            tmpJar.toFile().deleteOnExit();
            populateAddUrl().accept(tmpJar.toUri().toURL());
            
            new PathModifyListener(entry.getPath(), path -> {
                try (OpenedOutputInterface outputInterface = OpenedOutputInterface.ofJar(tmpJar)) {
                    Thread.sleep(4000);
                    Logger.info("Detected File Modification at " + path.getFileName().toString());
                    Map<String, byte[]> redefine = new HashMap<>();
                    try (OpenedInputInterface inputInterface = OpenedInputInterface.ofJar(entry.getPath())) {
                        Logger.debug("Transforming " + entry.getTransformers().size() + " transformer(s) from " + entry.getPath().toString() + " to " + tmpJar + ": ");
                        for (Transformer transformer : entry.getTransformers()) {
                            Logger.debug(" - " + transformer.toString());
                        }
                        Transform.runTransformers(new SimpleTransformerContext(
                                $ -> {}, true, false, false
                        ), classpathProvider, entry.getPath().toString(), new AbstractOutputInterface(inputInterface) {
                            @Override
                            public boolean addFile(String path, byte[] bytes) throws IOException {
                                String s = stripLeadingSlash.apply(path);
                                if (outputInterface.addFile(s, bytes) && (debugOut == null || debugOut.addFile(s, bytes))) {
                                    if (path.endsWith(".class")) {
                                        s = s.substring(0, s.length() - 6);
                                        String sha256 = HashUtils.sha256(bytes);
                                        if (!Objects.equals(classRedefineCache.get(s), sha256)) {
                                            classRedefineCache.put(s, sha256);
                                            redefine.put(s, bytes);
                                        }
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
                                        String sha256 = HashUtils.sha256(bytes);
                                        if (!Objects.equals(classRedefineCache.get(s), sha256)) {
                                            classRedefineCache.put(s, sha256);
                                            redefine.put(s, bytes);
                                        }
                                    }
                                }
                                
                                return bytes;
                            }
                            
                            @Override
                            public String toString() {
                                return inputInterface.toString();
                            }
                            
                            @Override
                            public void close() {
                            }
                        }, entry.getTransformers());
                    }
                    if (TransformerAgent.getInstrumentation().isRedefineClassesSupported()) {
                        redefineClasses(redefine);
                    }
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
    
    private static List<PathWithTransformersEntry> parsePathWithTransformersEntries(String configText) throws IOException {
        Map<Path, List<TransformerPair>> map;
        try (TransformersReader reader = new TransformersReader(new StringReader(configText))) {
            map = reader.readAll();
        }
        return map.entrySet()
                .stream()
                .map(entry -> new PathWithTransformersEntry(entry.getKey(), entry.getValue().stream()
                        .map(TransformerPair::construct)
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }
    
    private static void redefineClasses(Map<String, byte[]> redefine) throws Exception {
        Class<?>[] allLoadedClasses = TransformerAgent.getInstrumentation().getAllLoadedClasses();
        List<ClassDefinition> definitions = new ArrayList<>();
        redefine.forEach((s, bytes) -> {
            String name = s.replace('/', '.');
            Iterator<Class<?>> iterator = Arrays.stream(allLoadedClasses)
                    .filter(a -> a.getClassLoader() != ClassLoader.getSystemClassLoader())
                    .filter(a -> Objects.equals(a.getName(), name))
                    .iterator();
            while (iterator.hasNext()) {
                Class<?> a = iterator.next();
                if (a.getClassLoader() == ClassLoader.getSystemClassLoader()) continue;
                Logger.debug("Redefining " + name);
                definitions.add(new ClassDefinition(a, bytes));
            }
        });
        if (!definitions.isEmpty()) {
            Transform.logTime(() -> {
                TransformerAgent.getInstrumentation().redefineClasses(definitions.toArray(new ClassDefinition[0]));
            }, "Redefined " + definitions.size() + " class(es)");
        }
    }
    
    private static void applyProperties() throws IOException {
        Path propertiesPath = Paths.get(System.getProperty(PROPERTIES));
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(Files.readAllBytes(propertiesPath)));
        properties.forEach((o, o2) -> System.setProperty((String) o, (String) o2));
    }
    
    private static void doInstrumentationStuff() {
        boolean prepare = true;
        TransformHandler handler;
        try {
            TransformerContext context = new SimpleTransformerContext(args -> {}, false, true, false);
            if (prepare) {
                handler = new TinyRemapperPreparedTransformerHandler(classpathProvider, context).asThreadLocked();
            } else {
                handler = new SimpleTransformerHandler(classpathProvider, context).asThreadLocked();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Instrumentation instrumentation = TransformerAgent.getInstrumentation();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
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
                                public void handle(Consumer<String> action) {
                                    action.accept(className + ".class");
                                }
                                
                                @Override
                                public void handle(BiConsumer<String, byte[]> action) {
                                    action.accept(className + ".class", classBytes.get());
                                }
                                
                                @Override
                                public boolean addFile(String path, byte[] bytes) {
                                    if (Transform.stripLoadingSlash(path).equals(className + ".class") && bytes != null) {
                                        classBytes.set(bytes);
                                        return true;
                                    }
                                    
                                    return false;
                                }
                                
                                @Override
                                public byte[] modifyFile(String path, UnaryOperator<byte[]> action) {
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
                                public void close() {
                                    
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
    
    private static class PathWithTransformersEntry {
        private final Path path;
        private final File file;
        private final List<Transformer> transformers;
        
        public PathWithTransformersEntry(Path path, List<Transformer> transformers) {
            this.path = path;
            this.file = path.toFile();
            this.transformers = transformers;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathWithTransformersEntry)) return false;
            PathWithTransformersEntry that = (PathWithTransformersEntry) o;
            return file.equals(that.file);
        }
        
        @Override
        public int hashCode() {
            return file.hashCode();
        }
        
        public Path getPath() {
            return path;
        }
        
        public File toFile() {
            return file;
        }
        
        public List<Transformer> getTransformers() {
            return transformers;
        }
    }
}
