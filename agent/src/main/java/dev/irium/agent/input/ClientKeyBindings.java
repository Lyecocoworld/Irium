package dev.irium.agent.input;

import net.minecraft.client.KeyMapping;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Touches des mods streamés : cumulées ici, fusionnées dans le tableau
 * vanilla (Options.keyMappings) à CHAQUE enregistrement — de façon
 * ADDITIVE (seulement les touches pas déjà présentes).
 *
 * M7-B12 fix : l'ancienne logique "une seule injection" faisait que le
 * premier mod activé (SVC -> PTT) verrouillait le tableau — les touches
 * des mods suivants (Xaero : 10+) n'étaient jamais fusionnées. Symptôme
 * réel : seul le PTT visible dans le menu Controls.
 */
public final class ClientKeyBindings {

    private static final List<KeyMapping> MOD_KEYS = new ArrayList<>();
    private static Field cachedField;

    private ClientKeyBindings() {}

    public static synchronized void register(KeyMapping mapping) {
        if (mapping == null || MOD_KEYS.contains(mapping)) return;
        MOD_KEYS.add(mapping);
        inject();
    }

    public static synchronized boolean isRegistered(KeyMapping mapping) {
        return MOD_KEYS.contains(mapping);
    }

    /**
     * Fusion additive : ajoute au tableau vanilla uniquement les touches
     * mod absentes. Appelé à chaque register() — idempotent.
     */
    private static synchronized void inject() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            if (cachedField == null) {
                cachedField = mc.options.getClass().getField("keyMappings");
                cachedField.setAccessible(true);
            }
            KeyMapping[] current = (KeyMapping[]) cachedField.get(mc.options);
            List<KeyMapping> toAdd = new ArrayList<>();
            for (KeyMapping k : MOD_KEYS) {
                boolean present = false;
                for (KeyMapping c : current) {
                    if (c == k) { present = true; break; }
                }
                if (!present) toAdd.add(k);
            }
            if (toAdd.isEmpty()) return;
            KeyMapping[] merged = new KeyMapping[current.length + toAdd.size()];
            System.arraycopy(current, 0, merged, 0, current.length);
            for (int i = 0; i < toAdd.size(); i++) merged[current.length + i] = toAdd.get(i);
            cachedField.set(mc.options, merged);
            dev.irium.agent.SafeLog.offer("[fabric-keys] " + merged.length
                    + " touches au total (+" + toAdd.size() + " mods)");
        } catch (Throwable t) {
            // champ renommé/privé : les touches restent fonctionnelles (KeyboardHook),
            // juste absentes du menu visuel
            dev.irium.agent.SafeLog.offer("[fabric-keys] injection options échec: " + t);
        }
    }

    /** Sandbox : oubliées à la déconnexion. */
    public static synchronized void clear() {
        MOD_KEYS.clear();
    }
}
