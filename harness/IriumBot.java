import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/**
 * Bot de test Irium M2 — protocole 776 (Canvas 26.2).
 * Modes :
 *   vanilla : reste connecté, ne répond JAMAIS au challenge irium -> doit être classé VANILLA (timeout).
 *   agent   : répond au challenge irium:hello avec le codec -> doit être classé AGENT v0.1.0.
 *
 * IDs PLAY (ordre d'enregistrement GameProtocols, extraits du bytecode) :
 *   clientbound : ... 0x18 custom_payload ... keep_alive ~0x20+ (on matche par heuristique)
 *   serverbound : 0x18 custom_payload, keep_alive id variable -> on scanne
 */
public class IriumBot {
    static int compression = -1;
    static String state = "login";
    static boolean challengeSeen = false;
    static boolean registered = false;
    static long nonceVal;
    static boolean agentReplied = false;
    static long start = System.currentTimeMillis();

    public static void main(String[] a) throws Exception {
        String mode = a.length > 0 ? a[0] : "vanilla";
        String name = mode.equals("agent") ? "IriumAgent" : "PlainVani";
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
                    case "play" -> handlePlay(mode, out, d, r);
                }
            }
            System.out.println("[bot:" + mode + "] fin de session normale (" + lifetime + " ms)");
            // POST-MORTEM: classification observée côté bot
            System.out.println("[bot:" + mode + "] challenge vu=" + challengeSeen
                    + " réponse envoyée=" + agentReplied);
        }
    }

    /* -------- PLAY : register des canaux (comme le fera l'agent), keepalive, challenge -------- */
    static void handlePlay(String mode, DataOutputStream out, byte[] d, int[] r) throws IOException {
        int pid = r[0];
        if (!registered && mode.equals("agent")) {
            // L'agent Irium fera exactement ça : déclarer ses canaux au serveur dès le PLAY.
            // Un client vanilla ne register JAMAIS irium:hello -> le serveur droppe le challenge.
            // custom_payload serverbound = 0x16 (23e enregistré), body = canaux \0-séparés
            send(out, 0x16, w -> { str(w, "minecraft:register"); w.write("irium:hello".getBytes(StandardCharsets.UTF_8)); });
            registered = true;
            System.out.println("[bot:" + mode + "] minecraft:register irium:hello envoyé");
        }
        // DEBUG: dump horodaté de tout ce qui arrive en PLAY
        System.out.println("[t+" + (System.currentTimeMillis() - start) + "ms] play pid=0x"
                + Integer.toHexString(pid) + " len=" + d.length + " hex=" + hex(d, Math.min(24, d.length)));
        // custom_payload clientbound = 0x18
        if (pid == 0x18) {
            String chan = strAt(d, r[1]);
            int dataOff = r[1] + varIntAt(d, r[1])[1] - r[1] + (strAt(d, r[1]).length() > 0 ? 0 : 0);
            if (chan.equals("irium:hello")) {
                // data = le reste du paquet après le canal
                int chanLen = strAt(d, r[1]).getBytes(StandardCharsets.UTF_8).length;
                // recalcul propre : r[1] pointe après le pid ; varInt longueur du canal puis canal puis data
                int p = r[1];
                int[] lr = varIntAt(d, p);
                p = lr[1] + lr[0]; // début des données
                byte[] body = Arrays.copyOfRange(d, p, d.length);
                if (body.length >= 12 && body[0] == 'I' && body[1] == 'R') {
                    challengeSeen = true;
                    nonceVal = 0;
                    for (int i = 0; i < 8; i++) nonceVal = (nonceVal << 8) | (body[4 + i] & 0xFF);
                    System.out.println("[bot:" + mode + "] CHALLENGE irium nonce=0x" + Long.toHexString(nonceVal));
                    if (mode.equals("agent") && !agentReplied) {
                        agentReplied = true;
                        byte[] resp = Handshake.encodeAgentResponse(nonceVal, "0.1.0", 0x1F);
                        send(out, 0x16, w -> { str(w, "irium:hello"); w.write(resp); });
                        System.out.println("[bot:agent] réponse AGENT envoyée (v0.1.0, caps=0x1F)");
                    }
                } else {
                    System.out.println("[bot:" + mode + "] payload irium non-challenge (" + body.length + "B)");
                }
            }
            return;
        }
        // keep_alive play : id varlong juste après le pid. Garde-fou : ignorer les paquets
        // sans body (pid seul) et les valeurs non-epoch (les autres paquets courts).
        if (r[1] < d.length && d.length <= 10) {
            long id = varLongAt(d, r[1]);
            if (id > 1_500_000_000_000L) {
                send(out, 0x1C, w -> varLong(w, id)); // serverbound keep_alive play = 0x1C (29e)
                return;
            }
        }
    }

    /* ---------------- transport ---------------- */
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
    static String hex(byte[] d, int n) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < n; i++) sb.append(String.format("%02x ", d[i])); return sb.toString(); }
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
