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
import dev.architectury.transformer.transformers.base.ClassEditTransformer;
import dev.architectury.transformer.util.Logger;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;

import static dev.architectury.transformer.transformers.RemapInjectables.*;

public class TransformExpectPlatform implements ClassEditTransformer {
    private String platformPackage = null;
    
    @Override
    public void supplyProperties(JsonObject json) {
        platformPackage = json.has(BuiltinProperties.PLATFORM_PACKAGE) ?
                json.getAsJsonPrimitive(BuiltinProperties.PLATFORM_PACKAGE).getAsString() : null;
    }
    
    @Override
    public ClassNode doEdit(String name, ClassNode node) {
        if (!RemapInjectables.isInjectInjectables()) return node;
        for (MethodNode method : node.methods) {
            if (method.invisibleAnnotations != null && method.invisibleAnnotations.stream().anyMatch(it -> EXPECT_PLATFORM.equals(it.desc) || EXPECT_PLATFORM_LEGACY2.equals(it.desc)) ||
                method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(it -> EXPECT_PLATFORM_LEGACY.equals(it.desc))) {
                if ((method.access & Opcodes.ACC_STATIC) == 0) {
                    Logger.error("@ExpectPlatform can only apply to static methods!");
                } else {
                    method.instructions.clear();
                    Type type = Type.getMethodType(method.desc);
                    
                    int stackIndex = 0;
                    for (Type argumentType : type.getArgumentTypes()) {
                        method.instructions.add(new VarInsnNode(argumentType.getOpcode(Opcodes.ILOAD), stackIndex));
                        stackIndex += argumentType.getSize();
                    }
                    
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, getPlatformClass(platformPackage, node.name), method.name, method.desc));
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
    
    private static String getPlatformClass(@Nullable String platformPackage, String lookupClass) {
        String platform = platformPackage;
        if (platform == null) {
            platform = System.getProperty(BuiltinProperties.PLATFORM_PACKAGE);
            if (platform == null) {
                platform = System.getProperty(BuiltinProperties.PLATFORM_NAME);
                Preconditions.checkNotNull(platform, BuiltinProperties.PLATFORM_NAME + " is not present!");
                if (platform.equals("quilt"))
                    platform = "fabric";
            }
        }

        String lookupType = lookupClass.replace("$", "") + "Impl";
        
        return lookupType.substring(0, lookupType.lastIndexOf('/')) + "/" + platform + "/" +
               lookupType.substring(lookupType.lastIndexOf('/') + 1);
    }
}