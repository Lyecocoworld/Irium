package dev.irium.agent.input;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Touches des mods streamés : cumulées ici, injectées dans les options
 * vanilla (KeyMapping[] options.keyMappings) à la prochaine ouverture des
 * réglages — jamais de crash si la reflexion échoue (touches fonctionnent
 * via KeyboardHook, juste absentes du menu).
 */
public final class ClientKeyBindings {

    private static final List<KeyMapping> MOD_KEYS = new ArrayList<>();

    private ClientKeyBindings() {}

    public static synchronized void register(KeyMapping mapping) {
        if (mapping == null || MOD_KEYS.contains(mapping)) return;
        MOD_KEYS.add(mapping);
        inject();
    }

    public static synchronized boolean isRegistered(KeyMapping mapping) {
        return MOD_KEYS.contains(mapping);
    }

    /** Tente d'injecter les touches dans options.keyMappings (reflexion sûre). */
    private static synchronized void inject() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.options == null) return;
            java.lang.reflect.Field f = mc.options.getClass().getField("keyMappings");
            f.setAccessible(true);
            KeyMapping[] current = (KeyMapping[]) f.get(mc.options);
            for (KeyMapping k : current) {
                if (MOD_KEYS.contains(k)) return; // déjà injecté
            }
            KeyMapping[] merged = new KeyMapping[current.length + MOD_KEYS.size()];
            System.arraycopy(current, 0, merged, 0, current.length);
            for (int i = 0; i < MOD_KEYS.size(); i++) merged[current.length + i] = MOD_KEYS.get(i);
            f.set(mc.options, merged);
        } catch (Throwable t) {
            // champs renommés/privés : les touches restent locales au mod
        }
    }

    /** Sandbox : oubliées à la déconnexion. */
    public static synchronized void clear() {
        MOD_KEYS.clear();
    }
}
