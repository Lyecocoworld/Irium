package dev.irium.agent.module;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * M5 : recettes actives dans cette session (recues du serveur, canal
 * irium:module type 0x04). Consulté par RecipeTransformer à CHAQUE
 * définition/retransformation de classe.
 */
final class RecipeStore {

    private static final List<Recipe> RECIPES = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, Boolean> APPLIED = new ConcurrentHashMap<>();

    private RecipeStore() {}

    static void add(Recipe r) {
        if (r != null) RECIPES.add(r);
    }

    /** Première recette correspondant à cette classe (cible unique en v1). */
    static Recipe match(String className) {
        for (Recipe r : RECIPES) {
            if (r.target().equals(className)) return r;
        }
        return null;
    }

    static void markApplied(String className) {
        APPLIED.put(className, Boolean.TRUE);
    }

    /** Une recette déjà appliquée : les retransformations ultérieures sont silencieuses. */
    static boolean isApplied(String className) {
        return APPLIED.containsKey(className);
    }

    static void clearAll() {
        RECIPES.clear();
        APPLIED.clear();
    }
}
