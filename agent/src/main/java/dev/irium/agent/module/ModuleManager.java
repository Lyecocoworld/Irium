package dev.irium.agent.module;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * M4 : runtime de modules streamés, UNE instance par canal (session).
 *
 * Fil du protocole (canal irium:module) — serveur -> agent :
 *   BEGIN     (0x01) manifestId str, totalLen i32, sha256 32B, mainClass str
 *   CHUNK     (0x02) seq i32, octets bruts (<= ~24 Ko par trame)
 *   ACTIVATE  (0x03) (vide)
 * agent -> serveur :
 *   EVENT     (0x81) tag str, data str
 *
 * Sécurité v1 : refus si sha256(reassemblé) != sha256(manifest), refus si
 * totalLen > 1 Mo, refus hors PLAY. La signature Ed25519 arrive en M5/M6 —
 * le hash verify prouve déjà la chaîne "vérifier avant de définir".
 */
public final class ModuleManager {

    public static final String CHANNEL = "irium:module";
    static final AttributeKey<ModuleManager> KEY = AttributeKey.valueOf("irium.modules");

    private static final int MAX_MODULE_BYTES = 32 << 20; // 32 Mo (M7-B : mods complets, SVC 5.6 Mo)

    /** Réception en cours. */
    private static final class Rx {
        String manifestId; String mainClass; byte[] sha256; int totalLen;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int lastSeq = -1;
    }

    private final Channel channel;
    private final Map<String, IriumModule> modules = new HashMap<>();
    private Rx rx;

    private ModuleManager(Channel channel) { this.channel = channel; }

    public static ModuleManager of(Channel ch) {
        ModuleManager m = ch.attr(KEY).get();
        if (m == null) { m = new ModuleManager(ch); ch.attr(KEY).set(m); }
        return m;
    }

    /* ---------------- réception ---------------- */

    /** Invoqué par le tap pour chaque custom_payload irium:module. body suit le channel-string. */
    public void ingest(ByteBuf body) {
        int type = body.readUnsignedByte();
        switch (type) {
            case 0x01 -> { // BEGIN
                rx = new Rx();
                rx.manifestId = readStr(body);
                rx.totalLen = body.readInt();
                byte[] h = new byte[32];
                body.readBytes(h);
                rx.sha256 = h;
                rx.mainClass = readStr(body);
                if (rx.totalLen <= 0 || rx.totalLen > MAX_MODULE_BYTES) {
                    IriumAgentLike.log("[module] BEGIN refusé (taille " + rx.totalLen + " hors bornes)");
                    rx = null;
                }
            }
            case 0x02 -> { // CHUNK
                if (rx == null) return;
                int seq = body.readInt();
                if (seq != rx.lastSeq + 1) { // perte/désordre -> reset dur
                    IriumAgentLike.log("[module] chunk " + seq + " hors séquence -> abandon du transfert");
                    rx = null;
                    return;
                }
                rx.lastSeq = seq;
                byte[] b = new byte[body.readableBytes()];
                body.readBytes(b);
                rx.out.write(b, 0, b.length);
                if (rx.out.size() > MAX_MODULE_BYTES) {
                    IriumAgentLike.log("[module] dépassement de taille -> abandon");
                    rx = null;
                }
            }
            case 0x03 -> activate();
            case 0x05 -> { // MODJAR (M7-B) : jar Fabric complet
                Rx r = rx; rx = null;
                if (r == null) { IriumAgentLike.log("[module] MODJAR sans transfert -> ignoré"); return; }
                byte[] bytes = r.out.toByteArray();
                if (bytes.length != r.totalLen) {
                    IriumAgentLike.log("[module] MODJAR longueur " + bytes.length + " != " + r.totalLen + " -> refus");
                    return;
                }
                byte[] actual = sha256(bytes);
                if (!MessageDigest.isEqual(actual, r.sha256)) {
                    IriumAgentLike.log("[module] MODJAR sha256 MISMATCH -> refus (module '" + r.manifestId + "')");
                    return;
                }
                IriumAgentLike.log("[module] MODJAR '" + r.manifestId + "' reçu (" + bytes.length + " octets, sha256 ok) -> installation");
                // installation sur le thread de rendu si dispo, sinon direct
                dev.irium.agent.module.FabricModHost.install(bytes);
            }
            case 0x04 -> { // RECIPE (M5)
                String target = readStr(body);
                String method = readStr(body);
                String desc = readStr(body);
                String anchorHex = readStr(body);
                String bridge = readStr(body);
                Recipe r = Recipe.of(target, method, desc, anchorHex, bridge);
                if (r == null) {
                    IriumAgentLike.log("[recette] format invalide -> refus");
                } else {
                    RecipeStore.add(r);
                    IriumAgentLike.log("[recette] reçue : " + target + "." + method + desc
                            + " (ancre " + anchorHex.substring(0, 8) + "..)");
                    // si la classe est DÉJÀ chargée, retransformer à chaud
                    IriumAgentLike.retransform(target.replace('/', '.'));
                }
            }
            default -> IriumAgentLike.log("[module] type inconnu 0x" + Integer.toHexString(type));
        }
    }

