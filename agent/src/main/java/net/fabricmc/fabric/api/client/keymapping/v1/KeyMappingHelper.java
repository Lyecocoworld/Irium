package net.fabricmc.fabric.api.client.keymapping.v1;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Surface Irium — vraie forme 26.2 : registerKeyMapping RETOURNE le KeyMapping. */
public final class KeyMappingHelper {

    private KeyMappingHelper() {}

    public static KeyMapping registerKeyMapping(KeyMapping mapping) {
        dev.irium.agent.input.ClientKeyBindings.register(mapping);
        return mapping;
    }

    public static InputConstants.Key getBoundKeyOf(KeyMapping mapping) {
        return mapping.getDefaultKey();
    }
}
