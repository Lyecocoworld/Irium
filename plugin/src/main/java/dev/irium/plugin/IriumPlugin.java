package dev.irium.plugin;




import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Irium — Phase 0 / J1 : consentement + canal hello.
 *
 * Règles d'architecture respectées :
 * - Folia-safe : aucun BukkitScheduler, callbacks reschedulés sur EntityScheduler
 * - Dialog gate : protocol >= 767 sinon fallback chat cliquable
 * - Aucun emoji dans les labels (charte CocoWorld), MiniMessage partout
 * - Consentement persistant par joueur (jamais redemandé)
 */
public final class IriumPlugin extends JavaPlugin {

    public static final String CHANNEL_HELLO = "irium:hello";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Set<UUID> active = new HashSet<>();
    private final Set<UUID> declined = new HashSet<>();
    private File consentFile;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        consentFile = new File(getDataFolder(), "consent.yml");
        loadConsent();
        messages = loadMessagesFr();

        // Canal hello (login/configuration) — détection agent (M2 : handshake réel)
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL_HELLO);
        handshake = new HandshakeListener(this);
        handshake.registerChannels();

        // Commande /irium (CommandMap legacy-compatible — jamais getCommand() avec paper-plugin.yml)
        registerCommand();

        // Join : proposer une fois par joueur (si pas déjà de choix)
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        getLogger().info("Irium 0.2.0 (M2) — consent + handshake. Canal: " + CHANNEL_HELLO);
    }

    /** Log préfixé (utilisé par HandshakeListener et les tests). */
    public static void log(String message) {
        JavaPlugin pl = instance;
        if (pl != null) pl.getLogger().info(message);
        else System.out.println("[irium] " + message);
    }

    private static IriumPlugin instance;

    @Override
    public void onDisable() {
        saveConsent();
    }

    /* ---------------- messages ---------------- */

    private YamlConfiguration loadMessagesFr() {
        File f = new File(getDataFolder(), "messages_fr.yml");
        if (!f.exists()) {
            try {
                Files.createDirectories(f.getParentFile().toPath());
                Files.copy(getResource("messages_fr.yml"), f.toPath());
            } catch (IOException e) {
                getLogger().warning("messages_fr.yml manquant: " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    public Component msg(String key) {
        return MM.deserialize(messages.getString(key, "<#FA4943>message manquant: " + key + "</#FA4943>"));
    }

    public String raw(String key) {
        return messages.getString(key, key);
    }

    /* ---------------- consentement ---------------- */

    public boolean hasChosen(UUID id)      { return active.contains(id) || declined.contains(id); }
    public boolean isActive(UUID id)       { return active.contains(id); }

    public void setActive(UUID id, boolean on) {
        if (on) { active.add(id); declined.remove(id); }
        else    { declined.add(id); active.remove(id); }
        saveConsent();
    }

    private void loadConsent() {
        if (!consentFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(consentFile);
        for (String s : y.getStringList("active")) active.add(UUID.fromString(s));
        for (String s : y.getStringList("declined")) declined.add(UUID.fromString(s));
    }

    private void saveConsent() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("active", active.stream().map(UUID::toString).toList());
        y.set("declined", declined.stream().map(UUID::toString).toList());
        try { y.save(consentFile); } catch (IOException e) { getLogger().warning("consent save: " + e.getMessage()); }
    }

    /* ---------------- détection agent (M2) ---------------- */

    private HandshakeListener handshake;

    HandshakeListener handshake() {
        return handshake;
    }

    /** M2 : handshake réel sur le canal irium:hello. */
    public boolean agentDetected(Player p) {
        HandshakeListener.Classified c = handshake != null ? handshake.classificationOf(p.getUniqueId()) : null;
        return c != null && c.classification() == HandshakeListener.Classification.AGENT;
    }

    public void sendHello(Player p) {
        if (handshake != null) {
            handshake.start(p, classified ->
                    getLogger().info("client classé: " + classified.player().getName()
                            + " = " + classified.classification()
                            + (classified.classification() == HandshakeListener.Classification.AGENT
                            ? " v" + classified.agentVersion() : "")));
        }
    }

    /* ---------------- commande ---------------- */

    private void registerCommand() {
        org.bukkit.command.Command cmd = new org.bukkit.command.Command("irium") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player p)) return true;
                if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
                    String state = isActive(p.getUniqueId()) ? "ACTIF" : (hasChosen(p.getUniqueId()) ? "classique" : "non choisi");
                    String agent = agentDetected(p) ? "détecté" : "non détecté";
                    p.sendMessage(msg("prefix").append(MM.deserialize(
                            raw("status_line").replace("{state}", state).replace("{agent}", agent))));
                    return true;
                }
                if (args.length > 0 && args[0].startsWith("__")) {
                    // chemins cliqués depuis le fallback chat
                    p.getScheduler().run(IriumPlugin.this, task -> {
                        if (args[0].equals("__accept")) ConsentFlow.accept(IriumPlugin.this, p);
                        else ConsentFlow.decline(IriumPlugin.this, p);
                    }, () -> {
                    });
                    return true;
                }
                p.getScheduler().run(IriumPlugin.this, task -> ConsentFlow.offer(IriumPlugin.this, p), () -> {
                });
                return true;
            }
        };
        getServer().getCommandMap().register("irium", "irium", cmd);
    }
}
