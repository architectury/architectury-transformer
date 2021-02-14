package me.shedaniel.architectury.transformer;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
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
import java.lang.management.ManagementFactory;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static me.shedaniel.architectury.transformer.Transform.formatDuration;

public class TransformerRuntime {
    public static final String RUNTIME_TRANSFORM_CONFIG = "architectury.runtime.transformer";
    public static final String MAIN_CLASS = "architectury.main.class";
    public static final String PROPERTIES = "architectury.properties";
    public static final Map<String, List<Transformer>> CLASSES_TO_TRANSFORM = new HashMap<>();
    
    public static void main(String[] args) throws Throwable {
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        injectAgent();
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
        
        Path tmpJar = Files.createTempFile(null, ".jar");
        Files.deleteIfExists(tmpJar);
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
                    String key = stripLeadingSlash.apply(entry.getKey().relativize(path).toString());
                    System.out.println("[Architectury Transformer Runtime] Mark file as transformable: " + key);
                    CLASSES_TO_TRANSFORM.put(key, entry.getValue());
                });
            } else {
                try (JarInputInterface inputInterface = new JarInputInterface(entry.getKey())) {
                    inputInterface.handle((path, bytes) -> {
                        String key = stripLeadingSlash.apply(path);
                        System.out.println("[Architectury Transformer Runtime] Mark file as transformable: " + key);
                        CLASSES_TO_TRANSFORM.put(key, entry.getValue());
                    });
                }
            }
        }
        try (JarOutputInterface outputInterface = new JarOutputInterface(tmpJar)) {
            for (Map.Entry<Path, List<Transformer>> entry : toTransform.entrySet()) {
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
                }, new JarInputInterface(entry.getKey()), new OutputInterface() {
                    @Override
                    public void addFile(String path, byte[] bytes) throws IOException {
                        String key = stripLeadingSlash.apply(path);
                        System.out.println("[Architectury Transformer Runtime] Added file " + key);
                        outputInterface.addFile(key, bytes);
                    }
                    
                    @Override
                    public void modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
                        
                    }
                    
                    @Override
                    public void close() throws IOException {
                        
                    }
                }, entry.getValue());
            }
        }
        
        tmpJar.toFile().deleteOnExit();
        populateAddUrl().accept(tmpJar.toUri().toURL());

//        take(toTransform, populateAddUrl());
        
        List<String> cp = new ArrayList<>(Arrays.asList(System.getProperty("java.class.path", "").split(File.pathSeparator)));
        for (Path tmpPath : TMP_PATHS) {
            cp.add(tmpPath.toAbsolutePath().toString());
        }
        System.setProperty("java.class.path", String.join(File.pathSeparator, cp));
        
        Path mainClassPath = Paths.get(System.getProperty(MAIN_CLASS));
        String mainClass = new String(Files.readAllBytes(mainClassPath), StandardCharsets.UTF_8);
        MethodHandle handle = MethodHandles.publicLookup().findStatic(Class.forName(mainClass), "main", MethodType.methodType(void.class, String[].class));
        handle.invokeExact(argsList.toArray(new String[0]));
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
    
    private static void injectAgent() throws IOException, AgentLoadException, AgentInitializationException, AttachNotSupportedException {
        // Do horrible stuff here
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        String pid = jvmName.substring(0, jvmName.indexOf("@"));
        VirtualMachine jvm = VirtualMachine.attach(pid);
        jvm.loadAgent(createTmpAgentJar().getAbsolutePath());
        jvm.detach();
    }
    
    private static File createTmpAgentJar() throws IOException {
        File tmpAgentJar = File.createTempFile("architectury-agent", ".jar");
        tmpAgentJar.deleteOnExit();
        try (InputStream stream = TransformerRuntime.class.getResourceAsStream("/agent/injection.jar")) {
            Files.copy(stream, tmpAgentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tmpAgentJar;
    }
    
    private static final Set<Path> TMP_PATHS = new HashSet<>();
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(TransformerRuntime::shutdownHook));
    }
    
    private static void shutdownHook() {
        for (Path tmpPath : TMP_PATHS) {
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }
    
    public static void take(Map<Path, List<Transformer>> toTransform, Consumer<URL> addUrl) throws IOException {
        for (Map.Entry<Path, List<Transformer>> entry : toTransform.entrySet()) {
            Path input = entry.getKey();
            Path output = Files.createTempFile(null, ".jar");
            TMP_PATHS.add(output);
            
            List<Transformer> transformers = entry.getValue();
//            Transform.runTransformers(input, output, transformers);
            addUrl.accept(output.toUri().toURL());
        }
    }
}
