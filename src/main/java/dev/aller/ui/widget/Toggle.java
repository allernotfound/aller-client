package dev.aller.ui.widget;

import dev.aller.platform.Canvas;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;

/** A pill switch. Stateless about its value: the owner passes the current state when drawing. */
public final class Toggle {
    public static final float W = 26, H = 14;
    private final Spring knob = Spring.bouncy(0);
    private final Spring hover = Spring.snappy(0);
    private boolean primed;

    public void draw(Canvas c, float x, float y, boolean on, boolean hovered) {
        if (!primed) {
            knob.snap(on ? 1 : 0);
            primed = true;
        }
        float t = knob.target(on ? 1 : 0).update();
        float hv = hover.target(hovered ? 1 : 0).update();
        float tc = Math.clamp(t, 0f, 1f);

        int off = Colors.mix(0x26FFFFFF, 0x3AFFFFFF, hv);
        c.rect(x, y, W, H, H / 2, off);
        if (tc > 0.01f) {
            c.pushAlpha(tc);
            c.shadow(x, y + 1, W, H, H / 2, 6, Colors.withAlpha(Theme.accent(), 0.45f));
            Theme.accentFill(c, x, y, W, H, H / 2);
            c.popAlpha();
        }
        float kr = (H - 4) / 2 + 0.6f * hv;
        // The knob stretches slightly while travelling, like a drop of liquid.
        float travel = W - H;
        float stretch = 1.6f * (1 - Math.abs(2 * tc - 1)) * (knob.settled() ? 0 : 1);
        float kx = x + H / 2 + travel * t;
        c.rect(kx - kr - stretch, y + H / 2 - kr, (kr + stretch) * 2, kr * 2, kr, Colors.WHITE);
    }
}
