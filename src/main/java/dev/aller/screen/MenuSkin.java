package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.MenuKind;
import dev.aller.platform.ScreenHost;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

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
        front = rendering == Mc.screen();
    }

    public static void end(Canvas c) {
        if (drawing) flushBox(c);
        drawing = false;
        selection = false;
    }

    // ---- pointer ---------------------------------------------------------------------------------

    /** False while the menu is only on show beneath one of Aller's screens: nothing in it reacts then. */
    private static boolean front;

    /**
     * Whether a widget vanilla calls highlighted is shown as such. Vanilla keeps the button last
     * clicked focused, and so highlighted, until something else is clicked; that only means
     * something to a player moving through the menu with the keyboard.
     */
    private static boolean lit(boolean highlighted, boolean over) {
        return front && highlighted && (over || keyboard());
    }

    private static boolean keyboard() {
        return Mc.mc().getLastInputType().isKeyboard();
    }

    private static boolean pressing() {
        return GLFW.glfwGetMouseButton(Mc.window(), 0) == GLFW.GLFW_PRESS;
    }

    // ---- background ------------------------------------------------------------------------------

    /** Stands in for the panorama: outside a world there is nothing to blur, so the menu backdrop shows instead. */
    public static boolean backdrop(Canvas c) {
        if (!drawing) return false;
        // Coming from the main menu, the backdrop settles from that menu's brighter one.
        Entrance.still(c, () -> {
            c.pushSolid();
            Theme.scene(c, c.width(), c.height(), Entrance.handover());
            c.popAlpha();
        });
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
                    Entrance.still(c, () -> {
                        c.rect(x, y, w, h, 0, 0x5207060B);
                        c.gradientV(x, y, w, h * 0.3f, 0, 0x8007060B, 0x0007060B);
                        c.gradientV(x, y + h * 0.7f, w, h * 0.3f, 0, 0x0007060B, 0x8007060B);
                    });
                }
            }
            case "inworld_menu_background.png" -> {
                if (w >= c.width() - 1 && h >= c.height() - 1) Entrance.still(c, () -> c.rect(x, y, w, h, 0, 0x73050409));
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
            case "widget/scroller" -> scroller(c, x, y, w, h);
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

    /** The glass button, as {@code ui.widget.Button} draws it and moves it; also used for mods that draw their own. */
    public static void button(Canvas c, float x, float y, float w, float h, boolean highlighted, boolean enabled) {
        boolean over = front && c.hovered(x, y, w, h);
        boolean hot = enabled && lit(highlighted, over);
        Anim a = anim(BUTTON, x, y, w, h, hot).step(hot, hot && over && pressing());
        // Rows of buttons that touch in vanilla get a little air between them.
        if (h >= 18) {
            y += 1;
            h -= 2;
        }
        c.push();
        c.pixel(true);
        // Lift on hover, sink on press; the label stays put.
        if (!c.pixelated()) c.scale(1f + 0.02f * a.hv - 0.04f * a.pr, x + w / 2, y + h / 2);
        c.pushAlpha(enabled ? 1f : 0.5f);
        plate(c, x, y, w, h, a.hv, a.pr);
        if (hot && !over) c.stroke(x, y, w, h, Math.min(Theme.R_MD, h / 2), 1, Colors.withAlpha(Theme.accent(), 0.85f));
        c.popAlpha();
        c.pixel(false);
        c.pop();
    }

    private static void plate(Canvas c, float x, float y, float w, float h, float hv, float pr) {
        float r = Math.min(Theme.R_MD, h / 2);
        c.rect(x, y, w, h, r, BASE);
        c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
        if (pr > 0.01f) c.rect(x, y, w, h, r, Colors.withAlpha(Colors.BLACK, 0.20f * pr));
        c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hv));
    }

    // Vanilla draws a slider as a track sprite followed by its handle. The track is drawn as a
    // button and remembered; the handle then adds the progress line and knob along its bottom
    // edge, clear of the label.
    private static float sliderX, sliderY, sliderW, sliderH;

    private static void sliderTrack(Canvas c, float x, float y, float w, float h, boolean focused) {
        Anim a = anim(HANDLE, x, y, w, h, false);
        c.pixel(true);
        plate(c, x, y + 1, w, h - 2, a.hv, 0);
        if (front && focused && keyboard()) {
            c.stroke(x, y + 1, w, h - 2, Math.min(Theme.R_MD, (h - 2) / 2), 1, Colors.withAlpha(Theme.accent(), 0.85f));
        }
        c.pixel(false);
        sliderX = x;
        sliderY = y;
        sliderW = w;
        sliderH = h;
    }

    private static void sliderHandle(Canvas c, float x, float y, float w, float h, boolean highlighted) {
        boolean matched = sliderW > w && y == sliderY && h == sliderH && x >= sliderX && x + w <= sliderX + sliderW + 0.5f;
        if (!matched) {
            // A handle without its track (another mod's widget): a plain grip.
            c.rect(x + w / 2 - 1.5f, y + 3, 3, h - 6, 1.5f, Colors.lighten(Theme.accent(), 0.35f));
            return;
        }
        float tx = sliderX, tw = sliderW, ty = y + 1, th = h - 2;
        sliderW = 0;
        boolean over = front && c.hovered(tx, y, tw, h);
        boolean hot = lit(highlighted, over);
        Anim a = anim(HANDLE, tx, y, tw, h, hot).step(hot, hot && over && pressing());

        float fraction = Math.clamp((x - tx) / Math.max(1, tw - w), 0f, 1f);
        // The thumb travels between the track's rounded ends.
        float pad = Math.min(6.5f, th * 0.36f), kx = tx + pad + (tw - pad * 2) * fraction;
        // The filled part is the track's own shape, cut off at the thumb.
        c.clip(tx, ty, kx - tx, th);
        c.pixel(true);
        c.gradientH(tx, ty, tw, th, Math.min(Theme.R_MD, th / 2), Colors.withAlpha(Theme.accent(), 0.20f + 0.08f * a.hv),
                Colors.withAlpha(Theme.accent(), 0.38f + 0.10f * a.hv));
        c.pixel(false);
        c.unclip();
        // A tall thumb, standing a little proud of the track above and below.
        float kw = 4 + a.hv - 0.6f * a.pr, over2 = 1.5f + 0.5f * a.hv, ky = ty - over2, kh = th + over2 * 2;
        c.shadow(kx - kw / 2, ky + 1, kw, kh, kw / 2, 4, Colors.withAlpha(Colors.BLACK, 0.45f));
        c.rect(kx - kw / 2, ky, kw, kh, kw / 2, Colors.mix(Colors.lighten(Theme.accent(), 0.6f), Colors.WHITE, a.hv));
    }

    private static void field(Canvas c, float x, float y, float w, float h, boolean focused) {
        Anim a = anim(FIELD_FOCUS, x, y, w, h, focused).step(focused, false);
        float r = Math.min(Theme.R_SM, h / 2);
        c.rect(x, y, w, h, r, FIELD);
        c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER_STRONG, Colors.withAlpha(Theme.accent(), 0.75f), a.hv));
    }

    private static void checkbox(Canvas c, float x, float y, float w, float h, boolean on, boolean highlighted) {
        boolean over = front && c.hovered(x, y, w, h);
        boolean hot = lit(highlighted, over);
        Anim a = anim(CHECK, x, y, w, h, hot).step(hot, hot && over && pressing());
        float r = Math.min(4.5f, h / 2);
        c.push();
        if (!c.pixelated()) c.scale(1f + 0.05f * a.hv - 0.09f * a.pr, x + w / 2, y + h / 2);
        if (on) {
            Theme.accentFill(c, x, y, w, h, r);
            c.gradientV(x, y, w, h, r, Colors.withAlpha(Colors.WHITE, 0.10f + 0.10f * a.hv), 0x00FFFFFF);
            Icons.CHECK.draw(c, x + w / 2, y + h / 2, Math.min(w, h) * 0.78f, Theme.onAccent());
        } else {
            c.rect(x, y, w, h, r, BASE);
            c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, a.hv));
            c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER_STRONG, Theme.TEXT_MUTED, a.hv));
        }
        if (hot && !over) c.stroke(x - 1, y - 1, w + 2, h + 2, r + 1, 1, Colors.withAlpha(Theme.accent(), 0.85f));
        c.pop();
    }

    private static void tab(Canvas c, float x, float y, float w, float h, boolean selected, boolean highlighted) {
        boolean over = front && c.hovered(x, y, w, h);
        boolean hot = lit(highlighted, over);
        Anim a = anim(TAB, x, y, w, h, hot).step(hot, false);
        float px = x + 2, py = y + 3, pw = w - 4, ph = h - 5;
        if (selected) {
            c.rect(px, py, pw, ph, Theme.R_MD, BASE);
            c.rect(px, py, pw, ph, Theme.R_MD, Theme.RAISED_HOVER);
            c.stroke(px, py, pw, ph, Theme.R_MD, 1, Theme.BORDER_STRONG);
            c.rect(px + pw / 2 - 7, py + ph - 2.5f, 14, 1.5f, 0.75f, Theme.accent());
        } else {
            c.rect(px, py, pw, ph, Theme.R_MD, Colors.withAlpha(Colors.WHITE, 0.08f * a.hv));
        }
        if (hot && !over) c.stroke(px, py, pw, ph, Theme.R_MD, 1, Colors.withAlpha(Theme.accent(), 0.85f));
    }

    private static void scroller(Canvas c, float x, float y, float w, float h) {
        boolean over = front && c.hovered(x - 2, y, w + 4, h);
        Anim a = anim(HANDLE, x, 0, w, h, false).step(over, false);
        float bar = 3 + a.hv;
        c.rect(x + w / 2 - bar / 2, y + 2, bar, h - 4, bar / 2, Colors.withAlpha(Colors.WHITE, 0.36f + 0.24f * a.hv));
    }

    // ---- lists -----------------------------------------------------------------------------------

    /** Brackets vanilla's list selection highlight, which is two plain fills (an outline, then black inside). */
    public static void selection(boolean on) {
        selection = on && drawing;
    }

    // ---- flat rectangles -------------------------------------------------------------------------

    /**
     * A plain filled rectangle. Vanilla menus hardly use them, but Sodium's settings screen is
     * built from nothing else, so on its screens each one becomes an Aller surface.
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
        flatFill(c, x, y, w, h, color);
        return true;
    }

    /** A two-colour rectangle: on Sodium's screen that is the panel behind its list of pages. */
    public static boolean gradient(Canvas c, float x0, float y0, float x1, float y1, int top, int bottom) {
        if (!drawing) return false;
        if (!flat || selection) return fill(c, x0, y0, x1, y1, bottom);
        surface(c, Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0), Theme.R_MD);
        return true;
    }

    /**
     * Sodium's rectangles, told apart by what they are filled with: shades of black are surfaces
     * (the darker, the more raised: its hover is the darkest), white and the theme colours are
     * marks drawn on them.
     */
    private static void flatFill(Canvas c, float x, float y, float w, float h, int color) {
        int alpha = color >>> 24, rgb = color & 0xFFFFFF;
        float least = Math.min(w, h);
        if (rgb != 0) {
            if (rgb == 0xFFFFFF && alpha <= 0x20) {
                // A faint wash: the pointer over an entry in the list of pages.
                c.rect(x, y + 0.5f, w, h - 1, 4.5f, Colors.withAlpha(Colors.WHITE, 0.07f));
                return;
            }
            int mark = rgb == 0xFFFFFF ? Colors.withAlpha(Theme.TEXT, alpha / 255f) : flatColor(color);
            if (least <= 4) {
                // Slider tracks and thumbs, underlines, the bar beside the page in view: pills.
                if (h > w && h >= 12) {
                    y += 3;
                    h -= 6;
                }
                c.rect(x, y, w, h, Math.min(w, h) / 2, mark);
            } else {
                c.rect(x, y, w, h, Math.min(3, least / 2), mark);
            }
            return;
        }
        boolean row = h >= 10 && h <= 28 && w >= 16;
        if (!row) {
            // Blocks: the tooltip beside the options, the search results.
            if (least <= 2.5f) c.rect(x, y, w, h, 0, Colors.withAlpha(0xFF0D0B14, 0.30f + alpha / 255f * 0.6f));
            else if (alpha >= 0xD0) panel(c, x, y, w, h);
            else surface(c, x, y, w, h, Math.min(Theme.R_MD, least / 2));
            return;
        }
        // Rows and buttons. A hairline of air keeps neighbours apart now that their corners are round.
        boolean hovered = alpha >= 0xD0;
        Anim a = anim(FLAT, x, y, w, h, hovered);
        a.step(hovered && front, hovered && front && pressing() && c.hovered(x, y, w, h));
        // What the rectangle is at rest decides its look: a button, a heading, or a quiet row.
        if (!hovered) a.level = alpha >= 0x80 ? w <= 100 ? 2 : 1 : alpha >= 0x50 ? 1 : 0;
        float ry = y + 0.5f, rh = h - 1, r = Math.min(4.5f, rh / 2);
        if (a.level == 2) {
            c.pixel(true);
            plate(c, x, ry, w, rh, a.hv, a.pr);
            c.pixel(false);
        } else {
            c.rect(x, ry, w, rh, r, Colors.withAlpha(0xFF0D0B14, a.level == 1 ? 0.62f : 0.34f + 0.30f * a.hv));
            c.rect(x, ry, w, rh, r, Colors.mix(a.level == 1 ? 0x12FFFFFF : 0x08FFFFFF, Theme.RAISED_HOVER, a.hv));
            if (a.hv > 0.01f) c.stroke(x, ry, w, rh, r, 1, Colors.withAlpha(Colors.WHITE, 0.16f * a.hv));
        }
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
        // Not a selection after all: draw the held rectangle as it was meant to be.
        flushBox(c);
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

    /** Draws a rectangle that was held back as a possible selection box and turned out not to be one. */
    private static void flushBox(Canvas c) {
        if (boxAt != Motion.time()) return;
        boxAt = -1;
        c.rect(boxX, boxY, boxW, boxH, 0, boxFocused ? 0xFFFFFFFF : 0xFF808080);
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
                : (color & 0xFFFFFF) == 0x00FFEE ? Colors.withAlpha(Theme.accent(), 0.85f) : flatColor(color);
        // A ring round a row sits on the row as it is drawn, half a pixel in.
        boolean row = h >= 10 && h <= 28 && w >= 16 && Math.min(w, h) > 12;
        c.stroke(x0, y0 + (row ? 0.5f : 0), w, h - (row ? 1 : 0), Math.min(4.5f, Math.min(w, h) / 2), 1, mapped);
        return true;
    }

    /**
     * Sodium's theme colours become the accent, so its ticks, sliders and headings match the rest of
     * the client. Each mod on its screen has a pastel of its own with a lighter and a darker shade;
     * they are told from ordinary coloured text (warnings, formatting codes) by being pastel.
     */
    private static int flatColor(int color) {
        float[] hsv = Colors.toHsv(color);
        if (hsv[1] < 0.10f || hsv[1] > 0.50f || hsv[2] < 0.45f) return color;
        int accent = Theme.accent();
        int shade = hsv[2] < 0.75f ? Colors.mix(accent, Theme.TEXT_MUTED, 0.45f) : hsv[1] < 0.28f ? Colors.lighten(accent, 0.45f) : accent;
        return (color & 0xFF000000) | (shade & 0xFFFFFF);
    }

    /** The tint of a texture about to be drawn: on Sodium's screen its icons take the accent too. */
    public static int tint(int color) {
        return drawing && flat ? flatColor(color) : color;
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
        final Spring hover, press = Spring.snappy(0);
        float seen = -1, hv, pr;
        /** For Sodium's rows: what kind of surface it is at rest. */
        int level;

        Anim(float start) {
            hover = Spring.snappy(start);
            hv = start;
        }

        /** Advances the springs, once a frame however often the widget is drawn. */
        Anim step(boolean on, boolean down) {
            if (seen != Motion.time()) {
                seen = Motion.time();
                hv = Math.clamp(hover.target(on ? 1 : 0).update(), 0f, 1f);
                pr = Math.clamp(press.target(down ? 1 : 0).update(), 0f, 1f);
            }
            return this;
        }
    }

    /** Vanilla widgets keep no animation state, so springs are filed under the rectangle they are drawn at. */
    private static final Map<Long, Anim> anims = new HashMap<>();
    private static float pruned;

    /** @param on the state a rectangle not seen before starts settled in, so scrolling a list does not replay every hover */
    private static Anim anim(int kind, float x, float y, float w, float h, boolean on) {
        long key = (((long) kind * 31 + Float.floatToIntBits(x)) * 31 + Float.floatToIntBits(y)) * 31 + Float.floatToIntBits(w);
        key = key * 31 + Float.floatToIntBits(h);
        Anim a = anims.get(key);
        if (a == null) anims.put(key, a = new Anim(on ? 1 : 0));
        return a;
    }
}
