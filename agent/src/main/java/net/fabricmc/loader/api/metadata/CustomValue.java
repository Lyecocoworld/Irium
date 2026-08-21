package net.fabricmc.loader.api.metadata;

import java.util.Iterator;
import java.util.Set;

/**
 * Adaptateur Irium — valeurs custom de fabric.mod.json.
 * M7-X2 : signatures EXACTES de fabric-loader 0.17 (vérifiées par
 * harness/api-surface-check.py) — les anciennes asString()/asObject()
 * avaient des noms fantômes, CustomValue est lu par Mod Menu
 * (custom.modmenu:api_level, badges, links) et par les configs de mods.
 */
public interface CustomValue {

    CvType getType();

    default String getAsString() { throw new UnsupportedOperationException(); }

    default Number getAsNumber() { throw new UnsupportedOperationException(); }

    default boolean getAsBoolean() { throw new UnsupportedOperationException(); }

    default CvObject getAsObject() { throw new UnsupportedOperationException(); }

    default CvArray getAsArray() { throw new UnsupportedOperationException(); }

    enum CvType { STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL }

    /** Object custom — Iterator<Map.Entry<String, CustomValue>> + accès par clé. */
    interface CvObject extends Iterable<java.util.Map.Entry<String, CustomValue>> {
        CustomValue get(String key);
        Set<String> keySet();
        boolean containsKey(String key);
        int size();
    }

    /** Array custom — Iterator<CustomValue>. */
    interface CvArray extends Iterable<CustomValue> {
        CustomValue get(int index);
        int size();
    }

    /** Fabrique Irium : valeur immuable depuis un objet JSON déjà dé-sérialisé
     *  (Map/List/String/Number/Boolean/null). */
    static CustomValue of(Object o) {
        if (o == null) return NULL;
        if (o instanceof String s) return new Impl(s);
        if (o instanceof Number n) return new Impl(n);
        if (o instanceof Boolean b) return new Impl(b);
        if (o instanceof java.util.List<?> l) {
            java.util.List<CustomValue> out = new java.util.ArrayList<>(l.size());
            for (Object x : l) out.add(of(x));
            return new Impl(new CvArrayImpl(out));
        }
        if (o instanceof java.util.Map<?, ?> m) {
            java.util.LinkedHashMap<String, CustomValue> out = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), of(e.getValue()));
            }
            return new Impl(new CvObjectImpl(out));
        }
        return NULL;
    }

    CustomValue NULL = new Impl(null);

    final class Impl implements CustomValue {
        private final Object v;
        Impl(Object v) { this.v = v; }
        @Override public CvType getType() {
            if (v == null) return CvType.NULL;
            if (v instanceof String) return CvType.STRING;
            if (v instanceof Number) return CvType.NUMBER;
            if (v instanceof Boolean) return CvType.BOOLEAN;
            if (v instanceof CvObject) return CvType.OBJECT;
            if (v instanceof CvArray) return CvType.ARRAY;
            return CvType.NULL;
        }
        @Override public String getAsString() { return String.valueOf(v); }
        @Override public Number getAsNumber() { return (Number) v; }
        @Override public boolean getAsBoolean() { return (Boolean) v; }
        @Override public CvObject getAsObject() { return (CvObject) v; }
        @Override public CvArray getAsArray() { return (CvArray) v; }
    }

    final class CvObjectImpl implements CvObject {
        private final java.util.LinkedHashMap<String, CustomValue> m;
        CvObjectImpl(java.util.LinkedHashMap<String, CustomValue> m) { this.m = m; }
        @Override public CustomValue get(String key) { return m.get(key); }
        @Override public Set<String> keySet() { return java.util.Collections.unmodifiableSet(m.keySet()); }
        @Override public boolean containsKey(String key) { return m.containsKey(key); }
        @Override public int size() { return m.size(); }
        @Override public Iterator<java.util.Map.Entry<String, CustomValue>> iterator() {
            return java.util.Collections.unmodifiableMap(m).entrySet().iterator();
        }
    }

    final class CvArrayImpl implements CvArray {
        private final java.util.List<CustomValue> l;
        CvArrayImpl(java.util.List<CustomValue> l) { this.l = l; }
        @Override public CustomValue get(int index) { return l.get(index); }
        @Override public int size() { return l.size(); }
        @Override public Iterator<CustomValue> iterator() { return java.util.Collections.unmodifiableList(l).iterator(); }
    }
}
