package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.feature.Browser;
import dev.aller.feature.Browser.Mark;
import dev.aller.feature.Browser.Tab;
import dev.aller.feature.Browser.Visit;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Picture;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Sounds;
import dev.aller.platform.WebNative;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.IconButton;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The browser window: a tab strip, a toolbar and the page.
 *
 * <p>A web page is not drawn by Aller. It is a real window of its own laid over the panel, which cannot fade,
 * move or sit beneath anything, so it is only shown while the panel is still and nothing overlaps
 * it. The rest of the time its place is taken by a spinner (opening) or by a still picture of the
 * page (closing, a menu dropping over it, the launcher), which Aller can animate like anything else.
 */
public final class BrowserScreen extends AllerScreen {
    private static final float MARGIN = 10, STRIP = 26, BAR = 26, PAD = 5, ROW = 18;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm"), DAY = DateTimeFormatter.ofPattern("d MMM");
    /** Longest the window waits for a fresh still of the page before closing anyway, in seconds. */
    private static final float STILL_WAIT = 0.25f;

    private record Suggestion(String title, String detail, String target) {}

    private final Screen parent;
    private final TextField address = new TextField("Search or type an address");
    private final TextField search = new TextField("");
    private final TextField filter = new TextField("Search history");
    private final IconButton back = new IconButton(Icons.BACK, "Back", () -> Browser.back(tab()));
    private final IconButton forward = new IconButton(Icons.FORWARD, "Forward", () -> Browser.forward(tab()));
    private final IconButton reload = new IconButton(Icons.RELOAD, "Reload", () -> Browser.reload(tab()));
    private final IconButton stop = new IconButton(Icons.CLOSE, "Stop", () -> Browser.reload(tab()));
    private final IconButton star = new IconButton(Icons.STAR, "Bookmark this page", () -> Browser.toggleBookmark(tab()));
    private final IconButton historyButton = new IconButton(Icons.CLOCK, "History", this::openHistory);
    private final IconButton secretButton = new IconButton(Icons.PRIVATE, "Private tabs", this::togglePrivate);
    private final IconButton plus = new IconButton(Icons.PLUS, "New tab", this::newTab);
    private final IconButton closeButton = new IconButton(Icons.CLOSE, "Close", this::close);
    private final Button retry = new Button("Reload", () -> Browser.reload(tab())).style(Button.Style.PRIMARY);
    private final Button clearHistory = new Button("Clear history", this::clearHistory).style(Button.Style.GHOST);
    private final Button clearData = new Button("Clear browsing data", this::clearData).style(Button.Style.DANGER);
    private final Scroll scroll = new Scroll();
    private final Map<Object, Spring> hovers = new IdentityHashMap<>();
    private final Spring tint = Spring.smooth(0), loadBar = Spring.smooth(0), drop = Spring.snappy(0);
    private final List<Suggestion> suggestions = new ArrayList<>();
    private int picked;
    private boolean clearArmed, dataArmed;

    private float px, py, pw, ph, pageX, pageY, pageW, pageH, tabW, stripX;
    private float lastMx, lastMy, dropMx, dropMy;

    /** The tab whose real page is on screen now, or null while a spinner or a still stands in for it. */
    private Tab live;
    private int[] placed;
    /** Frames the open animation has been at rest. */
    private int calm;
    private boolean leaving, creating, focusPage = true;
    private Runnable afterLeaving;
    private float leaveAt, stillAsked = -10;
    private Tab shownFor;

    public BrowserScreen(Screen parent) {
        this.parent = parent;
        address.maxLength = search.maxLength = 2048;
        address.textSize = 8f;
        search.textSize = 9.5f;
        filter.textSize = 8f;
        address.onChange = text -> suggest();
        retry.textSize = 8.5f;
        clearHistory.textSize = clearData.textSize = 7.5f;
    }

    private static Tab tab() {
        return Browser.active();
    }

    @Override
    public AllerScreen underlay() {
        return parent instanceof ScreenHost host ? host.screen : null;
    }

    @Override
    public Screen vanillaUnderlay() {
        return parent instanceof ScreenHost ? null : parent;
    }

    @Override
    public boolean pausesGame() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    public boolean closeOnEscape() {
        return false;
    }

    @Override
    public boolean capturing() {
        return address.focused || search.focused || filter.focused;
    }

    @Override
    public void opened() {
        super.opened();
        // Opening shows a spinner until the panel has landed, not what the page looked like last time.
        Browser.dropStill(tab());
        Browser.windowShown(true);
        focusPage = true;
        if (!tab().page()) search.focused = true;
    }

    @Override
    public void reshown() {
        Browser.windowShown(true);
    }

    @Override
    public void closed() {
        hide();
        Browser.windowShown(false);
    }

    @Override
    public void close() {
        close(() -> Mc.setScreen(parent));
    }

    /** Closing waits a moment for a picture of the page, so there is something to animate away. */
    @Override
    public void close(Runnable then) {
        if (leaving || isClosing()) return;
        if (live == null) {
            super.close(then);
            return;
        }
        leaving = true;
        afterLeaving = then;
        leaveAt = Browser.now();
        if (live.stillAt < leaveAt - 0.1f) requestStill(true);
    }

