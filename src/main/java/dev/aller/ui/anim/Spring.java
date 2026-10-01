package dev.aller.ui.anim;

/**
 * A damped spring that chases a target value. Retargeting mid-flight keeps the current velocity,
 * which is what makes interrupted animations (hover in/out, open/close) feel continuous.
 */
public final class Spring {
    private float value;
    private float velocity;
    private float target;
    private final float stiffness;
    private final float damping;

    public Spring(float initial, float stiffness, float damping) {
        this.value = this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    /** Quick with a slight overshoot: the default for buttons, toggles and panels. */
    public static Spring snappy(float initial) {
        return new Spring(initial, 420f, 30f);
    }

    /** Noticeable overshoot, for things that should "pop" (toasts, palette opening). */
    public static Spring bouncy(float initial) {
        return new Spring(initial, 320f, 18f);
    }

    /** Critically damped, no overshoot: for opacity and colour, where overshoot looks like a glitch. */
    public static Spring smooth(float initial) {
        return new Spring(initial, 260f, 32f);
    }

    public Spring target(float target) {
        this.target = target;
        return this;
    }

    public float target() {
        return target;
    }

    public Spring snap(float v) {
        value = target = v;
        velocity = 0;
        return this;
    }

    /** Advances the simulation by this frame's delta and returns the new value. */
    public float update() {
        if (Motion.reduced()) {
            value = target;
            velocity = 0;
            return value;
        }
        float remaining = Motion.delta();
        // Fixed sub-steps keep the integration stable at low frame rates.
        while (remaining > 0) {
            float dt = Math.min(remaining, 1f / 120f);
            float accel = stiffness * (target - value) - damping * velocity;
            velocity += accel * dt;
            value += velocity * dt;
            remaining -= dt;
        }
        if (Math.abs(target - value) < 0.0005f && Math.abs(velocity) < 0.0005f) {
            value = target;
            velocity = 0;
        }
        return value;
    }

    public float get() {
        return value;
    }

    public boolean settled() {
        return value == target && velocity == 0;
    }
}
