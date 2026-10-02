package dev.aller.screen.store;

import dev.aller.feature.store.Store;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.PackList;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * What the store adds to the game's own resource pack list: the button that opens it, in the top
 * right corner, and a mark on the packs it has just downloaded. The list stays vanilla's (or the
 * restyled one); these are drawn over it and the button's click is read once a frame, the way the
 * launcher's shortcut is, so nothing is added to the screen's own widgets.
 */
public final class PackListExtras {
    private static final float W = 104, H = 20, MARGIN = 8, TOP = 6;
    private static final String LABEL = "Get more packs";

    private static final Spring hover = Spring.snappy(0), press = Spring.snappy(0);
    private static boolean wasDown, armed;

    private PackListExtras() {}

    private static float left(Screen screen) {
        return screen.width - MARGIN - W;
    }

    private static boolean over(Screen screen) {
        float mx = Mc.mouseX(), my = Mc.mouseY(), x = left(screen);
        return mx >= x && mx < x + W && my >= TOP && my < TOP + H;
    }

    /** Opens the store when the button is clicked. Called every frame. */
    public static void poll() {
        boolean down = GLFW.glfwGetMouseButton(Mc.window(), 0) == GLFW.GLFW_PRESS;
        Screen screen = Mc.screen();
        boolean live = PackList.is(screen) && !Mc.loadingOverlay() && over(screen);
        if (down && !wasDown) armed = live;
        if (!down && wasDown && armed && live) {
            Sounds.click();
            StoreScreen.open(screen);
        }
        if (!down) armed = false;
        wasDown = down;
    }

    /** Draws the button over the pack list. */
    public static void button(Canvas c, Screen screen) {
        if (!PackList.is(screen)) return;
        boolean front = screen == Mc.screen();
        boolean over = front && over(screen);
        float hv = hover.target(over ? 1 : 0).update();
        float pr = press.target(over && armed ? 1 : 0).update();
        float x = left(screen), y = TOP, r = Math.min(Theme.R_MD, H / 2), t = Motion.time();
        boolean still = Motion.reduced();
        float breath = still ? 0.5f : 0.5f + 0.5f * (float) Math.sin(t * 2.2f);

        c.push();
        c.pixel(true);
        if (!c.pixelated()) c.scale(1f + 0.04f * hv - 0.05f * pr, x + W / 2, y + H / 2);
        c.shadow(x, y + 1, W, H, r, 8 + 5 * breath + 5 * hv, Colors.withAlpha(Theme.accent(), 0.28f + 0.16f * breath + 0.2f * hv));
        Theme.accentFill(c, x, y, W, H, r);
        c.gradientV(x, y, W, H, r, Colors.withAlpha(Colors.WHITE, 0.22f + 0.1f * hv), 0x00FFFFFF);
        c.stroke(x, y, W, H, r, 1, Colors.withAlpha(Colors.WHITE, 0.3f + 0.25f * hv));
        c.pixel(false);

        // The glint: a slanted band of light crossing the button every few seconds.
        float cycle = (t % 3.2f) / 0.85f;
        if (cycle < 1 && !still) {
            float gx = x - 24 + (W + 48) * Easing.IN_OUT_CUBIC.apply(cycle);
            c.clip(x + 2, y + 1, W - 4, H - 2);
            c.push();
            c.rotate(0.38f, gx, y + H / 2);
            c.gradientH(gx - 9, y - 12, 9, H + 24, 0, 0x00FFFFFF, 0x77FFFFFF);
            c.gradientH(gx, y - 12, 9, H + 24, 0, 0x77FFFFFF, 0x00FFFFFF);
            c.pop();
            c.unclip();
        }

        int ink = Theme.onAccent();
        float size = 8f, mark = 9.5f, tw = Fonts.SEMIBOLD.width(LABEL, size);
        float tx = x + (W - tw - mark - 5) / 2;
        Icons.CART.draw(c, tx + mark / 2, y + H / 2, mark, ink);
        c.textMiddle(Fonts.SEMIBOLD, LABEL, tx + mark + 5, y, H, size, ink);

        // Two sparkles that take turns.
        if (!still) {
            sparkle(c, x + W - 5, y + 3, 3.4f, t * 2.6f);
            sparkle(c, x + 6, y + H - 3, 2.6f, t * 2.6f + 2.2f);
        }
        c.pop();
    }

    private static void sparkle(Canvas c, float cx, float cy, float size, float phase) {
        float glow = (float) Math.pow(Math.max(0, Math.sin(phase)), 3);
        if (glow < 0.02f) return;
        c.star(cx, cy, size * (0.5f + 0.5f * glow), Colors.withAlpha(Colors.WHITE, 0.95f * glow));
    }

    /** A row of the list is about to draw: light it if the store has just put that pack there. */
    public static void entry(Canvas c, String packId, float x, float y, float w, float h) {
        String name = PackList.filename(packId);
        if (name == null || !Store.fresh(name)) return;
        float pulse = Motion.reduced() ? 0.5f : 0.5f + 0.5f * (float) Math.sin(Motion.time() * 3f);
        int accent = Theme.accent();
        c.rect(x - 2, y - 2, w + 4, h + 4, 5, Colors.withAlpha(accent, 0.13f + 0.09f * pulse));
        c.stroke(x - 2, y - 2, w + 4, h + 4, 5, 1, Colors.withAlpha(accent, 0.55f + 0.35f * pulse));
        String tag = "New";
        float size = 6.4f, tw = Fonts.SEMIBOLD.width(tag, size) + 8, th = 10;
        float tx = x + w - tw - 1, ty = y + h - th - 1;
        Theme.accentFill(c, tx, ty, tw, th, th / 2);
        c.textMiddle(Fonts.SEMIBOLD, tag, tx + 4, ty, th, size, Theme.onAccent());
    }
}
