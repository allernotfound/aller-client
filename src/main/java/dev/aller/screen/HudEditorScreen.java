package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.hud.Hud;
import dev.aller.hud.HudModule;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Sounds;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.Toggle;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arrange the HUD directly on screen: drag elements (they snap to edges, the centre and each
 * other), scroll over one to resize it, right-click for its settings. A drawer on the right adds
 * and removes elements.
 */
public final class HudEditorScreen extends AllerScreen {
    private static final float MARGIN = 6, GAP = 2, DRAWER_W = 168, ROW = 22;

    private static final class Anim {
        final Spring focus = Spring.snappy(0);
        final Spring hover = Spring.snappy(0);
        final Toggle toggle = new Toggle();
    }

    private final Screen parent;
    private final Map<HudModule, Anim> anims = new IdentityHashMap<>();
    private final List<Button> bar = new ArrayList<>();
    private final Button elements;
    private final Spring drawer = Spring.snappy(0);
    private final Spring guideXAlpha = Spring.smooth(0), guideYAlpha = Spring.smooth(0);
    private final Spring lift = Spring.snappy(0);
    private final Scroll drawerScroll = new Scroll();
    private HudModule dragging, selected;
    private float grabX, grabY, dragX, dragY;
    private float guideX, guideY;
    private boolean drawerOpen, moved;

    public HudEditorScreen(Screen parent) {
        this.parent = parent;
        elements = new Button("Elements", () -> drawerOpen = !drawerOpen);
        bar.add(elements);
        bar.add(new Button("Reset layout", () -> {
            for (HudModule h : Hud.modules()) h.resetPosition();
            AllerClient.config().markDirty();
            Toasts.info("HUD layout reset", "Every element is back in its default spot");
        }).style(Button.Style.GHOST));
        bar.add(new Button("Done", this::close).style(Button.Style.PRIMARY));
        for (Button b : bar) b.textSize = 8.5f;
    }

    @Override
    public void close() {
        AllerClient.config().markDirty();
        close(() -> Mc.setScreen(parent));
    }

    @Override
    public boolean blurBehind() {
        return false;
    }

    /** The editor works in HUD units so elements sit exactly where they will in game. */
    @Override
    public float scale() {
        return AllerClient.options().hudScale.get();
    }

    private Anim anim(HudModule h) {
        return anims.computeIfAbsent(h, k -> new Anim());
    }

    private float drawerX() {
        return width - (DRAWER_W + 8) * Math.clamp(drawer.get(), 0f, 1.05f);
    }

    private HudModule at(float mx, float my) {
        HudModule found = null;
        for (HudModule h : Hud.modules()) {
            if (!h.enabled() || !h.shown) continue;
            float x = h.x(width), y = h.y(height);
            // A small margin makes thin elements easier to grab.
            if (mx >= x - 2 && mx < x + h.scaledW() + 2 && my >= y - 2 && my < y + h.scaledH() + 2) found = h;
        }
        return found;
    }

    // ---- drawing -------------------------------------------------------------------------------

