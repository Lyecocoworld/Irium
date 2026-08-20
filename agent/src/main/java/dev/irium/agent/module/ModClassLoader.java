package dev.irium.agent.module;

import java.util.Map;

/**
 * Classloader dédié à UN mod Fabric streamé d'UNE session.
 * Parent = classloader de l'agent (API module + Minecraft par délégation).
 * Toutes les entrées du jar mod sont en mémoire ; findClass y pioche,
 * les ressources aussi (mixins.json, assets). Abandonné à la déconnexion :
 * plus aucune référence -> GC'able, le code serveur ne survit jamais à la déconnexion.
 */
final class ModClassLoader extends ClassLoader {

    private final Map<String, byte[]> entries;

    ModClassLoader(Map<String, byte[]> entries) {
        super(ModClassLoader.class.getClassLoader());
        this.entries = entries;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        byte[] b = entries.get(path);
        if (b == null) throw new ClassNotFoundException(name);
        return defineClass(name, b, 0, b.length);
    }

    @Override
    public java.io.InputStream getResourceAsStream(String name) {
        byte[] b = entries.get(name.startsWith("/") ? name.substring(1) : name);
        if (b == null) return super.getResourceAsStream(name);
        return new java.io.ByteArrayInputStream(b);
    }
}
