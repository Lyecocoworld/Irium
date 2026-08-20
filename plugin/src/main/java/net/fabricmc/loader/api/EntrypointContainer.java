package net.fabricmc.loader.api;

/** Adaptateur Irium — conteneur d'entrypoint. */
public interface EntrypointContainer<T> {

    T getEntrypoint();
}
