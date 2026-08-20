package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Adaptateur Irium — forme exacte de l'officiel : interface générique,
 * statics serverboundPlay()/clientboundPlay(). Irium n'a pas de registration
 * protocolaire (le client vanilla accepte les custom payloads non enregistrés)
 * — les codecs sont stockés pour le bridge Messenger.
 */
public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {

    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);

    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec, int maxSize) {
        return register(type, codec);
    }

    default <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> registerLarge(
            CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec,
            java.util.function.IntSupplier maxSizeSupplier) {
        return register(type, codec);
    }

    @SuppressWarnings("rawtypes")
    static PayloadTypeRegistry<FriendlyByteBuf> serverboundConfiguration() {
        return dev.irium.agent.module.ClientPayloadRegistry.INSTANCE;
    }

    @SuppressWarnings("rawtypes")
    static PayloadTypeRegistry<FriendlyByteBuf> clientboundConfiguration() {
        return dev.irium.agent.module.ClientPayloadRegistry.INSTANCE;
    }

    @SuppressWarnings("rawtypes")
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() {
        return dev.irium.agent.module.ClientPayloadRegistry.INSTANCE;
    }

    @SuppressWarnings("rawtypes")
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() {
        return dev.irium.agent.module.ClientPayloadRegistry.INSTANCE;
    }
}
