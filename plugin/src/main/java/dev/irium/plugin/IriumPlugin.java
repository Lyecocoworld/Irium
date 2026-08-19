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
        saveDefaultConfig();
        consentFile = new File(getDataFolder(), "consent.yml");
        loadConsent();
        messages = loadMessagesFr();

        // Canal hello (login/configuration) — détection agent (J2 branchera l'écoute réelle)
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL_HELLO);

        // Commande /irium (CommandMap legacy-compatible — jamais getCommand() avec paper-plugin.yml)
        registerCommand();

        // Join : proposer une fois par joueur (si pas déjà de choix)
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        getLogger().info("Irium 0.1.0 (J1) — consent layer ready. Canal: " + CHANNEL_HELLO);
    }

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

    /* ---------------- détection agent (J2) ---------------- */

    /** J2 branchera la réponse réelle au canal hello. J1 : heuristique = activé + rejoin. */
    public boolean agentDetected(Player p) {
        return active.contains(p.getUniqueId());
    }

    public void sendHello(Player p) {
        if (p.getListeningPluginChannels().contains(CHANNEL_HELLO)) {
            p.sendPluginMessage(this, CHANNEL_HELLO, new byte[]{1});
            getLogger().info("hello envoyé à " + p.getName() + " (agent présent)");
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
                    p.getScheduler().run(IriumPlugin.this, null,
                            () -> {
                                if (args[0].equals("__accept")) ConsentFlow.accept(IriumPlugin.this, p);
                                else ConsentFlow.decline(IriumPlugin.this, p);
                            });
                    return true;
                }
                p.getScheduler().run(IriumPlugin.this, null, () -> ConsentFlow.offer(IriumPlugin.this, p));
                return true;
            }
        };
        getServer().getCommandMap().register("irium", "irium", cmd);
    }
}
