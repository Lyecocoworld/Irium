package dev.irium.agent.module;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * M7-X3 : pont accessWidener <-> pipeline transformer. Applatit les règles
 * sur le bytecode via tree API (read -> AccessWidener.apply -> write).
 * Le widener agit AVANT sponge-mixin (ordre fabric-loader).
 */
public final class AccessWidenerGateway {

    private AccessWidenerGateway() {}

    /**
     * Applique les règles AW actives sur le bytecode d'une classe, si elle est
     * ciblée. Retourne le bytecode (éventuellement) modifié — jamais null si
     * bytes != null ; toute erreur -> bytes inchangés (l'install ne doit pas
     * échouer sur une règle malformée).
     */
    public static byte[] widen(String internalName, byte[] bytes) {
        if (bytes == null) return null;
        if (!AccessWidener.concerns(internalName)) return bytes;
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            if (!AccessWidener.apply(cn)) return bytes;
            ClassWriter cw = new ClassWriter(0); // pas de COMPUTE_FRAMES : accès flags seulement
            cn.accept(cw);
            dev.irium.agent.SafeLog.v("[irium:aw]", "widen " + internalName);
            return cw.toByteArray();
        } catch (Throwable t) {
            dev.irium.agent.SafeLog.v("[irium:aw]", "widen échec " + internalName + ": " + t);
            return bytes;
        }
    }
}
