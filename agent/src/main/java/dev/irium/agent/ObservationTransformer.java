package dev.irium.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Observation-only transformer (M1).
 *
 * PIÈGE M7-B7 (crash réel ClassCircularityError java/util/Formatter$FormatSpecifierParser) :
 * TOUT code exécuté dans transform() peut déclencher du chargement de classes
 * (String.format -> Formatter -> ...). Si un autre thread charge la même classe
 * en parallèle -> ClassCircularityError -> crash du client pendant l'init.
 *
 * Règles absolues :
 *  - transform() ne fait AUCUNE opération pouvant charger une classe (pas de log,
 *    pas de format, pas d'autoboxing lourd). On ne touche que des types déjà
 *    chargés avant l'enregistrement du transformer (Queue, String déjà en mémoire).
 *  - L'observation détaillée est OPT-IN : -Dirium.observe=1. Sinon : compteur muet.
 *  - Quand active, les noms sont mis en file et loggués par une thread dédiée
 *    (démarrée AVANT l'enregistrement, classes préchargées par avance).
 */
final class ObservationTransformer implements ClassFileTransformer {

    private static final boolean OBSERVE = Boolean.getBoolean("irium.observe");

    private static final ConcurrentLinkedQueue<String> PENDING = new ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.atomic.AtomicLong TOTAL =
            new java.util.concurrent.atomic.AtomicLong();

    private static volatile boolean drainStarted;

    /** À appeler AVANT addTransformer : précharge les classes du drain. */
    static void startDrain() {
        if (drainStarted) return;
        drainStarted = true;
        PENDING.offer(""); // précharge offer/poll/isEmpty dès maintenant
        PENDING.poll();
        if (OBSERVE) {
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        String s = PENDING.poll();
                        if (s == null) { Thread.sleep(200); continue; }
                        if (!s.isEmpty()) IriumAgent.log("[observe] " + s);
                    } catch (Throwable ignored) {
                        return;
                    }
                }
            }, "irium-observe-drain");
            t.setDaemon(true);
            t.start();
        }
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        TOTAL.incrementAndGet();
        if (OBSERVE && className != null) {
            // AUCUN log ici — file seulement (classes ConcurrentLinkedQueue déjà chargées)
            String loaderName = loader == null ? "bootstrap" : String.valueOf(loader.getName());
            PENDING.offer(className + " (loader=" + loaderName + ")");
        }
        return null; // observation only — never modify bytecode here
    }
}
