package dev.irium.agent;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Irium client agent — M1 skeleton.
 *
 * Dormant by default. The premain detects whether the host process is a
 * Minecraft client; on any other Java program the agent exits immediately
 * and touches nothing. On a Minecraft client it installs an
 * observation-only ClassFileTransformer that logs class loads during the
 * startup window: this produces the real anchoring map for M4 recipes.
 */
public final class IriumAgent {

    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private IriumAgent() {
    }

    /** Entry point when started with -javaagent (full power window). */
    public static void premain(String args, Instrumentation inst) {
        bootstrap(args, inst, false);
    }

    /** Entry point when attached hot to a running JVM (retransform window). */
    public static void agentmain(String args, Instrumentation inst) {
        bootstrap(args, inst, true);
    }

    private static void bootstrap(String args, Instrumentation inst, boolean hotAttach) {
        if (!ACTIVE.compareAndSet(false, true)) {
            return; // already bootstrapped — ignore duplicate attach
        }
        String mode = hotAttach ? "attach" : "premain";
        boolean force = args != null && args.contains("force");
        try {
            HostDetection.Result host = HostDetection.detect();
            log("[" + mode + "] irium-agent 0.3.0 bootstrapping" + (force ? " (force)" : ""));
            log("[" + mode + "] host detection: " + host);

            if (!host.minecraft() && !force) {
                // Dormant: not a Minecraft process. Touch nothing further.
                log("[" + mode + "] non-Minecraft process -> dormant, no transformer registered");
                return;
            }

            log("[" + mode + "] Minecraft detected -> registering netty hook (M3) + observation transformer");
            inst.addTransformer(new NettyHook(), false);
            inst.addTransformer(new ObservationTransformer(), true);
        } catch (Throwable t) {
            // A client agent must NEVER break the host process.
            log("[" + mode + "] bootstrap failed, staying dormant: " + t);
        }
    }

    static void log(String message) {
        System.err.println("[irium] " + message);
    }

    /** Diagnostic verbeux (labo). */
    static boolean DEBUG = true; // M3 lab : garder trace de chaque trame vue par le tap
}
