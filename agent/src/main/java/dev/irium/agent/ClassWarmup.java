package dev.irium.agent;

/**
 * M7-B8 : précharge synchrone des classes PARTAGÉES entre l'agent (thread
 * d'attach : Gson pour fabric.mod.json + configs mixin sponge) et le client
 * (thread Render : Options.load → Gson → JsonWriter.<clinit> → String.format).
 *
 * Deux threads qui font le PREMIER chargement des mêmes classes en parallèle
 * → ClassCircularityError → crash du client à l'init (2 crashes réels :
 * 19:57:26 et 20:02:49, signature identique Formatter$FormatSpecifierParser).
 *
 * L'attach arrive ~2s après le start du process ; MC n'atteint Options.load
 * que ~7s plus tard : un warmup de <100ms au tout début de agentmain gagne
 * la course avec une marge confortable. Ensuite, toutes les utilisations
 * concurrentes portent sur des classes DÉJÀ définies+initialisées = sûres.
 *
 * DOIT être appelé avant tout addTransformer / MixinBootstrap / Gson agent.
 */
final class ClassWarmup {

    private ClassWarmup() {}

    static void warm() {
        // 1. famille java.util.Formatter complète (via un vrai format)
        try { String.format("%05.1f", 1.0); } catch (Throwable ignored) {}
        // 2. Gson partagé (celui du classpath MC — le loader APP résout les
        //    libs MC avant le jar agent appendé)
        ClassLoader app = ClassLoader.getSystemClassLoader();
        String[] gson = {
                "com.google.gson.JsonParser",
                "com.google.gson.JsonElement",
                "com.google.gson.JsonObject",
                "com.google.gson.Gson",
                "com.google.gson.stream.JsonWriter",
        };
        for (String n : gson) {
            try { Class.forName(n, true, app); } catch (Throwable ignored) {}
        }
        // 3. jul (sponge loggue via java.util.logging pendant le parse config)
        try { Class.forName("java.util.logging.Logger", true, null); } catch (Throwable ignored) {}
        try { Class.forName("java.util.logging.Level", true, null); } catch (Throwable ignored) {}
    }
}
