package dev.aller.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.aller.AllerClient;
import dev.aller.command.Launcher;
import dev.aller.module.Modules;
import dev.aller.module.mods.UtilityMods;
import dev.aller.platform.Mc;
import dev.aller.platform.Picture;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.WebNative;
import dev.aller.screen.BrowserScreen;
import dev.aller.setting.Settings;
import dev.aller.ui.Toasts;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The web browser's data and its link to the system webview: tabs, bookmarks, history and the
 * events coming back from {@link WebNative}. {@link BrowserScreen} is only the window onto it, so
 * pages (and their sound) carry on while it is closed.
 *
 * <p>Private tabs are a second set that uses the webview's own private profile. Nothing about them
 * is written anywhere by Aller: no history, no session, no site icons.
 */
public final class Browser {
    public enum Engine implements Settings.Named {
        GOOGLE("Google", "https://www.google.com/search?q="),
        BRAVE("Brave", "https://search.brave.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        BING("Bing", "https://www.bing.com/search?q="),
        PERPLEXITY("Perplexity", "https://www.perplexity.ai/search?q="),
        ECOSIA("Ecosia", "https://www.ecosia.org/search?q="),
        STARTPAGE("Startpage", "https://www.startpage.com/do/search?q="),
        QWANT("Qwant", "https://www.qwant.com/?q="),
        KAGI("Kagi", "https://kagi.com/search?q="),
        YAHOO("Yahoo", "https://search.yahoo.com/search?p=");

        private final String label;
        public final String query;

        Engine(String label, String query) {
            this.label = label;
            this.query = query;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public static final String HISTORY = "aller:history";
    private static final int MAX_VISITS = 2000;
    /** Seconds a tab can sit unseen and silent before its page is put to sleep. */
    private static final float SLEEP_AFTER = 300;

    /**
     * Passed to the browser process. The feature names are Edge's own (each was checked against the
     * WebView2 runtime's binary): no SmartScreen lookups, no Family Safety reporting or filtering
     * service, no sign-in with the Windows account, and diagnostic data forced off. The plain
     * switches stop crash uploads, reliability pings and usage reports.
     */
    private static final String ARGUMENTS = "--disable-features=msWebOOUI,msPdfOOUI,msSmartScreenProtection,msSmartScreenEnableTelemetry,"
            + "msWebView2EnableFamilySafety,msFamilySafetyGraphApi,msEdge3PTelemetry,msEdge3PErrorTelemetry,msEdgeHJTelemetry,"
            + "msExtensionTelemetryFramework,msUserDataDirUsageTelemetry,msSendTelemetryForProcessIntegrity,msSendTelemetryForProcessIntegrityV2,"
            + "msEnableMATSTelemetry,msSendSSODiagnostics,msImplicitSignin,msSingleSignOnOSForPrimaryAccountIsShared"
            + " --enable-features=msDiagnosticDataForceOff"
            + " --disable-domain-reliability --disable-breakpad --metrics-recording-only --no-pings --disable-sync";

    /**
     * Runs in every page. Escape belongs to the page first: it leaves full screen or a dialog, then
     * a text box, and only an Escape nothing wanted closes the browser.
     */
    private static final String SCRIPT = "window.addEventListener('keydown',function(e){if(e.key!=='Escape'||e.defaultPrevented||document.fullscreenElement)return;"
            + "var a=document.activeElement;if(a&&(a.isContentEditable||/^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName))){a.blur();return;}"
            + "if(document.querySelector('dialog[open]'))return;window.ipc.postMessage('aller:esc');},false);";

    public static final class Tab {
        public final boolean secret;
        /** Empty for the start page, {@link #HISTORY}, or a web address. */
        public String url = "";
        public String title = "";
        /** The native webview, or -1 while the tab has none (a start page, or a restored tab not looked at yet). */
        public int view = -1;
        public boolean loading, canBack, canForward, audio, muted, fullscreen, crashed, focused;
        /** The page as it last looked, drawn while the real one has to be out of the way. */
        public Picture still;
        public float stillAt = -1000;
        float seen;
        boolean asleep;

        Tab(boolean secret) {
            this.secret = secret;
            seen = now();
        }

        /** Whether the tab shows a web page rather than one of Aller's own. */
        public boolean page() {
            return !url.isEmpty() && !url.startsWith("aller:");
        }

        public String label() {
            if (!title.isBlank()) return title;
            if (url.isEmpty()) return secret ? "Private tab" : "New tab";
            return url.equals(HISTORY) ? "History" : host(url);
        }
    }

    public record Mark(String url, String title) {}

    public static final class Visit {
        public final String url;
        public String title;
        public final long time;

        Visit(String url, String title, long time) {
            this.url = url;
            this.title = title;
            this.time = time;
        }
    }

    private static final List<Tab> open = new ArrayList<>(), secret = new ArrayList<>();
    private static Tab activeOpen, activeSecret;
    private static boolean incognito;
    private static final List<Mark> marks = new ArrayList<>();
    private static final List<Visit> visits = new ArrayList<>();
    private static final Map<String, Picture> icons = new HashMap<>();
    private static final Set<String> noIcon = new LinkedHashSet<>();
    private static boolean loaded, dirty, keyWasDown, chat, muffled;
    private static float savedAt, sleptAt;
    private static final long epoch = System.nanoTime();

    private Browser() {}

    /** Seconds since the game started, unaffected by the animation speed setting. */
    public static float now() {
        return (System.nanoTime() - epoch) / 1e9f;
    }

    private static UtilityMods.WebBrowser mod() {
        return Modules.BROWSER;
    }

    public static boolean usable() {
        return mod().enabled() && WebNative.supported();
    }

    public static Path dir() {
        return Mc.mc().gameDirectory.toPath().resolve("aller-browser");
    }

    public static Engine engine() {
        return mod().engine.get();
    }

    // ---- opening ---------------------------------------------------------------------------------

    /**
     * Shows the browser, over {@code parent} if there is one.
     *
     * @param input an address or something to search for, or null to show the tabs as they are
     */
    public static void open(Screen parent, String input) {
        if (!mod().enabled()) return;
        load();
        if (!WebNative.load(dir())) {
            Toasts.warn("Browser unavailable", WebNative.problem());
            return;
        }
        start();
        if (input != null && !input.isBlank()) {
            Tab tab = active();
            go(tab.url.isEmpty() ? tab : newTab(), input);
        }
        if (Mc.current() instanceof BrowserScreen) return;
        boolean keep = parent instanceof ScreenHost || parent != null && !(parent instanceof ChatScreen) && !(parent instanceof ConfirmLinkScreen);
        Mc.setScreen(new ScreenHost(new BrowserScreen(keep ? parent : null)));
    }

    private static void start() {
        if (WebNative.state() == WebNative.PENDING || WebNative.state() == WebNative.READY) return;
        Path profile = dir().resolve("profile");
        if (Files.exists(dir().resolve("clear"))) {
            deleteTree(profile);
            deleteTree(dir().resolve("clear"));
        }
        WebNative.start(profile.toAbsolutePath().toString(), ARGUMENTS);
        syncKeys();
    }

    /** Tells the webviews which key combinations are Aller's even while a page has the keyboard. */
    public static void syncKeys() {
        if (!WebNative.loaded()) return;
        int ctrl = GLFW.GLFW_MOD_CONTROL, shift = GLFW.GLFW_MOD_SHIFT;
        List<Integer> keys = new ArrayList<>();
        for (int key : new int[] {GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_TAB}) {
            keys.add(Settings.Key.pack(key, ctrl));
        }
        keys.add(Settings.Key.pack(GLFW.GLFW_KEY_TAB, ctrl | shift));
        keys.add(Settings.Key.pack(GLFW.GLFW_KEY_N, ctrl | shift));
        keys.add(Settings.Key.pack(GLFW.GLFW_KEY_T, ctrl | shift));
        for (int n = GLFW.GLFW_KEY_1; n <= GLFW.GLFW_KEY_9; n++) keys.add(Settings.Key.pack(n, ctrl));
        keys.add(GLFW.GLFW_KEY_F6);
        keys.add(mod().keybind.get());
        WebNative.keys(keys.stream().mapToInt(Browser::toNative).filter(k -> k >= 0).toArray());
    }

    /** GLFW's key code as a Windows virtual key, keeping the modifiers; -1 for keys with no simple equivalent. */
    private static int toNative(int packed) {
        if (packed < 0) return -1;
        int key = Settings.Key.code(packed), mods = Settings.Key.mods(packed), vk;
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9 || key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) vk = key;
        else if (key == GLFW.GLFW_KEY_TAB) vk = 0x09;
        else if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F24) vk = 0x70 + key - GLFW.GLFW_KEY_F1;
        else return -1;
        return vk | mods << 16;
    }

