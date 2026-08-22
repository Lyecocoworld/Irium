package dev.irium.agent.mixin.mc;

import dev.irium.agent.SafeLog;
import dev.irium.agent.module.FabricModHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M7-X22 : en mode "expérience complète", l'écran multijoueur vanilla n'a
 * aucune raison d'exister — le serveur est armé, le join est direct.
 *
 * Point d'entrée unique constaté (javap 26.2) : Minecraft.disconnectFromWorld
 * construit un JoinMultiplayerScreen et l'installe via Gui.setScreen (pas
 * setScreenAndShow). On intercepte donc Gui.setScreen : si vanilla veut
 * installer un JoinMultiplayerScreen (déconnexion, bouton Retour, Realms…),
 * on substitue le titre AVANT installation — pas d'init() partielle, pas de
 * cycle de vie cassé. Le TitleScreen recréé repasse par TitleScreenMixin
 * (SP/Realms grisés, MP -> join direct).
 *
 * En boot classique, ce mixin ne fait strictement rien.
 */
@Mixin(net.minecraft.client.gui.Gui.class)
public abstract class MinecraftScreenGate {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void irium$gateMultiplayerScreen(Screen screen, CallbackInfo ci) {
        if (!FabricModHost.isBootedByIrium() || FabricModHost.armedServer() == null) {
            return; // boot classique : comportement 100% vanilla
        }
        if (screen instanceof JoinMultiplayerScreen) {
            ci.cancel(); // l'écran multijoueur n'est JAMAIS installé
            SafeLog.offer("[gateway] écran multijoueur intercepté -> retour titre");
            // installer le titre via le chemin standard (repasse par TitleScreenMixin)
            Minecraft.getInstance().setScreenAndShow(new TitleScreen());
        }
    }
}
