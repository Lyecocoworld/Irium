package dev.irium.plugin;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * M2 : classification des clients via le canal irium:hello.
 *
 * Flux :
 *  1. start(player) -> envoi du challenge HELLO (12 bytes) sur irium:hello.
 *  2. Réponse valide (nonce echo + agentVersion + caps) -> classement AGENT.
 *  3. Aucune réponse dans HELLO_TIMEOUT_TICKS -> classement VANILLA.
 *  4. Un joueur n'est classé qu'une fois par connexion.
 *
 * Folia-safe : le timeout passe par EntityScheduler.runDelayed (jamais
 * BukkitScheduler), et chaque callback revalide la connexion.
 */
public final class HandshakeListener {

    public static final int HELLO_TIMEOUT_TICKS = 40; // 2 s

    public enum Classification { VANILLA, AGENT }

    public record Classified(Player player, Classification classification,
                             String agentVersion, int caps) {
    }

    private final Plugin plugin;
    private final Map<UUID, Long> pendingNonces = new ConcurrentHashMap<>();
    private final Map<UUID, Classified> classified = new ConcurrentHashMap<>();

    public HandshakeListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Enregistre le canal entrant (à appeler dans onEnable). */
    public void registerChannels() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, IriumPlugin.CHANNEL_HELLO, (channel, player, bytes) ->
                        handleResponse(player, bytes));
    }

    /** Lance le handshake pour un joueur qui vient de rejoindre. */
    public void start(Player player, Consumer<Classified> onClassified) {
        if (classified.containsKey(player.getUniqueId())) {
            return; // déjà classé pour cette connexion
        }
        long nonce = ThreadLocalRandom.current().nextLong();
        pendingNonces.put(player.getUniqueId(), nonce);

        // 1. Challenge immédiat.
        player.sendPluginMessage(plugin, IriumPlugin.CHANNEL_HELLO,
                HandshakeCodec.encodeHello(nonce));
        IriumPlugin.log("hello -> " + player.getName() + " (nonce=" + Long.toHexString(nonce) + ")");

        // 2. Timeout : pas de réponse -> vanilla. EntityScheduler = Folia-safe.
        player.getScheduler().runDelayed(plugin, task -> {
            Long sent = pendingNonces.remove(player.getUniqueId());
            if (sent != null && player.isOnline()) {
                Classified result = new Classified(player, Classification.VANILLA, "", 0);
                classified.put(player.getUniqueId(), result);
                IriumPlugin.log("timeout -> " + player.getName() + " = VANILLA");
                onClassified.accept(result);
            }
        }, () -> {
        }, HELLO_TIMEOUT_TICKS);

        // 3. Réponse éventuelle anticipée.
        pendingResponses.put(player.getUniqueId(), onClassified);
    }

    private final Map<UUID, Consumer<Classified>> pendingResponses = new ConcurrentHashMap<>();

    private void handleResponse(Player player, byte[] bytes) {
        HandshakeCodec.AgentResponse resp = HandshakeCodec.decodeAgentResponse(bytes);
        if (resp == null) {
            IriumPlugin.log("réponse invalide de " + player.getName() + " (ignorée)");
            return;
        }
        Long sent = pendingNonces.remove(player.getUniqueId());
        if (sent == null || sent != resp.nonce()) {
            IriumPlugin.log("nonce invalide de " + player.getName() + " (ignoré)");
            return;
        }
        Consumer<Classified> cb = pendingResponses.remove(player.getUniqueId());
        Classified result = new Classified(player, Classification.AGENT,
                resp.agentVersion(), resp.caps());
        classified.put(player.getUniqueId(), result);
        IriumPlugin.log("response -> " + player.getName() + " = AGENT v" + resp.agentVersion()
                + " caps=0b" + Integer.toBinaryString(resp.caps()));
        if (cb != null) {
            cb.accept(result);
        }
    }

    /** Classement courant (null si handshake non terminé). */
    public Classified classificationOf(UUID playerId) {
        return classified.get(playerId);
    }

    /** Oublie un joueur parti (à appeler sur PlayerQuitEvent). */
    public void forget(UUID playerId) {
        pendingNonces.remove(playerId);
        pendingResponses.remove(playerId);
        classified.remove(playerId);
    }
}
