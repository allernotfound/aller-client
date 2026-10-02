package dev.aller.module;

import com.google.gson.JsonObject;
import dev.aller.platform.Mc;
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
    /** Set for features that may not work everywhere yet; the UI shows a badge and warns when it is switched on. */
    public String experimentalNote;
    /** Set by a module that reads its key itself (the browser opens with it); the manager then leaves it alone. */
    public boolean ownKey;
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

    protected Module experimental(String note) {
        experimentalNote = note;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        // Let go first, so a hold-style module restores whatever it changed while active.
        if (!on) setHeld(false);
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

    public enum Activation { HOLD, TOGGLE }

    /** Set for hold-style modules: whether the key must stay down or flips the effect on and off. */
    public Settings.Choice<Activation> activation;

    /** Binds a key that "Reset to defaults" and a fresh profile come back to. */
    protected Module bind(int key) {
        keybind.withDefault(key);
        return this;
    }

    /** Makes this a hold-style module (zoom, freelook): its key drives the effect instead of toggling the module. */
    protected Module holdKey(int key) {
        activation = choice("activation", "Key mode", Activation.HOLD);
        activation.describe("Hold the key, or press once to start and again to stop");
        return bind(key);
    }

    /** Hold-style modules (zoom, freelook) act while the key is down instead of toggling. */
    public boolean holdToActivate() {
        return activation != null;
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

    /** Key state fed in by the manager; {@code pressed} is true only on the frame the key went down. */
    void keyInput(boolean down, boolean pressed, boolean usable) {
        if (activation.get() == Activation.HOLD) setHeld(down);
        else if (pressed) setHeld(!held);
        else if (!usable && Mc.mc().player == null) setHeld(false);
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
        var on = o.get("enabled");
        if (on != null && on.isJsonPrimitive() && on.getAsJsonPrimitive().isBoolean()) setEnabled(on.getAsBoolean());
    }
}
