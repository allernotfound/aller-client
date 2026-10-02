package dev.aller.screen.palette;

import dev.aller.feature.Waypoints;
import dev.aller.feature.Waypoints.Shape;
import dev.aller.feature.Waypoints.Waypoint;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import dev.aller.ui.widget.Toggle;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The waypoints saved for the current world. Click one to open its editor: rename it, pick a
 * colour from the swatches or the hue bar, and choose the marker shape.
 */
public final class WaypointsPage extends Page {
    private static final float ROW = 28, EDITOR = 70, PAD = 12, SWATCH = 13, SHAPE = 18;
    private static final int[] SWATCHES = {
            0xFF8B5CF6, 0xFF6366F1, 0xFF38BDF8, 0xFF2DD4BF, 0xFF34D399, 0xFFA3E635,
            0xFFFACC15, 0xFFF59E0B, 0xFFFB7185, 0xFFF472B6, 0xFFE2E8F0,
    };

    private static final class RowState {
        final Toggle toggle = new Toggle();
        final Spring hover = Spring.snappy(0);
        final Spring expand = Spring.snappy(0);
        float y, h;
    }

    private final Scroll scroll = new Scroll();
    private final TextField name = new TextField("Waypoint name");
    private final TextField rename = new TextField("Name");
    private final Button add = new Button("Add here", this::addHere).style(Button.Style.PRIMARY);
    private final Map<Waypoint, RowState> states = new IdentityHashMap<>();
    private Waypoint editing;
    private boolean draggingHue;
    private float bx, by, bw, bh;
    private int nextColor;

    public WaypointsPage() {
        name.maxLength = rename.maxLength = 28;
        name.textSize = rename.textSize = 8f;
        add.textSize = 8f;
        rename.onChange = text -> {
            if (editing != null && !text.isBlank()) {
                editing.name = text.trim();
                Waypoints.save();
            }
        };
    }

    @Override
    public String title() {
        return "Waypoints";
    }

    @Override
    public String subtitle() {
        if (!Game.inWorld()) return "";
        int n = Waypoints.all().size();
        return n == 0 ? "None in this world yet" : n + " saved here  ·  click one to edit";
    }

    private void addHere() {
        if (!Game.inWorld()) return;
        String label = name.text.isBlank() ? "Waypoint " + (Waypoints.all().size() + 1) : name.text.trim();
        edit(Waypoints.addHere(label, Waypoints.PALETTE[nextColor++ % Waypoints.PALETTE.length]));
        name.setText("");
        name.focused = false;
    }

    private void edit(Waypoint wp) {
        editing = editing == wp ? null : wp;
        rename.focused = false;
        if (editing != null) rename.setText(editing.name);
        Sounds.click();
    }

    @Override
    public void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        float realMx = mx;
        if (!hot) mx = -999;
        float cw = w - PAD * 2, left = x + PAD;
        if (!Game.inWorld()) {
            c.textCentered(Fonts.SEMIBOLD, "No world loaded", x + w / 2, y + h / 2 - 12, 10f, Theme.TEXT);
            c.textCentered(Fonts.REGULAR, "Waypoints are kept per world and server. Join one to see them.",
                    x + w / 2, y + h / 2 + 3, 8f, Theme.TEXT_MUTED);
            return;
        }
        List<Waypoint> all = Waypoints.all();
        states.keySet().removeIf(wp -> !all.contains(wp));
        if (editing != null && !all.contains(editing)) editing = null;
        float content = 8 + 26 + 8;
        for (Waypoint wp : all) content += ROW + EDITOR * Math.clamp(states.computeIfAbsent(wp, k -> new RowState()).expand.get(), 0f, 1f);
        float cy = y + 8 - scroll.update(content, h);

        name.bounds(left, cy, cw - 70, 20);
        name.draw(c, mx, my);
        add.bounds(left + cw - 64, cy, 64, 20);
        add.draw(c, mx, my);
        cy += 26;

        if (all.isEmpty()) {
            c.textCentered(Fonts.REGULAR, "Stand somewhere worth remembering and press Add here.", x + w / 2, cy + 30, 8f, Theme.TEXT_MUTED);
        }
        String dim = Game.dimensionId();
        for (Waypoint wp : all) {
            RowState st = states.get(wp);
            boolean isEditing = wp == editing;
            float e = Math.clamp(st.expand.target(isEditing ? 1 : 0).update(), 0f, 1f);
            st.y = cy;
            st.h = ROW + EDITOR * e;
            boolean over = mx >= left && mx < left + cw && my >= cy && my < cy + ROW;
            float hv = st.hover.target(over || isEditing ? 1 : 0).update();
            c.rect(left, cy + 1, cw, st.h - 2, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.03f + 0.05f * hv));
            if (e > 0.02f) c.stroke(left, cy + 1, cw, st.h - 2, Theme.R_SM, 1, Colors.withAlpha(wp.color, 0.45f * e));

