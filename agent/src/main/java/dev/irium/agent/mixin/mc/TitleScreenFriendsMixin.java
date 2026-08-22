package dev.irium.agent.mixin.mc;

import dev.irium.agent.SafeLog;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M7-X24 : Friends List REMOVE complet du title screen (décision user,
 * investigation 2026-08-22). Le bouton vanilla (coin haut, icône)
 * n'existe pas en mode Irium.
 *
 * Technique : removeWidget au TAIL de init() — le bouton sort des listes
 * render/narration/focus de l'écran. Le champ privé 'friends' N'EST PAS
 * nullé : tick() continue d'appeler refreshIncomingRequestCount() sur
 * l'objet orphelin (inerte, jamais rendu) — zéro NPE garanti.
 * Re-init (resize) → init() re-crée le bouton → TAIL le re-supprime.
 *
 * Hors mode armé (boot classique), ce mixin ne fait strictement rien.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenFriendsMixin extends net.minecraft.client.gui.screens.Screen {

    @Shadow(aliases = {"friends"})
    private FriendsButton friends;

    protected TitleScreenFriendsMixin(net.minecraft.network.chat.Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void irium$removeFriendsButton(CallbackInfo ci) {
        // Boot classique : titre 100% vanilla, bouton vanilla conservé.
        if (!dev.irium.agent.module.FabricModHost.isBootedByIrium()
                || dev.irium.agent.module.FabricModHost.armedServer() == null) {
            return;
        }
        if (this.friends == null) {
            return;
        }
        try {
            this.removeWidget(this.friends);
            SafeLog.offer("[gateway] friends button REMOVE (title) — bouton vanilla retiré");
        } catch (Throwable t) {
            SafeLog.offer("[gateway] friends REMOVE échoué (title): " + t);
        }
    }
}