    @Override
    protected void draw(Canvas c, float mx, float my) {
        float fade = fade();
        c.rect(0, 0, width, height, 0, Colors.withAlpha(0xFF050409, 0.32f * fade));
        float dr = drawer.target(drawerOpen ? 1 : 0).update();
        boolean overDrawer = dr > 0.5f && mx >= drawerX();
        boolean overBar = false;
        for (Button b : bar) overBar |= b.hit(mx, my);
        HudModule hovered = dragging != null || overDrawer || overBar ? null : at(mx, my);

        c.pushAlpha(fade);
        // Thirds: an element anchors to whichever third of the screen its centre is in.
        float ga = 0.05f + 0.07f * lift.target(dragging != null ? 1 : 0).update();
        for (int i = 1; i < 3; i++) {
            c.rect(width * i / 3f, 0, 0.5f, height, 0, Colors.withAlpha(Colors.WHITE, ga));
            c.rect(0, height * i / 3f, width, 0.5f, 0, Colors.withAlpha(Colors.WHITE, ga));
        }

        Hud.draw(c, true, dragging);
        for (HudModule h : Hud.modules()) {
            if (h == dragging || !h.enabled() || !h.shown) continue;
            outline(c, h, h.x(width), h.y(height), h == hovered, h == selected);
        }

        if (dragging != null && Hud.measure(dragging, true)) {
            float x = mx - grabX, y = my - grabY;
            if (Math.abs(x - dragX) > 0.5f || Math.abs(y - dragY) > 0.5f) moved = true;
            float[] snapped = snap(dragging, x, y);
            dragX = Math.clamp(snapped[0], 0, Math.max(0, width - dragging.scaledW()));
            dragY = Math.clamp(snapped[1], 0, Math.max(0, height - dragging.scaledH()));
            float gx = guideXAlpha.update(), gy = guideYAlpha.update();
            // Guides span just the things being lined up, so it is clear what the element snapped to.
            if (gx > 0.02f) c.rect(guideX - 0.5f, guideFromY - 4, 1, guideToY - guideFromY + 8, 0, Colors.withAlpha(Theme.accent(), 0.9f * gx));
            if (gy > 0.02f) c.rect(guideFromX - 4, guideY - 0.5f, guideToX - guideFromX + 8, 1, 0, Colors.withAlpha(Theme.accent(), 0.9f * gy));
            float l = lift.get();
            c.shadow(dragX, dragY + 4 * l, dragging.scaledW(), dragging.scaledH(), 6, 14, Colors.withAlpha(Colors.BLACK, 0.45f * l));
            Hud.drawOne(c, dragging, dragX, dragY, 1f, true);
            outline(c, dragging, dragX, dragY, true, true);
        } else {
            guideXAlpha.snap(0);
            guideYAlpha.snap(0);
        }

        // Hint and toolbar.
        String hint = selected != null
                ? selected.name + "  ·  arrows nudge  ·  R resets  ·  Del removes"
                : "Drag to move  ·  scroll to resize  ·  right-click for settings";
        float hw = Fonts.MEDIUM.width(hint, 7.5f) + 20;
        float hy = height - 58 + (1 - openness()) * 30;
        c.rect((width - hw) / 2, hy, hw, 17, 8.5f, 0xD2100E18);
        c.stroke((width - hw) / 2, hy, hw, 17, 8.5f, 1, Theme.BORDER);
        c.textMiddle(Fonts.MEDIUM, hint, (width - hw) / 2 + 10, hy, 17, 7.5f, Theme.TEXT_DIM);

        float bw = 0;
        for (Button b : bar) bw += Fonts.MEDIUM.width(b.label, 8.5f) + 26 + 5;
        bw -= 5;
        float barX = (width - bw) / 2 - 6, barY = height - 34 + (1 - openness()) * 30;
        Theme.panel(c, barX, barY, bw + 12, 28, 14);
        float bx = barX + 6;
        elements.style(drawerOpen ? Button.Style.PRIMARY : Button.Style.GLASS);
        for (Button b : bar) {
            float w = Fonts.MEDIUM.width(b.label, 8.5f) + 26;
            b.bounds(bx, barY + 5, w, 18);
            b.draw(c, mx, my);
            bx += w + 5;
        }

        if (dr > 0.01f) drawDrawer(c, mx, my, dr);
        c.popAlpha();
        Toasts.draw(c);
    }

    private void outline(Canvas c, HudModule h, float x, float y, boolean hovered, boolean isSelected) {
        Anim a = anim(h);
        float f = a.focus.target(isSelected ? 1 : 0).update();
        float hv = a.hover.target(hovered ? 1 : 0).update();
        float w = h.scaledW(), ht = h.scaledH(), p = 2 + hv;
        int col = Colors.mix(Colors.withAlpha(Colors.WHITE, 0.16f + 0.3f * hv), Theme.accent(), f);
        c.stroke(x - p, y - p, w + p * 2, ht + p * 2, 4 + p, 1, col);
        if (f > 0.02f) {
            c.rect(x - p, y - p, w + p * 2, ht + p * 2, 4 + p, Colors.withAlpha(Theme.accent(), 0.08f * f));
            String label = h.name + "  " + String.format("%.2fx", h.scale.get());
            float lw = Fonts.SEMIBOLD.width(label, 6.5f) + 10;
            float lx = Math.clamp(x - p, 2, Math.max(2, width - lw - 2));
            float ly = y - p - 13 < 2 ? y + ht + p + 2 : y - p - 13;
            c.pushAlpha(Math.clamp(f, 0f, 1f));
            c.rect(lx, ly, lw, 11, 4, Theme.accent());
            c.textMiddle(Fonts.SEMIBOLD, label, lx + 5, ly, 11, 6.5f, Theme.onAccent());
            c.popAlpha();
        }
    }

