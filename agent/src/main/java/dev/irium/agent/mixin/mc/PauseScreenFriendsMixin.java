package dev.irium.agent.mixin.mc;

import dev.irium.agent.SafeLog;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M7-X24 : Friends List REMOVE complet du pause menu (décision user).
 * Le bouton vanilla (icône social, rangée du bas) n'existe pas en mode Irium.
 *
 * javap 26.2 : createPauseMenu() crée le bouton via CommonButtons.friends()
 * et l'ajoute à un LinearLayout (rangée icons) avant addRenderableWidget
 * du layout complet. removeWidget au TAIL le sort des listes
 * render/narration/focus de l'écran ; le champ privé 'friends' n'est PAS
 * nullé (onFriendListUpdate() continue sur l'objet orphelin — inert, zéro NPE).
 *
 * Hors mode armé (boot classique), ce mixin ne fait strictement rien.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenFriendsMixin extends net.minecraft.client.gui.screens.Screen {

    @Shadow(aliases = {"friends"})
    private FriendsButton friends;

    protected PauseScreenFriendsMixin(net.minecraft.network.chat.Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void irium$removeFriendsButton(CallbackInfo ci) {
        // Boot classique : pause menu 100% vanilla, bouton vanilla conservé.
        if (!dev.irium.agent.module.FabricModHost.isBootedByIrium()
                || dev.irium.agent.module.FabricModHost.armedServer() == null) {
            return;
        }
        if (this.friends == null) {
            return;
        }
        try {
            this.removeWidget(this.friends);
            SafeLog.offer("[gateway] friends button REMOVE (pause) — bouton vanilla retiré");
        } catch (Throwable t) {
            SafeLog.offer("[gateway] friends REMOVE échoué (pause): " + t);
        }
    }
}
