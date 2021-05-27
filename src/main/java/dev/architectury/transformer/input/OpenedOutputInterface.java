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

import dev.architectury.transformer.util.ClosableChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class OpenedOutputInterface extends ClosableChecker implements OutputInterface {
    private final Provider provider;
    private Lock lock = new ReentrantLock();
    private OutputInterface outputInterface;
    
    protected OpenedOutputInterface(Provider provider) {
        this.provider = provider;
    }
    
    public static OpenedOutputInterface of(Provider provider) {
        return new OpenedOutputInterface(provider);
    }
    
    public static OpenedOutputInterface ofJar(Path path) {
        return new OpenedOutputInterface(() -> new JarOutputInterface(path));
    }
    
    public static OpenedOutputInterface ofDirectory(Path path) {
        return new OpenedOutputInterface(() -> new DirectoryOutputInterface(path));
    }
    
    @FunctionalInterface
    public interface Provider {
        OutputInterface provide() throws IOException;
    }
    
    private OutputInterface getParent() throws IOException {
        validateCloseState();
        
        try {
            lock.lock();
            if (outputInterface == null || outputInterface.isClosed()) {
                return outputInterface = provider.provide();
            }
            
            return outputInterface;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void handle(Consumer<String> action) throws IOException {
        getParent().handle(action);
    }
    
    @Override
    public void handle(BiConsumer<String, byte[]> action) throws IOException {
        getParent().handle(action);
    }
    
    @Override
    public boolean addFile(String path, byte[] bytes) throws IOException {
        return getParent().addFile(path, bytes);
    }
    
    @Override
    public byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException {
        return getParent().modifyFile(path, action);
    }
    
    @Override
    public void close() throws IOException {
        closeAndValidate();
        if (outputInterface != null) {
            outputInterface.close();
        }
        outputInterface = null;
    }
}
