package dev.irium.agent;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Irium client agent — M1 skeleton.
 *
 * Dormant by default. The premain detects whether the host process is a
 * Minecraft client; on any other Java program the agent exits immediately
 * and touches nothing. On a Minecraft client it installs an
 * observation-only ClassFileTransformer that logs class loads during the
 * startup window: this produces the real anchoring map for M4 recipes.
 */
public final class IriumAgent {

    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static volatile Instrumentation INSTR; // M5 : retransformation à chaud des recettes

    private IriumAgent() {
    }

    /** Entry point when started with -javaagent (full power window). */
    public static void premain(String args, Instrumentation inst) {
        bootstrap(args, inst, false);
    }

    /** Entry point when attached hot to a running JVM (retransform window). */
    public static void agentmain(String args, Instrumentation inst) {
        bootstrap(args, inst, true);
    }

    private static void bootstrap(String args, Instrumentation inst, boolean hotAttach) {
        if (!ACTIVE.compareAndSet(false, true)) {
            return; // already bootstrapped — ignore duplicate attach
        }
        String mode = hotAttach ? "attach" : "premain";
        boolean force = args != null && args.contains("force");
        // M7-B6 : boot:host:port -> armer les mods du cache serveur AVANT le boot MC
        boolean bootArm = args != null && args.startsWith("boot:");
        // M7-X21 "Gateway" : premain d'une instance Irium-ée -> choix AVANT boot.
        boolean gateway = args != null && args.contains("gateway");
        // M7-X21 : automatisation (tests bot) : -Dirium.gateway.choice=
        //   classic | full:host:port — court-circuite le dialog Swing.
        String autoChoice = System.getProperty("irium.gateway.choice");
        try {
            HostDetection.Result host = HostDetection.detect();
            log("[" + mode + "] irium-agent 0.7.0 bootstrapping" + (force ? " (force)" : ""));
            // M7-B8 : warmup AVANT TOUT — les classes partagées agent/client (Gson,
            // Formatter) doivent être définies par NOUS en premier, sinon course
            // avec la thread Render (ClassCircularityError, 2 crashes réels).
            ClassWarmup.warm();
            log("[" + mode + "] host detection: " + host);

            if (!host.minecraft() && !force) {
                // Dormant: not a Minecraft process. Touch nothing further.
                log("[" + mode + "] non-Minecraft process -> dormant, no transformer registered");
                return;
            }

            // M7-X21 Gateway : le choix se fait ICI, avant toute définition de
            // classe MC. Classique = retour immédiat, RIEN n'est enregistré —
            // le boot est physiquement vanilla (zéro mixin, zéro hook).
            if (gateway && !hotAttach) {
                if (autoChoice != null && !autoChoice.isBlank()) {
                    if (autoChoice.startsWith("full:")) {
                        String srv = autoChoice.substring("full:".length());
                        dev.irium.agent.module.BootChooser.forceFull(srv);
                        log("[gateway] choix auto: EXPÉRIENCE COMPLÈTE (" + srv + ")");
                    } else {
                        log("[gateway] choix auto: instance classique");
                        return; // dormant total pour ce boot
                    }
                } else {
                    dev.irium.agent.module.BootChooser.chooseBlocking();
                    if (!dev.irium.agent.module.BootChooser.wantsFullBoot()) {
                        log("[gateway] instance classique -> agent dormant pour ce boot");
                        return; // dormant total pour ce boot
                    }
                }
                // Full : armer le modset du serveur choisi AVANT toute classe MC.
                args = "boot:" + dev.irium.agent.module.BootChooser.server();
                bootArm = true;
            }

            log("[" + mode + "] Minecraft detected -> registering netty hook (M3) + observation transformer");
            SafeLog.start(); // M7-B7 : logger safe-callback AVANT tout addTransformer
            ObservationTransformer.startDrain(); // file d'observation (opt-in -Dirium.observe=1)
            INSTR = inst;
            inst.addTransformer(new NettyHook(), true);          // retransformable : marche en attach à chaud
            inst.addTransformer(new dev.irium.agent.module.RecipeTransformer(), true); // M5 : retransformation autorisée
            inst.addTransformer(new ObservationTransformer(), true);
            // M7-B : runtime Mixin embarqué (mods Fabric streamés)
            dev.irium.agent.mixin.MixinGateway.start(inst);
            // M7-B6 : armer les mods du cache serveur AVANT toute définition MC.
            // IMPORTANT : après MixinGateway.start (instrumentation non null pour
            // appendToSystemClassLoaderSearch).
            if (bootArm) {
                dev.irium.agent.module.FabricModHost.armForBoot(args);
            }
            // M7-X21 : plus de shadow-arm à l'attach — la physique M7-X19 a
            // tranché (classes déjà chargées = mixins impossible). Le Gateway
            // premain est LE chemin d'armement des mods.
            if (hotAttach) {
                // client déjà lancé : netty est déjà chargé, on le retransforme
                // pour que les PROCHAINES connexions installent le tap.
                retransformLoaded("io.netty.channel.DefaultChannelPipeline");
            }
        } catch (Throwable t) {
            // A client agent must NEVER break the host process.
            log("[" + mode + "] bootstrap failed, staying dormant: " + t);
        }
    }

    public static void log(String message) {
        System.err.println("[irium] " + message);
    }

    /** M5 : retransforme une classe déjà chargée (recette reçue après chargement). */
    public static void retransformLoaded(String fqcn) {
        Instrumentation instr = INSTR;
        if (instr == null) return;
        try {
            for (Class<?> c : instr.getAllLoadedClasses()) {
                if (c.getName().equals(fqcn)) {
                    instr.retransformClasses(c);
                    IriumAgent.log("[recette] retransformation demandée pour " + fqcn);
                    return;
                }
            }
            // pas encore chargée : le transformer l'attrapera à sa définition
        } catch (Throwable t) {
            IriumAgent.log("[recette] retransformation impossible : " + t);
        }
    }

    /** Diagnostic verbeux (labo). */
    static boolean DEBUG = Boolean.getBoolean("irium.debug"); // -Dirium.debug=true pour la trace verbeuse
}
