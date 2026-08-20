package dev.irium.agent.module;

import com.google.gson.Gson;
import dev.irium.agent.IriumAgent;
import dev.irium.agent.mixin.MixinGateway;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * M7-B : hôte de mods Fabric streamés côté client.
 *
 * ModJAR (0x04 sur irium:module) : jar complet du mod (SVC = 5,6 Mo).
 * - parse fabric.mod.json
 * - classes définies dans un ModuleClassLoader (parent = app loader : MC visible)
 * - entrypoints "main" (ModInitializer) puis "client" (ClientModInitializer)
 * - mixins.json enregistrés dans le MixinGateway
 * - sandbox : tout meurt à la déconnexion
 */
public final class FabricModHost {

    private static final Gson GSON = new Gson();

    /** mods chargés : modId -> mod */
    private static final Map<String, Mod> MODS = new ConcurrentHashMap<>();
    /** classes -> mod (dispatch des entrypoints) */
    private static final Map<String, Mod> BY_CLASS = new ConcurrentHashMap<>();

    private static final class Mod {
        final String id;
        final ModJarMeta meta;
        final ModClassLoader loader;
        Mod(String id, ModJarMeta meta, ModClassLoader loader) { this.id = id; this.meta = meta; this.loader = loader; }
    }

    /** fabric.mod.json (surface utile). */
    public static final class ModJarMeta {
        public String id;
        public String version;
        public Map<String, List<String>> entrypoints;
        public List<String> mixins;
        public Map<String, Object> depends;
        public Map<String, Object> custom;
    }

    /* ---------------- M7-B6 : cache par serveur + armement au boot ---------------- */

    /** Mods armes pour CE boot : modId -> sha256hex (rempli au premain). */
    static final Map<String, String> ARMED = new ConcurrentHashMap<>();
    /** Vrai si ce boot a ete arme par Irium (agent arg = boot:host:port). */
    static volatile boolean bootedByIrium;
    /** host:port arme pour ce boot (si bootedByIrium). */
    static volatile String armedServer;

    static String safeName(String host) { return host.replaceAll("[^A-Za-z0-9._-]", "_"); }

