package dev.aller.ui.anim;

/** A fixed-duration eased 0..1 progress, for staged sequences where timing must be exact. */
public final class Tween {
    private final float duration;
    private final Easing easing;
    private float delay;
    private float elapsed;
    private boolean forward = true;

    public Tween(float durationSeconds, Easing easing) {
        this.duration = durationSeconds;
        this.easing = easing;
    }

    public Tween delay(float seconds) {
        this.delay = seconds;
        this.elapsed = -seconds;
        return this;
    }

    public Tween restart() {
        elapsed = -delay;
        forward = true;
        return this;
    }

    public Tween reverse() {
        forward = false;
        return this;
    }

    public Tween forward() {
        forward = true;
        return this;
    }

    public float update() {
        if (Motion.reduced()) {
            elapsed = forward ? duration : 0;
        } else if (forward) {
            elapsed = Math.min(duration, elapsed + Motion.delta());
        } else {
            elapsed = Math.max(0, elapsed - Motion.delta());
        }
        return get();
    }

    public float get() {
        float t = duration <= 0 ? 1 : Math.clamp(elapsed / duration, 0f, 1f);
        return easing.apply(t);
    }

    public boolean finished() {
        return forward ? elapsed >= duration : elapsed <= 0;
    }
}
