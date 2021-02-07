package me.shedaniel.architectury.transformer.transformers;

import me.shedaniel.architectury.transformer.Transform;
import me.shedaniel.architectury.transformer.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Handle @ForgeEvent and @ForgeEventCancellable and promote @Environment from being an invisible annotation to being an visible annotation.
 */
public class TransformForgeAnnotations implements Transformer {
    public static final String FORGE_EVENT = "Lme/shedaniel/architectury/ForgeEvent;";
    public static final String FORGE_EVENT_CANCELLABLE = "Lme/shedaniel/architectury/ForgeEventCancellable;";
    public static final String CANCELABLE = "Lnet/minecraftforge/eventbus/api/Cancelable;";
    
    private static final String ENVIRONMENT = "net/fabricmc/api/Environment";
    
    @Override
    public void transform(Path input, Path output) throws Throwable {
        Transform.transform(input, output, (node, classAdder) -> {
            if ((node.access & Opcodes.ACC_INTERFACE) == 0) {
                if (node.visibleAnnotations != null && node.visibleAnnotations.stream().anyMatch(
                        annotation -> Objects.equals(annotation.desc, FORGE_EVENT) || Objects.equals(annotation.desc, FORGE_EVENT_CANCELLABLE)
                )) {
                    node.superName = "net/minecraftforge/eventbus/api/Event";
                    for (MethodNode method : node.methods) {
                        if (Objects.equals(method.name, "<init>")) {
                            for (AbstractInsnNode insnNode : method.instructions) {
                                if (insnNode.getOpcode() == Opcodes.INVOKESPECIAL) {
                                    MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                                    if (Objects.equals(methodInsnNode.name, "<init>") && Objects.equals(methodInsnNode.owner, "java/lang/Object")) {
                                        methodInsnNode.owner = "net/minecraftforge/eventbus/api/Event";
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (node.signature != null) {
                        int index = node.signature.lastIndexOf('L');
                        String s = index == -1 ? node.signature : node.signature.substring(0, index);
                        node.signature = s + "Lnet/minecraftforge/eventbus/api/Event;";
                    }
                    // if @ForgeEventCancellable, add the cancellable annotation from forge
                    if (node.visibleAnnotations.stream().anyMatch(annotation -> Objects.equals(annotation.desc, FORGE_EVENT_CANCELLABLE))) {
                        node.visibleAnnotations.add(new AnnotationNode(CANCELABLE));
                    }
                }
            }
            if (node.visibleAnnotations == null) {
                node.visibleAnnotations = new ArrayList<>();
            }
            {
                Collection<AnnotationNode> invisibleEnvironments;
                if (node.invisibleAnnotations == null) {
                    invisibleEnvironments = node.invisibleAnnotations.stream().filter(annotation -> Objects.equals(annotation.desc, "L${environmentClass};"))
                            .collect(Collectors.toList());
                    node.invisibleAnnotations.removeAll(invisibleEnvironments);
                } else {
                    invisibleEnvironments = Collections.emptyList();
                }
                node.visibleAnnotations.addAll(invisibleEnvironments);
            }
            for (FieldNode field : node.fields) {
                if (field.visibleAnnotations == null) {
                    field.visibleAnnotations = new ArrayList<>();
                }
                
                Collection<AnnotationNode> invisibleEnvironments;
                if (field.invisibleAnnotations == null) {
                    invisibleEnvironments = field.invisibleAnnotations.stream().filter(annotation -> Objects.equals(annotation.desc, "L${environmentClass};"))
                            .collect(Collectors.toList());
                    field.invisibleAnnotations.removeAll(invisibleEnvironments);
                } else {
                    invisibleEnvironments = Collections.emptyList();
                }
                field.visibleAnnotations.addAll(invisibleEnvironments);
            }
            for (MethodNode method : node.methods) {
                if (method.visibleAnnotations == null) {
                    method.visibleAnnotations = new ArrayList<>();
                }
                
                Collection<AnnotationNode> invisibleEnvironments;
                if (method.invisibleAnnotations == null) {
                    invisibleEnvironments = method.invisibleAnnotations.stream().filter(annotation -> Objects.equals(annotation.desc, "L${environmentClass};"))
                            .collect(Collectors.toList());
                    method.invisibleAnnotations.removeAll(invisibleEnvironments);
                } else {
                    invisibleEnvironments = Collections.emptyList();
                }
                method.visibleAnnotations.addAll(invisibleEnvironments);
            }
            return node;
        });
    }
}