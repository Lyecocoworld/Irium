import dev.irium.agent.hud.HudBridge;
import dev.irium.agent.module.IriumContext;
import dev.irium.agent.module.IriumModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * M5 LIVE — module HUD streamé pour le VRAI client 26.2.
 *
 * Poussé par le serveur après classification AGENT. La recette a injecté
 * HudBridge.tick(extractor) à la fin de Hud.extractRenderState(...).
 * Ce module dessine "Irium linked" en jaune Irium sur fond gemstone —
 * entièrement par réflexion (le module n'est compilé que contre l'API agent).
 */
public class HudModule implements IriumModule {

    private static final int BG = 0xA60A0A0B;    // gemstone translucide
    private static final int FG = 0xFFFFD84D;    // jaune Irium
    private static final int X = 8, Y = 8, W = 84, H = 16;

    private Method mText;
    private Method mFill;
    private Object font;
    private volatile boolean broken;

    @Override
    public void onEnable(IriumContext ctx) {
        ctx.log("HudModule ACTIF — dessin 'Irium linked' sur le HUD du client");
        ctx.emit("hud", "module HUD chargé, renderer branché sur extractRenderState");
        try {
            Class<?> ext = Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor");
            Class<?> fontCls = Class.forName("net.minecraft.client.gui.Font");
            mText = ext.getMethod("text", fontCls, String.class, int.class, int.class, int.class);
            mFill = ext.getMethod("fill", int.class, int.class, int.class, int.class, int.class);
            Object mc = Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance").invoke(null);
            Field f = mc.getClass().getField("font");
            font = f.get(mc);
        } catch (Throwable t) {
            broken = true;
            ctx.log("HudModule: client non compatible (" + t.getClass().getSimpleName() + ") — HUD désactivé");
            ctx.emit("hud_error", t.getClass().getSimpleName() + ": " + t.getMessage());
            return;
        }
        ctx.hud(() -> {
            Object g = HudBridge.lastExtractor;
            if (g == null || broken) return;
            try {
                mFill.invoke(g, X - 2, Y - 2, X + W, Y + H, BG);
                mText.invoke(g, font, "Irium linked", X + 2, Y + 2, FG);
            } catch (Throwable t) {
                broken = true; // plus jamais tenté — jamais casser le rendu hôte
            }
        });
    }

    @Override
    public void onDisable() {
        System.err.println("[irium][mod] HudModule désactivé (HUD disparu)");
    }
}
