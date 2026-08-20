package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Adaptateur Irium — forme exacte de l'officiel (classe finale, statics).
 * Bridge : Messenger Bukkit/Paper.
 */
public final class ServerPlayNetworking {

    private ServerPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        dev.irium.plugin.fabric.FabricNetBridge.registerServerReceiver(type, handler);
        return true;
    }

    public static PlayPayloadHandler<?> unregisterGlobalReceiver(Identifier channelName) {
        return dev.irium.plugin.fabric.FabricNetBridge.unregisterServerReceiver(channelName);
    }

    public static java.util.Set<Identifier> getGlobalReceivers() {
        return dev.irium.plugin.fabric.FabricNetBridge.globalReceivers();
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        dev.irium.plugin.fabric.FabricNetBridge.sendToClient(player, payload);
    }

    public static boolean canSend(ServerPlayer player, Identifier channelName) {
        return dev.irium.plugin.fabric.FabricNetBridge.canSend(player, channelName);
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        MinecraftServer server();

        ServerPlayer player();

        PacketSender responseSender();
    }
}
