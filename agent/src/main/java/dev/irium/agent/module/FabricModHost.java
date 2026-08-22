package dev.irium.agent.module;

import com.google.gson.Gson;
import dev.irium.agent.IriumAgent;
import dev.irium.agent.mixin.MixinGateway;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.loader.api.metadata.CustomValue;
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

    /**
     * M7-X2 : parseur fmj tolérant — GSON strict jette sur des formes LÉGALES
     * de la spec fabric.mod.json (vérifié empiriquement, voir TestGson) :
     *  - entrypoints string shorthand {"client": "com.x.Init"}
     *  - entrypoints objets {"client": [{"adapter":"kotlin","value":"..."}]}
     *  - authors objets [{"name":"Bob","contact":{...}}]
     * Un throw GSON = MOD ENTIÈREMENT REFUSÉ (silencieux). On parse via
     * JsonTree -> ModJarMeta à la main, jamais de JsonSyntaxException.
     */
    private static final Gson GSON = new Gson();

    static ModJarMeta parseFmj(String json) {
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        ModJarMeta m = new ModJarMeta();
        m.id = jsonStr(o, "id");
        m.version = jsonStr(o, "version");
        m.name = jsonStr(o, "name");
        m.description = jsonStr(o, "description");
        m.icon = o.has("icon") && !o.get("icon").isJsonNull() ? GSON.fromJson(o.get("icon"), Object.class) : null;
        m.authors = o.has("authors") && !o.get("authors").isJsonNull() ? GSON.fromJson(o.get("authors"), Object.class) : null;
        m.license = o.has("license") && !o.get("license").isJsonNull() ? GSON.fromJson(o.get("license"), Object.class) : null;
        m.custom = o.has("custom") && o.get("custom").isJsonObject()
                ? GSON.fromJson(o.get("custom"), Map.class) : null;
        m.contact = o.has("contact") && o.get("contact").isJsonObject()
                ? GSON.fromJson(o.get("contact"), Map.class) : null;
        m.entrypoints = new java.util.LinkedHashMap<>();
        if (o.has("entrypoints") && o.get("entrypoints").isJsonObject()) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("entrypoints").entrySet()) {
                List<String> names = new ArrayList<>();
                if (e.getValue().isJsonArray()) {
                    for (com.google.gson.JsonElement el : e.getValue().getAsJsonArray()) {
                        collectEntrypointValue(el, names);
                    }
                } else if (e.getValue().isJsonObject()) {
                    collectEntrypointValue(e.getValue(), names); // {"adapter":..., "value":...}
                } else if (e.getValue().isJsonPrimitive()) {
                    names.add(e.getValue().getAsString()); // shorthand string
                }
                m.entrypoints.put(e.getKey(), names);
            }
        }
        m.mixins = new ArrayList<>();
        if (o.has("accessWidener") && o.get("accessWidener").isJsonPrimitive()) {
            m.accessWidener = o.get("accessWidener").getAsString();
        }
        if (o.has("mixins")) {
            com.google.gson.JsonElement mx = o.get("mixins");
            if (mx.isJsonArray()) {
                for (com.google.gson.JsonElement el : mx.getAsJsonArray()) {
                    if (el.isJsonPrimitive()) m.mixins.add(el.getAsString());
                }
            } else if (mx.isJsonObject()) { // extension Quilt: {"client":[...], "common":[...]}
                for (com.google.gson.JsonElement arr : mx.getAsJsonObject().asMap().values()) {
                    if (arr.isJsonArray()) {
                        for (com.google.gson.JsonElement el : arr.getAsJsonArray()) {
                            if (el.isJsonPrimitive()) m.mixins.add(el.getAsString());
                        }
                    }
                }
            }
        }
        return m;
    }

    private static String jsonStr(com.google.gson.JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    /** String simple OU objet {adapter, value} (kotlin/quilt) -> extraire "value". */
    private static void collectEntrypointValue(com.google.gson.JsonElement el, List<String> out) {
        if (el.isJsonPrimitive()) {
            out.add(el.getAsString());
        } else if (el.isJsonObject()) {
            com.google.gson.JsonObject ob = el.getAsJsonObject();
            if (ob.has("value") && ob.get("value").isJsonPrimitive()) out.add(ob.get("value").getAsString());
            // adapter différent (kotlin, quartet...) : la valeur reste un FQCN chargeable
        }
    }

    /** mods chargés : modId -> mod */
    private static final Map<String, Mod> MODS = new ConcurrentHashMap<>();
    /** classes -> mod (dispatch des entrypoints) */
    private static final Map<String, Mod> BY_CLASS = new ConcurrentHashMap<>();
    /**
     * M7-X3 : instances d'entrypoints CONSTRUITES dans cette JVM (FQCN -> instance
     * forte). Un mod Fabric est un singleton de facto (HudMod.INSTANCE,
     * XaeroLib.INSTANCE…) : re-APPELER newInstance() écrase le champ statique avec
     * un objet à moitié construit (le super-ctor publie this PUIS jette
     * IllegalStateException sur détection de doublon) -> NPE à chaque tick
     * (crash xaerominimap 18:24 : xaeroHudFabric jamais assigné sur l'instance B).
     * uninstallAll() vide la sandbox mais les classes restent chargées et leurs
     * statics vivants : au re-join on RE-RUN l'init (onInitializeClient) sur
     * l'instance conservée — jamais le constructeur. Les mods avalent leur
     * propre double-init (catch Throwable -> firstStageError, cf. bytecode
     * XaeroMinimapFabric.onInitializeClient).
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> ENTRYPOINT_INSTANCES = new java.util.concurrent.ConcurrentHashMap<>();

    /** M7-X17 : classes d'entrypoints DÉJÀ EXÉCUTÉES dans cette JVM (FQCN). */
    private static final java.util.Set<String> ENTRYPOINT_RAN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final class Mod {
        final String id;
        final ModJarMeta meta;
        final ModClassLoader loader;
        /** jar matérialisé sur disque (classpath APP) — M7-B9 pour getRootPaths. */
        volatile java.nio.file.Path jarPath;
        /** M7-B11 : sous-mods JiJ (ex. xaerolib) — entrypoints lancés avant le nôtre. */
        volatile List<Mod> jij;
        /** M7-B12 : vrai si ce Mod est un sous-mod JiJ (activé via son parent, jamais en boucle directe). */
        boolean isJij;
        /** M7-B11 : entrées du jar en mémoire (icônes, assets — getPath). */
        volatile Map<String, byte[]> entries;
        Mod(String id, ModJarMeta meta, ModClassLoader loader) { this.id = id; meta.getClass(); this.meta = meta; this.loader = loader; }
    }

    /** fabric.mod.json (surface utile). */
    public static final class ModJarMeta {
        public String id;
        public String version;
        public Map<String, List<String>> entrypoints;
        public List<String> mixins;
        /** M7-X3 : chemin du fichier .accesswidener (null si absent). */
        public String accessWidener;
        public Map<String, Object> depends;
        public Map<String, Object> custom;
        /** icon du fabric.mod.json : String ("icon.png") ou Map{"16":"a.png","32":"b.png"}. */
        public Object icon;
        /** M7-B11d : surface descriptive lue par Mod Menu (liste + description). */
        public String name;
        public String description;
        /** String ("Author") ou liste ["A","B"]. */
        public Object authors;
        public Object license;
        /** {"homepage":"...","sources":"...","issues":"..."} */
        public Map<String, Object> contact;
    }

    /* ---------------- M7-B6 : cache par serveur + armement au boot ---------------- */

    /** Mods armes pour CE boot : modId -> sha256hex (rempli au premain). */
    static final Map<String, String> ARMED = new ConcurrentHashMap<>();
    /** Vrai si ce boot a ete arme par Irium (arg boot: OU attach avec cache). */
    static volatile boolean bootedByIrium;

    /** M7-X19 : ce boot a-t-il été armé par nous (arg boot:)? Visible de VanillaExit. */
    public static boolean isBootedByIrium() { return bootedByIrium; }
    /** M7-X21 : host:port pour lequel CE boot est armé (Gateway full mode). */
    public static String armedServer() { return armedServer; }
    /** M7-B12 : activation précoce déjà faite (fin ctor Minecraft). */
    static volatile boolean earlyActivated;
    /** host:port arme pour ce boot (si bootedByIrium). */
    static volatile String armedServer;
    /** Dernier MODSET reçu par serveur : host -> (clé jar -> sha256hex). */
    static final Map<String, Map<String, String>> LAST_MODSET = new ConcurrentHashMap<>();
    /** Jars effectivement en cache : host -> (clé jar -> sha256hex). */
    static final Map<String, Map<String, String>> CACHED = new ConcurrentHashMap<>();

    /** M7-B7 : le serveur a annoncé son set -> mémoriser (pour cacheCompleteFor). */
    public static void rememberModset(String host, Map<String, String> modset) {
        if (host != null && !modset.isEmpty()) LAST_MODSET.put(host, new ConcurrentHashMap<>(modset));
    }

    /** M7-B7 : tous les jars annoncés par le MODSET sont-ils en cache (sha ok) ? */
    public static boolean cacheCompleteFor(String host) {
        Map<String, String> want = LAST_MODSET.get(host);
        if (want == null || want.isEmpty()) return false;
        Map<String, String> have = CACHED.get(host);
        if (have == null) return false;
        for (Map.Entry<String, String> e : want.entrySet()) {
            String sha = have.get(e.getKey());
            if (sha == null || !sha.equals(e.getValue())) return false;
        }
        return true;
    }

    /**
     * M7-B7 ATTACH À CHAUD : armer les caches de TOUS les serveurs connus.
     * Le watcher attache AVANT que MC ne définisse ses classes -> les configs
     * mixin s'appliquent À LA DÉFINITION (légal JVMTI), zéro restart. Si la
     * course est perdue (classes déjà définies), le MODSET déclenchera la
     * relance auto en fallback.
     */
    public static void armFromCache() {
        // M7-B12 : verrou = même moniteur que install() — le ctor Minecraft peut
        // terminer PENDANT l'armement (thread render vs thread attach) et
        // onMinecraftReady() doit voir un état cohérent (tout ou rien).
        synchronized (FabricModHost.class) {
        int armed = 0;
        try {
            java.nio.file.Path root = java.nio.file.Path.of(
                    System.getProperty("user.home"), ".irium", "servers");
            if (!java.nio.file.Files.exists(root)) return;
            // M7-B12 : verdict course AVANT la boucle (les classes MC se chargent
            // pendant l'armement — mesurer à l'entrée, pas à la fin). Critère : une
            // CIBLE DE MIXIN est-elle déjà chargée ? (pas "une classe MC quelconque"
            // — net.minecraft.client.User/Main se chargent avant le ctor Minecraft,
            // faussant le verdict). Si une cible est chargée, ses mixins ne peuvent
            // plus s'appliquer -> relance MODSET nécessaire.
            java.util.Set<String> mixinTargets = collectMixinTargetsOfCachedJars(root);
            boolean early = mixinTargets.isEmpty()
                    || !dev.irium.agent.mixin.MixinGateway.anyOfClassLoaded(mixinTargets);
            try (var stream = java.nio.file.Files.list(root)) {
                for (java.nio.file.Path dir : (Iterable<java.nio.file.Path>) stream::iterator) {
                    if (!java.nio.file.Files.isDirectory(dir)) continue;
                    try (var jars = java.nio.file.Files.list(dir)) {
                        for (java.nio.file.Path jar : (Iterable<java.nio.file.Path>) jars::iterator) {
                            if (!jar.toString().endsWith(".jar")) continue;
                            String key = jar.getFileName().toString().replaceAll("\\.jar$", "");
                            try {
                                byte[] bytes = java.nio.file.Files.readAllBytes(jar);
                                ModJarMeta meta = metaOfJar(bytes);
                                if (meta == null || meta.id == null) continue;
                                installInternalLocked(bytes, true);
                                ARMED.put(key, sha256Hex(bytes));
                                armed++;
                            } catch (Throwable t) {
                                IriumAgent.log("[attach-arm] échec " + jar.getFileName() + ": " + t);
                            }
                        }
                    }
                }
            }
            if (armed > 0) {
                // M7-B11 : ne marquer "armé pour ce boot" QUE si l'attach a gagné
                // la course (aucune classe MC définie). Sinon les mixins des mods ne
                // peuvent PAS s'appliquer aux classes déjà chargées (Minecraft,
                // Keyboard, PackRepository...) et le MODSET doit déclencher la
                // relance premain — sinon SVC/Xaero tournent sans leurs mixins
                // (CCE IPackRepository, minimap muette...).
                bootedByIrium = early;
                IriumAgent.log("[attach-arm] " + armed + " mod(s) armé(s) depuis le cache "
                        + (bootedByIrium
                            ? "(attach précoce, transform à la définition)"
                            : "(attach TARDIF — classes MC déjà chargées, relance MODSET requise)"));
            }
        } catch (Throwable t) {
            IriumAgent.log("[attach-arm] impossible: " + t);
        }
        }
    }

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

    /**
     * M7-B12 : collecte les cibles @Mixin de TOUS les jars du cache (sous-mods
     * JiJ inclus). Sert au verdict de course attach : si une cible est déjà
     * chargée au moment de l'attach, les mixins ne s'appliqueront pas -> relance.
     */
    private static java.util.Set<String> collectMixinTargetsOfCachedJars(java.nio.file.Path root) {
        java.util.Set<String> targets = new java.util.HashSet<>();
        try {
            try (var servers = java.nio.file.Files.list(root)) {
                for (java.nio.file.Path dir : (Iterable<java.nio.file.Path>) servers::iterator) {
                    if (!java.nio.file.Files.isDirectory(dir)) continue;
                    try (var jars = java.nio.file.Files.list(dir)) {
                        for (java.nio.file.Path jar : (Iterable<java.nio.file.Path>) jars::iterator) {
                            if (!jar.toString().endsWith(".jar")) continue;
                            try {
                                for (Map.Entry<String, byte[]> e : unzip(java.nio.file.Files.readAllBytes(jar)).entrySet()) {
                                    if (!e.getKey().endsWith(".class")) continue;
                                    targets.addAll(dev.irium.agent.mixin.MixinTargetScanner.scan(e.getValue()));
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return targets;
    }

    /** Parse fabric.mod.json depuis un jar (null si absent/invalide). */
    static ModJarMeta metaOfJar(byte[] jarBytes) {
        try {
            Map<String, byte[]> entries = unzip(jarBytes);
            byte[] fmj = entries.get("fabric.mod.json");
            if (fmj == null) return null;
            return parseFmj(new String(fmj, StandardCharsets.UTF_8));
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

    /** M7-B12 : corps d'installation sous moniteur (appelé avec le verrou déjà tenu). */
    private static void installInternalLocked(byte[] modJarBytes, boolean early) throws Exception {
        installInternal(modJarBytes, early);
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
        ModJarMeta meta = parseFmj(new String(fmj, StandardCharsets.UTF_8));
        if (meta.id == null || meta.id.isBlank()) { IriumAgent.log("[fabric-mod] id absent -> refus"); return; }

        // M7-X23 : ré-install après uninstallAll() (rejoin). Les classes,
        // mixins, AW et entrypoints vivent TOUJOURS dans cette JVM (GHOSTS +
        // ENTRYPOINT_INSTANCES + ENTRYPOINT_RAN). Seul le resource pack a été
        // vidé par IriumPackSource.clear() -> le re-régénérer depuis le jar
        // reçu, SANS classloader/mixins/AW/reload ni ré-activation. Sans ça :
        // rejoin = install complet + Reloading ResourceManager à chaque fois
        // (écran de chargement systématique + crash Xaero Pipeline is not
        // valid, reload en plein render du monde).
        if (!early && !MODS.containsKey(meta.id) && ACTIVATED_MODS.contains(meta.id)) {
            IriumAgent.log("[fabric-mod] mod '" + meta.id + "' ghost (déjà activé cette JVM) -> pack seul re-régistré, rejoin simple");
            IriumPackSource.register(meta.id, entries);
            onRenderThread(() -> {
                if (dev.irium.agent.IriumTap.currentChannel() != null) {
                    dev.irium.agent.IriumTap.fireJoinLate();
                }
            });
            return;
        }

        if (MODS.containsKey(meta.id)) {
            if (early) {
                // M7-B7 : armement multi-serveurs à l'attach — même mod déjà armé
                // depuis un autre cache -> rien à faire (pas d'entrypoints au boot)
                IriumAgent.log("[fabric-mod] mod '" + meta.id + "' déjà armé (autre cache) -> ignoré");
                return;
            }
            // M7-B6 : déjà ARMÉ au boot -> ce join active les entrypoints (le
            // mixins/configs sont déjà en place depuis le premain)
            Mod existing = MODS.get(meta.id);
            // M7-X17 : déjà activé dans cette JVM -> rejoin simple. Les entrypoints
            // ne se ré-exécutent JAMAIS (sodium: duplicate mod id, Xaero: singleton).
            if (existing == null && ACTIVATED_MODS.contains(meta.id)) {
                IriumAgent.log("[fabric-mod] mod '" + meta.id + "' déjà activé dans cette JVM -> rejoin simple");
                onRenderThread(() -> {
                    if (dev.irium.agent.IriumTap.currentChannel() != null) {
                        dev.irium.agent.IriumTap.fireJoinLate();
                    }
                });
                return;
            }
            IriumAgent.log("[fabric-mod] mod '" + meta.id + "' déjà armé -> activation entrypoints");
            onRenderThread(() -> {
                // M7-B12 : si l'activation précoce (fin ctor Minecraft) a déjà eu
                // lieu, les entrypoints ont tourné -> ne PAS double-exécuter
                // (Xaero/SVC recréeraient leurs instances -> état cassé).
                if (earlyActivated) {
                    IriumAgent.log("[fabric-mod] précoce déjà faite -> join simple (pas de ré-activation)");
                    if (dev.irium.agent.IriumTap.currentChannel() != null) {
                        dev.irium.agent.IriumTap.fireJoinLate();
                    }
                    return;
                }
                // JiJ d'abord (dépendances), puis le parent
                for (Mod sub : jijModsOf(existing)) {
                    runEntrypoint(sub, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());
                    ACTIVATED_MODS.add(sub.id); // M7-X23
                }
                runEntrypoint(existing, "main", net.fabricmc.api.ModInitializer.class, mi -> mi.onInitialize());
                runEntrypoint(existing, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());
                ACTIVATED_MODS.add(existing.id); // M7-X23
                if (dev.irium.agent.IriumTap.currentChannel() != null) {
                    dev.irium.agent.IriumTap.fireJoinLate();
                }
            });
            return;
        }

        ModClassLoader loader = new ModClassLoader(entries);
        // M7-X13b : enregistrer TOUTES les classes du jar (mod + JiJ fusionnés)
        // dans l'allowlist du pipeline JVMTI — les @Accessor des mods doivent
        // traverser le MixinTransformer même chargés par le loader APP.
        java.util.List<String> modClassNames = new ArrayList<>();
        for (String k : entries.keySet()) {
            // nom INTERNE sans ".class" — c'est ce que reçoit le transformer JVMTI.
            // Les JiJ sont fusionnés à plat (net/...), le préfixe irium-jij/ n'est
            // que pour les ressources dupliquées.
            if (k.endsWith(".class") && !k.startsWith("irium-jij/"))
                modClassNames.add(k.substring(0, k.length() - ".class".length()));
        }
        MixinGateway.registerModClasses(modClassNames);
        // Racine M7-B4-5 : interfaces du mod injectees dans des classes MC -> le jar
        // doit etre visible du loader APP (identite de classe unique, cast possible)
        java.nio.file.Path modJar = materializeJar(meta.id, entries);
        MixinGateway.appendModToSystemClassPath(modJar);
        Mod mod = new Mod(meta.id, meta, loader);
        mod.entries = entries;
        MODS.put(meta.id, mod);

        // M7-X3 : accessWidener — règles appliquées AVANT mixin (ordre
        // fabric-loader). Early (premain) : les classes pas encore chargées
        // seront widennées à leur définition via le pipeline transformer.
        // Late (join) : retransform des classes ciblées déjà chargées.
        java.util.Set<String> awRetransform = applyAccessWidener(meta, entries, early);

        // M7-B11 : sous-mods JiJ (ex. xaerolib) — VRAIS mods avec entrypoints et
        // mixins propres. Sur vraie Fabric le loader les découvre et les initialise
        // AVANT leur parent (dépendance). Sans ça : XaeroLib.INSTANCE null ->
        // NPE dans CustomRenderTypes.applyFixedOrder (Stage 2/3).
        List<Mod> jijMods = new ArrayList<>();
        for (String k : entries.keySet()) {
            if (!k.startsWith("irium-jij/") || !k.endsWith("/fabric.mod.json")) continue;
            try {
                ModJarMeta jm = parseFmj(
                        new String(entries.get(k), StandardCharsets.UTF_8));
                if (jm == null || jm.id == null || jm.id.isBlank() || MODS.containsKey(jm.id)) continue;
                // le JiJ partage le classloader du parent (classes déjà fusionnées)
                Mod sub = new Mod(jm.id, jm, loader);
                sub.isJij = true;
                // M7-B11c : entries propres au JiJ (icône, assets) — clés préfixées
                // "irium-jij/<jar>/". Utile pour Mod Menu (icônes voicechat_api/xaerolib).
                String pfx = k.substring(0, k.length() - "fabric.mod.json".length());
                Map<String, byte[]> subEntries = new HashMap<>();
                for (Map.Entry<String, byte[]> ee : entries.entrySet()) {
                    if (ee.getKey().startsWith(pfx)) {
                        subEntries.put(ee.getKey().substring(pfx.length()), ee.getValue());
                    }
                }
                sub.entries = subEntries;
                MODS.put(jm.id, sub);
                jijMods.add(sub);
                // M7-X15b : accessWidener du JiJ — sur vraie Fabric le loader
                // applique celui de CHAQUE mod (JiJ inclus). Sans ça, le mixin
                // de fabric-resource-loader-v1 accède à un champ resté privé
                // -> IllegalAccessError PackRepository (02:04). Le subEntries du
                // JiJ ne contient PAS l'AW (préfixe irium-jij/ seulement pour
                // les ressources non-classe) -> on le lit dans les entries du
                // parent à la racine du JiJ fusionné.
                try {
                    if (jm.accessWidener != null) {
                        java.util.Set<String> subAw = applyAccessWidener(jm, entries, early);
                        awRetransform.addAll(subAw);
                    }
                } catch (Throwable awErr) {
                    IriumAgent.log("[fabric-mod] JiJ " + jm.id + " accessWidener échec: " + awErr);
                }
                if (jm.mixins != null) {
                    for (String cfg : jm.mixins) MixinGateway.addConfig(cfg);
                }
                IriumAgent.log("[fabric-mod] JiJ '" + jm.id + "' v" + jm.version + " installé (sous-mod de " + meta.id + ")");
            } catch (Throwable t) {
                IriumAgent.log("[fabric-mod] JiJ " + k + " échec: " + t);
            }
        }
        mod.jij = jijMods;
        for (Mod sub : jijMods) sub.jarPath = modJar; // getOrigin() du JiJ = jar fusionné

        // M7-B9 : assets (lang/textures) servis comme resource pack built-in
        IriumPackSource.register(meta.id, entries);
        mod.jarPath = modJar;

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

        // M7-X3 : classes AW déjà chargées (install late) -> retransform pour
        // appliquer les flags. ÉCHEC NON FATAL : une classe non widennée
        // nécessite un restart (comme les mixins déjà chargés).
        if (!awRetransform.isEmpty()) {
            MixinGateway.retransform(awRetransform.toArray(new String[0]));
        }

        if (early) {
            // M7-X3 : preLaunch (ex. sodium : env checks + GPU probe + workarounds).
            // Sur vraie Fabric : avant le jeu. Ici : fin d'armement boot.
            runEntrypoint(mod, "preLaunch", net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint.class,
                    pl -> pl.onPreLaunch());
            IriumAgent.log("[fabric-mod] '" + meta.id + "' arme au boot (configs mixin only)");
            return; // entrypoints au join, pas avant le boot MC
        }

        IriumAgent.log("[fabric-mod] '" + meta.id + "' v" + meta.version + " installe ("
                + entries.size() + " entrees, " + countClasses(entries) + " classes)");

        // entrypoints : d'abord main, puis client ; JOIN ensuite (même task render,
        // APRÈS init — sinon les receivers du mod ratent le join déjà passé)
        onRenderThread(() -> {
            // JiJ d'abord (dépendances), puis le parent
            for (Mod sub : jijMods) {
                runEntrypoint(sub, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());
                ACTIVATED_MODS.add(sub.id); // M7-X23
            }
            runEntrypoint(mod, "main", net.fabricmc.api.ModInitializer.class, mi -> mi.onInitialize());
            runEntrypoint(mod, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());
            ACTIVATED_MODS.add(mod.id); // M7-X23
            if (dev.irium.agent.IriumTap.currentChannel() != null) {
                dev.irium.agent.IriumTap.fireJoinLate();
            }
        });

        // M7-B9 : install à chaud -> les resource packs doivent recharger pour
        // voir les assets du mod (lang, textures). Sur le render thread.
        dev.irium.agent.module.ResourcePackReloader.schedule();
    }

    /** M7-X17 : mods dont les entrypoints ont TOURNÉ dans cette JVM. Un
     * entrypoint Fabric s'exécute UNE fois par JVM (sémantique loader). Après
     * un uninstallAll() (déconnexion) + re-install (rejoin), on rebranche via
     * fireJoinLate()/JOIN events — JAMAIS en ré-exécutant onInitializeClient :
     * sodium ConfigManager jette "duplicate mod id" (02:44), Xaero écrasait son
     * singleton. uninstallAll() ne vide PAS ce set. */
    private static final java.util.Set<String> ACTIVATED_MODS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private interface Init<T> { void run(T t); }

    /**
     * M7-X3 : extrait le .accesswidener du jar, cumule les règles dans
     * AccessWidener.ACTIVE et retourne les owners déjà chargés à retransformer
     * (install late). En early (premain), retourne vide : les classes seront
     * widennées à leur définition par le pipeline transformer.
     */
    private static java.util.Set<String> applyAccessWidener(ModJarMeta meta, Map<String, byte[]> entries, boolean early) {
        if (meta.accessWidener == null) return java.util.Collections.emptySet();
        byte[] awb = entries.get(meta.accessWidener);
        if (awb == null) {
            IriumAgent.log("[fabric-mod] accessWidener '" + meta.accessWidener + "' introuvable dans le jar -> ignoré");
            return java.util.Collections.emptySet();
        }
        Map<String, List<AccessWidener.Rule>> rules = AccessWidener.parse(new String(awb, StandardCharsets.UTF_8));
        int n = rules.values().stream().mapToInt(List::size).sum();
        AccessWidener.ACTIVE.putAll(rules);
        // owners dé-finalisables (règles extendable member)
        for (List<AccessWidener.Rule> rs : rules.values()) {
            for (AccessWidener.Rule r : rs) {
                if (!r.isClass && !r.accessible) AccessWidener.EXTENDABLE_OWNERS.add(r.owner);
            }
        }
        IriumAgent.log("[fabric-mod] accessWidener '" + meta.id + "': " + n + " règle(s), "
                + rules.size() + " classe(s) ciblée(s)");
        if (early) return java.util.Collections.emptySet();
        // late : retransformer les owners déjà chargés (les autres seront
        // widennées à leur définition)
        java.util.Set<String> toRetransform = new java.util.LinkedHashSet<>();
        for (String owner : rules.keySet()) {
            String dotted = owner.replace('/', '.');
            try {
                Class.forName(dotted, false, FabricModHost.class.getClassLoader());
                toRetransform.add(dotted); // déjà chargée -> retransform
            } catch (ClassNotFoundException notLoaded) {
                // pas encore chargée -> sera widennée à la définition
            } catch (Throwable ignored) {}
        }
        return toRetransform;
    }


    /** M7-B11 : sous-mods JiJ d'un mod parent (activation au join, avant le parent). */
    private static List<Mod> jijModsOf(Mod parent) {
        return parent.jij == null ? java.util.Collections.emptyList() : parent.jij;
    }

    /**
     * M7-B11 : exécute r sur le thread RENDER (sémantique Fabric). Sur vraie
     * Fabric les entrypoints tournent sur le thread principal AVANT le premier
     * tick ; chez nous ils sont déclenchés au join depuis le thread Netty ->
     * course avec les mixins tick (Xaero : HudMod.INSTANCE publié par le
     * super-ctor pendant que le render thread l'utilise déjà). Minecraft.execute
     * sérialise avec les ticks ; si le render thread est déjà le courant, run direct.
     */
    private static void onRenderThread(Runnable r) {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            Object inst = mc.getMethod("getInstance").invoke(null);
            // isSameThread() : vrai si le thread courant EST le thread jeu (render/main)
            boolean same = (Boolean) mc.getMethod("isSameThread").invoke(inst);
            if (same) { r.run(); return; }
            // execute() : BlockableEventLoop — sérialise avec les ticks
            mc.getMethod("execute", Runnable.class).invoke(inst, r);
        } catch (Throwable t) {
            IriumAgent.log("[fabric-mod] bascule render thread échec (" + t + ") -> exécution directe");
            r.run();
        }
    }

    /**
     * M7-B12 : appelé par MinecraftReadyMixin à la fin du ctor Minecraft.
     * Les mods armés (boot/attach précoce) ont leurs mixins vivants depuis la
     * définition des classes, mais leurs entrypoints n'ont pas tourné — certains
     * handlers les supposent initialisés (xaerolib : XaeroLib.INSTANCE dans
     * Player.onTickHead). On active tout ICI : instance Minecraft présente,
     * aucun monde/tick/join encore possible. Idempotent, une fois par boot.
     */
    public static void onMinecraftReady() {
        // M7-B12 : le verrou sérialise avec armFromCache()/install() — si le ctor
        // Minecraft finit PENDANT l'armement, on attend sa fin ici (état complet).
        synchronized (FabricModHost.class) {
            if (!bootedByIrium) return; // pas d'armement boot -> rien à activer tôt
            if (earlyActivated) return;
            earlyActivated = true;
        }
        IriumAgent.log("[fabric-mod] activation précoce (fin ctor Minecraft) : " + MODS.size() + " mod(s)");
        // Boucle UNIQUE dans l'ordre correct : JiJ (dépendances) AVANT leur parent,
        // parent par parent. Les JiJ sont aussi dans MODS (surface FabricLoader)
        // mais ne doivent PAS être re-activés en boucle directe (duplicate
        // config channel xaerolib:main -> XaeroLib.INSTANCE cassé -> NPE cascade).
        List<Mod> ordered = new ArrayList<>();
        for (Mod mod : MODS.values()) {
            if (mod.isJij) continue; // les JiJ sont activés via leur parent ci-dessous
            for (Mod sub : jijModsOf(mod)) ordered.add(sub);
            ordered.add(mod);
        }
        for (Mod m : ordered) {
            runEntrypoint(m, "main", net.fabricmc.api.ModInitializer.class, mi -> mi.onInitialize());
            runEntrypoint(m, "client", net.fabricmc.api.ClientModInitializer.class, ci -> ci.onInitializeClient());
            ACTIVATED_MODS.add(m.id); // M7-X23 : alimente le chemin ghost rejoin-simple
        }
        IriumAgent.log("[fabric-mod] activation précoce terminée");
    }

    private static <T> void runEntrypoint(Mod mod, String key, Class<T> type, Init<T> init) {
        List<String> names = mod.meta.entrypoints == null ? null : mod.meta.entrypoints.get(key);
        if (names == null || names.isEmpty()) return;
        for (String name : names) {
            // M7-X17 : une classe d'entrypoint ne s'exécute QU'UNE FOIS par JVM
            // (sémantique fabric-loader). Re-join -> fireJoinLate() rebranche le
            // réseau ; ré-exécuter onInitializeClient casse sodium (duplicate
            // mod id 02:44) et écrasait le singleton Xaero.
            if (ENTRYPOINT_RAN.contains(name)) {
                IriumAgent.log("[fabric-mod] entrypoint " + key + " " + name + " déjà exécuté dans cette JVM -> skip");
                continue;
            }
            ENTRYPOINT_RAN.add(name);
            try {
                // M7-X18 : entrypoint method-ref "a.b.C::method" (légal fmj,
                // language adapter par défaut). Charger la classe, invoquer
                // la méthode statique. Ex: fabric NetworkingImpl::init.
                if (name.contains("::")) {
                    int cut = name.indexOf("::");
                    String cn = name.substring(0, cut);
                    String mn = name.substring(cut + 2);
                    Class<?> mc = mod.loader.loadClass(cn);
                    java.lang.reflect.Method m = mc.getDeclaredMethod(mn);
                    m.setAccessible(true);
                    m.invoke(null);
                    IriumAgent.log("[fabric-mod] entrypoint " + key + " (method-ref): " + name);
                    continue;
                }
                Class<?> c = mod.loader.loadClass(name);
                // M7-X3 : jamais re-construire — re-run l'init sur l'instance conservée.
                // Le 2e newInstance() jette APRÈS avoir écrasé le singleton statique
                // du mod (objet à moitié construit) -> NPE à chaque tick.
                final boolean[] fresh = {false};
                Object o = ENTRYPOINT_INSTANCES.computeIfAbsent(name, n -> {
                    fresh[0] = true;
                    try { return c.getDeclaredConstructor().newInstance(); }
                    catch (Throwable t) { throw new RuntimeException(t); }
                });
                if (!type.isInstance(o)) {
                    IriumAgent.log("[fabric-mod] entrypoint " + key + " " + name + " n'implémente pas " + type.getSimpleName());
                    continue;
                }
                IriumAgent.log("[fabric-mod] entrypoint " + key + ": " + name + (fresh[0] ? "" : " (instance conservée)"));
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
                    // M7-X3 : instance unique par FQCN — getEntrypoints est appelé
                    // à chaque ouverture d'écran (Mod Menu -> "modmenu") : un
                    // newInstance() par appel écraserait le singleton du mod.
                    Object o = ENTRYPOINT_INSTANCES.computeIfAbsent(n, k -> {
                        try { return c.getDeclaredConstructor().newInstance(); }
                        catch (Throwable t) { throw new RuntimeException(t); }
                    });
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
                @Override public net.fabricmc.loader.api.ModContainer getProvider() {
                    return dev.irium.agent.module.FabricLoaderClient.modContainer();
                }
            });
        }
        return out;
    }

    public static Optional<net.fabricmc.loader.api.ModContainer> container(String modId) {
        // M7-B11 : conteneur virtuel "irium" — l'agent EST l'hôte des mods
        // streamés (getProvider() des EntrypointContainer, Mod Menu...).
        if ("irium".equals(modId)) {
            return Optional.of(new net.fabricmc.loader.api.ModContainer() {
                @Override public net.fabricmc.loader.api.metadata.ModMetadata getMetadata() {
                    return new ModMetadata() {
                        @Override public String getId() { return "irium"; }
                        @Override public net.fabricmc.loader.api.Version getVersion() {
                            return net.fabricmc.loader.api.SemanticVersion.parse("0.6.19");
                        }
                        @Override public String getName() { return "Irium"; }
                        @Override public String getType() { return "fabric"; }
                    };
                }
                @Override public List<Path> getRootPaths() {
                    try {
                        return List.of(Path.of(dev.irium.agent.IriumAgent.class
                                .getProtectionDomain().getCodeSource().getLocation().toURI()));
                    } catch (Throwable t) { return List.of(Path.of(".")); }
                }
                @Override public net.fabricmc.loader.api.metadata.ModOrigin getOrigin() {
                    return ModOriginIRIUM.INSTANCE;
                }
            });
        }
        Mod found = MODS.get(modId);
        if (found == null) found = GHOSTS.get(modId); // ghost : queryable après wipe (crash 19:40)
        if (found == null) return Optional.empty();
        final Mod m = found;
        return Optional.of(new net.fabricmc.loader.api.ModContainer() {
            @Override public net.fabricmc.loader.api.metadata.ModMetadata getMetadata() { return metaOf(m); }
            @Override public List<Path> getRootPaths() {
                java.nio.file.Path jp = m.jarPath;
                return jp != null ? List.of(jp) : List.of(Path.of("."));
            }
            /** M7-B11 : Mod Menu icônes — getPath doit donner un FICHIER lisible,
             *  pas un chemin virtuel dans le jar zip. Extraction à la demande. */
            @Override public Path getPath(String file) {
                Map<String, byte[]> en = m.entries;
                if (en != null) {
                    String key = file.startsWith("/") ? file.substring(1) : file;
                    byte[] b = en.get(key);
                    if (b != null) {
                        try {
                            java.nio.file.Path dir = java.nio.file.Path.of(
                                    System.getProperty("java.io.tmpdir"), "irium-modfiles", m.id);
                            java.nio.file.Files.createDirectories(dir);
                            java.nio.file.Path out = dir.resolve(key.replace('/', '_'));
                            if (!java.nio.file.Files.exists(out)) {
                                java.nio.file.Files.write(out, b);
                            }
                            return out;
                        } catch (Throwable ignored) {}
                    }
                }
                return net.fabricmc.loader.api.ModContainer.super.getPath(file);
            }
            @Override public net.fabricmc.loader.api.metadata.ModOrigin getOrigin() {
                // M7-B11 : Xaero lit getOrigin().getPaths().get(0).getFileName()
                // (PlatformContextFabric) pour placer ses dossiers de config ->
                // retourner le VRAI jar matérialisé, pas une liste vide.
                java.nio.file.Path jp = m.jarPath;
                if (jp == null) return ModOriginIRIUM.INSTANCE;
                return new ModOriginIRIUM(jp);
            }
        });
    }

    /** ModOrigin Irium — pointe le jar matérialisé du mod streamé. */
    static final class ModOriginIRIUM implements net.fabricmc.loader.api.metadata.ModOrigin {
        static final ModOriginIRIUM INSTANCE = new ModOriginIRIUM(null);
        private final java.nio.file.Path jar;
        ModOriginIRIUM(java.nio.file.Path jar) { this.jar = jar; }
        @Override public Kind getKind() { return jar != null ? Kind.PATH : Kind.UNKNOWN; }
        @Override public List<Path> getPaths() { return jar != null ? List.of(jar) : List.of(); }
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
            @Override public String getName() { return m.meta.name != null ? m.meta.name : m.id; }
            @Override public String getType() { return "fabric"; }
            /** M7-B11d : description réelle du fabric.mod.json (Mod Menu). */
            @Override public String getDescription() {
                return m.meta.description == null ? "" : m.meta.description;
            }
            /** M7-B11d : authors (String, liste de strings, OU liste d'objets
             *  {name, contact} — forme standard des gros mods). */
            @Override public java.util.Collection<net.fabricmc.loader.api.metadata.Person> getAuthors() {
                java.util.List<net.fabricmc.loader.api.metadata.Person> out = new java.util.ArrayList<>();
                Object a = m.meta.authors;
                if (a instanceof String s) {
                    out.add(person(s));
                } else if (a instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof String s) out.add(person(s));
                        else if (o instanceof Map<?, ?> mo && mo.get("name") instanceof String nm) {
                            out.add(person(nm, mo.get("contact")));
                        }
                    }
                }
                return out;
            }
            /** M7-B11d : licence ("MIT", "All Rights Reserved"...). */
            @Override public java.util.Collection<String> getLicense() {
                Object lic = m.meta.license;
                if (lic instanceof String s) return java.util.List.of(s);
                if (lic instanceof List<?> l) {
                    java.util.List<String> out = new java.util.ArrayList<>();
                    for (Object o : l) if (o instanceof String s) out.add(s);
                    return out;
                }
                return java.util.List.of();
            }
            /** M7-B11d : contacts réels (liens Source/Homepage de la description). */
            @Override public net.fabricmc.loader.api.metadata.ContactInformation getContact() {
                Map<String, Object> c = m.meta.contact;
                if (c == null || c.isEmpty()) return net.fabricmc.loader.api.metadata.ModMetadata.super.getContact();
                java.util.Map<String, String> flat = new java.util.HashMap<>();
                for (Map.Entry<String, Object> e : c.entrySet()) {
                    if (e.getValue() instanceof String s) flat.put(e.getKey(), s);
                }
                return new net.fabricmc.loader.api.metadata.ContactInformation() {
                    @Override public java.util.Optional<String> get(String key) {
                        return java.util.Optional.ofNullable(flat.get(key));
                    }
                    @Override public java.util.Map<String, String> asMap() { return flat; }
                };
            }
            /** M7-X2 : custom values RÉELLES du fmj (Mod Menu lit custom.modmenu:*,
             *  badges/links/api_level ; Sodium lit custom sodium.options...). */
            @Override public boolean containsCustomValue(String key) {
                return m.meta.custom != null && m.meta.custom.containsKey(key);
            }
            @Override public CustomValue getCustomValue(String key) {
                if (m.meta.custom == null) return null;
                Object v = m.meta.custom.get(key);
                return v == null ? null : CustomValue.of(v);
            }
            @Override public java.util.Map<String, CustomValue> getCustomValues() {
                java.util.Map<String, CustomValue> out = new java.util.HashMap<>();
                if (m.meta.custom != null) {
                    for (Map.Entry<String, Object> e : m.meta.custom.entrySet()) {
                        out.put(e.getKey(), CustomValue.of(e.getValue()));
                    }
                }
                return out;
            }
            /** M7-B11 : Mod Menu lit l'icône (getIcon -> requireNonNull sinon crash). */
            @Override public java.util.Optional<String> getIconPath(int size) {
                Object ic = m.meta.icon;
                if (ic instanceof String s) return java.util.Optional.of(s);
                if (ic instanceof Map<?, ?> map && !map.isEmpty()) {
                    // prendre la plus grande clé <= size, sinon la plus grande
                    Object best = null; int bestK = -1;
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        try {
                            int k = Integer.parseInt(String.valueOf(e.getKey()));
                            if (k <= size && k > bestK) { bestK = k; best = e.getValue(); }
                        } catch (NumberFormatException ignored) {}
                    }
                    if (best == null) { // aucune <= size : prendre la première
                        best = map.values().iterator().next();
                    }
                    if (best instanceof String s) return java.util.Optional.of(s);
                }
                return java.util.Optional.empty();
            }
        };
    }

    /** M7-B11d : Person minimal. Les authors objets du fmj portent name + contact. */
    private static net.fabricmc.loader.api.metadata.Person person(String name) {
        return new net.fabricmc.loader.api.metadata.Person() {
            @Override public String getName() { return name; }
        };
    }

    /** M7-X2 : Person avec contact ({homepage, sources, issues} de l'author). */
    private static net.fabricmc.loader.api.metadata.Person person(String name, Object contact) {
        java.util.Map<String, String> flat = new java.util.HashMap<>();
        if (contact instanceof Map<?, ?> cm) {
            for (Map.Entry<?, ?> e : cm.entrySet()) {
                if (e.getValue() instanceof String s) flat.put(String.valueOf(e.getKey()), s);
            }
        }
        return new net.fabricmc.loader.api.metadata.Person() {
            @Override public String getName() { return name; }
            @Override public net.fabricmc.loader.api.metadata.ContactInformation getContact() {
                if (flat.isEmpty()) return net.fabricmc.loader.api.metadata.Person.super.getContact();
                return new net.fabricmc.loader.api.metadata.ContactInformation() {
                    @Override public java.util.Optional<String> get(String key) { return java.util.Optional.ofNullable(flat.get(key)); }
                    @Override public java.util.Map<String, String> asMap() { return flat; }
                };
            }
        };
    }

    public static Collection<net.fabricmc.loader.api.ModContainer> allContainers() {
        List<net.fabricmc.loader.api.ModContainer> out = new ArrayList<>();
        // MODS d'abord, puis ghosts jamais présents (mods uninstallés mais queryables)
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>(MODS.keySet());
        ids.addAll(GHOSTS.keySet());
        for (String id : ids) container(id).ifPresent(out::add);
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
            // M7-B11 : ABSOLU obligatoire — Xaero fait configDir.getParent().resolve(...)
            // (HudMod.loadClient) ; un chemin relatif -> getParent() null -> NPE.
            Path p = Path.of("irium-config").toAbsolutePath().normalize();
            Files.createDirectories(p);
            return p;
        } catch (Throwable t) { return Path.of(".").toAbsolutePath(); }
    }

    /* ---------------- payloads ---------------- */

    /** Émission custom_payload serverbound via le tap (0x16). */
    public static void sendPayload(CustomPacketPayload payload) {
        dev.irium.agent.ClientPayloadSender.send(payload);
    }

    /* ---------------- sandbox ---------------- */

    /** métadonnées des mods désinstallés (ghost containers) : la sandbox se vide
     *  à la déconnexion MAIS les classes restent chargées (singletons vivants).
     *  Mod Menu cache sa liste et fait getModContainer(id).orElseThrow() au
     *  rendu -> un wipe pendant qu'un écran mod est ouvert = FATAL (crash 19:40).
     *  Les mods désinstallés restent donc QUERYABLES (métas + icône), comme sur
     *  vraie Fabric où un mod installé n'est jamais dé-chargé. */
    private static final Map<String, Mod> GHOSTS = new ConcurrentHashMap<>();

    public static synchronized void uninstallAll() {
        int n = MODS.size();
        for (Map.Entry<String, Mod> e : MODS.entrySet()) GHOSTS.putIfAbsent(e.getKey(), e.getValue());
        MODS.clear();
        BY_CLASS.clear();
        net.fabricmc.fabric.impl.client.networking.ClientNetworkingImpl.clear();
        dev.irium.agent.hud.FabricHudBridge.clearAll();
        IriumPackSource.clear();
        IriumAgent.log("[fabric-mod] sandbox vidée (" + n + " mod(s), " + GHOSTS.size() + " ghost(s))");
    }

    /* ---------------- utilitaires ---------------- */

    private static Map<String, byte[]> unzip(byte[] jar) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        Map<String, byte[]> nested = new java.util.LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                byte[] b = zin.readAllBytes();
                out.put(e.getName(), b);
                // JiJ : jars imbriqués -> fusionnés (les modules fabric-* de fabric-api
                // sont couverts par la surface Irium, on ne fusionne que le reste, ex.
                // voicechat-api, xaerolib-fabric-*. NB: startsWith sur le BASENAME —
                // "xaerolib-fabric-26.2.jar" contient "fabric-" mais ne fait PAS
                // partie de fabric-api, il doit être fusionné.)
                if (e.getName().startsWith("META-INF/jars/") && e.getName().endsWith(".jar")) {
                    // M7-X14 : fusionner TOUS les JiJ, y compris fabric-*. La décision
                    // M7-B11 de les exclure ("la surface Irium couvre fabric") était
                    // valable pour voicechat/xaero/modmenu mais fausse pour sodium :
                    // il embarque fabric-block-getter-api-v2 etc. et crash en
                    // NoClassDefFoundError si on les jette (BlockRenderCache 01:45).
                    // Les stubs net/fabricmc de l'agent ne couvrent PAS 37 interfaces
                    // FRAPI. Les vraies classes JiJ (putIfAbsent) priment sur les
                    // stubs car définies dans le loader APP via le jar matérialisé.
                    nested.put(e.getName(), b);
                }
            }
        }
        for (Map.Entry<String, byte[]> ne : nested.entrySet()) {
            String base = ne.getKey();
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(ne.getValue()))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String n2 = e.getName();
                    // NB: META-INF/services/ des JiJ sont conservés (putIfAbsent) —
                    // xaerolib résout ses helpers platform via ServiceLoader.
                    byte[] b2 = zin.readAllBytes();
                    if (n2.equals("fabric.mod.json")) {
                        // M7-B11 : métas JiJ conservées sous clé préfixée — le JiJ est
                        // un VRAI mod (entrypoints + mixins propres, ex. xaerolib pose
                        // XaeroLib.INSTANCE dans son entrypoint client) et doit être
                        // installé comme sous-mod AVANT son parent.
                        out.putIfAbsent("irium-jij/" + base + "/" + n2, b2);
                        continue;
                    }
                    // M7-B11c : ressources du JiJ (icône, assets) AUSSI préfixées pour
                    // les entries propres du sous-mod (Mod Menu icônes) — la fusion
                    // à plat expose voicechat.png avant icon.png du JiJ (collision).
                    if (!n2.endsWith(".class")) {
                        out.putIfAbsent("irium-jij/" + base + "/" + n2, b2);
                    }
                    out.putIfAbsent(n2, b2);
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

    /** Écrit le jar du mod sur disque (cache par CONTENU) pour l'exposition au loader APP. */
    private static java.nio.file.Path materializeJar(String id, Map<String, byte[]> entries) throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "irium-mods");
        java.nio.file.Files.createDirectories(dir);
        // M7-X15 : le cache doit inclure le CONTENU, pas seulement l'id. Un jar
        // périmé (ex. sodium matérialisé AVANT M7-X14, sans ses JiJ fabric)
        // restait appendu au classpath pour toujours -> NoClassDefFoundError sur
        // des classes pourtant fusionnées (ExtendedBlockModelSubmit 01:53).
        String digest = sha256Hex(entriesDigest(entries)).substring(0, 12);
        java.nio.file.Path jar = dir.resolve(id + "-" + digest + ".jar");
        if (java.nio.file.Files.exists(jar)) return jar;
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

    /** Digest déterministe des entries (clés triées + bytes) pour la clé de cache. */
    private static byte[] entriesDigest(Map<String, byte[]> entries) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            java.util.TreeMap<String, byte[]> sorted = new java.util.TreeMap<>(entries);
            for (Map.Entry<String, byte[]> e : sorted.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(e.getValue());
            }
            return md.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
