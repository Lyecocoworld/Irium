package dev.irium.agent.module;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.security.MessageDigest;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * M5 : ancre canonique MÉTHODE-SEULE.
 *
 * Le hash de la classe entière est instable : les launchers tiers (SKL, etc.)
 * injectent un javaagent qui réordonne le constant-pool à chaque run — 19 Ko
 * d'octets changent alors que le code est sémantiquement identique.
 *
 * L'ancre canonique ne hashe QUE la méthode cible, avec :
 *   - chaque opcode + opérandes CP-RÉSOLUS (owner.name.desc, valeurs ldc…)
 *   - les labels normalisés en ordinaux d'apparition
 *   - la table d'exceptions
 *   - sans : frames, numéros de ligne, maxs (recalculés par le launcher)
 *
 * Résultat : stable vanilla ↔ transformé, mais toujours lié au code exact —
 * un byte de code changé = ancre cassée = recette refusée.
 */
public final class MethodAnchor {

    private MethodAnchor() {}

    /** @return sha256 du flux canonique de la méthode, ou null si absente/abstraite. */
    public static byte[] canonicalHash(byte[] classfileBuffer, String method, String desc) throws Exception {
        Hasher h = new Hasher(method, desc);
        new ClassReader(classfileBuffer).accept(h, ClassReader.SKIP_FRAMES);
        return h.found ? h.md.digest() : null;
    }

    private static final class Hasher extends ClassVisitor {
        final String method, desc;
        final MessageDigest md;
        boolean found;

        Hasher(String method, String desc) throws Exception {
            super(Opcodes.ASM9);
            this.method = method;
            this.desc = desc;
            this.md = MessageDigest.getInstance("SHA-256");
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if (!name.equals(method) || !descriptor.equals(desc)) return null;
            if ((access & Opcodes.ACC_ABSTRACT) != 0) return null;
            found = true;
            return new CanonicalWriter(api, md);
        }
    }

    /** Écrit le flux canonique dans le digest. Ordre des octets = contrat de l'ancre. */
    static final class CanonicalWriter extends MethodVisitor {
        final MessageDigest md;
        final Map<Label, Integer> labelOrdinals = new IdentityHashMap<>();
        int nextLabel = 0;

        CanonicalWriter(int api, MessageDigest md) {
            super(api);
            this.md = md;
        }

        private void op(int opcode) { md.update((byte) (opcode >> 8)); md.update((byte) opcode); }
        private void i(int v) { md.update((byte) (v >> 24)); md.update((byte) (v >> 16)); md.update((byte) (v >> 8)); md.update((byte) v); }
        private void s(String v) { byte[] b = v.getBytes(java.nio.charset.StandardCharsets.UTF_8); i(b.length); md.update(b); }
        private int lab(Label l) {
            Integer o = labelOrdinals.get(l);
            if (o == null) { o = nextLabel++; labelOrdinals.put(l, o); }
            return o;
        }

        @Override public void visitInsn(int opcode) { op(opcode); }
        @Override public void visitIntInsn(int opcode, int operand) { op(opcode); i(operand); }
        @Override public void visitVarInsn(int opcode, int v) { op(opcode); i(v); }
        @Override public void visitTypeInsn(int opcode, String type) { op(opcode); s(type); }
        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) { op(opcode); s(owner); s(name); s(desc); }
        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) { op(opcode); s(owner); s(name); s(desc); md.update((byte) (itf ? 1 : 0)); }
        @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) { s(name); s(desc); s(bsm.getOwner()); s(bsm.getName()); s(bsm.getDesc()); i(bsmArgs.length); for (Object a : bsmArgs) s(String.valueOf(a)); }
        @Override public void visitJumpInsn(int opcode, Label label) { op(opcode); i(lab(label)); }
        @Override public void visitLabel(Label label) { op(0x100); i(lab(label)); }
        @Override public void visitLdcInsn(Object value) { op(0x101); s(value == null ? "null" : value.getClass().getName() + ":" + value); }
        @Override public void visitIincInsn(int v, int increment) { op(0x102); i(v); i(increment); }
        @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... keys) { op(0x103); i(min); i(max); i(lab(dflt)); for (Label k : keys) i(lab(k)); }
        @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) { op(0x104); i(lab(dflt)); i(keys.length); for (int k = 0; k < keys.length; k++) { i(keys[k]); i(lab(labels[k])); } }
        @Override public void visitMultiANewArrayInsn(String desc, int dims) { op(0x105); s(desc); i(dims); }
        @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) { op(0x106); i(lab(start)); i(lab(end)); i(lab(handler)); s(type == null ? "*" : type); }
        // frames / lignes / maxs / variables locales : exclus (recalculés par launchers)
    }
}
