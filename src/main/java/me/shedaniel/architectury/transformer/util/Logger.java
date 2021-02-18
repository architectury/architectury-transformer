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
