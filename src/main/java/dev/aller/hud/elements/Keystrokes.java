package dev.aller.hud.elements;

import dev.aller.feature.Clicks;
import dev.aller.hud.HudModule;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.KeyMapping;

/** WASD, mouse buttons and space, each key lighting up and sinking as it is pressed. */
public final class Keystrokes extends HudModule {
    public final Settings.Bool mouse = bool("mouse", "Mouse buttons", true);
    public final Settings.Bool space = bool("space", "Space bar", true);
    public final Settings.Bool cps = bool("cps", "CPS on mouse buttons", true);
    public final Settings.Bool useAccent = bool("use_accent", "Use accent colour when pressed", true);
    public final Settings.Color pressed = color("pressed", "Pressed colour", 0xFF8B5CF6).visibleWhen(() -> !this.useAccent.get());

    private static final float KEY = 20, GAP = 2;
    private final Spring[] press = new Spring[7];

    public Keystrokes() {
        super("keystrokes", "Keystrokes", "Show which movement keys and mouse buttons are pressed", AnchorH.LEFT, AnchorV.BOTTOM, 6, 6);
        keywords("wasd", "keys", "inputs");
        for (int i = 0; i < press.length; i++) press[i] = Spring.snappy(0);
    }

    @Override
    protected boolean measure(boolean editing) {
        w = KEY * 3 + GAP * 2;
        h = KEY * 2 + GAP;
        if (mouse.get()) h += KEY * 0.9f + GAP;
        if (space.get()) h += KEY * 0.55f + GAP;
        return true;
    }

    @Override
    protected void render(Canvas c, boolean editing) {
        var o = Mc.mc().options;
        key(c, 0, o.keyUp, "W", KEY + GAP, 0, KEY, KEY, null);
        key(c, 1, o.keyLeft, "A", 0, KEY + GAP, KEY, KEY, null);
        key(c, 2, o.keyDown, "S", KEY + GAP, KEY + GAP, KEY, KEY, null);
        key(c, 3, o.keyRight, "D", (KEY + GAP) * 2, KEY + GAP, KEY, KEY, null);
        float y = (KEY + GAP) * 2;
        if (mouse.get()) {
            float half = (w - GAP) / 2, mh = KEY * 0.9f;
            key(c, 4, o.keyAttack, "LMB", 0, y, half, mh, cps.get() && Clicks.left() > 0 ? Clicks.left() + " CPS" : null);
            key(c, 5, o.keyUse, "RMB", half + GAP, y, half, mh, cps.get() && Clicks.right() > 0 ? Clicks.right() + " CPS" : null);
            y += mh + GAP;
        }
        if (space.get()) {
            float sh = KEY * 0.55f;
            float t = press[6].target(o.keyJump.isDown() ? 1 : 0).update();
            box(c, 0, y, w, sh, t);
            c.rect(w / 2 - 12, y + sh / 2 - 0.75f, 24, 1.5f, 0.75f, Colors.mix(Theme.TEXT_DIM, Colors.WHITE, t));
        }
    }

    private void key(Canvas c, int index, KeyMapping mapping, String label, float x, float y, float kw, float kh, String sub) {
        float t = press[index].target(mapping.isDown() ? 1 : 0).update();
        c.push();
        c.scale(1f - 0.07f * t, x + kw / 2, y + kh / 2);
        box(c, x, y, kw, kh, t);
        int text = Colors.mix(Theme.TEXT, Colors.WHITE, t);
        if (sub == null) {
            float size = label.length() > 1 ? 7f : 9f;
            c.textCentered(Fonts.SEMIBOLD, label, x + kw / 2, y + (kh - Fonts.SEMIBOLD.height(size)) / 2, size, text);
        } else {
            c.textCentered(Fonts.SEMIBOLD, label, x + kw / 2, y + 2.2f, 6.5f, text);
            c.textCentered(Fonts.REGULAR, sub, x + kw / 2, y + 10f, 5.5f, Colors.fade(text, 0.75f));
        }
        c.pop();
    }

    private void box(Canvas c, float x, float y, float bw, float bh, float t) {
        float tc = Math.clamp(t, 0f, 1f);
        int on = useAccent.get() ? Theme.accent() : pressed.get();
        if (background.get() || tc > 0.01f) {
            c.rect(x, y, bw, bh, 4, Colors.mix(background.get() ? Theme.GLASS_HUD : 0x00100E18, Colors.withAlpha(on, 0.85f), tc));
        }
        c.stroke(x, y, bw, bh, 4, 1, Colors.mix(0x1EFFFFFF, Colors.withAlpha(Colors.lighten(on, 0.4f), 0.9f), tc));
    }
}
