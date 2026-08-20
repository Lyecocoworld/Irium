package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/** Adaptateur Irium — cycle de vie serveur, tirés depuis le plugin Bukkit. */
public final class ServerLifecycleEvents {

    private ServerLifecycleEvents() {}

    public static final Event<ServerStarted> SERVER_STARTED =
            EventFactory.createArrayBacked(ServerStarted.class,
                    ls -> server -> { for (ServerStarted l : ls) l.onServerStarted(server); });

    public static final Event<ServerStopping> SERVER_STOPPING =
            EventFactory.createArrayBacked(ServerStopping.class,
                    ls -> server -> { for (ServerStopping l : ls) l.onServerStopping(server); });

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }
}