    // ---- the real page ---------------------------------------------------------------------------

    private void requestStill(boolean now) {
        float time = Browser.now();
        if (live == null || !now && time - stillAsked < 1f) return;
        stillAsked = time;
        WebNative.action(live.view, WebNative.CAPTURE);
    }

    private void hide() {
        if (live == null) return;
        if (live.view >= 0) {
            // Hand the keyboard back first: a hidden window that keeps it would swallow every key.
            WebNative.action(live.view, WebNative.FOCUS_GAME);
            WebNative.show(live.view, false);
        }
        live = null;
        placed = null;
    }

    private boolean covered() {
        return !suggestions.isEmpty() && address.focused || Toasts.bottom() > pageY;
    }

    /** Puts the page's window where the panel's page area is, or takes it away. Runs once a frame while this is the screen in front. */
    private void sync() {
        Tab tab = tab();
        if (tab != shownFor) {
            // Another tab: the old page leaves at once, and its picture is not needed any more.
            if (shownFor != null && shownFor != live) Browser.dropStill(shownFor);
            if (live != null && live != tab) {
                Tab old = live;
                hide();
                Browser.dropStill(old);
            }
            shownFor = tab;
            clearArmed = dataArmed = false;
            scroll.reset();
        }
        calm = Math.abs(openness() - 1f) < 0.003f ? calm + 1 : 0;
        boolean landed = calm > 2 && !isClosing();
        var window = Mc.mc().getWindow();
        float unit = (float) window.getGuiScale() * scale();
        // The page's window is placed on the screen, so it has to follow the game window when that moves too.
        int[] wx = new int[1], wy = new int[1];
        GLFW.glfwGetWindowPos(Mc.window(), wx, wy);
        int[] rect = tab.fullscreen
                ? new int[] {0, 0, window.getScreenWidth(), window.getScreenHeight(), wx[0], wy[0]}
                : new int[] {Math.round(pageX * unit), Math.round(pageY * unit), Math.round(pageW * unit), Math.round(pageH * unit), wx[0], wy[0]};

        boolean web = tab.page() && !tab.crashed;
        if (web && tab.view < 0 && landed && !leaving && !creating && WebNative.state() == WebNative.READY) {
            // Starting a page stalls for a moment while the browser answers; do it between frames.
            creating = true;
            AllerClient.defer(() -> {
                creating = false;
                Browser.realise(tab, rect[2], rect[3]);
            });
        }

        if (leaving) {
            if (live == null || live.stillAt >= leaveAt - 0.1f || Browser.now() - leaveAt > STILL_WAIT) {
                hide();
                leaving = false;
                super.close(afterLeaving);
            }
            return;
        }
        // A minimised window has no room for the page; it is put back when the game returns.
        boolean minimised = GLFW.glfwGetWindowAttrib(Mc.window(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE || rect[2] < 8 || rect[3] < 8;
        if (web && tab.view >= 0 && landed && !covered() && !minimised) {
            Browser.seen(tab);
            if (live != tab || !java.util.Arrays.equals(rect, placed)) {
                WebNative.place(tab.view, rect[0], rect[1], rect[2], rect[3]);
                if (live != tab) WebNative.show(tab.view, true);
                live = tab;
                placed = rect;
            }
            if (focusPage) {
                WebNative.action(tab.view, WebNative.FOCUS);
                focusPage = false;
            }
        } else {
            hide();
        }
    }

    // ---- actions ---------------------------------------------------------------------------------

    private void newTab() {
        Browser.newTab();
        unfocus();
        search.setText("");
        search.focused = true;
    }

    private void closeTab(Tab tab) {
        if (live == tab) hide();
        if (Browser.close(tab)) close();
    }

    private void togglePrivate() {
        hide();
        Browser.setIncognito(!Browser.incognito());
        unfocus();
        if (!tab().page()) search.focused = true;
    }

    private void openHistory() {
        if (Browser.incognito()) Browser.setIncognito(false);
        for (Tab tab : Browser.tabs()) {
            if (tab.url.equals(Browser.HISTORY)) {
                Browser.select(tab);
                return;
            }
        }
        Tab tab = tab().url.isEmpty() ? tab() : Browser.newTab();
        Browser.go(tab, Browser.HISTORY);
        unfocus();
    }

    private void clearHistory() {
        if (!clearArmed) {
            clearArmed = true;
            return;
        }
        clearArmed = false;
        Browser.clearHistory();
    }

    private void clearData() {
        if (!dataArmed) {
            dataArmed = true;
            return;
        }
        dataArmed = false;
        Browser.clearData();
    }

    private void go(String input) {
        if (input == null || input.isBlank()) return;
        Browser.go(tab(), input);
        unfocus();
        focusPage = true;
    }

    private void unfocus() {
        address.focused = search.focused = filter.focused = false;
        suggestions.clear();
    }

    private void focusAddress() {
        // A picture taken now is what shows beneath the suggestions once typing starts.
        requestStill(true);
        if (live != null) WebNative.action(live.view, WebNative.FOCUS_GAME);
        unfocus();
        address.focused = true;
        address.setText(tab().page() ? tab().url : "");
        address.selectAll();
    }

    private void suggest() {
        suggestions.clear();
        picked = 0;
        String typed = address.text.trim();
        if (typed.isEmpty() || !address.focused) return;
        suggestions.add(Browser.isAddress(typed)
                ? new Suggestion("Go to " + typed, "", typed)
                : new Suggestion("Search " + Browser.engine().label() + " for “" + typed + "”", "", typed));
        for (Mark mark : Browser.suggest(typed, 6)) {
            suggestions.add(new Suggestion(mark.title().isBlank() ? Browser.host(mark.url()) : mark.title(), mark.url(), mark.url()));
        }
    }

    /**
     * A browser shortcut, from the game's keyboard or forwarded by a page that had it.
     *
     * @param toggle whether this is the key that opens and closes the browser
     */
    public boolean shortcut(int key, int mods, boolean toggle) {
        if (leaving || isClosing()) return false;
        if (toggle) {
            close();
            return true;
        }
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0, shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0, alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        if (key == GLFW.GLFW_KEY_F6) {
            focusAddress();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            Browser.reload(tab());
            return true;
        }
        if (alt && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            if (key == GLFW.GLFW_KEY_LEFT) Browser.back(tab());
            else Browser.forward(tab());
            return true;
        }
        if (!ctrl) return false;
        List<Tab> tabs = Browser.tabs();
        switch (key) {
            case GLFW.GLFW_KEY_T -> {
                if (shift) return false;
                newTab();
            }
            case GLFW.GLFW_KEY_N -> {
                if (!shift) return false;
                togglePrivate();
            }
            case GLFW.GLFW_KEY_W -> closeTab(tab());
            case GLFW.GLFW_KEY_L -> focusAddress();
            case GLFW.GLFW_KEY_D -> Browser.toggleBookmark(tab());
            case GLFW.GLFW_KEY_H -> openHistory();
            case GLFW.GLFW_KEY_R -> Browser.reload(tab());
            case GLFW.GLFW_KEY_TAB -> {
                Browser.select(tabs.get(Math.floorMod(tabs.indexOf(tab()) + (shift ? -1 : 1), tabs.size())));
                focusPage = true;
            }
            default -> {
                if (key < GLFW.GLFW_KEY_1 || key > GLFW.GLFW_KEY_9) return false;
                int n = key - GLFW.GLFW_KEY_1;
                Browser.select(tabs.get(key == GLFW.GLFW_KEY_9 ? tabs.size() - 1 : Math.min(n, tabs.size() - 1)));
                focusPage = true;
            }
        }
        return true;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void layout() {
        px = MARGIN;
        py = MARGIN;
        pw = width - MARGIN * 2;
        ph = height - MARGIN * 2;
        pageX = px + PAD;
        pageY = py + STRIP + BAR;
        pageW = pw - PAD * 2;
        pageH = ph - STRIP - BAR - PAD;
    }

    /** The accent, or a cool grey while the tabs are private. */
    private int tone() {
        float t = Math.clamp(tint.get(), 0f, 1f);
        return Colors.mix(Theme.accent(), 0xFF9AA6BC, t);
    }

    private Spring hover(Object key) {
        return hovers.computeIfAbsent(key, k -> Spring.snappy(0));
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        Tab tab = tab();
        boolean front = !drawingUnderlay && Mc.current() == this;
        if (front) {
            if (live != null && (mx != lastMx || my != lastMy)) requestStill(false);
            lastMx = mx;
            lastMy = my;
        }
        float open = openness(), fade = fade();
        float secret = tint.target(Browser.incognito() ? 1 : 0).update();
        if (Mc.mc().level == null && underlay() == null && vanillaUnderlay() == null) Theme.scene(c, width, height);
        Theme.veil(c, width, height, fade, 0.5f + 0.18f * secret);
        c.pushAlpha(fade);
        c.push();
        c.scale(0.96f + 0.04f * open, width / 2, height / 2);
        c.translate(0, (1 - open) * 8);
        Theme.panel(c, px, py, pw, ph, Theme.R_LG);
        if (secret > 0.01f) {
            // Private tabs: a darker, colder panel so it is plain which set is in view.
            c.rect(px, py, pw, ph, Theme.R_LG, Colors.withAlpha(0xFF0A0C12, 0.62f * secret));
            c.stroke(px, py, pw, ph, Theme.R_LG, 1, Colors.withAlpha(0xFF9AA6BC, 0.28f * secret));
        }

        drawStrip(c, mx, my, tab);
        drawBar(c, mx, my, tab);
        drawPage(c, mx, my, tab);
        drawSuggestions(c, mx, my);
        for (IconButton button : new IconButton[] {back, forward, tab.loading ? stop : reload}) button.drawTip(c, true);
        for (IconButton button : new IconButton[] {star, historyButton, secretButton, closeButton}) button.drawTip(c, false);
        c.pop();
        c.popAlpha();
        Toasts.draw(c);
        if (front) sync();
    }

    private void drawStrip(Canvas c, float mx, float my, Tab active) {
        List<Tab> tabs = Browser.tabs();
        float y = py + 5, h = STRIP - 7;
        float left = px + 8, right = px + pw - 8 - 18 - 4 - 18 - 8;
        if (Browser.incognito()) {
            String word = "Private";
            float bw = Fonts.SEMIBOLD.width(word, 7.2f) + 24;
            c.rect(left, y, bw, h, h / 2, 0x33A8B2C8);
            Icons.PRIVATE.draw(c, left + 10, y + h / 2, 9, 0xFFD5DBE8);
            c.textMiddle(Fonts.SEMIBOLD, word, left + 18, y, h, 7.2f, 0xFFD5DBE8);
            left += bw + 6;
        }
        stripX = left;
        tabW = Math.clamp((right - left - 22) / tabs.size(), 30f, 132f);
        c.clip(left - 1, py, right - left + 2, STRIP);
        float x = left;
        for (Tab tab : tabs) {
            boolean over = inside(mx, my, x, y, tabW - 2, h), on = tab == active;
            float hv = hover(tab).target(over ? 1 : 0).update();
            float w = tabW - 2;
            c.rect(x, y, w, h, 6, on ? 0x26FFFFFF : Colors.withAlpha(Colors.WHITE, 0.03f + 0.06f * hv));
            if (on) c.rect(x + 7, y + h - 1.5f, w - 14, 1.5f, 0.75f, tone());
            float tx = x + 6;
            drawIcon(c, tab, tx + 4, y + h / 2, 8);
            tx += 12;
            float room = x + w - 5 - tx;
            boolean closable = (on || over) && w > 52;
            if (closable) room -= 11;
            boolean sound = tab.audio || tab.muted;
            if (sound && w > 60) room -= 11;
            if (room > 8) {
                c.textMiddle(on ? Fonts.MEDIUM : Fonts.REGULAR, Fonts.REGULAR.truncate(tab.label(), 7.2f, room), tx, y, h, 7.2f,
                        on ? Theme.TEXT : Colors.mix(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv));
            }
            float ix = x + w - 9;
            if (closable) {
                boolean hot = inside(mx, my, ix - 5, y + 3, 10, h - 6);
                if (hot) c.circle(ix, y + h / 2, 5, 0x30FFFFFF);
                Icons.CLOSE.draw(c, ix, y + h / 2, 7, hot ? Theme.TEXT : Theme.TEXT_MUTED);
                ix -= 11;
            }
            if (sound && w > 60) (tab.muted ? Icons.MUTED : Icons.SOUND).draw(c, ix, y + h / 2, 8, tab.muted ? Theme.TEXT_MUTED : tone());
            x += tabW;
        }
        c.unclip();
        plus.bounds(Math.min(x + 2, right - 18), y + 0.5f, 18, h - 1);
        plus.draw(c, mx, my);
        secretButton.active = Browser.incognito();
        secretButton.label = Browser.incognito() ? "Back to your tabs" : Browser.privateCount() > 0 ? "Private tabs (" + Browser.privateCount() + ")" : "Private tabs";
        secretButton.bounds(px + pw - 8 - 18 - 4 - 18, y + 0.5f, 18, h - 1);
        secretButton.draw(c, mx, my);
        closeButton.bounds(px + pw - 8 - 18, y + 0.5f, 18, h - 1);
        closeButton.draw(c, mx, my);
    }

    /** A tab's or a site's mark: a spinner while loading, its own icon if known, a letter otherwise. */
    private void drawIcon(Canvas c, Tab tab, float cx, float cy, float size) {
        if (tab.loading) {
            spinner(c, cx, cy, size * 0.42f, tone());
        } else if (tab.url.equals(Browser.HISTORY)) {
            Icons.CLOCK.draw(c, cx, cy, size, Theme.TEXT_DIM);
        } else if (!tab.page()) {
            (tab.secret ? Icons.PRIVATE : Icons.SEARCH).draw(c, cx, cy, size, Theme.TEXT_DIM);
        } else {
            siteIcon(c, tab.url, cx, cy, size);
        }
    }

    private void siteIcon(Canvas c, String url, float cx, float cy, float size) {
        Picture icon = Browser.icon(url);
        if (icon != null) {
            icon.draw(c, cx - size / 2, cy - size / 2, size, size);
            return;
        }
        String host = Browser.host(url);
        String letter = host.isEmpty() ? "?" : host.substring(0, 1).toUpperCase();
        c.circle(cx, cy, size / 2, Colors.withAlpha(tone(), 0.35f));
        float ts = size * 0.62f;
        c.text(Fonts.SEMIBOLD, letter, cx - Fonts.SEMIBOLD.width(letter, ts) / 2, cy - Fonts.SEMIBOLD.height(ts) / 2, ts, Theme.TEXT);
    }

    private static void spinner(Canvas c, float cx, float cy, float r, int color) {
        float turn = Motion.reduced() ? 0 : Browser.now() * 6.5f;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float age = (float) ((turn - a) / (Math.PI * 2));
            age -= (float) Math.floor(age);
            c.circle(cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a), Math.max(0.6f, r * 0.22f), Colors.withAlpha(color, 0.15f + 0.85f * (1 - age)));
        }
    }

    private void drawBar(Canvas c, float mx, float my, Tab tab) {
        float y = py + STRIP + 1, h = BAR - 7, x = px + 8;
        back.enabled = tab.canBack && tab.page();
        forward.enabled = tab.canForward && tab.page();
        reload.enabled = stop.enabled = tab.page();
        back.bounds(x, y, h + 2, h);
        forward.bounds(x + h + 5, y, h + 2, h);
        IconButton third = tab.loading ? stop : reload;
        third.bounds(x + (h + 5) * 2, y, h + 2, h);
        back.draw(c, mx, my);
        forward.draw(c, mx, my);
        third.draw(c, mx, my);

        float right = px + pw - 8;
        historyButton.bounds(right - (h + 2), y, h + 2, h);
        historyButton.draw(c, mx, my);
        star.enabled = tab.page();
        star.active = tab.page() && Browser.bookmarked(tab.url);
        star.label = star.active ? "Remove bookmark" : "Bookmark this page";
        star.bounds(right - (h + 2) * 2 - 3, y, h + 2, h);
        star.draw(c, mx, my);

        float ax = x + (h + 5) * 3 + 2, aw = right - (h + 2) * 2 - 8 - ax;
        address.bounds(ax, y, aw, h);
        if (!address.focused) {
            address.text = tab.page() ? pretty(tab.url) : "";
            address.placeholder = tab.url.equals(Browser.HISTORY) ? "History" : "Search " + Browser.engine().label() + " or type an address";
        }
        address.draw(c, mx, my);

        // Loading: a thin bar that runs along under the toolbar.
        float busy = loadBar.target(tab.loading ? 1 : 0).update();
        if (busy > 0.02f) {
            float span = pw - 16, at = (Browser.now() * 0.9f) % 1f, bw = span * 0.3f;
            c.clip(px + 8, y + h + 2, span, 2);
            c.rect(px + 8 + at * (span + bw) - bw, y + h + 2.5f, bw, 1.2f, 0.6f, Colors.withAlpha(tone(), busy));
            c.unclip();
        }
    }

    /** An address without the clutter: no scheme, no trailing slash. */
    private static String pretty(String url) {
        String s = url.startsWith("https://") ? url.substring(8) : url;
        return s.endsWith("/") && s.indexOf('/') == s.length() - 1 ? s.substring(0, s.length() - 1) : s;
    }

    private void drawPage(Canvas c, float mx, float my, Tab tab) {
        c.rect(pageX, pageY, pageW, pageH, 4, 0xF00B0A10);
        if (tab.url.isEmpty()) {
            drawStart(c, mx, my, tab);
        } else if (tab.url.equals(Browser.HISTORY)) {
            drawHistory(c, mx, my);
        } else if (tab.crashed) {
            message(c, "This page stopped working", WebNative.state() == WebNative.FAILED ? "The system's web engine (WebView2) could not start." : "Its process ended unexpectedly.");
            retry.bounds(pageX + pageW / 2 - 40, pageY + pageH / 2 + 16, 80, 20);
            retry.draw(c, mx, my);
        } else if (WebNative.state() == WebNative.FAILED) {
            message(c, "The browser could not start", "The system's web engine (WebView2) is missing or would not run. See the game log.");
        } else if (live != tab || drawingUnderlay) {
            if (tab.still != null) {
                tab.still.draw(c, pageX, pageY, pageW, pageH);
            } else {
                spinner(c, pageX + pageW / 2, pageY + pageH / 2 - 8, 9, tone());
                String host = Browser.host(tab.url);
                c.textCentered(Fonts.REGULAR, host.isEmpty() ? "Loading…" : "Opening " + host + "…", pageX + pageW / 2, pageY + pageH / 2 + 10, 8, Theme.TEXT_MUTED);
            }
        }
    }

    private void message(Canvas c, String title, String body) {
        c.textCentered(Fonts.SEMIBOLD, title, pageX + pageW / 2, pageY + pageH / 2 - 18, 11, Theme.TEXT);
        c.textCentered(Fonts.REGULAR, body, pageX + pageW / 2, pageY + pageH / 2 - 3, 8, Theme.TEXT_MUTED);
    }

    // ---- start page ------------------------------------------------------------------------------

    private static final float TILE_W = 68, TILE_H = 50, TILE_GAP = 6;

    private float startW() {
        return Math.min(380, pageW - 40);
    }

    private float startX() {
        return pageX + (pageW - startW()) / 2;
    }

    private float startY() {
        return pageY + Math.max(18, pageH * 0.16f);
    }

    private int tileColumns() {
        return Math.max(1, (int) ((startW() + TILE_GAP) / (TILE_W + TILE_GAP)));
    }

    private void drawStart(Canvas c, float mx, float my, Tab tab) {
        float w = startW(), x = startX(), y = startY();
        String heading = tab.secret ? "Private tab" : "Where to?";
        c.textCentered(Fonts.BOLD, heading, pageX + pageW / 2, y, 16, Theme.TEXT);
        y += 28;
        search.placeholder = "Search " + Browser.engine().label() + " or type an address";
        search.bounds(x, y, w, 26);
        search.draw(c, mx, my);
        y += 38;
        if (tab.secret) {
            String[] notes = {"Private tabs keep no history, cookies, sign-ins or suggestions.",
                    "Everything they stored is forgotten when the last one is closed.",
                    "Sites, your network and the server you play on can still see you."};
            for (String note : notes) {
                c.textCentered(Fonts.REGULAR, note, pageX + pageW / 2, y, 7.8f, Theme.TEXT_MUTED);
                y += 12;
            }
            return;
        }
        c.clip(pageX, y - 2, pageW, pageY + pageH - y);
        List<Mark> marks = Browser.bookmarks();
        if (!marks.isEmpty()) {
            c.text(Fonts.SEMIBOLD, "Bookmarks", x + 1, y, 7.2f, Theme.TEXT_DIM);
            y += 13;
            int columns = tileColumns();
            float tw = (w - TILE_GAP * (columns - 1)) / columns;
            for (int i = 0; i < marks.size() && i < columns * 2; i++) {
                Mark mark = marks.get(i);
                float tx = x + (i % columns) * (tw + TILE_GAP), ty = y + (i / columns) * (TILE_H + TILE_GAP);
                boolean over = inside(mx, my, tx, ty, tw, TILE_H);
                float hv = hover(mark).target(over ? 1 : 0).update();
                c.rect(tx, ty - 1.5f * hv, tw, TILE_H, Theme.R_MD, Colors.withAlpha(Colors.WHITE, 0.05f + 0.05f * hv));
                c.stroke(tx, ty - 1.5f * hv, tw, TILE_H, Theme.R_MD, 1, Colors.mix(Theme.BORDER, Colors.withAlpha(tone(), 0.6f), hv));
                siteIcon(c, mark.url(), tx + tw / 2, ty + 18 - 1.5f * hv, 16);
                c.textCentered(Fonts.REGULAR, Fonts.REGULAR.truncate(mark.title().isBlank() ? Browser.host(mark.url()) : mark.title(), 6.8f, tw - 8),
                        tx + tw / 2, ty + 34 - 1.5f * hv, 6.8f, Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
                if (over) {
                    boolean hot = inside(mx, my, tx + tw - 13, ty, 13, 13);
                    Icons.CLOSE.draw(c, tx + tw - 7, ty + 6, 6.5f, hot ? Theme.DANGER : Theme.TEXT_MUTED);
                }
            }
            y += ((Math.min(marks.size(), columns * 2) + columns - 1) / columns) * (TILE_H + TILE_GAP) + 8;
        }
        List<Visit> visits = Browser.history();
        if (!visits.isEmpty()) {
            c.text(Fonts.SEMIBOLD, "Recently visited", x + 1, y, 7.2f, Theme.TEXT_DIM);
            y += 13;
            for (int i = 0; i < visits.size() && i < 6 && y + ROW < pageY + pageH; i++) {
                visitRow(c, visits.get(i), x, y, w, inside(mx, my, x, y, w, ROW), false);
                y += ROW + 1;
            }
        }
        if (marks.isEmpty() && visits.isEmpty()) {
            c.textCentered(Fonts.REGULAR, "Pages you star and places you have been will show up here.", pageX + pageW / 2, y + 6, 7.8f, Theme.TEXT_MUTED);
        }
        c.unclip();
    }

    private void visitRow(Canvas c, Visit visit, float x, float y, float w, boolean over, boolean when) {
        float hv = hover(visit).target(over ? 1 : 0).update();
        c.rect(x, y, w, ROW, 5, Colors.withAlpha(Colors.WHITE, 0.07f * hv));
        siteIcon(c, visit.url, x + 10, y + ROW / 2, 9);
        String host = Browser.host(visit.url);
        float hw = Math.min(w * 0.35f, Fonts.REGULAR.width(host, 7f)), right = x + w - 8;
        if (when) {
            var at = Instant.ofEpochMilli(visit.time).atZone(ZoneId.systemDefault());
            String stamp = at.toLocalDate().equals(LocalDate.now()) ? TIME.format(at) : DAY.format(at);
            c.textRight(Fonts.REGULAR, stamp, right, y + (ROW - Fonts.REGULAR.height(7f)) / 2, 7f, Theme.TEXT_MUTED);
            right -= 34;
        }
        c.textRight(Fonts.REGULAR, Fonts.REGULAR.truncate(host, 7f, hw), right, y + (ROW - Fonts.REGULAR.height(7f)) / 2, 7f, Theme.TEXT_MUTED);
        String title = visit.title == null || visit.title.isBlank() ? pretty(visit.url) : visit.title;
        c.textMiddle(Fonts.REGULAR, Fonts.REGULAR.truncate(title, 8f, right - hw - 10 - (x + 20)), x + 20, y, ROW, 8f, Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
    }

    // ---- history page ----------------------------------------------------------------------------

    private List<Visit> filtered() {
        String q = filter.text.trim().toLowerCase();
        if (q.isEmpty()) return Browser.history();
        List<Visit> out = new ArrayList<>();
        for (Visit visit : Browser.history()) {
            if (visit.url.toLowerCase().contains(q) || visit.title != null && visit.title.toLowerCase().contains(q)) out.add(visit);
        }
        return out;
    }

    private float listX() {
        return pageX + Math.max(14, (pageW - 520) / 2);
    }

    private float listW() {
        return pageW - (listX() - pageX) * 2;
    }

    private void drawHistory(Canvas c, float mx, float my) {
        float x = listX(), w = listW(), y = pageY + 12;
        c.text(Fonts.BOLD, "History", x, y, 13, Theme.TEXT);
        clearData.label = dataArmed ? "Click again: signs you out everywhere" : "Clear browsing data";
        clearHistory.label = clearArmed ? "Click again to clear" : "Clear history";
        float dw = Fonts.MEDIUM.width(clearData.label, 7.5f) + 18, hw = Fonts.MEDIUM.width(clearHistory.label, 7.5f) + 18;
        clearData.bounds(x + w - dw, y, dw, 16);
        clearHistory.bounds(x + w - dw - hw - 4, y, hw, 16);
        clearData.draw(c, mx, my);
        clearHistory.draw(c, mx, my);
        y += 24;
        filter.bounds(x, y, w, 20);
        filter.draw(c, mx, my);
        y += 28;
        List<Visit> visits = filtered();
        float viewH = pageY + pageH - y - 6;
        float off = scroll.update(visits.size() * (ROW + 1), viewH);
        c.clip(x - 2, y, w + 4, viewH);
        boolean within = inside(mx, my, x, y, w, viewH);
        for (int i = 0; i < visits.size(); i++) {
            float ry = y + i * (ROW + 1) - off;
            if (ry + ROW < y || ry > y + viewH) continue;
            visitRow(c, visits.get(i), x, ry, w, within && inside(mx, my, x, ry, w, ROW), true);
        }
        if (visits.isEmpty()) {
            c.textCentered(Fonts.REGULAR, filter.text.isBlank() ? "Nothing here yet." : "No pages match.", pageX + pageW / 2, y + 18, 8, Theme.TEXT_MUTED);
        }
        c.unclip();
        scroll.drawBar(c, x + w + 3, y + 2, viewH - 4);
    }

    // ---- suggestions -----------------------------------------------------------------------------

    private void drawSuggestions(Canvas c, float mx, float my) {
        float shown = drop.target(address.focused && !suggestions.isEmpty() ? 1 : 0).update();
        if (shown < 0.02f || suggestions.isEmpty()) return;
        float x = address.x, y = address.y + address.h + 3, w = address.w, h = suggestions.size() * ROW + 6;
        c.pushAlpha(Math.clamp(shown, 0f, 1f));
        c.push();
        c.translate(0, (1 - shown) * -4);
        c.shadow(x, y + 4, w, h, Theme.R_MD, 16, 0x80000000);
        c.rect(x, y, w, h, Theme.R_MD, 0xF5121019);
        c.stroke(x, y, w, h, Theme.R_MD, 1, Theme.BORDER_STRONG);
        for (int i = 0; i < suggestions.size(); i++) {
            Suggestion s = suggestions.get(i);
            float ry = y + 3 + i * ROW;
            if (inside(mx, my, x, ry, w, ROW) && (mx != dropMx || my != dropMy)) picked = i;
            if (i == picked) c.rect(x + 3, ry, w - 6, ROW, 5, Colors.withAlpha(tone(), 0.22f));
            if (i == 0) Icons.SEARCH.draw(c, x + 12, ry + ROW / 2, 8, Theme.TEXT_DIM);
            else siteIcon(c, s.target, x + 12, ry + ROW / 2, 9);
            float dw = s.detail.isEmpty() ? 0 : Math.min(w * 0.4f, Fonts.REGULAR.width(pretty(s.detail), 7f));
            if (dw > 0) c.textRight(Fonts.REGULAR, Fonts.REGULAR.truncate(pretty(s.detail), 7f, dw), x + w - 9, ry + (ROW - Fonts.REGULAR.height(7f)) / 2, 7f, Theme.TEXT_MUTED);
            c.textMiddle(Fonts.REGULAR, Fonts.REGULAR.truncate(s.title, 8f, w - 34 - dw), x + 22, ry, ROW, 8f, Theme.TEXT);
        }
        dropMx = mx;
        dropMy = my;
        c.pop();
        c.popAlpha();
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (leaving || isClosing()) return false;
        if (!inside(x, y, px, py, pw, ph)) {
            close();
            return true;
        }
        // A click on Aller's own controls takes the keyboard back from the page.
        if (live != null) WebNative.action(live.view, WebNative.FOCUS_GAME);
        Tab tab = tab();

        if (address.focused && !suggestions.isEmpty()) {
            float sy = address.y + address.h + 6;
            if (inside(x, y, address.x, sy, address.w, suggestions.size() * ROW)) {
                go(suggestions.get(Math.clamp((int) ((y - sy) / ROW), 0, suggestions.size() - 1)).target);
                return true;
            }
        }
        boolean wasFocused = address.focused;
        if (address.hit(x, y)) {
            if (!wasFocused) focusAddress();
            else address.mouseDown(x, y, button);
            return true;
        }
        unfocus();

        float ty = py + 5, th = STRIP - 7;
        if (inside(x, y, stripX, ty, tabW * Browser.tabs().size(), th)) {
            List<Tab> tabs = Browser.tabs();
            int at = Math.clamp((int) ((x - stripX) / tabW), 0, tabs.size() - 1);
            Tab hit = tabs.get(at);
            float tx = stripX + at * tabW, w = tabW - 2;
            boolean closable = w > 52;
            float ix = tx + w - 9;
            if (button == 2 || button == 0 && closable && inside(x, y, ix - 5, ty, 10, th)) {
                Sounds.click();
                closeTab(hit);
                return true;
            }
            if (closable) ix -= 11;
            if (button == 0 && (hit.audio || hit.muted) && w > 60 && inside(x, y, ix - 5, ty, 10, th)) {
                Browser.toggleMute(hit);
                return true;
            }
            if (button == 0 && hit != tab) {
                Sounds.click();
                Browser.select(hit);
                focusPage = true;
            }
            return true;
        }
        if (button != 0) return true;
        for (IconButton b : new IconButton[] {back, forward, tab.loading ? stop : reload, star, historyButton, secretButton, plus, closeButton}) {
            if (b.mouseDown(x, y, button)) return true;
        }
        if (!inside(x, y, pageX, pageY, pageW, pageH)) return true;

        if (tab.url.isEmpty()) {
            if (search.mouseDown(x, y, button)) return true;
            if (!tab.secret) startClick(x, y);
        } else if (tab.url.equals(Browser.HISTORY)) {
            if (filter.mouseDown(x, y, button) || clearHistory.mouseDown(x, y, button) || clearData.mouseDown(x, y, button)) return true;
            clearArmed = dataArmed = false;
            float lx = listX(), ly = pageY + 12 + 24 + 28, lw = listW();
            if (inside(x, y, lx, ly, lw, pageY + pageH - ly - 6)) {
                List<Visit> visits = filtered();
                int at = (int) ((y - ly + scroll.get()) / (ROW + 1));
                if (at >= 0 && at < visits.size()) go(visits.get(at).url);
            }
        } else if (tab.crashed) {
            retry.mouseDown(x, y, button);
        }
        return true;
    }

    private void startClick(float x, float y) {
        float w = startW(), sx = startX(), sy = startY() + 28 + 38;
        List<Mark> marks = Browser.bookmarks();
        if (!marks.isEmpty()) {
            sy += 13;
            int columns = tileColumns();
            float tw = (w - TILE_GAP * (columns - 1)) / columns;
            for (int i = 0; i < marks.size() && i < columns * 2; i++) {
                float tx = sx + (i % columns) * (tw + TILE_GAP), ty = sy + (i / columns) * (TILE_H + TILE_GAP);
                if (!inside(x, y, tx, ty, tw, TILE_H)) continue;
                if (inside(x, y, tx + tw - 13, ty, 13, 13)) Browser.removeBookmark(marks.get(i));
                else go(marks.get(i).url());
                return;
            }
            sy += ((Math.min(marks.size(), columns * 2) + columns - 1) / columns) * (TILE_H + TILE_GAP) + 8;
        }
        List<Visit> visits = Browser.history();
        sy += 13;
        for (int i = 0; i < visits.size() && i < 6; i++) {
            if (inside(x, y, sx, sy + i * (ROW + 1), w, ROW)) {
                go(visits.get(i).url);
                return;
            }
        }
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        boolean used = false;
        Tab tab = tab();
        for (IconButton b : new IconButton[] {back, forward, tab.loading ? stop : reload, star, historyButton, secretButton, plus, closeButton}) {
            used |= b.mouseUp(x, y, button);
        }
        used |= retry.mouseUp(x, y, button);
        used |= clearHistory.mouseUp(x, y, button);
        used |= clearData.mouseUp(x, y, button);
        return used;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        if (tab().url.equals(Browser.HISTORY)) scroll.scroll(amount * 1.5f);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (leaving || isClosing()) return true;
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (shortcut(key, mods, false)) return true;
        if (address.focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                unfocus();
                focusPage = true;
            } else if (enter) {
                go(suggestions.isEmpty() ? address.text : suggestions.get(Math.clamp(picked, 0, suggestions.size() - 1)).target);
            } else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
                if (!suggestions.isEmpty()) picked = Math.floorMod(picked + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), suggestions.size());
            } else {
                address.keyDown(key, mods);
            }
            return true;
        }
        if (search.focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (search.text.isEmpty()) close();
                else search.setText("");
            } else if (enter) {
                String typed = search.text;
                search.setText("");
                go(typed);
            } else {
                search.keyDown(key, mods);
            }
            return true;
        }
        if (filter.focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) filter.focused = false;
            else filter.keyDown(key, mods);
            scroll.reset();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) close();
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (leaving || isClosing()) return false;
        if (address.charTyped(codepoint) || search.charTyped(codepoint)) return true;
        if (filter.charTyped(codepoint)) {
            scroll.reset();
            return true;
        }
        return false;
    }
}
