package dev.irium.agent.module;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M7-X3 : support accessWidener. 34/60 mods du corpus en dépendent (sodium,
 * lithium, iris…). Le format (v1, namespace officiel = noms runtime MC 26.2
 * mojang-mappés, AUCUN remap nécessaire) :
 *
 *   accessible  class  net/minecraft/...            → classe PUBLIC
 *   extendable  class  net/minecraft/...            → classe PUBLIC+non-FINAL
 *   accessible  field  owner name desc              → champ PUBLIC
 *   extendable  field  owner name desc              → champ PUBLIC+non-FINAL (+owner non-FINAL)
 *   accessible  method owner name desc              → méthode PUBLIC
 *   extendable  method owner name desc              → méthode PUBLIC+non-FINAL (+owner non-FINAL)
 *
 * On applique les règles au ClassNode (tree API) DANS le pipeline du
 * transformer, AVANT sponge-mixin : le bytecode mixin s'applique ensuite sur
 * des classes déjà widennées (même ordre que fabric-loader : AW d'abord).
 * L'owner d'une règle extendable field/method est aussi dé-finalisé quand
 * on transforme sa classe (règle "owner").
 */
public final class AccessWidener {

    private static final int ACC_FINAL = Opcodes.ACC_FINAL;

    /** une règle AW parsée. */
    public static final class Rule {
        public final boolean accessible; // sinon extendable
        public final boolean isClass, isField; // sinon method
        public final String owner, name, desc;
        Rule(boolean accessible, boolean isClass, boolean isField, String owner, String name, String desc) {
            this.accessible = accessible; this.isClass = isClass; this.isField = isField;
            this.owner = owner; this.name = name; this.desc = desc;
        }
        @Override public String toString() {
            return (accessible ? "accessible" : "extendable") + " "
                    + (isClass ? "class " : isField ? "field " : "method ") + owner
                    + (isClass ? "" : " " + name + (desc == null ? "" : " " + desc));
        }
    }

    /** owner (slash) -> règles. On saute l'en-tête "accessWidener v1 namespace". */
    public static Map<String, List<Rule>> parse(String content) {
        Map<String, List<Rule>> out = new ConcurrentHashMap<>();
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("accessWidener ")) continue;
            String[] p = line.split("\\s+");
            // class:    [accessible|extendable] class owner
            // member:   [accessible|extendable] [field|method] owner name [desc]
            if (p.length < 3) continue;
            boolean accessible;
            // M7-X18b : "transitive-accessible"/"transitive-extendable" (module
            // fabric-transitive-access-wideners-v1) = mêmes effets d'accès que
            // accessible/extendable pour nous (la transitivité aux mods dépendants
            // est automatique : tous nos mods partagent le même loader APP).
            // Ex: DebugScreenEntries.register (DebugOverlayClient 02:50).
            if ("accessible".equals(p[0]) || "transitive-accessible".equals(p[0])) accessible = true;
            else if ("extendable".equals(p[0]) || "transitive-extendable".equals(p[0])) accessible = false;
            else continue;
            boolean isClass = "class".equals(p[1]);
            boolean isField = "field".equals(p[1]);
            if (!isClass && !isField && !"method".equals(p[1])) continue;
            if (isClass) {
                if (p.length != 3) continue;
                add(out, new Rule(accessible, true, false, p[2], null, null));
            } else {
                if (p.length < 4) continue;
                String desc = p.length >= 5 ? p[4] : null;
                add(out, new Rule(accessible, false, isField, p[2], p[3], desc));
            }
        }
        return out;
    }

    private static void add(Map<String, List<Rule>> out, Rule r) {
        out.computeIfAbsent(r.owner, k -> new ArrayList<>()).add(r);
    }

    /* ---------------- application (tree API) ---------------- */

    /** widener cumulatif de tous les mods installés : owner -> règles. */
    static final Map<String, List<Rule>> ACTIVE = new ConcurrentHashMap<>();

    /** owners ciblés par AU MOINS une règle extendable member (dé-finaliser l'owner). */
    static final java.util.Set<String> EXTENDABLE_OWNERS = ConcurrentHashMap.newKeySet();

    /**
     * Applique les règles d'un owner sur son ClassNode. Retourne true si modifié.
     * Appelé dans le pipeline du transformer (avant sponge-mixin) pour CHAQUE
     * classe dont le nom matche ACTIVE (ou EXTENDABLE_OWNERS pour dé-finaliser).
     */
    public static boolean apply(ClassNode cn) {
        boolean changed = false;
        List<Rule> rules = ACTIVE.get(cn.name);
        if (rules != null) {
            for (Rule r : rules) {
                if (r.isClass) {
                    if (!isPublic(cn.access) || (!r.accessible && (cn.access & ACC_FINAL) != 0)) {
                        cn.access = widen(cn.access, r.accessible);
                        changed = true;
                    }
                } else if (r.isField) {
                    for (FieldNode f : cn.fields) {
                        if (!f.name.equals(r.name)) continue;
                        if (r.desc != null && !r.desc.equals(f.desc)) continue;
                        if (!isPublic(f.access) || (!r.accessible && (f.access & ACC_FINAL) != 0)) {
                            f.access = widen(f.access, r.accessible);
                            changed = true;
                        }
                    }
                } else {
                    for (MethodNode m : cn.methods) {
                        if (!m.name.equals(r.name)) continue;
                        if (r.desc != null && !r.desc.equals(m.desc)) continue;
                        if (!isPublic(m.access) || (!r.accessible && (m.access & ACC_FINAL) != 0)) {
                            m.access = widen(m.access, r.accessible);
                            changed = true;
                        }
                    }
                }
            }
        }
        // owner d'une règle extendable field/method : la classe devient
        // PUBLIC+non-FINAL (fabric: "the class is also made public and non-final")
        if (EXTENDABLE_OWNERS.contains(cn.name) && ((cn.access & ACC_FINAL) != 0 || !isPublic(cn.access))) {
            cn.access = widen(cn.access, false);
            changed = true;
        }
        return changed;
    }

    /** cette classe a-t-elle des règles AW en attente ? (filtre pipeline rapide) */
    public static boolean concerns(String internalName) {
        return ACTIVE.containsKey(internalName) || EXTENDABLE_OWNERS.contains(internalName);
    }

    private static boolean isPublic(int access) {
        return (access & Opcodes.ACC_PUBLIC) != 0;
    }

    /**
     * accessible  -> PUBLIC
     * extendable  -> PUBLIC + FINAL off
     */
    private static int widen(int access, boolean accessibleOnly) {
        int out = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
        if (!accessibleOnly) out &= ~ACC_FINAL;
        return out;
    }
}
