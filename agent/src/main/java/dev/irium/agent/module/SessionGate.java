package dev.irium.agent.module;

/**
 * M7-X20 : porte de session Irium (shadow-arm).
 *
 * Les mods streamés sont cuits dans la JVM dès l'attach (course gagnée =
 * zéro relance), mais leurs SURFACES ne doivent s'exprimer que pendant une
 * session Irium — sinon le titre, le singleplayer et les autres serveurs
 * sont "parasités" (bouton Mods, minimap, handshake mods...).
 *
 *   begin() : trame MODSET reçue -> ce serveur EST Irium -> surfaces actives.
 *   end()   : canal fermé -> surfaces muettes, retour vanilla à l'œil.
 *
 * Un serveur non-Irium n'émet jamais MODSET -> la porte reste fermée ->
 * les mods armés restent invisibles et silencieux dessus.
 */
public final class SessionGate {

    private static volatile boolean active;
    /** Vrai dès qu'un MODSET a été vu sur CE canal — la gate PEUT s'ouvrir (payloads bufferisés en attendant). */
    private static volatile boolean modsetSeen;

    private SessionGate() {}

    /** Le serveur courant a prouvé qu'il est Irium (MODSET reçu). */
    public static void begin() {
        modsetSeen = true;
        if (!active) {
            active = true;
            dev.irium.agent.IriumAgent.log("[session] Irium confirmé -> surfaces mods ACTIVES");
            dev.irium.agent.hud.FabricPipBridge.onSessionBegin();
        }
    }

    /** La gate PEUT-elle s'ouvrir (MODSET vu) même si elle ne l'est pas encore ? */
    public static boolean mayOpen() {
        return modsetSeen;
    }

    /** Fin de session (canal fermé) : retour vanilla à l'œil. */
    public static void end() {
        modsetSeen = false;
        if (active) {
            active = false;
            dev.irium.agent.IriumAgent.log("[session] déconnexion -> surfaces mods MUETTES (retour vanilla)");
            dev.irium.agent.hud.FabricPipBridge.onSessionEnd();
        }
    }

    /** Une session Irium est-elle active ? (gate de toutes les surfaces mods) */
    public static boolean isActive() {
        return active;
    }
}
