package net.fabricmc.fabric.api.client.rendering.v1;

/**
 * Surface Irium de PictureInPictureRendererRegistry (fabric-rendering-v1 25.x).
 * Xaero référence ce registre dans son chargeur de contexte plateforme : sans
 * la classe, NoClassDefFoundError au démarrage du mod.
 *
 * L'implémentation réelle crée des renderers "picture in picture" (livre,
 * bannière, entité) branchés dans le pipeline GUI vanilla. La surface Irium
 * accepte l'enregistrement mais ne crée pas de renderer — la minimap rend
 * dans son propre layer HUD (déjà couvert par FabricHudBridge).
 */
public final class PictureInPictureRendererRegistry {

    private PictureInPictureRendererRegistry() {}

    /** Contexte passé à la factory (la surface Irium expose Minecraft). */
    public interface Context {
        net.minecraft.client.Minecraft minecraft();
    }

    /**
     * Factory du renderer — la surface Irium ne l'appelle jamais, mais la
     * signature doit être IDENTIQUE au vrai module (retour PictureInPictureRenderer<?>)
     * : les lambdas du mod encodent ce descripteur dans leur invokedynamic —
     * un retour Object = LambdaMetafactory LinkageError au runtime.
     */
    @FunctionalInterface
    public interface Factory {
        net.minecraft.client.gui.render.pip.PictureInPictureRenderer<?> createRenderer(Context context);
    }

    /** Accepté et transmis au bridge PiP (instancié dans le GuiRenderer au ctor). */
    public static void register(Factory factory) {
        dev.irium.agent.hud.FabricPipBridge.register(factory);
    }
}
