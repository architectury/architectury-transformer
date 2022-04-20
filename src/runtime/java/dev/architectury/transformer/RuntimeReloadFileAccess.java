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

package dev.architectury.transformer;

import dev.architectury.transformer.input.AbstractFileAccess;
import dev.architectury.transformer.input.FileAccess;
import dev.architectury.transformer.util.HashUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class RuntimeReloadFileAccess extends AbstractFileAccess {
    private final Map<String, String> lastClassRedefineCache;
    private final Map<String, String> thisClassRedefineCache;
    private final Map<String, byte[]> redefine;
    private final FileAccess out;
    
    public RuntimeReloadFileAccess(Map<String, String> lastClassRedefineCache, Map<String, String> thisClassRedefineCache, Map<String, byte[]> redefine, FileAccess out) {
        super(out);
        this.lastClassRedefineCache = lastClassRedefineCache;
        this.thisClassRedefineCache = thisClassRedefineCache;
        this.redefine = redefine;
        this.out = out;
    }
    
    @Override
    public boolean addFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        if (out.addFile(s, bytes)) {
            if (path.endsWith(".class")) {
                s = s.substring(0, s.length() - 6);
                String sha256 = HashUtils.sha256(bytes);
                if (!Objects.equals(lastClassRedefineCache.get(s), sha256)) {
                    thisClassRedefineCache.put(s, sha256);
                    redefine.put(s, bytes);
                } else if (thisClassRedefineCache.containsKey(s)) {
                    thisClassRedefineCache.remove(s);
                    redefine.remove(s);
                }
            }
            
            return true;
        }
        return false;
    }
    
    @Override
    public byte[] modifyFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        bytes = out.modifyFile(s, bytes);
        if (s.endsWith(".class") && bytes != null) {
            String sha256 = HashUtils.sha256(bytes);
            String className = s.substring(0, s.length() - 6);
            if (!Objects.equals(lastClassRedefineCache.get(className), sha256)) {
                thisClassRedefineCache.put(className, sha256);
                redefine.put(className, bytes);
            } else if (thisClassRedefineCache.containsKey(className)) {
                thisClassRedefineCache.remove(className);
                redefine.remove(className);
            }
        }
        return bytes;
    }
    
    @Override
    public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        String s = Transform.trimSlashes(path);
        byte[] bytes = out.modifyFile(s, action);
        if (s.endsWith(".class") && bytes != null) {
            String sha256 = HashUtils.sha256(bytes);
            String className = s.substring(0, s.length() - 6);
            if (!Objects.equals(lastClassRedefineCache.get(className), sha256)) {
                thisClassRedefineCache.put(className, sha256);
                redefine.put(className, bytes);
            } else if (thisClassRedefineCache.containsKey(className)) {
                thisClassRedefineCache.remove(className);
                redefine.remove(className);
            }
        }
        return bytes;
    }
    
    @Override
    public boolean deleteFile(String path) throws IOException {
        String s = Transform.trimSlashes(path);
        if (out.deleteFile(s)) {
            if (path.endsWith(".class")) {
                s = s.substring(0, s.length() - 6);
                thisClassRedefineCache.remove(s);
                redefine.remove(s);
            }
            
            return true;
        }
        return false;
    }
    
    @Override
    public String toString() {
        return out.toString();
    }
    
    @Override
    public void close() {
    }
}
