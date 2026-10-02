package dev.aller.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Base for anything that owns settings and is saved to the profile (modules, HUD elements, client options). */
public abstract class Configurable {
    private final List<Setting<?>> settings = new ArrayList<>();

    public List<Setting<?>> settings() {
        return settings;
    }

    private String pendingSection;

    protected <S extends Setting<?>> S add(S setting) {
        setting.section = pendingSection;
        pendingSection = null;
        settings.add(setting);
        return setting;
    }

    /** Starts a titled group: the next setting added gets this heading above it. */
    protected void section(String title) {
        pendingSection = title;
    }

    protected Settings.Bool bool(String id, String name, boolean def) {
        return add(new Settings.Bool(id, name, def));
    }

    protected Settings.Num num(String id, String name, float def, float min, float max, float step) {
        return add(new Settings.Num(id, name, def, min, max, step));
    }

    protected Settings.Color color(String id, String name, int argb) {
        return add(new Settings.Color(id, name, argb, true));
    }

    protected <E extends Enum<E>> Settings.Choice<E> choice(String id, String name, E def) {
        return add(new Settings.Choice<>(id, name, def));
    }

    protected Settings.Key key(String id, String name, int def) {
        return add(new Settings.Key(id, name, def));
    }

    protected Settings.Text text(String id, String name, String def, int maxLength) {
        return add(new Settings.Text(id, name, def, maxLength));
    }

    public JsonObject save() {
        JsonObject o = new JsonObject();
        for (Setting<?> s : settings) o.add(s.id, s.save());
        return o;
    }

    public void load(JsonObject o) {
        for (Setting<?> s : settings) {
            JsonElement e = o.get(s.id);
            if (e != null && !e.isJsonNull()) s.load(e);
        }
    }

    public void resetSettings() {
        for (Setting<?> s : settings) s.reset();
    }
}
