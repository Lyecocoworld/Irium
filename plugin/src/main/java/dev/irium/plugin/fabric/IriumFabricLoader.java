package dev.irium.plugin.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/** Chargé depuis le classloader du mod par ServerModHost. */
@SuppressWarnings("unused")
final class IriumFabricLoader implements net.fabricmc.loader.api.FabricLoader {

    private final ModMetaParser.ModMeta meta;
    private final java.nio.file.Path jar;
    private final ClassLoader modClassLoader;

    IriumFabricLoader(ModMetaParser.ModMeta meta, java.nio.file.Path jar, ClassLoader modClassLoader) {
        this.meta = meta;
        this.jar = jar;
        this.modClassLoader = modClassLoader;
    }

    @Override
    public <T> java.util.List<T> getEntrypoints(String key, Class<T> type) {
        java.util.List<T> out = new java.util.ArrayList<>();
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) out.add(c.getEntrypoint());
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> java.util.List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        String cls = "main".equals(key) ? meta.main : ("client".equals(key) ? meta.client : null);
        if (cls == null) return java.util.List.of();
        EntrypointContainer<T> container = () -> {
            try {
                return (T) Class.forName(cls, true, modClassLoader).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        return java.util.List.of(container);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, java.util.function.Consumer<? super T> invoker) {
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) {
            invoker.accept(c.getEntrypoint());
        }
    }

    @Override
    public java.util.Optional<net.fabricmc.loader.api.ModContainer> getModContainer(String modId) {
        if (!meta.id.equals(modId)) return java.util.Optional.empty();
        return java.util.Optional.of(new net.fabricmc.loader.api.ModContainer() {
            @Override public net.fabricmc.loader.api.metadata.ModMetadata getMetadata() {
                return new ModMetadataImpl(meta);
            }
            @Override public java.util.List<java.nio.file.Path> getRootPaths() {
                return java.util.List.of(jar);
            }
            @Override public net.fabricmc.loader.api.metadata.ModOrigin getOrigin() {
                return new net.fabricmc.loader.api.metadata.ModOrigin() {
                    @Override public Kind getKind() { return Kind.PATH; }
                    @Override public java.util.List<java.nio.file.Path> getPaths() { return java.util.List.of(jar); }
                };
            }
        });
    }

    @Override public boolean isModLoaded(String modId) { return meta.id.equals(modId); }

    @Override public boolean isDevelopmentEnvironment() { return false; }

    @Override public EnvType getEnvironmentType() { return EnvType.SERVER; }

    @Override public java.nio.file.Path getGameDir() {
        return java.nio.file.Path.of(".").toAbsolutePath().normalize();
    }

    @Override public java.nio.file.Path getConfigDir() {
        java.nio.file.Path p = java.nio.file.Path.of("config");
        try { java.nio.file.Files.createDirectories(p); } catch (Exception ignored) {}
        return p;
    }
}
