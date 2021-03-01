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

import me.shedaniel.architectury.transformer.transformers.base.TinyRemapperTransformer;
import net.fabricmc.tinyremapper.IMappingProvider;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Remap architectury injectables calls to the injected classes.
 */
public class RemapInjectables implements TinyRemapperTransformer {
    public static final String expectPlatform = "Lme/shedaniel/architectury/ExpectPlatform;";
    public static final String expectPlatformNew = "Lme/shedaniel/architectury/annotations/ExpectPlatform;";
    public static final String expectPlatformTransformed = "Lme/shedaniel/architectury/annotations/ExpectPlatform$Transformed;";

    @Override
    public List<IMappingProvider> collectMappings() throws Exception {
        if (isInjectInjectables()) {
            return Collections.singletonList(sink -> {
                sink.acceptClass(
                        "me/shedaniel/architectury/targets/ArchitecturyTarget",
                        getUniqueIdentifier() + "/PlatformMethods"
                );
                sink.acceptMethod(
                        new IMappingProvider.Member(
                                "me/shedaniel/architectury/targets/ArchitecturyTarget",
                                "getCurrentTarget",
                                "()Ljava/lang/String;"
                        ), "getModLoader"
                );
            });
        }
        return Collections.emptyList();
    }
    
    public static String getUniqueIdentifier() {
        return System.getProperty(BuiltinProperties.UNIQUE_IDENTIFIER);
    }
    
    public static boolean isInjectInjectables() {
        return System.getProperty(BuiltinProperties.INJECT_INJECTABLES, "true").equals("true");
    }
    
    public static String[] getClasspath() {
        return System.getProperty(BuiltinProperties.COMPILE_CLASSPATH, "true").split(File.pathSeparator);
    }
}