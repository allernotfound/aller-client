package dev.aller;

import dev.aller.setting.Configurable;
import dev.aller.setting.Settings;
import dev.aller.ui.anim.Motion;
import org.lwjgl.glfw.GLFW;

/** Client-wide preferences (as opposed to per-module settings). */
public final class ClientOptions extends Configurable {
    public final Settings.Color accent = add(new Settings.Color("accent", "Accent colour", 0xFF8B5CF6, false));
    public final Settings.Key menuKey = key("menu_key", "Open palette", GLFW.GLFW_KEY_RIGHT_SHIFT);
    public final Settings.Key hudEditorKey = key("hud_editor_key", "Open HUD editor", Settings.Key.NONE);
    public final Settings.Num animationSpeed = num("animation_speed", "Animation speed", 1f, 0.5f, 2f, 0.1f).suffix("x");
    public final Settings.Bool reduceMotion = bool("reduce_motion", "Reduce motion", false);
    public final Settings.Bool blur = bool("blur", "Blur behind menus", true);
    public final Settings.Bool customMainMenu = bool("custom_main_menu", "Aller main menu", true);
    public final Settings.Bool customPauseMenu = bool("custom_pause_menu", "Aller pause menu", true);
    public final Settings.Bool customLoading = bool("custom_loading", "Aller loading screens", true);
    public final Settings.Num backdropIntensity = num("backdrop_intensity", "Menu backdrop intensity", 1f, 0f, 1.5f, 0.05f);
    public final Settings.Num backdropCell = num("backdrop_cell", "Menu backdrop glyph size", 3f, 2f, 10f, 0.5f).suffix("px");
    public final Settings.Bool autoProfiles = bool("auto_profiles", "Switch profiles automatically by rule", true);
    public final Settings.Bool toasts = bool("toasts", "Toggle notifications", true);
    public final Settings.Bool fairPlayWarnings = bool("fair_play_warnings", "Fair-play warnings", true);
    public final Settings.Num hudSnap = num("hud_snap", "HUD editor snap distance", 6f, 0f, 16f, 1f).suffix("px");

    public ClientOptions() {
        animationSpeed.onChange(v -> applyMotion());
        reduceMotion.onChange(v -> applyMotion());
    }

    public void applyMotion() {
        Motion.configure(animationSpeed.get(), reduceMotion.get());
    }
}
