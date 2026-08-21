package net.fabricmc.loader.api.entrypoint;

/**
 * Adaptateur Irium — PreLaunchEntrypoint (fabric-loader).
 * M7-X3 : sodium déclare entrypoints.preLaunch (SodiumPreLaunch :
 * checkEnvironment, GraphicsAdapterProbe.findAdapters, Workarounds.init).
 * Sur vraie Fabric il tourne AVANT le jeu ; ici on le fire à l'armement
 * (fin d'installInternal early), avant toute activation.
 */
public interface PreLaunchEntrypoint {
    void onPreLaunch();
}
