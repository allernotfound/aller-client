package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Pipelines;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;

/**
 * The startup (and resource-reload) splash that stands in for the Mojang logo: the Aller wordmark
 * assembling over the menu backdrop, with a thin progress bar. Vanilla's overlay keeps doing the
 * actual loading; this only replaces what it draws.
 */
public final class Splash {
    private static final Spring bar = new Spring(0, 90f, 20f);
    private static float shownAt = -1;
    private static float lastDraw = -10;

    private Splash() {}

    public static boolean enabled() {
        return AllerClient.options().customLoading.get();
    }

    /**
     * @param progress 0..1 reload progress
     * @param alpha    1 while loading, falling to 0 as the overlay hands over to the screen beneath
     */
    public static void draw(Canvas c, float progress, float alpha) {
        float w = c.width(), h = c.height();
        float now = Motion.time();
        // A gap in drawing means this is a new overlay (e.g. a resource reload), so replay the intro.
        if (now - lastDraw > 0.5f) {
            shownAt = now;
            bar.snap(0);
        }
        lastDraw = now;
        float t = now - shownAt;

        if (!Pipelines.ready()) {
            // Shaders unavailable: a bare progress bar using vanilla fills.
            float bw = Math.min(180, w - 60), bx = (w - bw) / 2, by = h * 0.6f;
            c.pushAlpha(alpha);
            c.plainRect(bx, by, bw, 2, 0x33FFFFFF);
            c.plainRect(bx, by, bw * Math.clamp(progress, 0f, 1f), 2, Theme.accent());
            c.popAlpha();
            return;
        }

        var opt = AllerClient.options();
        float in = Easing.OUT_CUBIC.apply(Math.clamp(t / 1.2f, 0f, 1f));
        c.backdrop(0, 0, w, h, Theme.accent(), opt.backdropIntensity.get() * 0.55f * in * alpha, now, opt.backdropCell.get());
        // Keep the centre calm so the wordmark reads clearly.
        c.gradientV(0, 0, w, h * 0.5f, 0, 0x0007060B, 0xB307060B);
        c.gradientV(0, h * 0.5f, w, h * 0.5f, 0, 0xB307060B, 0x0007060B);

        c.pushAlpha(alpha);
        String word = "Aller";
        float size = Math.clamp(h * 0.14f, 22f, 44f);
        float total = Fonts.BOLD.width(word, size) + size * 0.2f;
        float pen = (w - total) / 2, y = h * 0.5f - size * 0.75f;
        for (int i = 0; i < word.length(); i++) {
            float e = Easing.OUT_CUBIC.apply(Math.clamp(t * 1.9f - i * 0.13f, 0f, 1f));
            String ch = word.substring(i, i + 1);
            c.pushAlpha(e);
            c.text(Fonts.BOLD, ch, pen, y + (1 - e) * size * 0.45f, size, Theme.TEXT);
            c.popAlpha();
            pen += Fonts.BOLD.width(ch, size) - size * 0.016f;
        }
        float dot = Easing.OUT_BACK.apply(Math.clamp(t * 1.9f - 0.75f, 0f, 1f));
        float r = size * 0.095f * dot, dx = pen + size * 0.1f, dy = y + size * 0.86f;
        c.shadow(dx - r, dy - r, r * 2, r * 2, r, size * 0.4f, Colors.withAlpha(Theme.accent(), 0.75f * dot));
        c.circle(dx, dy, r, Theme.accent());

        float rest = Math.clamp(t * 1.4f - 0.7f, 0f, 1f);
        c.pushAlpha(rest);
        float bw = Math.min(170, w - 80), bx = (w - bw) / 2, by = y + size * 1.55f;
        float p = Math.clamp(bar.target(Math.clamp(progress, 0f, 1f)).update(), 0f, 1f);
        c.rect(bx, by, bw, 2.5f, 1.25f, 0x22FFFFFF);
        if (p > 0.004f) {
            c.shadow(bx, by, bw * p, 2.5f, 1.25f, 7, Colors.withAlpha(Theme.accent(), 0.55f));
            c.gradientH(bx, by, bw * p, 2.5f, 1.25f, Theme.accent2(), Theme.accent());
        }
        c.textCentered(Fonts.MEDIUM, "Loading  " + Math.round(p * 100) + "%", w / 2, by + 9, 7f, Theme.TEXT_MUTED);
        c.popAlpha();
        c.popAlpha();
    }
}
