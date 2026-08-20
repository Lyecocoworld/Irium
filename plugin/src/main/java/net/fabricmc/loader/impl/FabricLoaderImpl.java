package net.fabricmc.loader.impl;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Adaptateur Irium — l'impl officielle vit dans net.fabricmc.loader.impl et
 * expose INSTANCE ; le getInstance() de l'API lit ce champ.
 */
public final class FabricLoaderImpl implements FabricLoader {

    /** Installé par l'adaptateur Irium avant tout appel au mod. */
    public static volatile FabricLoader INSTANCE;

    private final FabricLoader delegate;

    public FabricLoaderImpl(FabricLoader delegate) {
        this.delegate = delegate;
    }

    @Override public <T> java.util.List<T> getEntrypoints(String key, Class<T> type) {
        return delegate.getEntrypoints(key, type);
    }

    @Override public <T> java.util.List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return delegate.getEntrypointContainers(key, type);
    }

    @Override public <T> void invokeEntrypoints(String key, Class<T> type, java.util.function.Consumer<? super T> invoker) {
        delegate.invokeEntrypoints(key, type, invoker);
    }

    @Override public net.fabricmc.loader.api.ObjectShare getObjectShare() {
        return net.fabricmc.loader.api.ObjectShare.create();
    }

    @Override public net.fabricmc.loader.api.MappingResolver getMappingResolver() {
        return delegate.getMappingResolver();
    }

    @Override public java.util.Optional<net.fabricmc.loader.api.ModContainer> getModContainer(String modId) {
        return delegate.getModContainer(modId);
    }

    @Override public java.util.Collection<net.fabricmc.loader.api.ModContainer> getAllMods() {
        return delegate.getAllMods();
    }

    @Override public boolean isModLoaded(String modId) { return delegate.isModLoaded(modId); }

    @Override public boolean isDevelopmentEnvironment() { return delegate.isDevelopmentEnvironment(); }

    @Override public net.fabricmc.api.EnvType getEnvironmentType() { return delegate.getEnvironmentType(); }

    @Override public String getRawGameVersion() { return delegate.getRawGameVersion(); }

    @Override public Object getGameInstance() { return delegate.getGameInstance(); }

    @Override public java.nio.file.Path getGameDir() { return delegate.getGameDir(); }

    @Override public java.nio.file.Path getConfigDir() { return delegate.getConfigDir(); }

    @Override public String[] getLaunchArguments(boolean launchTarget) {
        return delegate.getLaunchArguments(launchTarget);
    }
}
