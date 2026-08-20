package dev.irium.agent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

/**
 * Callback statique injecté dans DefaultChannelPipeline.addLast/addBefore.
 * Contrat strict : ne jamais lever, ne jamais bloquer — sinon on casse TOUT
 * canal netty du host (y compris hors Minecraft).
 */
public final class IriumHooks {

    private IriumHooks() {}

    public static void onHandlerAdded(ChannelPipeline p, String name) {
        try {
            if (name == null || name.isEmpty()) return;
            // Tout handler nommé peut précéder 'decoder' dans l'ordre d'ajout :
            // on tente l'installation à chaque fois (idempotent si déjà en place).
            Channel ch = p.channel();
            Runnable task = () -> {
                try {
                    IriumTap.install(p);
                } catch (Throwable t) {
                    IriumAgent.log("[tap] install échoué (ignoré) : " + t);
                }
            };
            if (ch.isRegistered()) {
                ch.eventLoop().execute(task); // différé : jamais de mutation pipeline pendant l'itération
            }
        } catch (Throwable t) {
            // silence total par conception
        }
    }
}
