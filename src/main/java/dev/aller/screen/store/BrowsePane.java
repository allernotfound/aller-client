package dev.aller.screen.store;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.aller.AllerClient;
import dev.aller.feature.store.Modrinth;
import dev.aller.feature.store.Modrinth.Category;
import dev.aller.feature.store.Modrinth.Project;
import dev.aller.feature.store.Modrinth.Sort;
import dev.aller.feature.store.Store;
import dev.aller.feature.store.Text;
import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.IconButton;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Search: filters down the left, the search bar and sorting along the top, results as a grid or as rows. */
final class BrowsePane extends Pane {
    private static final float SIDEBAR = 112, BAR = 22, GAP = 8, ROW = 46, FILTER = 13;
    private static final int PAGE = 24;
    private static final String VIEW_KEY = "store_grid";

    /** What is kept per result while it is on show. */
    private static final class Card {
        final Project project;
        final InstallButton button;
        final Spring hover = Spring.snappy(0);
        List<String> summary = List.of();
        float summaryWidth = -1;
        float x, y, w, h;
        boolean visible;

        Card(BrowsePane pane, Project project) {
            this.project = project;
            this.button = new InstallButton(pane.screen.kind, project);
        }
    }

    private final TextField search = new TextField("");
    private final Scroll scroll = new Scroll(), filterScroll = new Scroll();
    private final Button sortButton = new Button("", this::nextSort).icon(Icons.SORT);
    private final Button retry = new Button("Try again", () -> search(true));
    private final Button clear = new Button("Clear filters", this::clearFilters).style(Button.Style.GHOST);
    private final IconButton gridButton = new IconButton(Icons.GRID, "Grid", () -> setGrid(true));
    private final IconButton rowsButton = new IconButton(Icons.ROWS, "Rows", () -> setGrid(false));
    private final List<Card> cards = new ArrayList<>();
    private final Set<Category> chosen = new HashSet<>();
    private final Map<Object, Spring> filterHover = new HashMap<>(), filterOn = new HashMap<>();
    /** Filter rows as last drawn, for clicks: the category, or null for the game version switch. */
    private record FilterRow(Category category, float y) {}
    private final List<FilterRow> filterRows = new ArrayList<>();
    private Store.State state = Store.State.IDLE;
    private String error = "";
    private Sort sort = Sort.RELEVANCE;
    private boolean thisVersion = true, grid = true, loadingMore, started;
    private int total, request;
    private float dirtyAt = -1, filtersH, contentH;
    private float mainX, mainW, listY, listH, sideW;

    BrowsePane(StoreScreen screen) {
        super(screen);
        search.placeholder = "Search " + screen.kind.title.toLowerCase(java.util.Locale.ROOT) + " on Modrinth";
        search.textSize = 8.5f;
        search.maxLength = 80;
        search.onChange = text -> dirtyAt = Motion.time();
        sortButton.textSize = 7.8f;
        retry.textSize = 8f;
        clear.textSize = 7.2f;
        JsonElement saved = AllerClient.config().extra(VIEW_KEY);
        try {
            if (saved != null) grid = saved.getAsBoolean();
        } catch (RuntimeException ignored) {
            // keep the grid
        }
    }

    @Override
    void shown() {
        if (!started) {
            started = true;
            search(true);
        }
    }

    @Override
    boolean typing() {
        return search.focused;
    }

    // ---- searching -------------------------------------------------------------------------------

    private void search(boolean fresh) {
        int token = ++request;
        dirtyAt = -1;
        if (fresh) state = Store.State.LOADING;
        else loadingMore = true;
        Modrinth.Query query = new Modrinth.Query(screen.kind, search.text.trim(), sort, Set.copyOf(chosen),
                thisVersion ? Store.gameVersion() : null, fresh ? 0 : cards.size(), PAGE);
        Store.async(() -> Modrinth.search(query), page -> {
            if (token != request) return;
            if (fresh) {
                cards.clear();
                scroll.reset();
            }
            Set<String> have = new HashSet<>();
            for (Card card : cards) have.add(card.project.id);
            for (Project p : page.hits()) if (have.add(p.id)) cards.add(new Card(this, p));
            // Fewer than asked for means the end, whatever the total says.
            total = page.hits().size() < PAGE ? cards.size() : Math.max(page.total(), cards.size());
            state = Store.State.READY;
            loadingMore = false;
        }, message -> {
            if (token != request) return;
            loadingMore = false;
            if (fresh) {
                state = Store.State.FAILED;
                error = message;
            } else {
                // Stop asking for more; what is there stays.
                total = cards.size();
            }
        });
    }

