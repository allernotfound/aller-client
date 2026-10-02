package dev.aller.screen.store;

import dev.aller.feature.store.Kind;
import dev.aller.feature.store.Modrinth;
import dev.aller.feature.store.Modrinth.Project;
import dev.aller.feature.store.Store;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.IconButton;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;

/**
 * The store: a full page for finding things on Modrinth and putting them in the game's folder.
 * Browse (search, filters, a grid or rows of results) and Installed (what is in the folder, with
 * updates) are its two tabs; picking a project slides its page in over either. It is written
 * against a {@link Kind}, so only where it is opened from says it is about resource packs.
 */
public final class StoreScreen extends AllerScreen {
    static final float HEADER = 34, MARGIN = 10;

    private enum Tab { BROWSE, INSTALLED }

    /** The screen the last store was opened from: downloads stay marked as new for as long as that one is around. */
    private static WeakReference<Screen> lastParent = new WeakReference<>(null);

    final Kind kind;
    private final Screen parent;
    private final BrowsePane browse;
    private final InstalledPane installed;
    private DetailPane detail;
    private Tab tab = Tab.BROWSE;
    private final Spring page = Spring.smooth(0), tabSlide = Spring.snappy(0), creditHover = Spring.snappy(0);
    private final Spring[] tabHover = {Spring.snappy(0), Spring.snappy(0)};
    private final IconButton back = new IconButton(Icons.BACK, "Back", this::leave);
    private final IconButton closeButton = new IconButton(Icons.CLOSE, "Close", this::close);
    private final int changesAtOpen;
    private float tabsX, tabsW, creditX, creditW;

    public StoreScreen(Screen parent, Kind kind) {
        this.parent = parent;
        this.kind = kind;
        if (lastParent.get() != parent) Store.settle();
        lastParent = new WeakReference<>(parent);
        changesAtOpen = Store.changes();
        browse = new BrowsePane(this);
        installed = new InstalledPane(this);
    }

    /** Opens the store over the game's list of what it adds to. */
    public static void open(Screen list, Kind kind) {
        Mc.setScreen(new ScreenHost(new StoreScreen(list, kind)));
    }

    @Override
    public void opened() {
        super.opened();
        Store.scan(kind);
        browse.shown();
    }

    @Override
    public void reshown() {
        front().shown();
    }

    @Override
    public void close() {
        close(() -> {
            Mc.setScreen(parent);
            // The list underneath reads its folder again at once, so what was fetched is there to see.
            if (Store.changes() != changesAtOpen) PackListExtras.refresh(parent, kind);
        });
    }

    @Override
    public boolean closeOnEscape() {
        return false;
    }

    @Override
    public boolean capturing() {
        return front().typing();
    }

    // ---- moving about ----------------------------------------------------------------------------

    private Pane list() {
        return tab == Tab.BROWSE ? browse : installed;
    }

    private Pane front() {
        return detail != null && page.target() > 0.5f ? detail : list();
    }

    void open(Project project) {
        detail = new DetailPane(this, project);
        page.target(1);
    }

    /** One step back: out of a project's page, or out of the store. */
    private void leave() {
        if (detail != null && page.target() > 0.5f) {
            page.target(0);
            list().shown();
        } else {
            close();
        }
    }

    private void show(Tab next) {
        if (detail != null && page.target() > 0.5f) page.target(0);
        if (tab != next) {
            tab = next;
            list().shown();
        }
    }

    /** Follows a link from a page: in Aller's browser if that is on, otherwise the system's. */
    void link(String url) {
        if (url == null) return;
        if (dev.aller.feature.Browser.usable()) dev.aller.feature.Browser.open(Mc.screen(), url);
        else if (!Nav.openUrl(url)) Toasts.warn("Could not open the link", url);
    }

    float screenWidth() {
        return width;
    }

    float screenHeight() {
        return height;
    }

    /** Stands in for the clicks the self-test cannot make. */
    public void dev(String action) {
        switch (action) {
            case "rows" -> browse.view(false);
            case "grid" -> browse.view(true);
            case "open" -> {
                Project first = browse.first();
                if (first != null) open(first);
            }
            case "description" -> detail(0);
            case "gallery" -> detail(1);
            case "versions" -> {
                detail(2);
                if (detail != null) detail.expand();
            }
            case "picture" -> {
                if (detail != null) detail.enlarge();
            }
            case "download" -> {
                if (detail != null) detail.download();
            }
            case "installed" -> show(Tab.INSTALLED);
            case "tidy" -> {
                // Take back what the run fetched.
                for (Store.Installed entry : java.util.List.copyOf(Store.installed(kind))) {
                    if (Store.fresh(entry.filename)) Store.remove(kind, entry);
                }
            }
            default -> {}
        }
    }

