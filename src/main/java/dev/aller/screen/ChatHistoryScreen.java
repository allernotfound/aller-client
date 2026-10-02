package dev.aller.screen;

import dev.aller.feature.ChatArchive;
import dev.aller.feature.ChatArchive.Entry;
import dev.aller.feature.ChatLog;
import dev.aller.feature.ChatText;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Sounds;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;

/**
 * Search through everything ever said in chat: Aller's own chat logs and, before those, the chat
 * lines in the game's logs. It opens at the newest line like the chat itself. Only a window of
 * the history is held at a time: scrolling towards either end reads the next block of matching
 * lines from disk on a worker thread and lets go of the far end.
 */
public final class ChatHistoryScreen extends AllerScreen {
    private static final float HEADER = 36, BAR = 24, FOOTER = 22, GUTTER = 42, LINE = 10, HEADING = 20;
    /** Lines read at a time, and how many are kept before the far end is dropped. */
    private static final int BLOCK = 300, KEEP = 1500;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK);

    private static final class Row {
        final Entry entry;
        final Component text;
        List<FormattedCharSequence> lines;
        String heading;
        float y, h;

        Row(Entry entry) {
            this.entry = entry;
            this.text = ChatText.decode(entry.coded());
        }
    }

    private record Block(int source, int index, List<Row> rows) {}

    private record Loaded(int source, int index, List<Entry> entries) {}

    /** One search over one set of files. Everything but the constructor runs on the worker thread. */
    private static final class Search {
        final List<ChatArchive.Source> sources;
        final Predicate<String> match;
        /** The matching lines of the files read last, so stepping through one file reads it once. */
        final LinkedHashMap<Integer, List<Entry>> cache = new LinkedHashMap<>(4, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, List<ChatArchive.Entry>> eldest) {
                return size() > 3;
            }
        };

        Search(List<ChatArchive.Source> sources, Predicate<String> match) {
            this.sources = sources;
            this.match = match;
        }

        List<Entry> entries(int source) {
            return cache.computeIfAbsent(source, s -> ChatArchive.read(sources.get(s), match));
        }

        /** The block before one; start from {@code (sources.size(), 0)} for the newest. Null at the start of history. */
        Loaded before(int source, int index) {
            if (index > 0) return block(source, index - 1);
            for (int s = source - 1; s >= 0; s--) {
                List<Entry> e = entries(s);
                if (!e.isEmpty()) return block(s, (e.size() - 1) / BLOCK);
            }
            return null;
        }

        Loaded after(int source, int index) {
            if ((index + 1) * BLOCK < entries(source).size()) return block(source, index + 1);
            for (int s = source + 1; s < sources.size(); s++) {
                if (!entries(s).isEmpty()) return block(s, 0);
            }
            return null;
        }

        private Loaded block(int source, int index) {
            List<Entry> e = entries(source);
            return new Loaded(source, index, new ArrayList<>(e.subList(index * BLOCK, Math.min(e.size(), (index + 1) * BLOCK))));
        }
    }

    private final Screen parent;
    private final String server = Game.inWorld() ? Game.worldKey() : null;
    private final TextField search = new TextField("Search chat history");
    private final Scroll scroll = new Scroll();
    private final ArrayDeque<Block> blocks = new ArrayDeque<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Aller Client chat history");
        t.setDaemon(true);
        return t;
    });
    private final Queue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final Spring[] chipHover = {Spring.snappy(0), Spring.snappy(0), Spring.snappy(0), Spring.snappy(0)};
    private final float[] chipX = new float[5];
    private Search current;
    private int generation;
    private boolean regex, matchCase, everything;
    private boolean loading, topDone, bottomDone = true, stick = true;
    private String error;
    private float pendingAt = -1;
    private float px, py, pw, ph, listY, listH, contentH, wrapWidth;
    private int rowCount;

    public ChatHistoryScreen(Screen parent) {
        this.parent = parent;
        everything = server == null;
        search.bare = true;
        search.focused = true;
        search.textSize = 10f;
        search.maxLength = 128;
        search.onChange = text -> pendingAt = Motion.time();
    }

    @Override
    public AllerScreen underlay() {
        return parent instanceof ScreenHost host ? host.screen : null;
    }

    @Override
    public void opened() {
        super.opened();
        restart();
    }

    @Override
    public void closed() {
        generation++;
        worker.shutdownNow();
    }

    @Override
    public void close() {
        close(() -> Mc.setScreen(parent));
    }

    @Override
    public boolean closeOnEscape() {
        return false; // Escape clears the query first
    }

    // ---- loading -------------------------------------------------------------------------------

    private void restart() {
        int gen = ++generation;
        pendingAt = -1;
        blocks.clear();
        current = null;
        error = null;
        topDone = false;
        bottomDone = true;
        stick = true;
        Predicate<String> match;
        try {
            match = ChatArchive.matcher(search.text, regex, matchCase);
        } catch (PatternSyntaxException e) {
            error = "Not a valid regular expression";
            loading = false;
            topDone = true;
            return;
        }
        loading = true;
        String scope = everything ? null : server;
        worker.execute(() -> {
            Search s = new Search(ChatArchive.sources(scope), match);
            Loaded newest = s.before(s.sources.size(), 0);
            inbox.add(() -> {
                if (gen != generation) return;
                current = s;
                loading = false;
                if (newest == null) topDone = true;
                else add(newest, true);
            });
        });
    }

    private void older() {
        if (loading || topDone || current == null || blocks.isEmpty()) return;
        loading = true;
        int gen = generation, source = blocks.peekFirst().source, index = blocks.peekFirst().index;
        Search s = current;
        worker.execute(() -> {
            Loaded found = s.before(source, index);
            inbox.add(() -> {
                if (gen != generation) return;
                loading = false;
                if (found == null) topDone = true;
                else add(found, true);
            });
        });
    }

    private void newer() {
        if (loading || bottomDone || current == null || blocks.isEmpty()) return;
        loading = true;
        int gen = generation, source = blocks.peekLast().source, index = blocks.peekLast().index;
        Search s = current;
        worker.execute(() -> {
            Loaded found = s.after(source, index);
            inbox.add(() -> {
                if (gen != generation) return;
                loading = false;
                if (found == null) bottomDone = true;
                else add(found, false);
            });
        });
    }

    /** Takes a block in at one end and, past {@link #KEEP} lines, drops blocks from the other; what is on screen stays put. */
    private void add(Loaded loaded, boolean top) {
        List<Row> rows = new ArrayList<>(loaded.entries.size());
        for (Entry e : loaded.entries) rows.add(new Row(e));
        Block block = new Block(loaded.source, loaded.index, rows);
        float before = contentH;
        if (top) blocks.addFirst(block);
        else blocks.addLast(block);
        arrange();
        if (top) scroll.shift(contentH - before);
        while (rowCount > KEEP && blocks.size() > 2) {
            before = contentH;
            if (top) {
                blocks.removeLast();
                bottomDone = false;
                stick = false;
                arrange();
            } else {
                blocks.removeFirst();
                topDone = false;
                arrange();
                scroll.shift(contentH - before);
            }
        }
    }

    /** Wraps and positions every loaded row; a row that starts a new day or server gets a heading. */
    private void arrange() {
        var font = Mc.mc().font;
        int width = Math.max(40, (int) (pw - GUTTER - 16));
        boolean rewrap = width != wrapWidth;
        wrapWidth = width;
        float y = 4;
        int count = 0;
        Entry prev = null;
        for (Block b : blocks) {
            for (Row r : b.rows) {
                Entry e = r.entry;
                boolean heading = prev == null || !prev.date().equals(e.date()) || !prev.server().equals(e.server());
                r.heading = heading ? heading(e) : null;
                if (r.lines == null || rewrap) r.lines = font.split(r.text, width);
                r.y = y;
                r.h = (heading ? HEADING : 0) + Math.max(1, r.lines.size()) * LINE + 2;
                y += r.h;
                prev = e;
                count++;
            }
        }
        rowCount = count;
        contentH = y + 4;
    }

    private static String heading(Entry e) {
        LocalDate today = LocalDate.now();
        String day = e.date().equals(today) ? "Today" : e.date().equals(today.minusDays(1)) ? "Yesterday" : e.date().format(DAY);
        return e.server().isEmpty() ? day + "  ·  game log" : day + "  ·  " + place(e.server());
    }

    /** "mp_play.example.net" -> "play.example.net". */
    private static String place(String key) {
        return key.startsWith("mp_") || key.startsWith("sp_") ? key.substring(3) : key;
    }

    // ---- drawing -------------------------------------------------------------------------------

    @Override
    protected void layout() {
        pw = Math.min(520, width - 24);
        ph = Math.min(360, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        arrange();
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        for (Runnable r; (r = inbox.poll()) != null; ) r.run();
        if (pendingAt >= 0 && Motion.time() - pendingAt > 0.22f) restart();

        float open = openness(), fade = fade();
        if (Mc.mc().level == null && underlay() == null) Theme.scene(c, width, height);
        Theme.veil(c, width, height, fade, 0.42f);
        c.pushAlpha(fade);
        c.push();
        c.scale(0.94f + 0.06f * open, width / 2, height / 2);
        c.translate(0, (1 - open) * 10);
        Theme.panel(c, px, py, pw, ph, Theme.R_LG);

        // Search bar.
        float cx = px + 17, cy = py + HEADER / 2;
        int icon = error != null ? Theme.DANGER : Colors.mix(Theme.TEXT_MUTED, Theme.accent(), search.text.isEmpty() ? 0 : 1);
        Icons.SEARCH.draw(c, cx + 0.5f, cy + 0.5f, 11f, icon);
        search.bounds(px + 31, py, pw - 31 - 44, HEADER);
        search.draw(c, 0, 0);
        float kw = Fonts.SEMIBOLD.width("esc", 12 * 0.56f) + 12 * 0.6f;
        Theme.keycap(c, "esc", px + pw - 12 - kw, py + (HEADER - 12) / 2, 12);
        c.rect(px + 1, py + HEADER - 0.5f, pw - 2, 0.5f, 0, Theme.BORDER);

        drawBar(c, mx, my);
        listY = py + HEADER + BAR;
        listH = ph - HEADER - BAR - FOOTER;
        drawList(c, mx, my);

        c.rect(px + 1, py + ph - FOOTER, pw - 2, 0.5f, 0, Theme.BORDER);
        drawFooter(c);
        c.pop();
        c.popAlpha();
        Toasts.draw(c);
    }

    private String[] chips() {
        String here = server == null ? null : server.startsWith("sp_") ? "This world" : "This server";
        return new String[] {here, "Everything", "Regex", "Match case"};
    }

    private boolean chipOn(int i) {
        return switch (i) {
            case 0 -> !everything;
            case 1 -> everything;
            case 2 -> regex;
            default -> matchCase;
        };
    }

    private void drawBar(Canvas c, float mx, float my) {
        String[] labels = chips();
        float y = py + HEADER + 5, h = 15;
        float x = px + 10;
        for (int i = 0; i < labels.length; i++) {
            chipX[i] = x;
            if (labels[i] == null) continue;
            // The two switches sit at the right end, away from the scope.
            if (i == 2) {
                x = px + pw - 10 - (Fonts.MEDIUM.width(labels[2], 7.5f) + 16) - 4 - (Fonts.MEDIUM.width(labels[3], 7.5f) + 16);
                chipX[i] = x;
            }
            float w = Fonts.MEDIUM.width(labels[i], 7.5f) + 16;
            boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
            float hv = chipHover[i].target(over ? 1 : 0).update();
            if (chipOn(i)) Theme.accentFill(c, x, y, w, h, h / 2);
            else c.rect(x, y, w, h, h / 2, Colors.withAlpha(Colors.WHITE, 0.05f + 0.06f * hv));
            c.textMiddle(Fonts.MEDIUM, labels[i], x + 8, y, h, 7.5f, chipOn(i) ? Theme.onAccent() : Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
            x += w + 4;
        }
        chipX[4] = x;
    }

    private void drawList(Canvas c, float mx, float my) {
        if (stick) scroll.toEnd();
        float off = scroll.update(contentH, listH);
        if (off < 240) older();
        if (contentH - off - listH < 240) newer();
        if (scroll.atEnd() && bottomDone) stick = true;

        c.clip(px, listY, pw, listH);
        boolean inList = mx >= px && mx < px + pw && my >= listY && my < listY + listH;
        for (Block b : blocks) {
            for (Row r : b.rows) {
                float y = listY + r.y - off;
                if (y + r.h < listY || y > listY + listH) continue;
                if (r.heading != null) {
                    float tw = Fonts.SEMIBOLD.width(r.heading, 7f);
                    c.text(Fonts.SEMIBOLD, r.heading, px + 12, y + 7, 7f, Theme.TEXT_DIM);
                    c.rect(px + 18 + tw, y + 11, pw - 30 - tw, 0.5f, 0, 0x14FFFFFF);
                    y += HEADING;
                }
                float h = r.h - (r.heading != null ? HEADING : 0);
                if (inList && my >= y && my < y + h) c.rect(px + 6, y, pw - 12, h, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.06f));
                Entry e = r.entry;
                boolean pinged = e.has(ChatLog.MENTION) || e.has(ChatLog.PRIVATE);
                if (pinged) {
                    c.rect(px + 6, y, pw - 12, h, Theme.R_SM, Colors.withAlpha(Theme.accent(), 0.10f));
                    c.rect(px + 6, y + 2, 1.5f, h - 4, 0.75f, Theme.accent());
                }
                String time = e.time().length() >= 5 ? e.time().substring(0, 5) : e.time();
                c.text(Fonts.REGULAR, time, px + 12, y + 2, 6.8f, Theme.TEXT_MUTED);
                c.pushAlpha(e.has(ChatLog.HIDDEN) ? 0.45f : 1f);
                for (int i = 0; i < r.lines.size(); i++) c.vanillaText(r.lines.get(i), px + GUTTER, y + 1 + i * LINE, Colors.WHITE);
                c.popAlpha();
            }
        }
        if (blocks.isEmpty() && !loading) {
            String title = error != null ? error : search.text.isEmpty() ? "No chat history yet" : "Nothing matches";
            String hint = error != null ? "Check the expression, or switch Regex off to search for the text itself."
                    : !everything ? "Nothing from here. Older game logs are under Everything."
                    : search.text.isEmpty() ? "Lines appear here as they are said, with the Chat log mod on." : "Try fewer words, or another spelling.";
            c.textCentered(Fonts.SEMIBOLD, title, px + pw / 2, listY + listH / 2 - 14, 10f, error != null ? Theme.DANGER : Theme.TEXT);
            c.textCentered(Fonts.REGULAR, hint, px + pw / 2, listY + listH / 2 + 2, 8f, Theme.TEXT_MUTED);
        }
        c.unclip();
        scroll.drawBar(c, px + pw - 5, listY + 3, listH - 6);
    }

    private void drawFooter(Canvas c) {
        float fy = py + ph - FOOTER, kh = 11, ky = fy + (FOOTER - kh) / 2 + 0.5f;
        String status = loading ? (blocks.isEmpty() ? "Searching…" : "Reading older lines…")
                : rowCount == 0 ? ""
                : rowCount + (rowCount == 1 ? " line" : " lines") + (topDone && bottomDone ? "" : " in view of more");
        float statusW = Fonts.REGULAR.width(status, 7.2f);
        c.textRight(Fonts.REGULAR, status, px + pw - 12, fy + (FOOTER - Fonts.REGULAR.height(7.2f)) / 2 + 0.5f, 7.2f, Theme.TEXT_MUTED);
        String[][] hints = {{"click", "copy"}, {"tab", "scope"}, {"ctrl R", "regex"}, {"ctrl M", "match case"}};
        float x = px + 10, limit = px + pw - 24 - statusW;
        for (String[] hint : hints) {
            if (hint[0].equals("tab") && server == null) continue;
            float kw = Math.max(kh, Fonts.SEMIBOLD.width(hint[0], kh * 0.56f) + kh * 0.6f);
            float lw = Fonts.REGULAR.width(hint[1], 7.2f);
            if (x + kw + 4 + lw > limit) break;
            Theme.keycap(c, hint[0], x, ky, kh);
            c.textMiddle(Fonts.REGULAR, hint[1], x + kw + 4, fy + 0.5f, FOOTER, 7.2f, Theme.TEXT_MUTED);
            x += kw + lw + 14;
        }
    }

    // ---- input ---------------------------------------------------------------------------------

    private void toggle(int chip) {
        switch (chip) {
            case 0 -> everything = false;
            case 1 -> everything = true;
            case 2 -> regex = !regex;
            default -> matchCase = !matchCase;
        }
        Sounds.click();
        restart();
    }

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        if (x < px || x >= px + pw || y < py || y >= py + ph) {
            close();
            return true;
        }
        if (button != 0) return true;
        if (y >= py + HEADER && y < listY) {
            String[] labels = chips();
            for (int i = 0; i < labels.length; i++) {
                if (labels[i] == null) continue;
                float w = Fonts.MEDIUM.width(labels[i], 7.5f) + 16;
                // A scope that is already chosen stays chosen; the two switches flip.
                if (x >= chipX[i] && x < chipX[i] + w && (i >= 2 || !chipOn(i))) toggle(i);
            }
            return true;
        }
        if (y < listY || y >= listY + listH) return true;
        float off = scroll.get();
        for (Block b : blocks) {
            for (Row r : b.rows) {
                float top = listY + r.y - off + (r.heading != null ? HEADING : 0);
                if (y < top || y >= listY + r.y - off + r.h) continue;
                String text = ChatText.strip(r.entry.coded());
                Mc.setClipboard(text);
                Sounds.click();
                Toasts.info("Copied", text);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        if (amount > 0) stick = false;
        scroll.scroll(amount * 1.5f);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (isClosing()) return false;
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (search.text.isEmpty()) {
                    close();
                } else {
                    search.setText("");
                    restart();
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (server != null) toggle(everything ? 0 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                restart();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> {
                stick = false;
                scroll.scroll(key == GLFW.GLFW_KEY_UP ? 1 : listH / 32f);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll.scroll(key == GLFW.GLFW_KEY_DOWN ? -1 : -listH / 32f);
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                if (ctrl) {
                    toggle(2);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_M -> {
                if (ctrl) {
                    toggle(3);
                    return true;
                }
            }
            default -> {}
        }
        search.focused = true;
        search.keyDown(key, mods);
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (isClosing()) return false;
        search.focused = true;
        return search.charTyped(codepoint);
    }
}
