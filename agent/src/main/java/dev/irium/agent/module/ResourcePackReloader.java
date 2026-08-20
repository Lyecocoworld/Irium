package dev.irium.agent.module;

import dev.irium.agent.SafeLog;

/**
 * M7-B9 : recharge les resource packs du client après une install à chaud.
 *
 * Premier join (pas de cache) : le mod est streamé APRÈS le reload initial du
 * client -> ses assets (lang/textures) sont invisibles. On demande à MC de
 * recharger via Minecraft.reloadResourcePacks() sur le render thread.
 *
 * Coalescing : un seul reload même si plusieurs mods arrivent coup sur coup.
 */
public final class ResourcePackReloader {

    private static volatile boolean pending;

    private ResourcePackReloader() {}

    /** Demande un reload (coalescé, thread-safe). */
    public static void schedule() {
        if (pending) return;
        pending = true;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) { pending = false; return; }
            mc.execute(() -> {
                pending = false;
                try {
                    mc.reloadResourcePacks();
                    SafeLog.offer("[pack] resource packs rechargés (assets du mod visibles)");
                } catch (Throwable t) {
                    SafeLog.offer("[pack] reload échec: " + t);
                }
            });
        } catch (Throwable t) {
            pending = false;
            SafeLog.offer("[pack] planification reload échec: " + t);
        }
    }
}
