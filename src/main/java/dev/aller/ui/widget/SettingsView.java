package dev.aller.ui.widget;

import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Sounds;
import dev.aller.setting.Setting;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Renders a list of {@link Setting}s as editable rows: switches, sliders, colour pickers, segmented
 * choices, key captures and text fields, under optional group headings. A setting's description
 * sits under its name. Used by the palette's module pages and the client options page. The owner
 * positions it, clips it and scrolls it. Right-clicking a row resets that setting.
 */
public final class SettingsView {
    private static final float ROW = 24f;
    private static final float LABEL = 8.5f;
    private static final float NOTE = 8f, NOTE_SIZE = 6.8f, HEADER = 22f;

    private final List<Row> rows = new ArrayList<>();
    private Row active;
    /** Called after any value changes, so the owner can mark the config dirty. */
    public Runnable onChange = () -> {};
    /** Says what else a key is bound to ("Zoom"), or null if nothing; shown as a warning under the key row. */
    public Function<Settings.Key, String> conflict = k -> null;

    public SettingsView set(List<? extends Setting<?>> settings) {
        clear();
        for (Setting<?> s : settings) add(s);
        return this;
    }

    public void clear() {
        rows.clear();
        active = null;
    }

    public SettingsView add(Setting<?> s) {
        if (s.section != null) header(s.section);
        rows.add(make(s));
        return this;
    }

