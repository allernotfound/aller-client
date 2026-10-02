package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.MenuKind;
import dev.aller.platform.ScreenHost;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;

/**
 * Restyles the menus Aller does not replace outright: options, world and server lists, packs, and
 * other mods' screens. Those screens keep their own layout and logic. What changes is how their
 * pieces are drawn: the hooks in {@code GuiGraphicsMixin} hand every widget sprite, menu texture
 * and flat rectangle here before vanilla draws it, and this class paints the Aller equivalent in
 * its place. Text is redrawn in Inter by {@code VanillaText}, which also swaps the font metrics so
 * centring and wrapping still come out right.
 *
 * <p>Working at the draw call rather than per screen is what lets one piece of code cover every
 * vanilla menu and anything a mod builds from vanilla widgets.
 */
public final class MenuSkin {
    /** The groups a player can switch restyling on and off for. */
    public enum Menu { OPTIONS, VIDEO, WORLDS, MULTIPLAYER, PACKS, SHADERS, REALMS, MOD_LIST, OTHER, OTHER_MODS }

    /** Size vanilla text is redrawn at: Inter's capitals then stand as tall as Minecraft's own. */
    public static final float TEXT_SIZE = 9f;
    public static final float TITLE_SIZE = 11.5f;

    private static final int FIELD = 0xCC0A0910, BASE = 0xB80D0B14;
    private static final int BUTTON = 1, FIELD_FOCUS = 2, CHECK = 3, TAB = 4, HANDLE = 5, FLAT = 6;

    private static Screen screen;
    private static boolean active, flat, drawing, selection;
    private static String title = "";

    private MenuSkin() {}

    // ---- which screens ---------------------------------------------------------------------------

    public static boolean enabled(Menu menu) {
        var o = AllerClient.options();
        if (!o.restyleMenus.get()) return false;
        return switch (menu) {
            case OPTIONS -> o.restyleOptions.get();
            case VIDEO -> o.restyleVideo.get();
            case WORLDS -> o.restyleWorlds.get();
            case MULTIPLAYER -> o.restyleMultiplayer.get();
            case PACKS -> o.restylePacks.get();
            case SHADERS -> o.restyleShaders.get();
            case REALMS -> o.restyleRealms.get();
            case MOD_LIST -> o.restyleModList.get();
            case OTHER -> o.restyleOther.get();
            case OTHER_MODS -> o.restyleOtherMods.get();
        };
    }

    /**
     * Notes which screen is (about to be) showing. Called before a screen lays itself out, because
     * from then on its text has to be measured in Inter. The switches can only change while one of
     * Aller's own screens is up, so comparing the instance is enough.
     */
    public static void sync(Screen now) {
        // A menu kept on show beneath one of Aller's screens is still the menu being restyled.
        if (now instanceof ScreenHost host && host.screen.vanillaUnderlay() != null) now = host.screen.vanillaUnderlay();
        if (now == screen) return;
        screen = now;
        Menu menu = MenuKind.of(now);
        active = menu != null && enabled(menu);
        flat = active && MenuKind.flat(now);
        title = active ? now.getTitle().getString() : "";
        anims.clear();
        sliderW = 0;
    }

    /** Forgets the screen in hand, so the next one re-reads the switches (the launcher can change them over a menu). */
    public static void invalidate() {
        screen = null;
        active = flat = false;
        title = "";
    }

    /** Whether the current screen is restyled, so its text is measured and drawn in Inter. */
    public static boolean active() {
        return active;
    }

    /** True only while the restyled screen itself is drawing (not the HUD behind it or toasts above). */
    public static boolean drawing() {
        return drawing;
    }

    public static String title() {
        return title;
    }

    public static void frame() {
        drawing = false;
        selection = false;
        sync(Mc.screen());
        float now = Motion.time();
        if (now - pruned > 2f) {
            pruned = now;
            anims.values().removeIf(a -> now - a.seen > 1.5f);
        }
    }

    public static void begin(Screen rendering) {
        drawing = active && rendering == screen;
    }

    public static void end() {
        drawing = false;
        selection = false;
    }

    // ---- background ------------------------------------------------------------------------------

