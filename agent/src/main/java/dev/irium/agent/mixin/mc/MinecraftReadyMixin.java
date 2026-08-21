package dev.irium.agent.mixin.mc;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M7-B12 : activation PRÉCOCE des entrypoints des mods armés.
 *
 * Les mixins des mods s'appliquent à la définition des classes MC (armement
 * premain/attach précoce) — leurs handlers sont donc vivants dès le boot.
 * Certains supposent le mod initialisé (xaerolib : XaeroLib.INSTANCE dans
 * Player.onTickHead -> NPE au premier tick d'entité si l'entrypoint n'a pas
 * tourné). Sur vraie Fabric, les entrypoints s'exécutent AVANT le title
 * screen ; ici on les déclenche à la fin du ctor Minecraft : instance
 * présente, ressources rechargées (assets des mods visibles), mais AUCUN
 * monde/tick/join possible. C'est le dernier point sûr avant le game loop.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftReadyMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void irium$onMinecraftReady(CallbackInfo ci) {
        try {
            dev.irium.agent.module.FabricModHost.onMinecraftReady();
        } catch (Throwable t) {
            dev.irium.agent.SafeLog.offer("irium activation précoce échec: " + t);
        }
    }
}
