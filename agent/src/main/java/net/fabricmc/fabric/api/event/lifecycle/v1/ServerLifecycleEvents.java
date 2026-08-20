package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Surface Irium côté CLIENT : les mods universels (SVC) référencent les
 * événements serveur. Sur un client, ces événements ne se déclenchent
 * jamais — les Event<T> sont créés vides via EventFactory.
 */
public final class ServerLifecycleEvents {

    private ServerLifecycleEvents() {}

    @FunctionalInterface
    public interface ServerStarting { void onServerStarting(net.minecraft.server.MinecraftServer server); }
    @FunctionalInterface
    public interface ServerStarted { void onServerStarted(net.minecraft.server.MinecraftServer server); }
    @FunctionalInterface
    public interface ServerStopping { void onServerStopping(net.minecraft.server.MinecraftServer server); }
    @FunctionalInterface
    public interface ServerStopped { void onServerStopped(net.minecraft.server.MinecraftServer server); }
    @FunctionalInterface
    public interface StartDataPackReload {
        void onStartDataPackReload(net.minecraft.server.MinecraftServer server, net.minecraft.server.packs.resources.CloseableResourceManager resourceManager);
    }
    @FunctionalInterface
    public interface EndDataPackReload {
        void onEndDataPackReload(net.minecraft.server.MinecraftServer server, net.minecraft.server.packs.resources.CloseableResourceManager resourceManager, boolean success);
    }
    @FunctionalInterface
    public interface SyncDataPackContents {
        void onSyncDataPackContents(net.minecraft.server.MinecraftServer server, boolean success);
    }
    @FunctionalInterface
    public interface BeforeSave { void onBeforeSave(net.minecraft.server.MinecraftServer server, boolean flush, boolean force); }
    @FunctionalInterface
    public interface AfterSave { void onAfterSave(net.minecraft.server.MinecraftServer server, boolean flush, boolean force); }

    public static final Event<ServerStarting> SERVER_STARTING = EventFactory.createArrayBacked(ServerStarting.class);
    public static final Event<ServerStarted> SERVER_STARTED = EventFactory.createArrayBacked(ServerStarted.class);
    public static final Event<ServerStopping> SERVER_STOPPING = EventFactory.createArrayBacked(ServerStopping.class);
    public static final Event<ServerStopped> SERVER_STOPPED = EventFactory.createArrayBacked(ServerStopped.class);
    public static final Event<SyncDataPackContents> SYNC_DATA_PACK_CONTENTS = EventFactory.createArrayBacked(SyncDataPackContents.class);
    public static final Event<StartDataPackReload> START_DATA_PACK_RELOAD = EventFactory.createArrayBacked(StartDataPackReload.class);
    public static final Event<EndDataPackReload> END_DATA_PACK_RELOAD = EventFactory.createArrayBacked(EndDataPackReload.class);
    public static final Event<BeforeSave> BEFORE_SAVE = EventFactory.createArrayBacked(BeforeSave.class);
    public static final Event<AfterSave> AFTER_SAVE = EventFactory.createArrayBacked(AfterSave.class);
}
