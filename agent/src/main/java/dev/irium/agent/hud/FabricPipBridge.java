package dev.irium.agent.hud;

import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * M7-B11 : bridge PiP (fabric-rendering-v1 25.x) — collecte les factories
 * PictureInPictureRendererRegistry.register(...) des mods streamés (ex. Xaero
 * enregistre MinimapPipRenderer) et les instancie dans la map du GuiRenderer
 * via GuiRendererMixin. Sans ce branchement, la minimap Xaero 26.x ne rend
 * RIEN (son pipeline de rendu EST le pipeline PiP vanilla).
 *
 * Thread-safety : register() peut arriver après le ctor GuiRenderer (mods
 * installés à chaud) -> CopyOnWrite + instanciation immédiate si déjà prêt.
 */
public final class FabricPipBridge {

    private static final List<PictureInPictureRendererRegistry.Factory> FACTORIES = new CopyOnWriteArrayList<>();
    /** M7-X20 : stateClass -> factory des renderers actuellement branchés. */
    private static final java.util.Map<Class<?>, PictureInPictureRendererRegistry.Factory> INSTANTIATED = new java.util.concurrent.ConcurrentHashMap<>();
    /** Map du GuiRenderer vivant (Class<RenderState> -> renderer), null avant le 1er GuiRenderer. */
    private static volatile Map<Class<?>, Object> targetMap;
    private static volatile Object minecraft;

    private FabricPipBridge() {}

    public static void register(PictureInPictureRendererRegistry.Factory factory) {
        if (factory == null) return;
        FACTORIES.add(factory);
        // M7-X20 : shadow-arm — pas d'instanciation hors session Irium (le
        // titre/SP ne doivent montrer AUCUNE minimap). La session branchera.
        if (!dev.irium.agent.module.SessionGate.isActive()) return;
        // à chaud : si un GuiRenderer vit déjà, instancier tout de suite
        Map<Class<?>, Object> map = targetMap;
        Object mc = minecraft;
        if (map != null && mc != null) {
            instantiate(factory, map, mc);
        }
    }

    /** Appelé par GuiRendererMixin au ctor : map mutable + instanciation de tout. */
    @SuppressWarnings("unchecked")
    public static void onGuiRendererReady(Map<Class<?>, Object> map, Object mc) {
        targetMap = map;
        minecraft = mc;
        // M7-X20 : hors session Irium, aucun renderer mod n'est branché —
        // le GuiRenderer reste strictement vanilla (pas de minimap au titre).
        if (!dev.irium.agent.module.SessionGate.isActive()) return;
        for (PictureInPictureRendererRegistry.Factory f : FACTORIES) {
            instantiate(f, map, mc);
        }
    }

    /** M7-X20 : MODSET reçu -> brancher les renderers au GuiRenderer vivant. */
    public static void onSessionBegin() {
        Map<Class<?>, Object> map = targetMap;
        Object mc = minecraft;
        if (map == null || mc == null) return; // pas encore de GuiRenderer : le ctor les prendra
        for (PictureInPictureRendererRegistry.Factory f : FACTORIES) {
            instantiate(f, map, mc);
        }
    }

    /** M7-X20 : fin de session -> retirer les renderers mods de la map vivante. */
    public static void onSessionEnd() {
        Map<Class<?>, Object> map = targetMap;
        if (map == null) { INSTANTIATED.clear(); return; }
        int removed = 0;
        for (Class<?> stateClass : INSTANTIATED.keySet()) {
            if (map.remove(stateClass) != null) removed++;
        }
        INSTANTIATED.clear();
        if (removed > 0) dev.irium.agent.IriumAgent.log("[fabric-pip] session end : " + removed + " renderer(s) retiré(s)");
    }

    @SuppressWarnings("unchecked")
    private static void instantiate(PictureInPictureRendererRegistry.Factory f, Map<Class<?>, Object> map, Object mc) {
        try {
            Object renderer = f.createRenderer(new PictureInPictureRendererRegistry.Context() {
                @Override public net.minecraft.client.Minecraft minecraft() {
                    return (net.minecraft.client.Minecraft) FabricPipBridge.minecraft;
                }
            });
            if (renderer == null) return;
            Class<?> stateClass = (Class<?>) renderer.getClass()
                    .getMethod("getRenderStateClass").invoke(renderer);
            map.put(stateClass, renderer);
            INSTANTIATED.put(stateClass, f);
            dev.irium.agent.IriumAgent.log("[fabric-pip] renderer " + renderer.getClass().getSimpleName()
                    + " branché pour state " + stateClass.getSimpleName());
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[fabric-pip] instanciation échec: " + t);
        }
    }
}
