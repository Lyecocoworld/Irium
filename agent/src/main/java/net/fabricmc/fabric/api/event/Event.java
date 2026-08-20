package net.fabricmc.fabric.api.event;

import net.minecraft.resources.Identifier;

/**
 * Adaptateur Irium — forme exacte de l'officiel : classe abstraite avec
 * invoker volatile, DEFAULT_PHASE = Identifier("fabric", "default").
 */
public abstract class Event<T> {

    /** Rempli statiquement — voir EventFactory (chargeur de classe). */
    public static final Identifier DEFAULT_PHASE;

    static {
        Identifier phase = null;
        try {
            phase = Identifier.fromNamespaceAndPath("fabric", "default");
        } catch (Throwable ignored) {
        }
        DEFAULT_PHASE = phase;
    }

    protected volatile T invoker;

    public final T invoker() {
        return invoker;
    }

    public abstract void register(T handler);

    public void register(Identifier phase, T handler) {
        register(handler);
    }

    public void addPhaseOrdering(Identifier firstPhase, Identifier laterPhase) {
        // v1 : ordre d'insertion
    }
}
