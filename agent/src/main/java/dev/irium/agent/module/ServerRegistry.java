package dev.irium.agent.module;

import dev.irium.agent.IriumAgent;

import java.nio.file.DirectoryStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * M7-X21 : registre des serveurs Irium connus du client.
 *
 * Source de vérité : le cache disque ~/.irium/servers/<host_port>/ (un
 * dossier par serveur, rempli par les MODJAR streamés). Un dossier avec au
 * moins un .jar = un serveur connu. Les noms d'affichage viennent du
 * servers.json optionnel (~/.irium/servers.json) écrit à la première
 * connexion réussie — sans lui, host:port brut.
 */
public final class ServerRegistry {

    /** host_port -> nom d'affichage (optionnel). */
    private static final java.util.Map<String, String> NAMES = new java.util.concurrent.ConcurrentHashMap<>();

    private ServerRegistry() {
    }

    /** Une entrée du registre : host:port + nom d'affichage. */
    public record ServerEntry(String hostKey, String name, int jarCount) {
        public String displayName() {
            return name != null && !name.isBlank() ? name : hostKey;
        }
    }

    static Path serversRoot() {
        return Path.of(System.getProperty("user.home"), ".irium", "servers");
    }

    /** Liste des serveurs connus (cache non vide), triés par nom. */
    public static List<ServerEntry> list() {
        loadNames();
        List<ServerEntry> out = new ArrayList<>();
        try {
            Path root = serversRoot();
            if (!Files.isDirectory(root)) return out;
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
                for (Path dir : dirs) {
                    if (!Files.isDirectory(dir)) continue;
                    int jars = 0;
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.jar")) {
                        for (Path ignored : files) jars++;
                    }
                    if (jars == 0) continue;
                    String key = dir.getFileName().toString().replace('_', ':');
                    out.add(new ServerEntry(key, NAMES.get(key), jars));
                }
            }
            out.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[gateway] scan cache échoué: " + t);
        }
        return out;
    }

    /** Charge ~/.irium/servers.json : { "host:port": "Nom du serveur", ... } */
    private static void loadNames() {
        try {
            Path f = Path.of(System.getProperty("user.home"), ".irium", "servers.json");
            if (!Files.isRegularFile(f)) return;
            NAMES.clear();
            String raw = Files.readString(f);
            int i = 0;
            // parse minimal { "k": "v", ... } sans dépendance (gson pas dispo
            // en premain ordre 0)
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(raw);
            while (m.find()) NAMES.put(m.group(1), m.group(2));
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[gateway] servers.json illisible: " + t);
        }
    }
}
