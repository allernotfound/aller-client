package dev.aller;

import dev.aller.command.Launcher;
import dev.aller.dev.DevHarness;
import dev.aller.feature.Chat;
import dev.aller.feature.Clicks;
import dev.aller.feature.Replay;
import dev.aller.feature.Waypoints;
import dev.aller.hud.Hud;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.screen.HudEditorScreen;
import dev.aller.screen.MenuSkin;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;

/** Per-frame entry points, called from the render mixins. */
public final class Frame {
    private Frame() {}

    public static void begin() {
        Motion.frame();
        dev.aller.feature.Shots.frameBegin();
        MenuSkin.frame();
        Clicks.frame();
        AllerClient.modules().pollKeys();
        Launcher.poll();
        Chat.poll();
        dev.aller.screen.store.PackListExtras.poll();
        dev.aller.feature.Containers.poll();
        dev.aller.feature.Photo.poll();
        dev.aller.feature.Browser.frame();
        dev.aller.feature.Pocket.poll();
    }

    public static void end() {
        Replay.frameEnd();
        dev.aller.feature.Shots.frameEnd();
        dev.aller.feature.Photo.frameEnd();
        DevHarness.frameEnd();
    }

    /** In-game HUD layer. Runs after vanilla's HUD, before any open screen. */
    public static void hud(Canvas c) {
        dev.aller.feature.Pocket.draw(c);
        // A screenshot taken with the HUD hidden still gets its card.
        if (Mc.mc().player != null && Mc.hudHidden() && Mc.screen() == null) dev.aller.ui.ShotCard.draw(c);
        if (Mc.mc().player == null || Mc.hudHidden()) return;
        // The HUD editor draws the elements itself so it can move them around.
        boolean editing = Mc.current() instanceof HudEditorScreen;
        if (!editing) {
            Modules.VITALS.draw(c);
            if (Modules.WAYPOINTS.enabled()) Waypoints.draw(c);
            dev.aller.feature.ChestMemory.draw(c);
            dev.aller.feature.Bubbles.draw(c);
            if (Modules.CROSSHAIR.enabled() && Mc.screen() == null) Modules.CROSSHAIR.draw(c);
            c.beginScale(AllerClient.options().hudScale.get());
            Hud.draw(c, false, null);
            c.endScale();
            dev.aller.feature.TabList.draw(c);
        }
        // Aller screens draw toasts themselves so they appear above the blur.
        if (Mc.current() == null) Toasts.draw(c);
    }
}
