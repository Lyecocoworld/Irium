package dev.irium.agent.module;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M7-B : registre de codecs de payloads côté client.
 * Les mods streamés y enregistrent leurs codecs (PayloadTypeRegistry.serverboundPlay()
 * etc.) ; le ClientPayloadSender les utilise pour encoder l'émission.
 */
public final class ClientPayloadRegistry {

    /** Instance compatible PayloadTypeRegistry (bridge raw). */
    @SuppressWarnings("rawtypes")
    public static final net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry INSTANCE =
            new net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry() {
        @Override
        public CustomPacketPayload.TypeAndCodec register(
                net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type type,
                net.minecraft.network.codec.StreamCodec codec) {
            Identifier id = type.id();
            SERVERBOUND.put(id, codec);
            CLIENTBOUND.put(id, codec);
            dev.irium.agent.IriumAgent.log("[fabric-net] codec " + id + " enregistré");
            return null;
        }
    };

    /** id -> codec d'émission (serverbound). */
    public static final Map<Identifier, StreamCodec<FriendlyByteBuf, CustomPacketPayload>> SERVERBOUND = new ConcurrentHashMap<>();
    /** id -> codec de réception (clientbound). */
    public static final Map<Identifier, StreamCodec<FriendlyByteBuf, CustomPacketPayload>> CLIENTBOUND = new ConcurrentHashMap<>();

    private boolean clientbound; // dernier axe demandé

    private ClientPayloadRegistry() {}

    // Les 4 statiques de l'API retournent INSTANCE ; l'axe est déduit à l'appel
    // en regardant la pile ? Non : trop fragile. On enregistre des deux côtés.

    public static void clear() {
        SERVERBOUND.clear();
        CLIENTBOUND.clear();
    }

    @SuppressWarnings("unused")
    private static RegistryFriendlyByteBuf unused() { return null; }
}
