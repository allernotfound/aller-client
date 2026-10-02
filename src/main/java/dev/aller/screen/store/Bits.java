package dev.aller.screen.store;

import dev.aller.feature.store.Images;
import dev.aller.feature.store.Modrinth.Project;
import dev.aller.platform.Canvas;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.font.Fonts;

/** Pieces the store's panes share: pictures with their stand-ins, stat lines, chips. */
final class Bits {
    /** Modrinth's green, for the credit and nothing else. */
    static final int MODRINTH = 0xFF1BD96A;

    private Bits() {}

    /** Screen pixels a length in this screen's units covers, for asking for pictures at the size they are drawn. */
    static float px(float units) {
        return units * dev.aller.ui.AllerScreen.density();
    }

    /** A project's icon, or a tinted tile with a box on it while there is none. */
    static void icon(Canvas c, Project p, float x, float y, float size) {
        float radius = size * 0.22f;
        Images.Image image = p.iconUrl == null ? null : Images.get(p.iconUrl, px(size));
        if (image != null && image.ready()) {
            c.picture(image.tex, x, y, size, size, radius, true);
            return;
        }
        int tint = tint(p);
        c.rect(x, y, size, size, radius, Colors.withAlpha(tint, 0.28f));
        c.stroke(x, y, size, size, radius, 1, Colors.withAlpha(tint, 0.35f));
        if (image == null || image.failed) Icons.PACKAGE.draw(c, x + size / 2, y + size / 2, size * 0.5f, Colors.withAlpha(Colors.WHITE, 0.6f));
        else wait(c, x, y, size, size, radius);
    }

    /** The colour Modrinth took from the icon, or the accent. */
    static int tint(Project p) {
        return p.color != 0 ? p.color | 0xFF000000 : Theme.accent();
    }

    /**
     * A picture filling a box (cropped to it), or a breathing block until it has loaded.
     *
     * @return true once the picture itself was drawn
     */
    static boolean cover(Canvas c, String url, float x, float y, float w, float h, float radius) {
        Images.Image image = url == null ? null : Images.get(url, px(Math.max(w, h)));
        if (image != null && image.ready()) {
            c.picture(image.tex, x, y, w, h, radius, true);
            return true;
        }
        if (image != null && !image.failed) wait(c, x, y, w, h, radius);
        return false;
    }

    static void wait(Canvas c, float x, float y, float w, float h, float radius) {
        float pulse = 0.05f + 0.035f * (float) Math.sin(Motion.time() * 4 + x * 0.03f);
        c.rect(x, y, w, h, radius, Colors.withAlpha(Colors.WHITE, pulse));
    }

    /** An icon and a figure, as in "↓ 48.2M". @return the width used */
    static float stat(Canvas c, Icons icon, String text, float x, float y, float size, int color) {
        icon.draw(c, x + size * 0.5f, y + Fonts.REGULAR.height(size) / 2, size * 1.05f, color);
        return size * 1.45f + c.text(Fonts.MEDIUM, text, x + size * 1.45f, y, size, color);
    }

    static float statWidth(String text, float size) {
        return size * 1.45f + Fonts.MEDIUM.width(text, size);
    }

    /** A small rounded label. @return its width */
    static float chip(Canvas c, String text, float x, float y, float size, int fill, int color) {
        float w = Fonts.MEDIUM.width(text, size) + size * 1.1f, h = size * 1.75f;
        c.rect(x, y, w, h, h / 2, fill);
        c.textMiddle(Fonts.MEDIUM, text, x + size * 0.55f, y, h, size, color);
        return w;
    }

    static float chipWidth(String text, float size) {
        return Fonts.MEDIUM.width(text, size) + size * 1.1f;
    }

    /** Three dots taking turns, for something on its way. */
    static void dots(Canvas c, float cx, float cy, int color) {
        for (int i = 0; i < 3; i++) {
            float beat = 0.5f + 0.5f * (float) Math.sin(Motion.time() * 6 - i * 0.9f);
            c.circle(cx + (i - 1) * 7, cy, 1.4f + beat, Colors.withAlpha(color, 0.35f + 0.5f * beat));
        }
    }

    /** "Vanilla-like" for "vanilla-like". */
    static String label(String category) {
        if (category.isEmpty()) return category;
        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
    }
}
