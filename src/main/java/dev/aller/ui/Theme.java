package dev.aller.ui;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;

/** Design tokens. Every colour, radius and surface treatment in the client comes from here. */
public final class Theme {
    private Theme() {}

    public static final int BG = 0xFF07060B;
    /** Translucent panel fill; reads as frosted glass over a blurred scene. */
    public static final int GLASS = 0xC2100E18;
    /** Lighter glass for in-game HUD chips where nothing is blurred behind. */
    public static final int GLASS_HUD = 0x99100E18;
    public static final int SURFACE = 0xFF14121D;
    public static final int RAISED = 0x14FFFFFF;
    public static final int RAISED_HOVER = 0x24FFFFFF;
    public static final int BORDER = 0x1EFFFFFF;
    public static final int BORDER_STRONG = 0x38FFFFFF;

    public static final int TEXT = 0xFFF5F3FB;
    public static final int TEXT_DIM = 0xFFB0AAC4;
    public static final int TEXT_MUTED = 0xFF7A7490;

    public static final int DANGER = 0xFFF2617A;
    public static final int WARN = 0xFFF5B74E;
    public static final int SUCCESS = 0xFF4ADE9C;

    public static final float R_SM = 5f;
    public static final float R_MD = 8f;
    public static final float R_LG = 12f;

    /** The two looks the font and corner options add up to. */
    public enum Look { SMOOTH, PIXEL }

    /**
     * Set while something is drawn in a look other than the one chosen in the options (onboarding,
     * before the player has picked one, and its previews of each); null to follow the options.
     */
    public static Look look;

    public static int accent() {
        return AllerClient.options().accent.get() | 0xFF000000;
    }

    /** Second gradient stop: the accent rotated towards blue. */
    public static int accent2() {
        float[] hsv = Colors.toHsv(accent());
        return Colors.hsv((hsv[0] - 0.09f + 1f) % 1f, Math.min(1f, hsv[1] * 0.95f), Math.min(1f, hsv[2] * 1.02f), 1f);
    }

    /** Text colour that stays readable on the accent. */
    public static int onAccent() {
        return Colors.luminance(accent()) > 0.62f ? 0xFF14121D : Colors.WHITE;
    }

    /** A floating glass panel: soft shadow, tinted fill, hairline border. */
    public static void panel(Canvas c, float x, float y, float w, float h, float radius) {
        c.shadow(x, y + 6, w, h, radius, 22, 0x66000000);
        c.rect(x, y, w, h, radius, GLASS);
        c.gradientV(x, y, w, h, radius, 0x0FFFFFFF, 0x00FFFFFF);
        c.stroke(x, y, w, h, radius, 1, BORDER);
    }

    /** A compact chip used for HUD elements. */
    public static void chip(Canvas c, float x, float y, float w, float h, float radius, int fill) {
        c.rect(x, y, w, h, radius, fill);
        c.stroke(x, y, w, h, radius, 1, 0x14FFFFFF);
    }

    /** The accent gradient fill used for primary buttons and active toggles. */
    public static void accentFill(Canvas c, float x, float y, float w, float h, float radius) {
        c.gradientH(x, y, w, h, radius, accent(), accent2());
    }

    /** The animated menu backdrop, for Aller screens shown outside a world (where there is nothing to blur). */
    public static void scene(Canvas c, float w, float h) {
        scene(c, w, h, 0);
    }

    /** @param lift 0 for the usual quieter backdrop, up to 1 for the main menu's full strength */
    public static void scene(Canvas c, float w, float h, float lift) {
        var opt = AllerClient.options();
        c.rect(0, 0, w, h, 0, BG);
        c.backdrop(0, 0, w, h, accent(), opt.backdropIntensity.get() * (0.7f + 0.3f * lift), dev.aller.ui.anim.Motion.time(), opt.backdropCell.get());
    }

    /**
     * The layer between an Aller menu and whatever is behind it, following the "Behind menus" option.
     *
     * @param dim how dark the layer is when the scene behind is blurred
     */
    public static void veil(Canvas c, float w, float h, float fade, float dim) {
        switch (AllerClient.options().background.get()) {
            case BLUR -> c.rect(0, 0, w, h, 0, Colors.withAlpha(0xFF050409, dim * fade));
            case DARKEN -> c.rect(0, 0, w, h, 0, Colors.withAlpha(0xFF050409, Math.min(0.85f, dim + 0.32f) * fade));
            case SOLID -> c.rect(0, 0, w, h, 0, Colors.withAlpha(BG, fade));
            case NONE -> {}
        }
    }

    /** A small keyboard-key chip; returns its width. */
    public static float keycap(Canvas c, String label, float x, float y, float h) {
        float size = h * 0.56f;
        float w = Math.max(h, dev.aller.ui.font.Fonts.SEMIBOLD.width(label, size) + h * 0.6f);
        c.rect(x, y, w, h, h * 0.28f, 0x1CFFFFFF);
        c.stroke(x, y, w, h, h * 0.28f, 1, BORDER);
        c.textMiddle(dev.aller.ui.font.Fonts.SEMIBOLD, label, x + (w - dev.aller.ui.font.Fonts.SEMIBOLD.width(label, size)) / 2, y, h, size, TEXT_DIM);
        return w;
    }
}
