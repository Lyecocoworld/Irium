package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;

/**
 * Surface Irium de KeyMappingHelper (forme officielle 26.2).
 * Les touches des mods streamés sont enregistrées dans le registre vanilla
 * via réflexion (options.keyMappings) — pas de menu réglages, mais les
 * touches fonctionnent.
 */
public final class KeyMappingHelper {

    private KeyMappingHelper() {}

    /** Enregistre une touche de mod streamé. */
    public static void registerKeyBinding(KeyMapping mapping) {
        dev.irium.agent.input.ClientKeyBindings.add(mapping);
    }

    /** @return true si la touche vanilla était déjà enregistrée. */
    public static boolean isRegistered(KeyMapping mapping) {
        return dev.irium.agent.input.ClientKeyBindings.isRegistered(mapping);
    }
}
