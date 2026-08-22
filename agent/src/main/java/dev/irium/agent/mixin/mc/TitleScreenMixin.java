package dev.irium.agent.mixin.mc;

import dev.irium.agent.SafeLog;
import dev.irium.agent.module.FabricModHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M7-X21 : mode "expérience complète". Le titre reste LE titre vanilla,
 * agencement normal, rien de remplacé. Deux retargets uniquement :
 *
 *   - Singleplayer : bouton grisé (active=false), clic inerte — le serveur
 *     Irium armé a l'autorité sur cette session.
 *   - Multijoueur : même bouton, même position, même libellé — mais le clic
 *     joint directement le serveur armé (ConnectScreen.startConnecting).
 *
 * Options / Quitter / Realms / accessibilité / langue : intacts.
 * Hors mode armé (boot classique), ce mixin ne fait strictement rien.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends net.minecraft.client.gui.screens.Screen {

    protected TitleScreenMixin(net.minecraft.network.chat.Component title) {
        super(title);
    }

    @Inject(method = "createNormalMenuOptions", at = @At("TAIL"))
    private void irium$retargetTitle(int i, int j, CallbackInfoReturnable<Integer> cir) {
        if (!FabricModHost.isBootedByIrium() || FabricModHost.armedServer() == null) {
            return; // boot classique : titre 100% vanilla, ne rien toucher
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            String addr = FabricModHost.armedServer();

            // Vanilla ajoute, dans l'ordre : Singleplayer, Multijoueur, Realms.
            // On identifie par clé de traduction quand possible, ordre sinon.
            AbstractButton sp = null;
            AbstractButton mp = null;
            AbstractButton realms = null;
            int seen = 0;
            for (var child : this.children()) {
                if (!(child instanceof AbstractButton b)) continue;
                String key = translationKey(b.getMessage());
                seen++;
                if (key != null) {
                    if ("menu.singleplayer".equals(key)) sp = b;
                    else if ("menu.multiplayer".equals(key)) mp = b;
                    else if ("menu.online".equals(key)) realms = b;
                } else {
                    if (seen == 1 && sp == null) sp = b;
                    else if (seen == 2 && mp == null) mp = b;
                    else if (seen == 4 && realms == null) realms = b;
                }
            }
            if (sp == null || mp == null) {
                SafeLog.offer("[gateway] SP/MP introuvables sur le titre — titre vanilla conservé");
                return;
            }

            // SP ET Realms : grisés, clic inert. Message au survol pour expliquer.
            lockButton(sp);
            if (realms != null) {
                lockButton(realms);
            }

            // MP : même position/taille/libellé, onPress = join direct.
            Button replacement = Button.builder(
                            mp.getMessage(),
                            btn -> IriumConnect.connect(mc, (TitleScreen) (Object) this, addr))
                    .bounds(mp.getX(), mp.getY(), mp.getWidth(), mp.getHeight())
                    .build();
            this.removeWidget(mp);
            this.addRenderableWidget(replacement);

            SafeLog.offer("[gateway] titre vanilla: SP" + (realms != null ? "+Realms" : "")
                    + " grisés, MP -> join direct " + addr
                    + " (" + mp.getWidth() + "x" + mp.getHeight() + ")");
        } catch (Throwable t) {
            SafeLog.offer("[gateway] retarget titre échoué: " + t);
        }
    }

    /** Grise un bouton du titre (clic inert) + message de survol explicatif.
     *  Le libellé vanilla est conservé tel quel. */
    private void lockButton(AbstractButton b) {
        b.active = false;
        try {
            java.lang.reflect.Method m = AbstractWidget.class.getMethod("setInactiveMessage", Component.class);
            m.setAccessible(true);
            m.invoke(b, Component.literal("Verrouillé — serveur Irium armé"));
        } catch (Throwable ignore) {
            // setInactiveMessage absent : le grisage suffit
        }
    }

    /** Clé de traduction du message si translatable, sinon null. */
    private static String translationKey(Component c) {
        try {
            if (c == null) return null;
            Object contents = c.getClass().getMethod("getContents").invoke(c);
            if (contents instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                return tc.getKey();
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
}
