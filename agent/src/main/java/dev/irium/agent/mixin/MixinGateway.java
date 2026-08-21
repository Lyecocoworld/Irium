package dev.irium.agent.mixin;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * M7-B : héberge le runtime Mixin (sponge-mixin relocalisé dans l'agent) dans la JVM du client.
 *
 * Usage :
 *   MixinGateway.start(inst);                          // une fois, à l'attach de l'agent
 *   MixinGateway.registerMod(classLoaderWithMixins);   // par mod streamé
 *   MixinGateway.addConfig("voicechat.mixins.json");   // par config mixin du mod
 *
 * Le ClassFileTransformer délègue chaque classe au MixinTransformer officiel.
 * MC 26.2 tourne en noms Mojang non obfusqués : zéro remapping nécessaire.
 */
public final class MixinGateway {

    private static boolean started;
    private static IMixinTransformer transformer;
    private static Instrumentation instrumentation;
    /** classloaders des mods (ressources mixins.json + classes mixin) */
    static final List<ClassLoader> modLoaders = new ArrayList<>();

    private MixinGateway() {}

    public static synchronized void start(Instrumentation inst) {
        if (started) return;
        started = true;
        instrumentation = inst;
        try {
            // M7-B9 : config mixin de l'AGENT (injecte la pack source Irium dans
            // PackRepository dès le boot). APRÈS MixinBootstrap.init() — avant,
            // l'environnement n'existe pas encore ("Environment conflict").
            MixinBootstrap.init();
            Mixins.addConfiguration("irium.mixins.json");
            // M7-B11 : sponge 0.8.7 bride MAX_SUPPORTED à JAVA_13 -> les mods
            // récents déclarent JAVA_21/JAVA_25 et leur config ENTIERE est
            // rejetée (MixinInitialisationError, ex. mixins.modmenu.json).
            // MAX_SUPPORTED est un champ public static NON-final -> on l'élève.
            try {
                Class<?> cl = Class.forName(
                        "org.spongepowered.asm.mixin.MixinEnvironment$CompatibilityLevel");
                java.lang.reflect.Field f = cl.getField("MAX_SUPPORTED");
                f.set(null, cl.getField("JAVA_25").get(null));
                log("CompatibilityLevel.MAX_SUPPORTED élevé à JAVA_25");
            } catch (Throwable compat) {
                log("MAX_SUPPORTED inchangé: " + compat);
            }
            // M7-B11 : la détection ASM de sponge lit Opcodes.getDeclaredFields()
            // dans l'ordre de déclaration : ASM10_EXPERIMENTAL (bit experimental
            // -> ignoré pour la version) puis ASM9 -> conclut "ASM 9.0" alors
            // qu'on embarque 9.9.1. JAVA_25.isSupported() exige ASM >= 9.8 ->
            // config rejetée. On force les champs de version détectée.
            try {
                Class<?> asm = Class.forName("org.spongepowered.asm.util.asm.ASM");
                java.lang.reflect.Field maj = asm.getDeclaredField("majorVersion");
                java.lang.reflect.Field min = asm.getDeclaredField("minorVersion");
                java.lang.reflect.Field imin = asm.getDeclaredField("implMinorVersion");
                java.lang.reflect.Field pat = asm.getDeclaredField("patchVersion");
                maj.setAccessible(true); min.setAccessible(true);
                imin.setAccessible(true); pat.setAccessible(true);
                maj.setInt(null, 9); min.setInt(null, 9);
                imin.setInt(null, 9); pat.setInt(null, 1);
                log("ASM détecté forcé à 9.9.1 (sponge lit ASM10_EXPERIMENTAL→9.0)");
            } catch (Throwable asmv) {
                log("version ASM inchangée: " + asmv);
            }
            // Racine M7-B4 : à l'attach à chaud, PERSONNE n'a instancié le MixinTransformer
            // (c'est normalement le rôle du launcher hôte). getActiveTransformer() = null
            // pour toujours -> transform() no-op silencieux -> retransform sans effet.
            // MixinTransformer est package-private : instanciation par réflexion.
            Object t = MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT).getActiveTransformer();
            if (!(t instanceof IMixinTransformer)) {
                Class<?> mtClass = Class.forName(
                        "org.spongepowered.asm.mixin.transformer.MixinTransformer");
                java.lang.reflect.Constructor<?> ctor = mtClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                t = ctor.newInstance();
                if (!(t instanceof IMixinTransformer)) {
                    throw new IllegalStateException("MixinTransformer instancié n'implémente pas IMixinTransformer");
                }
                // Le constructeur s'est auto-enregistré comme transformer actif de l'env DEFAULT
            }
            if (t instanceof IMixinTransformer imt) transformer = imt;
            // M7-B9 : MixinExtras — sur vraie Fabric le loader le fournit ; nous
            // on EST le loader. init() enregistre les injecteurs (@WrapOperation,
            // @ModifyExpressionValue...) AVANT que les mods ne préparent leurs mixins.
            try {
                com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
                log("MixinExtras prêt");
            } catch (Throwable mex) {
                log("MixinExtras échec: " + mex);
            }
            inst.addTransformer(new IriumMixinTransformer(), true);
            log("Mixin runtime prêt (transformer=" + (transformer != null) + ")");
        } catch (Throwable t) {
            log("échec bootstrap Mixin: " + t);
        }
    }

    public static void registerMod(ClassLoader modLoader) {
        synchronized (modLoaders) { modLoaders.add(modLoader); }
    }

    /**
     * M7-B11 : l'attach a-t-il eu lieu AVANT la définition des classes MC ?
     * Si une classe net.minecraft.* est déjà chargée, les mixins des mods ne
     * peuvent plus s'appliquer "à la définition" pour les cibles précoces
     * (Minecraft, Keyboard, PackRepository) -> il faut la relance premain.
     */
    public static boolean anyMinecraftClassLoaded() {
        try {
            if (instrumentation == null) return true; // pas d'inst = pas de mixins, prudent
            for (Class<?> c : instrumentation.getAllLoadedClasses()) {
                String n = c.getName();
                // M7-B12 : net.minecraft.client.main.Main est TOUJOURS chargée avant
                // tout attach (c'est la classe de départ du process) — l'ignorer.
                // Le verdict "précoce" reste correct : Minecraft, Keyboard, Player...
                // ne se définissent qu'après, pendant le bootstrap du jeu.
                if (n.startsWith("net.minecraft.client.main.")) continue;
                if (n.startsWith("net.minecraft.client.") || n.startsWith("net.minecraft.server.")) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return true; // en cas de doute : considérer tardif (relance = sûr)
        }
    }

    /**
     * M7-B12 : UNE de ces classes (formes pointées) est-elle déjà définie ?
     * Critère exact du verdict course : si une cible de mixin est déjà chargée,
     * ses mixins ne pourront PAS s'appliquer à la définition -> relance.
     */
    public static boolean anyOfClassLoaded(java.util.Set<String> dottedNames) {
        try {
            if (instrumentation == null) return true;
            java.util.Set<String> loaded = new java.util.HashSet<>();
            for (Class<?> c : instrumentation.getAllLoadedClasses()) {
                loaded.add(c.getName());
            }
            for (String n : dottedNames) {
                if (loaded.contains(n)) return true;
            }
            return false;
        } catch (Throwable t) {
            return true; // doute -> tardif (relance = sûr)
        }
    }

    /**
     * Racine M7-B4-5 : les mixins injectent des INTERFACES DU MOD dans des classes MC
     * (PackRepository doit implémenter IPackRepository du mod). La classe MC vit dans
     * le loader APP, l'interface dans le ModClassLoader -> NoClassDefFoundError au cast.
     * Solution standard des agents : écrire le jar du mod sur disque et l'ajouter au
     * classpath du loader APP via appendToSystemClassLoaderSearch -> l'interface est
     * définie UNE fois, parent-first, visible des deux côtés.
     */
    public static void appendModToSystemClassPath(java.nio.file.Path modJar) {
        try {
            instrumentation.appendToSystemClassLoaderSearch(new java.util.jar.JarFile(modJar.toFile()));
            log("jar mod ajouté au classpath du loader APP: " + modJar.getFileName());
        } catch (Throwable t) {
            log("échec appendToSystemClassLoaderSearch: " + t);
        }
    }

    public static synchronized void addConfig(String configResource) {
        try {
            Mixins.addConfiguration(configResource);
            log("config mixin enregistrée: " + configResource);
        } catch (Throwable t) {
            StringBuilder sb = new StringBuilder("échec config mixin " + configResource + ": " + t);
            Throwable c = t.getCause();
            int depth = 0;
            while (c != null && depth++ < 6) {
                sb.append(" <- cause: ").append(c);
                c = c.getCause();
            }
            log(sb.toString());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(5, st.length); i++) log("    at " + st[i]);
        }
    }

    /** Retransforme des classes déjà chargées (attach à chaud) pour y appliquer les mixins.
     *  JVMTI interdit l'ajout de méthodes/interfaces sur classe chargée : une classe
     *  qui échoue ne doit PAS annuler les autres (batch = tout ou rien). */
    public static void retransform(String... classNames) {
        if (instrumentation == null) return;
        for (String n : classNames) {
            try {
                Class<?> c = Class.forName(n, false, MixinGateway.class.getClassLoader());
                if (!instrumentation.isModifiableClass(c)) continue;
                instrumentation.retransformClasses(c);
                log("retransformé: " + n);
            } catch (UnsupportedOperationException uoe) {
                // ex: attempted to add a method — classe chargée avant l'agent
                log("retransform IMPOSSIBLE (classe déjà chargée, JVMTI): " + n + " — " + uoe.getMessage());
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                log("retransform échec " + n + ": " + t);
            }
        }
    }

    static byte[] transform(String name, byte[] bytes) {
        // M7-B11 : ne transformer QUE les classes Minecraft. Un transformer JVMTI
        // global voit TOUT (java.io.*, classes des mods...) et le MixinTransformer
        // n'est pas fait pour : java/* déclenche des chargements en cascade
        // (ClassCircularityError au premain), et les classes des mods chargées
        // pendant addConfig (ex. le MixinPlugin custom de Xaero) re-enter le
        // runtime non-thread-safe -> ClassCircularityError -> config morte.
        if (name == null || bytes == null) return null;
        if (!(name.startsWith("net/minecraft/") || name.startsWith("com/mojang/"))) return null;
        IMixinTransformer t = lazyTransformer();
        if (t == null) return null;
        String dotted = name.replace('/', '.');
        try {
            byte[] out = t.transformClassBytes(name, dotted, bytes);
            if (out == null) return null;
            return out;
        } catch (Throwable err) {
            log("mixin transform échec " + name + ": " + err);
            return null;
        }
    }

    private static IMixinTransformer lazyTransformer() {
        IMixinTransformer t = transformer;
        if (t != null) return t;
        synchronized (MixinGateway.class) {
            if (transformer == null) {
                try {
                    Object o = MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT)
                            .getActiveTransformer();
                    if (o instanceof IMixinTransformer imt) transformer = imt;
                } catch (Throwable err) {
                    log("résolution lazy transformer échec: " + err);
                }
            }
            return transformer;
        }
    }

    /** Le transformer JVM : délègue tout au MixinTransformer, jamais d'exception. */
    public static final class IriumMixinTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(Module module, ClassLoader loader, String className,
                                Class<?> classBeingRedefined, ProtectionDomain pd, byte[] bytes) {
            return MixinGateway.transform(className, bytes);
        }
    }

    static void log(String m) { dev.irium.agent.SafeLog.v("[irium:mixin]", m); }
}
