package dev.aller.screen.shots;

import dev.aller.feature.Shots;
import dev.aller.feature.Shots.Shot;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Os;
import dev.aller.platform.Sounds;
import dev.aller.platform.Tex;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.IconButton;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The screenshots: a full page, like the pack store. Every screenshot in a grid under the day it
 * was taken, a column down the left to narrow it to the favourites or to one world or server, and
 * a search. Picking one shows it full size over the page, where it can be zoomed, stepped through,
 * starred, renamed, copied, found in the folder or deleted.
 */
public final class ShotsScreen extends AllerScreen {
    private static final float HEADER = 34, MARGIN = 10, SIDE = 122, GAP = 6, HEADING = 18, TILE = 116, ROW = 19, BUTTON = 20, EDGE = 28, FOOTER = 24;

    private record Tile(Shot shot, float x, float y) {}

    private record Heading(String text, float y) {}

    /** A row of the column on the left: everything, the favourites, or one place. */
    private record Entry(String label, String place, boolean favourites, int count, Icons icon) {}

    private final Screen parent;
    private final Scroll scroll = new Scroll(), sideScroll = new Scroll();
    private final TextField search = new TextField("Search");
    private final TextField rename = new TextField("Name");
    private final IconButton back = new IconButton(Icons.BACK, "Back", this::leave);
    private final IconButton closeButton = new IconButton(Icons.CLOSE, "Close", this::close);
    private final IconButton folder = new IconButton(Icons.FOLDER, "Open the screenshots folder", () -> Mc.openFolder(Shots.dir()));
    private final IconButton star = new IconButton(Icons.STAR, "Favourite", this::favourite);
    private final IconButton edit = new IconButton(Icons.EDIT, "Rename", this::startRename);
    private final IconButton copy = new IconButton(Icons.COPY, "Copy the picture", this::copy);
    private final IconButton reveal = new IconButton(Icons.FOLDER, "Show in folder", this::reveal);
    private final IconButton trash = new IconButton(Icons.TRASH, "Delete", this::delete);
    private final IconButton previous = new IconButton(Icons.CHEVRON_LEFT, "Newer", () -> step(-1));
    private final IconButton following = new IconButton(Icons.CHEVRON_RIGHT, "Older", () -> step(1));
    private final IconButton[] tools = {star, edit, copy, reveal, trash};

    private final List<Shot> shown = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();
    private final Map<Shot, Spring> hovers = new IdentityHashMap<>();
    private final Map<String, Spring> rowHovers = new HashMap<>();
    /** The place the grid is narrowed to, or null for all of them. */
    private String place;
    private boolean favourites;
    private int seen = -1, columns = 1, arrangedFor = -1;
    private float tileW, tileH, contentH, arrangedWidth;
    private float gridX, gridY, gridW, gridH, sideH;

    /** The screenshot shown full size, or null while the grid is in front. */
    private Shot viewing;
    private final Spring view = Spring.smooth(0), zoom = Spring.snappy(1), panX = Spring.snappy(0), panY = Spring.snappy(0), swap = Spring.smooth(1);
    private final Spring undoIn = Spring.snappy(0);
    private float zoomTo = 1, panToX, panToY, lastX, lastY;
    /** Where the picture is, from the last frame: the area it may fill, and its size at no zoom. */
    private float areaX, areaY, areaW, areaH, fitW, fitH;
    private int direction;
    private boolean dragging, renaming, skipChar;
    private long lastClick;
    private float undoX, undoY, undoW;

    public ShotsScreen(Screen parent) {
        this(parent, null);
    }

    /** @param focus a screenshot to open on, full size; null for the grid */
    public ShotsScreen(Screen parent, Shot focus) {
        this.parent = parent;
        search.textSize = 8f;
        search.maxLength = 60;
        search.onChange = text -> seen = -1;
        rename.textSize = 8.5f;
        rename.maxLength = 80;
        trash.danger = true;
        if (focus != null) {
            viewing = focus;
            view.target(1);
        }
    }

    @Override
    public void opened() {
        super.opened();
        Shots.scan();
    }

    @Override
    public void reshown() {
        Shots.scan();
    }

    @Override
    public void close() {
        close(() -> Mc.setScreen(parent));
    }

    @Override
    public boolean closeOnEscape() {
        return false;
    }

    @Override
    public boolean capturing() {
        return search.focused || renaming;
    }