    private static int fromNative(int packed) {
        int vk = packed & 0xFFFF, mods = packed >> 16 & 0xF, key;
        if (vk == 0x09) key = GLFW.GLFW_KEY_TAB;
        else if (vk >= 0x70 && vk <= 0x87) key = GLFW.GLFW_KEY_F1 + vk - 0x70;
        else key = vk;
        return Settings.Key.pack(key, mods);
    }

    // ---- tabs ------------------------------------------------------------------------------------

    public static boolean incognito() {
        return incognito;
    }

    /** Switches the window between the ordinary tabs and the private ones. */
    public static void setIncognito(boolean on) {
        incognito = on;
        active();
    }

    public static List<Tab> tabs() {
        return incognito ? secret : open;
    }

    public static int privateCount() {
        return secret.size();
    }

    /** The tab in view; there is always one. */
    public static Tab active() {
        load();
        List<Tab> tabs = tabs();
        Tab tab = incognito ? activeSecret : activeOpen;
        if (tab == null || !tabs.contains(tab)) {
            if (tabs.isEmpty()) tabs.add(new Tab(incognito));
            tab = tabs.get(tabs.size() - 1);
            select(tab);
        }
        return tab;
    }

    public static void select(Tab tab) {
        if (tab.secret) activeSecret = tab;
        else activeOpen = tab;
        incognito = tab.secret;
        tab.seen = now();
        dirty = true;
    }

