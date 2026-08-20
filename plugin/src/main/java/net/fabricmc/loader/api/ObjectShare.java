package net.fabricmc.loader.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Adaptateur Irium — ObjectShare officiel (clé→valeur partagée). */
public interface ObjectShare {

    Object get(String key);

    Object put(String key, Object value);

    Object remove(String key);

    <V> V computeIfAbsent(String key, Function<String, V> computingFunction);

    Map<String, Object> toMap();

    static ObjectShare create() {
        return new ObjectShare() {
            private final Map<String, Object> map = new ConcurrentHashMap<>();

            @Override public Object get(String key) { return map.get(key); }
            @Override public Object put(String key, Object value) { return map.put(key, value); }
            @Override public Object remove(String key) { return map.remove(key); }
            @Override @SuppressWarnings("unchecked")
            public <V> V computeIfAbsent(String key, Function<String, V> f) {
                return (V) map.computeIfAbsent(key, f);
            }
            @Override public Map<String, Object> toMap() { return Map.copyOf(map); }
        };
    }
}
