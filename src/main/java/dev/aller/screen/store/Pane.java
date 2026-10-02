package dev.aller.screen.store;

import dev.aller.platform.Canvas;
import dev.aller.ui.Theme;

/** One of the store's views. The screen gives it bounds each frame and passes input on while it is in front. */
abstract class Pane {
    final StoreScreen screen;
    float x, y, w, h;

    Pane(StoreScreen screen) {
        this.screen = screen;
    }

    final void bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    abstract void draw(Canvas c, float mx, float my);

    /** Drawn after everything else, unclipped: a lightbox, tooltips. */
    void overlay(Canvas c, float mx, float my) {}

    boolean mouseDown(float mx, float my, int button) {
        return false;
    }

    void mouseUp(float mx, float my, int button) {}

    void scroll(float mx, float my, float amount) {}

    /** @return true if the key was used; Escape that is not used goes back or closes */
    boolean keyDown(int key, int mods) {
        return false;
    }

    boolean charTyped(int codepoint) {
        return false;
    }

    /** True while a text field in the pane has the keyboard. */
    boolean typing() {
        return false;
    }

    /** The pane is in front again (after a page was closed, or the screen came back from another). */
    void shown() {}

    static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** A quiet panel for a column or a list: no shadow, a hairline border. */
    static void surface(Canvas c, float x, float y, float w, float h, float radius) {
        c.rect(x, y, w, h, radius, 0xA60C0B13);
        c.gradientV(x, y, w, h, radius, 0x0CFFFFFF, 0x00FFFFFF);
        c.stroke(x, y, w, h, radius, 1, Theme.BORDER);
    }
}
