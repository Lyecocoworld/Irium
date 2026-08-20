package net.fabricmc.loader.api.entrypoint;

/**
 * Adaptateur Irium — copie bytecode-exacte de l'officiel (package
 * net.fabricmc.loader.api.entrypoint).
 */
public interface EntrypointContainer<T> {

    T getEntrypoint();

    default String getDefinition() { return "irium"; }
}
