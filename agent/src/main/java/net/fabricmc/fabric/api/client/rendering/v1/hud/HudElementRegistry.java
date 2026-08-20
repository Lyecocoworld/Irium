package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

/**
 * Surface Irium de HudElementRegistry (formes officielles 26.2 — interface).
 */
public interface HudElementRegistry {

    static void addFirst(Identifier id, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    static void addLast(Identifier id, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    static void attachElementBefore(Identifier id, Identifier other, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    static void attachElementAfter(Identifier id, Identifier other, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    static void removeElement(Identifier id) {
        dev.irium.agent.hud.FabricHudBridge.remove(id);
    }

    static void replaceElement(Identifier id, java.util.function.Function<HudElement, HudElement> replacer) {
        dev.irium.agent.hud.FabricHudBridge.replace(id, replacer);
    }
}
