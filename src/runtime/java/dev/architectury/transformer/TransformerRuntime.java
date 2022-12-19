/*
 * This file is licensed under the MIT License, part of architectury-transformer.
 * Copyright (c) 2020, 2021, 2022 architectury
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
import dev.architectury.transformer.input.DirectoryFileAccess;
import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.input.MemoryFileAccess;
import dev.architectury.transformer.input.OpenedFileAccess;
import dev.architectury.transformer.transformers.BuiltinProperties;
import dev.architectury.transformer.transformers.ClasspathProvider;
import dev.architectury.transformer.transformers.base.edit.SimpleTransformerContext;
import dev.architectury.transformer.transformers.base.edit.TransformerContext;
import dev.architectury.transformer.transformers.classpath.ReadClasspathProvider;
import dev.architectury.transformer.transformers.properties.TransformersReader;
import dev.architectury.transformer.util.Logger;
import dev.architectury.transformer.util.TransformerPair;

import java.io.*;
import java.lang.instrument.ClassDefinition;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class TransformerRuntime {
    public static final String RUNTIME_TRANSFORM_CONFIG = "architectury.runtime.transformer";
    public static final String MAIN_CLASS = "architectury.main.class";
    public static final String PROPERTIES = "architectury.properties";
    public static final Set<File> TRANSFORM_FILES = new HashSet<>();
    public static final Map<String, ToTransformData> CLASSES_TO_TRANSFORM = new HashMap<>();
    private static ReadClasspathProvider classpathProvider;
    
    private static boolean isDebugOutputEnabled() {
        return System.getProperty(BuiltinProperties.DEBUG_OUTPUT, "false").equals("true");
    }
    
    public static class ToTransformData {
        private final List<Transformer> transformers;
        private final FileAccess originalSource;
        private final FileAccess debugOut;
        
        public ToTransformData(List<Transformer> transformers, FileAccess originalSource, FileAccess debugOut) {
            this.transformers = transformers;
            this.originalSource = originalSource;
            this.debugOut = debugOut;
        }
        
        public List<Transformer> getTransformers() {
            return transformers;
        }
        
        public FileAccess getOriginalSource() {
            return originalSource;
        }
        
        public FileAccess getDebugOut() {
            return debugOut;
        }
    }
    
    public static void main(String[] args) throws Throwable {
        Logger.info("Architectury Runtime " + TransformerRuntime.class.getPackage().getImplementationVersion());
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        applyProperties();
        
        // We start our journey of achieving hell
        Path configPath = Paths.get(System.getProperty(RUNTIME_TRANSFORM_CONFIG));
        String configText = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        List<PathWithTransformersEntry> toTransform = parsePathWithTransformersEntries(configText);
        
        AtomicInteger i = new AtomicInteger();
        Map<Path, DirectoryFileAccess> debugOuts = new ConcurrentHashMap<>();
        for (PathWithTransformersEntry entry : toTransform) {
            DirectoryFileAccess debugOut = isDebugOutputEnabled() ? debugOuts.computeIfAbsent(entry.getPath(), key -> {
                try {
                    Path file = Paths.get(System.getProperty(BuiltinProperties.LOCATION, System.getProperty("user.dir"))).resolve(".architectury-transformer/debug-" + entry.getPath().getFileName().toString() + "-" + (i.incrementAndGet()));
                    Files.createDirectories(file);
                    return DirectoryFileAccess.of(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }) : null;
            TRANSFORM_FILES.add(entry.toFile().getAbsoluteFile());
            if (Files.isDirectory(entry.getPath())) {
                try (OpenedFileAccess outputInterface = OpenedFileAccess.ofDirectory(entry.getPath())) {
                    MemoryFileAccess remember = outputInterface.remember();
                    outputInterface.handle(path -> {
                        String key = Transform.trimSlashes(path);
                        CLASSES_TO_TRANSFORM.put(key, new ToTransformData(entry.getTransformers(), remember, debugOut));
                    });
                }
            } else {
                try (OpenedFileAccess outputInterface = OpenedFileAccess.ofJar(entry.getPath())) {
                    MemoryFileAccess remember = outputInterface.remember();
                    outputInterface.handle(path -> {
                        String key = Transform.trimSlashes(path);
                        CLASSES_TO_TRANSFORM.put(key, new ToTransformData(entry.getTransformers(), remember, debugOut));
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
            DirectoryFileAccess debugOut = debugOuts.get(entry.getPath());
            Path tmpJar = Files.createTempFile(null, ".jar");
            tmpJars.add(tmpJar);
            Files.deleteIfExists(tmpJar);
            try (OpenedFileAccess outputInterface = OpenedFileAccess.ofJar(tmpJar)) {
                try (OpenedFileAccess og = OpenedFileAccess.ofJar(entry.getPath())) {
                    og.copyTo(outputInterface);
                }
                Logger.debug("Transforming " + entry.getTransformers().size() + " transformer(s) from " + entry.getPath().toString() + " to " + tmpJar + ": ");
                for (Transformer transformer : entry.getTransformers()) {
                    Logger.debug(" - " + transformer.toString());
                }
                Transform.runTransformers(new SimpleTransformerContext(a -> {
                    argsList.addAll(Arrays.asList(a));
                    Logger.debug("Appended Launch Argument: " + Arrays.toString(a));
                }, false, true, true), classpathProvider, entry.getPath().toString(), new RuntimeFileAccess(classRedefineCache, outputInterface, debugOut), entry.getTransformers());
            }
            
            tmpJar.toFile().deleteOnExit();
            populateAddUrl().accept(tmpJar.toUri().toURL());
            
            new PathModifyListener(entry.getPath(), path -> {
                try {
                    try (OpenedFileAccess outputInterface = OpenedFileAccess.ofJar(tmpJar)) {
                        Thread.sleep(4000);
                        if (!System.getProperty("os.name").startsWith("Windows")) {
                            Files.deleteIfExists(tmpJar);
                        }
                        try (OpenedFileAccess og = OpenedFileAccess.ofJar(entry.getPath())) {
                            og.copyTo(outputInterface);
                        }
                        Logger.info("Detected File Modification at " + path.getFileName().toString());
                        Map<String, byte[]> redefine = new HashMap<>();
                        Logger.debug("Transforming " + entry.getTransformers().size() + " transformer(s) from " + entry.getPath().toString() + " to " + tmpJar + ": ");
                        for (Transformer transformer : entry.getTransformers()) {
                            Logger.debug(" - " + transformer.toString());
                        }
                        Map<String, String> thisClassRedefineCache = new HashMap<>(classRedefineCache);
                        Transform.runTransformers(new SimpleTransformerContext(
                                $ -> {}, true, false, false
                        ), classpathProvider, entry.getPath().toString(), new RuntimeReloadFileAccess(classRedefineCache, thisClassRedefineCache, redefine, outputInterface), entry.getTransformers());
                        classRedefineCache.putAll(thisClassRedefineCache);
                        if (TransformerAgent.getInstrumentation().isRedefineClassesSupported()) {
                            if (debugOut != null) {
                                for (Map.Entry<String, byte[]> redefineEntry : redefine.entrySet()) {
                                    debugOut.modifyFile(redefineEntry.getKey() + ".class", redefineEntry.getValue());
                                }
                            }
                            redefineClasses(entry.getPath().toString(), redefine);
                        }
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
    
    private static void redefineClasses(String input, Map<String, byte[]> redefine) throws Exception {
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
            }, "Redefined " + definitions.size() + " class(es) from " + input);
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
                handler = new TinyRemapperPreparedTransformerHandler(classpathProvider, context, false).asThreadLocked();
            } else {
                handler = new SimpleTransformerHandler(classpathProvider, context, false).asThreadLocked();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Instrumentation instrumentation = TransformerAgent.getInstrumentation();
        instrumentation.addTransformer(new ClassTransformerFileAccess(handler, CLASSES_TO_TRANSFORM::get), instrumentation.isRedefineClassesSupported());
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
                TransformerAgent.getInstrumentation().appendToSystemClassLoaderSearch(new JarFile(new File(url.toURI())));
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
