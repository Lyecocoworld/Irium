package net.fabricmc.fabric.impl.client.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Transport ClientPlayNetworking : route les custom_payload du tap vers les
 * handlers enregistrés par les mods streamés, et émet via le tap.
 * Décodage via le codec clientbound enregistré (ClientPayloadRegistry).
 */
public final class ClientNetworkingImpl {

    public interface RawHandler {
        void receive(byte[] body);
    }

    private static final Map<Identifier, RawHandler> HANDLERS = new HashMap<>();

    public static boolean registerReceiver(Identifier id, RawHandler h) {
        boolean dup = HANDLERS.containsKey(id);
        HANDLERS.put(id, h);
        return !dup;
    }

    public static void unregisterReceiver(Identifier id) { HANDLERS.remove(id); }

    public static Set<Identifier> receivers() { return new HashSet<>(HANDLERS.keySet()); }

    /** Wrap d'un PlayPayloadHandler typé en RawHandler (décodage par codec). */
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> boolean registerTyped(
            Identifier id, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
        return registerReceiver(id, body -> {
            StreamCodec<FriendlyByteBuf, CustomPacketPayload> codec =
                    dev.irium.agent.module.ClientPayloadRegistry.CLIENTBOUND.get(id);
            if (codec == null) {
                dev.irium.agent.IriumAgent.log("[fabric-net] pas de codec clientbound pour " + id + " -> payload ignoré");
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(body));
            T payload;
            try {
                payload = (T) codec.decode(buf);
            } finally {
                buf.release();
            }
            handler.receive(payload, CONTEXT);
        });
    }

    /** Contexte d'exécution des handlers (client + player + sender). */
    static final ClientPlayNetworking.Context CONTEXT = new ClientPlayNetworking.Context() {
        @Override public net.minecraft.client.Minecraft client() {
            return net.minecraft.client.Minecraft.getInstance();
        }
        @Override public net.minecraft.client.player.LocalPlayer player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
        @Override public net.fabricmc.fabric.api.networking.v1.PacketSender responseSender() {
            return RESPONSE_SENDER;
        }
    };

    static final net.fabricmc.fabric.api.networking.v1.PacketSender RESPONSE_SENDER =
            new net.fabricmc.fabric.api.networking.v1.PacketSender() {
                @Override public net.minecraft.network.protocol.Packet<?> createPacket(CustomPacketPayload payload) { return null; }
                @Override public void sendPacket(net.minecraft.network.protocol.Packet<?> packet) {
                    dev.irium.agent.IriumAgent.log("[fabric-net] sendPacket(Packet) non supporté (payloads uniquement)");
                }
                @Override public void sendPacket(CustomPacketPayload payload) {
                    dev.irium.agent.ClientPayloadSender.send(payload);
                }
                @Override public void sendPacket(net.minecraft.network.protocol.Packet<?> packet, io.netty.channel.ChannelFutureListener l) {}
                @Override public void disconnect(net.minecraft.network.chat.Component reason) {
                    dev.irium.agent.IriumAgent.log("[fabric-net] disconnect demandé: " + reason);
                }
            };

    /** Appelé par le tap : payload clientbound brut (après le channel-string). */
    public static void dispatch(Identifier id, byte[] body) {
        RawHandler h = HANDLERS.get(id);
        if (h == null) return;
        try {
            h.receive(body);
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[fabric-net] handler " + id + " a levé (ignoré) : " + t);
        }
    }

    public static void send(CustomPacketPayload payload) {
        dev.irium.agent.ClientPayloadSender.send(payload);
    }

    /** Sandbox : plus aucun handler (déconnexion). */
    public static void clear() { HANDLERS.clear(); }

    private ClientNetworkingImpl() {}
}
