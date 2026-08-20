package dev.irium.plugin.fabric;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Parse de fabric.mod.json via Gson (présent dans le classpath serveur). */
final class ModMetaParser {

    static final class ModMeta {
        final String id, version, name, main, client;
        ModMeta(String id, String version, String name, String main, String client) {
            this.id = id; this.version = version; this.name = name;
            this.main = main; this.client = client;
        }
    }

    private static final Gson GSON = new Gson();

    private ModMetaParser() {}

    static ModMeta parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String id = root.get("id").getAsString();
        String version = root.has("version") ? root.get("version").getAsString() : "0.0.0";
        String name = root.has("name") ? root.get("name").getAsString() : id;
        String main = null;
        String client = null;
        if (root.has("entrypoints")) {
            JsonObject eps = root.getAsJsonObject("entrypoints");
            main = firstEntrypoint(eps, "main");
            client = firstEntrypoint(eps, "client");
        }
        return new ModMeta(id, version, name, main, client);
    }

    private static String firstEntrypoint(JsonObject eps, String key) {
        if (!eps.has(key)) return null;
        com.google.gson.JsonElement el = eps.get(key);
        if (el.isJsonArray() && el.getAsJsonArray().size() > 0) {
            return el.getAsJsonArray().get(0).getAsString();
        }
        if (el.isJsonPrimitive()) return el.getAsString();
        return null;
    }
}
