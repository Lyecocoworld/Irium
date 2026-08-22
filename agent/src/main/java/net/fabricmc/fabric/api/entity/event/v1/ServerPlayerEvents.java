package net.fabricmc.fabric.api.entity.event.v1;

import net.minecraft.resources.Identifier;

/**
 * Surface Irium côté CLIENT : les mods universels (ex. Xaero) référencent les
 * événements joueur serveur. Sur un client distant, ces événements ne se
 * déclenchent jamais — Event<T> vides (pattern Irium des surfaces server-side).
 *
 * M7-X16b : AUTO-CONTENU. Ce stub est le seul ServerPlayerEvents du classpath
 * (aucun JiJ du modset ne fournit entity-event-api-v1). Son clinit ne doit
 * dépendre d'AUCUNE classe d'un module JiJ (EventFactory du vrai fabric-api-base
 * n'a PAS la forme 1-arg -> NoSuchMethodError -> clinit mort -> NoClassDefFound
 * en cascade, crash 02:32). Event local minimal, zéro dépendance externe.
 */
public final class ServerPlayerEvents {

    private ServerPlayerEvents() {}

    /** Event minimal auto-contenu — registre volatile, invoker combiné simple. */
    private static final class LocalEvent<T> extends net.fabricmc.fabric.api.event.Event<T> {
        @Override public void register(T handler) {
            // Événement serveur sur client distant : jamais déclenché, registre no-op.
        }
    }

    @FunctionalInterface
    public interface CopyFrom { void copyFromPlayer(net.minecraft.server.level.ServerPlayer original, net.minecraft.server.level.ServerPlayer clone, boolean alive); }
    @FunctionalInterface
    public interface AfterRespawn { void afterRespawn(net.minecraft.server.level.ServerPlayer oldPlayer, net.minecraft.server.level.ServerPlayer newPlayer, boolean alive); }
    @FunctionalInterface
    public interface Join { void onPlayerJoin(net.minecraft.server.level.ServerPlayer player); }
    @FunctionalInterface
    public interface Leave { void onPlayerLeave(net.minecraft.server.level.ServerPlayer player); }
    @FunctionalInterface
    public interface AllowDeath { boolean allowDeath(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount); }

    public static final net.fabricmc.fabric.api.event.Event<CopyFrom> COPY_FROM = new LocalEvent<>();
    public static final net.fabricmc.fabric.api.event.Event<AfterRespawn> AFTER_RESPAWN = new LocalEvent<>();
    public static final net.fabricmc.fabric.api.event.Event<Join> JOIN = new LocalEvent<>();
    public static final net.fabricmc.fabric.api.event.Event<Leave> LEAVE = new LocalEvent<>();
    public static final net.fabricmc.fabric.api.event.Event<AllowDeath> ALLOW_DEATH = new LocalEvent<>();
}
