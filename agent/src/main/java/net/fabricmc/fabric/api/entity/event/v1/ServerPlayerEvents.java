package net.fabricmc.fabric.api.entity.event.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Surface Irium côté CLIENT : les mods universels (ex. Xaero) référencent les
 * événements joueur serveur. Sur un client distant, ces événements ne se
 * déclenchent jamais — Event<T> vides via EventFactory (pattern Irium des
 * surfaces server-side, cf. ServerLifecycleEvents).
 */
public final class ServerPlayerEvents {

    private ServerPlayerEvents() {}

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

    public static final Event<CopyFrom> COPY_FROM = EventFactory.createArrayBacked(CopyFrom.class);
    public static final Event<AfterRespawn> AFTER_RESPAWN = EventFactory.createArrayBacked(AfterRespawn.class);
    public static final Event<Join> JOIN = EventFactory.createArrayBacked(Join.class);
    public static final Event<Leave> LEAVE = EventFactory.createArrayBacked(Leave.class);
    public static final Event<AllowDeath> ALLOW_DEATH = EventFactory.createArrayBacked(AllowDeath.class);
}
