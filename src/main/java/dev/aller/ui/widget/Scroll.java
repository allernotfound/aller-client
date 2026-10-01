package dev.aller.ui.widget;

import dev.aller.platform.Canvas;
import dev.aller.ui.Colors;
import dev.aller.ui.anim.Spring;

/** Smooth scrolling state for a clipped list, with a fading scrollbar thumb. */
public final class Scroll {
    private final Spring offset = new Spring(0, 300f, 34f);
    private final Spring barAlpha = Spring.smooth(0);
    private float target;
    private float max;
    private float idle = 10;

    /** Call each frame with the content and viewport heights; returns the current scroll offset. */
    public float update(float contentHeight, float viewHeight) {
        max = Math.max(0, contentHeight - viewHeight);
        target = Math.clamp(target, 0, max);
        idle += dev.aller.ui.anim.Motion.delta();
        return offset.target(target).update();
    }

    public void scroll(float wheel) {
        target = Math.clamp(target - wheel * 28f, 0, max);
        idle = 0;
    }

    /** Scrolls the minimum distance needed to bring a row into view. */
    public void reveal(float rowTop, float rowHeight, float viewHeight) {
        if (rowTop < target) target = rowTop;
        else if (rowTop + rowHeight > target + viewHeight) target = rowTop + rowHeight - viewHeight;
        idle = 0;
    }

    public void reset() {
        target = 0;
        offset.snap(0);
    }

    public float get() {
        return offset.get();
    }

    public void drawBar(Canvas c, float x, float y, float viewHeight) {
        float a = barAlpha.target(max > 0 && idle < 1.2f ? 1 : 0).update();
        if (a < 0.01f || max <= 0) return;
        float content = viewHeight + max;
        float thumb = Math.max(18, viewHeight * viewHeight / content);
        float ty = y + (viewHeight - thumb) * Math.clamp(offset.get() / max, 0f, 1f);
        c.rect(x, ty, 2.5f, thumb, 1.25f, Colors.withAlpha(Colors.WHITE, 0.28f * a));
    }
}
