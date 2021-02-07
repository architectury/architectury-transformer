package me.shedaniel.architectury.transformer;

public class TransformerStepSkipped extends Throwable {
    public static final TransformerStepSkipped INSTANCE = new TransformerStepSkipped();
    
    private TransformerStepSkipped() {}
}
