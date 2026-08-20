package net.fabricmc.fabric.api.client.rendering.v1.hud;

/** Surface Irium de HudElement (forme officielle 26.2). */
public interface HudElement {
    void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor extractor,
                            net.minecraft.client.DeltaTracker deltaTracker);
}
