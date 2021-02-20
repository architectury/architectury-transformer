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

package me.shedaniel.architectury.transformer.transformers;

import com.google.gson.*;
import me.shedaniel.architectury.transformer.Transform;
import me.shedaniel.architectury.transformer.transformers.base.AssetEditTransformer;
import me.shedaniel.architectury.transformer.transformers.base.edit.AssetEditSink;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import me.shedaniel.architectury.transformer.util.Logger;
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
    private TinyTree srg;
    
    @Override
    public void doEdit(TransformerContext context, AssetEditSink sink) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        List<String> mixinConfigs = new ArrayList<>();
        String refmap = System.getProperty(BuiltinProperties.REFMAP_NAME);
        sink.handle((path, bytes) -> {
            String trimmedPath = Transform.stripLoadingSlash(path);
            if (trimmedPath.endsWith(".json") && !trimmedPath.contains("/") && !trimmedPath.contains("\\")) {
                Logger.debug("Checking whether " + path + " is a mixin config.");
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
        if (!mixinConfigs.isEmpty()) {
            Logger.debug("Found mixin config(s): " + String.join(",", mixinConfigs));
        }
        if (context.canModifyAssets()) {
            sink.transformFile("META-INF/MANIFEST.MF", bytes -> {
                try {
                    Logger.debug("Injecting MixinConfigs into /META-INF/MANIFEST.MF");
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
            Logger.error("Failed to inject mixin config!");
        }
        if (refmap != null) {
            Logger.debug("Remapping refmap from intermediary to srg: " + refmap);
            sink.transformFile(refmap, bytes -> {
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
                            for (Map.Entry<String, JsonElement> entry : copy.entrySet()) {
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
    
    private String replaceFirst(String full, String toReplace, String replacement) {
        int index = full.indexOf(toReplace);
        if (index == -1) return full;
        StringBuilder builder = new StringBuilder();
        builder.append(full, 0, index);
        builder.append(replacement);
        builder.append(full, index + toReplace.length(), full.length());
        return builder.toString();
    }
    
    private void remapRefmap(JsonObject obj) throws IOException {
        if (srg == null) {
            Path srgMappingsPath = Paths.get(System.getProperty(BuiltinProperties.MAPPINGS_WITH_SRG));
            try (BufferedReader reader = Files.newBufferedReader(srgMappingsPath)) {
                srg = TinyMappingFactory.loadWithDetection(reader);
            }
        }
        
        Pattern methodPattern = Pattern.compile("L(.*);(.*)(\\(.*)");
        Pattern methodPatternWithoutClass = Pattern.compile("(.*)(\\(.*)");
        Pattern fieldPattern = Pattern.compile("L(.*);(.*):(.*)");
        Pattern fieldPatternWithoutClass = Pattern.compile("(.*):(.*)");
        
        for (String key : new HashSet<>(obj.keySet())) {
            String originalRef = obj.get(key).getAsString();
            String remappedRef = null;
            
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
                remappedRef = replaceFirst(originalRef, methodMatch.group(1),
                        matchedClass.map(it -> it.getName("srg")).orElse(methodMatch.group(1)));
                remappedRef = replaceFirst(remappedRef, methodMatch.group(2), replacementName);
                remappedRef = replaceFirst(remappedRef, methodMatch.group(3), remapDescriptor(methodMatch.group(3),
                        it -> srg.getClasses().stream()
                                .filter(def -> Objects.equals(def.getName("intermediary"), it))
                                .findFirst()
                                .map(def -> def.getName("srg"))
                                .orElse(it)
                ));
            } else if (fieldMatch.matches()) {
                Optional<ClassDef> matchedClass = srg.getClasses().stream().filter(it -> Objects.equals(it.getName("intermediary"), fieldMatch.group(1))).findFirst();
                String replacementName = srg.getClasses().stream()
                        .flatMap(it -> it.getMethods().stream())
                        .filter(it -> Objects.equals(it.getName("intermediary"), fieldMatch.group(2)))
                        .filter(it -> Objects.equals(it.getDescriptor("intermediary"), fieldMatch.group(3)))
                        .findFirst()
                        .map(it -> it.getName("srg"))
                        .orElse(fieldMatch.group(2));
                remappedRef = replaceFirst(originalRef, fieldMatch.group(1),
                        matchedClass.map(it -> it.getName("srg")).orElse(fieldMatch.group(1)));
                remappedRef = replaceFirst(remappedRef, fieldMatch.group(2), replacementName);
                remappedRef = replaceFirst(remappedRef, fieldMatch.group(3), remapDescriptor(fieldMatch.group(3),
                        it -> srg.getClasses().stream()
                                .filter(def -> Objects.equals(def.getName("intermediary"), it))
                                .findFirst()
                                .map(def -> def.getName("srg"))
                                .orElse(it)
                ));
            } else if (fieldMatchWithoutClass.matches()) {
                String replacementName = srg.getClasses().stream()
                        .flatMap(it -> it.getFields().stream())
                        .filter(it -> Objects.equals(it.getName("intermediary"), fieldMatchWithoutClass.group(1)))
                        .filter(it -> Objects.equals(it.getDescriptor("intermediary"), fieldMatchWithoutClass.group(2)))
                        .findFirst()
                        .map(it -> it.getName("srg"))
                        .orElse(fieldMatchWithoutClass.group(1));
                remappedRef = replaceFirst(originalRef, fieldMatchWithoutClass.group(1), replacementName);
                remappedRef = replaceFirst(remappedRef, fieldMatchWithoutClass.group(2), remapDescriptor(fieldMatchWithoutClass.group(2),
                        it -> srg.getClasses().stream()
                                .filter(def -> Objects.equals(def.getName("intermediary"), it))
                                .findFirst()
                                .map(def -> def.getName("srg"))
                                .orElse(it)
                ));
            } else if (methodMatchWithoutClass.matches()) {
                String replacementName = srg.getClasses().stream()
                        .flatMap(it -> it.getMethods().stream())
                        .filter(it -> Objects.equals(it.getName("intermediary"), methodMatchWithoutClass.group(1)))
                        .filter(it -> Objects.equals(it.getDescriptor("intermediary"), methodMatchWithoutClass.group(2)))
                        .findFirst()
                        .map(it -> it.getName("srg"))
                        .orElse(methodMatchWithoutClass.group(1));
                remappedRef = replaceFirst(originalRef, methodMatchWithoutClass.group(1), replacementName);
                remappedRef = replaceFirst(remappedRef, methodMatchWithoutClass.group(2), remapDescriptor(methodMatchWithoutClass.group(2),
                        it -> srg.getClasses().stream()
                                .filter(def -> Objects.equals(def.getName("intermediary"), it))
                                .findFirst()
                                .map(def -> def.getName("srg"))
                                .orElse(it)
                ));
            } else if (classMatch.isPresent()) {
                remappedRef = classMatch.get().getName("srg");
            }
            
            if (remappedRef == null) {
                Logger.error("Failed to remap refmap value: " + originalRef);
            } else {
                obj.addProperty(key, remappedRef);
                Logger.debug("Remapped refmap value: " + originalRef + " -> " + remappedRef);
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
            String resultString = result.toString();
            Logger.debug("Remapped descriptor: %s -> %s", self, resultString);
            return resultString;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}