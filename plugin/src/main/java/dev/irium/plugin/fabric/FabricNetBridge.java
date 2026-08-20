package dev.irium.plugin.fabric;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * M7 : pont Messenger Bukkit/Paper <-> mod Fabric hébergé.
 *
 * Encodage : codec StreamCodec du mod -> RegistryFriendlyByteBuf -> custom
 * payload brut sur le canal MC via Player.sendPluginMessage.
 * Décodage : octets d'un client AGENT -> codec -> PlayPayloadHandler du mod.
 */
public final class FabricNetBridge {

    private static final Map<String, CodecEntry<? extends CustomPacketPayload>> CODECS = new HashMap<>();
    private static final Map<String, HandlerEntry<? extends CustomPacketPayload>> HANDLERS = new HashMap<>();
    private static volatile Plugin plugin;

    private record CodecEntry<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {}

    private record HandlerEntry<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayPayloadHandler<T> handler) {}

    private FabricNetBridge() {}

    public static void init(Plugin plugin) {
        FabricNetBridge.plugin = plugin;
    }

    /* ------------- PayloadTypeRegistry ------------- */

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry registry() {
        return new net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry() {
            @Override
            public CustomPacketPayload.TypeAndCodec register(CustomPacketPayload.Type type, StreamCodec codec) {
                CODECS.put(type.id().toString(), new CodecEntry(type, codec));
                return new CustomPacketPayload.TypeAndCodec(type, codec);
            }
        };
    }

    /* ------------- ServerPlayNetworking ------------- */

    public static <T extends CustomPacketPayload> void registerServerReceiver(
            CustomPacketPayload.Type<T> type,
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayPayloadHandler<T> handler) {
        HANDLERS.put(type.id().toString(), new HandlerEntry<>(type, handler));
        Plugin p = plugin;
        if (p != null && !Bukkit.getMessenger().isIncomingChannelRegistered(p, type.id().toString())) {
            Bukkit.getMessenger().registerIncomingPluginChannel(p, type.id().toString(),
                    (channel, player, bytes) -> onClientPayload(player, channel, bytes));
        }
    }

    public static net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayPayloadHandler<?> unregisterServerReceiver(
            Identifier channelName) {
        HandlerEntry<?> e = HANDLERS.remove(channelName.toString());
        return e == null ? null : e.handler();
    }

    public static Set<Identifier> globalReceivers() {
        Set<Identifier> out = new HashSet<>();
        for (HandlerEntry<?> e : HANDLERS.values()) out.add(e.type().id());
        return out;
    }

    public static boolean canSend(ServerPlayer player, Identifier channelName) {
        Player p = Bukkit.getPlayer(player.getUUID());
        return p != null;
    }

    /** Encodage + envoi d'un payload du mod vers un client. */
    @SuppressWarnings("unchecked")
    public static void sendToClient(ServerPlayer nmsPlayer, CustomPacketPayload payload) {
        Plugin p = plugin;
        if (p == null) return;
        CodecEntry<? extends CustomPacketPayload> entry = CODECS.get(payload.type().id().toString());
        Player bPlayer = Bukkit.getPlayer(nmsPlayer.getUUID());
        if (bPlayer == null || entry == null) {
            p.getLogger().warning("[fabric-bridge] envoi impossible (canal=" + payload.type().id()
                    + ", codec=" + (entry == null ? "ABSENT" : "ok") + ", player=" + bPlayer.getName() + ")");
            return;
        }
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), nmsPlayer.level().getServer().registryAccess());
            ((StreamCodec) entry.codec()).encode(buf, payload);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            bPlayer.sendPluginMessage(p, payload.type().id().toString(), data);
        } catch (Throwable t) {
            p.getLogger().warning("[fabric-bridge] encode échec: " + t);
        }
    }

    /** Réception d'un custom payload du mod depuis un client. */
    @SuppressWarnings("unchecked")
    public static void onClientPayload(Player bPlayer, String channel, byte[] bytes) {
        CodecEntry<? extends CustomPacketPayload> ce = CODECS.get(channel);
        HandlerEntry<? extends CustomPacketPayload> he = HANDLERS.get(channel);
        if (ce == null || he == null) return;
        try {
            ServerPlayer nms = nms(bPlayer);
            if (nms == null) return;
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                    Unpooled.wrappedBuffer(bytes), nms.level().getServer().registryAccess());
            Object decoded = ((StreamCodec) ce.codec()).decode(buf);
            CustomPacketPayload payload = (CustomPacketPayload) decoded;
            ((HandlerEntry<CustomPacketPayload>) he).handler().receive(payload, new ContextImpl(nms));
        } catch (Throwable t) {
            Plugin p = plugin;
            if (p != null) p.getLogger().warning("[fabric-bridge] decode échec (" + channel + "): " + t);
        }
    }

    /** Contexte Context officiel du mod. */
    private record ContextImpl(ServerPlayer player) implements
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context {

        @Override public MinecraftServer server() { return player.level().getServer(); }

        @Override public ServerPlayer player() { return player; }

        @Override public net.fabricmc.fabric.api.networking.v1.PacketSender responseSender() {
            return new net.fabricmc.fabric.api.networking.v1.PacketSender() {
                @Override public net.minecraft.network.protocol.Packet<?> createPacket(CustomPacketPayload payload) {
                    return null;
                }
                @Override public void sendPacket(net.minecraft.network.protocol.Packet<?> packet,
                                                 io.netty.channel.ChannelFutureListener listener) {}
                @Override public void disconnect(net.minecraft.network.chat.Component reason) {}
            };
        }
    }

    /** Bukkit Player -> NMS ServerPlayer. */
    private static ServerPlayer nms(Player p) {
        try {
            return (ServerPlayer) p.getClass().getMethod("getHandle").invoke(p);
        } catch (Exception e) {
            return null;
        }
    }
}
