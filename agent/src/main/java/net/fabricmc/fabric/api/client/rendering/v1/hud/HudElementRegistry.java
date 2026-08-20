package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * Surface Irium de HudElementRegistry (forme officielle 26.2 : v1/hud/).
 * Les éléments sont portés par le pont Hud de l'agent (recette sur
 * Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)V).
 */
public final class HudElementRegistry {

    public static void addFirst(Identifier id, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    public static void addLast(Identifier id, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    public static void attachElementBefore(Identifier id, Identifier other, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    public static void attachElementAfter(Identifier id, Identifier other, HudElement element) {
        dev.irium.agent.hud.FabricHudBridge.add(id, element);
    }

    public static void removeElement(Identifier id) {
        dev.irium.agent.hud.FabricHudBridge.remove(id);
    }

    public static void replaceElement(Identifier id, Function<HudElement, HudElement> replacer) {
        dev.irium.agent.hud.FabricHudBridge.replace(id, replacer);
    }

    private HudElementRegistry() {}
}
