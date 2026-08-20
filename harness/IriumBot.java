import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/**
 * Bot de test Irium — protocole 776 (Canvas 26.2).
 * Modes :
 *   vanilla : ne répond JAMAIS au challenge irium -> classé VANILLA.
 *   agent   : répond au challenge irium:hello -> classé AGENT.
 *   voice   : agent + canaux voicechat:* + réponse request_secret + UDP auth
 *             -> doit faire apparaître le joueur dans le voice server du mod.
 */
public class IriumBot {
    static int compression = -1;
    static String state = "login";
    static boolean challengeSeen = false;
    static boolean registered = false;
    static long nonceVal;
    static boolean agentReplied = false;
    static long start = System.currentTimeMillis();
    static String mode = "vanilla";

    /** Canaux SVC que le mod client register au PLAY. */
    static final String[] VOICE_CHANNELS = {
            "voicechat:request_secret", "voicechat:update_state", "voicechat:player_state",
            "voicechat:player_states", "voicechat:remove_player_state", "voicechat:secret",
            "voicechat:add_category", "voicechat:remove_category", "voicechat:add_group",
            "voicechat:remove_group", "voicechat:join_group", "voicechat:create_group",
            "voicechat:leave_group", "voicechat:joined_group"
    };

    public static void main(String[] a) throws Exception {
        mode = a.length > 0 ? a[0] : "vanilla";
        String name = a.length > 1 && !a[1].isEmpty() ? a[1]
                : (mode.equals("vanilla") ? "PlainVani" : mode.equals("voice") ? "VoiceBot" : "IriumAgent");
        int lifetime = a.length > 2 ? Integer.parseInt(a[2]) : 20000;
        System.out.println("[bot:" + mode + "] connexion 25599 pour " + lifetime + " ms");
        try (Socket s = new Socket("127.0.0.1", 25599)) {
            s.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            // handshake + login
            send(out, 0, w -> { varInt(w, 776); str(w, "127.0.0.1"); w.writeShort(25599); varInt(w, 2); });
            UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            send(out, 0, w -> { str(w, name); w.writeLong(u.getMostSignificantBits()); w.writeLong(u.getLeastSignificantBits()); });
            // boucle principale
            while (System.currentTimeMillis() - start < lifetime) {
                byte[] d;
                try { d = readFrame(in); } catch (SocketTimeoutException ste) { continue; }
                if (d == null) { System.out.println("[bot:" + mode + "] connexion fermée par le serveur"); return; }
                int[] r = varIntAt(d, 0);
                int pid = r[0];
                switch (state) {
                    case "login" -> {
                        if (pid == 0x03 && d.length <= 5) { compression = varIntAt(d, r[1])[0]; }
                        else if (pid == 0x02) { send(out, 0x03, w -> {}); state = "configuration"; }
                        else if (pid == 0x00) { System.out.println("DISCONNECT(login): " + strAt(d, r[1])); return; }
                    }
                    case "configuration" -> {
                        if (pid == 0x0e) { send(out, 0x07, w -> varInt(w, 0)); }
                        else if (pid == 0x04) { long id = varLongAt(d, r[1]); send(out, 0x04, w -> varLong(w, id)); }
                        else if (pid == 0x03) { send(out, 0x03, w -> {}); state = "play"; System.out.println("[bot:" + mode + "] PLAY"); }
                        else if (pid == 0x02) { System.out.println("DISCONNECT(config): " + strAt(d, r[1])); return; }
                    }
                    case "play" -> handlePlay(out, d, r);
                }
            }
            System.out.println("[bot:" + mode + "] fin de session normale (" + lifetime + " ms)");
            System.out.println("[bot:" + mode + "] challenge vu=" + challengeSeen
                    + " réponse envoyée=" + agentReplied + " secret=" + (secretHex == null ? "non reçu" : secretHex.substring(0, 16) + ".."));
        }
    }

    static String secretHex;
    static int voicePort = 24454;
    static DatagramSocket udp;
    static boolean udpAuthenticated = false;

