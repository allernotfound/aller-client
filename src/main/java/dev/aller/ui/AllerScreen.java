package dev.aller.ui;

import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.ui.anim.Spring;
import org.lwjgl.glfw.GLFW;

/**
 * A full-screen Aller UI. Version-independent: Minecraft's own Screen class changes between
 * releases, so a thin host adapts it to these callbacks.
 *
 * <p>Every screen opens and closes through {@link #openness()}, a spring from 0 to 1. Closing is
 * deferred until the spring has run back down, so exit animations always play.
 */
public abstract class AllerScreen {
    protected float width;
    protected float height;
    private final Spring open = Spring.snappy(0f);
    private boolean closing;
    private boolean finished;
    private Runnable afterClose;

    public final void resize(float w, float h) {
        width = w;
        height = h;
        layout();
    }

    /** Recompute positions; called on open and whenever the window size changes. */
    protected void layout() {}

    public void opened() {
        open.snap(0f).target(1f);
        closing = false;
        finished = false;
    }

    /** Called once the screen has actually been removed. */
    public void closed() {}

    public void tick() {}

    public final void frame(Canvas c, float mouseX, float mouseY) {
        open.update();
        if (closing && open.get() < 0.02f) {
            if (!finished) {
                finished = true;
                Runnable next = afterClose;
                // Swap screens between frames rather than in the middle of rendering one.
                dev.aller.AllerClient.defer(() -> {
                    if (next != null) next.run();
                    else if (Mc.current() == this) Mc.setScreen(null);
                });
            }
            return;
        }
        draw(c, mouseX, mouseY);
    }

    protected abstract void draw(Canvas c, float mouseX, float mouseY);

    /** True while a screen is being drawn as the blurred layer beneath another one. */
    public static boolean drawingUnderlay;

    /** A screen to keep showing, blurred, behind this one (the menu a panel was opened from), or null. */
    public AllerScreen underlay() {
        return null;
    }

    /** Draws this screen as a passive backdrop: no hover, no input, no close handling. */
    public final void drawUnder(Canvas c) {
        drawingUnderlay = true;
        try {
            draw(c, -10000, -10000);
        } finally {
            drawingUnderlay = false;
        }
    }

    /** Size multiplier for this screen; layout happens in scaled units. */
    public float scale() {
        return dev.aller.AllerClient.options().uiScale.get();
    }

    /** 0 when fully closed, 1 when fully open; may overshoot slightly. */
    protected float openness() {
        return open.get();
    }

    /** Clamped 0..1 version for opacity. */
    protected float fade() {
        return Math.clamp(open.get(), 0f, 1f);
    }

    public boolean isClosing() {
        return closing;
    }

    /** Plays the close animation, then returns to the game. */
    public void close() {
        close(null);
    }

    /** Plays the close animation, then runs {@code then} (e.g. opening another screen). */
    public void close(Runnable then) {
        if (closing) return;
        closing = true;
        afterClose = then;
        open.target(0f);
    }

    public boolean mouseDown(float x, float y, int button) {
        return false;
    }

    public boolean mouseUp(float x, float y, int button) {
        return false;
    }

    public boolean mouseScroll(float x, float y, float amount) {
        return false;
    }

    public boolean keyDown(int key, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && closeOnEscape()) {
            close();
            return true;
        }
        return false;
    }

    public boolean charTyped(int codepoint) {
        return false;
    }

    public boolean closeOnEscape() {
        return true;
    }

    public boolean pausesGame() {
        return false;
    }

    /** Whether the world behind should be blurred (frosted-glass effect). */
    public boolean blurBehind() {
        return true;
    }

    /** Whether vanilla should draw its panorama/dirt background (false: we draw our own). */
    public boolean vanillaBackground() {
        return false;
    }

    protected static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
