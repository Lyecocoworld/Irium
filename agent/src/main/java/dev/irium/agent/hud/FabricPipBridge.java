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
    /** Map du GuiRenderer vivant (Class<RenderState> -> renderer), null avant le 1er GuiRenderer. */
    private static volatile Map<Class<?>, Object> targetMap;
    private static volatile Object minecraft;

    private FabricPipBridge() {}

    public static void register(PictureInPictureRendererRegistry.Factory factory) {
        if (factory == null) return;
        FACTORIES.add(factory);
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
        for (PictureInPictureRendererRegistry.Factory f : FACTORIES) {
            instantiate(f, map, mc);
        }
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
            dev.irium.agent.IriumAgent.log("[fabric-pip] renderer " + renderer.getClass().getSimpleName()
                    + " branché pour state " + stateClass.getSimpleName());
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[fabric-pip] instanciation échec: " + t);
        }
    }
}
