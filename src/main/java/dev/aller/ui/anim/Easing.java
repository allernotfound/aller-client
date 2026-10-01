package dev.aller.ui.anim;

@FunctionalInterface
public interface Easing {
    float apply(float t);

    Easing LINEAR = t -> t;
    Easing OUT_CUBIC = t -> 1 - (float) Math.pow(1 - t, 3);
    Easing OUT_QUINT = t -> 1 - (float) Math.pow(1 - t, 5);
    Easing OUT_EXPO = t -> t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    Easing IN_CUBIC = t -> t * t * t;
    Easing IN_OUT_CUBIC = t -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    Easing OUT_BACK = t -> {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    };
}
