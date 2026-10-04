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

package dev.architectury.transformer.transformers;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.transformers.base.AssetEditTransformer;
import dev.architectury.transformer.transformers.base.ClassEditTransformer;
import dev.architectury.transformer.transformers.base.edit.TransformerContext;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/**
 * Remap architectury injectables calls to the injected classes.
 */
public class RemapInjectables implements AssetEditTransformer, ClassEditTransformer {
    public static final String EXPECT_PLATFORM_LEGACY = "Lme/shedaniel/architectury/ExpectPlatform;";
    public static final String EXPECT_PLATFORM_LEGACY2 = "Lme/shedaniel/architectury/annotations/ExpectPlatform;";
    public static final String EXPECT_PLATFORM = "Ldev/architectury/injectables/annotations/ExpectPlatform;";
    public static final String EXPECT_PLATFORM_TRANSFORMED = "Ldev/architectury/injectables/annotations/ExpectPlatform$Transformed;";
    public static final String PLATFORM_ONLY_LEGACY = "Lme/shedaniel/architectury/annotations/PlatformOnly;";
    public static final String PLATFORM_ONLY = "Ldev/architectury/injectables/annotations/PlatformOnly;";
    private static final String ARCHITECTURY_TARGET = "dev/architectury/injectables/targets/ArchitecturyTarget";
    private String uniqueIdentifier = getUniqueIdentifier() + "/PlatformMethods";

    @Override
    public void supplyProperties(JsonObject json) {
        if (json.has(BuiltinProperties.UNIQUE_IDENTIFIER))
            uniqueIdentifier = json.getAsJsonPrimitive(BuiltinProperties.UNIQUE_IDENTIFIER).getAsString() + "/PlatformMethods";
    }

    @Override
    public void doEdit(TransformerContext context, FileAccess output) throws Exception {
        if (!RemapInjectables.isInjectInjectables()) return;
        output.addClass(uniqueIdentifier, buildPlatformMethodClass(uniqueIdentifier));
    }

    private byte[] buildPlatformMethodClass(String className) {
        /* Generates the following class:
         * public final class PlatformMethods {
         *   public static String getCurrentTarget() {
         *     return platform;
         *   }
         * }
         */
        String platform = System.getProperty(BuiltinProperties.PLATFORM_NAME);
        Preconditions.checkNotNull(platform, BuiltinProperties.PLATFORM_NAME + " is not present!");

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, className, null, "java/lang/Object", null);
        {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getCurrentTarget", "()Ljava/lang/String;", null, null);
            method.visitLdcInsn(platform);
            method.visitInsn(Opcodes.ARETURN);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Override
    public ClassNode doEdit(String name, ClassNode node) {
        if (!isInjectInjectables()) return node; // no need to edit the class
        ClassNode newNode = new ClassNode();
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return ARCHITECTURY_TARGET.equals(internalName) ? uniqueIdentifier : internalName;

            }
        };
        ClassVisitor cv = new ClassRemapper(newNode, remapper);
        node.accept(cv);
        return newNode;
    }

    public static String getUniqueIdentifier() {
        return System.getProperty(BuiltinProperties.UNIQUE_IDENTIFIER);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isInjectInjectables() {
        return System.getProperty(BuiltinProperties.INJECT_INJECTABLES, "true").equals("true");
    }
}