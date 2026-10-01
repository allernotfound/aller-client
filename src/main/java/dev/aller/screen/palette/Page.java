package dev.aller.screen.palette;

import dev.aller.platform.Canvas;

/**
 * A view that slides in over the palette's search list: a module's settings, client options,
 * profiles, waypoints or session stats. The palette draws the header and back button; the page
 * draws everything below it.
 */
public abstract class Page {
    public abstract String title();

    public String subtitle() {
        return "";
    }

    /**
     * Draws the page body. The palette has already clipped to this rectangle.
     *
     * @param hot whether the mouse is inside the body (false disables hover effects)
     */
    public abstract void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot);

    /** Draws an optional control at the right end of the header (e.g. a module's switch). */
    public void header(Canvas c, float right, float y, float h, float mx, float my) {}

    public boolean headerClick(float mx, float my, int button) {
        return false;
    }

    public boolean mouseDown(float mx, float my, int button) {
        return false;
    }

    public boolean mouseUp(float mx, float my, int button) {
        return false;
    }

    public boolean mouseScroll(float mx, float my, float amount) {
        return false;
    }

    public boolean keyDown(int key, int mods) {
        return false;
    }

    public boolean charTyped(int codepoint) {
        return false;
    }

    /** True while the page wants raw keyboard input (text field focus, key capture). */
    public boolean capturing() {
        return false;
    }
}
