package dev.aller;

import dev.aller.setting.Configurable;
import dev.aller.setting.Setting;
import dev.aller.setting.Settings;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Client-wide preferences (as opposed to per-module settings). Declaration order is the order in
 * the UI; each initialiser block opens a titled group there. They are shown as two pages: the
 * interface settings ({@link #ui()}) and the rest ({@link #client()}).
 */
public final class ClientOptions extends Configurable {
    /** The face Aller's own text is set in. */
    public enum Typeface { INTER, MINECRAFT }

    /** Which rounded shapes get stepped, pixel-art corners. */
    public enum Pixelate { OFF, BUTTONS, EVERYTHING }

    /** What an Aller menu does to whatever is behind it. */
    public enum Background { BLUR, DARKEN, SOLID, NONE }

    // ---- UI settings -----------------------------------------------------------------------------

    { section("Look"); }
    public final Settings.Color accent = add(new Settings.Color("accent", "Accent colour", 0xFF8B5CF6, false));
    public final Settings.Choice<Typeface> typeface = choice("typeface", "Font", Typeface.INTER)
            .describe("Minecraft sets Aller Client's own text in the game's pixel font");
    public final Settings.Choice<Pixelate> pixelate = choice("pixelate", "Pixelated corners", Pixelate.OFF)
            .describe("Stepped, pixel-art corners in place of smooth ones");
    public final Settings.Choice<Background> background = choice("background", "Behind menus", Background.BLUR)
            .describe("Blur also needs Minecraft's own menu background blur to be above zero");
    public final Settings.Num backdropIntensity = num("backdrop_intensity", "Menu backdrop intensity", 1f, 0f, 1.5f, 0.05f)
            .format(v -> Math.round(v * 100) + "%");
    public final Settings.Num backdropCell = num("backdrop_cell", "Menu backdrop glyph size", 3f, 2f, 10f, 0.5f).suffix("px");

    { section("Size"); }
    public final Settings.Num uiScale = num("ui_scale", "Menu size", 1f, 0.7f, 1.5f, 0.05f).suffix("x")
            .describe("Scales every Aller Client menu, panel and all");
    public final Settings.Num paletteZoom = zoom("palette_zoom", "Mod palette zoom")
            .describe("Shrinks what is inside the palette so more mods fit. Ctrl and scroll does it too");
    public final Settings.Num launcherZoom = zoom("launcher_zoom", "Launcher zoom")
            .describe("Shrinks what is inside the launcher so more commands fit. Ctrl and scroll does it too");
    // A new id: sizes saved under the old one were chosen against a default that was far too large.
    public final Settings.Num hudScale = num("hud_size", "HUD size", 0.8f, 0.5f, 2f, 0.05f).suffix("x")
            .describe("Scales every HUD element together, on top of each one's own scale");

    { section("Motion"); }
    public final Settings.Num animationSpeed = num("animation_speed", "Animation speed", 1f, 0.5f, 2f, 0.1f).suffix("x");
    public final Settings.Bool reduceMotion = bool("reduce_motion", "Reduce motion", false)
            .describe("Skip springs and slides: things change state at once");

    { section("Screens"); }
    public final Settings.Bool customMainMenu = bool("custom_main_menu", "Aller Client main menu", true);
    public final Settings.Bool customPauseMenu = bool("custom_pause_menu", "Aller Client pause menu", true);
    public final Settings.Bool customLoading = bool("custom_loading", "Aller Client loading screens", true)
            .describe("The startup splash and the connecting and world loading screens");
    public final Settings.Bool gridView = bool("grid_view", "Show mods as a grid", false);

    { section("Feedback"); }
    public final Settings.Bool toasts = bool("toasts", "Toggle notifications", true)
            .describe("A toast whenever a mod is switched on or off");
    public final Settings.Num uiVolume = num("ui_volume", "Interface sounds", 100f, 0f, 100f, 5f)
            .format(v -> v <= 0 ? "Off" : Math.round(v) + "%");
    public final Settings.Num hudSnap = num("hud_snap", "HUD editor snap distance", 6f, 0f, 16f, 1f)
            .format(v -> v <= 0 ? "Off" : Math.round(v) + "px");

    { section("Minecraft menus (experimental)"); }
    // A new id here too, so the switch starts off for everyone: it was on by default before it was ready.
    public final Settings.Bool restyleMenus = bool("skin_menus", "Restyle Minecraft's menus", false)
            .describe("Experimental. Aller Client's backdrop, panels, buttons and type on the menus chosen below");
    public final Settings.Bool restyleOptions = menu("restyle_options", "Options");
    public final Settings.Bool restyleVideo = menu("restyle_video", "Video settings")
            .describe("Sodium's video settings too, when it is installed");
    public final Settings.Bool restyleWorlds = menu("restyle_worlds", "Singleplayer")
            .describe("The world list and the create and edit world screens");
    public final Settings.Bool restyleMultiplayer = menu("restyle_multiplayer", "Multiplayer")
            .describe("The server list and the add and direct connect screens");
    public final Settings.Bool restylePacks = menu("restyle_packs", "Resource and data packs");
    public final Settings.Bool restyleShaders = menu("restyle_shaders", "Shader packs")
            .describe("Iris's shader pack screens, when it is installed");
    public final Settings.Bool restyleRealms = menu("restyle_realms", "Realms");
    public final Settings.Bool restyleModList = menu("restyle_mod_list", "Installed mods")
            .describe("Mod Menu's list, when it is installed");
    public final Settings.Bool restyleOther = menu("restyle_other", "Other Minecraft menus")
            .describe("Statistics, advancements, confirmations, disconnect messages and the rest");
    public final Settings.Bool restyleOtherMods = menu("restyle_other_mods", "Menus from other mods")
            .describe("Switch off if another mod's screen looks wrong");

    /** Everything above is the look of the interface; everything below is how the client behaves. */
    private final int uiCount = settings().size();

    // ---- client settings -------------------------------------------------------------------------

    { section("Keys"); }
    public final Settings.Key menuKey = key("menu_key", "Open palette", GLFW.GLFW_KEY_RIGHT_SHIFT);
    public final Settings.Key launcherKey = key("launcher_key", "Open launcher",
            Settings.Key.pack(GLFW.GLFW_KEY_K, GLFW.GLFW_MOD_CONTROL)).chord()
            .describe("Quick actions from anywhere. With Ctrl or Alt in it, it works on menus too");
    public final Settings.Key hudEditorKey = key("hud_editor_key", "Open HUD editor", Settings.Key.NONE);

    { section("Behaviour"); }
    public final Settings.Bool fairPlayWarnings = bool("fair_play_warnings", "Fair-play warnings", true)
            .describe("Warn once per session when enabling a mod some servers restrict");
    public final Settings.Bool autoProfiles = bool("auto_profiles", "Switch profiles automatically by rule", true);

    /** Set once the saved options have been read, so loading them does not count as the player changing them. */
    private boolean loaded;

    /** The interface settings: look, sizes, motion and which screens Aller draws. */
    public List<Setting<?>> ui() {
        return settings().subList(0, uiCount);
    }

    /** Keys and behaviour. */
    public List<Setting<?>> client() {
        return settings().subList(uiCount, settings().size());
    }

    private Settings.Num zoom(String id, String name) {
        return num(id, name, 1f, 0.6f, 1.2f, 0.05f).format(v -> Math.round(v * 100) + "%");
    }

    /** One per-menu switch under {@link #restyleMenus}. */
    private Settings.Bool menu(String id, String name) {
        return bool(id, name, true).visibleWhen(() -> restyleMenus.get());
    }

    public ClientOptions() {
        animationSpeed.onChange(v -> applyMotion());
        reduceMotion.onChange(v -> applyMotion());
        restyleMenus.onChange(on -> {
            if (on && loaded) Toasts.warn("Menu restyling is experimental", "It does not work well yet. Switch it off if a menu looks wrong.");
        });
    }

    /** Called once the saved options are in, and again whenever a motion setting changes. */
    public void applyMotion() {
        loaded = true;
        Motion.configure(animationSpeed.get(), reduceMotion.get());
    }
}
