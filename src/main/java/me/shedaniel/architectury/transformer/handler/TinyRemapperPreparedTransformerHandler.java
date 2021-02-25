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

package me.shedaniel.architectury.transformer.handler;

import me.shedaniel.architectury.transformer.transformers.ClasspathProvider;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import me.shedaniel.architectury.transformer.util.Logger;
import net.fabricmc.tinyremapper.ClassInstance;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class TinyRemapperPreparedTransformerHandler extends SimpleTransformerHandler {
    private TinyRemapper remapper;
    
    public TinyRemapperPreparedTransformerHandler(ClasspathProvider classpath, TransformerContext context) throws Exception {
        super(classpath, context);
        prepare();
    }
    
    private void prepare() throws Exception {
        Logger.debug("Preparing tiny remapper prepared transformer: " + getClass().getName());
        remapper = TinyRemapper.newRemapper().build();
        
        remapper.readClassPath(classpath.provide());
    }
    
    private void resetTR(Collection<IMappingProvider> mappingProviders) throws Exception {
        Field outputBufferField = remapper.getClass().getDeclaredField("outputBuffer");
        outputBufferField.setAccessible(true);
        outputBufferField.set(remapper, null);
        
        resetTRMap("classMap");
        resetTRMap("methodMap");
        resetTRMap("methodArgMap");
        resetTRMap("fieldMap");
        
        Field mappingProvidersField = remapper.getClass().getDeclaredField("mappingProviders");
        mappingProvidersField.setAccessible(true);
        ((Collection<IMappingProvider>) mappingProvidersField.get(remapper)).clear();
        ((Collection<IMappingProvider>) mappingProvidersField.get(remapper)).addAll(mappingProviders);
    }
    
    private void resetTRMap(String fieldName) throws Exception {
        Field field = remapper.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Map) field.get(remapper)).clear();
    }
    
    private Map<String, ClassInstance> tmpClasses;
    private Map<String, ClassInstance> tmpReadClasses;
    
    @Override
    public TinyRemapper getRemapper(List<IMappingProvider> providers) throws Exception {
        Field classesField = remapper.getClass().getDeclaredField("classes");
        classesField.setAccessible(true);
        tmpClasses = new HashMap<>((Map<String, ClassInstance>) classesField.get(remapper));
        Field readClassesField = remapper.getClass().getDeclaredField("readClasses");
        readClassesField.setAccessible(true);
        tmpReadClasses = new HashMap<>((Map<String, ClassInstance>) readClassesField.get(remapper));
        resetTR(providers);
        return remapper;
    }
    
    @Override
    protected void closeRemapper(TinyRemapper remapper) throws Exception {
        if (tmpClasses != null) {
            Field classesField = remapper.getClass().getDeclaredField("classes");
            classesField.setAccessible(true);
            classesField.set(remapper, tmpClasses);
            tmpClasses = null;
        }
        if (tmpReadClasses != null) {
            Field readClassesField = remapper.getClass().getDeclaredField("readClasses");
            readClassesField.setAccessible(true);
            readClassesField.set(remapper, tmpReadClasses);
            tmpReadClasses = null;
        }
        resetTR(Collections.emptyList());
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        if (this.remapper != null) {
            this.remapper.finish();
        }
        this.remapper = null;
    }
}
