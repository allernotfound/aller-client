package dev.aller.ui;

import dev.aller.platform.Canvas;

/** Small chart primitives shared by the live HUD graph and the stats dashboard. */
public final class Graph {
    private Graph() {}

    /** A line chart with a soft fill underneath, auto-scaled to the data's range. */
    public static void line(Canvas c, float x, float y, float w, float h, float[] data, int color) {
        c.rect(x, y + h - 0.5f, w, 0.5f, 0, 0x22FFFFFF);
        if (data.length < 2) return;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // Pad the range so a flat series sits mid-chart instead of on an edge.
        float range = Math.max(max - min, Math.max(1f, max * 0.1f));
        float lo = Math.max(0, min - range * 0.15f), hi = max + range * 0.15f;

        // Cap the number of segments so long windows stay cheap to draw.
        int points = Math.min(data.length, Math.max(2, (int) (w / 2.5f)));
        float px = 0, py = 0;
        for (int i = 0; i < points; i++) {
            float t = i / (float) (points - 1);
            float v = sample(data, t);
            float cx = x + t * w;
            float cy = y + h - (v - lo) / (hi - lo) * h;
            if (i > 0) {
                // Fill: a column under each segment, fading towards the baseline.
                float top = Math.min(py, cy);
                c.gradientV(px, top, cx - px + 0.4f, y + h - top, 0, Colors.withAlpha(color, 0.22f), Colors.withAlpha(color, 0f));
                c.line(px, py, cx, cy, 1.3f, color);
            }
            px = cx;
            py = cy;
        }
        c.circle(px, py, 1.9f, Colors.lighten(color, 0.5f));
    }

    private static float sample(float[] data, float t) {
        float pos = t * (data.length - 1);
        int i = (int) pos;
        if (i >= data.length - 1) return data[data.length - 1];
        float f = pos - i;
        return data[i] + (data[i + 1] - data[i]) * f;
    }

    /** Vertical bars normalised to the largest value, for session history. */
    public static void bars(Canvas c, float x, float y, float w, float h, float[] data, int color, int highlight) {
        if (data.length == 0) return;
        float max = 1;
        for (float v : data) max = Math.max(max, v);
        float slot = w / data.length;
        float bw = Math.max(1.5f, Math.min(10, slot * 0.7f));
        for (int i = 0; i < data.length; i++) {
            float bh = Math.max(1.5f, data[i] / max * h);
            float bx = x + i * slot + (slot - bw) / 2;
            c.rect(bx, y + h - bh, bw, bh, Math.min(2, bw / 2), i == highlight ? Colors.lighten(color, 0.35f) : Colors.withAlpha(color, 0.7f));
        }
    }
}