    /**
     * Snapping. Each axis considers a short list of targets and takes the nearest one in range:
     * the screen margin and centre, the matching edge of every other element (left to left, right
     * to right, top to top, bottom to bottom), and sitting directly beside or below a neighbour
     * with the standard gap. Unlike edges are never matched, which is what made columns hard to
     * line up before.
     */
    private float[] snap(HudModule h, float x, float y) {
        float dist = Mc.shiftDown() ? 0 : AllerClient.options().hudSnap.get();
        float w = h.scaledW(), ht = h.scaledH();
        bestX = bestY = dist;
        outX = x;
        outY = y;
        snapH = null;
        snapV = null;
        boolean gx = false, gy = false;

        gx |= tryX(x, MARGIN, 0, 0, height, HudModule.AnchorH.LEFT);
        gx |= tryX(x, width - MARGIN, w, 0, height, HudModule.AnchorH.RIGHT);
        gx |= tryX(x, width / 2, w / 2, 0, height, HudModule.AnchorH.CENTER);
        gy |= tryY(y, MARGIN, 0, 0, width, HudModule.AnchorV.TOP);
        gy |= tryY(y, height - MARGIN, ht, 0, width, HudModule.AnchorV.BOTTOM);
        gy |= tryY(y, height / 2, ht / 2, 0, width, HudModule.AnchorV.MIDDLE);

        for (HudModule o : Hud.modules()) {
            if (o == h || !o.enabled() || !o.shown) continue;
            float ox = o.x(width), oy = o.y(height), ow = o.scaledW(), oh = o.scaledH();
            float top = Math.min(y, oy), bottom = Math.max(y + ht, oy + oh);
            float left = Math.min(x, ox), right = Math.max(x + w, ox + ow);
            gx |= tryX(x, ox, 0, top, bottom, HudModule.AnchorH.LEFT);
            gx |= tryX(x, ox + ow, w, top, bottom, HudModule.AnchorH.RIGHT);
            gy |= tryY(y, oy, 0, left, right, null);
            gy |= tryY(y, oy + oh, ht, left, right, null);
            // Stacking and sitting side by side only make sense next to an element that overlaps on the other axis.
            if (x < ox + ow && x + w > ox) {
                gy |= tryY(y, oy + oh + GAP, 0, left, right, null);
                gy |= tryY(y, oy - GAP, ht, left, right, null);
            }
            if (y < oy + oh && y + ht > oy) {
                gx |= tryX(x, ox + ow + GAP, 0, top, bottom, null);
                gx |= tryX(x, ox - GAP, w, top, bottom, null);
            }
        }
        guideXAlpha.target(gx ? 1 : 0);
        guideYAlpha.target(gy ? 1 : 0);
        return new float[] {outX, outY};
    }

    private float bestX, bestY, outX, outY;
    private float guideFromX, guideToX, guideFromY, guideToY;
    private HudModule.AnchorH snapH;
    private HudModule.AnchorV snapV;

    /** @param off where on the dragged element the target applies (0 = its left edge, w = its right edge) */
    private boolean tryX(float x, float target, float off, float from, float to, HudModule.AnchorH anchor) {
        float d = Math.abs(x + off - target);
        if (d >= bestX) return false;
        bestX = d;
        outX = target - off;
        guideX = target;
        guideFromY = from;
        guideToY = to;
        snapH = anchor;
        return true;
    }

    private boolean tryY(float y, float target, float off, float from, float to, HudModule.AnchorV anchor) {
        float d = Math.abs(y + off - target);
        if (d >= bestY) return false;
        bestY = d;
        outY = target - off;
        guideY = target;
        guideFromX = from;
        guideToX = to;
        snapV = anchor;
        return true;
    }

