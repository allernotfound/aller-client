package dev.aller.hud;

import dev.aller.platform.Canvas;
import dev.aller.setting.Settings;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;

/**
 * A HUD chip showing a short label and a value ("FPS 144"). Most informational HUD mods are one of
 * these with a different value supplier. The chip width follows the text with a spring so changing
 * values don't make it jitter.
 */
public abstract class TextHud extends HudModule {
    protected static final float SIZE = 8f;
    protected static final float PAD_X = 6f;
    protected static final float PAD_Y = 4f;

    public final Settings.Bool showLabel = bool("label", "Show label", true);
    public final Settings.Color textColor = color("text_color", "Text colour", 0xFFF5F3FB);
    public final Settings.Bool accentLabel = bool("accent_label", "Accent-coloured label", true);

    private final Spring width = new Spring(0, 380f, 34f);
    private String label = "";
    private String value = "";

    protected TextHud(String id, String name, String description, AnchorH h, AnchorV v, float x, float y) {
        super(id, name, description, h, v, x, y);
    }

    /** Short caption shown before the value, or null for none. */
    protected abstract String label();

    /** The value to show, or null to hide the element. {@code editing} asks for a sample when there is no data. */
    protected abstract String value(boolean editing);

    @Override
    protected boolean measure(boolean editing) {
        String v = value(editing);
        if (v == null) return false;
        value = v;
        String l = showLabel.get() ? label() : null;
        label = l == null ? "" : l;
        float target = PAD_X * 2 + Fonts.SEMIBOLD.width(value, SIZE)
                + (label.isEmpty() ? 0 : Fonts.MEDIUM.width(label, SIZE * 0.82f) + 4);
        if (width.get() == 0) width.snap(target);
        w = width.target(target).update();
        h = Fonts.SEMIBOLD.height(SIZE) + PAD_Y * 2;
        return true;
    }

    @Override
    protected void render(Canvas c, boolean editing) {
        chip(c, h / 2.6f);
        float x = PAD_X;
        if (!label.isEmpty()) {
            float ls = SIZE * 0.82f;
            int lc = accentLabel.get() ? Theme.accent() : Theme.TEXT_DIM;
            x += c.textMiddle(Fonts.MEDIUM, label, x, 0.3f, h, ls, lc) + 4;
        }
        c.textMiddle(Fonts.SEMIBOLD, value, x, 0, h, SIZE, textColor.get());
    }
}
