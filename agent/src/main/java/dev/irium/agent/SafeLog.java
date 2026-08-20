package dev.irium.agent;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Logger sûr DANS les callbacks JVMTI (transform/redefinition).
 *
 * PIÈGE M7-B7 (crash réel) : System.err/String.format dans transform() peut
 * charger des classes (Formatter...) pendant qu'un autre thread les charge
 * aussi -> ClassCircularityError -> crash du client.
 *
 * Ici : file non bloquante (classes préchargées) + thread dédiée qui loggue.
 * Aucune allocation de formatage dans le callback — concaténation simple
 * (invokedynamic String.concat, déjà résolu au premier appel hors callback).
 */
public final class SafeLog {

    private static final ConcurrentLinkedQueue<String> PENDING = new ConcurrentLinkedQueue<>();
    private static volatile boolean started;

    private SafeLog() {}

    /** À appeler UNE fois depuis bootstrap() AVANT tout addTransformer. */
    public static void start() {
        if (started) return;
        started = true;
        // précharger la machinerie de concat + log HORS callback
        warm("SafeLog warmup");
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    String s = PENDING.poll();
                    if (s == null) { Thread.sleep(100); continue; }
                    System.err.println(s);
                } catch (Throwable ignored) {
                    return;
                }
                }
        }, "irium-safelog");
        t.setDaemon(true);
        t.start();
    }

    /** Warmup : force la résolution de la concaténation et du println hors callback. */
    private static void warm(String s) {
        PENDING.offer("[irium] " + s);
        System.err.println("[irium] SafeLog prêt");
    }

    /** Appelable depuis un callback JVMTI : concat simple + file. JAMAIS de format. */
    public static void v(String tag, Object msg) {
        if (!started) return; // pas démarré -> silence (sécurité)
        PENDING.offer("[irium] " + tag + " " + String.valueOf(msg));
    }

    /** Appelable depuis un callback JVMTI. */
    public static void v(String tag, Object a, Object b) {
        if (!started) return;
        PENDING.offer("[irium] " + tag + " " + a + " " + b);
    }

    /** M7-B9 : queue directe (non-callback) — utilisé par les packs mixins. */
    public static void offer(String line) {
        if (!started) return;
        PENDING.offer("[irium] " + line);
    }
}
