package dev.irium.plugin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * M7-B : pousse un jar de mod Fabric COMPLET vers un client AGENT via
 * irium:module. Le client l'installe via FabricModHost (classloader dédié,
 * entrypoints, mixins enregistrés — sandbox : tout retombe à la déconnexion).
 *
 * Fil : BEGIN(manifestId, totalLen, sha256, "") / CHUNK(seq, octets) /
 * MODJAR (0x05). La limite 32 Mo couvre SVC (5,6 Mo) et Xaero (1,6 Mo).
 *
 * Folia-safe : EntityScheduler.run (le canal netty du joueur appartient à sa
 * région).
 */
public final class ModJarPusher {

    private static final int CHUNK = 24 * 1024; // marge sous la limite plugin-message

    private final Plugin plugin;
    private final File modJarsDir;

    public ModJarPusher(Plugin plugin) {
        this.plugin = plugin;
        this.modJarsDir = new File(plugin.getDataFolder(), "modjars");
        if (!modJarsDir.exists()) modJarsDir.mkdirs();
    }

    /** @return jars disponibles dans modjars/. */
    public String[] available() {
        String[] list = modJarsDir.list((d, n) -> n.endsWith(".jar"));
        return list == null ? new String[0] : list;
    }

    /** Pousse un mod jar à un joueur classé AGENT. */
    public void push(Player player, String modJar) {
        File f = new File(modJarsDir, modJar.endsWith(".jar") ? modJar : modJar + ".jar");
        if (!f.isFile()) {
            IriumPlugin.log("modjar introuvable: " + modJar + " (dans " + modJarsDir + ")");
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            IriumPlugin.log("lecture modjar impossible: " + e.getMessage());
            return;
        }
        byte[] sha = sha256(bytes);
        String manifestId = f.getName().replaceAll("\\.jar$", "");

        player.getScheduler().run(plugin, task -> {
            // 1. BEGIN (mainClass vide : fabric.mod.json fait foi)
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            o.write(0x01);
            writeStr(o, manifestId);
            writeInt(o, bytes.length);
            o.write(sha, 0, 32);
            writeStr(o, "");
            player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, o.toByteArray());

            // 2. CHUNKs
            for (int off = 0, seq = 0; off < bytes.length; off += CHUNK, seq++) {
                int n = Math.min(CHUNK, bytes.length - off);
                java.io.ByteArrayOutputStream c = new java.io.ByteArrayOutputStream();
                c.write(0x02);
                writeInt(c, seq);
                c.write(bytes, off, n);
                player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, c.toByteArray());
            }

            // 3. MODJAR (fin + installation)
            player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, new byte[]{0x05});

            IriumPlugin.log("modjar '" + manifestId + "' poussé -> " + player.getName()
                    + " (" + bytes.length + " octets, " + (bytes.length + CHUNK - 1) / CHUNK + " chunk(s))");
        }, () -> {});
    }

    /** Pousse TOUS les modjars disponibles à un joueur classé AGENT. */
    public void pushAll(Player player) {
        for (String f : available()) {
            push(player, f);
        }
    }

    /**
     * M7-B6 : pousse le MODSET (0x06) — la liste id+sha256hex de tous les mods du
     * serveur, AVANT les jars. Le client compare avec son boot armé : match ->
     * activation directe ; mismatch -> il cache les jars puis se relance armé.
     */
    public void pushModset(Player player) {
        String[] files = available();
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(0x06);
        o.write(files.length);
        for (String f : files) {
            File jar = new File(modJarsDir, f);
            try {
                byte[] bytes = Files.readAllBytes(jar.toPath());
                String id = f.replaceAll("\\.jar$", "");
                String shaHex = hex(sha256(bytes));
                writeStr(o, id);
                writeStr(o, shaHex);
            } catch (IOException e) {
                IriumPlugin.log("modset: lecture " + f + " impossible: " + e.getMessage());
            }
        }
        player.getScheduler().run(plugin, task ->
                player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, o.toByteArray()), () -> {});
        IriumPlugin.log("modset poussé -> " + player.getName() + " (" + files.length + " mod(s))");
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
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
