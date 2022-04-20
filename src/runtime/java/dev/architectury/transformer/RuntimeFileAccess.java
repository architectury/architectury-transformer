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
import java.util.function.UnaryOperator;

public class RuntimeFileAccess extends AbstractFileAccess {
    private final Map<String, String> classRedefineCache;
    private final FileAccess out;
    private final FileAccess debugOut;
    
    public RuntimeFileAccess(Map<String, String> classRedefineCache, FileAccess out, FileAccess debugOut) {
        super(out);
        this.classRedefineCache = classRedefineCache;
        this.out = out;
        this.debugOut = debugOut;
    }
    
    @Override
    public boolean addFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        if (s.endsWith(".class")) {
            classRedefineCache.put(s.substring(0, s.length() - 6), HashUtils.sha256(bytes));
        }
        return out.addFile(s, bytes) && (debugOut == null || debugOut.addFile(s, bytes));
    }
    
    @Override
    public byte[] modifyFile(String path, byte[] bytes) throws IOException {
        String s = Transform.trimSlashes(path);
        bytes = out.modifyFile(s, bytes);
        if (s.endsWith(".class") && bytes != null) {
            classRedefineCache.put(s.substring(0, s.length() - 6), HashUtils.sha256(bytes));
        }
        if (debugOut != null && bytes != null) {
            return debugOut.modifyFile(s, bytes);
        }
        return bytes;
    }
    
    @Override
    public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        String s = Transform.trimSlashes(path);
        byte[] bytes = out.modifyFile(s, action);
        if (s.endsWith(".class") && bytes != null) {
            classRedefineCache.put(s.substring(0, s.length() - 6), HashUtils.sha256(bytes));
        }
        if (debugOut != null && bytes != null) {
            return debugOut.modifyFile(s, bytes);
        }
        return bytes;
    }
    
    @Override
    public boolean deleteFile(String path) throws IOException {
        String s = Transform.trimSlashes(path);
        if (s.endsWith(".class")) {
            classRedefineCache.remove(s.substring(0, s.length() - 6));
        }
        return out.deleteFile(s) && (debugOut == null || debugOut.deleteFile(s));
    }
    
    @Override
    public String toString() {
        return out.toString();
    }
    
    @Override
    public void close() {
    }
}
