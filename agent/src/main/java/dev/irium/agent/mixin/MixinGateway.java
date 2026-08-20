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
            MixinBootstrap.init();
            MixinEnvironment env = MixinEnvironment.getEnvironment(MixinEnvironment.Phase.INIT);
            Object t = MixinEnvironment.getEnvironment(MixinEnvironment.Phase.DEFAULT).getActiveTransformer();
            if (t instanceof IMixinTransformer imt) transformer = imt;
            inst.addTransformer(new IriumMixinTransformer(), true);
            log("Mixin runtime prêt (transformer=" + (transformer != null) + ")");
        } catch (Throwable t) {
            log("échec bootstrap Mixin: " + t);
        }
    }

    public static void registerMod(ClassLoader modLoader) {
        synchronized (modLoaders) { modLoaders.add(modLoader); }
    }

    public static synchronized void addConfig(String configResource) {
        try {
            Mixins.addConfiguration(configResource);
            log("config mixin enregistrée: " + configResource);
        } catch (Throwable t) {
            log("échec config mixin " + configResource + ": " + t);
        }
    }

    /** Retransforme des classes déjà chargées (attach à chaud) pour y appliquer les mixins. */
    public static void retransform(String... classNames) {
        if (instrumentation == null) return;
        List<Class<?>> targets = new ArrayList<>();
        for (String n : classNames) {
            try {
                Class<?> c = Class.forName(n, false, MixinGateway.class.getClassLoader());
                if (instrumentation.isModifiableClass(c)) targets.add(c);
            } catch (ClassNotFoundException ignored) {}
        }
        if (targets.isEmpty()) return;
        try {
            instrumentation.retransformClasses(targets.toArray(new Class<?>[0]));
            log("retransformation mixin: " + targets.size() + " classes");
        } catch (Throwable t) {
            log("retransformation échec: " + t);
        }
    }

    static byte[] transform(String name, byte[] bytes) {
        IMixinTransformer t = lazyTransformer();
        if (t == null || bytes == null) return null;
        String dotted = name.replace('/', '.');
        try {
            return t.transformClassBytes(name, dotted, bytes);
            // un mixin cassé ne doit jamais tuer le chargement de la classe
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

    static void log(String m) { System.out.println("[irium:mixin] " + m); }
}
