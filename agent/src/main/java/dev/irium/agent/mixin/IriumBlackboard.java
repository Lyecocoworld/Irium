package dev.irium.agent.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.HashMap;
import java.util.Map;

/**
 * Blackboard Irium : IGlobalPropertyService (comme Blackboard de Fabric, Apache-2.0).
 * Stockage clé→valeur + properties système (mixin.* via clés préfixées).
 */
public final class IriumBlackboard implements IGlobalPropertyService {

    private final Map<IPropertyKey, Object> values = new HashMap<>();

    @Override public IPropertyKey resolveKey(String name) { return new StringKey(name); }

    @Override public <T> T getProperty(IPropertyKey key) {
        String name = ((StringKey) key).name;
        if (name.startsWith("system.")) {
            @SuppressWarnings("unchecked")
            T v = (T) System.getProperty(name.substring("system.".length()));
            return v;
        }
        @SuppressWarnings("unchecked")
        T v = (T) values.get(key);
        return v;
    }

    @Override public void setProperty(IPropertyKey key, Object value) {
        values.put(key, value);
    }

    @Override public <T> T getProperty(IPropertyKey key, T defaultValue) {
        T v = getProperty(key);
        return v != null ? v : defaultValue;
    }

    @Override public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object v = getProperty(key);
        return v != null ? String.valueOf(v) : defaultValue;
    }

    private static final class StringKey implements IPropertyKey {
        final String name;
        StringKey(String name) { this.name = name; }
        @Override public boolean equals(Object o) {
            return o instanceof StringKey k && k.name.equals(name);
        }
        @Override public int hashCode() { return name.hashCode(); }
        @Override public String toString() { return name; }
    }
}
