package dev.irium.agent.mixin.mc;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M7-B11 : registres client ré-ouverts aux écritures (sémantique
 * fabric-registry-sync-v0). Sur vraie Fabric, les entrypoints des mods
 * tournent AVANT le gel de BuiltInRegistries — un mod peut enregistrer
 * mob effects, sons, etc. Chez Irium le mod est streamé au JOIN : le
 * client vanilla a déjà gelé ses registres depuis le boot, et
 * "Registry is already frozen" tue le mod (Xaero : xaerominimap:no_minimap).
 *
 * validateWrite n'est qu'un bug-catcher vanilla (rien côté client ne
 * dépend de l'exception) : on le neutralise, les écritures tardives
 * des mods streamés passent comme sur Fabric.
 */
@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin {

    /** Couvre validateWrite() et validateWrite(ResourceKey) — HEAD cancel. */
    @Inject(method = "validateWrite*", at = @At("HEAD"), cancellable = true)
    private void irium$allowLateModRegistration(CallbackInfo ci) {
        ci.cancel();
    }
}
