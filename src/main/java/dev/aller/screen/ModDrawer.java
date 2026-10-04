package dev.aller.screen;

import dev.aller.compat.ModButtons;
import dev.aller.compat.ModButtons.Entry;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Sounds;
import dev.aller.platform.Tex;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Other mods' buttons on the main and pause menus ({@link ModButtons}), at the foot of the strip to
 * the right of the column: one wider button showing whose buttons it holds, which pops a list out
 * beside it. A row can be pinned, which gives it a button of its own in the strip, or hidden.
 */
final class ModDrawer {
    static final float ICON = 20, GAP = 5;
    private static final float PANEL = 190, ROW = 26, PAD = 4, FOOT = 19, MINI = 15, FACE = 12, OVERLAP = 8;
    private static final int MAX_ROWS = 7;
    private static final String FOOTER = "#footer";

    private final AllerScreen owner;
    private final boolean title;
    /** Hands the menu over to whatever the pressed button opens. */
    private final Consumer<Runnable> leave;

    private List<Entry> all = List.of();
    /** Buttons with a place of their own in the strip. */
    private final List<Entry> pins = new ArrayList<>();
    /** The drawer's rows: what is neither pinned nor hidden, then the hidden ones while they are being shown. */
    private final List<Entry> rows = new ArrayList<>();
    /** One icon per mod in the drawer, for the face of its button; null stands for a mod without one. */
    private final List<Tex> faces = new ArrayList<>();
    private int listed, hiddenCount;
    private boolean open, showHidden;
    private final Spring openness = Spring.snappy(0), hover = Spring.snappy(0), press = Spring.snappy(0);
    private final Map<String, Spring> springs = new HashMap<>();
    private float stripX, stripY, bx, by, bw, px, py, ph, scroll;
    private boolean down;
    private String downId;
    private int downPart;

    ModDrawer(AllerScreen owner, boolean title, Consumer<Runnable> leave) {
        this.owner = owner;
        this.title = title;
        this.leave = leave;
        refresh();
    }

    /** Lays Minecraft's own menu out again: a pressed button may have changed what it says. */
    void refresh() {
        all = ModButtons.collect(title);
        sort();
    }

    private void sort() {
        pins.clear();
        rows.clear();
        faces.clear();
        List<Entry> hidden = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        for (Entry e : all) {
            if (ModButtons.hidden(e)) hidden.add(e);
            else if (ModButtons.pinned(e)) pins.add(e);
            else {
                rows.add(e);
                String mod = String.valueOf(e.mod());
                if (!mods.contains(mod) && faces.size() < 3) {
                    mods.add(mod);
                    faces.add(e.icon());
                }
            }
        }
        listed = rows.size();
        hiddenCount = hidden.size();
        if (hiddenCount == 0) showHidden = false;
        if (showHidden) rows.addAll(hidden);
        if (listed == 0) open = false;
    }

    /** How many buttons this adds to the strip. */
    int slots() {
        return pins.size() + (listed > 0 ? 1 : 0);
    }

    /** How much further right than an icon button the drawer's button reaches. */
    float overhang() {
        return listed > 0 ? buttonWidth() - ICON : 0;
    }

    boolean open() {
        return open;
    }

    private float buttonWidth() {
        float stack = FACE + (faces.size() - 1) * OVERLAP;
        return 5 + stack + 5 + Fonts.SEMIBOLD.width(Integer.toString(listed), 8) + 3 + 8 + 5;
    }

