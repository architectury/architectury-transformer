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

package me.shedaniel.architectury.transformer.util;

import me.shedaniel.architectury.transformer.transformers.BuiltinProperties;

public class Logger {
    private static Boolean verbose = null;
    
    private Logger() {}
    
    public static void info(String str) {
        System.out.println("[Architectury Transformer] " + str);
    }
    
    public static void info(String str, Object... args) {
        info(String.format(str, args));
    }
    
    public static void debug(String str) {
        if (isVerbose()) {
            System.out.println("[Architectury Transformer DEBUG] " + str);
        }
    }
    
    public static void debug(String str, Object... args) {
        debug(String.format(str, args));
    }
    
    private static boolean isVerbose() {
        if (verbose == null) {
            verbose = System.getProperty(BuiltinProperties.VERBOSE, "false").equals("true");
        }
        
        return verbose;
    }
    
    public static void error(String str) {
        System.err.println("[Architectury Transformer] " + str);
    }
    
    public static void error(String str, Object... args) {
        error(String.format(str, args));
    }
}
