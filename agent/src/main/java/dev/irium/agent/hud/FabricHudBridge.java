package dev.irium.agent.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * M7-B : pont HUD Fabric → recette Hud.extractRenderState.
 * La recette injecte FabricHudBridge.tick(extractor, deltaTracker) en TAIL de
 * Hud.extractRenderState — chaque élément enregistré reçoit le contexte de rendu.
 */
public final class FabricHudBridge {

    private static final List<Identified> ELEMENTS = new CopyOnWriteArrayList<>();
    private static final Map<Identifier, Identified> BY_ID = new ConcurrentHashMap<>();

    private record Identified(Identifier id, HudElement element) {}

    private FabricHudBridge() {}

    public static void add(Identifier id, HudElement element) {
        if (id == null || element == null) return;
        remove(id);
        Identified e = new Identified(id, element);
        ELEMENTS.add(e);
        BY_ID.put(id, e);
    }

    public static void remove(Identifier id) {
        Identified e = BY_ID.remove(id);
        if (e != null) ELEMENTS.remove(e);
    }

    public static void replace(Identifier id, Function<HudElement, HudElement> replacer) {
        Identified e = BY_ID.get(id);
        if (e == null || replacer == null) return;
        HudElement next = replacer.apply(e.element());
        if (next != null) add(id, next);
    }

    /** Appelé par la recette injectée dans Hud.extractRenderState — ne JAMAIS lever. */
    public static void tick(Object extractor, Object deltaTracker) {
        for (Identified e : ELEMENTS) {
            try {
                e.element().extractRenderState((net.minecraft.client.gui.GuiGraphicsExtractor) extractor,
                        (net.minecraft.client.DeltaTracker) deltaTracker);
            } catch (Throwable ignored) {
                // un élément cassé ne doit pas casser le HUD vanilla
            }
        }
    }

    public static void clearAll() {
        int n = ELEMENTS.size();
        ELEMENTS.clear();
        BY_ID.clear();
        dev.irium.agent.IriumAgent.log("[fabric-hud] éléments vidés (" + n + ")");
    }
}
