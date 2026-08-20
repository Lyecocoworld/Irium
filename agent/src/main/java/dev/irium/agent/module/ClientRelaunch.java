package dev.irium.agent.module;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * M7-B6 : relance automatique du client avec le bon set de mods armé.
 *
 * Quand le MODSET du serveur != ce qui est armé dans CE boot, l'agent :
 *   1. récupère la ligne de commande du processus courant,
 *   2. remplace l'arg de l'agent par boot:host:port,
 *   3. ajoute --quickPlayMultiplayer host:port (reconnexion auto au serveur),
 *   4. démarre le nouveau process et quitte proprement l'ancien.
 * Launcher-agnostique : on ne dépend que de la commande du process lui-même.
 */
public final class ClientRelaunch {

    private ClientRelaunch() {}

    /** Lance le nouveau client armé et quitte le courant. */
    public static void relaunch(String serverHostPort) {
        try {
            String cmd = ProcessHandle.current().info().commandLine().orElse(null);
            if (cmd == null) {
                dev.irium.agent.IriumAgent.log("[relaunch] ligne de commande introuvable -> abandon");
                return;
            }
            Path agentJar = Path.of(dev.irium.agent.IriumAgent.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            List<String> out = new ArrayList<>();
            for (String tok : cmd.split("\\s+")) {
                if (tok.isBlank()) continue;
                if (tok.startsWith("-javaagent:")) continue; // sera ré-ajouté avec boot:
                out.add(tok);
            }
            int insertAt = indexOfMain(out);
            out.add(insertAt, "-javaagent:" + agentJar + "=boot:" + serverHostPort);
            // --quickPlayMultiplayer doit suivre la classe main : on l'append à la fin
            out.add("--quickPlayMultiplayer");
            out.add(serverHostPort);

            ProcessBuilder pb = new ProcessBuilder(out);
            pb.directory(new java.io.File(System.getProperty("user.dir", ".")));
            pb.inheritIO();
            Process p = pb.start();
            dev.irium.agent.IriumAgent.log("[relaunch] nouveau client PID " + p.pid() + " -> " + serverHostPort);
            Thread.sleep(800);
            Runtime.getRuntime().exit(0);
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[relaunch] échec: " + t);
        }
    }

    /** Options JVM suivies d'une valeur séparée (le token suivant n'est PAS la main class). */
    private static final java.util.Set<String> OPTS_WITH_VALUE = java.util.Set.of(
            "-cp", "-classpath", "--class-path", "-D", "-Xbootclasspath");

    /** Index du token classe principale (après java.exe, en ignorant options+valeurs). */
    static int indexOfMain(List<String> toks) {
        if (toks.isEmpty()) return 0;
        // token 0 = java.exe ; -jar <jar> la cible suit directement
        for (int i = 1; i < toks.size(); i++) {
            String t = toks.get(i);
            if (t.equals("-jar")) return i + 1 < toks.size() ? i + 1 : toks.size();
            if (t.startsWith("-")) {
                // option avec valeur séparée -> sauter la valeur
                String bare = t.contains("=") ? t.substring(0, t.indexOf('=')) : t;
                if (OPTS_WITH_VALUE.contains(bare) && !t.contains("=")) {
                    i++; // sauter la valeur
                }
                continue;
            }
            return i; // premier token non-option = classe principale
        }
        return toks.size();
    }
}