    /* -------- PLAY : register, keepalive, challenge, voicechat -------- */
    static void handlePlay(DataOutputStream out, byte[] d, int[] r) throws IOException {
        int pid = r[0];
        System.out.println("[t+" + (System.currentTimeMillis() - start) + "ms] play pid=0x"
                + Integer.toHexString(pid) + " len=" + d.length + " hex=" + hex(d, Math.min(32, d.length)));
        if (!registered && !mode.equals("vanilla")) {
            // canaux irium + voicechat, comme le vrai client SVC le ferait
            send(out, 0x16, w -> { str(w, "minecraft:register"); w.write("irium:hello".getBytes(StandardCharsets.UTF_8)); });
            StringBuilder chans = new StringBuilder();
            for (String c : VOICE_CHANNELS) chans.append(c).append('\0');
            send(out, 0x16, w -> { str(w, "minecraft:register"); w.write(chans.toString().getBytes(StandardCharsets.UTF_8)); });
            registered = true;
            System.out.println("[bot:" + mode + "] minecraft:register irium:hello + " + VOICE_CHANNELS.length + " canaux voicechat");
            // SVC : demande de secret — RequestSecretPacket.fromBytes lit readInt() (4 octets fixes)
            send(out, 0x16, w -> { str(w, "voicechat:request_secret"); w.writeInt(20); });
            System.out.println("[bot:voice] voicechat:request_secret envoyé (compat=20, int fixe)");
        }
        // custom_payload clientbound = 0x18
        if (pid == 0x18) {
            int p = r[1];
            int[] lr = varIntAt(d, p);
            String chan = new String(d, lr[1], lr[0], StandardCharsets.UTF_8);
            int dataOff = lr[1] + lr[0];
            byte[] body = Arrays.copyOfRange(d, dataOff, d.length);
            switch (chan) {
                case "irium:hello" -> {
                    if (body.length >= 12 && body[0] == 'I' && body[1] == 'R') {
                        challengeSeen = true;
                        nonceVal = 0;
                        for (int i = 0; i < 8 && 4 + i < body.length; i++) nonceVal = (nonceVal << 8) | (body[4 + i] & 0xFF);
                        System.out.println("[bot:" + mode + "] CHALLENGE irium nonce=0x" + Long.toHexString(nonceVal));
                        if (!mode.equals("vanilla") && !agentReplied) {
                            agentReplied = true;
                            byte[] resp = Handshake.encodeAgentResponse(nonceVal, "0.1.0", 0x1F);
                            send(out, 0x16, w -> { str(w, "irium:hello"); w.write(resp); });
                            System.out.println("[bot:agent] réponse AGENT envoyée (v0.1.0, caps=0x1F)");
                        }
                    }
                }
                case "voicechat:secret" -> {
                    secretHex = hex(body, Math.min(64, body.length));
                    System.out.println("[bot:voice] SECRET reçus (" + body.length + "B) hex=" + secretHex);
                    parseSecretAndAuth(body);
                }
                default -> {
                    if (chan.startsWith("voicechat:")) {
                        System.out.println("[bot:voice] " + chan + " (" + body.length + "B)");
                    }
                }
            }
            return;
        }
        // keep_alive play clientbound = 0x2C (44e enregistré, GameProtocols), body = long FIXE 8 octets
        // (pas varlong) — on écho les octets bruts sur serverbound keep_alive 0x1C
        if (pid == 0x2C && d.length >= 9) {
            byte[] body = Arrays.copyOfRange(d, r[1], d.length);
            send(out, 0x1C, w -> w.write(body));
            return;
        }
    }

    /* ---------------- SVC UDP : auth + keepalive + mic ---------------- */
    // SecretPacket.fromBytes : secret[n] || int port || uuid || byte codec || int mtu || double dist || int keepalive || bool groups || utf host || bool recording
    static byte[] secretBytes; static UUID playerUUID; static int kaMs; static String voiceHost;
    static void parseSecretAndAuth(byte[] body) {
        try {
            int p = 0;
            int secLen = 16; // GCM-128 : 56B observés - (4+16+1+4+8+4+1+2+1) = 16
            byte[] sec = Arrays.copyOfRange(body, p, p + secLen); p += secLen;
            secretBytes = sec;
            int port = readIntBE(body, p); p += 4;
            playerUUID = new UUID(readLongBE(body, p), readLongBE(body, p + 8)); p += 16;
            int codec = body[p++] & 0xFF;
            int mtu = readIntBE(body, p); p += 4;
            double dist = Double.longBitsToDouble(readLongBE(body, p)); p += 8;
            kaMs = readIntBE(body, p); p += 4;
            boolean groups = body[p++] != 0;
            int[] lr = varIntAt(body, p); String host = new String(body, lr[1], lr[0], StandardCharsets.UTF_8); p = lr[1] + lr[0];
            boolean recording = body[p] != 0;
            voiceHost = host.isEmpty() ? "127.0.0.1" : host;
            System.out.println("[bot:voice] secret=" + hex(sec, sec.length) + " port=" + port + " uuid=" + playerUUID
                    + " codec=" + codec + " mtu=" + mtu + " dist=" + dist + " ka=" + kaMs + "ms host=" + voiceHost);
            if (udp != null) return; // déjà démarré
            udp = new DatagramSocket();
            udp.setSoTimeout(2000);
            Thread t = new Thread(() -> {
                try { udpAuthLoop(port); }
                catch (Exception e) { System.out.println("[bot:voice] UDP ERR: " + e); }
            }, "svc-udp");
            t.setDaemon(true); t.start();
        } catch (Exception e) {
            System.out.println("[bot:voice] parse secret échec: " + e);
        }
    }

