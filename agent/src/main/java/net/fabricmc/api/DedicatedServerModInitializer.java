package net.fabricmc.api;

/**
 * Surface Irium : entrypoint "server" de fabric.mod.json (ex. Xaero déclare
 * la même classe en client ET server). Sur le host client Irium, le host
 * n'appelle que les entrypoints client — la classe doit juste exister pour
 * le linkage (sinon NoClassDefFoundError à la définition de la classe du mod).
 */
public interface DedicatedServerModInitializer {

    default void onInitializeServer() {}
}
