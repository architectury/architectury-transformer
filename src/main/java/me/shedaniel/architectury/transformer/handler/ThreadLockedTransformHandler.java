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

import me.shedaniel.architectury.transformer.Transformer;
import me.shedaniel.architectury.transformer.input.OutputInterface;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadLockedTransformHandler implements TransformHandler {
    private final TransformHandler parent;
    private final ReentrantLock lock = new ReentrantLock();
    
    ThreadLockedTransformHandler(TransformHandler parent) {
        this.parent = parent;
    }
    
    @Override
    public TransformHandler asThreadLocked() {
        return this;
    }
    
    @Override
    public void handle(String input, OutputInterface output, List<Transformer> transformers) throws Exception {
        lock.lock();
        try {
            this.parent.handle(input, output, transformers);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            this.parent.close();
        } finally {
            lock.unlock();
        }
    }
}