    static int readIntBE(byte[] d, int o) { return ((d[o]&0xFF)<<24)|((d[o+1]&0xFF)<<16)|((d[o+2]&0xFF)<<8)|(d[o+3]&0xFF); }
    static long readLongBE(byte[] d, int o) { long v=0; for (int i=0;i<8;i++) v=(v<<8)|(d[o+i]&0xFF); return v; }

    // wire AUTH : 0xFF || playerUUID(16) || AES-GCM-128(secret)[ IV(12) || ct(type=5||uuid||secret16) || tag16 ]
    static void udpAuthLoop(int port) throws Exception {
        System.out.println("[bot:voice] UDP auth vers " + voiceHost + ":" + port);
        byte[] auth = buildAuthPacket();
        long deadline = System.currentTimeMillis() + 15000;
        int attempt = 0;
        while (System.currentTimeMillis() < deadline && !udpAuthenticated) {
            udp.send(new DatagramPacket(auth, auth.length, InetAddress.getByName(voiceHost), port));
            attempt++;
            if (attempt <= 3 || attempt % 10 == 0) System.out.println("[bot:voice] AUTH #" + attempt + " envoyé (" + auth.length + "B)");
            try {
                byte[] buf = new byte[4096];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                udp.receive(resp);
                byte[] data = Arrays.copyOfRange(resp.getData(), 0, resp.getLength());
                System.out.println("[bot:verb] UDP reçus " + data.length + "B hex=" + hex(data, Math.min(48, data.length)));
                if (data.length > 1 && data[0] == (byte) 0xFF) {
                    // ACK ? le server envoie : 0xFF || UUID || ... ; on décode shallow
                    System.out.println("[bot:voice] *** RÉPONSE UDP VOICE CHAT *** (len=" + data.length + ")");
                    udpAuthenticated = true;
                    startVoiceStreaming();
                }
            } catch (SocketTimeoutException ste) { /* retry */ }
        }
        System.out.println("[bot:voice] UDP auth " + (udpAuthenticated ? "RÉUSSIE après " + attempt + " essais" : "échouée (pas de réponse)"));
    }

    static byte[] buildAuthPacket() throws Exception {
        // plaintext = type(1)=5 || uuid(16) || secret(16)
        ByteArrayOutputStream pt = new ByteArrayOutputStream();
        pt.write(5);
        pt.write(long2b(playerUUID.getMostSignificantBits())); pt.write(long2b(playerUUID.getLeastSignificantBits()));
        pt.write(secretBytes);
        return wrapEncrypted(pt); // 0xFF || uuid || varint(len) || GCM
    }

    static byte[] long2b(long v) { byte[] b = new byte[8]; for (int i = 7; i >= 0; i--) { b[i] = (byte) v; v >>= 8; } return b; }
    static byte[] aesGcmEncrypt(byte[] key, byte[] data) throws Exception {
        byte[] iv = new byte[12]; new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(data);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12); System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    /** Après auth : keepalive UDP (type 8) toutes les ~5s + un mic packet factice. */
    static void startVoiceStreaming() {
        Thread t = new Thread(() -> {
            try {
                // mic de test : OPUS silence factice
                Thread.sleep(1000);
                byte[] mic = buildMicPacket(new byte[0], false, System.currentTimeMillis());
                udp.send(new DatagramPacket(mic, mic.length, InetAddress.getByName(voiceHost), voicePort));
                System.out.println("[bot:voice] MIC envoyé");
                while (true) {
                    Thread.sleep(Math.min(kaMs > 0 ? kaMs : 5000, 10000));
                    byte[] ka = buildKeepAlivePacket();
                    udp.send(new DatagramPacket(ka, ka.length, InetAddress.getByName(voiceHost), voicePort));
                }
            } catch (Exception e) { System.out.println("[bot:voice] stream ERR: " + e); }
        }, "svc-stream");
        t.setDaemon(true); t.start();
    }