    public static Tab newTab() {
        Tab tab = new Tab(incognito);
        List<Tab> tabs = tabs();
        Tab current = incognito ? activeSecret : activeOpen;
        int at = tabs.indexOf(current);
        tabs.add(at < 0 ? tabs.size() : at + 1, tab);
        select(tab);
        return tab;
    }

    /** @return true if that was the last ordinary tab, so the window should close too */
    public static boolean close(Tab tab) {
        List<Tab> tabs = tab.secret ? secret : open;
        int at = tabs.indexOf(tab);
        if (at < 0) return false;
        release(tab);
        tabs.remove(at);
        dirty = true;
        if (!tabs.isEmpty()) {
            if ((tab.secret ? activeSecret : activeOpen) == tab) select(tabs.get(Math.min(at, tabs.size() - 1)));
            return false;
        }
        if (tab.secret) {
            // The private session ends with its last tab; the webview forgets it then.
            activeSecret = null;
            incognito = false;
            active();
            return false;
        }
        activeOpen = null;
        active();
        return !incognito;
    }

    private static void release(Tab tab) {
        if (tab.view >= 0) WebNative.destroy(tab.view);
        tab.view = -1;
        if (tab.still != null) tab.still.close();
        tab.still = null;
    }

    /** Gives a tab its webview once the browser process is up. Stalls for a moment, so call it between frames. */
    public static void realise(Tab tab, int width, int height) {
        if (tab.view >= 0 || !tab.page() || WebNative.state() != WebNative.READY) return;
        tab.view = WebNative.create(WebNative.window(), tab.url, SCRIPT, width, height, tab.secret);
        if (tab.view < 0) {
            tab.crashed = true;
            return;
        }
        tab.loading = true;
        if (tab.muted || muffled) WebNative.action(tab.view, WebNative.MUTE);
    }

    /** Called while a tab is on screen. */
    public static void seen(Tab tab) {
        tab.seen = now();
        if (tab.asleep && tab.view >= 0) {
            WebNative.action(tab.view, WebNative.RESUME);
            tab.asleep = false;
        }
    }

