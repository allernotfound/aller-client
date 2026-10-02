package dev.aller.ui;

import dev.aller.platform.Canvas;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small notifications that slide in at the top right, stack, and slide away. */
public final class Toasts {
    private static final float W = 150, H = 30, GAP = 5, LIFE = 2.2f;
    private static final List<Toast> toasts = new ArrayList<>();

    private Toasts() {}

    private static final class Toast {
        final String title, body;
        final int dot;
        float age;
        final float life;
        final Spring slide = Spring.bouncy(0);
        final Spring y = Spring.snappy(0);
        boolean placed;

        Toast(String title, String body, int dot, float life) {
            this.title = title;
            this.body = body;
            this.dot = dot;
            this.life = life;
        }
    }

    public static void show(String title, String body, boolean positive) {
        // Re-toggling the same thing replaces its toast instead of stacking duplicates.
        toasts.removeIf(t -> t.title.equals(title));
        add(new Toast(title, body, positive ? Theme.accent() : Theme.TEXT_MUTED, LIFE));
    }

    public static void info(String title, String body) {
        add(new Toast(title, body, Theme.accent(), 3.2f));
    }

    public static void warn(String title, String body) {
        add(new Toast(title, body, Theme.WARN, 6f));
    }

    private static void add(Toast t) {
        toasts.add(t);
        while (toasts.size() > 5) toasts.remove(0);
    }

    public static void draw(Canvas c) {
        if (dev.aller.ui.AllerScreen.drawingUnderlay) return;
        float slot = 8;
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); ) {
            Toast t = it.next();
            t.age += Motion.realDelta();
            boolean leaving = t.age > t.life;
            float s = t.slide.target(leaving ? 0 : 1).update();
            if (leaving && s < 0.02f) {
                it.remove();
                continue;
            }
            if (!t.placed) {
                t.y.snap(slot);
                t.placed = true;
            }
            float y = t.y.target(slot).update();
            float bodySize = 7.5f;
            float w = Math.max(W, Math.max(Fonts.SEMIBOLD.width(t.title, 8.5f), Fonts.REGULAR.width(t.body, bodySize)) + 34);
            w = Math.min(w, c.width() - 16);
            float x = c.width() - 8 - w + (1 - s) * (w + 16);

            c.pushAlpha(Math.clamp(s, 0f, 1f));
            Theme.panel(c, x, y, w, H, Theme.R_MD);
            c.shadow(x + 9, y + H / 2 - 3, 6, 6, 3, 6, Colors.withAlpha(t.dot, 0.6f));
            c.circle(x + 12, y + H / 2, 3, t.dot);
            c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(t.title, 8.5f, w - 30), x + 22, y + 5, 8.5f, Theme.TEXT);
            c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(t.body, bodySize, w - 30), x + 22, y + 16.5f, bodySize, Theme.TEXT_DIM);
            // Remaining-time bar.
            float left = Math.clamp(1 - t.age / t.life, 0f, 1f);
            c.rect(x + 8, y + H - 3, (w - 16) * left, 1.2f, 0.6f, Colors.withAlpha(t.dot, 0.5f));
            c.popAlpha();
            slot += (H + GAP) * Math.clamp(s, 0f, 1f);
        }
    }
}