    private Spring spring(String id) {
        return springs.computeIfAbsent(id, k -> Spring.snappy(0));
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** The pinned buttons, then the drawer's own, downwards from (x, y) at the strip's spacing. */
    void draw(Canvas c, float x, float y, float mx, float my) {
        stripX = x;
        stripY = y;
        for (int i = 0; i < pins.size(); i++) {
            Entry e = pins.get(i);
            float iy = y + i * (ICON + GAP);
            boolean over = inside(mx, my, x, iy, ICON, ICON);
            float hv = spring(e.id()).target(over ? 1 : 0).update();
            c.pushAlpha(e.active() ? 1 : 0.4f);
            c.push();
            plate(c, x, iy, ICON, hv, over && e.id().equals(downId) ? 1 : 0);
            face(c, e.icon(), x + (ICON - FACE) / 2, iy + (ICON - FACE) / 2, FACE, hv);
            c.pop();
            c.popAlpha();
        }
        if (listed == 0) return;

        bx = x;
        by = y + pins.size() * (ICON + GAP);
        bw = buttonWidth();
        boolean over = inside(mx, my, bx, by, bw, ICON);
        float hv = hover.target(over || open ? 1 : 0).update();
        float pr = press.target(down && over ? 1 : 0).update();
        c.push();
        plate(c, bx, by, bw, hv, pr);
        // Overlapped like a row of avatars, the first on top.
        for (int i = faces.size() - 1; i >= 0; i--) {
            float fx = bx + 5 + i * OVERLAP, fy = by + (ICON - FACE) / 2;
            c.rect(fx - 1.2f, fy - 1.2f, FACE + 2.4f, FACE + 2.4f, 4, 0xFF15131E);
            face(c, faces.get(i), fx, fy, FACE, hv);
        }
        float tx = bx + 5 + FACE + (faces.size() - 1) * OVERLAP + 5;
        int ink = Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv);
        tx += c.textMiddle(Fonts.SEMIBOLD, Integer.toString(listed), tx, by, ICON, 8, ink) + 3;
        c.push();
        c.rotate((float) Math.PI * Math.clamp(openness.get(), 0f, 1f), tx + 4, by + ICON / 2);
        Icons.CHEVRON_RIGHT.draw(c, tx + 4, by + ICON / 2, 8, ink);
        c.pop();
        c.pop();
    }

