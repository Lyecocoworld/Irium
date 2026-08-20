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

    @Override
    public java.net.URL getResource(String name) {
        if (entries.containsKey(name)) return toUrl(name);
        return super.getResource(name);
    }

    /** ServiceLoader : TOUTES les URLs matchant (mod + parent). */
    @Override
    public java.util.Enumeration<java.net.URL> getResources(String name) throws java.io.IOException {
        java.util.List<java.net.URL> out = new java.util.ArrayList<>();
        java.net.URL own = toUrl(name);
        if (own != null) out.add(own);
        java.util.Enumeration<java.net.URL> parent_ = super.getResources(name);
        while (parent_ != null && parent_.hasMoreElements()) out.add(parent_.nextElement());
        return java.util.Collections.enumeration(out);
    }

    /** URL bytes:// virtuelle — handler EXPLICITEMENT passé (5-arg ctor), aucune factory globale requise. */
    private java.net.URL toUrl(String name) {
        byte[] b = entries.get(name);
        if (b == null) return null;
        try {
            return new java.net.URL("bytes", null, -1, "/" + name, HANDLER);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Handler du protocole bytes: lit l'entrée depuis la map du loader propriétaire. */
    private final java.net.URLStreamHandler HANDLER = new java.net.URLStreamHandler() {
        @Override
        protected java.net.URLConnection openConnection(java.net.URL u) {
            return new java.net.URLConnection(u) {
                @Override public void connect() { connected = true; }
                @Override public java.io.InputStream getInputStream() {
                    String p = u.getPath();
                    byte[] b = entries.get(p.startsWith("/") ? p.substring(1) : p);
                    return b == null ? null : new java.io.ByteArrayInputStream(b);
                }
                @Override public int getContentLength() {
                    String p = u.getPath();
                    byte[] b = entries.get(p.startsWith("/") ? p.substring(1) : p);
                    return b == null ? -1 : b.length;
                }
            };
        }
    };
}
