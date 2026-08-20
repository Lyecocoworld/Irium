package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Surface Irium de ClientPlayNetworking (forme officielle 26.2).
 * Le transport réel passe par le tap Irium (custom_payload brut), pas par
 * Connection#send de MC : les payloads sont routés vers les handlers enregistrés.
 */
public final class ClientPlayNetworking {

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        net.minecraft.client.Minecraft client();
        net.minecraft.client.player.LocalPlayer player();
        net.fabricmc.fabric.api.networking.v1.PacketSender responseSender();
    }

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return net.fabricmc.fabric.impl.client.networking.ClientNetworkingImpl
                .registerTyped(type.id(), handler);
    }

    public static ClientPlayNetworking.PlayPayloadHandler<?> unregisterGlobalReceiver(Identifier id) {
        net.fabricmc.fabric.impl.client.networking.ClientNetworkingImpl.unregisterReceiver(id);
        return null;
    }

    public static Set<Identifier> getGlobalReceivers() {
        return net.fabricmc.fabric.impl.client.networking.ClientNetworkingImpl.receivers();
    }

    public static <T extends CustomPacketPayload> boolean registerReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return registerGlobalReceiver(type, handler);
    }

    public static Set<Identifier> getSendable() { return getGlobalReceivers(); }
    public static boolean canSend(Identifier channel) { return true; }
    public static boolean canSend(CustomPacketPayload.Type<?> type) { return true; }

    public static void send(CustomPacketPayload payload) {
        net.fabricmc.fabric.impl.client.networking.ClientNetworkingImpl.send(payload);
    }

    private ClientPlayNetworking() {}
}
