package dev.irium.plugin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * M5 : pousse une recette de transformation vers un client AGENT.
 * Format (canal irium:module, type 0x04) :
 *   [0x04][target str][method str][desc str][anchor-hex str][bridge str]
 * L'ancre = sha256 des octets originaux de la classe cible côté client.
 */
public final class RecipePusher {

    private final Plugin plugin;

    public RecipePusher(Plugin plugin) {
        this.plugin = plugin;
    }

    public void push(Player player, String target, String method, String desc, String anchorHex) {
        String bridge = "dev/irium/agent/hud/HudBridge";
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(0x04);
        writeStr(o, target);
        writeStr(o, method);
        writeStr(o, desc);
        writeStr(o, anchorHex);
        writeStr(o, bridge);
        player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_MODULE, o.toByteArray());
        plugin.getLogger().info("[recette] poussée -> " + player.getName()
                + " : " + target + "." + method + desc + " (ancre " + anchorHex.substring(0, 8) + "..)");
    }

    static void writeStr(java.io.ByteArrayOutputStream o, String s) {
        byte[] x = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(o, x.length);
        o.write(x, 0, x.length);
    }

    static void writeVarInt(java.io.ByteArrayOutputStream o, int v) {
        while ((v & 0xFFFFFF80) != 0) { o.write((v & 0x7F) | 0x80); v >>>= 7; }
        o.write(v);
    }
}
