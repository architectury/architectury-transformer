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

package me.shedaniel.architectury.transformer.input;

import me.shedaniel.architectury.transformer.util.ClosableChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class OpenedInputInterface extends ClosableChecker implements InputInterface {
    private final Provider provider;
    private Lock lock = new ReentrantLock();
    private InputInterface inputInterface;
    
    protected OpenedInputInterface(Provider provider) {
        this.provider = provider;
    }
    
    public static OpenedInputInterface of(Provider provider) {
        return new OpenedInputInterface(provider);
    }
    
    public static OpenedInputInterface ofJar(Path path) {
        return new OpenedInputInterface(() -> new JarInputInterface(path));
    }
    
    public static OpenedInputInterface ofDirectory(Path path) {
        return new OpenedInputInterface(() -> new DirectoryInputInterface(path));
    }
    
    @FunctionalInterface
    public interface Provider {
        InputInterface provide() throws IOException;
    }
    
    private InputInterface getParent() throws IOException {
        validateCloseState();
        
        try {
            lock.lock();
            if (inputInterface == null || inputInterface.isClosed()) {
                return inputInterface = provider.provide();
            }
            
            return inputInterface;
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
    public void close() throws IOException {
        closeAndValidate();
        if (inputInterface != null) {
            inputInterface.close();
        }
        inputInterface = null;
    }
}