    // type 1 : data || whispering || sequenceNumber
    static byte[] buildMicPacket(byte[] data, boolean whispering, long seq) throws Exception {
        ByteArrayOutputStream pt = new ByteArrayOutputStream();
        pt.write(1);
        varIntStream(pt, data.length); pt.write(data);
        pt.write(whispering ? 1 : 0);
        pt.write(long2b(seq));
        return wrapEncrypted(pt);
    }
    // type 8 : corps vide (KeepAlivePacket ne sérialise rien)
    static byte[] buildKeepAlivePacket() throws Exception {
        ByteArrayOutputStream pt = new ByteArrayOutputStream();
        pt.write(8);
        return wrapEncrypted(pt);
    }
    static byte[] wrapEncrypted(ByteArrayOutputStream pt) throws Exception {
        byte[] enc = aesGcmEncrypt(secretBytes, pt.toByteArray());
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(0xFF);
        wire.write(long2b(playerUUID.getMostSignificantBits())); wire.write(long2b(playerUUID.getLeastSignificantBits()));
        varIntStream(wire, enc.length); // FriendlyByteBuf.readByteArray(2048) lit varint len + bytes
        wire.write(enc);
        return wire.toByteArray();
    }
    static void varIntStream(ByteArrayOutputStream o, int v) { while ((v & 0xFFFFFF80) != 0) { o.write((v & 0x7F) | 0x80); v >>>= 7; } o.write(v); }
    static void send(DataOutputStream out, int pid, W body) throws IOException {
        ByteArrayOutputStream bb = new ByteArrayOutputStream();
        DataOutputStream bw = new DataOutputStream(bb);
        varInt(bw, pid); body.write(bw);
        byte[] b = bb.toByteArray();
        ByteArrayOutputStream fb = new ByteArrayOutputStream();
        DataOutputStream fw = new DataOutputStream(fb);
        if (compression >= 0) {
            if (b.length < compression) {
                varInt(fw, b.length + 1); varInt(fw, 0); fw.write(b);
            } else {
                byte[] z = zlib(b);
                varInt(fw, z.length + varIntLen(b.length)); varInt(fw, b.length); fw.write(z);
            }
        } else { varInt(fw, b.length); fw.write(b); }
        out.write(fb.toByteArray()); out.flush();
    }
    record Frame(byte[] d) {}
    static byte[] readFrame(DataInputStream in) throws IOException {
        int len = rv(in);
        if (len <= 0) return null;
        byte[] raw = new byte[len];
        in.readFully(raw);
        if (compression < 0) return raw;
        int[] r = varIntAt(raw, 0);
        int size = r[0];
        if (size == 0) return Arrays.copyOfRange(raw, r[1], raw.length);
        Inflater inf = new Inflater();
        inf.setInput(raw, r[1], raw.length - r[1]);
        byte[] buf = new byte[size];
        try { inf.inflate(buf); } catch (Exception e) { throw new IOException(e); }
        return buf;
    }
    static byte[] zlib(byte[] data) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DeflaterOutputStream dd = new DeflaterOutputStream(b)) { dd.write(data); }
        return b.toByteArray();
    }
    static String hex(byte[] d, int n) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < n; i++) sb.append(String.format("%02x", d[i])); return sb.toString(); }
    interface W { void write(DataOutputStream w) throws IOException; }
    static void varInt(DataOutputStream w, int v) throws IOException { while ((v & 0xFFFFFF80) != 0) { w.write((v & 0x7F) | 0x80); v >>>= 7; } w.write(v); }
    static void varLong(DataOutputStream w, long v) throws IOException { while ((v & 0xFFFFFFFFFFFFFF80L) != 0) { w.write((int) ((v & 0x7F) | 0x80)); v >>>= 7; } w.write((int) v); }
    static int varIntLen(int v) { int n = 1; while ((v & 0xFFFFFF80) != 0) { v >>>= 0x07; n++; } return n; }
    static long varLongAt(byte[] d, int o) { long v = 0; int sh = 0, p = o; while (true) { int b = d[p++] & 0xFF; v |= (long) (b & 0x7F) << sh; if ((b & 0x80) == 0) return v; sh += 7; } }
    static String strAt(byte[] d, int o) { int[] r = varIntAt(d, o); return new String(d, r[1], r[0], StandardCharsets.UTF_8); }
    static void str(DataOutputStream w, String s) throws IOException { byte[] x = s.getBytes(StandardCharsets.UTF_8); varInt(w, x.length); w.write(x); }
    static int rv(DataInputStream in) throws IOException { int v = 0, sh = 0; while (true) { int b = in.readUnsignedByte(); v |= (b & 0x7F) << sh; if ((b & 0x80) == 0) return v; sh += 7; } }
    static int[] varIntAt(byte[] d, int o) { int v = 0, sh = 0, p = o; while (true) { int b = d[p++] & 0xFF; v |= (b & 0x7F) << sh; if ((b & 0x80) == 0) return new int[]{v, p}; sh += 7; } }
}