    static java.nio.file.Path serverDir(String host) throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(
                System.getProperty("user.home"), ".irium", "servers", safeName(host));
        java.nio.file.Files.createDirectories(dir);
        return dir;
    }

    /** Jar en cache pour ce serveur (ou null). */
    public static java.nio.file.Path cachedJar(String host, String modId) {
        try {
            java.nio.file.Path f = serverDir(host).resolve(modId + ".jar");
            return java.nio.file.Files.exists(f) ? f : null;
        } catch (Throwable t) { return null; }
    }

    /** Ecrit un jar dans le cache du serveur. */
    static void cacheJar(String host, String modId, byte[] jarBytes) {
        try {
            java.nio.file.Path f = serverDir(host).resolve(modId + ".jar");
            java.nio.file.Files.write(f, jarBytes);
        } catch (Throwable t) {
            IriumAgent.log("[fabric-mod] echec cache jar " + modId + ": " + t);
        }
    }

    /** sha256 hex (null si impossible). */
    static String sha256Hex(byte[] b) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Throwable t) { return null; }
    }

    /**
     * PREMAIN : arme les mods du cache du serveur AVANT le boot MC. Configs mixin
     * enregistrees -> les classes MC se definissent AVEC les mixins (legal :
     * definition, pas retransform). Entrypoints NON lances ici.
     * @param arg forme "boot:host:port"
     */
    public static void armForBoot(String arg) {
        try {
            String host = arg.substring(arg.indexOf(':') + 1);
            armedServer = host;
            bootedByIrium = true;
            java.nio.file.Path dir = serverDir(host);
            try (var stream = java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path jar : (Iterable<java.nio.file.Path>) stream::iterator) {
                    if (!jar.toString().endsWith(".jar")) continue;
                    String key = jar.getFileName().toString().replaceAll("\\.jar$", "");
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(jar);
                        ModJarMeta meta = metaOfJar(bytes);
                        if (meta == null || meta.id == null) continue;
                        installInternal(bytes, true);
                        // clé = nom de fichier du cache (MÊME clé que MODSET serveur et
                        // que manifestId des trames BEGIN — jamais meta.id)
                        ARMED.put(key, sha256Hex(bytes));
                        IriumAgent.log("[boot] mod armé: " + meta.id + " (clé " + key + ")");
                    } catch (Throwable t) {
                        IriumAgent.log("[boot] échec armement " + jar.getFileName() + ": " + t);
                    }
                }
            }
            IriumAgent.log("[boot] armement " + host + ": " + ARMED.size() + " mod(s)");
        } catch (Throwable t) {
            IriumAgent.log("[boot] armement impossible: " + t);
        }
    }

    /** Parse fabric.mod.json depuis un jar (null si absent/invalide). */
    static ModJarMeta metaOfJar(byte[] jarBytes) {
        try {
            Map<String, byte[]> entries = unzip(jarBytes);
            byte[] fmj = entries.get("fabric.mod.json");
            if (fmj == null) return null;
            return GSON.fromJson(new String(fmj, StandardCharsets.UTF_8), ModJarMeta.class);
        } catch (Throwable t) { return null; }
    }

    /**
     * JOIN : le set du serveur est-il deja arme dans CE boot ?
     * @param modset modId -> sha256hex
     */
    public static boolean isArmedFor(Map<String, String> modset) {
        if (modset.isEmpty()) return true;
        if (!bootedByIrium) return false;
        for (Map.Entry<String, String> e : modset.entrySet()) {
            String armed = ARMED.get(e.getKey());
            if (armed == null || !armed.equals(e.getValue())) return false;
        }
        return true;
    }


    /* ---------------- ponts de test (harnais) ---------------- */

    public static java.nio.file.Path serverDirPub(String host) throws Exception { return serverDir(host); }
    public static java.util.Map<String, String> ARMED_PUB() { return ARMED; }
    public static String sha256HexPub(byte[] b) { return sha256Hex(b); }

    /* ---------------- chargement ---------------- */

    public static synchronized void install(byte[] modJarBytes) {
        try {
            installInternal(modJarBytes, false);
        } catch (Throwable t) {
            IriumAgent.log("[fabric-mod] echec installation: " + t);
        }
    }

    private static void installInternal(byte[] modJarBytes, boolean early) throws Exception {
        // FabricLoader.INSTANCE cote client (avant tout code du mod)
        if (net.fabricmc.loader.impl.FabricLoaderImpl.INSTANCE == null) {
            net.fabricmc.loader.impl.FabricLoaderImpl.INSTANCE =
                    new net.fabricmc.loader.impl.FabricLoaderImpl(
                            new dev.irium.agent.module.FabricLoaderClient());
        }
        Map<String, byte[]> entries = unzip(modJarBytes);
        byte[] fmj = entries.get("fabric.mod.json");
        if (fmj == null) { IriumAgent.log("[fabric-mod] pas de fabric.mod.json -> refus"); return; }
        ModJarMeta meta = GSON.fromJson(new String(fmj, StandardCharsets.UTF_8), ModJarMeta.class);
        if (meta.id == null || meta.id.isBlank()) { IriumAgent.log("[fabric-mod] id absent -> refus"); return; }

        if (MODS.containsKey(meta.id)) {
            // M7-B6 : déjà ARMÉ au boot -> ce join active les entrypoints (le
            // mixins/configs sont déjà en place depuis le premain)
            Mod existing = MODS.get(meta.id);
            IriumAgent.log("[fabric-mod] mod '" + meta.id + "' déjà armé -> activation entrypoints");
            runEntrypoint(existing, "main", net.fabricmc.api.ModInitializer.class, mi -> mi.onInitialize());
            runEntrypoint(existing, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());
            if (dev.irium.agent.IriumTap.currentChannel() != null) {
                dev.irium.agent.IriumTap.fireJoinLate();
            }
            return;
        }

        ModClassLoader loader = new ModClassLoader(entries);
        // Racine M7-B4-5 : interfaces du mod injectees dans des classes MC -> le jar
        // doit etre visible du loader APP (identite de classe unique, cast possible)
        java.nio.file.Path modJar = materializeJar(meta.id, entries);
        MixinGateway.appendModToSystemClassPath(modJar);
        Mod mod = new Mod(meta.id, meta, loader);
        MODS.put(meta.id, mod);

        // classes du mod enregistrees pour le dispatch
        for (String n : entries.keySet()) {
            if (n.endsWith(".class")) BY_CLASS.put(n.replace('/', '.').replace(".class", ""), mod);
        }

        // runtime Mixin pret a le voir (mixins.json + classes)
        MixinGateway.registerMod(loader);

        // configs mixin ; en mode early (premain) on NE retransforme RIEN : les
        // classes MC pas encore chargees seront transformees a leur definition
        if (meta.mixins != null && !meta.mixins.isEmpty()) {
            for (String cfg : meta.mixins) MixinGateway.addConfig(cfg);
            if (!early) {
                java.util.LinkedHashSet<String> targets = new java.util.LinkedHashSet<>();
                for (String cls : entries.keySet()) {
                    if (!cls.endsWith(".class")) continue;
                    byte[] b = entries.get(cls);
                    for (String t : dev.irium.agent.mixin.MixinTargetScanner.scan(b)) {
                        targets.add(t);
                    }
                }
                if (!targets.isEmpty()) {
                    MixinGateway.retransform(targets.toArray(new String[0]));
                }
            }
        }

        if (early) {
            IriumAgent.log("[fabric-mod] '" + meta.id + "' arme au boot (configs mixin only)");
            return; // entrypoints au join, pas avant le boot MC
        }

        IriumAgent.log("[fabric-mod] '" + meta.id + "' v" + meta.version + " installe ("
                + entries.size() + " entrees, " + countClasses(entries) + " classes)");

        // entrypoints : d'abord main, puis client
        runEntrypoint(mod, "main", net.fabricmc.api.ModInitializer.class, mi -> mi.onInitialize());
        runEntrypoint(mod, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());

        // le mod arrive APRES le PLAY : re-fire JOIN pour ses receivers
        if (dev.irium.agent.IriumTap.currentChannel() != null) {
            dev.irium.agent.IriumTap.fireJoinLate();
        }
    }

    private interface Init<T> { void run(T t); }

    private static <T> void runEntrypoint(Mod mod, String key, Class<T> type, Init<T> init) {
        List<String> names = mod.meta.entrypoints == null ? null : mod.meta.entrypoints.get(key);
        if (names == null || names.isEmpty()) return;
        for (String name : names) {
            try {
                Class<?> c = mod.loader.loadClass(name);
                Object o = c.getDeclaredConstructor().newInstance();
                if (!type.isInstance(o)) {
                    IriumAgent.log("[fabric-mod] entrypoint " + key + " " + name + " n'implémente pas " + type.getSimpleName());
                    continue;
                }
                IriumAgent.log("[fabric-mod] entrypoint " + key + ": " + name);
                @SuppressWarnings("unchecked")
                T t = (T) o;
                init.run(t);
            } catch (Throwable t) {
                StringBuilder sb = new StringBuilder("[fabric-mod] entrypoint " + key + " " + name + " échec: " + t);
                Throwable c = t.getCause();
                int depth = 0;
                while (c != null && depth++ < 8) {
                    sb.append(" <- cause: ").append(c);
                    c = c.getCause();
                }
                IriumAgent.log(sb.toString());
                StackTraceElement[] st = t.getStackTrace();
                for (int i = 0; i < Math.min(6, st.length); i++) IriumAgent.log("    at " + st[i]);
            }
        }
    }

    /* ---------------- FabricLoader surface ---------------- */

    public static <T> List<T> entrypoints(String key, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Mod m : MODS.values()) {
            List<String> names = m.meta.entrypoints == null ? null : m.meta.entrypoints.get(key);
            if (names == null) continue;
            for (String n : names) {
                try {
                    Class<?> c = m.loader.loadClass(n);
                    Object o = c.getDeclaredConstructor().newInstance();
                    if (type.isInstance(o)) { @SuppressWarnings("unchecked") T t = (T) o; out.add(t); }
                } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<T>> entrypointContainers(String key, Class<T> type) {
        List out = new ArrayList<>();
        for (T t : entrypoints(key, type)) {
            out.add(new net.fabricmc.loader.api.entrypoint.EntrypointContainer<>() {
                @Override public T getEntrypoint() { return t; }
                @Override public String getDefinition() { return "irium"; }
            });
        }
        return out;
    }

    public static Optional<net.fabricmc.loader.api.ModContainer> container(String modId) {
        Mod m = MODS.get(modId);
        if (m == null) return Optional.empty();
        return Optional.of(new net.fabricmc.loader.api.ModContainer() {
            @Override public net.fabricmc.loader.api.metadata.ModMetadata getMetadata() { return metaOf(m); }
            @Override public List<Path> getRootPaths() { return List.of(Path.of(".")); }
            @Override public net.fabricmc.loader.api.metadata.ModOrigin getOrigin() { return ModOriginIRIUM.INSTANCE; }
        });
    }

    /** ModOrigin constant (Kind.OTHER, aucun chemin). */
    static final class ModOriginIRIUM implements net.fabricmc.loader.api.metadata.ModOrigin {
        static final ModOriginIRIUM INSTANCE = new ModOriginIRIUM();
        @Override public Kind getKind() { return Kind.UNKNOWN; }
        @Override public List<Path> getPaths() { return List.of(); }
        @Override public String getParentModId() { return ""; }
        @Override public String getParentSubLocation() { return ""; }
    }

    private static ModMetadata metaOf(Mod m) {
        return new ModMetadata() {
            @Override public String getId() { return m.id; }
            @Override public net.fabricmc.loader.api.Version getVersion() {
                final String v = m.meta.version == null ? "1.0.0" : m.meta.version;
                return new net.fabricmc.loader.api.Version() {
                    @Override public String getFriendlyString() { return v; }
                    @Override public int compareTo(net.fabricmc.loader.api.Version o) { return 0; }
                };
            }
            @Override public String getName() { return m.id; }
            @Override public String getType() { return "fabric"; }
        };
    }

    public static Collection<net.fabricmc.loader.api.ModContainer> allContainers() {
        List<net.fabricmc.loader.api.ModContainer> out = new ArrayList<>();
        for (String id : MODS.keySet()) container(id).ifPresent(out::add);
        return out;
    }

    public static boolean isLoaded(String modId) { return MODS.containsKey(modId); }

    /** Test/debug : classloader d'un mod chargé (null si absent). */
    public static ClassLoader loaderOf(String modId) {
        Mod m = MODS.get(modId);
        return m == null ? null : m.loader;
    }

    public static Path configDir() {
        try {
            Path p = Path.of("irium-config");
            Files.createDirectories(p);
            return p;
        } catch (Throwable t) { return Path.of("."); }
    }

    /* ---------------- payloads ---------------- */

    /** Émission custom_payload serverbound via le tap (0x16). */
    public static void sendPayload(CustomPacketPayload payload) {
        dev.irium.agent.ClientPayloadSender.send(payload);
    }

    /* ---------------- sandbox ---------------- */

    public static synchronized void uninstallAll() {
        int n = MODS.size();
        MODS.clear();
        BY_CLASS.clear();
        net.fabricmc.fabric.impl.client.networking.ClientNetworkingImpl.clear();
        dev.irium.agent.hud.FabricHudBridge.clearAll();
        IriumAgent.log("[fabric-mod] sandbox vidée (" + n + " mod(s))");
    }

    /* ---------------- utilitaires ---------------- */

    private static Map<String, byte[]> unzip(byte[] jar) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        List<byte[]> nested = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                byte[] b = zin.readAllBytes();
                out.put(e.getName(), b);
                // JiJ : jars imbriqués -> fusionnés (les modules fabric-* sont couverts
                // par la surface Irium, on ne fusionne que le reste, ex. voicechat-api)
                if (e.getName().startsWith("META-INF/jars/") && e.getName().endsWith(".jar")
                        && !e.getName().contains("fabric-")) {
                    nested.add(b);
                }
            }
        }
        for (byte[] n : nested) {
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(n))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String n2 = e.getName();
                    if (n2.equals("fabric.mod.json") || n2.startsWith("META-INF/services/")) continue;
                    out.putIfAbsent(n2, zin.readAllBytes());
                }
            }
        }
        return out;
    }

    private static int countClasses(Map<String, byte[]> entries) {
        int n = 0;
        for (String k : entries.keySet()) if (k.endsWith(".class")) n++;
        return n;
    }

    /** Écrit le jar du mod sur disque (cache par contenu) pour l'exposition au loader APP. */
    private static java.nio.file.Path materializeJar(String id, Map<String, byte[]> entries) throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "irium-mods");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path jar = dir.resolve(id + ".jar");
        if (java.nio.file.Files.exists(jar)) return jar; // cache hit (id+contenu stables par session)
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                java.nio.file.Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return jar;
    }
}
