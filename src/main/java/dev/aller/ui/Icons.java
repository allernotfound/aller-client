package dev.aller.ui;

import dev.aller.platform.Canvas;

/**
 * Small line icons drawn with Canvas primitives, so they stay crisp at any scale and never depend
 * on a glyph the bundled font might not have.
 */
public enum Icons {
    CLOSE, ROWS, GRID, REALMS, MODS, QUIT, ADVANCEMENTS, LAN, REPORT, FEEDBACK, BUG, EDIT, PLUS;

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
            case EDIT -> {
                // A pencil.
                c.line(cx - 3.6f * u, cy + 3.6f * u, cx + 2.6f * u, cy - 2.6f * u, w * 1.5f, color);
                c.line(cx + 3.6f * u, cy - 3.9f * u, cx + 3.9f * u, cy - 3.6f * u, w * 1.5f, color);
                c.line(cx - 4.6f * u, cy + 4.6f * u, cx - 3.9f * u, cy + 3.9f * u, w * 0.7f, color);
            }
        }
    }
}
