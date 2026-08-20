package net.fabricmc.loader.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * Adaptateur Irium — ModContainer officiel (getRootPaths() = List<Path>).
 */
public interface ModContainer {

    ModMetadata getMetadata();

    List<Path> getRootPaths();

    default Optional<Path> findPath(String file) {
        for (Path root : getRootPaths()) {
            Path p = root.resolve(file).normalize();
            if (java.nio.file.Files.exists(p)) return Optional.of(p);
        }
        return Optional.empty();
    }

    ModOrigin getOrigin();

    default Optional<ModContainer> getContainingMod() { return Optional.empty(); }

    default java.util.Collection<ModContainer> getContainedMods() { return List.of(); }

    default Path getRoot() { return getRootPaths().get(0); }

    default Path getRootPath() { return getRoot(); }

    default Path getPath(String file) { return getRoot().resolve(file); }
}