    private void activate() {
        Rx r = rx; rx = null;
        if (r == null) { IriumAgentLike.log("[module] ACTIVATE sans transfert -> ignoré"); return; }
        byte[] bytes = r.out.toByteArray();
        if (bytes.length != r.totalLen) {
            IriumAgentLike.log("[module] longueur " + bytes.length + " != " + r.totalLen + " -> refus");
            return;
        }
        byte[] actual = sha256(bytes);
        if (!MessageDigest.isEqual(actual, r.sha256)) {
            IriumAgentLike.log("[module] sha256 MISMATCH -> refus de chargement (module '" + r.manifestId + "')");
            return;
        }
        try {
            ModuleClassLoader cl = new ModuleClassLoader(channel);
            Class<?> c = cl.define(bytes);
            if (!IriumModule.class.isAssignableFrom(c)) {
                IriumAgentLike.log("[module] " + c.getName() + " n'implémente pas IriumModule -> refus");
                return;
            }
            if (!c.getName().equals(r.mainClass)) {
                IriumAgentLike.log("[module] classe définie " + c.getName() + " != manifest " + r.mainClass + " -> refus");
                return;
            }
            IriumModule m = (IriumModule) c.getDeclaredConstructor().newInstance();
            m.onEnable(new Context());
            modules.put(r.manifestId, m);
            IriumAgentLike.log("[module] '" + r.manifestId + "' CHARGÉ et ACTIVÉ (" + bytes.length
                    + " octets, " + c.getName() + ", sha256 ok)");
        } catch (Throwable t) {
            IriumAgentLike.log("[module] échec d'activation: " + t);
        }
    }

    /* ---------------- émission ---------------- */

    private void send(byte[] payload) {
        // custom_payload serverbound PLAY = 0x16 ; corps = canal + payload
        ByteBuf out = Unpooled.buffer();
        writeVarInt(out, 0x16);
        writeStr(out, CHANNEL);
        out.writeBytes(payload);
        channel.writeAndFlush(out);
    }

    void emitEvent(String tag, String data) {
        ByteBuf p = Unpooled.buffer();
        p.writeByte(0x81);
        writeStr(p, tag);
        writeStr(p, data);
        byte[] payload = new byte[p.readableBytes()];
        p.readBytes(payload);
        p.release();
        send(payload);
    }

    /* ---------------- cycle de vie ---------------- */

    public static void close(Channel ch) {
        ModuleManager m = ch.attr(KEY).getAndSet(null);
        if (m == null) return;
        int n = m.modules.size();
        for (IriumModule mod : m.modules.values()) {
            try { mod.onDisable(); } catch (Throwable ignored) {}
        }
        m.modules.clear();
        RecipeStore.clearAll();            // M5 : plus aucune recette active
        dev.irium.agent.hud.HudBridge.clearAll(); // plus aucun renderer
        dev.irium.agent.module.FabricModHost.uninstallAll(); // M7-B : sandbox mods Fabric
        IriumAgentLike.log("[module] session fermée : " + n + " module(s) désactivé(s), classloader abandonné");
    }

    /* ---------------- contexte offert aux modules ---------------- */

    private final class Context implements IriumContext {
        @Override public void log(String message) { IriumAgentLike.log("[mod] " + message); }
        @Override public void emit(String tag, String data) {
            channel.eventLoop().execute(() -> emitEvent(tag, data));
        }
        @Override public void hud(Runnable renderer) {
            dev.irium.agent.hud.HudBridge.register(renderer);
        }
    }

    /* ---------------- utilitaires ---------------- */

    private static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readStr(ByteBuf b) {
        int len = readVarInt(b);
        return b.readCharSequence(len, StandardCharsets.UTF_8).toString();
    }

    private static int readVarInt(ByteBuf b) {
        int v = 0, sh = 0;
        while (true) {
            byte x = b.readByte();
            v |= (x & 0x7F) << sh;
            if ((x & 0x80) == 0) return v;
            sh += 7;
        }
    }

    private static void writeVarInt(ByteBuf b, int v) {
        while ((v & 0xFFFFFF80) != 0) { b.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        b.writeByte(v);
    }

    private static void writeStr(ByteBuf b, String s) {
        byte[] x = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(b, x.length);
        b.writeBytes(x);
    }

    /** Indirection de log/action (l'API module ne doit pas dépendre d'IriumTap). */
    static final class IriumAgentLike {
        private IriumAgentLike() {}
        static void log(String m) { dev.irium.agent.IriumAgent.log(m); }
        static void retransform(String fqcn) { dev.irium.agent.IriumAgent.retransformLoaded(fqcn); }
    }
}
