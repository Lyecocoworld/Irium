import java.nio.charset.StandardCharsets;

/** Codec handshake Irium — copie standalone du HandshakeCodec plugin (spec M2). */
public final class Handshake {
    public static final byte[] MAGIC = {'I', 'R'};
    public static final byte PROTOCOL_VERSION = 1;
    public static final byte TYPE_HELLO = 0x01;
    public static final byte TYPE_AGENT_RESPONSE = 0x02;

    public static Long decodeHelloNonce(byte[] data) {
        if (data == null || data.length != 12
                || data[0] != MAGIC[0] || data[1] != MAGIC[1]
                || data[2] != PROTOCOL_VERSION || data[3] != TYPE_HELLO) {
            return null;
        }
        long nonce = 0;
        for (int i = 0; i < 8; i++) nonce = (nonce << 8) | (data[4 + i] & 0xFF);
        return nonce;
    }

    public static byte[] encodeAgentResponse(long nonce, String agentVersion, int caps) {
        byte[] ver = agentVersion.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[12 + 2 + ver.length + 1];
        out[0] = MAGIC[0]; out[1] = MAGIC[1];
        out[2] = PROTOCOL_VERSION;
        out[3] = TYPE_AGENT_RESPONSE;
        for (int i = 0; i < 8; i++) out[4 + i] = (byte) (nonce >>> (56 - 8 * i));
        out[12] = (byte) (ver.length >>> 8);
        out[13] = (byte) ver.length;
        System.arraycopy(ver, 0, out, 14, ver.length);
        out[out.length - 1] = (byte) caps;
        return out;
    }
}