    public SettingsView header(String title) {
        rows.add(new HeaderRow(title));
        return this;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Row make(Setting<?> s) {
        if (s instanceof Settings.Bool b) return new BoolRow(b);
        if (s instanceof Settings.Num n) return new NumRow(n);
        if (s instanceof Settings.Color col) return new ColorRow(col);
        if (s instanceof Settings.Choice ch) return new ChoiceRow(ch);
        if (s instanceof Settings.Key k) return new KeyRow(k);
        if (s instanceof Settings.Text t) return new TextRow(t);
        throw new IllegalArgumentException("No widget for " + s.getClass());
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public float height() {
        float h = 0;
        for (Row r : rows) h += r.height() * r.shown.get();
        return h;
    }

    /** True while a row is waiting for a key press or has text focus; the owner must not treat keys as navigation. */
    public boolean capturing() {
        for (Row r : rows) if (r.capturing()) return true;
        return false;
    }

    /** @param hot whether the mouse is over the visible part of the view (false disables hover) */
    public void draw(Canvas c, float x, float y, float w, float mx, float my, boolean hot) {
        float cy = y;
        for (Row r : rows) {
            float vis = r.shown.target(r.visible() ? 1 : 0).update();
            if (vis < 0.02f) {
                r.h = 0;
                continue;
            }
            float h = r.height() * vis;
            r.x = x;
            r.y = cy;
            r.w = w;
            r.h = h;
            c.pushAlpha(Math.clamp(vis, 0f, 1f));
            if (r.setting == null) {
                r.draw(c, mx, my, false);
            } else {
                boolean over = hot && active == null && mx >= x && mx < x + w && my >= cy && my < cy + h;
                float hv = r.hover.target(over || active == r ? 1 : 0).update();
                c.rect(x, cy + 1, w, r.head() - 2, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.045f * hv));
                c.textMiddle(Fonts.MEDIUM, r.setting.name, x + 8, cy, ROW, LABEL, Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
                String note = r.note();
                if (!note.isEmpty()) {
                    c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(note, NOTE_SIZE, w - 16), x + 8, cy + ROW - 5, NOTE_SIZE, r.noteColor());
                }
                r.draw(c, mx, my, over);
            }
            c.popAlpha();
            cy += h;
        }
    }

    public boolean mouseDown(float mx, float my, int button) {
        boolean listening = false;
        for (Row r : rows) if (r instanceof KeyRow k && k.listening) listening = true;
        Row target = null;
        for (Row r : rows) {
            if (r.setting != null && r.h > 0 && mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) target = r;
        }
        if (listening) {
            // The click is consumed by the key capture (and may become the binding).
            for (Row r : rows) r.blur(button);
            return true;
        }
        for (Row r : rows) if (r != target) r.blur(button);
        if (target == null) return false;
        if (target.mouseDown(mx, my, button)) active = target;
        return true;
    }

    public boolean mouseUp(float mx, float my, int button) {
        if (active == null) return false;
        active.mouseUp(mx, my);
        active = null;
        return true;
    }

    public boolean keyDown(int key, int mods) {
        for (Row r : rows) if (r.capturing() && r.keyDown(key, mods)) return true;
        return false;
    }

    public boolean charTyped(int codepoint) {
        for (Row r : rows) if (r.capturing() && r.charTyped(codepoint)) return true;
        return false;
    }

    private void changed() {
        onChange.run();
    }

    // ---- rows ----------------------------------------------------------------------------------

    private abstract static class Row {
        final Setting<?> setting;
        final Spring hover = Spring.snappy(0);
        final Spring shown;
        float x, y, w, h;

        /** @param setting null for a heading */
        Row(Setting<?> setting) {
            this.setting = setting;
            this.shown = Spring.smooth(visible() ? 1 : 0);
        }

        boolean visible() {
            return setting == null || setting.visible();
        }

        /** A line of small print under the name: the setting's description unless a row has something more urgent to say. */
        String note() {
            return setting.description;
        }

        int noteColor() {
            return Theme.TEXT_MUTED;
        }

        /** Height of the part that holds the name and control; an expanding row adds to it. */
        float head() {
            return ROW + (note().isEmpty() ? 0 : NOTE);
        }

        float height() {
            return head();
        }

        abstract void draw(Canvas c, float mx, float my, boolean over);

        /** @return true to keep receiving the drag until the button is released */
        boolean mouseDown(float mx, float my, int button) {
            return false;
        }

        void mouseUp(float mx, float my) {}

        /** Another row (or empty space) was clicked. */
        void blur(int button) {}

        boolean keyDown(int key, int mods) {
            return false;
        }

        boolean charTyped(int codepoint) {
            return false;
        }

        boolean capturing() {
            return false;
        }
    }

    private static final class HeaderRow extends Row {
        final String title;

        HeaderRow(String title) {
            super(null);
            this.title = title.toUpperCase();
        }

        @Override
        float head() {
            return HEADER;
        }

        @Override
        void draw(Canvas c, float mx, float my, boolean over) {
            c.text(Fonts.SEMIBOLD, title, x + 8, y + 10, 6.4f, Theme.TEXT_MUTED);
            float lx = x + 14 + Fonts.SEMIBOLD.width(title, 6.4f);
            c.rect(lx, y + 13.5f, x + w - 8 - lx, 0.5f, 0, 0x14FFFFFF);
        }
    }

    private final class BoolRow extends Row {
        final Settings.Bool s;
        final Toggle toggle = new Toggle();

        BoolRow(Settings.Bool s) {
            super(s);
            this.s = s;
        }

        @Override
        void draw(Canvas c, float mx, float my, boolean over) {
            toggle.draw(c, x + w - 8 - Toggle.W, y + (ROW - Toggle.H) / 2, s.get(), over);
        }

        @Override
        boolean mouseDown(float mx, float my, int button) {
            if (button == 1) s.reset();
            else if (button == 0) s.toggle();
            else return false;
            Sounds.toggle(s.get());
            changed();
            return false;
        }
    }

    private final class NumRow extends Row {
        final Settings.Num s;
        final Spring pos = new Spring(0, 520f, 36f);
        final Spring grab = Spring.snappy(0);
        boolean dragging, primed;

        NumRow(Settings.Num s) {
            super(s);
            this.s = s;
        }

        float trackW() {
            return Math.min(120, w * 0.4f);
        }

        float trackX() {
            return x + w - 12 - trackW();
        }

        @Override
        void draw(Canvas c, float mx, float my, boolean over) {
            float tw = trackW(), tx = trackX(), ty = y + ROW / 2;
            if (dragging) apply(mx);
            float t = (s.get() - s.min) / (s.max - s.min);
            if (!primed) {
                pos.snap(t);
                primed = true;
            }
            float p = Math.clamp(pos.target(t).update(), 0f, 1f);
            float gr = grab.target(dragging ? 1 : over ? 0.5f : 0).update();

            c.textRight(Fonts.SEMIBOLD, s.display(), tx - 10, y + (ROW - Fonts.SEMIBOLD.height(8f)) / 2, 8f,
                    Colors.mix(Theme.TEXT_DIM, Theme.TEXT, gr));
            c.rect(tx, ty - 1.5f, tw, 3, 1.5f, 0x26FFFFFF);
            if (p > 0.005f) c.gradientH(tx, ty - 1.5f, tw * p, 3, 1.5f, Theme.accent2(), Theme.accent());
            float kx = tx + tw * p, kr = 4f + 1.5f * gr;
            c.shadow(kx - kr, ty - kr + 1, kr * 2, kr * 2, kr, 5, Colors.withAlpha(Theme.accent(), 0.5f * gr));
            c.circle(kx, ty, kr, Colors.WHITE);
        }

        void apply(float mx) {
            float t = Math.clamp((mx - trackX()) / trackW(), 0f, 1f);
            float before = s.get();
            s.set(s.min + t * (s.max - s.min));
            if (s.get() != before) changed();
        }

        @Override
        boolean mouseDown(float mx, float my, int button) {
            if (button == 1) {
                s.reset();
                changed();
                return false;
            }
            if (button != 0 || mx < trackX() - 8) return false;
            dragging = true;
            apply(mx);
            return true;
        }

        @Override
        void mouseUp(float mx, float my) {
            dragging = false;
        }
    }

    private final class ColorRow extends Row {
        static final float PICK_H = 54, BAR_H = 7, GAP = 6;
        final Settings.Color s;
        final Spring expand = Spring.snappy(0);
        boolean open;
        float hue, sat, val, alpha;
        int synced;
        boolean primed;
        /** 0 none, 1 saturation/value square, 2 hue bar, 3 alpha bar. */
        int drag;

        ColorRow(Settings.Color s) {
            super(s);
            this.s = s;
        }

        float extra() {
            return GAP + PICK_H + GAP + BAR_H + (s.alpha ? GAP + BAR_H : 0) + GAP;
        }

        @Override
        float height() {
            return head() + extra() * Math.clamp(expand.get(), 0f, 1f);
        }

        /** Keeps our HSV copy unless something else changed the colour (hue would be lost at zero saturation). */
        void sync() {
            if (primed && synced == s.get()) return;
            float[] hsv = Colors.toHsv(s.get());
            if (!primed || hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            alpha = Colors.alpha(s.get()) / 255f;
            synced = s.get();
            primed = true;
        }

        void write() {
            int col = Colors.hsv(hue, sat, val, s.alpha ? alpha : 1f);
            if (col != s.get()) {
                s.set(col);
                changed();
            }
            synced = s.get();
        }

        @Override
        void draw(Canvas c, float mx, float my, boolean over) {
            sync();
            float e = expand.target(open ? 1 : 0).update();
            int col = s.get();
            String hex = String.format(s.alpha ? "#%08X" : "#%06X", s.alpha ? col : col & 0xFFFFFF);
            float sw = 18, sh = 11, sx = x + w - 8 - sw, sy = y + (ROW - sh) / 2;
            c.textRight(Fonts.REGULAR, hex, sx - 7, y + (ROW - Fonts.REGULAR.height(7.5f)) / 2, 7.5f, Theme.TEXT_MUTED);
            c.rect(sx, sy, sw, sh, 3.5f, 0xFF1B1826);
            c.rect(sx, sy, sw, sh, 3.5f, col);
            c.stroke(sx, sy, sw, sh, 3.5f, 1, Theme.BORDER_STRONG);
            if (e < 0.02f) return;

            float px = x + 8, pw = w - 16, py = y + head() + GAP;
            if (drag == 1) {
                sat = Math.clamp((mx - px) / pw, 0f, 1f);
                val = 1 - Math.clamp((my - py) / PICK_H, 0f, 1f);
                write();
            }
            float hy = py + PICK_H + GAP, ay = hy + BAR_H + GAP, cap = BAR_H / 2;
            if (drag == 2) {
                hue = Math.clamp((mx - px - cap) / (pw - BAR_H), 0f, 0.9999f);
                write();
            }
            if (drag == 3) {
                alpha = Math.clamp((mx - px - cap) / (pw - BAR_H), 0f, 1f);
                write();
            }

            c.pushAlpha(Math.clamp(e, 0f, 1f));
            c.clip(x, y + head(), w, Math.max(0, h - head()));
            c.gradientH(px, py, pw, PICK_H, Theme.R_SM, Colors.WHITE, Colors.hsv(hue, 1, 1, 1));
            c.gradientV(px, py, pw, PICK_H, Theme.R_SM, 0x00000000, 0xFF000000);
            c.stroke(px, py, pw, PICK_H, Theme.R_SM, 1, Theme.BORDER);
            float kx = px + sat * pw, ky = py + (1 - val) * PICK_H;
            c.ring(kx, ky, 4.5f, 1.2f, 0xAA000000);
            c.ring(kx, ky, 3.6f, 1.4f, Colors.WHITE);

            // Hue wraps from red back to red, so a red pill underneath gives the bar rounded ends.
            c.rect(px, hy, pw, BAR_H, cap, 0xFFFF0000);
            float seg = (pw - BAR_H) / 6f;
            for (int i = 0; i < 6; i++) {
                c.gradientH(px + cap + seg * i, hy, seg + 0.5f, BAR_H, 0, Colors.hsv(i / 6f, 1, 1, 1), Colors.hsv((i + 1) / 6f, 1, 1, 1));
            }
            knob(c, px + cap + hue * (pw - BAR_H), hy + cap);

            if (s.alpha) {
                int solid = Colors.hsv(hue, sat, val, 1);
                c.rect(px, ay, pw, BAR_H, cap, 0xFF1B1826);
                c.gradientH(px, ay, pw, BAR_H, cap, Colors.withAlpha(solid, 0), solid);
                knob(c, px + cap + alpha * (pw - BAR_H), ay + cap);
            }
            c.unclip();
            c.popAlpha();
        }

        void knob(Canvas c, float kx, float ky) {
            c.circle(kx, ky, 4.6f, 0x66000000);
            c.circle(kx, ky, 3.8f, Colors.WHITE);
        }

        @Override
        boolean mouseDown(float mx, float my, int button) {
            if (button == 1 && my < y + head()) {
                s.reset();
                changed();
                return false;
            }
            if (button != 0) return false;
            if (my < y + head()) {
                open = !open;
                Sounds.click();
                return false;
            }
            if (!open) return false;
            float py = y + head() + GAP, hy = py + PICK_H + GAP, ay = hy + BAR_H + GAP;
            if (my >= py && my < py + PICK_H) drag = 1;
            else if (my >= hy - 3 && my < hy + BAR_H + 3) drag = 2;
            else if (s.alpha && my >= ay - 3 && my < ay + BAR_H + 3) drag = 3;
            return drag != 0;
        }

        @Override
        void mouseUp(float mx, float my) {
            drag = 0;
        }
    }

    private final class ChoiceRow<E extends Enum<E>> extends Row {
        final Settings.Choice<E> s;
        final Spring hlX = new Spring(0, 520f, 38f), hlW = new Spring(0, 520f, 38f);
        final float[] widths;
        boolean primed;

        ChoiceRow(Settings.Choice<E> s) {
            super(s);
            this.s = s;
            widths = new float[s.options.length];
        }

        float total() {
            float t = 0;
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Fonts.MEDIUM.width(Settings.Choice.label(s.options[i]), 7.5f) + 12;
                t += widths[i];
            }
            return t;
        }

        boolean segmented() {
            return total() <= w * 0.62f;
        }

        @Override
        void draw(Canvas c, float mx, float my, boolean over) {
            float bh = 15, by = y + (ROW - bh) / 2;
            if (segmented()) {
                float total = total(), bx = x + w - 8 - total;
                c.rect(bx, by, total, bh, bh / 2, 0x16FFFFFF);
                float cx = bx, selX = bx, selW = 0;
                for (int i = 0; i < widths.length; i++) {
                    if (s.options[i] == s.get()) {
                        selX = cx;
                        selW = widths[i];
                    }
                    cx += widths[i];
                }
                if (!primed) {
                    hlX.snap(selX - bx);
                    hlW.snap(selW);
                    primed = true;
                }
                float hx = bx + hlX.target(selX - bx).update(), hw = hlW.target(selW).update();
                Theme.accentFill(c, hx, by, hw, bh, bh / 2);
                cx = bx;
                for (int i = 0; i < widths.length; i++) {
                    boolean sel = s.options[i] == s.get();
                    String label = Settings.Choice.label(s.options[i]);
                    c.textMiddle(Fonts.MEDIUM, label, cx + 6, by, bh, 7.5f, sel ? Theme.onAccent() : Theme.TEXT_DIM);
                    cx += widths[i];
                }
            } else {
                String label = Settings.Choice.label(s.get());
                float lw = Fonts.MEDIUM.width(label, 8f), bw = lw + 34, bx = x + w - 8 - bw;
                c.rect(bx, by, bw, bh, bh / 2, 0x16FFFFFF);
                Icons.CHEVRON_LEFT.draw(c, bx + 8.5f, by + bh / 2, 8.5f, Theme.TEXT_MUTED);
                Icons.CHEVRON_RIGHT.draw(c, bx + bw - 8.5f, by + bh / 2, 8.5f, Theme.TEXT_MUTED);
                c.textMiddle(Fonts.MEDIUM, label, bx + 17, by, bh, 8f, Theme.TEXT);
            }
        }

        @Override
        boolean mouseDown(float mx, float my, int button) {
            if (button > 1) return false;
            if (button == 1) {
                s.reset();
            } else if (segmented()) {
                float cx = x + w - 8 - total();
                if (mx < cx) return false;
                for (int i = 0; i < widths.length; i++) {
                    if (mx >= cx && mx < cx + widths[i]) s.set(s.options[i]);
                    cx += widths[i];
                }
            } else {
                float lw = Fonts.MEDIUM.width(Settings.Choice.label(s.get()), 8f), bw = lw + 34, bx = x + w - 8 - bw;
                s.cycle(mx < bx + bw / 2 && mx >= bx ? -1 : 1);
            }
            Sounds.click();
            changed();
            return false;
        }
    }

    private final class KeyRow extends Row {
        final Settings.Key s;
        final Spring listen = Spring.snappy(0);
        boolean listening;

        KeyRow(Settings.Key s) {
            super(s);
            this.s = s;
        }

        private String clash() {
            return listening || s.get() == Settings.Key.NONE ? null : conflict.apply(s);
        }

        @Override
        String note() {
            String other = clash();
            return other != null ? "Also used by " + other : super.note();
        }

        @Override
        int noteColor() {
            return clash() != null ? Theme.WARN : super.noteColor();
        }

        @Override
        void draw(Canvas c, float mx, float my, boolean over) {
            float l = listen.target(listening ? 1 : 0).update();
            String label = listening ? (s.chord ? "Press keys…" : "Press a key…") : Mc.keyName(s.get());
            float bh = 15, bw = Math.max(34, Fonts.SEMIBOLD.width(label, 7.5f) + 14), bx = x + w - 8 - bw, by = y + (ROW - bh) / 2;
            float pulse = listening ? 0.5f + 0.5f * (float) Math.sin(Motion.time() * 6f) : 0;
            c.rect(bx, by, bw, bh, 4, Colors.mix(0x16FFFFFF, Colors.withAlpha(Theme.accent(), 0.22f + 0.12f * pulse), l));
            c.stroke(bx, by, bw, bh, 4, 1, Colors.mix(Theme.BORDER, Theme.accent(), l));
            c.textMiddle(Fonts.SEMIBOLD, label, bx + (bw - Fonts.SEMIBOLD.width(label, 7.5f)) / 2, by, bh, 7.5f,
                    s.get() == Settings.Key.NONE && !listening ? Theme.TEXT_MUTED : Theme.TEXT);
        }

        @Override
        boolean mouseDown(float mx, float my, int button) {
            if (button == 0) {
                listening = true;
                Sounds.click();
            } else if (button == 1) {
                // Back to the default binding; right-click again to clear it altogether.
                set(s.get().equals(s.defaultValue()) ? Settings.Key.NONE : s.defaultValue());
            }
            return false;
        }

        @Override
        void blur(int button) {
            if (!listening) return;
            // While listening, a side or middle mouse button becomes the binding; left and right just cancel.
            if (button >= 2) set(-2 - button);
            listening = false;
        }

        void set(int code) {
            s.set(code);
            listening = false;
            changed();
        }

        @Override
        boolean keyDown(int key, int mods) {
            if (!listening) return false;
            if (s.chord) {
                // A modifier on its own is the start of a chord: keep waiting for the key that completes it.
                if (key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER) return true;
                int held = mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT);
                set(key == GLFW.GLFW_KEY_ESCAPE && held == 0 ? Settings.Key.NONE : Settings.Key.pack(key, held));
                return true;
            }
            set(key == GLFW.GLFW_KEY_ESCAPE ? Settings.Key.NONE : key);
            return true;
        }

        @Override
        boolean capturing() {
            return listening;
        }
    }