    private boolean looking() {
        return viewing != null && view.target() > 0.5f;
    }

    /** One step back: out of the picture, or out of the screen. */
    private void leave() {
        if (looking()) closeViewer();
        else close();
    }

    // ---- what is shown ---------------------------------------------------------------------------

    private void refresh() {
        seen = Shots.version();
        arrangedFor = -1;
        Map<String, Integer> places = new HashMap<>();
        int stars = 0;
        for (Shot s : Shots.all()) {
            places.merge(s.place, 1, Integer::sum);
            if (s.favourite) stars++;
        }
        if (place != null && !places.containsKey(place)) place = null;
        entries.clear();
        entries.add(new Entry("All screenshots", null, false, Shots.all().size(), Icons.PICTURES));
        entries.add(new Entry("Favourites", null, true, stars, Icons.STAR));
        places.entrySet().stream()
                .sorted((a, b) -> {
                    int by = Integer.compare(b.getValue(), a.getValue());
                    return by != 0 ? by : a.getKey().compareToIgnoreCase(b.getKey());
                })
                .forEach(e -> entries.add(new Entry(e.getKey().isEmpty() ? "Unknown" : e.getKey(), e.getKey(), false, e.getValue(),
                        e.getKey().equals(Shots.MENUS) ? Icons.GRID : Icons.REALMS)));

        String query = search.text.strip().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Shot s : Shots.all()) {
            if (favourites && !s.favourite) continue;
            if (place != null && !place.equals(s.place)) continue;
            if (!query.isEmpty() && !s.name.toLowerCase(Locale.ROOT).contains(query) && !s.place.toLowerCase(Locale.ROOT).contains(query)) continue;
            shown.add(s);
        }
        hovers.keySet().retainAll(new java.util.HashSet<>(Shots.all()));
    }

    private void pick(Entry entry) {
        favourites = entry.favourites;
        place = entry.place;
        seen = -1;
        scroll.reset();
    }

    private boolean picked(Entry entry) {
        return entry.favourites == favourites && java.util.Objects.equals(entry.place, place);
    }

    /** Lays the tiles out in content coordinates, a heading for each day. */
    private void arrange() {
        if (arrangedFor == seen && arrangedWidth == gridW) return;
        arrangedFor = seen;
        arrangedWidth = gridW;
        tiles.clear();
        headings.clear();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone), day = null;
        float y = 0;
        int column = 0;
        for (Shot s : shown) {
            LocalDate taken = Instant.ofEpochMilli(s.time).atZone(zone).toLocalDate();
            if (!taken.equals(day)) {
                if (column > 0) y += tileH + GAP;
                if (day != null) y += 4;
                day = taken;
                column = 0;
                headings.add(new Heading(day(taken, today), y));
                y += HEADING;
            }
            tiles.add(new Tile(s, column * (tileW + GAP), y));
            if (++column == columns) {
                column = 0;
                y += tileH + GAP;
            }
        }
        if (column > 0) y += tileH + GAP;
        contentH = y;
    }

    private static String day(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "Today";
        if (date.equals(today.minusDays(1))) return "Yesterday";
        String text = date.getDayOfMonth() + " " + date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return date.getYear() == today.getYear() ? text : text + " " + date.getYear();
    }

    private static String clock(long time) {
        ZonedDateTime at = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        return String.format(Locale.ROOT, "%02d:%02d", at.getHour(), at.getMinute());
    }

    private static String size(long bytes) {
        if (bytes >= 1 << 20) return String.format(Locale.ROOT, "%.1f MB", bytes / (float) (1 << 20));
        return Math.max(1, bytes >> 10) + " KB";
    }

    // ---- the picture in front --------------------------------------------------------------------

    private void show(Shot shot, int from) {
        if (shot == viewing && looking()) return;
        boolean already = looking();
        viewing = shot;
        view.target(1);
        direction = from;
        renaming = false;
        resetZoom(true);
        if (already && !Motion.reduced()) swap.snap(0).target(1);
        else swap.snap(1);
    }

    private void closeViewer() {
        view.target(0);
        renaming = false;
        dragging = false;
        // Back in the grid, the picture that was open is in view.
        for (Tile t : tiles) if (t.shot == viewing) scroll.reveal(t.y - HEADING, tileH + HEADING + GAP, gridH);
    }

    private void resetZoom(boolean now) {
        zoomTo = 1;
        panToX = panToY = 0;
        if (now) {
            zoom.snap(1);
            panX.snap(0);
            panY.snap(0);
        }
    }

    private void step(int by) {
        if (!looking() || shown.isEmpty()) return;
        int at = shown.indexOf(viewing);
        int next = Math.clamp(at + by, 0, shown.size() - 1);
        if (at < 0) next = 0;
        if (shown.get(next) != viewing) show(shown.get(next), by < 0 ? -1 : 1);
    }

    private void favourite() {
        if (viewing != null) Shots.favourite(viewing, !viewing.favourite);
    }

    private void reveal() {
        if (viewing != null) Os.reveal(viewing.file);
    }

    private void copy() {
        if (viewing == null) return;
        Toasts.info("Copying the picture", viewing.name);
        Shots.copy(viewing, ok -> {
            if (ok) Toasts.info("Copied to the clipboard", "Paste it anywhere a picture goes");
            else Toasts.warn("Could not copy the picture", "The clipboard did not take it.");
        });
    }

    private void delete() {
        if (viewing == null) return;
        int at = shown.indexOf(viewing);
        if (!Shots.delete(viewing)) return;
        refresh();
        if (shown.isEmpty() || at < 0) closeViewer();
        else show(shown.get(Math.min(at, shown.size() - 1)), 1);
    }

    private void undo() {
        Shot restored = Shots.undo();
        if (restored == null) return;
        refresh();
        if (looking() && shown.contains(restored)) show(restored, -1);
    }

    private void startRename() {
        if (viewing == null) return;
        int dot = viewing.name.lastIndexOf('.');
        rename.setText(dot > 0 ? viewing.name.substring(0, dot) : viewing.name);
        rename.selectAll();
        rename.focused = true;
        renaming = true;
    }

    private void finishRename() {
        if (viewing != null && !rename.text.isBlank() && !Shots.rename(viewing, rename.text)) return;
        renaming = false;
        rename.focused = false;
    }

    /** The zoom at which one pixel of the picture covers one pixel of the screen. */
    private float trueSize() {
        if (viewing == null || viewing.width == 0 || fitW <= 0) return 2;
        return viewing.width / (fitW * density());
    }

    private float maxZoom() {
        return Math.max(8, trueSize() * 4);
    }

    /** Zooms to a level, keeping the point of the picture under (mx, my) where it is. */
    private void zoomAt(float level, float mx, float my) {
        level = Math.clamp(level, 1f, maxZoom());
        float cx = areaX + areaW / 2 + panToX, cy = areaY + areaH / 2 + panToY, ratio = level / zoomTo;
        panToX -= (mx - cx) * (ratio - 1);
        panToY -= (my - cy) * (ratio - 1);
        zoomTo = level;
        clampPan();
    }

    private void clampPan() {
        float roomX = Math.max(0, (fitW * zoomTo - areaW) / 2), roomY = Math.max(0, (fitH * zoomTo - areaH) / 2);
        panToX = Math.clamp(panToX, -roomX, roomX);
        panToY = Math.clamp(panToY, -roomY, roomY);
    }

    /** Stands in for the clicks the self-test cannot make. */
    public void dev(String action) {
        switch (action) {
            case "view" -> {
                if (!shown.isEmpty()) show(shown.get(0), 1);
            }
            case "next" -> step(1);
            case "zoom" -> zoomAt(3, areaX + areaW * 0.4f, areaY + areaH * 0.4f);
            case "favourite" -> favourite();
            case "favourites" -> pick(entries.get(1));
            case "all" -> pick(entries.get(0));
            case "rename" -> startRename();
            case "delete" -> delete();
            case "undo" -> undo();
            case "grid" -> closeViewer();
            default -> {}
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void layout() {
        gridX = MARGIN + SIDE + 10;
        gridY = HEADER + 4;
        gridW = width - MARGIN - 5 - gridX;
        gridH = height - gridY - MARGIN;
        sideH = gridH;
        columns = Math.max(1, (int) ((gridW + GAP) / (TILE + GAP)));
        tileW = (gridW - GAP * (columns - 1)) / columns;
        tileH = tileW * 9 / 16;
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        if (seen != Shots.version()) refresh();
        float open = openness(), fade = fade();
        if (Mc.mc().level == null) Theme.scene(c, width, height);
        Theme.veil(c, width, height, fade, 0.5f);
        c.pushAlpha(fade);
        c.push();
        c.translate(0, (1 - open) * 12);

        float t = Math.clamp(view.update(), 0f, 1f);
        if (viewing != null && t < 0.01f && view.target() < 0.5f) viewing = null;
        boolean front = looking();
        float px = front ? -10000 : mx, py = front ? -10000 : my;
        if (t < 0.99f) {
            c.pushAlpha(1 - t * 0.6f);
            drawHeader(c, px, py);
            drawSide(c, px, py);
            drawGrid(c, px, py);
            c.popAlpha();
        }
        if (viewing != null) drawViewer(c, front ? mx : -10000, front ? my : -10000, t);
        drawUndo(c, mx, my);

        if (!front) {
            folder.drawTip(c, false);
            closeButton.drawTip(c, false);
            back.drawTip(c, true);
        }
        c.pop();
        c.popAlpha();
        Toasts.draw(c);
    }

    private void drawHeader(Canvas c, float mx, float my) {
        float cy = 7;
        back.label = "Back";
        back.bounds(MARGIN, cy, BUTTON, BUTTON);
        back.draw(c, mx, my);
        float tx = MARGIN + BUTTON + 8;
        float tw = c.text(Fonts.BOLD, "Screenshots", tx, cy + 4.2f, 12, Theme.TEXT);
        int total = Shots.all().size();
        String count = Shots.loading() ? "Reading the folder…" : shown.size() == total ? total + (total == 1 ? " screenshot" : " screenshots")
                : shown.size() + " of " + total;
        c.text(Fonts.REGULAR, count, tx + tw + 8, cy + 7.4f, 8.5f, Theme.TEXT_MUTED);

        closeButton.bounds(width - MARGIN - BUTTON, cy, BUTTON, BUTTON);
        closeButton.draw(c, mx, my);
        folder.bounds(closeButton.x - 4 - BUTTON, cy, BUTTON, BUTTON);
        folder.draw(c, mx, my);
        float sw = Math.clamp(width * 0.26f, 90f, 170f);
        search.bounds(folder.x - 6 - sw, cy, sw, BUTTON);
        search.draw(c, mx, my);
        if (search.text.isEmpty() && !search.focused) Icons.SEARCH.draw(c, search.x + sw - 11, cy + BUTTON / 2, 8.5f, Theme.TEXT_MUTED);
    }

    private void drawSide(Canvas c, float mx, float my) {
        float x = MARGIN, y = gridY;
        c.rect(x, y, SIDE, sideH, Theme.R_MD, 0xA60C0B13);
        c.gradientV(x, y, SIDE, sideH, Theme.R_MD, 0x0CFFFFFF, 0x00FFFFFF);
        c.stroke(x, y, SIDE, sideH, Theme.R_MD, 1, Theme.BORDER);
        float content = 6 + entries.size() * ROW + (entries.size() > 2 ? HEADING : 0) + 6;
        float off = sideScroll.update(content, sideH);
        boolean within = inside(mx, my, x, y, SIDE, sideH);
        c.clip(x, y + 1, SIDE, sideH - 2);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float ry = rowY(i) - off;
            if (i == 2) c.text(Fonts.SEMIBOLD, "PLACES", x + 9, ry - HEADING + 7, 6, Theme.TEXT_MUTED);
            if (ry + ROW < y || ry > y + sideH) continue;
            boolean on = picked(e), over = within && inside(mx, my, x + 4, ry, SIDE - 8, ROW - 1);
            float hv = rowHovers.computeIfAbsent(i + e.label, k -> Spring.snappy(0)).target(over ? 1 : 0).update();
            if (on) c.rect(x + 4, ry, SIDE - 8, ROW - 1, Theme.R_SM, Colors.withAlpha(Theme.accent(), 0.2f));
            else c.rect(x + 4, ry, SIDE - 8, ROW - 1, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.07f * hv));
            int ink = on ? Theme.TEXT : Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv);
            e.icon.draw(c, x + 13, ry + (ROW - 1) / 2, 8.5f, on ? Theme.accent() : ink);
            String number = Integer.toString(e.count);
            float nw = Fonts.REGULAR.width(number, 7);
            c.textMiddle(Fonts.REGULAR, number, x + SIDE - 10 - nw, ry, ROW - 1, 7, Theme.TEXT_MUTED);
            Fonts font = on ? Fonts.MEDIUM : Fonts.REGULAR;
            float room = SIDE - 22 - 14 - nw;
            c.textAny(font, font.truncateAny(e.label, 7.8f, room), x + 21, ry + (ROW - 1 - font.height(7.8f)) / 2, 7.8f, ink);
        }
        c.unclip();
        sideScroll.drawBar(c, x + SIDE - 4, y + 3, sideH - 6);
    }

    private float rowY(int index) {
        return gridY + 6 + index * ROW + (index >= 2 ? HEADING : 0);
    }

    private void drawGrid(Canvas c, float mx, float my) {
        arrange();
        float off = scroll.update(contentH, gridH);
        if (shown.isEmpty()) {
            String line = Shots.loading() ? "Reading the screenshots folder…" : Shots.all().isEmpty()
                    ? "No screenshots yet. Press " + Mc.keyName(Mc.boundCode(Mc.mc().options.keyScreenshot)) + " to take one."
                    : "Nothing here matches.";
            Icons.CAMERA.draw(c, gridX + gridW / 2, gridY + gridH / 2 - 14, 22, Theme.TEXT_MUTED);
            c.textCentered(Fonts.REGULAR, line, gridX + gridW / 2, gridY + gridH / 2 + 4, 8.5f, Theme.TEXT_DIM);
            return;
        }
        boolean within = inside(mx, my, gridX, gridY, gridW, gridH);
        c.clip(gridX - 3, gridY, gridW + 6, gridH);
        for (Heading h : headings) {
            float y = gridY + h.y - off;
            if (y + HEADING < gridY || y > gridY + gridH) continue;
            float tw = c.text(Fonts.SEMIBOLD, h.text, gridX + 1, y + 5, 7.4f, Theme.TEXT_DIM);
            c.rect(gridX + tw + 8, y + 9.5f, gridW - tw - 8, 0.5f, 0, 0x14FFFFFF);
        }
        for (Tile t : tiles) {
            float x = gridX + t.x, y = gridY + t.y - off;
            if (y + tileH < gridY || y > gridY + gridH) continue;
            drawTile(c, t.shot, x, y, within && inside(mx, my, x, y, tileW, tileH), mx, my);
        }
        c.unclip();
        scroll.drawBar(c, width - MARGIN - 3, gridY + 2, gridH - 4);
    }

    private void drawTile(Canvas c, Shot s, float x, float y, boolean over, float mx, float my) {
        float hv = hovers.computeIfAbsent(s, k -> Spring.snappy(0)).target(over ? 1 : 0).update();
        c.push();
        c.translate(0, -1.5f * hv);
        if (hv > 0.02f) c.shadow(x, y + 3, tileW, tileH, Theme.R_MD, 10, Colors.withAlpha(Colors.BLACK, 0.4f * hv));
        Tex tex = Shots.thumb(s);
        if (tex != null) {
            c.picture(tex, x, y, tileW, tileH, Theme.R_MD, true);
        } else if (Shots.failed(s)) {
            c.rect(x, y, tileW, tileH, Theme.R_MD, 0x14FFFFFF);
            Icons.ALERT.draw(c, x + tileW / 2, y + tileH / 2, 12, Theme.TEXT_MUTED);
        } else {
            float pulse = 0.05f + 0.035f * (float) Math.sin(Motion.time() * 4 + x * 0.03f);
            c.rect(x, y, tileW, tileH, Theme.R_MD, Colors.withAlpha(Colors.WHITE, pulse));
        }
        c.stroke(x, y, tileW, tileH, Theme.R_MD, 1 + 0.3f * hv, Colors.mix(Theme.BORDER, Theme.accent(), hv));
        if (hv > 0.02f) {
            c.pushAlpha(hv);
            c.gradientV(x, y + tileH - 20, tileW, 20, Theme.R_MD, 0x00000000, 0xCC000000);
            String time = clock(s.time);
            float tw = Fonts.MEDIUM.width(time, 6.6f);
            c.text(Fonts.MEDIUM, time, x + tileW - 6 - tw, y + tileH - 11, 6.6f, Theme.TEXT);
            c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny(s.name, 6.6f, tileW - 18 - tw), x + 6, y + tileH - 11, 6.6f, Theme.TEXT_DIM);
            c.popAlpha();
        }
        // The star: always there on a favourite, offered on the others while the pointer is on the tile.
        float sx = x + tileW - 10, sy = y + 10;
        boolean onStar = over && starHit(x, y, mx, my);
        if (s.favourite || hv > 0.02f) {
            c.pushAlpha(s.favourite ? 1 : hv);
            c.circle(sx, sy, 7, Colors.withAlpha(Colors.BLACK, onStar ? 0.75f : 0.5f));
            if (s.favourite) c.star(sx, sy + 0.3f, 4.6f, Theme.accent());
            else Icons.STAR.draw(c, sx, sy, 8, onStar ? Theme.TEXT : Theme.TEXT_DIM);
            c.popAlpha();
        }
        c.pop();
    }

    private boolean starHit(float tileX, float tileY, float mx, float my) {
        return inside(mx, my, tileX + tileW - 18, tileY + 2, 16, 16);
    }

    private void drawViewer(Canvas c, float mx, float my, float t) {
        Shot s = viewing;
        c.pushAlpha(t);
        c.rect(0, 0, width, height, 0, 0xF0050409);

        // The top row: back to the grid, the name, and what can be done with the picture.
        float cy = 7;
        back.label = "Back to the grid";
        back.bounds(MARGIN, cy, BUTTON, BUTTON);
        back.draw(c, mx, my);
        closeButton.bounds(width - MARGIN - BUTTON, cy, BUTTON, BUTTON);
        closeButton.draw(c, mx, my);
        float bx = closeButton.x - 10;
        for (int i = tools.length - 1; i >= 0; i--) {
            bx -= BUTTON;
            tools[i].bounds(bx, cy, BUTTON, BUTTON);
            bx -= 4;
        }
        star.active = s.favourite;
        star.label = s.favourite ? "Remove from favourites" : "Favourite";
        for (IconButton b : tools) b.draw(c, mx, my);
        float tx = MARGIN + BUTTON + 8, room = bx - tx - 4;
        if (renaming && !rename.focused) renaming = false;
        if (renaming) {
            rename.bounds(tx, cy, Math.min(room, 220), BUTTON);
            rename.draw(c, mx, my);
        } else {
            c.textAny(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncateAny(s.name, 10, room), tx, cy + 5.2f, 10, Theme.TEXT);
        }

        // The picture, as large as fits, then zoomed and moved from there.
        areaX = MARGIN + EDGE;
        areaY = HEADER + 2;
        areaW = width - areaX * 2;
        areaH = height - areaY - FOOTER - 4;
        Tex full = Shots.full(s), tex = full != null ? full : Shots.thumb(s);
        float pw = s.width > 0 ? s.width : tex != null ? tex.width : 16, ph = s.height > 0 ? s.height : tex != null ? tex.height : 9;
        float fit = Math.min(areaW / pw, areaH / ph);
        fitW = pw * fit;
        fitH = ph * fit;
        if (dragging) {
            if (!Mc.isDown(-2)) {
                dragging = false;
            } else {
                panToX += mx - lastX;
                panToY += my - lastY;
                lastX = mx;
                lastY = my;
                clampPan();
                panX.snap(panToX);
                panY.snap(panToY);
            }
        }
        float z = Math.max(0.2f, zoom.target(zoomTo).update());
        float w = fitW * z, h = fitH * z, sw = Math.clamp(swap.update(), 0f, 1f);
        float x = areaX + (areaW - w) / 2 + panX.target(panToX).update() + direction * 26 * (1 - sw);
        float y = areaY + (areaH - h) / 2 + panY.target(panToY).update() + (1 - t) * 10;
        c.clip(areaX, areaY, areaW, areaH);
        c.pushAlpha(sw);
        if (tex != null) {
            c.picture(tex, x, y, w, h, z < 1.02f ? Theme.R_MD : 0, false);
        } else if (Shots.failed(s)) {
            Icons.ALERT.draw(c, areaX + areaW / 2, areaY + areaH / 2 - 10, 18, Theme.TEXT_MUTED);
            c.textCentered(Fonts.REGULAR, "This picture could not be read.", areaX + areaW / 2, areaY + areaH / 2 + 6, 8.5f, Theme.TEXT_DIM);
        } else {
            float pulse = 0.05f + 0.035f * (float) Math.sin(Motion.time() * 4);
            c.rect(x, y, w, h, Theme.R_MD, Colors.withAlpha(Colors.WHITE, pulse));
        }
        c.popAlpha();
        c.unclip();

        int at = shown.indexOf(s);
        previous.enabled = at > 0;
        following.enabled = at >= 0 && at < shown.size() - 1;
        float my0 = areaY + areaH / 2 - BUTTON / 2;
        previous.bounds(MARGIN, my0, BUTTON, BUTTON);
        following.bounds(width - MARGIN - BUTTON, my0, BUTTON, BUTTON);
        previous.draw(c, mx, my);
        following.draw(c, mx, my);

        // When, how large, how heavy, where.
        ZonedDateTime taken = Instant.ofEpochMilli(s.time).atZone(ZoneId.systemDefault());
        StringBuilder details = new StringBuilder().append(taken.getDayOfMonth()).append(' ')
                .append(taken.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)).append(' ').append(taken.getYear()).append(", ").append(clock(s.time));
        if (s.width > 0) details.append("  •  ").append(s.width).append(" × ").append(s.height);
        details.append("  •  ").append(size(s.size));
        if (!s.place.isEmpty()) details.append("  •  ").append(s.place);
        float fy = height - FOOTER - 1;
        String where = at < 0 ? "" : (at + 1) + " of " + shown.size();
        float ww = Fonts.MEDIUM.width(where, 7.6f);
        String line = Fonts.REGULAR.truncateAny(details.toString(), 7.8f, width - MARGIN * 2 - ww * 2 - 60);
        c.textAny(Fonts.REGULAR, line, (width - Fonts.REGULAR.widthAny(line, 7.8f)) / 2, fy + (FOOTER - Fonts.REGULAR.height(7.8f)) / 2, 7.8f, Theme.TEXT_DIM);
        c.textMiddle(Fonts.MEDIUM, where, width - MARGIN - ww, fy, FOOTER, 7.6f, Theme.TEXT_MUTED);
        if (z > 1.02f) {
            String level = Math.round(z / trueSize() * 100) + "%";
            c.textMiddle(Fonts.MEDIUM, level, MARGIN, fy, FOOTER, 7.6f, Theme.TEXT_MUTED);
        }

        for (IconButton b : tools) b.drawTip(c, false);
        closeButton.drawTip(c, false);
        back.drawTip(c, true);
        previous.drawTip(c, true);
        following.drawTip(c, false);
        c.popAlpha();
    }

    /** While a deleted screenshot can be taken back: what went, and the way to undo it. */
    private void drawUndo(Canvas c, float mx, float my) {
        Shots.Deleted d = Shots.lastDeleted();
        float a = Math.clamp(undoIn.target(d != null ? 1 : 0).update(), 0f, 1f);
        if (d == null || a < 0.01f) {
            undoW = 0;
            return;
        }
        String label = "Deleted " + Fonts.REGULAR.truncateAny(d.shot().name, 7.8f, 150), action = "Undo";
        float lw = Fonts.REGULAR.widthAny(label, 7.8f), aw = Fonts.SEMIBOLD.width(action, 7.8f) + 14, h = 22;
        undoW = 12 + lw + 10 + aw + 4;
        undoX = (width - undoW) / 2;
        // Clear of the line under the picture.
        undoY = height - (looking() ? FOOTER + 8 : MARGIN + 6) - h + (1 - a) * 10;
        boolean over = inside(mx, my, undoX, undoY, undoW, h);
        c.pushAlpha(a);
        c.shadow(undoX, undoY + 4, undoW, h, h / 2, 14, 0x80000000);
        c.rect(undoX, undoY, undoW, h, h / 2, 0xF2171422);
        c.stroke(undoX, undoY, undoW, h, h / 2, 1, Theme.BORDER_STRONG);
        c.textAny(Fonts.REGULAR, label, undoX + 12, undoY + (h - Fonts.REGULAR.height(7.8f)) / 2, 7.8f, Theme.TEXT_DIM);
        float ax = undoX + undoW - aw - 4;
        c.rect(ax, undoY + 4, aw, h - 8, (h - 8) / 2, Colors.withAlpha(Theme.accent(), over ? 0.45f : 0.25f));
        c.textMiddle(Fonts.SEMIBOLD, action, ax + 7, undoY + 4, h - 8, 7.8f, Theme.TEXT);
        c.rect(undoX + 11, undoY + h - 2.5f, (undoW - 22) * d.left(), 1, 0.5f, Colors.withAlpha(Theme.accent(), 0.6f));
        c.popAlpha();
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        if (button == 0 && undoW > 0 && inside(x, y, undoX, undoY, undoW, 22)) {
            Sounds.click();
            undo();
            return true;
        }
        // The mouse's own back button.
        if (button == 3) {
            leave();
            return true;
        }
        if (looking()) return viewerDown(x, y, button);
        search.mouseDown(x, y, button);
        if (button != 0) return true;
        if (back.mouseDown(x, y, button) || closeButton.mouseDown(x, y, button) || folder.mouseDown(x, y, button)) return true;
        if (inside(x, y, MARGIN, gridY, SIDE, sideH)) {
            float off = sideScroll.get();
            for (int i = 0; i < entries.size(); i++) {
                if (inside(x, y, MARGIN + 4, rowY(i) - off, SIDE - 8, ROW - 1)) {
                    if (!picked(entries.get(i))) Sounds.click();
                    pick(entries.get(i));
                    return true;
                }
            }
            return true;
        }
        if (inside(x, y, gridX, gridY, gridW, gridH)) {
            float off = scroll.get();
            for (Tile t : tiles) {
                float tx = gridX + t.x, ty = gridY + t.y - off;
                if (!inside(x, y, tx, ty, tileW, tileH)) continue;
                Sounds.click();
                if (starHit(tx, ty, x, y)) Shots.favourite(t.shot, !t.shot.favourite);
                else show(t.shot, 1);
                return true;
            }
        }
        return true;
    }

    private boolean viewerDown(float x, float y, int button) {
        if (renaming) rename.mouseDown(x, y, button);
        if (button != 0) return true;
        if (back.mouseDown(x, y, button) || closeButton.mouseDown(x, y, button) || previous.mouseDown(x, y, button) || following.mouseDown(x, y, button)) return true;
        for (IconButton b : tools) if (b.mouseDown(x, y, button)) return true;
        if (!inside(x, y, areaX, areaY, areaW, areaH)) return true;
        long now = System.nanoTime();
        if (now - lastClick < 300_000_000L) {
            // A double click: in to the picture's true size, or back out.
            lastClick = 0;
            if (zoomTo > 1.02f) resetZoom(false);
            else zoomAt(Math.max(2, trueSize()), x, y);
            return true;
        }
        lastClick = now;
        dragging = true;
        lastX = x;
        lastY = y;
        return true;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        dragging = false;
        back.mouseUp(x, y, button);
        closeButton.mouseUp(x, y, button);
        if (looking()) {
            previous.mouseUp(x, y, button);
            following.mouseUp(x, y, button);
            for (IconButton b : tools) b.mouseUp(x, y, button);
        } else {
            folder.mouseUp(x, y, button);
        }
        return true;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        if (looking()) zoomAt(zoomTo * (float) Math.pow(1.25, amount), x, y);
        else if (inside(x, y, MARGIN, gridY, SIDE, sideH)) sideScroll.scroll(amount);
        else scroll.scroll(amount * 1.5f);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (isClosing()) return false;
        skipChar = false;
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0, enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (renaming) {
            if (key == GLFW.GLFW_KEY_ESCAPE) renaming = rename.focused = false;
            else if (enter) finishRename();
            else rename.keyDown(key, mods);
            return true;
        }
        if (search.focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || enter) search.focused = false;
            else search.keyDown(key, mods);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (!looking()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) close();
            else if (ctrl && key == GLFW.GLFW_KEY_F) search.focused = true;
            else if (enter && !shown.isEmpty()) show(shown.get(0), 1);
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> closeViewer();
            case GLFW.GLFW_KEY_LEFT -> step(-1);
            case GLFW.GLFW_KEY_RIGHT -> step(1);
            case GLFW.GLFW_KEY_HOME -> step(-shown.size());
            case GLFW.GLFW_KEY_END -> step(shown.size());
            case GLFW.GLFW_KEY_DELETE -> delete();
            case GLFW.GLFW_KEY_F -> favourite();
            case GLFW.GLFW_KEY_R -> {
                startRename();
                // The letter itself arrives next, as typing.
                skipChar = true;
            }
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_0 -> resetZoom(false);
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) copy();
            }
            default -> {}
        }
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (isClosing()) return false;
        if (skipChar) {
            skipChar = false;
            return true;
        }
        if (renaming) return rename.charTyped(codepoint);
        return search.charTyped(codepoint);
    }
}