    /** What typing {@code input} into the address bar leads to: the address itself, or a search for it. */
    public static String resolve(String input) {
        String text = input.trim();
        if (text.equals(HISTORY)) return text;
        String lower = text.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) return text;
        boolean address = !text.contains(" ") && (text.contains(".") && !text.endsWith(".") || lower.startsWith("localhost"));
        if (address && !lower.contains("://")) return "https://" + text;
        return engine().query + URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** Whether {@link #resolve} would treat the text as an address. */
    public static boolean isAddress(String input) {
        return !resolve(input).startsWith(engine().query);
    }

    public static void go(Tab tab, String input) {
        if (input == null || input.isBlank()) return;
        String url = resolve(input);
        tab.crashed = false;
        tab.title = "";
        if (!tab.page() || url.startsWith("aller:")) {
            // Leaving or entering one of Aller's own pages: the webview comes and goes with it.
            if (url.startsWith("aller:")) release(tab);
            tab.url = url;
            tab.loading = tab.page();
        } else if (tab.view >= 0) {
            tab.url = url;
            tab.loading = true;
            WebNative.navigate(tab.view, url);
        } else {
            tab.url = url;
        }
        dirty = true;
    }

    public static void back(Tab tab) {
        if (tab.view >= 0 && tab.canBack) WebNative.action(tab.view, WebNative.BACK);
    }

    public static void forward(Tab tab) {
        if (tab.view >= 0 && tab.canForward) WebNative.action(tab.view, WebNative.FORWARD);
    }

    public static void reload(Tab tab) {
        if (tab.crashed) {
            release(tab);
            tab.crashed = false;
            tab.loading = true;
        } else if (tab.view >= 0) {
            WebNative.action(tab.view, tab.loading ? WebNative.STOP : WebNative.RELOAD);
        }
    }

    public static void toggleMute(Tab tab) {
        tab.muted = !tab.muted;
        if (tab.view >= 0 && !muffled) WebNative.action(tab.view, tab.muted ? WebNative.MUTE : WebNative.UNMUTE);
    }

    private static int away;

    /**
     * GLFW minimises a fullscreen window as soon as it loses the keyboard, which a page taking it
     * would trigger. While the browser is open that is switched off, and done here instead when
     * the player really does leave for another program.
     */
    private static void holdFullscreen(boolean browserOpen) {
        long window = Mc.window();
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_AUTO_ICONIFY, browserOpen ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
        if (!browserOpen || !Mc.mc().getWindow().isFullscreen() || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
            away = 0;
            return;
        }
        away = WebNative.foreground(WebNative.window()) ? 0 : away + 1;
        if (away == 3) GLFW.glfwIconifyWindow(window);
    }

    /** The window opened or closed. With "Keep playing when closed" off, every page is silenced while it is shut. */
    public static void windowShown(boolean shown) {
        holdFullscreen(shown);
        boolean quiet = !shown && !mod().background.get();
        if (quiet != muffled) {
            muffled = quiet;
            for (Tab tab : all()) {
                if (tab.view >= 0 && !tab.muted) WebNative.action(tab.view, quiet ? WebNative.MUTE : WebNative.UNMUTE);
            }
        }
        if (!shown) save();
        else syncKeys();
    }

    private static List<Tab> all() {
        List<Tab> all = new ArrayList<>(open);
        all.addAll(secret);
        return all;
    }

    private static Tab byView(int id) {
        for (Tab tab : open) if (tab.view == id) return tab;
        for (Tab tab : secret) if (tab.view == id) return tab;
        return null;
    }

    // ---- once a frame ----------------------------------------------------------------------------

    public static void frame() {
        Screen screen = Mc.screen();
        if (screen instanceof ChatScreen) chat = true;
        else if (!(screen instanceof ConfirmLinkScreen)) chat = false;

        int chord = mod().keybind.get();
        boolean down = mod().enabled() && chord != Settings.Key.NONE && Mc.isDown(chord);
        if (down && !keyWasDown) pressed(chord);
        keyWasDown = down;

        if (!WebNative.loaded()) return;
        if (Mc.current() instanceof BrowserScreen) holdFullscreen(true);
        String[] events = WebNative.poll();
        if (events != null) for (String event : events) handle(event);

        float now = now();
        if (dirty && now - savedAt > 5) save();
        if (now - sleptAt > 10) {
            sleptAt = now;
            for (Tab tab : all()) {
                if (tab.view >= 0 && !tab.asleep && !tab.audio && now - tab.seen > SLEEP_AFTER) {
                    WebNative.action(tab.view, WebNative.SUSPEND);
                    tab.asleep = true;
                }
            }
        }
    }

    private static void pressed(int chord) {
        if (Mc.loadingOverlay()) return;
        if (Mc.current() instanceof BrowserScreen showing) {
            showing.close();
            return;
        }
        boolean modified = (Settings.Key.mods(chord) & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0;
        Screen screen = Mc.screen();
        if (Launcher.allowed(screen, modified)) open(screen, null);
    }

    private static void handle(String event) {
        String[] parts = event.split("\t", 3);
        if (parts.length < 3) return;
        String kind = parts[1], data = parts[2];
        if (kind.equals("error")) {
            AllerClient.LOG.warn("Browser: {}", data);
            return;
        }
        Tab tab;
        try {
            tab = byView(Integer.parseInt(parts[0]));
        } catch (NumberFormatException e) {
            return;
        }
        if (tab == null) return;
        BrowserScreen screen = Mc.current() instanceof BrowserScreen showing ? showing : null;
        switch (kind) {
            case "url" -> {
                if (!data.isEmpty() && !data.equals("about:blank")) tab.url = data;
                dirty = true;
            }
            case "title" -> {
                tab.title = data;
                if (!tab.secret && !visits.isEmpty() && visits.get(0).url.equals(tab.url)) visits.get(0).title = data;
                dirty = true;
            }
            case "load" -> {
                tab.loading = data.equals("1");
                if (!tab.loading) visited(tab);
            }
            case "nav" -> {
                tab.canBack = data.startsWith("1");
                tab.canForward = data.endsWith("1");
            }
            case "audio" -> {
                tab.audio = data.equals("1");
                if (tab.audio) tab.seen = now();
            }
            case "full" -> tab.fullscreen = data.equals("1");
            case "focus" -> tab.focused = data.equals("1");
            case "crash" -> tab.crashed = true;
            case "open" -> {
                // A page asked for a new window: it becomes a tab beside the one that asked.
                boolean was = incognito;
                incognito = tab.secret;
                select(tab);
                go(newTab(), data);
                if (screen == null) incognito = was;
            }
            case "close" -> {
                if (close(tab) && screen != null) screen.close();
            }
            case "download" -> Toasts.info("Download blocked", "Aller's browser does not save files. " + host(data));
            case "esc" -> {
                if (screen != null && tab == active()) screen.close();
            }
            case "key" -> {
                try {
                    int packed = fromNative(Integer.parseInt(data));
                    if (screen != null) screen.shortcut(Settings.Key.code(packed), Settings.Key.mods(packed), packed == mod().keybind.get());
                } catch (NumberFormatException ignored) {
                    // not a key
                }
            }
            case "favicon" -> icon(tab, WebNative.blob(tab.view, WebNative.FAVICON));
            case "capture" -> {
                if (data.equals("1")) still(tab, WebNative.blob(tab.view, WebNative.STILL));
            }
            default -> {}
        }
    }

    /** Decodes a page still away from the render thread, then hands it to the tab if it is still wanted. */
    private static void still(Tab tab, byte[] png) {
        if (png == null) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                return NativeImage.read(png);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }).thenAcceptAsync(image -> {
            if (image == null) return;
            if (byView(tab.view) != tab) {
                image.close();
                return;
            }
            if (tab.still != null) tab.still.close();
            tab.still = new Picture(image);
            tab.stillAt = now();
        }, Mc.mc());
    }

