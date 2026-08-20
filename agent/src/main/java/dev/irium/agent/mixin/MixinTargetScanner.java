package dev.irium.agent.mixin;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * M7-B : extrait les cibles d'annotation @Mixin(XXX.class) du bytecode d'un mod.
 * Générique : fonctionne pour n'importe quel mod, aucune liste en dur.
 *
 * Détail : @Mixin est CLASS-retention (RuntimeInvisibleAnnotations) et sa
 * cible est un tableau "value" de Type — visitArray("value") doit parcourir
 * chaque élément Class-info.
 */
public final class MixinTargetScanner {

    private MixinTargetScanner() {}

    /** @return les classes cibles (forme pointée) de toutes les classes mixin du mod. */
    public static List<String> scan(byte[] mixinClassBytes) {
        Set<String> targets = new LinkedHashSet<>();
        if (mixinClassBytes == null) return new ArrayList<>(targets);
        try {
            new ClassReader(mixinClassBytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (!desc.endsWith("spongepowered/asm/mixin/Mixin;")) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if (value instanceof Type t) targets.add(t.getClassName());
                        }
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            // "value" (Classes) ET "targets" (String[]) : tout parcourir
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String n, Object value) {
                                    if (value instanceof Type t) targets.add(t.getClassName());
                                    else if (value instanceof String s && s.indexOf('.') > 0) targets.add(s);
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(targets);
    }
}
