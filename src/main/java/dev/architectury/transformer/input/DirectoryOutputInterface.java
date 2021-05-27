/*
 * This file is licensed under the MIT License, part of architectury-transformer.
 * Copyright (c) 2020, 2021 architectury
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

package dev.architectury.transformer.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.UnaryOperator;

public class DirectoryOutputInterface extends DirectoryInputInterface implements OutputInterface {
    private static final WeakHashMap<Path, DirectoryOutputInterface> INTERFACES = new WeakHashMap<>();
    
    protected DirectoryOutputInterface(Path root) {
        super(root);
    }
    
    public static DirectoryOutputInterface of(Path root) throws IOException {
        synchronized (INTERFACES) {
            if (INTERFACES.containsKey(root)) {
                return INTERFACES.get(root);
            }
            
            for (Map.Entry<Path, DirectoryOutputInterface> entry : INTERFACES.entrySet()) {
                if (Files.isSameFile(entry.getKey(), root)) {
                    return entry.getValue();
                }
            }
            
            DirectoryOutputInterface outputInterface = new DirectoryOutputInterface(root);
            INTERFACES.put(root, outputInterface);
            return outputInterface;
        }
    }
    
    @Override
    public boolean addFile(String path, byte[] bytes) throws IOException {
        validateCloseState();
        if (bytes == null) return false;
        Path p = root.resolve(path);
        Path parent = p.normalize().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(p, bytes, StandardOpenOption.CREATE);
        return true;
    }
    
    @Override
    public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        validateCloseState();
        Path file = root.resolve(path);
        
        if (Files.exists(file)) {
            byte[] bytes = Files.readAllBytes(file);
            try {
                bytes = action.apply(bytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to modify " + path, e);
            }
            addFile(path, bytes);
            return bytes;
        }
        
        return null;
    }
    
    @Override
    public void close() throws IOException {
        closeAndValidate();
        INTERFACES.remove(root, this);
    }
    
    @Override
    public String toString() {
        return root.toString();
    }
}
