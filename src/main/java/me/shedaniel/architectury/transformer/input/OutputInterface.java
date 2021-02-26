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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface OutputInterface extends InputInterface {
    boolean addFile(String path, byte[] bytes) throws IOException;
    
    byte[] modifyFile(String path, UnaryOperator<byte[]> action) throws IOException;
    
    default void modifyFiles(Predicate<String> pathPredicate, BiFunction<String, byte[], byte[]> action) throws IOException {
        try {
            handle(path -> {
                if (pathPredicate.test(path)) {
                    try {
                        modifyFile(path, bytes -> action.apply(path, bytes));
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
    
    default void modifyFiles(BiFunction<String, byte[], byte[]> action) throws IOException {
        modifyFiles(path -> true, action);
    }
    
    default boolean addFile(String path, String text) throws IOException {
        return addFile(path, text.getBytes(StandardCharsets.UTF_8));
    }
    
    default boolean addClass(String path, byte[] bytes) throws IOException {
        return addFile(path + ".class", bytes);
    }
}