    private void nextSort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        search(true);
    }

    private void clearFilters() {
        chosen.clear();
        search(true);
    }

    private void setGrid(boolean on) {
        if (grid == on) return;
        grid = on;
        scroll.reset();
        AllerClient.config().setExtra(VIEW_KEY, new JsonPrimitive(on));
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    void draw(Canvas c, float mx, float my) {
        if (dirtyAt >= 0 && Motion.time() - dirtyAt > 0.35f) search(true);
        sideW = w >= 380 ? SIDEBAR : 0;
        mainX = x + (sideW > 0 ? sideW + GAP : 0);
        mainW = x + w - mainX;
        if (sideW > 0) filters(c, mx, my);

        // Search, sort, grid or rows.
        float right = x + w;
        rowsButton.bounds(right - BAR, y, BAR, BAR);
        gridButton.bounds(right - BAR * 2 - 4, y, BAR, BAR);
        gridButton.active = grid;
        rowsButton.active = !grid;
        sortButton.label = sort.label;
        float sortW = Math.min(104, mainW * 0.3f);
        sortButton.bounds(gridButton.x - 6 - sortW, y, sortW, BAR);
        search.bounds(mainX, y, sortButton.x - 6 - mainX, BAR);
        search.draw(c, mx, my);
        sortButton.draw(c, mx, my);
        gridButton.draw(c, mx, my);
        rowsButton.draw(c, mx, my);

        listY = y + BAR + GAP;
        listH = y + h - listY;
        results(c, mx, my);
        gridButton.drawTip(c, false);
        rowsButton.drawTip(c, false);
    }

    private void filters(Canvas c, float mx, float my) {
        surface(c, x, y, sideW, h, Theme.R_LG);
        float top = y + 8, viewH = h - 16;
        float off = filterScroll.update(filtersH, viewH);
        boolean within = inside(mx, my, x, top, sideW, viewH);
        c.clip(x, top, sideW, viewH);
        filterRows.clear();
        float fy = top - off;
        fy = heading(c, "Game version", fy);
        fy = filterRow(c, null, "Only for " + Store.gameVersion(), thisVersion, fy, mx, my, within);

        Map<String, List<Category>> groups = new LinkedHashMap<>();
        for (String header : new String[] {"categories", "features", "resolutions", "performance impact"}) groups.put(header, new ArrayList<>());
        for (Category category : Store.categories(screen.kind)) groups.computeIfAbsent(category.header(), k -> new ArrayList<>()).add(category);
        for (Map.Entry<String, List<Category>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            group.getValue().sort((a, b) -> {
                int na = leading(a.name()), nb = leading(b.name());
                return na != nb ? Integer.compare(na, nb) : a.name().compareTo(b.name());
            });
            fy = heading(c, Bits.label(group.getKey()), fy + 6);
            for (Category category : group.getValue()) fy = filterRow(c, category, category.label(), chosen.contains(category), fy, mx, my, within);
        }
        if (Store.categories(screen.kind).isEmpty()) {
            Bits.dots(c, x + sideW / 2, fy + 16, Theme.TEXT_MUTED);
            fy += 30;
        }
        if (!chosen.isEmpty()) {
            clear.bounds(x + 8, fy + 6, sideW - 16, 16);
            clear.draw(c, within ? mx : -10000, my);
            fy += 24;
        } else {
            clear.bounds(0, -100, 0, 0);
        }
        filtersH = fy + off - top + 4;
        c.unclip();
        filterScroll.drawBar(c, x + sideW - 5, top, viewH);
    }

    /** Resolutions sort by their number; everything else by name. */
    private static int leading(String name) {
        int n = 0, i = 0;
        while (i < name.length() && Character.isDigit(name.charAt(i))) n = n * 10 + name.charAt(i++) - '0';
        return i == 0 ? 0 : n;
    }

    private float heading(Canvas c, String text, float fy) {
        c.text(Fonts.SEMIBOLD, text, x + 10, fy + 2, 7.2f, Theme.TEXT_MUTED);
        return fy + 14;
    }

    private float filterRow(Canvas c, Category category, String label, boolean on, float fy, float mx, float my, boolean within) {
        Object key = category == null ? "version" : category;
        boolean over = within && inside(mx, my, x + 4, fy, sideW - 8, FILTER);
        float hv = filterHover.computeIfAbsent(key, k -> Spring.snappy(0)).target(over ? 1 : 0).update();
        float set = Math.clamp(filterOn.computeIfAbsent(key, k -> Spring.snappy(on ? 1 : 0)).target(on ? 1 : 0).update(), 0f, 1f);
        if (hv > 0.01f) c.rect(x + 4, fy, sideW - 8, FILTER, 4, Colors.withAlpha(Colors.WHITE, 0.06f * hv));
        float box = 8, bx = x + 10, by = fy + (FILTER - box) / 2;
        c.rect(bx, by, box, box, 2.5f, Colors.mix(0x33000000, Theme.accent(), set));
        c.stroke(bx, by, box, box, 2.5f, 1, Colors.mix(Theme.BORDER_STRONG, Theme.accent(), set));
        if (set > 0.05f) Icons.CHECK.draw(c, bx + box / 2, by + box / 2, box * 0.95f * set, Theme.onAccent());
        String shown = Fonts.REGULAR.truncate(label, 7.6f, sideW - 34);
        c.textMiddle(on ? Fonts.MEDIUM : Fonts.REGULAR, shown, bx + box + 6, fy, FILTER, 7.6f,
                Colors.mix(Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv), Theme.TEXT, set));
        filterRows.add(new FilterRow(category, fy));
        return fy + FILTER;
    }

    private void results(Canvas c, float mx, float my) {
        for (Card card : cards) card.visible = false;
        if (cards.isEmpty()) {
            float cy = listY + listH / 2;
            retry.bounds(0, -100, 0, 0);
            if (state == Store.State.FAILED) {
                Icons.ALERT.draw(c, mainX + mainW / 2, cy - 26, 18, Theme.WARN);
                c.textCentered(Fonts.SEMIBOLD, error, mainX + mainW / 2, cy - 10, 9.5f, Theme.TEXT);
                c.textCentered(Fonts.REGULAR, "Check the connection and try once more.", mainX + mainW / 2, cy + 4, 7.8f, Theme.TEXT_MUTED);
                retry.bounds(mainX + mainW / 2 - 42, cy + 20, 84, 20);
                retry.draw(c, mx, my);
            } else if (state == Store.State.READY) {
                Icons.SEARCH.draw(c, mainX + mainW / 2, cy - 22, 18, Theme.TEXT_MUTED);
                c.textCentered(Fonts.SEMIBOLD, "Nothing matches", mainX + mainW / 2, cy - 6, 9.5f, Theme.TEXT);
                String hint = thisVersion ? "Try fewer filters, or turn off \"Only for " + Store.gameVersion() + "\"." : "Try another word or fewer filters.";
                c.textCentered(Fonts.REGULAR, hint, mainX + mainW / 2, cy + 8, 7.8f, Theme.TEXT_MUTED);
            } else {
                Bits.dots(c, mainX + mainW / 2, cy, Theme.TEXT_DIM);
            }
            return;
        }

        float lead = 13;
        int columns = grid ? Math.max(1, (int) ((mainW + GAP) / (150 + GAP))) : 1;
        float cw = (mainW - GAP * (columns - 1)) / columns;
        float ch = grid ? Math.round(cw * 0.4f) + 66 : ROW;
        float gap = grid ? GAP : 5;
        int rows = (cards.size() + columns - 1) / columns;
        boolean more = cards.size() < total;
        contentH = lead + rows * (ch + gap) + (more ? 26 : 4);
        float off = scroll.update(contentH, listH);
        if (more && !loadingMore && state == Store.State.READY && off + listH > contentH - 160) search(false);

        boolean within = inside(mx, my, mainX, listY, mainW, listH);
        c.clip(mainX - 3, listY, mainW + 6, listH);
        // While a new search is on its way, the old results wait dimmed.
        c.pushAlpha(state == Store.State.LOADING ? 0.45f : 1f);
        String count = String.format(java.util.Locale.UK, "%,d ", total) + (total == 1 ? screen.kind.noun : screen.kind.noun + "s")
                + (thisVersion ? " for " + Store.gameVersion() : "");
        c.text(Fonts.REGULAR, count, mainX + 1, listY - off + 1, 7.2f, Theme.TEXT_MUTED);
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            card.x = mainX + (i % columns) * (cw + GAP);
            card.y = listY - off + lead + (i / columns) * (ch + gap);
            card.w = cw;
            card.h = ch;
            if (card.y + ch < listY || card.y > listY + listH) continue;
            card.visible = true;
            boolean over = within && inside(mx, my, card.x, card.y, cw, ch);
            if (grid) card(c, card, over, within ? mx : -10000, my);
            else row(c, card, over, within ? mx : -10000, my);
        }
        if (more) Bits.dots(c, mainX + mainW / 2, listY - off + contentH - 13, Theme.TEXT_MUTED);
        c.popAlpha();
        c.unclip();
        scroll.drawBar(c, x + w - 2.5f, listY + 2, listH - 4);
    }

    private void card(Canvas c, Card card, boolean over, float mx, float my) {
        Project p = card.project;
        float hv = card.hover.target(over ? 1 : 0).update();
        float x = card.x, y = card.y, w = card.w, h = card.h, banner = h - 66;
        c.push();
        c.translate(0, -1.5f * hv);
        if (hv > 0.02f) c.shadow(x, y + 4, w, h, Theme.R_LG, 14, Colors.withAlpha(Colors.BLACK, 0.3f * hv));
        c.rect(x, y, w, h, Theme.R_LG, 0xB80D0B14);
        c.rect(x, y, w, h, Theme.R_LG, Colors.withAlpha(Colors.WHITE, 0.03f + 0.04f * hv));
        c.stroke(x, y, w, h, Theme.R_LG, 1, Colors.mix(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0.7f), hv));

        // A picture from the project's gallery across the top, or its colour where it has none.
        float px = x + 3, py = y + 3, pw = w - 6, ph = banner - 3;
        if (!Bits.cover(c, p.banner, px, py, pw, ph, Theme.R_LG - 3)) {
            int tint = Bits.tint(p);
            c.gradientV(px, py, pw, ph, Theme.R_LG - 3, Colors.withAlpha(tint, 0.34f), Colors.withAlpha(tint, 0.08f));
        }
        float icon = 28, ix = x + 8, iy = y + banner - 12;
        c.rect(ix - 2, iy - 2, icon + 4, icon + 4, icon * 0.22f + 2, 0xF2100E18);
        Bits.icon(c, p, ix, iy, icon);

        float tx = ix + icon + 6, tw = x + w - 8 - tx;
        c.textAny(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncateAny(p.title, 8.6f, tw), tx, y + banner + 3, 8.6f, Theme.TEXT);
        c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny("by " + p.author, 6.8f, tw), tx, y + banner + 14, 6.8f, Theme.TEXT_MUTED);

        float sw = w - 16;
        if (card.summaryWidth != sw) {
            card.summaryWidth = sw;
            card.summary = wrap(p.summary, Fonts.REGULAR, 7f, sw, 2);
        }
        float sy = y + banner + 26;
        for (String line : card.summary) {
            c.textAny(Fonts.REGULAR, line, x + 8, sy, 7f, Theme.TEXT_DIM);
            sy += 9.5f;
        }

        float fy = y + h - 15;
        float used = Bits.stat(c, Icons.DOWNLOAD, Text.count(p.downloads), x + 8, fy, 6.8f, Theme.TEXT_MUTED);
        Bits.stat(c, Icons.HEART, Text.count(p.follows), x + 8 + used + 7, fy, 6.8f, Theme.TEXT_MUTED);
        card.button.compact = true;
        card.button.bounds(x + w - 8 - 24, y + h - 21, 24, 16);
        card.button.draw(c, mx, my + 1.5f * hv);
        c.pop();
    }

    private void row(Canvas c, Card card, boolean over, float mx, float my) {
        Project p = card.project;
        float hv = card.hover.target(over ? 1 : 0).update();
        float x = card.x, y = card.y, w = card.w, h = card.h;
        c.rect(x, y, w, h, Theme.R_MD, 0xB80D0B14);
        c.rect(x, y, w, h, Theme.R_MD, Colors.withAlpha(Colors.WHITE, 0.03f + 0.04f * hv));
        c.stroke(x, y, w, h, Theme.R_MD, 1, Colors.mix(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0.7f), hv));
        float icon = 32;
        Bits.icon(c, p, x + 7, y + (h - icon) / 2, icon);

        float bw = 74, right = x + w - 8;
        card.button.compact = false;
        card.button.textSize = 7.6f;
        card.button.bounds(right - bw, y + h - 8 - 18, bw, 18);
        card.button.draw(c, mx, my);
        String downloads = Text.count(p.downloads), follows = Text.count(p.follows);
        float statsW = Bits.statWidth(downloads, 6.8f) + 7 + Bits.statWidth(follows, 6.8f);
        float sx = right - statsW;
        sx += Bits.stat(c, Icons.DOWNLOAD, downloads, sx, y + 8, 6.8f, Theme.TEXT_MUTED) + 7;
        Bits.stat(c, Icons.HEART, follows, sx, y + 8, 6.8f, Theme.TEXT_MUTED);

        float tx = x + 7 + icon + 8, tw = right - Math.max(bw, statsW) - 10 - tx;
        String title = Fonts.SEMIBOLD.truncateAny(p.title, 9f, tw * 0.6f);
        float used = c.textAny(Fonts.SEMIBOLD, title, tx, y + 6, 9f, Theme.TEXT);
        c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny("by " + p.author, 7f, tw - used - 6), tx + used + 6, y + 8, 7f, Theme.TEXT_MUTED);
        c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny(p.summary, 7.3f, tw), tx, y + 19, 7.3f, Theme.TEXT_DIM);
        float cx = tx;
        for (String category : p.categories) {
            String label = Bits.label(category);
            float cwide = Bits.chipWidth(label, 6.2f);
            if (cx + cwide > tx + tw) break;
            Bits.chip(c, label, cx, y + h - 15, 6.2f, 0x14FFFFFF, Theme.TEXT_MUTED);
            cx += cwide + 3;
        }
        String updated = Text.ago(p.updated);
        if (!updated.isEmpty() && cx + Fonts.REGULAR.width(updated, 6.4f) + 8 < tx + tw) {
            c.textRight(Fonts.REGULAR, "Updated " + updated, tx + tw, y + h - 13.5f, 6.4f, Theme.TEXT_MUTED);
        }
    }

    /** Breaks text into at most {@code max} lines, the last ending in an ellipsis if there was more. */
    static List<String> wrap(String text, Fonts font, float size, float width, int max) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (!line.isEmpty() && font.widthAny(candidate, size) > width) {
                if (lines.size() == max - 1) {
                    lines.add(font.truncateAny(line + " " + String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)), size, width));
                    return lines;
                }
                lines.add(line.toString());
                line = new StringBuilder(words[i]);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(font.truncateAny(line.toString(), size, width));
        return lines;
    }

    // For the self-test, which has no pointer.

    void view(boolean asGrid) {
        grid = asGrid;
        scroll.reset();
    }

    Project first() {
        return cards.isEmpty() ? null : cards.get(0).project;
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    boolean mouseDown(float mx, float my, int button) {
        boolean field = search.mouseDown(mx, my, button);
        if (button != 0) return field;
        if (field) return true;
        if (sortButton.mouseDown(mx, my, button) || gridButton.mouseDown(mx, my, button) || rowsButton.mouseDown(mx, my, button)
                || retry.mouseDown(mx, my, button)) return true;
        if (sideW > 0 && inside(mx, my, x, y + 8, sideW, h - 16)) {
            if (clear.mouseDown(mx, my, button)) return true;
            for (FilterRow row : filterRows) {
                if (!inside(mx, my, x + 4, row.y, sideW - 8, FILTER)) continue;
                boolean now;
                if (row.category == null) now = thisVersion = !thisVersion;
                else if (chosen.remove(row.category)) now = false;
                else now = chosen.add(row.category);
                Sounds.toggle(now);
                search(true);
                return true;
            }
            return true;
        }
        if (!inside(mx, my, mainX, listY, mainW, listH)) return false;
        for (Card card : cards) {
            if (!card.visible) continue;
            if (card.button.mouseDown(mx, my, button)) return true;
            if (inside(mx, my, card.x, card.y, card.w, card.h)) {
                Sounds.click();
                screen.open(card.project);
                return true;
            }
        }
        return false;
    }

    @Override
    void mouseUp(float mx, float my, int button) {
        sortButton.mouseUp(mx, my, button);
        gridButton.mouseUp(mx, my, button);
        rowsButton.mouseUp(mx, my, button);
        retry.mouseUp(mx, my, button);
        clear.mouseUp(mx, my, button);
        for (Card card : cards) card.button.mouseUp(mx, my, button);
    }

    @Override
    void scroll(float mx, float my, float amount) {
        if (sideW > 0 && mx < x + sideW) filterScroll.scroll(amount);
        else scroll.scroll(amount * 1.6f);
    }

    @Override
    boolean keyDown(int key, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (search.focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                search.focused = false;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                search(true);
                search.focused = false;
            } else {
                search.keyDown(key, mods);
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            search.focused = true;
            search.selectAll();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll.scroll(-listH / 30f);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll.scroll(listH / 30f);
            case GLFW.GLFW_KEY_DOWN -> scroll.scroll(-2);
            case GLFW.GLFW_KEY_UP -> scroll.scroll(2);
            case GLFW.GLFW_KEY_HOME -> scroll.reset();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean charTyped(int codepoint) {
        // Typing anywhere starts a search.
        if (!search.focused && codepoint > 32) search.focused = true;
        return search.charTyped(codepoint);
    }
}