    /** Stands in for the panorama: outside a world there is nothing to blur, so the menu backdrop shows instead. */
    public static boolean backdrop(Canvas c) {
        if (!drawing) return false;
        Theme.scene(c, c.width(), c.height());
        return true;
    }

    /** @return true to skip vanilla's blur: the backdrop must stay sharp, and Aller's own blur switch applies in a world */
    public static boolean skipBlur() {
        return drawing && (Mc.mc().level == null || AllerClient.options().background.get() != dev.aller.ClientOptions.Background.BLUR);
    }

    /** Vanilla's tiled menu textures: the screen dim, list backgrounds and the separators around them. */
    public static boolean texture(Canvas c, String namespace, String path, float x0, float y0, float x1, float y1) {
        if (!drawing || !path.startsWith("textures/gui/") || !namespace.equals("minecraft")) return false;
        float x = Math.min(x0, x1), y = Math.min(y0, y1), w = Math.abs(x1 - x0), h = Math.abs(y1 - y0);
        switch (path.substring(13)) {
            case "menu_background.png" -> {
                // Only the whole-screen dim; partial ones are strips behind headers, which the panels replace.
                if (w >= c.width() - 1 && h >= c.height() - 1) {
                    c.rect(x, y, w, h, 0, 0x5207060B);
                    c.gradientV(x, y, w, h * 0.3f, 0, 0x8007060B, 0x0007060B);
                    c.gradientV(x, y + h * 0.7f, w, h * 0.3f, 0, 0x0007060B, 0x8007060B);
                }
            }
            case "inworld_menu_background.png" -> {
                if (w >= c.width() - 1 && h >= c.height() - 1) c.rect(x, y, w, h, 0, 0x73050409);
            }
            case "menu_list_background.png", "inworld_menu_list_background.png" -> {
                // A list that spans the window gets a margin so it reads as a floating panel.
                float inset = w >= c.width() - 1 ? 6 : 0;
                surface(c, x + inset, y, w - inset * 2, h, Theme.R_LG);
            }
            case "header_separator.png", "footer_separator.png", "inworld_header_separator.png",
                 "inworld_footer_separator.png", "tab_header_background.png" -> {}
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A quiet panel for content areas: lighter than {@link Theme#panel} and without the drop shadow. */
    public static void surface(Canvas c, float x, float y, float w, float h, float radius) {
        c.rect(x, y, w, h, radius, 0x990C0B13);
        c.gradientV(x, y, w, h, radius, 0x0CFFFFFF, 0x00FFFFFF);
        c.stroke(x, y, w, h, radius, 1, Theme.BORDER);
    }

    // ---- widgets ---------------------------------------------------------------------------------

    /** A vanilla GUI sprite about to be drawn. @return true if Aller drew its own version instead */
    public static boolean sprite(Canvas c, String namespace, String path, float x, float y, float w, float h, int color) {
        if (!drawing || !namespace.equals("minecraft")) return false;
        float alpha = (color >>> 24) / 255f;
        if (alpha <= 0.004f) return false;
        c.pushAlpha(alpha);
        boolean handled = true;
        switch (path) {
            case "widget/button" -> button(c, x, y, w, h, false, true);
            case "widget/button_highlighted" -> button(c, x, y, w, h, true, true);
            case "widget/button_disabled" -> button(c, x, y, w, h, false, false);
            case "widget/slider" -> sliderTrack(c, x, y, w, h, false);
            case "widget/slider_highlighted" -> sliderTrack(c, x, y, w, h, true);
            case "widget/slider_handle" -> sliderHandle(c, x, y, w, h, false);
            case "widget/slider_handle_highlighted" -> sliderHandle(c, x, y, w, h, true);
            case "widget/text_field" -> field(c, x, y, w, h, false);
            case "widget/text_field_highlighted" -> field(c, x, y, w, h, true);
            case "widget/checkbox" -> checkbox(c, x, y, w, h, false, false);
            case "widget/checkbox_highlighted" -> checkbox(c, x, y, w, h, false, true);
            case "widget/checkbox_selected" -> checkbox(c, x, y, w, h, true, false);
            case "widget/checkbox_selected_highlighted" -> checkbox(c, x, y, w, h, true, true);
            case "widget/tab" -> tab(c, x, y, w, h, false, false);
            case "widget/tab_highlighted" -> tab(c, x, y, w, h, false, true);
            case "widget/tab_selected" -> tab(c, x, y, w, h, true, false);
            case "widget/tab_selected_highlighted" -> tab(c, x, y, w, h, true, true);
            case "widget/scroller_background" -> c.rect(x + w / 2 - 1.5f, y + 2, 3, h - 4, 1.5f, 0x12FFFFFF);
            case "widget/scroller" -> c.rect(x + w / 2 - 1.5f, y + 2, 3, h - 4, 1.5f, 0x5CFFFFFF);
            case "tooltip/background" -> {
                // The sprite carries a 9px transparent margin around the box.
                float px = x + 9, py = y + 9, pw = w - 18, ph = h - 18;
                c.shadow(px, py + 3, pw, ph, Theme.R_SM, 12, 0x73000000);
                c.rect(px, py, pw, ph, Theme.R_SM, 0xF2100E18);
                c.stroke(px, py, pw, ph, Theme.R_SM, 1, Theme.BORDER_STRONG);
            }
            case "tooltip/frame" -> {}
            case "popup/background" -> Theme.panel(c, x + 4, y + 4, w - 8, h - 8, Theme.R_LG);
            default -> handled = false;
        }
        c.popAlpha();
        return handled;
    }

    /** The glass button, as {@code ui.widget.Button} draws it; also used for mods that draw their own. */
    public static void button(Canvas c, float x, float y, float w, float h, boolean hot, boolean enabled) {
        float hv = enabled ? anim(BUTTON, x, y, w, h, hot) : 0;
        float r = Math.min(Theme.R_MD, h / 2);
        c.pushAlpha(enabled ? 1f : 0.5f);
        c.pixel(true);
        c.rect(x, y, w, h, r, BASE);
        c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
        c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hv));
        c.pixel(false);
        c.popAlpha();
    }

    // Vanilla draws a slider as a track sprite followed by its handle; the track is remembered so
    // the handle can fill the part of it that lies to its left.
    private static float sliderX, sliderY, sliderW, sliderH;

    private static void sliderTrack(Canvas c, float x, float y, float w, float h, boolean focused) {
        float r = Math.min(Theme.R_MD, h / 2);
        c.rect(x, y, w, h, r, BASE);
        c.rect(x, y, w, h, r, Theme.RAISED);
        c.stroke(x, y, w, h, r, 1, focused ? Theme.BORDER_STRONG : Theme.BORDER);
        sliderX = x;
        sliderY = y;
        sliderW = w;
        sliderH = h;
    }

    private static void sliderHandle(Canvas c, float x, float y, float w, float h, boolean hot) {
        float hv = anim(HANDLE, sliderX, y, sliderW, h, hot);
        if (sliderW > 0 && y == sliderY && h == sliderH && x >= sliderX && x + w <= sliderX + sliderW + 0.5f) {
            // The filled part is the track's own shape, cut off at the handle.
            c.clip(sliderX, y, x + w / 2 - sliderX, h);
            c.rect(sliderX, y, sliderW, h, Math.min(Theme.R_MD, h / 2), Colors.withAlpha(Theme.accent(), 0.30f + 0.12f * hv));
            c.unclip();
        }
        float bar = 2f + hv;
        c.rect(x + (w - bar) / 2, y + 3, bar, h - 6, bar / 2, Colors.mix(Colors.lighten(Theme.accent(), 0.35f), Colors.WHITE, hv));
    }

    private static void field(Canvas c, float x, float y, float w, float h, boolean focused) {
        float f = anim(FIELD_FOCUS, x, y, w, h, focused);
        float r = Math.min(Theme.R_SM, h / 2);
        c.rect(x, y, w, h, r, FIELD);
        c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER_STRONG, Colors.withAlpha(Theme.accent(), 0.9f), f));
    }

    private static void checkbox(Canvas c, float x, float y, float w, float h, boolean on, boolean hot) {
        float hv = anim(CHECK, x, y, w, h, hot);
        float r = Math.min(4.5f, h / 2);
        if (on) {
            Theme.accentFill(c, x, y, w, h, r);
            c.gradientV(x, y, w, h, r, Colors.withAlpha(Colors.WHITE, 0.10f + 0.10f * hv), 0x00FFFFFF);
            float t = Math.max(1.4f, h * 0.1f);
            c.line(x + w * 0.27f, y + h * 0.52f, x + w * 0.43f, y + h * 0.68f, t, Theme.onAccent());
            c.line(x + w * 0.43f, y + h * 0.68f, x + w * 0.74f, y + h * 0.34f, t, Theme.onAccent());
        } else {
            c.rect(x, y, w, h, r, BASE);
            c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
            c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER_STRONG, Theme.TEXT_MUTED, hv));
        }
    }

    private static void tab(Canvas c, float x, float y, float w, float h, boolean selected, boolean hot) {
        float hv = anim(TAB, x, y, w, h, hot);
        float px = x + 2, py = y + 3, pw = w - 4, ph = h - 5;
        if (selected) {
            c.rect(px, py, pw, ph, Theme.R_MD, BASE);
            c.rect(px, py, pw, ph, Theme.R_MD, Theme.RAISED_HOVER);
            c.stroke(px, py, pw, ph, Theme.R_MD, 1, Theme.BORDER_STRONG);
        } else {
            c.rect(px, py, pw, ph, Theme.R_MD, Colors.withAlpha(Colors.WHITE, 0.08f * hv));
        }
    }

    // ---- lists -----------------------------------------------------------------------------------

    /** Brackets vanilla's list selection highlight, which is two plain fills (an outline, then black inside). */
    public static void selection(boolean on) {
        selection = on && drawing;
    }

    // ---- flat rectangles -------------------------------------------------------------------------

    /**
     * A plain filled rectangle. Vanilla menus hardly use them, but Sodium's settings screen is
     * built from nothing else, so on its screens each one becomes a rounded Aller surface.
     *
     * @return true if it was drawn here
     */
    public static boolean fill(Canvas c, float x0, float y0, float x1, float y1, int color) {
        if (!drawing) return false;
        float x = Math.min(x0, x1), y = Math.min(y0, y1), w = Math.abs(x1 - x0), h = Math.abs(y1 - y0);
        if (selection) {
            // The outline fill carries the focus state in its colour; the inner fill is dropped.
            if (color != 0xFF000000) highlight(c, x, y, w, h, color == 0xFFFFFFFF);
            return true;
        }
        if (!flat) return outlinedBox(c, x, y, w, h, color);
        if (w >= c.width() - 1 && h >= c.height() - 1) return false;
        float r = Math.min(w, h) <= 2.5f ? 0 : Math.min(4, Math.min(w, h) / 2);
        int alpha = color >>> 24;
        if ((color & 0xFFFFFF) != 0) {
            c.rect(x, y, w, h, r, flatColor(color));
        } else if (alpha >= 0xD0) {
            float hv = anim(FLAT, x, y, w, h, true);
            c.rect(x, y, w, h, r, BASE);
            c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
        } else if (alpha >= 0x80) {
            float hv = anim(FLAT, x, y, w, h, false);
            c.rect(x, y, w, h, r, BASE);
            c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
        } else {
            // Resting rows: dark enough to read over the backdrop, with a hint of lift.
            c.rect(x, y, w, h, r, Colors.withAlpha(0xFF0D0B14, 0.30f + alpha / 255f));
            c.rect(x, y, w, h, r, 0x08FFFFFF);
        }
        return true;
    }

    private static float boxX, boxY, boxW, boxH, boxAt = -1;
    private static boolean boxFocused;

    /**
     * Lists that draw their own selection (Mod Menu's) use the same two fills as vanilla: a white
     * or grey box, then a black one a pixel smaller. The first is held back until the second
     * confirms the pair, and the pair becomes one highlight.
     */
    private static boolean outlinedBox(Canvas c, float x, float y, float w, float h, int color) {
        boolean pending = boxAt == Motion.time();
        if (pending && color == 0xFF000000 && x == boxX + 1 && y == boxY + 1 && w == boxW - 2 && h == boxH - 2) {
            boxAt = -1;
            highlight(c, boxX, boxY, boxW, boxH, boxFocused);
            return true;
        }
        if (pending) {
            // Not a selection after all: draw the held rectangle as it was meant to be.
            boxAt = -1;
            c.rect(boxX, boxY, boxW, boxH, 0, boxFocused ? 0xFFFFFFFF : 0xFF808080);
        }
        if ((color == 0xFFFFFFFF || color == 0xFF808080) && w >= 40 && h >= 12) {
            boxX = x;
            boxY = y;
            boxW = w;
            boxH = h;
            boxFocused = color == 0xFFFFFFFF;
            boxAt = Motion.time();
            return true;
        }
        return false;
    }

    private static void highlight(Canvas c, float x, float y, float w, float h, boolean focused) {
        c.rect(x, y, w, h, Theme.R_SM, Colors.withAlpha(Theme.accent(), focused ? 0.20f : 0.12f));
        c.stroke(x, y, w, h, Theme.R_SM, 1, Colors.withAlpha(Theme.accent(), focused ? 0.80f : 0.40f));
    }

    /** A one-pixel frame drawn by a flat-style mod (focus rings, tick boxes). */
    public static boolean border(Canvas c, float x0, float y0, float x1, float y1, int color) {
        if (!drawing) return false;
        float w = x1 - x0, h = y1 - y0;
        // White is both a focus ring (large) and an empty tick box (small).
        int mapped = color == 0xFFFFFFFF ? Math.min(w, h) <= 12 ? 0xA6FFFFFF : Colors.withAlpha(Theme.accent(), 0.9f)
                : (color & 0xFFFFFF) == 0x00FFEE ? Theme.BORDER_STRONG : flatColor(color);
        c.stroke(x0, y0, w, h, Math.min(4, Math.min(w, h) / 2), 1, mapped);
        return true;
    }

    /** Sodium's teal becomes the accent, so its ticks, sliders and tab markers match the rest of the client. */
    private static int flatColor(int color) {
        // Matched by hue: the theme has a base, a lighter and a darker shade, kept as such.
        float[] hsv = Colors.toHsv(color);
        if (hsv[0] < 0.40f || hsv[0] > 0.53f || hsv[1] < 0.12f) return color;
        int accent = Theme.accent();
        int shade = hsv[2] < 0.75f ? Colors.darken(accent, 0.25f) : hsv[1] < 0.28f ? Colors.lighten(accent, 0.45f) : accent;
        return (color & 0xFF000000) | (shade & 0xFFFFFF);
    }

    /** A bordered box a mod draws by hand (Iris's panels). */
    public static boolean panel(Canvas c, float x, float y, float w, float h) {
        if (!drawing) return false;
        c.rect(x, y, w, h, Theme.R_MD, 0xE6100E18);
        c.stroke(x, y, w, h, Theme.R_MD, 1, Theme.BORDER_STRONG);
        return true;
    }

    // ---- text ------------------------------------------------------------------------------------

    /** Maps Minecraft's whites and greys onto the theme's text colours; anything tinted is kept. */
    public static int textColor(int argb) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        if (r != g || g != b) return flat ? flatColor(argb) : argb;
        int base = r >= 0xE0 ? Theme.TEXT : r >= 0xA0 ? Theme.TEXT_DIM : r >= 0x70 ? Theme.TEXT_MUTED : argb;
        return (argb & 0xFF000000) | (base & 0xFFFFFF);
    }

    // ---- per-widget animation --------------------------------------------------------------------

    private static final class Anim {
        final Spring spring;
        float seen;

        Anim(float start) {
            spring = Spring.snappy(start);
        }
    }

    /** Vanilla widgets keep no animation state, so springs are filed under the rectangle they are drawn at. */
    private static final Map<Long, Anim> anims = new HashMap<>();
    private static float pruned;

    private static float anim(int kind, float x, float y, float w, float h, boolean on) {
        long key = (((long) kind * 31 + Float.floatToIntBits(x)) * 31 + Float.floatToIntBits(y)) * 31 + Float.floatToIntBits(w);
        key = key * 31 + Float.floatToIntBits(h);
        Anim a = anims.get(key);
        // A new rectangle starts settled, so scrolling a list does not replay every hover.
        if (a == null) anims.put(key, a = new Anim(on ? 1 : 0));
        if (a.seen != Motion.time()) {
            a.seen = Motion.time();
            a.spring.target(on ? 1 : 0).update();
        }
        return Math.clamp(a.spring.get(), 0f, 1f);
    }
}
