package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.transformers.base.edit.AssetEditSink;
import me.shedaniel.architectury.transformer.transformers.base.edit.TransformerContext;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generates a fake forge mod.
 */
public class GenerateFakeForgeMod extends AbstractFakeMod {
    @Override
    public void doEdit(TransformerContext context, AssetEditSink sink) throws Exception {
        String fakeModId = generateModId();
        sink.addFile("META-INF/mods.toml",
                "modLoader = \"javafml\"\n" +
                "loaderVersion = \"[33,)\"\n" +
                "license = \"Generated\"\n" +
                "[[mods]]\n" +
                "modId = \"" + fakeModId + "\"\n");
        sink.addFile("pack.mcmeta",
                "{\"pack\":{\"description\":\"Generated\",\"pack_format\":" + System.getProperty(BuiltinProperties.MCMETA_VERSION, "4") + "}}");
        sink.addFile("generated/" + fakeModId + ".class", generateClass(fakeModId));
    }
    
    private byte[] generateClass(String fakeModId) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(52, Opcodes.ACC_PUBLIC, "generated/" + fakeModId, null, "java/lang/Object", null);
        AnnotationVisitor modAnnotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", false);
        modAnnotation.visit("value", fakeModId);
        modAnnotation.visitEnd();
        {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, new String[0]);
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}