package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.feature.AutoProfiles;
import dev.aller.feature.Replay;
import dev.aller.feature.Waypoints;
import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Sounds;
import dev.aller.screen.palette.Page;
import dev.aller.screen.palette.ProfilesPage;
import dev.aller.screen.palette.SettingsPage;
import dev.aller.screen.palette.StatsPage;
import dev.aller.screen.palette.WaypointsPage;
import dev.aller.setting.Settings;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.anim.Tween;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.IconButton;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import dev.aller.ui.widget.Toggle;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Aller's control surface: a command palette. Type to find any mod or action, Enter to toggle it,
 * Right for its settings. The category dock under the search bar filters without typing, and
 * deeper views (settings, profiles, waypoints, stats) slide in as pages inside the same panel.
 */
public final class PaletteScreen extends AllerScreen {
    private static final float HEADER = 36, DOCK = 27, FOOTER = 22, ROW_H = 30, SECTION_H = 21, CARD_H = 58, CARD_GAP = 5;

    private record Action(String name, String detail, String keywords, BooleanSupplier available, Runnable run) {}

    private static final class Row {
        String section;
        int sectionCount;
        Module module;
        Action action;
        boolean[] hits;
        float score;
        /** Position inside the scrolling list, set by {@link #arrange()}. */
        float x, y, w, h;

        boolean selectable() {
            return section == null;
        }

        Object key() {
            return module != null ? module : action;
        }
    }

    private static final class RowAnim {
        final Toggle toggle = new Toggle();
        final Spring hover = Spring.snappy(0);
    }

    private final Screen parent;
    private final TextField search = new TextField("Search mods and actions");
    private final List<Action> actions = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<Object, RowAnim> anims = new IdentityHashMap<>();
    private final Scroll scroll = new Scroll();
    private final Spring selY = new Spring(0, 620f, 42f), selH = new Spring(ROW_H, 620f, 42f);
    private final Spring selX = new Spring(0, 620f, 42f), selW = new Spring(0, 620f, 42f);
    private final IconButton rowsView = new IconButton(Icons.ROWS, "List", () -> setGrid(false));
    private final IconButton gridView = new IconButton(Icons.GRID, "Grid", () -> setGrid(true));
    private final Spring dockX = new Spring(0, 520f, 38f), dockW = new Spring(0, 520f, 38f);
    private final Spring pageT = Spring.snappy(0);
    private final Spring backHover = Spring.snappy(0);
    private final Tween intro = new Tween(0.55f, Easing.OUT_CUBIC);
    private final float[] dockPos = new float[Category.values().length + 2];
    private List<String> profileNames = List.of();
    private Category filter;
    private int selected = -1;
    private float contentH;
    private boolean selPrimed, dockPrimed;
    private Page page, drawnPage;
    private float px, py, pw, ph;
    private float listY, listH;
    private float lastMx = -1, lastMy = -1;

    public PaletteScreen() {
        this((Screen) null);
    }

    public PaletteScreen(Screen parent) {
        this.parent = parent;
        search.bare = true;
        search.focused = true;
        search.textSize = 10f;
        search.onChange = text -> {
            rebuild();
            scroll.reset();
        };
        for (Category cat : Category.values()) {
            if (!AllerClient.modules().in(cat).isEmpty()) categories.add(cat);
        }
        action("Edit HUD layout", "Drag, resize and snap everything on your HUD", "hud editor move position layout arrange",
                Game::inWorld, () -> Mc.open(new HudEditorScreen(Mc.screen())));
        action("Client settings", "Accent colour, animation speed, menus and more", "options preferences accent theme color colour blur motion",
                () -> true, () -> open(new SettingsPage()));
        action("Profiles", "Switch between setups, or let rules switch them for you", "profile preset config auto rules",
                () -> true, () -> open(new ProfilesPage()));
        action("Waypoints", "Manage saved places and death markers", "waypoint marker location list",
                () -> true, () -> open(new WaypointsPage()));
        action("Session stats", "Playtime, FPS and combat numbers for this session and past ones", "stats statistics graph dashboard history playtime",
                () -> true, () -> open(new StatsPage()));
        action("Add waypoint here", "Save your current position", "waypoint new mark save place",
                Game::inWorld, () -> {
                    var w = Waypoints.addHere("Waypoint " + (Waypoints.all().size() + 1),
                            Waypoints.PALETTE[Waypoints.all().size() % Waypoints.PALETTE.length]);
                    Toasts.info("Waypoint added", w.name + " at " + Waypoints.coords(w));
                    close();
                });
        action("Save replay clip", "Write the last moments of gameplay to a video file", "clip record video replay highlight",
                () -> Game.inWorld() && Modules.REPLAY.enabled(), () -> {
                    Replay.save();
                    close();
                });
        profileNames = AllerClient.config().profileNames();
        rebuild();
    }

