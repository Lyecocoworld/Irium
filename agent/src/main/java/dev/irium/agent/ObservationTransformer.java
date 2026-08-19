package dev.irium.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observation-only transformer (M1).
 *
 * Registers nothing, transforms nothing: it only watches class loads and
 * logs a compact summary. The log is the raw material for M4 recipes —
 * it tells us which classes the client actually loads, in what order,
 * and through which classloader generation.
 */
final class ObservationTransformer implements ClassFileTransformer {

    private final AtomicLong total = new AtomicLong();
    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        total.incrementAndGet();
        // Deduplicate: log each distinct class once, first load only.
        if (seen.putIfAbsent(className, Boolean.TRUE) == null) {
            String loaderName = loader == null ? "bootstrap" : loader.getName();
            IriumAgent.log("[observe] " + className + " (loader=" + loaderName + ")");
        }
        return null; // observation only — never modify bytecode here
    }
}
