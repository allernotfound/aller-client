package dev.aller.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aller.AllerClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Actions the player adds: each sends one chat line or one command when run from the launcher.
 * They have no key of their own and send nothing by themselves, so this is a shortcut for typing,
 * not a macro.
 */
public final class CustomActions {
    public static final int NAME_MAX = 32, MESSAGE_MAX = 256;

    public static final class Custom {
        public String name = "";
        public String message = "";
    }

    private static final List<Custom> list = new ArrayList<>();
    private static boolean loaded;

    private CustomActions() {}

    public static List<Custom> all() {
        if (!loaded) {
            loaded = true;
            JsonElement saved = AllerClient.config().extra("custom_actions");
            if (saved != null && saved.isJsonArray()) {
                for (JsonElement e : saved.getAsJsonArray()) {
                    try {
                        JsonObject o = e.getAsJsonObject();
                        Custom c = new Custom();
                        c.name = clean(o.get("name").getAsString(), NAME_MAX);
                        c.message = clean(o.get("message").getAsString(), MESSAGE_MAX);
                        if (!c.name.isEmpty() && !c.message.isEmpty() && find(c.name) == null) list.add(c);
                    } catch (RuntimeException ignored) {
                        // skip a malformed entry rather than losing the rest
                    }
                }
            }
        }
        return list;
    }

    public static void save() {
        JsonArray arr = new JsonArray();
        for (Custom c : all()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", c.name);
            o.addProperty("message", c.message);
            arr.add(o);
        }
        AllerClient.config().setExtra("custom_actions", arr);
    }

    public static Custom find(String name) {
        for (Custom c : list) if (c.name.equalsIgnoreCase(name.trim())) return c;
        return null;
    }

    /** Adds an action; a name already taken gets a number after it. */
    public static Custom add(String name, String message) {
        Custom c = new Custom();
        c.name = free(clean(name, NAME_MAX), null);
        c.message = clean(message, MESSAGE_MAX);
        all().add(c);
        save();
        return c;
    }

    public static void rename(Custom c, String name) {
        String cleaned = clean(name, NAME_MAX);
        if (cleaned.isEmpty()) return;
        String old = key(c);
        c.name = free(cleaned, c);
        if (History.pinned(old)) {
            History.togglePin(old);
            History.togglePin(key(c));
        }
        History.forget(old);
        save();
    }

    public static void remove(Custom c) {
        all().remove(c);
        History.forget(key(c));
        save();
    }

    public static String key(Custom c) {
        return "custom." + c.name.toLowerCase();
    }

    private static String free(String name, Custom self) {
        all();
        String candidate = name;
        for (int n = 2; ; n++) {
            Custom taken = find(candidate);
            if (taken == null || taken == self) return candidate;
            candidate = name + " " + n;
        }
    }

    /** One line, no control characters: a message is exactly what would be typed into chat. */
    public static String clean(String text, int max) {
        String s = text.replaceAll("[\\p{Cntrl}§]", " ").strip();
        return s.length() > max ? s.substring(0, max).strip() : s;
    }
}
