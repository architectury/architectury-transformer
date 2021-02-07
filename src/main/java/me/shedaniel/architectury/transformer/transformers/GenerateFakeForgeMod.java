package me.shedaniel.architectury.transformer.transformers;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a fake forge mod.
 */
public class GenerateFakeForgeMod extends AbstractFakeMod {
    @Override
    public void transform(Path input, Path output) throws Throwable {
        String fakeModId = generateModId();
        Files.copy(input, output);
        ZipUtil.addEntries(output.toFile(), new ZipEntrySource[]{
                new ByteSource("META-INF/mods.toml",
                        ("modLoader = \"javafml\"\n" +
                         "loaderVersion = \"[33,)\"\n" +
                         "license = \"Generated\"\n" +
                         "[[mods]]\n" +
                         "modId = \"$fakeModId\"\n").getBytes(StandardCharsets.UTF_8)),
                new ByteSource("pack.mcmeta",
                        ("{\"pack\":{\"description\":\"Generated\",\"pack_format\":" + System.getProperty(BuiltinProperties.MCMETA_VERSION, "4") + "}}")
                                .getBytes(StandardCharsets.UTF_8)),
                new ByteSource("generated/" + fakeModId + ".class", generateClass(fakeModId))
        });
    }
    
    private byte[] generateClass(String fakeModId) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(52, Opcodes.ACC_PUBLIC, "generated/$fakeModId", null, "java/lang/Object", null);
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