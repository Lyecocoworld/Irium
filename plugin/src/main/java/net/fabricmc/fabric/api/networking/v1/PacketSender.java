package net.fabricmc.fabric.api.networking.v1;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;

/** Adaptateur Irium — PacketSender officiel (no-op sur le bridge Messenger). */
public interface PacketSender {

    Packet<?> createPacket(CustomPacketPayload payload);

    default void sendPacket(Packet<?> packet) {}

    default void sendPacket(CustomPacketPayload payload) {}

    void sendPacket(Packet<?> packet, ChannelFutureListener listener);

    default void sendPacket(CustomPacketPayload payload, ChannelFutureListener listener) {}

    void disconnect(Component reason);
}