    private void drawDrawer(Canvas c, float mx, float my, float dr) {
        float x = drawerX(), y = 8, h = height - 16;
        c.pushAlpha(Math.clamp(dr, 0f, 1f));
        Theme.panel(c, x, y, DRAWER_W, h, Theme.R_LG);
        c.rect(x, y, DRAWER_W, h, Theme.R_LG, 0xD9100E18);
        c.text(Fonts.SEMIBOLD, "HUD elements", x + 12, y + 11, 10f, Theme.TEXT);
        int on = 0;
        List<HudModule> all = Hud.modules();
        for (HudModule m : all) if (m.enabled()) on++;
        c.textRight(Fonts.REGULAR, on + " on", x + DRAWER_W - 12, y + 12.5f, 7.5f, Theme.TEXT_MUTED);
        c.rect(x + 1, y + 29.5f, DRAWER_W - 2, 0.5f, 0, Theme.BORDER);

        float ly = y + 32, lh = h - 36;
        float off = drawerScroll.update(all.size() * ROW + 4, lh);
        c.clip(x, ly, DRAWER_W, lh);
        float cy = ly + 2 - off;
        for (HudModule m : all) {
            boolean over = mx >= x + 4 && mx < x + DRAWER_W - 4 && my >= cy && my < cy + ROW && my >= ly && my < ly + lh;
            Anim a = anim(m);
            float hv = a.hover.target(over ? 1 : 0).update();
            c.rect(x + 5, cy + 1, DRAWER_W - 10, ROW - 2, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.06f * hv));
            c.textMiddle(Fonts.MEDIUM, m.name, x + 12 + 1.5f * hv, cy, ROW, 8.2f, m.enabled() ? Theme.TEXT : Theme.TEXT_DIM);
            a.toggle.draw(c, x + DRAWER_W - 12 - Toggle.W, cy + (ROW - Toggle.H) / 2, m.enabled(), over);
            cy += ROW;
        }
        c.unclip();
        drawerScroll.drawBar(c, x + DRAWER_W - 5, ly + 2, lh - 4);
        c.popAlpha();
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        for (Button b : bar) if (b.mouseDown(x, y, button)) return true;
        if (drawerOpen && x >= drawerX()) {
            float ly = 8 + 32, cy = ly + 2 - drawerScroll.get();
            for (HudModule m : Hud.modules()) {
                if (button == 0 && y >= cy && y < cy + ROW && y >= ly) {
                    m.toggle();
                    Sounds.toggle(m.enabled());
                    AllerClient.config().markDirty();
                    if (m.enabled()) selected = m;
                    else if (selected == m) selected = null;
                }
                cy += ROW;
            }
            return true;
        }
        HudModule hit = at(x, y);
        selected = hit;
        if (hit == null) return true;
        if (button == 1) {
            Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen(), hit)));
        } else if (button == 0) {
            dragging = hit;
            dragX = hit.x(width);
            dragY = hit.y(height);
            grabX = x - dragX;
            grabY = y - dragY;
            moved = false;
        }
        return true;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        boolean used = false;
        for (Button b : bar) used |= b.mouseUp(x, y, button);
        if (button == 0 && dragging != null) {
            if (moved) {
                dragging.place(dragX, dragY, width, height, snapH, snapV);
                AllerClient.config().markDirty();
            }
            dragging = null;
            used = true;
        }
        return used;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        if (drawerOpen && x >= drawerX()) {
            drawerScroll.scroll(amount);
            return true;
        }
        HudModule h = dragging != null ? dragging : at(x, y);
        if (h == null) return false;
        selected = h;
        // Resize around the element's centre so it doesn't drift away from the cursor.
        float cx = h.x(width) + h.scaledW() / 2, cy = h.y(height) + h.scaledH() / 2;
        h.scale.set(h.scale.get() + (amount > 0 ? 0.05f : -0.05f));
        if (dragging == null) h.place(cx - h.scaledW() / 2, cy - h.scaledH() / 2, width, height);
        AllerClient.config().markDirty();
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (selected != null && dragging == null) {
            float step = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
            float dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
            float dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
            if (dx != 0 || dy != 0) {
                selected.place(selected.x(width) + dx, selected.y(height) + dy, width, height);
                AllerClient.config().markDirty();
                return true;
            }
            if (key == GLFW.GLFW_KEY_R) {
                selected.resetPosition();
                selected.scale.reset();
                AllerClient.config().markDirty();
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                selected.setEnabled(false);
                Sounds.toggle(false);
                selected = null;
                AllerClient.config().markDirty();
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_TAB) {
            drawerOpen = !drawerOpen;
            return true;
        }
        return super.keyDown(key, mods);
    }
}
