package dev.aller.feature;

import dev.aller.platform.Mc;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/** Counts mouse clicks per second by watching button edges once per frame. */
public final class Clicks {
    private static final ArrayDeque<Long> left = new ArrayDeque<>();
    private static final ArrayDeque<Long> right = new ArrayDeque<>();
    private static boolean leftDown, rightDown;

    private Clicks() {}

    public static void frame() {
        long now = System.currentTimeMillis();
        boolean l = Mc.isDown(-2 - GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean r = Mc.isDown(-2 - GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (l && !leftDown) left.add(now);
        if (r && !rightDown) right.add(now);
        leftDown = l;
        rightDown = r;
        while (!left.isEmpty() && now - left.peek() > 1000) left.poll();
        while (!right.isEmpty() && now - right.peek() > 1000) right.poll();
    }

    public static int left() {
        return left.size();
    }

    public static int right() {
        return right.size();
    }
}