            float dx = left + 14, dy = cy + ROW / 2;
            Waypoints.drawShape(c, wp.shape(), dx, dy, 5f, wp.color, true);
            boolean here = wp.dimension.equals(dim);
            String detail = String.format("%d, %d, %d", (int) Math.floor(wp.x), (int) Math.floor(wp.y), (int) Math.floor(wp.z))
                    + "  ·  " + (here ? Waypoints.distanceText(Waypoints.distance(wp)) + " away" : Game.pretty(wp.dimension))
                    + (wp.death ? "  ·  death marker" : "");
            c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(wp.name, 8.5f, cw - 100), left + 28, cy + 5, 8.5f,
                    wp.visible ? Theme.TEXT : Theme.TEXT_MUTED);
            c.text(Fonts.REGULAR, detail, left + 28, cy + 16, 7f, Theme.TEXT_MUTED);

            float tx = left + cw - 28 - Toggle.W;
            st.toggle.draw(c, tx, cy + (ROW - Toggle.H) / 2, wp.visible, over && mx >= tx - 4 && mx < tx + Toggle.W + 4);
            boolean overX = over && mx >= left + cw - 22;
            Icons.CLOSE.draw(c, left + cw - 12, cy + ROW / 2, 9, overX ? Theme.DANGER : Colors.fade(Theme.TEXT_MUTED, 0.5f + 0.5f * hv));

            if (e > 0.02f) {
                c.pushAlpha(e);
                c.clip(left, cy + ROW, cw, Math.max(0, st.h - ROW));
                drawEditor(c, wp, left + 10, cy + ROW, cw - 20, mx, my, realMx);
                c.unclip();
                c.popAlpha();
            }
            cy += st.h;
        }
        scroll.drawBar(c, x + w - 5, y + 4, h - 8);
    }

    // Editor geometry, relative to its top-left corner.
    private static final float NAME_Y = 2, NAME_H = 18, SHAPE_Y = 26, COLOR_Y = 50;

    private float shapesX(float ex, float ew) {
        return ex + ew - Shape.values().length * (SHAPE + 3) + 3;
    }

    private float hueX(float ex) {
        return ex + SWATCHES.length * (SWATCH + 4) + 6;
    }

    private void drawEditor(Canvas c, Waypoint wp, float ex, float ey, float ew, float mx, float my, float realMx) {
        if (wp == editing) {
            rename.bounds(ex, ey + NAME_Y, ew, NAME_H);
            rename.draw(c, mx, my);
        }

        c.textMiddle(Fonts.MEDIUM, "Marker", ex + 1, ey + SHAPE_Y, SHAPE, 7.5f, Theme.TEXT_DIM);
        float sx = shapesX(ex, ew);
        for (Shape shape : Shape.values()) {
            boolean sel = wp.shape() == shape;
            boolean over = mx >= sx && mx < sx + SHAPE && my >= ey + SHAPE_Y && my < ey + SHAPE_Y + SHAPE;
            c.rect(sx, ey + SHAPE_Y, SHAPE, SHAPE, 5, sel ? Colors.withAlpha(wp.color, 0.28f) : Colors.withAlpha(Colors.WHITE, over ? 0.12f : 0.05f));
            if (sel) c.stroke(sx, ey + SHAPE_Y, SHAPE, SHAPE, 5, 1, Colors.withAlpha(wp.color, 0.9f));
            Waypoints.drawShape(c, shape, sx + SHAPE / 2, ey + SHAPE_Y + SHAPE / 2, 4.6f, sel ? wp.color : Theme.TEXT_DIM, false);
            sx += SHAPE + 3;
        }

        float cyy = ey + COLOR_Y + SWATCH / 2;
        float cx = ex + SWATCH / 2;
        for (int swatch : SWATCHES) {
            boolean sel = (wp.color | 0xFF000000) == swatch;
            boolean over = Math.hypot(mx - cx, my - cyy) < SWATCH / 2 + 1.5f;
            if (sel) c.ring(cx, cyy, SWATCH / 2 + 1.6f, 1.1f, swatch);
            c.circle(cx, cyy, SWATCH / 2 - (sel || over ? 0 : 1.2f), swatch);
            cx += SWATCH + 4;
        }
        // Hue bar for anything the swatches don't cover.
        float hx = hueX(ex), hw = ex + ew - hx, bar = 7, hy = cyy - bar / 2, cap = bar / 2;
        if (hw > 30) {
            float[] hsv = Colors.toHsv(wp.color);
            if (draggingHue && wp == editing) {
                float t = Math.clamp((realMx - hx - cap) / (hw - bar), 0f, 0.9999f);
                wp.color = Colors.hsv(t, 0.72f, 1f, 1f);
                hsv = Colors.toHsv(wp.color);
            }
            c.rect(hx, hy, hw, bar, cap, 0xFFFF0000);
            float seg = (hw - bar) / 6f;
            for (int i = 0; i < 6; i++) {
                c.gradientH(hx + cap + seg * i, hy, seg + 0.5f, bar, 0, Colors.hsv(i / 6f, 0.72f, 1, 1), Colors.hsv((i + 1) / 6f, 0.72f, 1, 1));
            }
            if (hsv[1] > 0.15f) {
                float kx = hx + cap + hsv[0] * (hw - bar);
                c.circle(kx, cyy, 4.8f, 0x66000000);
                c.circle(kx, cyy, 4f, Colors.WHITE);
            }
        }
    }

    private boolean inBody(float mx, float my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        name.focused = false;
        boolean wasRenaming = rename.focused;
        rename.focused = false;
        if (!inBody(mx, my) || button != 0 || !Game.inWorld()) return false;
        if (name.mouseDown(mx, my, button) || add.mouseDown(mx, my, button)) return true;
        float cw = bw - PAD * 2, left = bx + PAD;
        for (Waypoint wp : new ArrayList<>(Waypoints.all())) {
            RowState st = states.get(wp);
            if (st == null || my < st.y || my >= st.y + st.h || mx < left || mx >= left + cw) continue;
            if (my < st.y + ROW) {
                if (mx >= left + cw - 22) {
                    Waypoints.remove(wp);
                    Sounds.click();
                } else if (mx >= left + cw - 32 - Toggle.W) {
                    wp.visible = !wp.visible;
                    Sounds.toggle(wp.visible);
                    Waypoints.save();
                } else {
                    edit(wp);
                }
                return true;
            }
            if (wp != editing) return true;
            float ex = left + 10, ey = st.y + ROW, ew = cw - 20;
            if (my >= ey + NAME_Y && my < ey + NAME_Y + NAME_H) {
                rename.mouseDown(mx, my, button);
                return true;
            }
            if (my >= ey + SHAPE_Y && my < ey + SHAPE_Y + SHAPE) {
                float sx = shapesX(ex, ew);
                for (Shape shape : Shape.values()) {
                    if (mx >= sx && mx < sx + SHAPE) {
                        wp.icon = shape.name();
                        Sounds.click();
                        Waypoints.save();
                    }
                    sx += SHAPE + 3;
                }
                return true;
            }
            if (my >= ey + COLOR_Y - 3 && my < ey + COLOR_Y + SWATCH + 3) {
                float cx = ex + SWATCH / 2;
                for (int swatch : SWATCHES) {
                    if (Math.abs(mx - cx) < SWATCH / 2 + 2) {
                        wp.color = swatch;
                        Sounds.click();
                        Waypoints.save();
                        return true;
                    }
                    cx += SWATCH + 4;
                }
                if (mx >= hueX(ex)) draggingHue = true;
                return true;
            }
            if (wasRenaming) rename.focused = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        if (draggingHue) {
            draggingHue = false;
            Waypoints.save();
        }
        return add.mouseUp(inBody(mx, my) ? mx : -999, my, button);
    }

    @Override
    public boolean mouseScroll(float mx, float my, float amount) {
        scroll.scroll(amount);
        return true;
    }

    private TextField focused() {
        return name.focused ? name : rename.focused ? rename : null;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        TextField f = focused();
        if (f == null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) f.focused = false;
        else if (key == GLFW.GLFW_KEY_ENTER) {
            if (f == name) addHere();
            else f.focused = false;
        } else f.keyDown(key, mods);
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        TextField f = focused();
        return f != null && f.charTyped(codepoint);
    }

    @Override
    public boolean capturing() {
        return focused() != null;
    }
}
