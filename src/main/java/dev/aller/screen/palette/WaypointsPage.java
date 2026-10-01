package dev.aller.screen.palette;

import dev.aller.feature.Waypoints;
import dev.aller.feature.Waypoints.Waypoint;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
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

/** The waypoints saved for the current world: add, recolour, hide and delete. */
public final class WaypointsPage extends Page {
    private static final float ROW = 28;

    private static final class RowState {
        final Toggle toggle = new Toggle();
        final Spring hover = Spring.snappy(0);
        float y;
    }

    private final Scroll scroll = new Scroll();
    private final TextField name = new TextField("Waypoint name");
    private final Button add = new Button("Add here", this::addHere).style(Button.Style.PRIMARY);
    private final Map<Waypoint, RowState> states = new IdentityHashMap<>();
    private float bx, by, bw, bh;
    private int nextColor;

    public WaypointsPage() {
        name.maxLength = 28;
        name.textSize = 8f;
        add.textSize = 8f;
    }

    @Override
    public String title() {
        return "Waypoints";
    }

    @Override
    public String subtitle() {
        if (!Game.inWorld()) return "";
        int n = Waypoints.all().size();
        return n == 0 ? "None in this world yet" : n + (n == 1 ? " saved here" : " saved here");
    }

    private void addHere() {
        if (!Game.inWorld()) return;
        String label = name.text.isBlank() ? "Waypoint " + (Waypoints.all().size() + 1) : name.text.trim();
        Waypoints.addHere(label, Waypoints.PALETTE[nextColor++ % Waypoints.PALETTE.length]);
        name.setText("");
    }

    @Override
    public void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        if (!hot) mx = -999;
        float pad = 12, cw = w - pad * 2;
        if (!Game.inWorld()) {
            c.textCentered(Fonts.SEMIBOLD, "No world loaded", x + w / 2, y + h / 2 - 12, 10f, Theme.TEXT);
            c.textCentered(Fonts.REGULAR, "Waypoints are kept per world and server. Join one to see them.",
                    x + w / 2, y + h / 2 + 3, 8f, Theme.TEXT_MUTED);
            return;
        }
        List<Waypoint> all = Waypoints.all();
        states.keySet().removeIf(wp -> !all.contains(wp));
        float content = 8 + 26 + all.size() * ROW + 8;
        float cy = y + 8 - scroll.update(content, h);

        name.bounds(x + pad, cy, cw - 70, 20);
        name.draw(c, mx, my);
        add.bounds(x + pad + cw - 64, cy, 64, 20);
        add.draw(c, mx, my);
        cy += 26;

        if (all.isEmpty()) {
            c.textCentered(Fonts.REGULAR, "Stand somewhere worth remembering and press Add here.", x + w / 2, cy + 30, 8f, Theme.TEXT_MUTED);
        }
        String dim = Game.dimensionId();
        for (Waypoint wp : all) {
            RowState st = states.computeIfAbsent(wp, k -> new RowState());
            st.y = cy;
            boolean over = mx >= x + pad && mx < x + pad + cw && my >= cy && my < cy + ROW;
            float hv = st.hover.target(over ? 1 : 0).update();
            c.rect(x + pad, cy + 1, cw, ROW - 2, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.03f + 0.05f * hv));
            float dx = x + pad + 13, dy = cy + ROW / 2;
            c.shadow(dx - 4, dy - 4, 8, 8, 4, 6, Colors.withAlpha(wp.color, 0.55f));
            c.circle(dx, dy, 4, wp.color);
            if (wp.death) c.textCentered(Fonts.BOLD, "✕", dx, dy - Fonts.BOLD.height(5.5f) / 2, 5.5f, 0xFF14121D);

            boolean here = wp.dimension.equals(dim);
            String detail = String.format("%d, %d, %d", (int) Math.floor(wp.x), (int) Math.floor(wp.y), (int) Math.floor(wp.z))
                    + "  ·  " + (here ? Waypoints.distanceText(Waypoints.distance(wp)) + " away" : Game.pretty(wp.dimension));
            c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(wp.name, 8.5f, cw - 100), x + pad + 26, cy + 5, 8.5f,
                    wp.visible ? Theme.TEXT : Theme.TEXT_MUTED);
            c.text(Fonts.REGULAR, detail, x + pad + 26, cy + 16, 7f, Theme.TEXT_MUTED);

            float tx = x + pad + cw - 26 - Toggle.W;
            st.toggle.draw(c, tx, cy + (ROW - Toggle.H) / 2, wp.visible, over && mx >= tx - 4 && mx < tx + Toggle.W + 4);
            boolean overX = over && mx >= x + pad + cw - 20;
            c.textMiddle(Fonts.MEDIUM, "✕", x + pad + cw - 14, cy, ROW, 8f, overX ? Theme.DANGER : Colors.fade(Theme.TEXT_MUTED, 0.4f + 0.6f * hv));
            cy += ROW;
        }
        scroll.drawBar(c, x + w - 5, y + 4, h - 8);
    }

    private boolean inBody(float mx, float my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        name.focused = false;
        if (!inBody(mx, my) || button != 0 || !Game.inWorld()) return false;
        if (name.mouseDown(mx, my, button) || add.mouseDown(mx, my, button)) return true;
        float pad = 12, cw = bw - pad * 2, x = bx;
        for (Waypoint wp : new ArrayList<>(Waypoints.all())) {
            RowState st = states.get(wp);
            if (st == null || my < st.y || my >= st.y + ROW || mx < x + pad || mx >= x + pad + cw) continue;
            if (mx >= x + pad + cw - 20) {
                Waypoints.remove(wp);
                Sounds.click();
            } else if (mx >= x + pad + cw - 30 - Toggle.W) {
                wp.visible = !wp.visible;
                Sounds.toggle(wp.visible);
                Waypoints.save();
            } else if (mx < x + pad + 24) {
                int[] palette = Waypoints.PALETTE;
                int idx = 0;
                for (int i = 0; i < palette.length; i++) if (palette[i] == wp.color) idx = i + 1;
                wp.color = palette[idx % palette.length];
                Sounds.click();
                Waypoints.save();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        return add.mouseUp(inBody(mx, my) ? mx : -999, my, button);
    }

    @Override
    public boolean mouseScroll(float mx, float my, float amount) {
        scroll.scroll(amount);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (!name.focused) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) name.focused = false;
        else if (key == GLFW.GLFW_KEY_ENTER) addHere();
        else name.keyDown(key, mods);
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        return name.focused && name.charTyped(codepoint);
    }

    @Override
    public boolean capturing() {
        return name.focused;
    }
}