    /** The same plate as {@code IconButton}'s, any width. Leaves its hover scale on the canvas for what is drawn on it. */
    private static void plate(Canvas c, float x, float y, float w, float hv, float pr) {
        float r = 6;
        c.pixel(true);
        if (!c.pixelated()) c.scale(1f + 0.06f * hv - 0.08f * pr, x + w / 2, y + ICON / 2);
        c.rect(x, y, w, ICON, r, 0xB80D0B14);
        c.rect(x, y, w, ICON, r, Colors.mix(Theme.RAISED, Colors.withAlpha(Theme.accent(), 0.22f), hv));
        c.stroke(x, y, w, ICON, r, 1, Colors.mix(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0.6f), hv));
        c.pixel(false);
    }

    /** A mod's icon, or a package where it has none or the mod is not known. */
    private static void face(Canvas c, Tex icon, float x, float y, float size, float hv) {
        if (icon != null) {
            c.picture(icon, x, y, size, size, size * 0.25f, true);
            return;
        }
        c.rect(x, y, size, size, size * 0.25f, 0xFF2A2638);
        Icons.PACKAGE.draw(c, x + size / 2, y + size / 2, size * 0.72f, Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
    }

    /** Names beside the pinned buttons and the list itself. Call after everything else on the menu. */
    void drawOver(Canvas c, float mx, float my, float vw, float vh) {
        for (int i = 0; i < pins.size(); i++) {
            Entry e = pins.get(i);
            float t = Math.clamp(spring(e.id()).get(), 0f, 1f);
            if (t < 0.02f || open) continue;
            String name = e.label(), hint = "right-click to unpin";
            float size = 7.5f, nw = Fonts.MEDIUM.widthAny(name, size), tw = nw + 8 + Fonts.REGULAR.width(hint, 6.5f) + 12, th = 15;
            float tx = stripX + ICON + 5 + 4 * (1 - t), ty = stripY + i * (ICON + GAP) + (ICON - th) / 2;
            c.pushAlpha(t);
            c.shadow(tx, ty + 2, tw, th, 5, 8, 0x66000000);
            c.rect(tx, ty, tw, th, 5, 0xF2171422);
            c.stroke(tx, ty, tw, th, 5, 1, Theme.BORDER_STRONG);
            c.textAny(Fonts.MEDIUM, name, tx + 6, ty + (th - Fonts.MEDIUM.height(size)) / 2, size, Theme.TEXT);
            c.textMiddle(Fonts.REGULAR, hint, tx + 6 + nw + 8, ty, th, 6.5f, Theme.TEXT_MUTED);
            c.popAlpha();
        }

        float o = Math.clamp(openness.target(open ? 1 : 0).update(), 0f, 1f);
        if (o < 0.01f || rows.isEmpty()) return;
        int shown = Math.min(rows.size(), MAX_ROWS);
        float listH = shown * ROW, foot = hiddenCount > 0 ? FOOT : 0;
        ph = PAD * 2 + listH + foot;
        // Out to the right of the button; under it where the window leaves no room for that.
        boolean beside = bx + bw + 6 + PANEL <= vw - 6;
        px = beside ? bx + bw + 6 : Math.max(6, Math.min(bx, vw - 6 - PANEL));
        py = Math.max(6, Math.min(beside ? by - PAD - 3 : by + ICON + 5, vh - 6 - ph));
        scroll = Math.clamp(scroll, 0, (rows.size() - shown) * ROW);

        c.pushAlpha(o);
        c.push();
        c.scale(0.95f + 0.05f * o, beside ? px : px + 14, beside ? by + ICON / 2 : py);
        c.translate(beside ? -5 * (1 - o) : 0, beside ? 0 : -5 * (1 - o));
        c.shadow(px, py + 3, PANEL, ph, Theme.R_MD, 16, 0x70000000);
        c.rect(px, py, PANEL, ph, Theme.R_MD, 0xF2171422);
        c.stroke(px, py, PANEL, ph, Theme.R_MD, 1, Theme.BORDER_STRONG);

        boolean clipped = rows.size() > shown;
        if (clipped) c.clip(px, py + PAD, PANEL, listH);
        for (int i = 0; i < rows.size(); i++) {
            float ry = py + PAD + i * ROW - scroll;
            if (ry + ROW < py || ry > py + PAD + listH) continue;
            row(c, rows.get(i), ry, mx, my, at(mx, my) == i);
        }
        if (clipped) c.unclip();

        if (foot > 0) {
            float fy = py + ph - PAD - FOOT;
            boolean over = FOOTER.equals(partId(mx, my));
            float hv = spring(FOOTER).target(over ? 1 : 0).update();
            c.rect(px + 8, fy + 2, PANEL - 16, 1, 0, Theme.BORDER);
            c.textMiddle(Fonts.REGULAR, hiddenCount + " hidden", px + PAD + 6, fy + 3, FOOT - 3, 7, Theme.TEXT_MUTED);
            String action = showHidden ? "Done" : "Show";
            c.textMiddle(Fonts.MEDIUM, action, px + PANEL - PAD - 6 - Fonts.MEDIUM.width(action, 7), fy + 3, FOOT - 3, 7,
                    Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
        }
        c.pop();
        c.popAlpha();
    }

    private void row(Canvas c, Entry e, float ry, float mx, float my, boolean over) {
        boolean gone = ModButtons.hidden(e);
        float hv = spring(e.id() + "#row").target(over ? 1 : 0).update();
        float x = px + PAD, w = PANEL - PAD * 2;
        c.rect(x, ry + 1, w, ROW - 2, 5, Colors.withAlpha(0xFFFFFFFF, 0.075f * hv));

        float tx = x + 5 + 14 + 7, room = x + w - tx - MINI * 2 - 8;
        c.pushAlpha(gone || !e.active() ? 0.45f : 1);
        face(c, e.icon(), x + 5, ry + (ROW - 14) / 2, 14, hv);
        String label = Fonts.MEDIUM.truncateAny(e.label(), 8, room);
        if (e.mod() != null && !e.mod().equals(e.label())) {
            c.textAny(Fonts.MEDIUM, label, tx, ry + 4.5f, 8, Theme.TEXT);
            c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny(e.mod(), 6.3f, room), tx, ry + 15, 6.3f, Theme.TEXT_MUTED);
        } else {
            c.textAny(Fonts.MEDIUM, label, tx, ry + (ROW - Fonts.MEDIUM.height(8)) / 2, 8, Theme.TEXT);
        }
        c.popAlpha();

        // Pin and hide appear with the pointer; a hidden row only offers to come back.
        c.pushAlpha(hv);
        int part = over ? part(mx) : 0;
        if (!gone) mini(c, Icons.PIN, miniX(1), ry, part == 1);
        mini(c, gone ? Icons.SHOW : Icons.HIDE, miniX(2), ry, part == 2);
        c.popAlpha();
    }

    private void mini(Canvas c, Icons icon, float x, float ry, boolean over) {
        float y = ry + (ROW - MINI) / 2;
        if (over) c.rect(x, y, MINI, MINI, 4, Theme.RAISED_HOVER);
        icon.draw(c, x + MINI / 2, y + MINI / 2, 8.5f, over ? Theme.TEXT : Theme.TEXT_DIM);
    }

    /** Left edge of the pin (1) or hide (2) button of a row. */
    private float miniX(int part) {
        float right = px + PANEL - PAD - 4;
        return part == 2 ? right - MINI : right - MINI * 2 - 2;
    }

    /** Which part of a row the pointer is over: 0 the row, 1 pin, 2 hide. */
    private int part(float mx) {
        if (mx >= miniX(2)) return 2;
        return mx >= miniX(1) && mx < miniX(1) + MINI ? 1 : 0;
    }

    /** The row under the pointer, or -1. */
    private int at(float mx, float my) {
        if (!open) return -1;
        float listH = Math.min(rows.size(), MAX_ROWS) * ROW;
        if (!inside(mx, my, px + PAD, py + PAD, PANEL - PAD * 2, listH)) return -1;
        int i = (int) ((my - py - PAD + scroll) / ROW);
        return i >= 0 && i < rows.size() ? i : -1;
    }

    /** What a click at the pointer would be on: a row's id, the footer, or null. */
    private String partId(float mx, float my) {
        int i = at(mx, my);
        if (i >= 0) return rows.get(i).id();
        boolean footer = open && hiddenCount > 0 && inside(mx, my, px, py + ph - PAD - FOOT, PANEL, FOOT + PAD);
        return footer ? FOOTER : null;
    }

    private int pinAt(float mx, float my) {
        for (int i = 0; i < pins.size(); i++) {
            if (inside(mx, my, stripX, stripY + i * (ICON + GAP), ICON, ICON)) return i;
        }
        return -1;
    }

    private boolean overButton(float mx, float my) {
        return listed > 0 && inside(mx, my, bx, by, bw, ICON);
    }

    boolean mouseDown(float mx, float my, int button) {
        if (open) {
            if (inside(mx, my, px, py, PANEL, ph)) {
                if (button == 0) {
                    downId = partId(mx, my);
                    downPart = at(mx, my) >= 0 ? part(mx) : 0;
                }
                return true;
            }
            if (button == 0 && overButton(mx, my)) {
                down = true;
                return true;
            }
            // Anywhere else shuts the list, and does nothing more.
            open = false;
            return true;
        }
        if (button == 0 && overButton(mx, my)) {
            down = true;
            return true;
        }
        int pin = pinAt(mx, my);
        if (pin < 0) return false;
        if (button == 1) {
            ModButtons.pin(pins.get(pin), false);
            Sounds.click();
            sort();
        } else if (button == 0 && pins.get(pin).active()) {
            downId = pins.get(pin).id();
            downPart = -1;
        }
        return true;
    }

    boolean mouseUp(float mx, float my, int button) {
        if (button != 0) return false;
        boolean used = false;
        if (down) {
            down = false;
            if (overButton(mx, my)) {
                open = !open;
                scroll = 0;
                Sounds.click();
                used = true;
            }
        }
        String id = downId;
        downId = null;
        if (id == null) return used;
        if (downPart < 0) {
            int pin = pinAt(mx, my);
            if (pin >= 0 && pins.get(pin).id().equals(id)) run(pins.get(pin));
            return true;
        }
        if (!id.equals(partId(mx, my))) return true;
        if (id.equals(FOOTER)) {
            showHidden = !showHidden;
            Sounds.click();
            sort();
            return true;
        }
        int i = at(mx, my);
        if (i < 0 || part(mx) != downPart) return true;
        Entry e = rows.get(i);
        boolean gone = ModButtons.hidden(e);
        if (downPart == 2) {
            ModButtons.hide(e, !gone);
        } else if (gone) {
            return true;
        } else if (downPart == 1) {
            ModButtons.pin(e, true);
        } else {
            if (e.active()) {
                open = false;
                run(e);
            }
            return true;
        }
        Sounds.click();
        sort();
        return true;
    }

    /** Presses the mod's own button, which makes its own click, and follows wherever that leads. */
    private void run(Entry e) {
        leave.accept(() -> {
            e.press().run();
            if (Mc.current() == owner) refresh();
        });
    }

    boolean mouseScroll(float mx, float my, float amount) {
        if (!open || !inside(mx, my, px, py, PANEL, ph)) return false;
        scroll -= amount * ROW;
        return true;
    }

    boolean keyDown(int key) {
        if (!open || key != GLFW.GLFW_KEY_ESCAPE) return false;
        open = false;
        return true;
    }

    /** For the harness, which has no pointer. */
    void dev(String action) {
        switch (action) {
            case "open" -> open = listed > 0;
            case "close" -> open = false;
            case "pin" -> ModButtons.pin(rows.get(0), true);
            case "unpin" -> ModButtons.pin(pins.get(0), false);
            case "hide" -> ModButtons.hide(rows.get(0), true);
            case "hidden" -> showHidden = !showHidden;
            case "press" -> run(rows.get(0));
            default -> { }
        }
        sort();
    }
}
