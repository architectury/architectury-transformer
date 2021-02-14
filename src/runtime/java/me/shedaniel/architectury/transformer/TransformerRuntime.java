package me.shedaniel.architectury.transformer;

import me.shedaniel.architectury.transformer.agent.TransformerAgent;
import me.shedaniel.architectury.transformer.handler.TinyRemapperPreparedTransformerHandler;
import me.shedaniel.architectury.transformer.handler.TransformHandler;
import me.shedaniel.architectury.transformer.input.InputInterface;
import me.shedaniel.architectury.transformer.input.JarInputInterface;
import me.shedaniel.architectury.transformer.input.JarOutputInterface;
import me.shedaniel.architectury.transformer.input.OutputInterface;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
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
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;
import java.util.*;
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
    public static final Map<String, List<Transformer>> CLASSES_TO_TRANSFORM = new HashMap<>();
    
    public static void main(String[] args) throws Throwable {
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        doInstrumentationStuff();
        Path propertiesPath = Paths.get(System.getProperty(PROPERTIES));
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(Files.readAllBytes(propertiesPath)));
        properties.forEach((o, o2) -> System.setProperty((String) o, (String) o2));
        // We start our journey of achieving hell
        Path configPath = Paths.get(System.getProperty(RUNTIME_TRANSFORM_CONFIG));
        String configText = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        Map<Path, List<Transformer>> toTransform = Arrays.stream(configText.split(File.pathSeparator))
                .map(s -> s.split("\\|"))
                .collect(Collectors.groupingBy(s -> Paths.get(s[0]),
                        Collectors.mapping(s -> {
                            try {
                                return (Transformer) Class.forName(s[1]).getConstructor().newInstance();
                            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        }, Collectors.toList())));
        
        UnaryOperator<String> stripLeadingSlash = s -> {
            if (s.startsWith(File.separator)) {
                return s.substring(1);
            } else if (s.startsWith("/")) {
                return s.substring(1);
            }
            return s;
        };
        for (Map.Entry<Path, List<Transformer>> entry : toTransform.entrySet()) {
            if (Files.isDirectory(entry.getKey())) {
                Files.walk(entry.getKey()).forEach(path -> {
                    CLASSES_TO_TRANSFORM.put(stripLeadingSlash.apply(entry.getKey().relativize(path).toString()), entry.getValue());
                });
            } else {
                try (JarInputInterface inputInterface = new JarInputInterface(entry.getKey())) {
                    inputInterface.handle((path, bytes) -> {
                        String key = stripLeadingSlash.apply(path);
                        CLASSES_TO_TRANSFORM.put(stripLeadingSlash.apply(path), entry.getValue());
                    });
                }
            }
        }
        List<Path> tmpJars = new ArrayList<>();
        for (Map.Entry<Path, List<Transformer>> entry : toTransform.entrySet()) {
            Path tmpJar = Files.createTempFile(null, ".jar");
            tmpJars.add(tmpJar);
            Files.deleteIfExists(tmpJar);
            Files.copy(entry.getKey(), tmpJar);
            try (JarOutputInterface outputInterface = new JarOutputInterface(tmpJar)) {
                try (JarInputInterface inputInterface = new JarInputInterface(entry.getKey())) {
                    Transform.runTransformers(new TransformerContext() {
                        @Override
                        public void appendArgument(String... args) {
                            argsList.addAll(Arrays.asList(args));
                            System.out.println("[Architectury Transformer Runtime] Appended Launch Argument: " + Arrays.toString(args));
                        }
        
                        @Override
                        public boolean canModifyAssets() {
                            return false;
                        }
        
                        @Override
                        public boolean canAppendArgument() {
                            return true;
                        }
                    }, inputInterface, new OutputInterface() {
                        @Override
                        public void addFile(String path, byte[] bytes) throws IOException {
                            outputInterface.addFile(stripLeadingSlash.apply(path), bytes);
                        }
        
                        @Override
                        public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
                            outputInterface.modifyFile(stripLeadingSlash.apply(path), action);
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
                try (JarOutputInterface outputInterface = new JarOutputInterface(tmpJar)) {
                    Thread.sleep(4000);
                    System.out.println("[Architectury Transformer Runtime] Detected File Modification at " + path.getFileName().toString());
                    try (JarInputInterface inputInterface = new JarInputInterface(entry.getKey())) {
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
                        }, inputInterface, new OutputInterface() {
                            @Override
                            public void addFile(String path, byte[] bytes) throws IOException {
                                if (path.endsWith(".class")) return;
                                outputInterface.addFile(stripLeadingSlash.apply(path), bytes);
                            }
        
                            @Override
                            public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
                                if (path.endsWith(".class")) return;
                                outputInterface.modifyFile(stripLeadingSlash.apply(path), action);
                            }
        
                            @Override
                            public void close() throws IOException {
            
                            }
                        }, entry.getValue());
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
    
    private static void doInstrumentationStuff() {
        TransformHandler handler;
        try {
            handler = new TinyRemapperPreparedTransformerHandler(new TransformerContext() {
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
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Instrumentation instrumentation = TransformerAgent.getInstrumentation();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
                    throws IllegalClassFormatException {
                if (loader == ClassLoader.getSystemClassLoader()) return classfileBuffer;
                byte[][] classBytes = {classfileBuffer};
                List<Transformer> transformers = CLASSES_TO_TRANSFORM.get(className + ".class");
                if (transformers != null) {
                    try {
                        Transform.measureTime(() -> {
                            handler.handle(new InputInterface() {
                                @Override
                                public void handle(BiConsumer<String, byte[]> action) throws IOException {
                                    action.accept(className + ".class", classBytes[0]);
                                }
                                
                                @Override
                                public void close() throws IOException {
                                    
                                }
                            }, new OutputInterface() {
                                @Override
                                public void addFile(String path, byte[] bytes) throws IOException {
                                    if (path.equals(className + ".class")) {
                                        classBytes[0] = bytes;
                                    }
                                }
                                
                                @Override
                                public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
                                    if (path.equals(className + ".class")) {
                                        classBytes[0] = action.apply(classBytes[0]);
                                    }
                                }
                                
                                @Override
                                public void close() throws IOException {
                                    
                                }
                            }, transformers);
                        }, duration -> {
                            System.out.println("[Architectury Transformer Runtime] Transformed " + className + " in " + formatDuration(duration));
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return classBytes[0];
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
    
    private static File createTmpAgentJar() throws IOException {
        File tmpAgentJar = File.createTempFile("architectury-agent", ".jar");
        tmpAgentJar.deleteOnExit();
        try (InputStream stream = TransformerRuntime.class.getResourceAsStream("/agent/injection.jar")) {
            Files.copy(stream, tmpAgentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tmpAgentJar;
    }
}
