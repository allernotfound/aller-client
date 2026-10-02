package dev.aller;

import dev.aller.setting.Configurable;
import dev.aller.setting.Settings;
import dev.aller.ui.anim.Motion;
import org.lwjgl.glfw.GLFW;

/**
 * Client-wide preferences (as opposed to per-module settings). Declaration order is the order in
 * the UI; each initialiser block opens a titled group there.
 */
public final class ClientOptions extends Configurable {
    { section("Look"); }
    public final Settings.Color accent = add(new Settings.Color("accent", "Accent colour", 0xFF8B5CF6, false));
    public final Settings.Num uiScale = num("ui_scale", "Menu and palette size", 1f, 0.7f, 1.5f, 0.05f).suffix("x");
    public final Settings.Num hudScale = num("hud_scale", "HUD size", 1f, 0.5f, 2f, 0.05f).suffix("x")
            .describe("Scales every HUD element together, on top of each one's own scale");
    public final Settings.Bool blur = bool("blur", "Blur behind menus", true)
            .describe("Also needs Minecraft's own menu background blur to be above zero");
    public final Settings.Num backdropIntensity = num("backdrop_intensity", "Menu backdrop intensity", 1f, 0f, 1.5f, 0.05f)
            .format(v -> Math.round(v * 100) + "%");
    public final Settings.Num backdropCell = num("backdrop_cell", "Menu backdrop glyph size", 3f, 2f, 10f, 0.5f).suffix("px");

    { section("Motion"); }
    public final Settings.Num animationSpeed = num("animation_speed", "Animation speed", 1f, 0.5f, 2f, 0.1f).suffix("x");
    public final Settings.Bool reduceMotion = bool("reduce_motion", "Reduce motion", false)
            .describe("Skip springs and slides: things change state at once");

    { section("Screens"); }
    public final Settings.Bool customMainMenu = bool("custom_main_menu", "Aller main menu", true);
    public final Settings.Bool customPauseMenu = bool("custom_pause_menu", "Aller pause menu", true);
    public final Settings.Bool customLoading = bool("custom_loading", "Aller loading screens", true)
            .describe("The startup splash and the connecting and world loading screens");
    public final Settings.Bool gridView = bool("grid_view", "Show mods as a grid", false);

    { section("Keys"); }
    public final Settings.Key menuKey = key("menu_key", "Open palette", GLFW.GLFW_KEY_RIGHT_SHIFT);
    public final Settings.Key hudEditorKey = key("hud_editor_key", "Open HUD editor", Settings.Key.NONE);

    { section("Behaviour"); }
    public final Settings.Bool toasts = bool("toasts", "Toggle notifications", true)
            .describe("A toast whenever a mod is switched on or off");
    public final Settings.Bool fairPlayWarnings = bool("fair_play_warnings", "Fair-play warnings", true)
            .describe("Warn once per session when enabling a mod some servers restrict");
    public final Settings.Num uiVolume = num("ui_volume", "Interface sounds", 100f, 0f, 100f, 5f)
            .format(v -> v <= 0 ? "Off" : Math.round(v) + "%");
    public final Settings.Bool autoProfiles = bool("auto_profiles", "Switch profiles automatically by rule", true);
    public final Settings.Num hudSnap = num("hud_snap", "HUD editor snap distance", 6f, 0f, 16f, 1f)
            .format(v -> v <= 0 ? "Off" : Math.round(v) + "px");

    public ClientOptions() {
        animationSpeed.onChange(v -> applyMotion());
        reduceMotion.onChange(v -> applyMotion());
    }

    public void applyMotion() {
        Motion.configure(animationSpeed.get(), reduceMotion.get());
    }
}
