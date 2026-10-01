package dev.aller.module;

import com.google.gson.JsonObject;
import dev.aller.setting.Configurable;
import dev.aller.setting.Settings;

/** A toggleable feature. Subclasses add settings in their constructor and override the hooks they need. */
public abstract class Module extends Configurable {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final Settings.Key keybind;
    /** Extra search terms for the palette ("fps" should find "Frame rate"). */
    public String[] keywords = {};
    /** Set for features some servers restrict; the UI shows a badge and a one-time warning. */
    public String fairPlayNote;
    private boolean enabled;

    protected Module(String id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = key("keybind", "Keybind", Settings.Key.NONE);
    }

    protected Module keywords(String... words) {
        keywords = words;
        return this;
    }

    protected Module restricted(String note) {
        fairPlayNote = note;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        if (on) onEnable();
        else onDisable();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** Whether a fresh profile starts with this module on. */
    public boolean enabledByDefault;

    protected Module onByDefault() {
        enabledByDefault = true;
        enabled = true;
        return this;
    }

    public void resetToDefaults() {
        resetSettings();
        setEnabled(enabledByDefault);
    }

    /** Hold-style modules (zoom, freelook) act while the key is down instead of toggling. */
    public boolean holdToActivate() {
        return false;
    }

    private boolean held;

    /** For hold-style modules: whether the bound key is down right now (and the module is on). */
    public boolean held() {
        return held && enabled;
    }

    void setHeld(boolean down) {
        if (down == held) return;
        held = down;
        if (enabled) onHeldChanged(down);
    }

    protected void onHeldChanged(boolean down) {}

    protected void onEnable() {}

    protected void onDisable() {}

    /** Called every client tick while enabled and in a world. */
    public void tick() {}

    @Override
    public JsonObject save() {
        JsonObject o = super.save();
        o.addProperty("enabled", enabled);
        return o;
    }

    @Override
    public void load(JsonObject o) {
        super.load(o);
        if (o.has("enabled")) setEnabled(o.get("enabled").getAsBoolean());
    }
}
