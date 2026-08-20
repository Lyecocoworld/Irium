package dev.irium.agent.module;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * M7-B9 : source de resource packs pour les mods streamés.
 *
 * Sur vraie Fabric, fabric-resource-loader déclare chaque mod comme pack
 * built-in (RepositorySource). Nous on EST le loader : cette source sert les
 * entrées assets/** des jars mod EN MÉMOIRE (ModClassLoader) comme pack.
 *
 * - Injectée dans le PackRepository au boot (mixin agent PackRepositoryMixin)
 *   -> les lang/textures sont présents dès le PREMIER reload (20:11:19 dans
 *   les logs réels), pas après le join (20:12) — root cause des placeholders
 *   key.voicechat.*.
 * - Un pack par mod, id "irium/<modId>", auto-activé (shouldAddAutomatically).
 * - pack.mcmeta fabriqué : format courant du client -> toujours compatible.
 */
public final class IriumPackSource implements RepositorySource {

    /** modId -> entrées du jar (référence stable vers la map du ModClassLoader). */
    private static final Map<String, Map<String, byte[]>> REGISTRY = new ConcurrentHashMap<>();

    /** PackSource Irium : auto-activation (comme pack.source.fabricmod). */
    static final PackSource IRIUM_SOURCE = PackSource.create(c -> c, true);

    public static final IriumPackSource INSTANCE = new IriumPackSource();

    private IriumPackSource() {}

    /** Enregistre les assets d'un mod installé/armé. Idempotent. */
    public static void register(String modId, Map<String, byte[]> entries) {
        REGISTRY.put(modId, entries);
    }

    /** Sandbox : tout oublier (déconnexion). */
    public static void clear() { REGISTRY.clear(); }

