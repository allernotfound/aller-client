package dev.aller.ui.widget;

import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;

/** A small square button showing only an icon; its name appears beside it on hover. */
public final class IconButton extends Widget {
    public final Icons icon;
    public String label;
    public boolean enabled = true;
    public boolean danger;
    /** Draws as selected (accent fill), for toggles such as the list/grid switch. */
    public boolean active;
    private final Runnable action;
    private final Spring hover = Spring.snappy(0);
    private final Spring press = Spring.snappy(0);
    private final Spring tip = Spring.smooth(0);
    private boolean down;

    public IconButton(Icons icon, String label, Runnable action) {
        this.icon = icon;
        this.label = label;
        this.action = action;
    }

    @Override
    public void draw(Canvas c, float mx, float my) {
        boolean over = enabled && hit(mx, my);
        float hv = hover.target(over ? 1 : 0).update();
        float pr = press.target(down && over ? 1 : 0).update();
        tip.target(over ? 1 : 0).update();
        float r = Math.min(6, w * 0.3f);
        c.push();
        c.pixel(true);
        if (!c.pixelated()) c.scale(1f + 0.06f * hv - 0.08f * pr, x + w / 2, y + h / 2);
        c.pushAlpha(enabled ? 1f : 0.4f);
        int tint = danger ? Theme.DANGER : Theme.accent();
        if (active) {
            Theme.accentFill(c, x, y, w, h, r);
        } else {
            c.rect(x, y, w, h, r, 0xB80D0B14);
            c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Colors.withAlpha(tint, 0.22f), hv));
            c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER, Colors.withAlpha(tint, 0.6f), hv));
        }
        c.pixel(false);
        int color = active ? Theme.onAccent() : Colors.mix(Theme.TEXT_DIM, danger ? Colors.lighten(Theme.DANGER, 0.3f) : Theme.TEXT, hv);
        icon.draw(c, x + w / 2, y + h / 2, Math.min(w, h) * 0.56f, color);
        c.popAlpha();
        c.pop();
    }

    /** Draws the name beside the button. Call after everything else so it sits on top. */
    public void drawTip(Canvas c, boolean toTheRight) {
        float t = Math.clamp(tip.get(), 0f, 1f);
        if (t < 0.02f || label == null) return;
        float size = 7.5f, tw = Fonts.MEDIUM.width(label, size) + 12, th = 15;
        float tx = toTheRight ? x + w + 5 + 4 * (1 - t) : x - tw - 5 - 4 * (1 - t);
        float ty = y + (h - th) / 2;
        c.pushAlpha(t);
        c.shadow(tx, ty + 2, tw, th, 5, 8, 0x66000000);
        c.rect(tx, ty, tw, th, 5, 0xF2171422);
        c.stroke(tx, ty, tw, th, 5, 1, Theme.BORDER_STRONG);
        c.textMiddle(Fonts.MEDIUM, label, tx + 6, ty, th, size, Theme.TEXT);
        c.popAlpha();
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        if (button == 0 && enabled && hit(mx, my)) {
            down = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        boolean wasDown = down;
        down = false;
        if (button == 0 && wasDown && enabled && hit(mx, my)) {
            Sounds.click();
            action.run();
            return true;
        }
        return false;
    }
}
