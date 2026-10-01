package dev.aller.ui;

/** ARGB colour helpers. */
public final class Colors {
    private Colors() {}

    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int TRANSPARENT = 0x00000000;

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    /** Replaces the alpha channel. */
    public static int withAlpha(int color, float alpha) {
        return (Math.clamp(Math.round(alpha * 255), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    /** Multiplies the existing alpha channel. */
    public static int fade(int color, float factor) {
        return (Math.clamp(Math.round((color >>> 24) * factor), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return argb(Math.round(aa + (ba - aa) * t), Math.round(ar + (br - ar) * t),
                Math.round(ag + (bg - ag) * t), Math.round(ab + (bb - ab) * t));
    }

    public static int lighten(int color, float amount) {
        return mix(color, (color & 0xFF000000) | 0xFFFFFF, amount);
    }

    public static int darken(int color, float amount) {
        return mix(color, color & 0xFF000000, amount);
    }

    /** @param h 0..1, s 0..1, v 0..1 */
    public static int hsv(float h, float s, float v, float a) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, v);
        return withAlpha(rgb, a);
    }

    public static float[] toHsv(int color) {
        return java.awt.Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, null);
    }

    /** Relative luminance 0..1, for choosing readable text on a coloured surface. */
    public static float luminance(int color) {
        return (0.2126f * (color >> 16 & 0xFF) + 0.7152f * (color >> 8 & 0xFF) + 0.0722f * (color & 0xFF)) / 255f;
    }
}
