package me.shedaniel.architectury.transformer.util;

import java.io.PrintStream;

public class LoggerFilter {
    public static void replaceSystemOut() {
        try {
            PrintStream previous = System.out;
            System.setOut(new PrintStream(previous) {
                @Override
                public PrintStream printf(String format, Object... args) {
                    if (format.equals("unknown invokedynamic bsm: %s/%s%s (tag=%d iif=%b)%n")) {
                        return this;
                    }
                    
                    return super.printf(format, args);
                }
            });
        } catch (SecurityException ignored) {
            // Failed to replace logger filter, just ignore
        }
    }
}
