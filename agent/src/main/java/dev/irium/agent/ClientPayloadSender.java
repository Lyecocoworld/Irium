package dev.irium.agent;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.netty.buffer.Unpooled;

/**
 * Émission custom_payload serverbound (0x16) via le canal du tap.
 * Encode le payload via son codec enregistré (ClientPayloadRegistry).
 */
public final class ClientPayloadSender {

    private ClientPayloadSender() {}

    public static void send(CustomPacketPayload payload) {
        io.netty.channel.Channel ch = IriumTap.currentChannel();
        if (ch == null || payload == null) return;
        try {
            Identifier id = payload.type().id();
            var codec = dev.irium.agent.module.ClientPayloadRegistry.SERVERBOUND.get(id);
            if (codec == null) {
                IriumAgent.log("[fabric-net] pas de codec pour " + id + " -> émission refusée");
                return;
            }
            io.netty.buffer.ByteBuf mc = Unpooled.buffer();
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(mc);
            codec.encode(buf, payload);
            byte[] body = new byte[mc.readableBytes()];
            mc.readBytes(body);
            mc.release();

            io.netty.buffer.ByteBuf out = Unpooled.buffer();
            writeVarInt(out, 0x16);
            writeString(out, id.toString());
            out.writeBytes(body);
            ch.writeAndFlush(out);
            IriumAgent.log("[fabric-net] envoyé " + id + " (" + body.length + "B)");
        } catch (Throwable t) {
            IriumAgent.log("[fabric-net] émission échec: " + t);
        }
    }

    private static void writeVarInt(io.netty.buffer.ByteBuf b, int v) {
        while ((v & 0xFFFFFF80) != 0) { b.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        b.writeByte(v);
    }

    private static void writeString(io.netty.buffer.ByteBuf b, String s) {
        byte[] x = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(b, x.length);
        b.writeBytes(x);
    }
}
