package dev.irium.agent.mixin.mc;

import dev.irium.agent.SafeLog;
import dev.irium.agent.module.IriumPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * M7-B9 : ajoute la pack source Irium à TOUT PackRepository construit.
 *
 * Le ctor vanilla fige `sources` en ImmutableSet — on le remplace par une copie
 * mutable + notre source (même pattern que le PackRepositoryMixin de SVC et que
 * fabric-resource-loader). Comme la config irium.mixins.json est armée dès
 * l'attach (avant la définition des classes MC), la source est en place pour le
 * PREMIER reload -> les assets des mods streamés (lang, textures) existent au
 * boot, pas seulement après le join.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void irium$injectPackSource(RepositorySource[] sources, CallbackInfo ci) {
        try {
            Set<RepositorySource> nu = new HashSet<>(this.sources);
            nu.add(IriumPackSource.INSTANCE);
            this.sources = nu;
        } catch (Throwable t) {
            SafeLog.offer("irium pack source injection échec: " + t);
        }
    }
}
