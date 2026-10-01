package dev.aller.ui.widget;

import dev.aller.platform.Canvas;

/** Base for retained widgets. The owner assigns bounds each frame, then forwards draw and input. */
public abstract class Widget {
    public float x, y, w, h;

    public Widget bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public boolean hit(float mx, float my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public abstract void draw(Canvas c, float mx, float my);

    public boolean mouseDown(float mx, float my, int button) {
        return false;
    }

    public boolean mouseUp(float mx, float my, int button) {
        return false;
    }

    public boolean keyDown(int key, int mods) {
        return false;
    }

    public boolean charTyped(int codepoint) {
        return false;
    }
}
