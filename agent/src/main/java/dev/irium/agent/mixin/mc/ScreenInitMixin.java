package dev.irium.agent.mixin.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M7-B12 : fire ScreenEvents BEFORE_INIT/AFTER_INIT (surface
 * fabric-screen-api-v1) à chaque Screen.init — c'est ainsi que ModMenu
 * insère son bouton "Mods" dans le title screen. Même pattern que le
 * ScreenMixin de fabric-screen-api-v1.
 */
@Mixin(Screen.class)
public abstract class ScreenInitMixin {

    @Inject(method = "init(II)V", at = @At("HEAD"))
    private void irium$beforeInit(CallbackInfo ci) {
        // M7-X20 : surface gated — hors session Irium (titre, SP, autre
        // serveur), le ScreenEvents ne tire PAS -> pas de bouton "Mods".
        if (!dev.irium.agent.module.SessionGate.isActive()) return;
        try {
            Screen self = (Screen) (Object) this;
            Minecraft mc = Minecraft.getInstance();
            int w = mc == null ? 0 : mc.getWindow().getGuiScaledWidth();
            int h = mc == null ? 0 : mc.getWindow().getGuiScaledHeight();
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT
                    .invoker().beforeInit(mc, self, w, h);
        } catch (Throwable t) {
            dev.irium.agent.SafeLog.offer("irium screen beforeInit: " + t);
        }
    }

    @Inject(method = "init(II)V", at = @At("RETURN"))
    private void irium$afterInit(CallbackInfo ci) {
        // M7-X20 : surface gated (cf. beforeInit).
        if (!dev.irium.agent.module.SessionGate.isActive()) return;
        try {
            Screen self = (Screen) (Object) this;
            Minecraft mc = Minecraft.getInstance();
            int w = mc == null ? 0 : mc.getWindow().getGuiScaledWidth();
            int h = mc == null ? 0 : mc.getWindow().getGuiScaledHeight();
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT
                    .invoker().afterInit(mc, self, w, h);
        } catch (Throwable t) {
            dev.irium.agent.SafeLog.offer("irium screen afterInit: " + t);
        }
    }
}
