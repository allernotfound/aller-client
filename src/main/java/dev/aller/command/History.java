package dev.aller.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aller.AllerClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the launcher remembers between sessions: recent and pinned commands, and how often each is used. */
public final class History {
    private static final int RECENT = 6, PINS = 9;
    private static final List<String> recent = new ArrayList<>();
    private static final List<String> pins = new ArrayList<>();
    private static final Map<String, Integer> uses = new HashMap<>();
    private static boolean loaded;

    private History() {}

    private static void load() {
        if (loaded) return;
        loaded = true;
        JsonElement saved = AllerClient.config().extra("launcher");
        if (saved == null || !saved.isJsonObject()) return;
        JsonObject o = saved.getAsJsonObject();
        strings(o.get("recent"), recent, RECENT);
        strings(o.get("pins"), pins, PINS);
        if (o.has("uses") && o.get("uses").isJsonObject()) {
            for (var e : o.getAsJsonObject("uses").entrySet()) {
                try {
                    uses.put(e.getKey(), e.getValue().getAsInt());
                } catch (RuntimeException ignored) {
                    // skip a count that is not a number
                }
            }
        }
    }

    private static void strings(JsonElement json, List<String> into, int limit) {
        if (json == null || !json.isJsonArray()) return;
        for (JsonElement e : json.getAsJsonArray()) {
            if (into.size() < limit && e.isJsonPrimitive() && !into.contains(e.getAsString())) into.add(e.getAsString());
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        JsonArray r = new JsonArray(), p = new JsonArray();
        recent.forEach(r::add);
        pins.forEach(p::add);
        JsonObject u = new JsonObject();
        uses.forEach(u::addProperty);
        o.add("recent", r);
        o.add("pins", p);
        o.add("uses", u);
        AllerClient.config().setExtra("launcher", o);
    }

    /** Notes that a command was run. */
    public static void used(String key) {
        if (key == null) return;
        load();
        recent.remove(key);
        recent.add(0, key);
        while (recent.size() > RECENT) recent.remove(recent.size() - 1);
        // A value ("fov=90") counts towards its command.
        int eq = key.indexOf('=');
        uses.merge(eq > 0 ? key.substring(0, eq) : key, 1, Integer::sum);
        // Counts only rank results, so the long tail of things used once long ago can go.
        if (uses.size() > 200) uses.values().removeIf(n -> n <= 1);
        save();
    }

    public static List<String> recent() {
        load();
        return recent;
    }

    public static List<String> pins() {
        load();
        return pins;
    }

    public static int uses(String key) {
        load();
        return key == null ? 0 : uses.getOrDefault(key, 0);
    }

    public static boolean pinned(String key) {
        load();
        return key != null && pins.contains(key);
    }

    /** @return false if there was no room for another pin */
    public static boolean togglePin(String key) {
        load();
        if (!pins.remove(key)) {
            if (pins.size() >= PINS) return false;
            pins.add(key);
        }
        save();
        return true;
    }

    /** Drops a key that no longer names anything (a deleted custom action). */
    public static void forget(String key) {
        load();
        if (recent.remove(key) | pins.remove(key) | uses.remove(key) != null) save();
    }
}
