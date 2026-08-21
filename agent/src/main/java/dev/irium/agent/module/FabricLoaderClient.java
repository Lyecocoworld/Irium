package dev.irium.agent.module;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * FabricLoader côté client Irium : CLIENT, mods = ceux streamés par le serveur.
 */
public final class FabricLoaderClient implements FabricLoader {

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return FabricModHost.entrypoints(key, type);
    }

    @Override
    public <T> List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return FabricModHost.entrypointContainers(key, type);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, java.util.function.Consumer<? super T> invoker) {
        for (T t : FabricModHost.entrypoints(key, type)) {
            try { invoker.accept(t); } catch (Throwable ignored) {}
        }
    }

    @Override public net.fabricmc.loader.api.ObjectShare getObjectShare() { return net.fabricmc.loader.api.ObjectShare.create(); }

    @Override public net.fabricmc.loader.api.MappingResolver getMappingResolver() {
        throw new UnsupportedOperationException("pas de remapping Irium (noms Mojang)");
    }

    @Override public Optional<ModContainer> getModContainer(String modId) {
        return FabricModHost.container(modId);
    }

    @Override public Collection<ModContainer> getAllMods() { return FabricModHost.allContainers(); }

    @Override public boolean isModLoaded(String modId) { return FabricModHost.isLoaded(modId); }

    @Override public boolean isDevelopmentEnvironment() { return false; }

    @Override public EnvType getEnvironmentType() { return EnvType.CLIENT; }

    @Override public String getRawGameVersion() { return "26.2"; }

    @Override public Object getGameInstance() {
        try { return net.minecraft.client.Minecraft.getInstance(); }
        catch (Throwable t) { return null; }
    }

    @Override public Path getGameDir() { return Path.of(".").toAbsolutePath().normalize(); }

    @Override public Path getConfigDir() { return FabricModHost.configDir(); }

    @Override public String[] getLaunchArguments(boolean launchTarget) { return new String[0]; }
}
