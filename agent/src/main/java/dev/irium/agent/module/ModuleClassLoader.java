package dev.irium.agent.module;

import io.netty.channel.Channel;

/**
 * Classloader dédié à UN module d'UNE session. Parent = classloader de
 * l'agent (donc accès à l'API module + au host par délégation standard).
 * Abandonné à la fermeture de session : plus aucune référence -> GC'able,
 * le code serveur ne survit jamais à la déconnexion.
 */
final class ModuleClassLoader extends ClassLoader {

    ModuleClassLoader(Channel channel) {
        super(ModuleClassLoader.class.getClassLoader());
    }

    /** Définit la classe à partir de ses octets, nom extrait du fichier lui-même. */
    Class<?> define(byte[] bytes) throws ClassFormatError {
        String internal = ClassFileName.parse(bytes);
        if (internal == null) throw new ClassFormatError("class file non parsable (this_class introuvable)");
        String name = internal.replace('/', '.');
        Class<?> c = defineClass(name, bytes, 0, bytes.length);
        resolveClass(c);
        return c;
    }
}