    public static void dropStill(Tab tab) {
        if (tab.still != null) tab.still.close();
        tab.still = null;
        tab.stillAt = -1000;
    }

    // ---- site icons ------------------------------------------------------------------------------

    private static void icon(Tab tab, byte[] png) {
        String host = host(tab.url);
        if (png == null || host.isEmpty()) return;
        try {
            Picture picture = new Picture(NativeImage.read(png));
            Picture old = icons.put(host, picture);
            if (old != null) old.close();
            noIcon.remove(host);
            if (tab.secret) return;
            Path file = iconFile(host);
            Files.createDirectories(file.getParent());
            Files.write(file, png);
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.debug("Could not keep the icon of {}", host, e);
        }
    }

    private static Path iconFile(String host) {
        return dir().resolve("favicons").resolve(host.replaceAll("[^a-z0-9.-]", "_") + ".png");
    }

    /** The site's icon if one has been seen, read from disk the first time it is asked for; otherwise null. */
    public static Picture icon(String url) {
        String host = host(url);
        if (host.isEmpty() || noIcon.contains(host)) return null;
        Picture picture = icons.get(host);
        if (picture != null) return picture;
        try {
            Path file = iconFile(host);
            if (Files.exists(file) && Files.size(file) < 1 << 20) {
                picture = new Picture(NativeImage.read(Files.readAllBytes(file)));
                icons.put(host, picture);
                return picture;
            }
        } catch (IOException | RuntimeException e) {
            AllerClient.LOG.debug("Unreadable icon for {}", host, e);
        }
        noIcon.add(host);
        return null;
    }

