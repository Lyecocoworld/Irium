package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;

/**
 * Adaptateur Irium — keymapping-api-v1 (fabric-key-binding-api-v1).
 * M7-X2 : registerKeyMapping(KeyMapping) référencé par Mod Menu/voicechat.
 * Délègue à OptionScreen/minecraft: la KeyMapping enregistrée côté MC.
 */
public final class KeyMappingHelper {
    private KeyMappingHelper() {}

    /** Enregistre la keymap dans le registre client (options.txt accessible). */
    public static KeyMapping registerKeyMapping(KeyMapping mapping) {
        try {
            // Minecraft.addKeyMapping est private -> via réflexion sur le champ
            // options.keyMappings du Minecraft.getInstance()
            var mc = net.minecraft.client.Minecraft.getInstance();
            var options = mc.options;
            java.lang.reflect.Field f = options.getClass().getDeclaredField("keyMappings");
            f.setAccessible(true);
            KeyMapping[] cur = (KeyMapping[]) f.get(options);
            KeyMapping[] next = java.util.Arrays.copyOf(cur, cur.length + 1);
            next[cur.length] = mapping;
            f.set(options, next);
        } catch (Throwable t) {
            // best-effort : la keymap reste fonctionnelle via Category
        }
        return mapping;
    }
}
