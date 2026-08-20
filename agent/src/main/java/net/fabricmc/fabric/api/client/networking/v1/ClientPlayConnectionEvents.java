package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.networking.v1.PacketSender;

/**
 * Surface Irium de ClientPlayConnectionEvents (forme officielle 26.2).
 * JOIN est déclenché par le tap quand PLAY est atteint ; DISCONNECT à channelInactive.
 */
public final class ClientPlayConnectionEvents {

    @FunctionalInterface
    public interface Init {
        void onPlayInit(net.minecraft.client.multiplayer.ClientPacketListener handler, net.minecraft.client.Minecraft client);
    }

    @FunctionalInterface
    public interface Join {
        void onPlayReady(net.minecraft.client.multiplayer.ClientPacketListener handler, PacketSender sender, net.minecraft.client.Minecraft client);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(net.minecraft.client.multiplayer.ClientPacketListener handler, PacketSender sender, net.minecraft.client.Minecraft client);
    }

    public static final Event<Init> INIT =
            EventFactory.createArrayBacked(Init.class,
                    listeners -> (handler, client) -> { for (Init l : listeners) l.onPlayInit(handler, client); });

    public static final Event<Join> JOIN =
            EventFactory.createArrayBacked(Join.class,
                    listeners -> (handler, sender, client) -> { for (Join l : listeners) l.onPlayReady(handler, sender, client); });

    public static final Event<Disconnect> DISCONNECT =
            EventFactory.createArrayBacked(Disconnect.class,
                    listeners -> (handler, sender, client) -> { for (Disconnect l : listeners) l.onPlayDisconnect(handler, sender, client); });

    private ClientPlayConnectionEvents() {}
}
