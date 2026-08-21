package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;

/**
 * Adaptateur Irium — keymapping-api-v1 (fabric-key-binding-api-v1).
 * M7-X2 : registerKeyMapping(KeyMapping) référencé par Mod Menu/voicechat.
 * Délègue à OptionScreen/minecraft: la KeyMapping enregistrée côté MC.
 */
public final class KeyMappingHelper {
    private KeyMappingHelper() {}

    /** M7-X3 : keybinds enregistrés avant la construction de Options (activation
     *  précoce à `instance = this`, instruction ~91) — flushés au RETURN du ctor. */
    private static final java.util.List<KeyMapping> PENDING = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Enregistre la keymap dans le registre client (options.txt accessible). */
    public static KeyMapping registerKeyMapping(KeyMapping mapping) {
        PENDING.add(mapping);
        tryFlush(mapping);
        return mapping;
    }

    private static void tryFlush(KeyMapping mapping) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.options == null) return; // pas prêt -> reste en file
            doRegister(mc, mapping);
            PENDING.remove(mapping);
        } catch (Throwable t) {
            // best-effort : la keymap reste fonctionnelle via Category
        }
    }

    /** Second stage du ctor Minecraft : options construits -> tout flusher. */
    public static void flushPending() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        for (KeyMapping k : PENDING) {
            try {
                doRegister(mc, k);
                PENDING.remove(k);
            } catch (Throwable ignored) {}
        }
    }

    private static void doRegister(net.minecraft.client.Minecraft mc, KeyMapping mapping) {
        // Minecraft.addKeyMapping est private -> via réflexion sur le champ
        // options.keyMappings du Minecraft.getInstance()
        try {
            var options = mc.options;
            java.lang.reflect.Field f = options.getClass().getDeclaredField("keyMappings");
            f.setAccessible(true);
            KeyMapping[] cur = (KeyMapping[]) f.get(options);
            for (KeyMapping existing : cur) {
                if (existing == mapping) return; // déjà là (double flush)
            }
            KeyMapping[] next = java.util.Arrays.copyOf(cur, cur.length + 1);
            next[cur.length] = mapping;
            f.set(options, next);
        } catch (Throwable t) {
            // best-effort : la keymap reste fonctionnelle via Category
        }
    }

    /**
     * M7-X3 : clé actuellement liée. SVC PTTKeyHandler.onMouseEvent l'appelle au
     * PREMIER CLIC SOURIS (crash 19:26 — NoSuchMethodError FATAL). Le vrai fabric
     * utilise un mixin accessor ; ici réflexion sur le champ protégé `key`,
     * fallback getDefaultKey().
     */
    public static com.mojang.blaze3d.platform.InputConstants.Key getBoundKeyOf(KeyMapping mapping) {
        try {
            java.lang.reflect.Field f = KeyMapping.class.getDeclaredField("key");
            f.setAccessible(true);
            com.mojang.blaze3d.platform.InputConstants.Key k =
                    (com.mojang.blaze3d.platform.InputConstants.Key) f.get(mapping);
            return k != null ? k : mapping.getDefaultKey();
        } catch (Throwable t) {
            return mapping.getDefaultKey();
        }
    }
}
