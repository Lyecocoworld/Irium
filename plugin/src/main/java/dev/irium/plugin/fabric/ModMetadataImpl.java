package dev.irium.plugin.fabric;

/** ModMetadata minimal (id, name, version, environment=UNIVERSAL). */
final class ModMetadataImpl implements net.fabricmc.loader.api.metadata.ModMetadata {

    private final ModMetaParser.ModMeta meta;

    ModMetadataImpl(ModMetaParser.ModMeta meta) {
        this.meta = meta;
    }

    @Override public String getType() { return "compiled"; }

    @Override public String getId() { return meta.id; }

    @Override public String getName() { return meta.name; }

    @Override public net.fabricmc.loader.api.Version getVersion() {
        return new IriumVersion(meta.version);
    }

    private record IriumVersion(String v) implements net.fabricmc.loader.api.Version {
        @Override public String getFriendlyString() { return v; }
        @Override public int compareTo(net.fabricmc.loader.api.Version o) {
            return v.compareTo(o.getFriendlyString());
        }
    }
}
