package dev.irium.agent.module;

import dev.irium.agent.IriumAgent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.MessageDigest;
import java.security.ProtectionDomain;

/**
 * M5 : applique les recettes du serveur au bytecode du host.
 *
 * Sécurité : l'ancre sha256 de la recette doit correspondre aux octets
 * ORIGINAUX de la classe cible — sinon la recette est refusée et le host
 * reste intact (version du client non prévue, classe déjà modifiée, etc.).
 *
 * Idempotence : une fois la classe patchée, ses octets ne correspondent plus
 * à l'ancre ; on marque "applied" pour rester silencieux sur les
 * retransformations suivantes (pas de double injection).
 *
 * Injection : INVOKESTATIC bridge.tick()V AVANT chaque RETURN de la méthode
 * ciblée (= TAIL au sens mixin). Aucune branche ajoutée, pile +0 net —
 * COMPUTE_MAXS suffit, les frames d'origine sont copiées telles quelles.
 */
public final class RecipeTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        try {
            Recipe r = RecipeStore.match(className);
            if (r == null) return null;

            byte[] hash = sha256(classfileBuffer);
            if (!MessageDigest.isEqual(hash, r.anchor())) {
                if (RecipeStore.isApplied(className)) return null; // retransform post-patch : silencieux
                IriumAgent.log("[recette] ancre INVALIDE pour " + className + " -> recette refusée, host intact");
                return null;
            }
            if (RecipeStore.isApplied(className)) return null; // déjà patché

            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cr.accept(new Visitor(Opcodes.ASM9, cw, r), 0);
            byte[] patched = cw.toByteArray();
            RecipeStore.markApplied(className);
            IriumAgent.log("[recette] ancre vérifiée -> hook injecté dans " + className
                    + "." + r.method() + " (bridge " + r.bridge() + ")");
            return patched;
        } catch (Throwable t) {
            IriumAgent.log("[recette] échec transformation, host intact : " + t);
            return null; // ne JAMAIS casser le host
        }
    }

    private static byte[] sha256(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    private static final class Visitor extends ClassVisitor {
        private final Recipe recipe;

        Visitor(int api, ClassWriter cw, Recipe recipe) {
            super(api, cw);
            this.recipe = recipe;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean target = name.equals(recipe.method()) && descriptor.equals(recipe.desc());
            if (mv != null && target && (access & Opcodes.ACC_ABSTRACT) == 0) {
                // si le 1er paramètre est une référence, on le passe au pont
                boolean refFirst = descriptor.startsWith("(L");
                return new TailHook(api, mv, recipe.bridge(), refFirst);
            }
            return mv;
        }
    }

    /**
     * Injecte bridge.tick(...)V avant chaque instruction RETURN de la méthode.
     * refFirst : ALOAD 1 + tick(Ljava/lang/Object;)V — stack net 0 avant RETURN.
     */
    private static final class TailHook extends MethodVisitor {
        private final String bridge;
        private final boolean refFirst;

        TailHook(int api, MethodVisitor mv, String bridge, boolean refFirst) {
            super(api, mv);
            this.bridge = bridge;
            this.refFirst = refFirst;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                if (refFirst) {
                    super.visitVarInsn(Opcodes.ALOAD, 1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, "tick", "(Ljava/lang/Object;)V", false);
                } else {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, "tick", "()V", false);
                }
            }
            super.visitInsn(opcode);
        }
    }
}
