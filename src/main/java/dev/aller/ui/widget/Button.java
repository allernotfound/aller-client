package dev.aller.ui.widget;

import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;

public class Button extends Widget {
    public enum Style { PRIMARY, GLASS, GHOST, DANGER }

    public String label;
    public String hint;
    public Style style = Style.GLASS;
    public float textSize = 9f;
    public boolean alignLeft;
    public boolean enabled = true;
    /** Drawn before the label. */
    public Icons icon;
    private final Runnable action;
    private final Spring hover = Spring.snappy(0);
    private final Spring press = Spring.snappy(0);
    private boolean down;

    public Button(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public Button style(Style s) {
        style = s;
        return this;
    }

    public Button hint(String text) {
        hint = text;
        return this;
    }

    public Button icon(Icons i) {
        icon = i;
        return this;
    }

    public Button left() {
        alignLeft = true;
        return this;
    }

    @Override
    public void draw(Canvas c, float mx, float my) {
        boolean over = enabled && hit(mx, my);
        float hv = hover.target(over ? 1 : 0).update();
        float pr = press.target(down && over ? 1 : 0).update();

        c.push();
        c.pixel(true);
        // Lift on hover, sink on press. Pixel-art corners would shimmer while scaling, so they stay put.
        if (!c.pixelated()) c.scale(1f + 0.02f * hv - 0.04f * pr, x + w / 2, y + h / 2);
        c.pushAlpha(enabled ? 1f : 0.45f);
        float r = Math.min(Theme.R_MD, h / 2);
        int textColor = Theme.TEXT;
        switch (style) {
            case PRIMARY -> {
                c.shadow(x, y + 3, w, h, r, 10 + 8 * hv, Colors.withAlpha(Theme.accent(), 0.25f + 0.25f * hv));
                Theme.accentFill(c, x, y, w, h, r);
                c.gradientV(x, y, w, h, r, Colors.withAlpha(Colors.WHITE, 0.10f + 0.10f * hv), 0x00FFFFFF);
                textColor = Theme.onAccent();
            }
            case DANGER -> {
                c.rect(x, y, w, h, r, Colors.withAlpha(Theme.DANGER, 0.14f + 0.14f * hv));
                c.stroke(x, y, w, h, r, 1, Colors.withAlpha(Theme.DANGER, 0.35f + 0.3f * hv));
                textColor = Colors.mix(Theme.DANGER, Colors.WHITE, 0.25f + 0.4f * hv);
            }
            case GLASS -> {
                // A dark base keeps the label readable over bright backgrounds.
                c.rect(x, y, w, h, r, 0xB80D0B14);
                c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
                c.stroke(x, y, w, h, r, 1, Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hv));
            }
            case GHOST -> {
                c.rect(x, y, w, h, r, Colors.withAlpha(Colors.WHITE, 0.07f * hv));
                textColor = Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv);
            }
        }
        c.pixel(false);
        Fonts font = style == Style.PRIMARY ? Fonts.SEMIBOLD : Fonts.MEDIUM;
        float pad = Math.min(12, h * 0.45f);
        float mark = icon != null ? textSize * 1.05f : 0, gap = icon != null ? textSize * 0.4f : 0;
        float tx = alignLeft ? x + pad + 2 * hv : x + (w - font.width(label, textSize) - mark - gap) / 2;
        if (icon != null) icon.draw(c, tx + mark / 2, y + h / 2, mark, textColor);
        c.textMiddle(font, label, tx + mark + gap, y, h, textSize, textColor);
        if (hint != null) {
            float hs = textSize * 0.82f;
            c.textMiddle(Fonts.REGULAR, hint, x + w - pad - Fonts.REGULAR.width(hint, hs), y, h, hs,
                    Colors.fade(textColor, 0.55f));
        }
        c.popAlpha();
        c.pop();
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
