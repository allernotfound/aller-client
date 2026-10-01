package dev.aller.ui.anim;

/**
 * Global animation clock. Every {@link Spring} and {@link Tween} reads its delta from here, so the
 * speed slider and reduce-motion setting apply to the whole client at once.
 */
public final class Motion {
    private static long lastNanos = System.nanoTime();
    private static float delta;
    private static float time;
    private static float speed = 1f;
    private static boolean reduced;

    private Motion() {}

    /** Call once per rendered frame. */
    public static void frame() {
        long now = System.nanoTime();
        float raw = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        // Clamp so a hitch (world load, alt-tab) doesn't make springs explode.
        raw = Math.min(raw, 0.1f);
        time += raw;
        delta = raw * speed;
    }

    /** Seconds since the previous frame, scaled by the user's animation speed. */
    public static float delta() {
        return delta;
    }

    /** Unscaled seconds since startup, for looping effects such as shaders. */
    public static float time() {
        return time;
    }

    public static boolean reduced() {
        return reduced;
    }

    public static void configure(float speedMultiplier, boolean reduceMotion) {
        speed = Math.max(0.1f, speedMultiplier);
        reduced = reduceMotion;
    }
}
