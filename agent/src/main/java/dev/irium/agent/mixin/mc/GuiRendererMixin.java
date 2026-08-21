package dev.irium.agent.mixin.mc;

import dev.irium.agent.SafeLog;
import dev.irium.agent.hud.FabricPipBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * M7-B11 : branche les renderers PiP des mods streamés (ex. MinimapPipRenderer
 * de Xaero) dans la map du GuiRenderer vanilla — même pattern que
 * fabric-rendering-v1 GuiRendererMixin : la map ImmutableMap du ctor devient
 * une IdentityHashMap mutable, puis FabricPipBridge instancie les factories
 * enregistrées. Sans ça, Xaero 26.x n'affiche pas la minimap (son rendu EST
 * le pipeline PiP).
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow @Final @Mutable private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void irium$branchPipRenderers(CallbackInfo ci) {
        try {
            // map vanilla immuable -> copie mutable (identité des Class keys)
            this.pictureInPictureRenderers = new IdentityHashMap<>(this.pictureInPictureRenderers);
            FabricPipBridge.onGuiRendererReady(castMap(this.pictureInPictureRenderers), Minecraft.getInstance());
        } catch (Throwable t) {
            SafeLog.offer("irium pip branch échec: " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> castMap(Map<?, ?> m) {
        return (Map<Class<?>, Object>) m;
    }
}