    /** "www.example.com/page" -> "example.com"; empty if the address has no host. */
    public static String host(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) return "";
            host = host.toLowerCase();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---- bookmarks and history -------------------------------------------------------------------

    public static List<Mark> bookmarks() {
        load();
        return marks;
    }

    public static boolean bookmarked(String url) {
        for (Mark mark : marks) if (mark.url.equals(url)) return true;
        return false;
    }

    public static void toggleBookmark(Tab tab) {
        if (!tab.page()) return;
        if (!marks.removeIf(mark -> mark.url.equals(tab.url))) marks.add(new Mark(tab.url, tab.label()));
        dirty = true;
    }

    public static void removeBookmark(Mark mark) {
        marks.remove(mark);
        dirty = true;
    }

    public static List<Visit> history() {
        load();
        return visits;
    }

    private static void visited(Tab tab) {
        if (tab.secret || !tab.page() || tab.crashed) return;
        String url = tab.url;
        if (!visits.isEmpty() && visits.get(0).url.equals(url)) return;
        visits.removeIf(visit -> visit.url.equals(url));
        visits.add(0, new Visit(url, tab.title, System.currentTimeMillis()));
        while (visits.size() > MAX_VISITS) visits.remove(visits.size() - 1);
        dirty = true;
    }

    public static void forget(Visit visit) {
        visits.remove(visit);
        dirty = true;
    }

    /** Bookmarks, then places been, that the typed text could mean. Typing in a private tab is never remembered. */
    public static List<Mark> suggest(String typed, int limit) {
        List<Mark> out = new ArrayList<>();
        String q = typed.trim().toLowerCase();
        if (q.isEmpty()) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (Mark mark : marks) {
            if (out.size() >= limit) return out;
            if (matches(mark.url, mark.title, q) && seen.add(mark.url)) out.add(mark);
        }
        for (Visit visit : visits) {
            if (out.size() >= limit) return out;
            if (matches(visit.url, visit.title, q) && seen.add(visit.url)) out.add(new Mark(visit.url, visit.title));
        }
        return out;
    }

    private static boolean matches(String url, String title, String q) {
        return url.toLowerCase().contains(q) || title != null && title.toLowerCase().contains(q);
    }

    public static void clearHistory() {
        visits.clear();
        dirty = true;
        save();
    }

