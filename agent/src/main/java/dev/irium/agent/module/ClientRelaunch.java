package dev.irium.agent.module;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * M7-B7 : relance automatique du client — SANS ProcessHandle.commandLine()
 * (indisponible dans javaw sous Windows). Reconstruction depuis :
 *   - java.home                          -> exécutable
 *   - RuntimeMXBean.getInputArguments()  -> args JVM (chaque arg déjà tokenisé
 *     par la JVM : les espaces dans les chemins survivent, pas de split fragile)
 *   - sun.java.command                   -> classe main + args programme
 *   - java.class.path                    -> classpath si forme -cp
 * + garde anti-boucle : -Dirium.norelaunch=1 posé sur le processus relancé.
 */
public final class ClientRelaunch {

    private ClientRelaunch() {}

    /** Au plus UNE relance par session (anti-boucle). */
    private static volatile boolean done;

    public static boolean mayRelaunch() {
        return !done && !Boolean.getBoolean("irium.norelaunch");
    }

    /** Lance le nouveau client armé et quitte le courant. */
    public static void relaunch(String serverHostPort) {
        if (!mayRelaunch()) {
            dev.irium.agent.IriumAgent.log("[relaunch] relance déjà faite ce boot -> refus (anti-boucle)");
            return;
        }
        done = true;
        try {
            List<String> cmd = buildCommand(serverHostPort);
            if (cmd == null) {
                dev.irium.agent.IriumAgent.log("[relaunch] reconstruction cmdline impossible -> abandon");
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(System.getProperty("user.dir", ".")));
            pb.inheritIO();
            Process p = pb.start();
            dev.irium.agent.IriumAgent.log("[relaunch] nouveau client PID " + p.pid() + " -> " + serverHostPort);
            dev.irium.agent.IriumAgent.log("[relaunch] cmdline: " + String.join(" ", cmd));
            Thread.sleep(1000);
            Runtime.getRuntime().exit(0);
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[relaunch] échec: " + t);
        }
    }

    /** Reconstruit la ligne de commande complète, armée pour serverHostPort. */
    static List<String> buildCommand(String serverHostPort) {
        // 1. exécutable java (javaw.exe si Windows)
        String java = null;
        try { java = ProcessHandle.current().info().command().orElse(null); } catch (Throwable ignored) {}
        if (java == null) {
            String home = System.getProperty("java.home");
            if (home == null) return null;
            java = Path.of(home, "bin",
                    System.getProperty("os.name", "").toLowerCase().contains("win") ? "javaw.exe" : "java").toString();
        }
        List<String> out = new ArrayList<>();
        out.add(java);
        // 2. args JVM d'origine (tokenisés par la JVM — gère les espaces)
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (a.startsWith("-javaagent:") && a.contains("irium")) continue; // remplacé
            if (a.startsWith("-Dirium.")) continue;                           // nos props
            out.add(a);
        }
        out.add("-Dirium.norelaunch=1"); // le processus relancé ne relancera plus
        // 3. agent armé pour CE serveur
        try {
            Path agentJar = Path.of(dev.irium.agent.IriumAgent.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            out.add("-javaagent:" + agentJar + "=boot:" + serverHostPort);
        } catch (Throwable t) {
            dev.irium.agent.IriumAgent.log("[relaunch] agent jar introuvable: " + t);
            return null;
        }
        // 4. main + args programme (on retire les anciens args de connexion)
        String sjc = System.getProperty("sun.java.command", "").trim();
        if (sjc.isEmpty()) return null;
        List<String> mainArgs = stripServerArgs(sjc.split("\\s+"));
        if (mainArgs.isEmpty()) return null;
        if (mainArgs.get(0).equals("-jar")) {
            out.addAll(mainArgs); // -jar <jar> <args...>
        } else {
            String cp = System.getProperty("java.class.path");
            if (cp == null || cp.isBlank()) return null;
            out.add("-cp");
            out.add(cp);
            out.addAll(mainArgs);
        }
        // 5. reconnexion automatique au serveur
        out.add("--quickPlayMultiplayer");
        out.add(serverHostPort);
        return out;
    }

    /** Retire --server/--port/--quickPlay* (et leur valeur) de la commande d'origine. */
    private static List<String> stripServerArgs(String[] toks) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < toks.length; i++) {
            String t = toks[i];
            if (t.startsWith("--quickPlay") || t.equals("--server") || t.equals("--port")) { i++; continue; }
            if (t.isBlank()) continue;
            out.add(t);
        }
        return out;
    }

    /** host:port du serveur courant via le tap netty (null si hors ligne). */
    static String currentServerHostPort() {
        try {
            io.netty.channel.Channel ch = dev.irium.agent.IriumTap.currentChannel();
            if (ch == null) return null;
            String s = String.valueOf(ch.remoteAddress());
            int slash = s.indexOf('/');
            int colon = s.lastIndexOf(':');
            if (slash >= 0 && colon > slash) {
                String hostPart = s.substring(slash + 1, colon);
                String portPart = s.substring(colon + 1).replaceAll("[^0-9]", "");
                return portPart.isEmpty() ? hostPart : hostPart + ":" + portPart;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