    private final class TextRow extends Row {
        final Settings.Text s;
        final TextField field = new TextField("");

        TextRow(Settings.Text s) {
            super(s);
            this.s = s;
            field.maxLength = s.maxLength;
            field.textSize = 8f;
            field.setText(s.get());
            field.onChange = v -> {
                s.set(v);
                changed();
            };
        }

        @Override
        void draw(Canvas c, float mx, float my, boolean over) {
            if (!field.focused && !field.text.equals(s.get())) field.setText(s.get());
            float fw = Math.min(150, w * 0.5f);
            field.bounds(x + w - 8 - fw, y + 3.5f, fw, ROW - 7);
            field.draw(c, mx, my);
        }

        @Override
        boolean mouseDown(float mx, float my, int button) {
            if (button == 1) {
                s.reset();
                field.focused = false;
                changed();
                return false;
            }
            field.mouseDown(mx, my, button);
            return false;
        }

        @Override
        void blur(int button) {
            field.focused = false;
        }

        @Override
        boolean keyDown(int key, int mods) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                field.focused = false;
                return true;
            }
            field.keyDown(key, mods);
            return true; // swallow everything while typing
        }

        @Override
        boolean charTyped(int codepoint) {
            return field.charTyped(codepoint);
        }

        @Override
        boolean capturing() {
            return field.focused;
        }
    }
}
