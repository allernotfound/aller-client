package dev.aller.hud;

import dev.aller.AllerClient;
import dev.aller.module.Module;
import dev.aller.platform.Canvas;

import java.util.ArrayList;
import java.util.List;

/** Lays out and draws every enabled HUD module. */
public final class Hud {
    private Hud() {}

    public static List<HudModule> modules() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : AllerClient.modules().all()) if (m instanceof HudModule h) out.add(h);
        return out;
    }

    /**
     * @param editing draw sample content and skip nothing, for the HUD editor
     * @param skip    an element the caller draws itself (the one being dragged), or null
     */
    public static void draw(Canvas c, boolean editing, HudModule skip) {
        for (Module m : AllerClient.modules().all()) {
            if (!(m instanceof HudModule hud) || hud == skip) continue;
            // Keep drawing briefly after disabling so the element can animate out.
            float a = hud.appear.target(hud.enabled() ? 1 : 0).update();
            hud.shown = a >= 0.02f && hud.measure(editing);
            if (!hud.shown) continue;
            drawOne(c, hud, hud.x(c.width()), hud.y(c.height()), a, editing);
        }
    }

    /** Lays out a single element outside the normal pass (the HUD editor's dragged element). */
    public static boolean measure(HudModule hud, boolean editing) {
        return hud.measure(editing);
    }

    public static void drawOne(Canvas c, HudModule hud, float x, float y, float appear, boolean editing) {
        float s = hud.scale.get();
        c.push();
        c.translate(x, y);
        c.scale(s, 0, 0);
        c.scale(0.85f + 0.15f * appear, hud.w / 2, hud.h / 2);
        c.pushAlpha(appear);
        hud.render(c, editing);
        c.popAlpha();
        c.pop();
    }
}
