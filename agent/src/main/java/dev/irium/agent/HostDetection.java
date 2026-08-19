package dev.irium.agent;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether the host JVM is a Minecraft client.
 *
 * M1 uses a conservative heuristic: main class name plus classpath evidence
 * (the client jar or its version directory). False positives are acceptable
 * at this stage (worst case: an observation transformer logs class loads);
 * false negatives simply keep the agent dormant. The detection criteria will
 * be tightened from the observation logs themselves in later milestones.
 */
final class HostDetection {

    record Result(boolean minecraft, String mainClass, List<String> evidence) {
        @Override
        public String toString() {
            return "minecraft=" + minecraft + ", mainClass=" + mainClass
                    + ", evidence=" + evidence;
        }
    }

    private HostDetection() {
    }

    static Result detect() {
        String mainClass = mainClassName();
        List<String> evidence = new ArrayList<>();

        // 1. Main class — the canonical client entry point (Mojang mapped).
        if (mainClass != null) {
            String mc = mainClass.toLowerCase(Locale.ROOT);
            if (mc.contains("net.minecraft.client.main.main")
                    || mc.contains("net.minecraft.client.main")) {
                evidence.add("main-class:" + mainClass);
            }
        }

        // 2. Classpath — a jar or folder whose path mentions a Minecraft
        //    client version directory (.minecraft/versions/...).
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(File.pathSeparator)) {
            String p = entry.toLowerCase(Locale.ROOT);
            if (p.contains("versions") && (p.contains(".minecraft")
                    || p.contains("minecraft") && p.endsWith(".jar"))) {
                evidence.add("classpath:" + shorten(entry));
                if (evidence.size() >= 4) {
                    break;
                }
            }
        }

        boolean minecraft = !evidence.isEmpty();
        return new Result(minecraft, mainClass, evidence);
    }

    private static String shorten(String path) {
        return path.length() > 90 ? "..." + path.substring(path.length() - 87) : path;
    }

    private static String mainClassName() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            // not the main class — fall through to the JVM name heuristic below
            if (args != null && args.toString().toLowerCase(Locale.ROOT).contains("irium")) {
                // launched from our own lab tooling; keep going anyway
            }
        } catch (Throwable ignored) {
        }
        String name = ManagementFactory.getRuntimeMXBean().getName(); // pid@host
        // Best-effort: the launcher main class, when discoverable.
        ProcessHandle ph = ProcessHandle.current();
        return ph.info().commandLine().map(c -> {
            // The main class is the first non-option token; keep it simple:
            // any token containing "net.minecraft" wins.
            for (String token : c.split("\\s+")) {
                if (token.contains("net.minecraft")) {
                    return token;
                }
                if (token.endsWith(".jar") && !token.contains(File.separator + "lib")) {
                    return token; // -jar launch: the jar IS the app
                }
                if (token.startsWith("-")) {
                    continue;
                }
            }
            return name;
        }).orElse(name);
    }
}
