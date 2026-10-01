package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.LoadingInfo;
import dev.aller.platform.Mc;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.anim.Tween;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Restyles vanilla's connecting and world-loading screens. Those screens own real logic (the
 * connection attempt, chunk progress), so they are left running and Aller paints over them: the
 * menu backdrop, the status in smooth type, an animated bar, and Aller-styled stand-ins drawn
 * exactly over the vanilla buttons so clicks still land on the real ones underneath.
 */
public final class LoadingSkin {
    private static final String[] TIPS = {
            "Press %KEY% in game to open the palette and search every mod.",
            "In the palette, press → on a mod to open its settings.",
            "Drag HUD elements anywhere in the HUD editor. They snap to edges and to each other.",
            "Profiles can switch by themselves when you join a server or enter combat.",
            "Instant replay keeps the last moments in memory. One key saves a clip.",
            "Scroll while zooming to zoom further.",
            "Right-click a slider or colour to reset it.",
            "Your last few deaths are saved as waypoints automatically.",
    };

    private static final class State {
        final Tween in = new Tween(0.45f, Easing.OUT_CUBIC);
        final Spring bar = new Spring(0, 120f, 22f);
        final Map<AbstractWidget, Button> buttons = new WeakHashMap<>();
        final float born = Motion.time();
        final int tipOffset = (int) (System.nanoTime() / 7 % TIPS.length);
    }

    private static final Map<Screen, State> states = new WeakHashMap<>();

    private LoadingSkin() {}

    /** Called after every vanilla screen has drawn; does nothing unless it is one of the loading screens. */
    public static void draw(Canvas c, Screen screen) {
        if (!AllerClient.options().customLoading.get()) return;
        LoadingInfo info = LoadingInfo.of(screen);
        if (info == null) return;
        State st = states.computeIfAbsent(screen, s -> new State());
        float w = c.width(), h = c.height(), now = Motion.time();
        float in = st.in.update();

        c.layer();
        Theme.scene(c, w, h);
        c.gradientV(0, 0, w, h * 0.45f, 0, 0x0007060B, 0xA607060B);
        c.gradientV(0, h * 0.45f, w, h * 0.55f, 0, 0xA607060B, 0x0007060B);

        c.pushAlpha(in);
        float cy = h * 0.40f + (1 - in) * 8;
        c.textCentered(Fonts.BOLD, Fonts.BOLD.truncate(info.title(), 15f, w - 40), w / 2, cy - 22, 15f, Theme.TEXT);
        if (!info.detail().isEmpty()) {
            c.textCentered(Fonts.REGULAR, Fonts.REGULAR.truncate(info.detail(), 8.5f, w - 40), w / 2, cy - 2, 8.5f, Theme.TEXT_DIM);
        }

        float bw = Math.min(200, w - 80), bx = (w - bw) / 2, by = cy + 16;
        c.rect(bx, by, bw, 2.5f, 1.25f, 0x22FFFFFF);
        if (info.progress() >= 0) {
            float p = Math.clamp(st.bar.target(info.progress()).update(), 0f, 1f);
            if (p > 0.004f) {
                c.shadow(bx, by, bw * p, 2.5f, 1.25f, 7, Colors.withAlpha(Theme.accent(), 0.55f));
                c.gradientH(bx, by, bw * p, 2.5f, 1.25f, Theme.accent2(), Theme.accent());
            }
            c.textCentered(Fonts.MEDIUM, Math.round(p * 100) + "%", w / 2, by + 8, 7f, Theme.TEXT_MUTED);
        } else {
            // No measurable progress: a highlight glides back and forth along the track.
            float phase = (now - st.born) * 1.15f;
            float t = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI);
            float seg = bw * (0.22f + 0.12f * (float) Math.sin(phase * Math.PI));
            float sx = bx + (bw - seg) * t;
            c.shadow(sx, by, seg, 2.5f, 1.25f, 7, Colors.withAlpha(Theme.accent(), 0.55f));
            c.gradientH(sx, by, seg, 2.5f, 1.25f, Theme.accent2(), Theme.accent());
        }

        // Stand-ins for the vanilla buttons (Cancel and the like).
        float mx = Mc.mouseX(), my = Mc.mouseY();
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            Button skin = st.buttons.computeIfAbsent(widget, k -> new Button("", () -> {}));
            skin.label = widget.getMessage().getString();
            skin.enabled = widget.active;
            skin.textSize = 8.5f;
            skin.bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            skin.draw(c, mx, my);
        }

        // A tip that changes every few seconds.
        float age = now - st.born;
        int index = (int) (age / 7f);
        float local = age - index * 7f;
        float tipAlpha = Math.clamp(Math.min(local / 0.5f, (7f - local) / 0.5f), 0f, 1f);
        String tip = TIPS[(index + st.tipOffset) % TIPS.length].replace("%KEY%", Mc.keyName(AllerClient.options().menuKey.get()));
        c.pushAlpha(tipAlpha * 0.9f);
        c.textCentered(Fonts.REGULAR, Fonts.REGULAR.truncate(tip, 7.5f, w - 30), w / 2, h - 22, 7.5f, Theme.TEXT_MUTED);
        c.popAlpha();
        c.popAlpha();
    }
}