    /** Forgets everything: history, site icons, and what sites stored (cookies, sign-ins, cache). Bookmarks stay. */
    public static void clearData() {
        load();
        visits.clear();
        icons.values().forEach(Picture::close);
        icons.clear();
        noIcon.clear();
        deleteTree(dir().resolve("favicons"));
        Tab any = null;
        for (Tab tab : all()) if (tab.view >= 0) any = tab;
        if (any != null) {
            WebNative.action(any.view, WebNative.CLEAR_DATA);
        } else if (WebNative.loaded() && WebNative.state() != WebNative.NONE) {
            // The browser process holds the profile open and no page can ask it to clear: do it at the next start.
            try {
                Files.createDirectories(dir());
                Files.writeString(dir().resolve("clear"), "");
            } catch (IOException e) {
                AllerClient.LOG.warn("Could not schedule the browser profile for clearing", e);
            }
        } else {
            deleteTree(dir().resolve("profile"));
        }
        dirty = true;
        save();
        Toasts.info("Browsing data cleared", "History, cookies, sign-ins and cache");
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> files = Files.walk(root)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // in use; it goes next time
                }
            });
        } catch (IOException e) {
            AllerClient.LOG.warn("Could not clear {}", root, e);
        }
    }

    // ---- links from chat -------------------------------------------------------------------------

    /** A link Minecraft is about to open in the system browser. @return true if this browser takes it instead */
    public static boolean chatLink(URI uri) {
        if (!chat || uri == null || !usable() || !mod().chatLinks.get()) return false;
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return false;
        String url = uri.toString();
        // Minecraft is in the middle of handling the click; change screens once it is done.
        AllerClient.defer(() -> open(null, url));
        return true;
    }

    // ---- files -----------------------------------------------------------------------------------

    private static void load() {
        if (loaded) return;
        loaded = true;
        for (JsonElement e : read("bookmarks.json")) {
            try {
                JsonObject o = e.getAsJsonObject();
                marks.add(new Mark(o.get("url").getAsString(), o.get("title").getAsString()));
            } catch (RuntimeException ignored) {
                // skip an entry that cannot be read
            }
        }
        for (JsonElement e : read("history.json")) {
            try {
                JsonObject o = e.getAsJsonObject();
                visits.add(new Visit(o.get("url").getAsString(), o.get("title").getAsString(), o.get("time").getAsLong()));
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        if (!mod().restore.get()) return;
        for (JsonElement e : read("session.json")) {
            try {
                JsonObject o = e.getAsJsonObject();
                Tab tab = new Tab(false);
                tab.url = o.get("url").getAsString();
                tab.title = o.get("title").getAsString();
                if (!tab.page()) continue;
                open.add(tab);
                if (o.has("active") && o.get("active").getAsBoolean()) activeOpen = tab;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
    }

    private static JsonArray read(String name) {
        Path file = dir().resolve(name);
        try {
            if (Files.exists(file)) {
                JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (e.isJsonArray()) return e.getAsJsonArray();
            }
        } catch (Exception e) {
            AllerClient.LOG.warn("Could not read {}", file, e);
        }
        return new JsonArray();
    }

    /** Writes bookmarks, history and the open tabs. Private tabs are in none of them. */
    public static void save() {
        if (!loaded) return;
        dirty = false;
        savedAt = now();
        JsonArray bookmarks = new JsonArray(), history = new JsonArray(), session = new JsonArray();
        for (Mark mark : marks) {
            JsonObject o = new JsonObject();
            o.addProperty("url", mark.url);
            o.addProperty("title", mark.title);
            bookmarks.add(o);
        }
        for (Visit visit : visits) {
            JsonObject o = new JsonObject();
            o.addProperty("url", visit.url);
            o.addProperty("title", visit.title == null ? "" : visit.title);
            o.addProperty("time", visit.time);
            history.add(o);
        }
        for (Tab tab : open) {
            if (!tab.page()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("url", tab.url);
            o.addProperty("title", tab.title);
            o.addProperty("active", tab == activeOpen);
            session.add(o);
        }
        write("bookmarks.json", bookmarks);
        write("history.json", history);
        write("session.json", session);
    }

    private static void write(String name, JsonArray json) {
        Path file = dir().resolve(name);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(name + ".tmp");
            Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AllerClient.LOG.warn("Could not save {}", file, e);
        }
    }

    public static void shutdown() {
        save();
        if (WebNative.loaded()) WebNative.shutdown();
    }
}
