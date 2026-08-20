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

    /* ---------------- chargement ---------------- */

    public static synchronized void install(byte[] modJarBytes) {
        try {
            // FabricLoader.INSTANCE côté client (avant tout code du mod)
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
                IriumAgent.log("[fabric-mod] mod '" + meta.id + "' déjà chargé -> ignoré");
                return;
            }

            ModClassLoader loader = new ModClassLoader(entries);
            Mod mod = new Mod(meta.id, meta, loader);
            MODS.put(meta.id, mod);

            // classes du mod enregistrées pour le dispatch
            for (String n : entries.keySet()) {
                if (n.endsWith(".class")) BY_CLASS.put(n.replace('/', '.').replace(".class", ""), mod);
            }

            // runtime Mixin prêt à le voir (mixins.json + classes)
            MixinGateway.registerMod(loader);

            // configs mixin + retransform à chaud des cibles extraites du bytecode
            if (meta.mixins != null && !meta.mixins.isEmpty()) {
                for (String cfg : meta.mixins) MixinGateway.addConfig(cfg);
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
                    IriumAgent.log("[fabric-mod] " + targets.size() + " cibles mixin retransformées: " + targets);
                }
            }

            IriumAgent.log("[fabric-mod] '" + meta.id + "' v" + meta.version + " installé ("
                    + entries.size() + " entrées, " + countClasses(entries) + " classes)");

            // entrypoints : d'abord main, puis client
            runEntrypoint(mod, "main", net.fabricmc.api.ModInitializer.class, mi -> mi.onInitialize());
            runEntrypoint(mod, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());

            // le mod arrive APRÈS le PLAY : re-fire JOIN pour ses receivers
            if (dev.irium.agent.IriumTap.currentChannel() != null) {
                dev.irium.agent.IriumTap.fireJoinLate();
            }

        } catch (Throwable t) {
            IriumAgent.log("[fabric-mod] échec installation: " + t);
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
}
