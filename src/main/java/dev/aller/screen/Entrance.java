package dev.aller.screen;

import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.MenuKind;
import dev.aller.platform.ScreenHost;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Tween;
import net.minecraft.client.gui.screens.Screen;

/**
 * Eases a Minecraft menu in. Its pieces cannot be animated one by one, so the whole screen settles
 * from slightly enlarged while everything it draws fades up: sprites, rectangles and text all
 * pass through hooks that multiply in {@link #alpha()}.
 *
 * <p>Opened from one of Aller's menus, that menu's content is kept on top for as long as it takes to
 * slide away, so the two overlap instead of one waiting for the other. Between two Minecraft menus
 * (options to a page of it and back) the same thing plays quicker and smaller.
 */
public final class Entrance {
    private static final Tween full = new Tween(0.58f, Easing.OUT_CUBIC).delay(0.05f), quick = new Tween(0.26f, Easing.OUT_CUBIC);
    private static Tween tween = full;
    private static Screen entering;
    /** The Aller menu being left, still drawn over the screen that replaces it. */
    private static AllerScreen leaving, pending;
    private static boolean fresh, drawing;
    private static float t = 1;
    private static Canvas canvas;

    private Entrance() {}

    /** Names the menu the next screen is opened from. Cleared again once that screen has been asked for. */
    static void from(AllerScreen menu) {
        pending = menu;
    }

    /** A screen is about to be shown. Called for every screen change. */
    static void opened(Screen previous, Screen next) {
        AllerScreen menu = pending;
        pending = null;
        // Only menus ease in (never an inventory or chat), unless one of Aller's menus is handing over to the screen.
        if (next == null || next == previous || next instanceof ScreenHost || menu == null && MenuKind.of(next) == null) {
            if (next != entering) entering = null;
            return;
        }
        boolean betweenMenus = menu == null && previous != null && !(previous instanceof ScreenHost);
        tween = betweenMenus ? quick : full;
        entering = next;
        leaving = menu;
        fresh = true;
        t = 0;
    }

    /** Before a screen draws. */
    public static void begin(Canvas c, Screen screen) {
        if (entering == null || screen != entering) return;
        if (screen != Mc.screen()) {
            // Shown beneath the launcher, or replaced before it finished.
            if (!(Mc.screen() instanceof ScreenHost)) entering = null;
            return;
        }
        // The frame that built the screen is a long one; the animation starts on the next.
        if (fresh) tween.restart();
        else tween.update();
        fresh = false;
        t = tween.get();
        if (tween.finished()) {
            entering = null;
            leaving = null;
            return;
        }
        drawing = true;
        canvas = c;
        c.push();
        c.scale(zoom(), c.width() / 2, c.height() / 2);
        c.pushAlpha(alpha());
    }

    /** After the screen has drawn. */
    public static void end(Canvas ignored) {
        if (!drawing) return;
        drawing = false;
        Canvas c = canvas;
        canvas = null;
        c.popAlpha();
        c.pop();
        if (leaving == null) return;
        c.layer();
        // A menu that is not restyled has the panorama behind it: Aller's backdrop lifts off that.
        if (Mc.mc().level == null && !MenuSkin.active()) {
            c.pushAlpha(1 - t);
            Theme.scene(c, c.width(), c.height());
            c.popAlpha();
        }
        c.beginScale(leaving.scale());
        leaving.drawLeaving(c);
        c.endScale();
    }

    private static float zoom() {
        return 1 + (tween == quick ? 0.022f : 0.05f) * (1 - t);
    }

    /**
     * Draws something the screen has behind it (the backdrop, the dim over the world) where it will
     * stay: only what is on the screen settles into place, or the background would seem to pulse.
     */
    public static void still(Canvas c, Runnable background) {
        if (!drawing) {
            background.run();
            return;
        }
        c.push();
        c.scale(1 / zoom(), c.width() / 2, c.height() / 2);
        background.run();
        c.pop();
    }

    /** What everything the entering screen draws is multiplied by; 1 whenever nothing is easing in. */
    public static float alpha() {
        if (!drawing) return 1;
        return tween == quick ? Math.min(1, 0.25f + t * 1.6f) : Math.min(1, t * 1.25f);
    }

    /** Whether the screen now drawing is easing in, so what it draws has to be faded. */
    public static boolean fading() {
        return drawing && alpha() < 1;
    }

    /** How much of the menu being left is still there, 1 to 0: the backdrop eases between the two with it. */
    public static float handover() {
        return entering != null && leaving != null ? 1 - t : 0;
    }
}
