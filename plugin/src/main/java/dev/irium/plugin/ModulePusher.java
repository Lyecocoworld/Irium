package dev.irium.plugin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * M4 : pousse un module (.irm = octets de classe compilée) vers un client AGENT
 * via le canal irium:module.
 *
 * Fil : BEGIN(manifest, totalLen, sha256, mainClass) / CHUNK(seq, octets) /
 * ACTIVATE. Le nom de la classe principale est extrait du bytecode lui-même
 * (this_class) — pas de configuration à maintenir : déposer le .irm dans
 * modules/ suffit.
 *
 * Folia-safe : l'envoi passe par EntityScheduler.run (le canal netty du joueur
 * appartient à sa région).
 */
public final class ModulePusher {

    private static final int CHUNK = 24 * 1024; // marge sous la limite plugin-message

    private final Plugin plugin;
    private final File modulesDir;

    public ModulePusher(Plugin plugin) {
        this.plugin = plugin;
        this.modulesDir = new File(plugin.getDataFolder(), "modules");
        if (!modulesDir.exists()) modulesDir.mkdirs();
    }

    /** @return nom interne du module (sans extension) ou null si introuvable. */
    public String[] available() {
        String[] list = modulesDir.list((d, n) -> n.endsWith(".irm"));
        return list == null ? new String[0] : list;
    }

    /** Pousse un module à un joueur classé AGENT. */
    public void push(Player player, String moduleFile) {
        File f = new File(modulesDir, moduleFile.endsWith(".irm") ? moduleFile : moduleFile + ".irm");
        if (!f.isFile()) {
            IriumPlugin.log("module introuvable: " + moduleFile + " (dans " + modulesDir + ")");
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            IriumPlugin.log("lecture module impossible: " + e.getMessage());
            return;
        }
        String mainClass = ModuleFile.parseClassName(bytes);
        if (mainClass == null) {
            IriumPlugin.log(moduleFile + ": pas un class file valide -> refus");
            return;
        }
        byte[] sha = sha256(bytes);
        final byte[] sent = tamper(bytes);
        // LABO uniquement : simule une falsification en transit (MITM / serveur malveillant)
        // en altérant les octets APRÈS le calcul du manifest. L'agent doit refuser.
        String manifestId = moduleFile.replaceAll("\\.irm$", "");

        player.getScheduler().run(plugin, task -> {
            // 1. BEGIN
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            o.write(0x01);
            writeStr(o, manifestId);
            writeInt(o, sent.length);
            o.write(sha, 0, 32);
            writeStr(o, mainClass);
            player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, o.toByteArray());

            // 2. CHUNKs
            for (int off = 0, seq = 0; off < bytes.length; off += CHUNK, seq++) {
                int n = Math.min(CHUNK, bytes.length - off);
                java.io.ByteArrayOutputStream c = new java.io.ByteArrayOutputStream();
                c.write(0x02);
                writeInt(c, seq);
                c.write(sent, off, n);
                player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, c.toByteArray());
            }

            // 3. ACTIVATE
            player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, new byte[]{0x03});

            IriumPlugin.log("module '" + manifestId + "' poussé -> " + player.getName()
                    + " (" + bytes.length + " octets, " + (bytes.length + CHUNK - 1) / CHUNK + " chunk(s), main=" + mainClass + ")");
        }, () -> {});
    }

    /** Pousse TOUS les modules disponibles à un joueur classé AGENT (M4). */
    public void pushAll(Player player) {
        for (String f : available()) {
            push(player, f);
        }
    }

    /* ---------------- format ---------------- */

    /** LABO : falsifie le dernier octet pour tester le refus côté agent. */
    private static byte[] tamper(byte[] bytes) {
        if (!Boolean.getBoolean("irium.test.tamper") || bytes.length < 17) return bytes;
        byte[] t = bytes.clone();
        t[t.length - 1] ^= 0x5A;
        return t;
    }

    static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeStr(java.io.ByteArrayOutputStream o, String s) {
        byte[] x = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(o, x.length);
        o.write(x, 0, x.length);
    }

    private static void writeInt(java.io.ByteArrayOutputStream o, int v) {
        o.write((v >>> 24) & 0xFF); o.write((v >>> 16) & 0xFF);
        o.write((v >>> 8) & 0xFF); o.write(v & 0xFF);
    }

    private static void writeVarInt(java.io.ByteArrayOutputStream o, int v) {
        while ((v & 0xFFFFFF80) != 0) { o.write((v & 0x7F) | 0x80); v >>>= 7; }
        o.write(v);
    }
}