    /** Opens straight to a module's settings (used by the HUD editor). */
    public PaletteScreen(Screen parent, Module module) {
        this(parent);
        page = drawnPage = new SettingsPage(module);
        pageT.snap(1);
        search.focused = false;
    }

    private void action(String name, String detail, String keywords, BooleanSupplier available, Runnable run) {
        actions.add(new Action(name, detail, keywords, available, run));
    }

    private void open(Page p) {
        page = drawnPage = p;
        pageT.target(1);
        search.focused = false;
        Sounds.click();
    }

    private void back() {
        if (page == null) return;
        page = null;
        pageT.target(0);
        search.focused = true;
        Sounds.click();
    }

    @Override
    public void opened() {
        super.opened();
        intro.restart();
    }

    /** Opened from a menu, the palette floats over it: the menu keeps drawing underneath, blurred. */
    @Override
    public AllerScreen underlay() {
        return parent instanceof ScreenHost host ? host.screen : null;
    }

    @Override
    public void close() {
        close(() -> Mc.setScreen(parent));
    }

    // ---- search --------------------------------------------------------------------------------

    private void rebuild() {
        Object keep = selected >= 0 && selected < rows.size() ? rows.get(selected).key() : null;
        rows.clear();
        String q = search.text.trim().toLowerCase();
        if (q.isEmpty()) {
            if (filter == null) {
                List<Row> quick = new ArrayList<>();
                for (Action a : actions) if (a.available.getAsBoolean()) quick.add(actionRow(a, null, 0));
                section("Quick actions", quick.size());
                rows.addAll(quick);
            }
            for (Category cat : categories) {
                if (filter != null && filter != cat) continue;
                List<Module> mods = AllerClient.modules().in(cat);
                if (filter == null) section(cat.label, mods.size());
                for (Module m : mods) rows.add(moduleRow(m, null, 0));
            }
        } else {
            List<Row> found = new ArrayList<>();
            for (Module m : AllerClient.modules().all()) {
                if (filter != null && m.category != filter) continue;
                boolean[] hits = new boolean[m.name.length()];
                float score = match(q, m.name, hits);
                for (String k : m.keywords) if (k.contains(q)) score = Math.max(score, 70);
                if (m.category.label.toLowerCase().contains(q)) score = Math.max(score, 50);
                if (m.description.toLowerCase().contains(q)) score = Math.max(score, 35);
                // A setting's name finds the mod it belongs to ("thickness" -> Custom crosshair).
                if (score <= 0 && q.length() >= 3) {
                    for (var s : m.settings()) if (s != m.keybind && s.name.toLowerCase().contains(q)) score = 20;
                }
                if (score > 0) found.add(moduleRow(m, hits, score));
            }
            if (filter == null) {
                for (Action a : actions) {
                    if (!a.available.getAsBoolean()) continue;
                    boolean[] hits = new boolean[a.name.length()];
                    float score = match(q, a.name, hits);
                    if (a.keywords.contains(q)) score = Math.max(score, 60);
                    if (score > 0) found.add(actionRow(a, hits, score - 1));
                }
                String active = AllerClient.config().activeProfile();
                for (String name : profileNames) {
                    if (name.equals(active)) continue;
                    String label = "Switch to profile: " + name;
                    boolean[] hits = new boolean[label.length()];
                    float score = match(q, label, hits);
                    if (score <= 0) continue;
                    Action a = new Action(label, "Load this setup now", "", () -> true, () -> {
                        AutoProfiles.manualSwitch(name);
                        Toasts.info("Profile: " + name, "Switched");
                        search.setText("");
                        rebuild();
                    });
                    found.add(actionRow(a, hits, score - 2));
                }
            }
            found.sort((a, b) -> Float.compare(b.score, a.score));
            rows.addAll(found);
        }

        // Keep the highlighted item when only the grouping changed; a new query starts at the best match.
        int first = -1, kept = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!r.selectable()) continue;
            if (first < 0) first = i;
            if (keep != null && r.key() == keep) kept = i;
        }
        selected = q.isEmpty() && kept >= 0 ? kept : first;
        arrange();
    }

    private boolean grid() {
        return AllerClient.options().gridView.get();
    }

    private void setGrid(boolean on) {
        if (grid() == on) return;
        AllerClient.options().gridView.set(on);
        AllerClient.config().markDirty();
        selPrimed = false;
    }

    /** Positions every row: full-width lines, or (in grid view) mods as cards flowing into columns. */
    private void arrange() {
        boolean grid = grid();
        float full = Math.max(120, pw - 12);
        int cols = Math.max(2, (int) (full / 128));
        float cardW = (full - CARD_GAP * (cols - 1)) / cols;
        float y = 2;
        int col = 0;
        for (Row r : rows) {
            if (grid && r.module != null) {
                r.x = col * (cardW + CARD_GAP);
                r.w = cardW;
                r.y = y;
                r.h = CARD_H;
                if (++col == cols) {
                    col = 0;
                    y += CARD_H + CARD_GAP;
                }
            } else {
                if (col != 0) {
                    col = 0;
                    y += CARD_H + CARD_GAP;
                }
                r.x = 0;
                r.w = full;
                r.y = y;
                r.h = r.section != null ? SECTION_H : ROW_H;
                y += r.h;
            }
        }
        if (col != 0) y += CARD_H + CARD_GAP;
        contentH = y + 4;
    }

    private void section(String label, int count) {
        Row r = new Row();
        r.section = label;
        r.sectionCount = count;
        rows.add(r);
    }

    private static Row moduleRow(Module m, boolean[] hits, float score) {
        Row r = new Row();
        r.module = m;
        r.hits = hits;
        r.score = score;
        return r;
    }

    private static Row actionRow(Action a, boolean[] hits, float score) {
        Row r = new Row();
        r.action = a;
        r.hits = hits;
        r.score = score;
        return r;
    }

    /**
     * Scores how well {@code q} matches {@code text}: a contiguous match beats scattered letters,
     * and matches at the start of a word beat matches in the middle. Zero means no match.
     */
    private static float match(String q, String text, boolean[] hits) {
        String t = text.toLowerCase();
        int idx = t.indexOf(q);
        if (idx >= 0) {
            for (int i = 0; i < q.length(); i++) hits[idx + i] = true;
            boolean wordStart = idx == 0 || !Character.isLetterOrDigit(t.charAt(idx - 1));
            return 100 + (idx == 0 ? 40 : wordStart ? 25 : 0) - idx * 0.5f - t.length() * 0.05f;
        }
        boolean[] tmp = new boolean[t.length()];
        float score = 30;
        int from = 0, last = -2;
        for (int i = 0; i < q.length(); i++) {
            char ch = q.charAt(i);
            if (ch == ' ') continue;
            int found = t.indexOf(ch, from);
            if (found < 0) return 0;
            boolean wordStart = found == 0 || !Character.isLetterOrDigit(t.charAt(found - 1));
            score += found == last + 1 ? 6 : wordStart ? 8 : 0;
            score -= (found - from) * 0.8f;
            tmp[found] = true;
            last = found;
            from = found + 1;
        }
        System.arraycopy(tmp, 0, hits, 0, tmp.length);
        return Math.max(score, 1);
    }

    private void select(int index, boolean reveal) {
        if (index < 0 || index >= rows.size() || !rows.get(index).selectable()) return;
        selected = index;
        if (reveal) {
            Row r = rows.get(index);
            // Keep the section title in view when moving up to its first row.
            float top = index > 0 && !rows.get(index - 1).selectable() ? rows.get(index - 1).y : r.y;
            scroll.reveal(top, r.y + r.h - top, listH);
        }
    }

    private void move(int direction) {
        int i = selected;
        for (int n = 0; n < rows.size(); n++) {
            i = Math.floorMod(i + direction, rows.size());
            if (rows.get(i).selectable()) {
                select(i, true);
                return;
            }
        }
    }

    /** Up/down in grid view: the nearest card in the next line, keeping the column where possible. */
    private void moveLine(int direction) {
        if (!grid() || selected < 0 || selected >= rows.size()) {
            move(direction);
            return;
        }
        Row from = rows.get(selected);
        int best = -1;
        float bestLine = Float.MAX_VALUE, bestDx = Float.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!r.selectable()) continue;
            float dy = (r.y - from.y) * direction;
            if (dy <= 0.5f) continue;
            float dx = Math.abs(r.x - from.x);
            if (dy < bestLine - 0.5f || Math.abs(dy - bestLine) <= 0.5f && dx < bestDx) {
                bestLine = dy;
                bestDx = dx;
                best = i;
            }
        }
        if (best >= 0) select(best, true);
        else move(direction);
    }

    private void cycleFilter(int direction) {
        int count = categories.size() + 1;
        int current = filter == null ? 0 : categories.indexOf(filter) + 1;
        int next = Math.floorMod(current + direction, count);
        setFilter(next == 0 ? null : categories.get(next - 1));
    }

    private void setFilter(Category cat) {
        if (filter == cat) return;
        filter = cat;
        selected = -1;
        rebuild();
        scroll.reset();
        Sounds.click();
    }

    private void activate(Row r, boolean settings) {
        if (r.module != null) {
            if (settings) open(new SettingsPage(r.module));
            else AllerClient.modules().userToggle(r.module);
        } else if (r.action != null) {
            Sounds.click();
            r.action.run.run();
        }
    }

    // ---- drawing -------------------------------------------------------------------------------

    @Override
    protected void layout() {
        pw = Math.min(440, width - 24);
        ph = Math.min(330, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        float open = openness(), fade = fade();
        boolean inWorld = Mc.mc().level != null;
        if (!inWorld && underlay() == null) Theme.scene(c, width, height);
        c.rect(0, 0, width, height, 0, Colors.withAlpha(0xFF050409, 0.42f * fade));

        c.pushAlpha(fade);
        c.push();
        c.scale(0.94f + 0.06f * open, width / 2, height / 2);
        c.translate(0, (1 - open) * 10);
        Theme.panel(c, px, py, pw, ph, Theme.R_LG);

        float t = Math.clamp(pageT.update(), 0f, 1f);
        if (t < 0.02f && page == null) drawnPage = null;
        float bodyY = py + HEADER, bodyH = ph - HEADER - FOOTER;
        boolean inPanel = mx >= px && mx < px + pw && my >= py && my < py + ph;

        c.clip(px, py, pw, ph);
        if (t < 0.98f) {
            c.pushAlpha(1 - t);
            c.push();
            c.translate(-28 * t, 0);
            drawSearch(c);
            drawRoot(c, bodyY, bodyH, mx, my, inPanel && page == null);
            c.pop();
            c.popAlpha();
        }
        if (drawnPage != null && t > 0.02f) {
            c.pushAlpha(t);
            c.push();
            c.translate(28 * (1 - t), 0);
            drawPageHeader(c, mx, my);
            boolean hot = page != null && inPanel && my >= bodyY && my < bodyY + bodyH;
            c.clip(px, bodyY, pw, bodyH);
            drawnPage.draw(c, px, bodyY, pw, bodyH, mx, my, hot);
            c.unclip();
            c.pop();
            c.popAlpha();
        }
        c.unclip();

        c.rect(px + 1, py + HEADER - 0.5f, pw - 2, 0.5f, 0, Theme.BORDER);
        c.rect(px + 1, py + ph - FOOTER, pw - 2, 0.5f, 0, Theme.BORDER);
        drawFooter(c, t);
        c.pop();
        c.popAlpha();

        lastMx = mx;
        lastMy = my;
        Toasts.draw(c);
    }

    private void drawSearch(Canvas c) {
        float cx = px + 17, cy = py + HEADER / 2;
        int icon = Colors.mix(Theme.TEXT_MUTED, Theme.accent(), search.text.isEmpty() ? 0 : 1);
        c.ring(cx - 1, cy - 1, 4.2f, 1.3f, icon);
        c.line(cx + 2.2f, cy + 2.2f, cx + 5.2f, cy + 5.2f, 1.4f, icon);
        search.bounds(px + 31, py, pw - 31 - 44, HEADER);
        search.draw(c, 0, 0);
        float kw = Fonts.SEMIBOLD.width("esc", 12 * 0.56f) + 12 * 0.6f;
        Theme.keycap(c, "esc", px + pw - 12 - kw, py + (HEADER - 12) / 2, 12);
    }

    private void drawPageHeader(Canvas c, float mx, float my) {
        boolean over = page != null && mx >= px + 8 && mx < px + 30 && my >= py + 7 && my < py + 29;
        float hv = backHover.target(over ? 1 : 0).update();
        c.rect(px + 9, py + 8, 20, 20, 6, Colors.withAlpha(Colors.WHITE, 0.06f + 0.08f * hv));
        c.textCentered(Fonts.MEDIUM, "←", px + 19 - 1.5f * hv, py + 8 + (20 - Fonts.MEDIUM.height(9.5f)) / 2, 9.5f,
                Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
        float tx = px + 37;
        tx += c.textMiddle(Fonts.SEMIBOLD, drawnPage.title(), tx, py, HEADER, 10.5f, Theme.TEXT);
        String sub = drawnPage.subtitle();
        if (!sub.isEmpty()) {
            c.textMiddle(Fonts.REGULAR, Fonts.REGULAR.truncate(sub, 8f, px + pw - 60 - tx), tx + 8, py + 0.5f, HEADER, 8f, Theme.TEXT_MUTED);
        }
        drawnPage.header(c, px + pw - 14, py, HEADER, mx, my);
    }

    private void drawRoot(Canvas c, float y, float h, float mx, float my, boolean interactive) {
        // Category dock.
        float dx = px + 10, dy = y + 6, dh = 16;
        String[] labels = new String[categories.size() + 1];
        labels[0] = "All";
        for (int i = 0; i < categories.size(); i++) labels[i + 1] = categories.get(i).label;
        int active = filter == null ? 0 : categories.indexOf(filter) + 1;
        float cx = dx, ax = dx, aw = 0;
        for (int i = 0; i < labels.length; i++) {
            float w = Fonts.MEDIUM.width(labels[i], 7.8f) + 16;
            dockPos[i] = cx;
            if (i == active) {
                ax = cx;
                aw = w;
            }
            cx += w + 2;
        }
        dockPos[labels.length] = cx;
        if (!dockPrimed) {
            dockX.snap(ax);
            dockW.snap(aw);
            dockPrimed = true;
        }
        float hx = dockX.target(ax).update(), hw = dockW.target(aw).update();
        c.shadow(hx, dy + 1, hw, dh, dh / 2, 7, Colors.withAlpha(Theme.accent(), 0.35f));
        Theme.accentFill(c, hx, dy, hw, dh, dh / 2);
        for (int i = 0; i < labels.length; i++) {
            float w = dockPos[i + 1] - dockPos[i] - 2;
            boolean over = interactive && mx >= dockPos[i] && mx < dockPos[i] + w && my >= dy - 2 && my < dy + dh + 2;
            int col = i == active ? Theme.onAccent() : over ? Theme.TEXT : Theme.TEXT_DIM;
            c.textMiddle(Fonts.MEDIUM, labels[i], dockPos[i] + 8, dy, dh, 7.8f, col);
        }
        float viewX = px + pw - 12 - 16 * 2 - 3;
        rowsView.active = !grid();
        gridView.active = grid();
        rowsView.bounds(viewX, dy, 16, 16);
        gridView.bounds(viewX + 19, dy, 16, 16);
        rowsView.draw(c, interactive ? mx : -999, my);
        gridView.draw(c, interactive ? mx : -999, my);
        if (filter != null && cx + Fonts.REGULAR.width(filter.blurb, 7.5f) + 16 < viewX) {
            c.textRight(Fonts.REGULAR, filter.blurb, viewX - 8, dy + (dh - Fonts.REGULAR.height(7.5f)) / 2, 7.5f, Theme.TEXT_MUTED);
        }

        // Results.
        arrange();
        listY = y + DOCK;
        listH = h - DOCK;
        float off = scroll.update(contentH, listH);
        boolean inList = interactive && my >= listY && my < listY + listH && mx >= px + 6 && mx < px + pw - 6;
        if (inList && (mx != lastMx || my != lastMy)) {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                float ry = listY + r.y - off, rxx = px + 6 + r.x;
                if (my >= ry && my < ry + r.h && mx >= rxx && mx < rxx + r.w) select(i, false);
            }
        }

        c.clip(px, listY, pw, listH);
        if (rows.isEmpty()) {
            c.textCentered(Fonts.SEMIBOLD, "Nothing matches “" + Fonts.SEMIBOLD.truncate(search.text, 10f, pw * 0.5f) + "”",
                    px + pw / 2, listY + listH / 2 - 14, 10f, Theme.TEXT);
            c.textCentered(Fonts.REGULAR, "Try a different word, or press Tab to change category.", px + pw / 2, listY + listH / 2 + 2, 8f, Theme.TEXT_MUTED);
        }
        float rx = px + 6, rw = pw - 12;
        if (selected >= 0 && selected < rows.size()) {
            Row s = rows.get(selected);
            if (!selPrimed) {
                selY.snap(s.y);
                selX.snap(s.x);
                selW.snap(s.w);
                selH.snap(s.h);
                selPrimed = true;
            }
            float sy = listY + selY.target(s.y).update() - off, sh = selH.target(s.h).update();
            float sx = rx + selX.target(s.x).update(), sw = selW.target(s.w).update();
            boolean card = grid() && s.module != null;
            float inset = card ? 0 : 1;
            c.rect(sx, sy + inset, sw, sh - inset * 2, Theme.R_MD, Colors.withAlpha(Theme.accent(), 0.13f));
            c.stroke(sx, sy + inset, sw, sh - inset * 2, Theme.R_MD, 1, Colors.withAlpha(Theme.accent(), card ? 0.55f : 0.30f));
            if (!card) c.rect(sx, sy + 8, 2, sh - 16, 1, Theme.accent());
        }
        float in = intro.update();
        int visibleIndex = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = listY + r.y - off;
            if (ry + r.h < listY || ry > listY + listH) continue;
            // Rows cascade in when the palette opens.
            float a = Math.clamp(in * 2.2f - visibleIndex * 0.11f, 0f, 1f);
            visibleIndex++;
            c.pushAlpha(a);
            c.push();
            c.translate(0, (1 - a) * 7);
            if (r.section != null) drawSection(c, r, rx, ry, rw);
            else if (grid() && r.module != null) drawCard(c, r, i == selected, rx + r.x, ry, r.w, inList ? mx : -999, my);
            else drawRow(c, r, i == selected, rx, ry, rw, inList ? mx : -999, my);
            c.pop();
            c.popAlpha();
        }
        c.unclip();
        scroll.drawBar(c, px + pw - 5, listY + 3, listH - 6);
    }

    private void drawSection(Canvas c, Row r, float x, float y, float w) {
        c.text(Fonts.SEMIBOLD, r.section.toUpperCase(), x + 10, y + 9, 6.4f, Theme.TEXT_MUTED);
        float lx = x + 16 + Fonts.SEMIBOLD.width(r.section.toUpperCase(), 6.4f);
        c.rect(lx, y + 12.5f, x + w - 10 - lx, 0.5f, 0, 0x14FFFFFF);
    }

    private void drawRow(Canvas c, Row r, boolean isSelected, float x, float y, float w, float mx, float my) {
        RowAnim anim = anims.computeIfAbsent(r.key(), k -> new RowAnim());
        float hv = anim.hover.target(isSelected ? 1 : 0).update();
        float right = x + w - 10;
        String name, detail;
        float textX = x + 30;

        if (r.module != null) {
            Module m = r.module;
            name = m.name;
            detail = m.description;
            boolean on = m.enabled();
            float cx = x + 16, cy = y + ROW_H / 2;
            if (on) {
                c.shadow(cx - 3, cy - 3, 6, 6, 3, 6, Colors.withAlpha(Theme.accent(), 0.7f));
                c.circle(cx, cy, 3, Theme.accent());
            } else {
                c.ring(cx, cy, 3, 1.1f, 0x55FFFFFF);
            }
            // Chevron: opens settings.
            boolean overChevron = mx >= right - 14 && mx < right + 4 && my >= y && my < y + ROW_H;
            c.textMiddle(Fonts.MEDIUM, "›", right - 7, y - 0.5f, ROW_H, 11f,
                    overChevron ? Theme.TEXT : Colors.fade(Theme.TEXT_MUTED, 0.45f + 0.55f * hv));
            right -= 20;
            anim.toggle.draw(c, right - Toggle.W, y + (ROW_H - Toggle.H) / 2, on, isSelected && !overChevron);
            right -= Toggle.W + 8;
            int key = m.keybind.get();
            if (key != Settings.Key.NONE) {
                String label = Mc.keyName(key);
                float kw = Math.max(13, Fonts.SEMIBOLD.width(label, 13 * 0.56f) + 13 * 0.6f);
                Theme.keycap(c, label, right - kw, y + (ROW_H - 13) / 2, 13);
                right -= kw + 8;
            }
        } else {
            name = r.action.name;
            detail = r.action.detail;
            c.rect(x + 8, y + 7, 16, 16, 5, Colors.withAlpha(Theme.accent(), 0.16f + 0.14f * hv));
            c.textCentered(Fonts.SEMIBOLD, "→", x + 16 + 1.2f * hv, y + 7 + (16 - Fonts.SEMIBOLD.height(8f)) / 2, 8f,
                    Colors.lighten(Theme.accent(), 0.35f));
            if (isSelected) {
                float kw = 13;
                c.pushAlpha(Math.clamp(hv, 0f, 1f));
                Theme.keycap(c, "⏎", right - kw, y + (ROW_H - 13) / 2, 13);
                c.popAlpha();
                right -= kw + 8;
            }
        }

        float nameSize = 9f;
        float nx = textX + 1.5f * hv, ny = y + 5.5f;
        int base = Colors.mix(Theme.TEXT_DIM, Theme.TEXT, r.module == null || r.module.enabled() ? 1f : 0.35f + 0.65f * hv);
        float endX = drawName(c, name, r.hits, nx, ny, nameSize, base);
        if (r.module != null && r.module.fairPlayNote != null) {
            float bw = Fonts.SEMIBOLD.width("CHECK RULES", 5.6f) + 8, bx = endX + 6;
            if (bx + bw < right) {
                c.rect(bx, ny + 1.2f, bw, 9, 4.5f, Colors.withAlpha(Theme.WARN, 0.14f));
                c.textMiddle(Fonts.SEMIBOLD, "CHECK RULES", bx + 4, ny + 1.2f, 9, 5.6f, Theme.WARN);
            }
        }
        c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(detail, 7.2f, right - textX - 6), nx, y + 17f, 7.2f, Theme.TEXT_MUTED);
    }

    /** Grid view: one mod as a compact card. */
    private void drawCard(Canvas c, Row r, boolean isSelected, float x, float y, float w, float mx, float my) {
        Module m = r.module;
        RowAnim anim = anims.computeIfAbsent(m, k -> new RowAnim());
        float hv = anim.hover.target(isSelected ? 1 : 0).update();
        boolean on = m.enabled();
        c.rect(x, y, w, CARD_H, Theme.R_MD, on ? Colors.withAlpha(Theme.accent(), 0.07f) : 0x0AFFFFFF);
        c.stroke(x, y, w, CARD_H, Theme.R_MD, 1, Theme.BORDER);

        float cx = x + 11, cy = y + 12;
        if (on) {
            c.shadow(cx - 3, cy - 3, 6, 6, 3, 6, Colors.withAlpha(Theme.accent(), 0.7f));
            c.circle(cx, cy, 3, Theme.accent());
        } else {
            c.ring(cx, cy, 3, 1.1f, 0x55FFFFFF);
        }
        boolean overChevron = mx >= x + w - 18 && mx < x + w && my >= y && my < y + 20;
        c.textMiddle(Fonts.MEDIUM, "›", x + w - 12, y + 1, 20, 11f,
                overChevron ? Theme.TEXT : Colors.fade(Theme.TEXT_MUTED, 0.45f + 0.55f * hv));

        int base = Colors.mix(Theme.TEXT_DIM, Theme.TEXT, on ? 1f : 0.35f + 0.65f * hv);
        String name = Fonts.SEMIBOLD.truncate(m.name, 8.5f, w - 38);
        boolean[] hits = r.hits != null && name.equals(m.name) ? r.hits : null;
        drawName(c, name, hits, x + 20, y + 6.5f, 8.5f, base);

        List<String> lines = Fonts.REGULAR.wrap(m.description, 6.6f, w - 18);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String line = i == 1 && lines.size() > 2 ? Fonts.REGULAR.truncate(lines.get(1) + " …", 6.6f, w - 18) : lines.get(i);
            c.text(Fonts.REGULAR, line, x + 10, y + 20.5f + i * 8.5f, 6.6f, Theme.TEXT_MUTED);
        }

        anim.toggle.draw(c, x + w - 9 - Toggle.W, y + CARD_H - 7 - Toggle.H, on, isSelected && !overChevron);
        int key = m.keybind.get();
        if (key != Settings.Key.NONE) {
            Theme.keycap(c, Mc.keyName(key), x + 9, y + CARD_H - 19, 12);
        } else if (m.fairPlayNote != null) {
            float bw = Fonts.SEMIBOLD.width("CHECK RULES", 5.4f) + 8;
            c.rect(x + 9, y + CARD_H - 17, bw, 9, 4.5f, Colors.withAlpha(Theme.WARN, 0.14f));
            c.textMiddle(Fonts.SEMIBOLD, "CHECK RULES", x + 13, y + CARD_H - 17, 9, 5.4f, Theme.WARN);
        }
    }

    /** Draws a name with the characters that matched the query picked out in the accent colour. */
    private float drawName(Canvas c, String name, boolean[] hits, float x, float y, float size, int color) {
        if (hits == null) return x + c.text(Fonts.SEMIBOLD, name, x, y, size, color);
        int accent = Colors.lighten(Theme.accent(), 0.3f);
        int start = 0;
        while (start < name.length()) {
            int end = start;
            while (end < name.length() && hits[end] == hits[start]) end++;
            x += c.text(Fonts.SEMIBOLD, name.substring(start, end), x, y, size, hits[start] ? accent : color);
            start = end;
        }
        return x;
    }

    private void drawFooter(Canvas c, float t) {
        float fy = py + ph - FOOTER, kh = 11, ky = fy + (FOOTER - kh) / 2 + 0.5f;
        int on = 0;
        for (Module m : AllerClient.modules().all()) if (m.enabled()) on++;
        String status = on + " of " + AllerClient.modules().all().size() + " on  ·  " + AllerClient.config().activeProfile();
        float statusW = Fonts.REGULAR.width(status, 7.2f);
        c.textRight(Fonts.REGULAR, status, px + pw - 12, fy + (FOOTER - Fonts.REGULAR.height(7.2f)) / 2 + 0.5f, 7.2f, Theme.TEXT_MUTED);

        boolean onModule = selected >= 0 && selected < rows.size() && rows.get(selected).module != null;
        String[][] hints = t > 0.5f
                ? new String[][] {{"esc", "back"}}
                : grid()
                ? new String[][] {{"←↑↓→", "move"}, {"⏎", onModule ? "toggle" : "open"}, {"⇧⏎", "settings"}, {"tab", "category"}}
                : new String[][] {{"↑↓", "move"}, {"⏎", onModule ? "toggle" : "open"}, {"→", "settings"}, {"tab", "category"}};
        float x = px + 10, limit = px + pw - 24 - statusW;
        for (String[] hint : hints) {
            float kw = Math.max(kh, Fonts.SEMIBOLD.width(hint[0], kh * 0.56f) + kh * 0.6f);
            float lw = Fonts.REGULAR.width(hint[1], 7.2f);
            if (x + kw + 4 + lw > limit) break;
            Theme.keycap(c, hint[0], x, ky, kh);
            c.textMiddle(Fonts.REGULAR, hint[1], x + kw + 4, fy + 0.5f, FOOTER, 7.2f, Theme.TEXT_MUTED);
            x += kw + lw + 14;
        }
    }

    // ---- input ---------------------------------------------------------------------------------

    private boolean inBody(float y) {
        return y >= py + HEADER && y < py + ph - FOOTER;
    }

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        if (x < px || x >= px + pw || y < py || y >= py + ph) {
            close();
            return true;
        }
        if (page != null) {
            if (y < py + HEADER) {
                if (x < px + 32) back();
                else page.headerClick(x, y, button);
                return true;
            }
            if (inBody(y)) page.mouseDown(x, y, button);
            return true;
        }
        if (!inBody(y)) return true;
        if (y < listY) {
            if (button == 0 && rowsView.hit(x, y)) {
                Sounds.click();
                setGrid(false);
            } else if (button == 0 && gridView.hit(x, y)) {
                Sounds.click();
                setGrid(true);
            }
            int count = categories.size() + 1;
            for (int i = 0; i < count; i++) {
                if (x >= dockPos[i] && x < dockPos[i + 1] - 2) setFilter(i == 0 ? null : categories.get(i - 1));
            }
            return true;
        }
        float off = scroll.get();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = listY + r.y - off, rx = px + 6 + r.x;
            if (!r.selectable() || y < ry || y >= ry + r.h || x < rx || x >= rx + r.w) continue;
            select(i, false);
            boolean card = grid() && r.module != null;
            boolean chevron = r.module != null && (card ? x >= rx + r.w - 18 && y < ry + 20 : x >= px + pw - 6 - 24);
            if (button == 0 || button == 1) activate(r, chevron || button == 1);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        return page != null && page.mouseUp(x, y, button);
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        if (page != null) return page.mouseScroll(x, y, amount);
        scroll.scroll(amount);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (isClosing()) return false;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (page != null) {
            if (page.capturing()) {
                page.keyDown(key, mods);
                return true;
            }
            if (page.keyDown(key, mods)) return true;
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_LEFT) back();
            else if (isMenuKey(key)) close();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (search.text.isEmpty()) close();
                else search.setText("");
                if (!isClosing()) {
                    rebuild();
                    scroll.reset();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveLine(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveLine(-1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (grid() && search.text.isEmpty()) {
                    move(-1);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                for (int i = 0; i < 6; i++) move(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                for (int i = 0; i < 6; i++) move(-1);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                cycleFilter(shift ? -1 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (selected >= 0 && selected < rows.size()) activate(rows.get(selected), shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (grid() && search.text.isEmpty()) {
                    move(1);
                    return true;
                }
                if (search.caretAtEnd() && selected >= 0 && selected < rows.size() && rows.get(selected).module != null) {
                    activate(rows.get(selected), true);
                    return true;
                }
            }
            default -> {}
        }
        if (isMenuKey(key)) {
            close();
            return true;
        }
        search.focused = true;
        search.keyDown(key, mods);
        return true;
    }

    /** The palette key closes the palette too, unless it is a key that types a character. */
    private static boolean isMenuKey(int key) {
        return key == AllerClient.options().menuKey.get() && GLFW.glfwGetKeyName(key, 0) == null && key != GLFW.GLFW_KEY_SPACE;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (isClosing()) return false;
        if (page != null) return page.charTyped(codepoint);
        search.focused = true;
        return search.charTyped(codepoint);
    }

    @Override
    public boolean closeOnEscape() {
        return false; // Escape is contextual here: back, clear the query, then close
    }
}
