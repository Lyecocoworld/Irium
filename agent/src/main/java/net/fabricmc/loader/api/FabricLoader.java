package net.fabricmc.loader.api;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * Adaptateur Irium — copie bytecode-exacte de l'interface officielle
 * fabric-loader 0.19.3 (le mod référence InterfaceMethodref : cette API DOIT
 * être une interface). getInstance() lit FabricLoaderImpl.INSTANCE, comme
 * l'officiel.
 */
public interface FabricLoader {

    static FabricLoader getInstance() {
        FabricLoader loader = net.fabricmc.loader.impl.FabricLoaderImpl.INSTANCE;
        if (loader == null) {
            throw new RuntimeException("Accessed FabricLoader too early!");
        }
        return loader;
    }

    <T> List<T> getEntrypoints(String key, Class<T> type);

    <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);

    <T> void invokeEntrypoints(String key, Class<T> type, java.util.function.Consumer<? super T> invoker);

    default net.fabricmc.loader.api.ObjectShare getObjectShare() {
        throw new UnsupportedOperationException();
    }

    default MappingResolver getMappingResolver() {
        throw new UnsupportedOperationException("irium: mojang official mappings");
    }

    Optional<ModContainer> getModContainer(String modId);

    default Collection<ModContainer> getAllMods() {
        return java.util.List.of();
    }

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    EnvType getEnvironmentType();

    default String getRawGameVersion() { return "26.2"; }

    default Object getGameInstance() { return null; }

    Path getGameDir();

    default File getGameDirectory() { return getGameDir().toFile(); }

    Path getConfigDir();

    default File getConfigDirectory() { return getConfigDir().toFile(); }

    default String[] getLaunchArguments(boolean launchTarget) { return new String[0]; }
}
