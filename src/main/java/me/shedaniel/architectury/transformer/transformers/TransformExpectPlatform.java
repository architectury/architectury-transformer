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

import com.google.common.base.Preconditions;
import me.shedaniel.architectury.transformer.input.OutputInterface;
import me.shedaniel.architectury.transformer.transformers.base.AssetEditTransformer;
import me.shedaniel.architectury.transformer.transformers.base.ClassEditTransformer;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import me.shedaniel.architectury.transformer.util.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.Objects;

public class TransformExpectPlatform implements AssetEditTransformer, ClassEditTransformer {
    @Override
    public void doEdit(TransformerContext context, OutputInterface output) throws Exception {
        if (!RemapInjectables.isInjectInjectables()) return;
        String className = RemapInjectables.getUniqueIdentifier() + "/PlatformMethods";
        output.addClass(className, buildPlatformMethodClass(className));
    }
    
    private byte[] buildPlatformMethodClass(String className) {
        /* Generates the following class:
         * public interface PlatformMethods {
         *   static String getModLoader() {
         *     return platform;
         *   }
         * }
         */
        String platform = System.getProperty(BuiltinProperties.PLATFORM_NAME);
        Preconditions.checkNotNull(platform, BuiltinProperties.PLATFORM_NAME + " is not present!");
        
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, className, null, "java/lang/Object", null);
        {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getModLoader", "()Ljava/lang/String;", null, null);
            method.visitLdcInsn(platform);
            method.visitInsn(Opcodes.ARETURN);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
    
    @Override
    public ClassNode doEdit(String name, ClassNode node) {
        if (!RemapInjectables.isInjectInjectables()) return node;
        for (MethodNode method : node.methods) {
            String platformMethodsClass = null;
            
            if (method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(it -> Objects.equals(it.desc, RemapInjectables.EXPECT_PLATFORM_LEGACY))) {
                platformMethodsClass = "me/shedaniel/architectury/PlatformMethods";
            } else if (method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(it -> Objects.equals(it.desc, RemapInjectables.EXPECT_PLATFORM))) {
                platformMethodsClass = RemapInjectables.getUniqueIdentifier() + "/PlatformMethods";
            }
            
            if (platformMethodsClass != null) {
                if ((method.access & Opcodes.ACC_STATIC) == 0) {
                    Logger.error("@ExpectPlatform can only apply to static methods!");
                } else {
                    method.instructions.clear();
                    Type type = Type.getMethodType(method.desc);
                    
                    int stackIndex = 0;
                    for (Type argumentType : type.getArgumentTypes()) {
                        method.instructions.add(new VarInsnNode(argumentType.getOpcode(Opcodes.ILOAD), stackIndex += argumentType.getSize()));
                    }
                    
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, getPlatformClass(name), method.name, method.desc));
                    method.instructions.add(new InsnNode(type.getReturnType().getOpcode(Opcodes.IRETURN)));
                    
                    method.maxStack = -1;
                    
                    // Add @ExpectPlatform.Transformed as a marker annotation
                    if (method.invisibleAnnotations == null) method.invisibleAnnotations = new ArrayList<>();
                    method.invisibleAnnotations.add(new AnnotationNode(RemapInjectables.EXPECT_PLATFORM_TRANSFORMED));
                }
            }
        }
        
        return node;
    }
    
    private static String getPlatformClass(String lookupClass) {
        String platform = System.getProperty(BuiltinProperties.PLATFORM_NAME);
        Preconditions.checkNotNull(platform, BuiltinProperties.PLATFORM_NAME + " is not present!");
        String lookupType = lookupClass.replace("$", "") + "Impl";
        
        return lookupType.substring(0, lookupType.lastIndexOf('.')) + "." + platform + "." +
               lookupType.substring(lookupType.lastIndexOf('.') + 1);
    }
}