    private void detail(int index) {
        if (detail != null) detail.show(index);
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void draw(Canvas c, float mx, float my) {
        float open = openness(), fade = fade();
        if (Mc.mc().level == null) Theme.scene(c, width, height);
        Theme.veil(c, width, height, fade, 0.5f);
        c.pushAlpha(fade);
        c.push();
        c.translate(0, (1 - open) * 12);

        float t = Math.clamp(page.update(), 0f, 1f);
        if (detail != null && t < 0.01f && page.target() < 0.5f) detail = null;
        header(c, mx, my, t);

        float bx = MARGIN, by = HEADER + 4, bw = width - MARGIN * 2, bh = height - by - MARGIN;
        Pane under = list();
        under.bounds(bx, by, bw, bh);
        if (t < 0.99f) {
            c.pushAlpha(1 - t);
            c.push();
            c.translate(-16 * t, 0);
            boolean live = detail == null || page.target() < 0.5f;
            under.draw(c, live ? mx : -10000, live ? my : -10000);
            c.pop();
            c.popAlpha();
        }
        if (detail != null) {
            detail.bounds(bx, by, bw, bh);
            c.pushAlpha(t);
            c.push();
            c.translate(16 * (1 - t), 0);
            boolean live = page.target() > 0.5f;
            detail.draw(c, live ? mx : -10000, live ? my : -10000);
            c.pop();
            c.popAlpha();
        }
        front().overlay(c, mx, my);
        closeButton.drawTip(c, false);
        back.drawTip(c, true);
        c.pop();
        c.popAlpha();
        Toasts.draw(c);
    }

    private void header(Canvas c, float mx, float my, float t) {
        float cy = 7, bh = 20;
        back.label = detail != null && page.target() > 0.5f ? "Back to the list" : "Back to your " + kind.noun + "s";
        back.bounds(MARGIN, cy, bh, bh);
        back.draw(c, mx, my);
        float tx = MARGIN + bh + 8;
        float tw = c.text(Fonts.BOLD, kind.title, tx, cy + 4.2f, 12, Theme.TEXT);

        // Browse and Installed, as one switch.
        String[] names = {"Browse", installedLabel()};
        float[] widths = new float[2];
        tabsW = 0;
        for (int i = 0; i < 2; i++) {
            widths[i] = Fonts.MEDIUM.width(names[i], 8.2f) + 18;
            tabsW += widths[i];
        }
        tabsX = tx + tw + 14;
        c.rect(tabsX, cy, tabsW, bh, bh / 2, 0x33000000);
        c.stroke(tabsX, cy, tabsW, bh, bh / 2, 1, Theme.BORDER);
        float slide = tabSlide.target(tab == Tab.BROWSE ? 0 : 1).update();
        float pillW = widths[0] + (widths[1] - widths[0]) * slide;
        Theme.accentFill(c, tabsX + 2 + slide * widths[0], cy + 2, pillW - 4, bh - 4, (bh - 4) / 2);
        float at = tabsX;
        for (int i = 0; i < 2; i++) {
            float hv = tabHover[i].target(inside(mx, my, at, cy, widths[i], bh) ? 1 : 0).update();
            float on = Math.clamp(i == 0 ? 1 - slide : slide, 0f, 1f);
            int color = Colors.mix(Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv), Theme.onAccent(), on);
            c.textMiddle(Fonts.MEDIUM, names[i], at + 9, cy, bh, 8.2f, color);
            at += widths[i];
        }

        closeButton.bounds(width - MARGIN - bh, cy, bh, bh);
        closeButton.draw(c, mx, my);

        // Where it all comes from.
        String credit = "From Modrinth";
        creditW = Fonts.MEDIUM.width(credit, 7.6f) + 26;
        creditX = closeButton.x - 8 - creditW;
        if (creditX > tabsX + tabsW + 8) {
            float hv = creditHover.target(inside(mx, my, creditX, cy, creditW, bh) ? 1 : 0).update();
            c.rect(creditX, cy, creditW, bh, bh / 2, Colors.withAlpha(Bits.MODRINTH, 0.10f + 0.08f * hv));
            c.stroke(creditX, cy, creditW, bh, bh / 2, 1, Colors.withAlpha(Bits.MODRINTH, 0.35f + 0.3f * hv));
            c.circle(creditX + 10, cy + bh / 2, 2.4f, Bits.MODRINTH);
            c.textMiddle(Fonts.MEDIUM, credit, creditX + 17, cy, bh, 7.6f, Colors.mix(Bits.MODRINTH, Colors.WHITE, 0.45f));
        } else {
            creditW = 0;
        }
    }

    private String installedLabel() {
        int updates = Store.updates(kind);
        return updates > 0 ? "Installed  •  " + updates + (updates == 1 ? " update" : " updates") : "Installed";
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        if (back.mouseDown(x, y, button) || closeButton.mouseDown(x, y, button)) return true;
        if (button == 0 && y < HEADER) {
            if (inside(x, y, tabsX, 7, tabsW, 20)) {
                float first = Fonts.MEDIUM.width("Browse", 8.2f) + 18;
                dev.aller.platform.Sounds.click();
                show(x < tabsX + first ? Tab.BROWSE : Tab.INSTALLED);
                return true;
            }
            if (creditW > 0 && inside(x, y, creditX, 7, creditW, 20)) {
                link(Modrinth.SITE + "/" + kind.type + "s");
                return true;
            }
        }
        // The mouse's own back button.
        if (button == 3) {
            leave();
            return true;
        }
        front().mouseDown(x, y, button);
        return true;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        back.mouseUp(x, y, button);
        closeButton.mouseUp(x, y, button);
        browse.mouseUp(x, y, button);
        installed.mouseUp(x, y, button);
        if (detail != null) detail.mouseUp(x, y, button);
        return true;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        front().scroll(x, y, amount);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (isClosing()) return false;
        if (front().keyDown(key, mods)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            leave();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        return !isClosing() && front().charTyped(codepoint);
    }
}
