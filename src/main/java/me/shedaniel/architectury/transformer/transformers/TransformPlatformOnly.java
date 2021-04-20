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

import me.shedaniel.architectury.transformer.transformers.base.ClassEditTransformer;
import me.shedaniel.architectury.transformer.util.Logger;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class TransformPlatformOnly implements ClassEditTransformer {
    @Override
    public ClassNode doEdit(String name, ClassNode node) {
        String platform = System.getProperty(BuiltinProperties.PLATFORM_NAME);
        if (platform == null) {
            Logger.debug("Skipping TransformPlatformOnly because BuiltinProperties.PLATFORM_NAME is not present");
            return node;
        }

        Iterator<MethodNode> iter = node.methods.iterator();
        while (iter.hasNext()) {
            MethodNode method = iter.next();
            AnnotationNode annotation = Optional.ofNullable(method.invisibleAnnotations)
                    .flatMap(nodes -> nodes.stream().filter(a -> a.desc.equals(RemapInjectables.platformOnly)).findAny())
                    .orElse(null);

            if (annotation == null) continue;

            for (int i = 0; i < annotation.values.size(); i += 2) {
                String key = (String) annotation.values.get(i);

                if (key.equals("value")) {
                    List<?> platforms = (List<?>) annotation.values.get(i + 1);
                    if (platforms.stream().map(String::valueOf).noneMatch(platform::equals)) {
                        iter.remove();
                    }
                }
            }
        }

        return node;
    }
}
