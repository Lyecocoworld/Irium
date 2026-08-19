package dev.irium.plugin;

import java.nio.charset.StandardCharsets;

/**
 * Codec du handshake Irium (M2) — spec partagée plugin/agent.
 *
 * Format binaire (volontairement trivial et versionné) :
 *   Hello (S->C, canal irium:hello) :
 *     magic   : 2 bytes  'I','R'
 *     version : 1 byte   protocole handshake (actuellement 1)
 *     type    : 1 byte   0x01 = HELLO_CHALLENGE
 *     nonce   : 8 bytes  identifiant de session (réponse attendue)
 *
 *   Welcome (C->S, canal irium:hello) :
 *     magic   : 2 bytes  'I','R'
 *     version : 1 byte   1
 *     type    : 1 byte   0x02 = AGENT_RESPONSE
 *     nonce   : 8 bytes  (echo du challenge)
 *     agentVer: chaîne   2 bytes longueur + UTF-8
 *     caps    : 1 byte   bitmap (bit0 = observe, bit1 = modules,
 *                         bit2 = recipes, bit3 = input, bit4 = render)
 *
 * Un client vanilla ignore le canal (pas de réponse) : timeout -> classement vanilla.
 */
public final class HandshakeCodec {

    public static final byte[] MAGIC = {'I', 'R'};
    public static final byte PROTOCOL_VERSION = 1;
    public static final byte TYPE_HELLO = 0x01;
    public static final byte TYPE_AGENT_RESPONSE = 0x02;
    public static final int CAP_OBSERVE = 0x01;
    public static final int CAP_MODULES = 0x02;
    public static final int CAP_RECIPES = 0x04;
    public static final int CAP_INPUT = 0x08;
    public static final int CAP_RENDER = 0x10;

    private HandshakeCodec() {
    }

    /** Construit le challenge S->C. */
    public static byte[] encodeHello(long nonce) {
        return new byte[]{
                MAGIC[0], MAGIC[1], PROTOCOL_VERSION, TYPE_HELLO,
                (byte) (nonce >>> 56), (byte) (nonce >>> 48), (byte) (nonce >>> 40), (byte) (nonce >>> 32),
                (byte) (nonce >>> 24), (byte) (nonce >>> 16), (byte) (nonce >>> 8), (byte) nonce
        };
    }

    /** Réponse C->S (utilisée par l'agent ; exposée aussi pour les tests). */
    public static byte[] encodeAgentResponse(long nonce, String agentVersion, int caps) {
        byte[] ver = agentVersion.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[12 + 2 + ver.length + 1];
        out[0] = MAGIC[0]; out[1] = MAGIC[1];
        out[2] = PROTOCOL_VERSION;
        out[3] = TYPE_AGENT_RESPONSE;
        for (int i = 0; i < 8; i++) {
            out[4 + i] = (byte) (nonce >>> (56 - 8 * i));
        }
        out[12] = (byte) (ver.length >>> 8);
        out[13] = (byte) ver.length;
        System.arraycopy(ver, 0, out, 14, ver.length);
        out[out.length - 1] = (byte) caps;
        return out;
    }

    /** Résultat du décodage d'une réponse agent. */
    public record AgentResponse(long nonce, String agentVersion, int caps) {
        public boolean hasCap(int cap) {
            return (caps & cap) != 0;
        }
    }

    /** Décode une réponse agent ; retourne null si tronquée/invalide. */
    public static AgentResponse decodeAgentResponse(byte[] data) {
        if (data == null || data.length < 15) {
            return null;
        }
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1]) {
            return null;
        }
        if (data[2] != PROTOCOL_VERSION || data[3] != TYPE_AGENT_RESPONSE) {
            return null;
        }
        long nonce = 0;
        for (int i = 0; i < 8; i++) {
            nonce = (nonce << 8) | (data[4 + i] & 0xFF);
        }
        int verLen = ((data[12] & 0xFF) << 8) | (data[13] & 0xFF);
        if (data.length < 14 + verLen + 1) {
            return null;
        }
        String version = new String(data, 14, verLen, StandardCharsets.UTF_8);
        int caps = data[14 + verLen] & 0xFF;
        return new AgentResponse(nonce, version, caps);
    }

    /** Décode un challenge (côté tests). */
    public static Long decodeHelloNonce(byte[] data) {
        if (data == null || data.length != 12
                || data[0] != MAGIC[0] || data[1] != MAGIC[1]
                || data[2] != PROTOCOL_VERSION || data[3] != TYPE_HELLO) {
            return null;
        }
        long nonce = 0;
        for (int i = 0; i < 8; i++) {
            nonce = (nonce << 8) | (data[4 + i] & 0xFF);
        }
        return nonce;
    }
}