    static Map<String, Map<String, byte[]>> registryForTest() { return REGISTRY; }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        for (Map.Entry<String, Map<String, byte[]>> e : REGISTRY.entrySet()) {
            try {
                Pack p = IriumModPack.create(e.getKey(), e.getValue());
                if (p != null) onLoad.accept(p);
            } catch (Throwable t) {
                dev.irium.agent.SafeLog.offer("[pack] échec " + e.getKey() + ": " + t);
            }
        }
    }

    /* ---------------- fabrique + PackResources virtuel ---------------- */

    static final class IriumModPack {

        private IriumModPack() {}

        static Pack create(String modId, Map<String, byte[]> entries) {
            PackLocationInfo loc = new PackLocationInfo(
                    "irium/" + modId,
                    Component.literal(modId + " (Irium)"),
                    IRIUM_SOURCE,
                    Optional.empty());
            // readMetaAndCreate appelle getCurrentVersion() en interne (jette
            // "Game version not set" hors client réel) -> ctor direct avec
            // metadata faite main, format = statiques RESOURCE_PACK_FORMAT_*.
            PackFormat fmt = PackFormat.of(
                    SharedConstants.RESOURCE_PACK_FORMAT_MAJOR,
                    SharedConstants.RESOURCE_PACK_FORMAT_MINOR);
            Pack.Metadata meta = new Pack.Metadata(
                    Component.literal(modId + " assets (streamed by Irium)"),
                    PackCompatibility.COMPATIBLE,
                    FeatureFlagSet.of(),
                    List.of());
            // required=true : rebuildSelected() n'ajoute AUTO que les packs
            // isRequired() (même mécanisme que les packs mod Fabric). Sinon le
            // pack est "available" mais jamais sélectionné -> ResourceManager
            // "vanilla" seul -> placeholders lang + textures manquantes.
            PackSelectionConfig sel = new PackSelectionConfig(true, Pack.Position.TOP, false);
            Pack.ResourcesSupplier sup = new Supplier(modId, entries);
            return new Pack(loc, sup, meta, sel);
        }

        /** Pack$ResourcesSupplier : les deux ouvertures servent la même map. */
        static final class Supplier implements Pack.ResourcesSupplier {
            private final String modId;
            private final Map<String, byte[]> entries;
            Supplier(String modId, Map<String, byte[]> entries) { this.modId = modId; this.entries = entries; }
            @Override public PackResources openPrimary(PackLocationInfo loc) { return new Res(loc, modId, entries); }
            @Override public PackResources openFull(PackLocationInfo loc, Pack.Metadata meta) { return new Res(loc, modId, entries); }
        }
    }

    /** PackResources 100% mémoire : assets/&lt;ns&gt;/&lt;path&gt; depuis la map du jar. */
    static final class Res implements PackResources {
        private final PackLocationInfo loc;
        private final String modId;
        private final Map<String, byte[]> entries;

        Res(PackLocationInfo loc, String modId, Map<String, byte[]> entries) {
            this.loc = loc; this.modId = modId; this.entries = entries;
        }

        @Override public PackLocationInfo location() { return loc; }

        @Override
        public IoSupplier<InputStream> getRootResource(String... elements) {
            byte[] b = entries.get(String.join("/", elements));
            return b == null ? null : () -> new ByteArrayInputStream(b);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            if (type != PackType.CLIENT_RESOURCES) return null;
            byte[] b = entries.get("assets/" + id.getNamespace() + "/" + id.getPath());
            return b == null ? null : () -> new ByteArrayInputStream(b);
        }

        @Override
        public void listResources(PackType type, String namespace, String start,
                                  PackResources.ResourceOutput out) {
            if (type != PackType.CLIENT_RESOURCES) return;
            // normaliser start (les appelants passent "textures", jamais "textures/")
            String s = start.endsWith("/") ? start.substring(0, start.length() - 1) : start;
            String prefix = "assets/" + namespace + "/" + s;
            int plen = prefix.length();
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String k = e.getKey();
                // doit être STRICTEMENT sous le dossier (pas le dossier lui-même,
                // pas un fichier "texturesX") : le char après le prefix doit être '/'
                if (!k.startsWith(prefix)) continue;
                if (k.length() <= plen || k.charAt(plen) != '/') continue;
                String rel = k.substring(plen + 1);
                if (rel.isEmpty()) continue;
                // CONTRAT FileToIdConverter : l'id émis CONTIENT le préfixe du dossier
                // ("textures/gui/x.png", "lang/fr_fr.json") — fileToId re-stripe
                // prefix+"/" et l'extension. Sans lui : StringIndexOutOfBounds ->
                // "Caught error loading resourcepacks" -> tous les packs retirés.
                out.accept(Identifier.fromNamespaceAndPath(namespace, s + "/" + rel),
                        () -> new ByteArrayInputStream(e.getValue()));
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            Set<String> ns = new HashSet<>();
            if (type != PackType.CLIENT_RESOURCES) return ns;
            for (String k : entries.keySet()) {
                if (!k.startsWith("assets/")) continue;
                int a = "assets/".length();
                int b = k.indexOf('/', a);
                if (b > a) ns.add(k.substring(a, b));
            }
            return ns;
        }

        /** pack.mcmeta fabriqué : format = celui du client courant -> jamais "incompatible". */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMetadataSection(net.minecraft.server.packs.metadata.MetadataSectionType<T> type) {
            if (type == PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES)) {
                // statiques clinit (sans lookup version.json) : robuste harnais + client
                PackFormat fmt = PackFormat.of(
                        SharedConstants.RESOURCE_PACK_FORMAT_MAJOR,
                        SharedConstants.RESOURCE_PACK_FORMAT_MINOR);
                return (T) new PackMetadataSection(
                        Component.literal(modId + " assets (streamed by Irium)"),
                        new InclusiveRange<>(fmt, fmt));
            }
            return null;
        }

        @Override public void close() {}
        @Override public String toString() { return "IriumPack[" + modId + "]"; }
    }

    /** Pont harnais : liste des modIds enregistrés. */
    public static List<String> registeredMods() { return new ArrayList<>(REGISTRY.keySet()); }
}
