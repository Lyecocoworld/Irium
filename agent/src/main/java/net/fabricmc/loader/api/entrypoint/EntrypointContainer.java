package net.fabricmc.loader.api.entrypoint;

/**
 * Adaptateur Irium — copie bytecode-exacte de l'officiel (package
 * net.fabricmc.loader.api.entrypoint). getProvider() requis par les mods
 * récents (ex. Mod Menu) : retourne le ModContainer qui déclare l'entrypoint.
 */
public interface EntrypointContainer<T> {

    T getEntrypoint();

    default String getDefinition() { return "irium"; }

    default net.fabricmc.loader.api.ModContainer getProvider() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("irium").orElse(null);
    }
}
