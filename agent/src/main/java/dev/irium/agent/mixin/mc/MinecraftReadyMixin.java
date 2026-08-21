package dev.irium.agent.mixin.mc;

import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M7-X12 : activation des entrypoints des mods armés — fidèle à fabric-loader.
 *
 * Point d'injection choisi d'après le bytecode du ctor Minecraft 26.2 :
 *   offset 133 : instance = this            (Minecraft.getInstance() != null)
 *   offset 309 : resourcePackRepository      (SVC addResourcePackSource)
 *   offset 394 : user = ...                 (XaeroLib Patreon.checkPatreon getUser)
 *   offset ~2752 : setFullscreen            (mixin sodium core.MinecraftMixin
 *                                             -> SodiumClientMod.options() — le
 *                                             champ CONFIG doit être peuplé AVANT)
 *
 * Inject APRÈS putfield user (ordi 394) : toutes les publications précoce
 * faites, ~2300 offsets avant le mixin sodium. HEAD (ancienne M7-X12) cassait
 * SVC (clinit FabricClientCompatibilityManager capture mc=null) et RETURN
 * (M7-B12) cassait sodium (Config not yet available).
 *
 * RETURN du ctor = second stage : flush des keybinds en attente (Options
 * construit à ce point — KeyMappingHelper.PENDING).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftReadyMixin {

    /** Après putfield user : instance + resourcePackRepository + user publiés. */
    @Inject(method = "<init>", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;user:Lnet/minecraft/client/User;",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.BY, by = 1))
    private void irium$onMinecraftEarly(CallbackInfo ci) {
        try {
            dev.irium.agent.module.FabricModHost.onMinecraftReady();
        } catch (Throwable t) {
            dev.irium.agent.SafeLog.offer("irium activation précoce échec: " + t);
        }
    }

    /** Second stage : options/ressources construits -> flush keybinds en attente. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void irium$onMinecraftCtorDone(CallbackInfo ci) {
        try {
            net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.flushPending();
        } catch (Throwable t) {
            dev.irium.agent.SafeLog.offer("irium flush keybinds échec: " + t);
        }
    }
}
