package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Surface Irium côté CLIENT : les mods universels (SVC) référencent
 * ServerPlayNetworking dans leur entrypoint main. Sur un client, ces appels
 * ne doivent rien faire (le serveur Irium héberge la partie serveur du mod).
 */
public final class ServerPlayNetworking {

    private ServerPlayNetworking() {}

    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context ctx);
    }

    public interface Context {
        net.minecraft.server.level.ServerPlayer player();
        net.fabricmc.fabric.api.networking.v1.PacketSender responseSender();
    }

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        // côté client : ignoré (le serveur héberge le mod)
        return true;
    }

    public static PlayPayloadHandler<?> unregisterGlobalReceiver(Identifier channelName) {
        return null;
    }

    public static Set<Identifier> getGlobalReceivers() {
        return Set.of();
    }

    public static void send(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload payload) {
        // côté client : no-op
    }

    public static boolean canSend(net.minecraft.server.level.ServerPlayer player, Identifier channelName) {
        return false;
    }
}
