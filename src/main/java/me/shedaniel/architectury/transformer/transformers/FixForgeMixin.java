package me.shedaniel.architectury.transformer.transformers;

import com.google.gson.*;
import me.shedaniel.architectury.transformer.transformers.base.AssetEditTransformer;
import me.shedaniel.architectury.transformer.transformers.base.edit.AssetEditSink;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds mixins to the MixinConfigs field in the manifest, and remap intermediary refmap to srg.
 */
public class FixForgeMixin implements AssetEditTransformer {
    @Override
    public void doEdit(TransformerContext context, AssetEditSink sink) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        List<String> mixinConfigs = new ArrayList<>();
        String refmap = System.getProperty(BuiltinProperties.REFMAP_NAME);
        sink.handle((path, bytes) -> {
            if (path.endsWith(".json") && !path.contains("/") && !path.contains("\\")) {
                try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes))) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        boolean hasMixins = json.has("mixins") && json.get("mixins").isJsonArray();
                        boolean hasClient = json.has("client") && json.get("client").isJsonArray();
                        boolean hasServer = json.has("server") && json.get("server").isJsonArray();
                        if (json.has("package") && (hasMixins || hasClient || hasServer)) {
                            mixinConfigs.add(path);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
        if (context.canModifyAssets()) {
            sink.transformFile("META-INF/MANIFEST.MF", bytes -> {
                try {
                    Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));
                    manifest.getMainAttributes().putValue("MixinConfigs", String.join(",", mixinConfigs));
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    manifest.write(stream);
                    return stream.toByteArray();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } else if (context.canAppendArgument()) {
            for (String config : mixinConfigs) {
                context.appendArgument("--mixin.config", config);
            }
        } else {
            System.err.println("Failed to inject mixin config!");
        }
        if (refmap != null) {
            sink.dangerouslyTransformFile(refmap, bytes -> {
                try {
                    JsonObject refmapElement = new JsonParser().parse(new InputStreamReader(new ByteArrayInputStream(bytes))).getAsJsonObject().deepCopy();
                    if (refmapElement.has("mappings")) {
                        for (Map.Entry<String, JsonElement> entry : refmapElement.get("mappings").getAsJsonObject().entrySet()) {
                            remapRefmap(entry.getValue().getAsJsonObject());
                        }
                    }
                    if (refmapElement.has("data")) {
                        JsonObject data = refmapElement.get("data").getAsJsonObject();
                        if (data.has("named:intermediary")) {
                            JsonObject copy = data.get("named:intermediary").getAsJsonObject().deepCopy();
                            for (Map.Entry<String, JsonElement> entry : copy.get("mappings").getAsJsonObject().entrySet()) {
                                remapRefmap(entry.getValue().getAsJsonObject());
                            }
                            data.add("searge", copy);
                            data.remove("named:intermediary");
                        }
                    }
                    return gson.toJson(refmapElement).getBytes(StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    private void remapRefmap(JsonObject obj) throws IOException {
        Path srgMappingsPath = Paths.get(System.getProperty(BuiltinProperties.MAPPINGS_WITH_SRG));
        TinyTree srg;
        try (BufferedReader reader = Files.newBufferedReader(srgMappingsPath)) {
            srg = TinyMappingFactory.loadWithDetection(reader);
        }
        Pattern methodPattern = Pattern.compile("L(.*);(.*)(\\(.*)");
        Pattern methodPatternWithoutClass = Pattern.compile("(.*)(\\(.*)");
        Pattern fieldPattern = Pattern.compile("L(.*);(.*):(.*)");
        Pattern fieldPatternWithoutClass = Pattern.compile("(.*):(.*)");
        
        for (String key : obj.keySet()) {
            String originalRef = obj.get(key).getAsString();
            
            Matcher methodMatch = methodPattern.matcher(originalRef);
            Matcher fieldMatch = fieldPattern.matcher(originalRef);
            Matcher fieldMatchWithoutClass = fieldPatternWithoutClass.matcher(originalRef);
            Matcher methodMatchWithoutClass = methodPatternWithoutClass.matcher(originalRef);
            Optional<ClassDef> classMatch = srg.getClasses().stream().filter(it -> Objects.equals(it.getName("intermediary"), originalRef)).findFirst();
            
            if (methodMatch.matches()) {
                Optional<ClassDef> matchedClass = srg.getClasses().stream().filter(it -> Objects.equals(it.getName("intermediary"), methodMatch.group(1))).findFirst();
                String replacementName = srg.getClasses().stream()
                        .flatMap(it -> it.getMethods().stream())
                        .filter(it -> Objects.equals(it.getName("intermediary"), methodMatch.group(2)))
                        .filter(it -> Objects.equals(it.getDescriptor("intermediary"), methodMatch.group(3)))
                        .findFirst()
                        .map(it -> it.getName("srg"))
                        .orElse(methodMatch.group(2));
                obj.addProperty(key, originalRef
                        .replaceFirst(methodMatch.group(1),
                                matchedClass.map(it -> it.getName("srg")).orElse(methodMatch.group(1)))
                        .replaceFirst(methodMatch.group(2), replacementName)
                        .replaceFirst(methodMatch.group(3), remapDescriptor(methodMatch.group(3),
                                it -> srg.getClasses().stream().filter(def -> Objects.equals(def.getName("intermediary"), it))
                                        .findFirst()
                                        .map(def -> def.getName("srg"))
                                        .orElse(it)
                                )
                        )
                );
            } else if (fieldMatch.matches()) {
                Optional<ClassDef> matchedClass = srg.getClasses().stream().filter(it -> Objects.equals(it.getName("intermediary"), fieldMatch.group(1))).findFirst();
                String replacementName = srg.getClasses().stream()
                        .flatMap(it -> it.getMethods().stream())
                        .filter(it -> Objects.equals(it.getName("intermediary"), fieldMatch.group(2)))
                        .filter(it -> Objects.equals(it.getDescriptor("intermediary"), fieldMatch.group(3)))
                        .findFirst()
                        .map(it -> it.getName("srg"))
                        .orElse(fieldMatch.group(2));
                obj.addProperty(key, originalRef
                        .replaceFirst(fieldMatch.group(1),
                                matchedClass.map(it -> it.getName("srg")).orElse(fieldMatch.group(1)))
                        .replaceFirst(fieldMatch.group(2), replacementName)
                        .replaceFirst(fieldMatch.group(3), remapDescriptor(fieldMatch.group(3),
                                it -> srg.getClasses().stream().filter(def -> Objects.equals(def.getName("intermediary"), it))
                                        .findFirst()
                                        .map(def -> def.getName("srg"))
                                        .orElse(it)
                                )
                        )
                );
            } else if (fieldMatchWithoutClass.matches()) {
                String replacementName = srg.getClasses().stream()
                        .flatMap(it -> it.getFields().stream())
                        .filter(it -> Objects.equals(it.getName("intermediary"), fieldMatchWithoutClass.group(1)))
                        .filter(it -> Objects.equals(it.getDescriptor("intermediary"), fieldMatchWithoutClass.group(2)))
                        .findFirst()
                        .map(it -> it.getName("srg"))
                        .orElse(fieldMatchWithoutClass.group(1));
                obj.addProperty(key, originalRef
                        .replaceFirst(fieldMatchWithoutClass.group(1), replacementName)
                        .replaceFirst(fieldMatchWithoutClass.group(2), remapDescriptor(fieldMatchWithoutClass.group(2),
                                it -> srg.getClasses().stream().filter(def -> Objects.equals(def.getName("intermediary"), it))
                                        .findFirst()
                                        .map(def -> def.getName("srg"))
                                        .orElse(it)
                                )
                        )
                );
            } else if (methodMatchWithoutClass.matches()) {
                String replacementName = srg.getClasses().stream()
                        .flatMap(it -> it.getMethods().stream())
                        .filter(it -> Objects.equals(it.getName("intermediary"), methodMatchWithoutClass.group(1)))
                        .filter(it -> Objects.equals(it.getDescriptor("intermediary"), methodMatchWithoutClass.group(2)))
                        .findFirst()
                        .map(it -> it.getName("srg"))
                        .orElse(methodMatchWithoutClass.group(1));
                obj.addProperty(key, originalRef
                        .replaceFirst(methodMatchWithoutClass.group(1), replacementName)
                        .replaceFirst(methodMatchWithoutClass.group(2), remapDescriptor(methodMatchWithoutClass.group(2),
                                it -> srg.getClasses().stream().filter(def -> Objects.equals(def.getName("intermediary"), it))
                                        .findFirst()
                                        .map(def -> def.getName("srg"))
                                        .orElse(it)
                                )
                        )
                );
            } else if (classMatch.isPresent()) {
                obj.addProperty(key, classMatch.get().getName("srg"));
            } else {
                System.err.println("Failed to remap refmap value: " + originalRef);
            }
        }
    }
    
    private String remapDescriptor(String self, UnaryOperator<String> classMappings) {
        try {
            StringReader reader = new StringReader(self);
            StringBuilder result = new StringBuilder();
            boolean insideClassName = false;
            StringBuilder className = new StringBuilder();
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    break;
                }
                if (c == (int) ';') {
                    insideClassName = false;
                    result.append(classMappings.apply(className.toString()));
                }
                if (insideClassName) {
                    className.append((char) c);
                } else {
                    result.append((char) c);
                }
                if (!insideClassName && c == (int) 'L') {
                    insideClassName = true;
                    className.setLength(0);
                }
            }
            return result.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}