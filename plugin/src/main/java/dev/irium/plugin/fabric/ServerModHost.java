package dev.irium.plugin.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.EntrypointContainer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * M7 : héberge de vrais mods Fabric côté serveur — SANS Fabric.
 *
 * Charge mods/*.jar dans un URLClassLoader enfant du classloader serveur
 * (accès NMS mojang-mappé par délégation), installe le FabricLoader stub,
 * exécute l'entrypoint "main". Le mod démarre son propre voice server UDP
 * dans ce process : c'est le vrai code du mod qui héberge.
 *
 * Cycles : Bukkit join/quit/lifecycle → events Fabric du mod.
 */
public final class ServerModHost implements Listener {

    private final Plugin plugin;
    private ClassLoader modClassLoader;

    public ServerModHost(Plugin plugin) {
        this.plugin = plugin;
    }

    /* ---------------- démarrage ---------------- */

    public void enable() {
        FabricNetBridge.init(plugin); // AVANT les mods : les register du mod doivent trouver plugin != null
        Path modsDir = plugin.getDataFolder().toPath().resolve("mods");
        // mods/ vit à côté du jar du plugin : plugins/Irium/mods/
        modsDir = plugin.getDataFolder().toPath().resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            plugin.getLogger().info("[fabric] aucun dossier mods/ — adaptateur inactif");
            return;
        }
        try (var files = Files.list(modsDir)) {
            List<Path> jars = files.filter(p -> p.toString().endsWith(".jar")).toList();
            if (jars.isEmpty()) {
                plugin.getLogger().info("[fabric] mods/ vide — adaptateur inactif");
                return;
            }
            URL[] urls = new URL[jars.size()];
            for (int i = 0; i < jars.size(); i++) urls[i] = jars.get(i).toUri().toURL();
            modClassLoader = new URLClassLoader("IriumMods", urls,
                    getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    return super.loadClass(name, resolve);
                }

                /** Patch à la définition : CommonCompatibilityManager.execute(MinecraftServer, Runnable)
                 *  fait MinecraftServer.execute() qui lève UnsupportedOperationException sur Folia —
                 *  on est déjà sur le thread régional du joueur : run direct. Jar intact sur disque. */
                @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                    byte[] bytes = readClassBytes(name);
                    if (bytes == null) throw new ClassNotFoundException(name);
                    if (name.equals("de.maxhenkel.voicechat.intercompatibility.CommonCompatibilityManager")) {
                        bytes = FoliaExecutePatch.patch(bytes);
                        plugin.getLogger().info("[fabric] patch Folia apply: CommonCompatibilityManager.execute -> run direct");
                    }
                    Class<?> c = defineClass(name, bytes, 0, bytes.length);
                    resolveClass(c);
                    return c;
                }

                private byte[] readClassBytes(String name) {
                    String path = name.replace('.', '/') + ".class";
                    try (java.io.InputStream is = getResourceAsStream(path)) {
                        return is == null ? null : is.readAllBytes();
                    } catch (Exception e) {
                        return null;
                    }
                }
            };
            for (Path jar : jars) startMod(jar);
        } catch (Throwable t) {
            plugin.getLogger().warning("[fabric] échec chargement mods: " + t);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ---------------- entrypoints ---------------- */

    private void startMod(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry entry = jf.getEntry("fabric.mod.json");
            if (entry == null) {
                plugin.getLogger().warning("[fabric] " + jar.getFileName() + ": pas de fabric.mod.json — ignoré");
                return;
            }
            String json = new String(jf.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            ModMetaParser.ModMeta meta = ModMetaParser.parse(json);
            plugin.getLogger().info("[fabric] hébergement " + meta.id + " " + meta.version
                    + " (entrypoint main=" + meta.main + ")");

            // 1. stubs Fabric visibles du mod via le classloader parent (plugin CL)
            //    — déjà compilés dans ce plugin (net.fabricmc.* dans le jar plugin)
            // 2. installer le loader stub (avant tout code du mod : getInstance() doit marcher)
            net.fabricmc.loader.impl.FabricLoaderImpl.INSTANCE =
                    new net.fabricmc.loader.impl.FabricLoaderImpl(
                            new IriumFabricLoader(meta, jar, modClassLoader));

            // 3. entrypoint main
            if (meta.main != null && !meta.main.isBlank()) {
                Class<?> c = modClassLoader.loadClass(meta.main);
                Object instance = c.getDeclaredConstructor().newInstance();
                plugin.getLogger().info("[fabric] entrypoint main: " + meta.main + " → onInitialize()");
                ((ModInitializer) instance).onInitialize();
                plugin.getLogger().info("[fabric] " + meta.id + " initialisé avec succès");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[fabric] échec démarrage " + jar.getFileName() + ": " + t);
            t.printStackTrace();
        }
    }

    /* ---------------- ponts Bukkit → Fabric ---------------- */

    /** Appelé par IriumPlugin après le boot complet du serveur. */
    public void fireServerStarted() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted((net.minecraft.server.MinecraftServer) server);
        } catch (Throwable t) {
            plugin.getLogger().warning("[fabric] SERVER_STARTED échec: " + t);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        firePlayerEvent(e.getPlayer(), "PLAYER_LOGGED_IN");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        firePlayerEvent(e.getPlayer(), "PLAYER_LOGGED_OUT");
    }

    /**
     * Tire l'event du mod (PlayerEvents.PLAYER_LOGGED_IN/OUT) par réflexion —
     * ces champs sont normalement remplis par PlayerManagerMixin, absent côté
     * Irium ; le plugin les déclenche depuis Bukkit.
     */
    private void firePlayerEvent(org.bukkit.entity.Player p, String fieldName) {
        if (modClassLoader == null) return;
        try {
            Object nms = p.getClass().getMethod("getHandle").invoke(p);
            Class<?> events = modClassLoader.loadClass("de.maxhenkel.voicechat.events.PlayerEvents");
            Object event = events.getField(fieldName).get(null);
            Object invoker = event.getClass().getMethod("invoker").invoke(event);
            Method accept = invoker.getClass().getMethod("accept", Object.class);
            accept.setAccessible(true);
            accept.invoke(invoker, nms);
            plugin.getLogger().info("[fabric] " + fieldName + " → " + p.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("[fabric] " + fieldName + " échec: " + t);
        }
    }
}
