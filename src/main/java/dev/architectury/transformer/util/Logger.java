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

package dev.architectury.transformer.util;

import dev.architectury.transformer.transformers.BuiltinProperties;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

public class Logger implements AutoCloseable {
    private final String location;
    private final boolean verbose;

    private PrintWriter writer;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public Logger(String location, boolean verbose) {
        this.location = location;
        this.verbose = verbose;
        try {
            File logFile = new File(location, ".architectury-transformer/debug.log");
            if (logFile.getParentFile().exists()) {
                try (Stream<Path> walk = Files.walk(logFile.getParentFile().toPath())) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
            logFile.getParentFile().mkdirs();
            writer = new PrintWriter(new FileWriter(logFile, false), true);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void info(String str) {
        String s = "[Architectury Transformer] " + str;
        System.out.println(s);
        this.writer.println(s);
    }

    public void info(String str, Object... args) {
        info(String.format(str, args));
    }

    public void debug(String str) {
        String s = "[Architectury Transformer DEBUG] " + str;
        if (isVerbose()) {
            System.out.println(s);
        }
        this.writer.println(s);
    }

    public void debug(String str, Object... args) {
        debug(String.format(str, args));
    }

    public void error(String str) {
        String s = "[Architectury Transformer] " + str;
        System.err.println(s);
        this.writer.println(s);
    }

    public void error(String str, Object... args) {
        error(String.format(str, args));
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }


    private static Logger cachedLogger = null;

    @Deprecated
    public static Logger getDefaultLogger() {
        String location = System.getProperty(BuiltinProperties.LOCATION, System.getProperty("user.dir"));
        Logger logger = cachedLogger;
        if (logger == null || !Objects.equals(location, logger.location)) {
            if (logger != null) {
                try {
                    logger.close();
                } catch (Exception e) {
                    //;
                }
            }
            logger = new Logger(location, System.getProperty(BuiltinProperties.VERBOSE, "false").equals("true"));
            cachedLogger = logger;
        }
        return logger;
    }
}
