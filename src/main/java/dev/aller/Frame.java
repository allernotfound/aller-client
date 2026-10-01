package dev.aller;

import dev.aller.dev.DevHarness;
import dev.aller.feature.Clicks;
import dev.aller.feature.Replay;
import dev.aller.feature.Waypoints;
import dev.aller.hud.Hud;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.screen.HudEditorScreen;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;

/** Per-frame entry points, called from the render mixins. */
public final class Frame {
    private Frame() {}

    public static void begin() {
        Motion.frame();
        Clicks.frame();
    }

    public static void end() {
        Replay.frameEnd();
        DevHarness.frameEnd();
    }

    /** In-game HUD layer. Runs after vanilla's HUD, before any open screen. */
    public static void hud(Canvas c) {
        if (Mc.mc().player == null || Mc.hudHidden()) return;
        // The HUD editor draws the elements itself so it can move them around.
        boolean editing = Mc.current() instanceof HudEditorScreen;
        if (!editing) {
            if (Modules.WAYPOINTS.enabled()) Waypoints.draw(c);
            if (Modules.CROSSHAIR.enabled() && Mc.screen() == null) Modules.CROSSHAIR.draw(c);
            Hud.draw(c, false, null);
        }
        // Aller screens draw toasts themselves so they appear above the blur.
        if (Mc.current() == null) Toasts.draw(c);
    }
}
