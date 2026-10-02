package dev.aller.ui;

import dev.aller.platform.Canvas;

/**
 * Small line icons drawn with Canvas primitives, so they stay crisp at any scale and never depend
 * on a glyph the bundled font might not have.
 */
public enum Icons {
    CLOSE, ROWS, GRID, REALMS, MODS, QUIT, ADVANCEMENTS, LAN, REPORT, FEEDBACK, BUG, EDIT, PLUS, SETTINGS, WARDROBE, FOLDER, UPLOAD,
    BACK, FORWARD, RELOAD, STAR, CLOCK, SEARCH, SOUND, MUTED, PRIVATE;

    /** Draws the icon centred on (cx, cy) inside a square of the given size. */
    public void draw(Canvas c, float cx, float cy, float size, int color) {
        float u = size / 12f;          // icons are designed on a 12-unit grid
        float w = Math.max(1f, 1.25f * u);
        switch (this) {
            case CLOSE -> {
                c.line(cx - 3.2f * u, cy - 3.2f * u, cx + 3.2f * u, cy + 3.2f * u, w, color);
                c.line(cx + 3.2f * u, cy - 3.2f * u, cx - 3.2f * u, cy + 3.2f * u, w, color);
            }
            case PLUS -> {
                c.line(cx - 3.6f * u, cy, cx + 3.6f * u, cy, w, color);
                c.line(cx, cy - 3.6f * u, cx, cy + 3.6f * u, w, color);
            }
            case ROWS -> {
                for (int i = -1; i <= 1; i++) c.line(cx - 4 * u, cy + i * 3.4f * u, cx + 4 * u, cy + i * 3.4f * u, w, color);
            }
            case GRID -> {
                float s = 3.6f * u, g = 0.9f * u;
                for (int i = 0; i < 4; i++) {
                    float x = i % 2 == 0 ? cx - g - s : cx + g, y = i < 2 ? cy - g - s : cy + g;
                    c.rect(x, y, s, s, 0.9f * u, color);
                }
            }
            case REALMS -> {
                // A globe: outline, equator and a meridian.
                c.ring(cx, cy, 4.6f * u, w, color);
                c.line(cx - 4.2f * u, cy, cx + 4.2f * u, cy, w * 0.85f, color);
                c.stroke(cx - 2.1f * u, cy - 4.6f * u, 4.2f * u, 9.2f * u, 2.1f * u, w * 0.85f, color);
            }
            case MODS -> {
                // Stacked blocks with one being added.
                float s = 4f * u;
                c.stroke(cx - 4.6f * u, cy + 0.4f * u, s, s, u, w, color);
                c.stroke(cx + 0.6f * u, cy + 0.4f * u, s, s, u, w, color);
                c.stroke(cx - 4.6f * u, cy - 4.6f * u, s, s, u, w, color);
                c.line(cx + 2.6f * u, cy - 4.4f * u, cx + 2.6f * u, cy - 1f * u, w, color);
                c.line(cx + 0.9f * u, cy - 2.7f * u, cx + 4.3f * u, cy - 2.7f * u, w, color);
            }
            case QUIT -> {
                // A doorway with an arrow leaving it.
                c.line(cx - 0.5f * u, cy - 4.4f * u, cx - 4.2f * u, cy - 4.4f * u, w, color);
                c.line(cx - 4.2f * u, cy - 4.4f * u, cx - 4.2f * u, cy + 4.4f * u, w, color);
                c.line(cx - 4.2f * u, cy + 4.4f * u, cx - 0.5f * u, cy + 4.4f * u, w, color);
                c.line(cx - 1f * u, cy, cx + 4.6f * u, cy, w, color);
                c.line(cx + 2.2f * u, cy - 2.4f * u, cx + 4.6f * u, cy, w, color);
                c.line(cx + 2.2f * u, cy + 2.4f * u, cx + 4.6f * u, cy, w, color);
            }
            case ADVANCEMENTS -> c.star(cx, cy + 0.3f * u, 5.4f * u, color);
            case LAN -> {
                // Three linked nodes.
                c.line(cx - 3.4f * u, cy + 2.6f * u, cx, cy - 3.2f * u, w * 0.85f, color);
                c.line(cx + 3.4f * u, cy + 2.6f * u, cx, cy - 3.2f * u, w * 0.85f, color);
                c.line(cx - 3.4f * u, cy + 2.6f * u, cx + 3.4f * u, cy + 2.6f * u, w * 0.85f, color);
                c.circle(cx, cy - 3.2f * u, 1.9f * u, color);
                c.circle(cx - 3.4f * u, cy + 2.6f * u, 1.9f * u, color);
                c.circle(cx + 3.4f * u, cy + 2.6f * u, 1.9f * u, color);
            }
            case REPORT -> {
                // A flag.
                c.line(cx - 3.6f * u, cy - 4.6f * u, cx - 3.6f * u, cy + 4.8f * u, w, color);
                c.stroke(cx - 3.6f * u, cy - 4.2f * u, 7.6f * u, 5f * u, 1.1f * u, w, color);
            }
            case FEEDBACK -> {
                // A speech bubble.
                c.stroke(cx - 4.8f * u, cy - 4.4f * u, 9.6f * u, 7f * u, 2f * u, w, color);
                c.line(cx - 2.2f * u, cy + 2.6f * u, cx - 3.4f * u, cy + 4.8f * u, w, color);
                c.line(cx - 3.4f * u, cy + 4.8f * u, cx - 0.2f * u, cy + 2.6f * u, w, color);
            }
            case BUG -> {
                c.stroke(cx - 2.4f * u, cy - 2.6f * u, 4.8f * u, 7.2f * u, 2.4f * u, w, color);
                c.line(cx - 1.4f * u, cy - 3.2f * u, cx - 2.6f * u, cy - 4.8f * u, w * 0.85f, color);
                c.line(cx + 1.4f * u, cy - 3.2f * u, cx + 2.6f * u, cy - 4.8f * u, w * 0.85f, color);
                for (int i = -1; i <= 1; i++) {
                    float y = cy + 1f * u + i * 2.3f * u;
                    c.line(cx - 2.6f * u, y, cx - 4.8f * u, y + i * 1.1f * u, w * 0.85f, color);
                    c.line(cx + 2.6f * u, y, cx + 4.8f * u, y + i * 1.1f * u, w * 0.85f, color);
                }
            }
            case SETTINGS -> {
                // A cog: a ring with six teeth.
                c.ring(cx, cy, 2.9f * u, w, color);
                for (int i = 0; i < 6; i++) {
                    float dx = (float) Math.cos(i * Math.PI / 3), dy = (float) Math.sin(i * Math.PI / 3);
                    c.line(cx + dx * 3.5f * u, cy + dy * 3.5f * u, cx + dx * 4.8f * u, cy + dy * 4.8f * u, w * 1.15f, color);
                }
            }
            case WARDROBE -> {
                // A coat hanger.
                c.ring(cx, cy - 3.7f * u, 1.3f * u, w * 0.85f, color);
                c.line(cx, cy - 2.4f * u, cx - 4.8f * u, cy + 2.4f * u, w, color);
                c.line(cx, cy - 2.4f * u, cx + 4.8f * u, cy + 2.4f * u, w, color);
                c.line(cx - 4.8f * u, cy + 2.4f * u, cx + 4.8f * u, cy + 2.4f * u, w, color);
            }
            case FOLDER -> {
                c.stroke(cx - 4.8f * u, cy - 2.8f * u, 9.6f * u, 7.2f * u, 1.3f * u, w, color);
                c.line(cx - 4.2f * u, cy - 4.2f * u, cx - 0.8f * u, cy - 4.2f * u, w, color);
            }
            case UPLOAD -> {
                // An arrow rising from a tray.
                c.line(cx, cy + 1.6f * u, cx, cy - 4.4f * u, w, color);
                c.line(cx - 2.6f * u, cy - 1.9f * u, cx, cy - 4.4f * u, w, color);
                c.line(cx + 2.6f * u, cy - 1.9f * u, cx, cy - 4.4f * u, w, color);
                c.line(cx - 4.2f * u, cy + 4.4f * u, cx + 4.2f * u, cy + 4.4f * u, w, color);
            }
            case BACK, FORWARD -> {
                float d = this == BACK ? -1 : 1;
                c.line(cx - 4 * u * d, cy, cx + 4 * u * d, cy, w, color);
                c.line(cx + 4 * u * d, cy, cx + 0.8f * u * d, cy - 3.2f * u, w, color);
                c.line(cx + 4 * u * d, cy, cx + 0.8f * u * d, cy + 3.2f * u, w, color);
            }
            case RELOAD -> {
                // Most of a circle, ending in an arrowhead.
                float r = 3.8f * u;
                int steps = 10;
                double from = -0.25 * Math.PI, sweep = 1.55 * Math.PI;
                for (int i = 0; i < steps; i++) {
                    double a = from + sweep * i / steps, b = from + sweep * (i + 1) / steps;
                    c.line(cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a), cx + r * (float) Math.cos(b), cy + r * (float) Math.sin(b), w, color);
                }
                float ex = cx + r * (float) Math.cos(from), ey = cy + r * (float) Math.sin(from);
                c.line(ex, ey, ex + 0.4f * u, ey - 3f * u, w, color);
                c.line(ex, ey, ex - 3f * u, ey - 0.2f * u, w, color);
            }
            case STAR -> c.star(cx, cy + 0.3f * u, 5.2f * u, color);
            case CLOCK -> {
                c.ring(cx, cy, 4.4f * u, w, color);
                c.line(cx, cy, cx, cy - 2.6f * u, w, color);
                c.line(cx, cy, cx + 2f * u, cy + 1.2f * u, w, color);
            }
            case SEARCH -> {
                c.ring(cx - 1f * u, cy - 1f * u, 3.3f * u, w, color);
                c.line(cx + 1.5f * u, cy + 1.5f * u, cx + 4.4f * u, cy + 4.4f * u, w * 1.15f, color);
            }
            case SOUND, MUTED -> {
                // A speaker, with a wave or a cross beside it.
                c.rect(cx - 4.6f * u, cy - 1.5f * u, 2.6f * u, 3f * u, 0.5f * u, color);
                c.line(cx - 2.2f * u, cy - 1.4f * u, cx + 0.2f * u, cy - 3.6f * u, w, color);
                c.line(cx - 2.2f * u, cy + 1.4f * u, cx + 0.2f * u, cy + 3.6f * u, w, color);
                c.line(cx + 0.2f * u, cy - 3.6f * u, cx + 0.2f * u, cy + 3.6f * u, w, color);
                if (this == SOUND) {
                    c.line(cx + 2.4f * u, cy - 1.6f * u, cx + 2.4f * u, cy + 1.6f * u, w * 0.85f, color);
                    c.line(cx + 4.4f * u, cy - 2.8f * u, cx + 4.4f * u, cy + 2.8f * u, w * 0.85f, color);
                } else {
                    c.line(cx + 2f * u, cy - 1.8f * u, cx + 5f * u, cy + 1.8f * u, w * 0.85f, color);
                    c.line(cx + 5f * u, cy - 1.8f * u, cx + 2f * u, cy + 1.8f * u, w * 0.85f, color);
                }
            }
            case PRIVATE -> {
                // A hat brim over a pair of glasses.
                c.line(cx - 5f * u, cy - 1.2f * u, cx + 5f * u, cy - 1.2f * u, w, color);
                c.rect(cx - 2.8f * u, cy - 4.6f * u, 5.6f * u, 3.4f * u, 1.2f * u, color);
                c.ring(cx - 2.5f * u, cy + 2.4f * u, 1.7f * u, w * 0.85f, color);
                c.ring(cx + 2.5f * u, cy + 2.4f * u, 1.7f * u, w * 0.85f, color);
                c.line(cx - 0.8f * u, cy + 2.4f * u, cx + 0.8f * u, cy + 2.4f * u, w * 0.85f, color);
            }
            case EDIT -> {
                // A pencil.
                c.line(cx - 3.6f * u, cy + 3.6f * u, cx + 2.6f * u, cy - 2.6f * u, w * 1.5f, color);
                c.line(cx + 3.6f * u, cy - 3.9f * u, cx + 3.9f * u, cy - 3.6f * u, w * 1.5f, color);
                c.line(cx - 4.6f * u, cy + 4.6f * u, cx - 3.9f * u, cy + 3.9f * u, w * 0.7f, color);
            }
        }
    }
}
