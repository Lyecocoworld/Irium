package dev.irium.agent.hud;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * M5 : pont entre le code injecté par recette et les modules streamés.
 *
 * La recette injecte un appel statique à tick() à la fin d'une méthode chaude
 * du host (équivalent générique du HudRenderCallback TAIL de Fabric).
 * Les modules s'enregistrent via IriumContext.hud(Runnable).
 *
 * tick(Object) : surcharge pour les recettes dont la méthode cible expose
 * son contexte de rendu en premier paramètre (ex: Hud.extractRenderState(
 * GuiGraphicsExtractor, ...)) — le module le récupère via lastExtractor().
 *
 * Contrat de robustesse : tick() ne doit JAMAIS lever — une exception ici
 * casserait la méthode du host qui nous porte.
 */
public final class HudBridge {

    private static final List<Runnable> HOOKS = new CopyOnWriteArrayList<>();

    /** Dernier contexte de rendu vu (GuiGraphicsExtractor en 26.2) — jamais hold fort. */
    public static volatile Object lastExtractor;

    private HudBridge() {}

    public static void register(Runnable renderer) {
        if (renderer != null) HOOKS.add(renderer);
    }

    /** Vide tous les hooks (sandbox : à la déconnexion, plus rien ne tourne). */
    public static void clearAll() {
        int n = HOOKS.size();
        HOOKS.clear();
        lastExtractor = null;
        dev.irium.agent.IriumAgent.log("[hud] hooks vidés (" + n + ")");
    }

    /** Appelé par le code injecté quand la méthode cible expose un contexte. */
    public static void tick(Object graphics) {
        lastExtractor = graphics;
        tick();
    }

    /** Appelé par le code injecté — point d'entrée unique, protégé. */
    public static void tick() {
        for (Runnable r : HOOKS) {
            try {
                r.run();
            } catch (Throwable ignored) {
                // jamais casser la méthode hôte
            }
        }
    }
}
