package dev.irium.agent.module;

/**
 * Contrat d'un module Irium streamé par un serveur.
 *
 * Un module est une classe compilée contre cette interface (et seulement
 * contre l'API agent — jamais contre le client). Le serveur pousse ses
 * octets, l'agent les définit dans un ModuleClassLoader dédié puis appelle
 * onEnable(). Tout ce que le serveur ajoute disparaît à la déconnexion
 * (onDisable + classloader abandonné au GC).
 */
public interface IriumModule {

    /** Appelé après chargement réussi, sur l'eventLoop netty : doit être rapide. */
    default void onEnable(IriumContext ctx) {}

    /** Appelé à la fermeture de la session (déconnexion). */
    default void onDisable() {}
}
