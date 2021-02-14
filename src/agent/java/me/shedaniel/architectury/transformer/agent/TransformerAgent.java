package me.shedaniel.architectury.transformer.agent;

import java.lang.instrument.Instrumentation;
import java.util.Objects;

public class TransformerAgent {
    private static Instrumentation instrumentation;
    
    public static void agentmain(String args, Instrumentation instrumentation) {
        if (!instrumentation.isRedefineClassesSupported()) {
            System.out.println("your instrumentation suck");
        }
        TransformerAgent.instrumentation = instrumentation;
    }
    
    public static Instrumentation getInstrumentation() {
        return Objects.requireNonNull(instrumentation, "Architectury Transformer Java Agent not attached!");
    }
}
