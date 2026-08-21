package net.fabricmc.loader.api;

/**
 * Surface Irium : SemanticVersion de fabric-loader. Utilisé par les mods pour
 * le parsing de versions (ex. Mod Menu UpdateChecker). Parse minimal : garde
 * la chaîne brute, comparable par ordre naturel.
 */
public final class SemanticVersion implements Version {

    private final String raw;

    private SemanticVersion(String raw) { this.raw = raw; }

    /** FabricLoaderImpl.getSemanticVersion() etc. */
    public static SemanticVersion parse(String s) {
        return new SemanticVersion(s == null ? "" : s);
    }

    @Override public String getFriendlyString() { return raw; }

    @Override public String toString() { return raw; }

    @Override
    public int compareTo(Version o) {
        return raw.compareTo(String.valueOf(o));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SemanticVersion sv && sv.raw.equals(raw);
    }

    @Override
    public int hashCode() { return raw.hashCode(); }
}
