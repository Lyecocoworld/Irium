package dev.irium.agent.module;

/**
 * API offerte à un module streamé — volontairement minuscule en v1.
 * Chaque appel est safe : l'exécution est rapatriée sur l'eventLoop du canal.
 */
public interface IriumContext {

    /** Log visible côté client (stderr, préfixe irium). */
    void log(String message);

    /** Émet un événement vers le serveur (canal irium:module, type EVENT). */
    void emit(String tag, String data);

    /**
     * M5 : enregistre un renderer appelé à chaque tick de la méthode hookée
     * par la recette (équivalent générique du HudRenderCallback de Fabric).
     * Retiré automatiquement à la fermeture de session.
     */
    void hud(Runnable renderer);
}
