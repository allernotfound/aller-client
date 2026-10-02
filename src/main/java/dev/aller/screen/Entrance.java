package dev.aller.screen;

import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Tween;
import net.minecraft.client.gui.screens.Screen;

/**
 * Eases a Minecraft screen in when it is opened from one of Aller's menus, so it does not cut in
 * after the menu has faded away. Vanilla widgets cannot be faded themselves, so the screen settles
 * from slightly enlarged while a veil of what was there before lifts off it.
 */
public final class Entrance {
    private static final Tween tween = new Tween(0.32f, Easing.OUT_CUBIC);
    private static boolean live, drawing;
    private static float t = 1;

    private Entrance() {}

    static void start() {
        tween.restart();
        live = true;
    }

    /** Before the screen draws. */
    public static void begin(Canvas c, Screen screen) {
        if (!live || screen instanceof ScreenHost || screen != Mc.screen()) return;
        t = tween.update();
        if (tween.finished()) {
            live = false;
            return;
        }
        drawing = true;
        c.push();
        c.scale(1 + 0.03f * (1 - t), c.width() / 2, c.height() / 2);
    }

    /** After the screen has drawn. */
    public static void end(Canvas c) {
        if (!drawing) return;
        drawing = false;
        c.pop();
        c.layer();
        if (Mc.mc().level == null) {
            c.pushAlpha(1 - t);
            Theme.scene(c, c.width(), c.height());
            c.popAlpha();
        } else {
            c.rect(0, 0, c.width(), c.height(), 0, Colors.withAlpha(0xFF050409, 0.55f * (1 - t)));
        }
    }
}
