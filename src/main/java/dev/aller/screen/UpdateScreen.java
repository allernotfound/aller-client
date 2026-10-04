package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.feature.Updater;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.doc.Doc;
import dev.aller.ui.doc.DocView;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Scroll;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.List;
import java.util.Locale;

/**
 * The update popup, over the main menu or the pause menu: which version there is and its release
 * notes, then the download and the restart it ends in. Once the download is done the only way out
 * is to close the game. The same panel shows the notes of what was installed, once, after an update.
 */
public final class UpdateScreen extends AllerScreen {
    private static final float W = 330, HEAD = 44, FOOT = 38, PAD = 15, BUTTON_H = 20;

    private final Screen parent;
    /** The notes of the version now running, or null when this is the offer of a newer one. */
    private final List<Updater.Note> news;
    private final DocView notes;
    private final Scroll scroll = new Scroll();
    private final Button later = new Button("Later", this::close);
    private final Button update = new Button("Update now", Updater::download).style(Button.Style.PRIMARY);
    private final Button retry = new Button("Try again", Updater::download).style(Button.Style.PRIMARY);
    private final Button quit = new Button("Close the game", Nav::quit).style(Button.Style.PRIMARY);
    private final Button done = new Button("Done", this::close).style(Button.Style.PRIMARY);
    private final Spring bar = Spring.smooth(0);
    private List<Button> shown = List.of();
    private float px, py, pw, ph;

    private static Screen settled;
    private static int settledFor;

    private UpdateScreen(Screen parent, List<Updater.Note> news) {
        this.parent = parent;
        this.news = news;
        Updater.Update u = Updater.update();
        List<Updater.Note> list = news != null ? news : u != null ? u.notes() : List.of();
        notes = new DocView(Doc.markdown(markdown(list), Updater.SITE));
    }

    /** Once a tick: brings the popup up by itself, but only on the main menu or the pause menu, and once that has settled. */
    public static void offer() {
        if (!Updater.wanted() && !Updater.hasNews()) return;
        Screen s = Mc.screen();
        if (!menu(s) || Mc.loadingOverlay()) {
            settled = null;
            return;
        }
        if (s != settled) {
            settled = s;
            settledFor = 0;
        }
        if (++settledFor > 16) show();
    }

    private static boolean menu(Screen s) {
        AllerScreen a = Mc.current();
        if (a != null) return (a instanceof MainMenuScreen || a instanceof PauseMenuScreen) && !a.isClosing();
        return s instanceof TitleScreen || s instanceof PauseScreen pause && pause.showsPauseMenu();
    }

    /** Opens the popup over whatever is on screen. What was installed last time comes before what could be installed next. */
    public static void show() {
        Screen under = Mc.current() instanceof UpdateScreen open ? open.parent : Mc.screen();
        settled = null;
        Mc.setScreen(new ScreenHost(new UpdateScreen(under, Updater.takeNews())));
    }

    private static String markdown(List<Updater.Note> list) {
        StringBuilder out = new StringBuilder();
        for (Updater.Note n : list) {
            // One release needs no heading: the panel's own says which it is.
            if (list.size() > 1) out.append("## ").append(n.version()).append("\n\n");
            out.append(n.body().isBlank() ? "*No notes were written for this release.*" : n.body()).append("\n\n");
        }
        return out.toString();
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

    /** With the new jar in place the game has to be closed; before that Escape is "Later". */
    @Override
    public boolean closeOnEscape() {
        return news != null || Updater.state() != Updater.State.READY;
    }

    @Override
    public void close() {
        if (news == null && Updater.state() != Updater.State.DOWNLOADING) Updater.later();
        close(() -> Mc.setScreen(parent));
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        Updater.State state = Updater.state();
        Updater.Update u = Updater.update();
        if (news == null && (u == null || state == Updater.State.IDLE)) {
            // The release was withdrawn while the popup was up.
            close(() -> Mc.setScreen(parent));
            return;
        }

        float open = openness(), fade = fade();
        pw = Math.min(W, width - 24);
        float docH = notes.empty() ? 0 : notes.height(pw - PAD * 2);
        float view = Math.clamp(docH + PAD * 2, 54, Math.max(54, Math.min(230, height - 24 - HEAD - FOOT)));
        ph = HEAD + view + FOOT;
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        if (Mc.mc().level == null && parent == null) Theme.scene(c, width, height);
        Theme.veil(c, width, height, fade, 0.42f);
        c.pushAlpha(fade);
        c.push();
        c.scale(0.94f + 0.06f * open, width / 2, height / 2);
        c.translate(0, (1 - open) * 10);
        if (isClosing()) mx = my = -1000;
        Theme.panel(c, px, py, pw, ph, Theme.R_LG);

        String title, sub;
        if (news != null) {
            title = "What's new";
            sub = AllerClient.NAME + " was updated to " + AllerClient.VERSION;
        } else {
            title = switch (state) {
                case DOWNLOADING -> "Downloading the update";
                case READY -> "Restart to finish";
                case FAILED -> "The update failed";
                default -> "Update available";
            };
            sub = AllerClient.NAME + " " + u.version() + " for Minecraft " + Updater.target()
                    + (u.size() > 0 ? "  •  " + String.format(Locale.ROOT, "%.1f MB", u.size() / 1e6) : "")
                    + "  •  you have " + Updater.running();
        }
        c.text(Fonts.BOLD, title, px + PAD, py + 11, 13, Theme.TEXT);
        float dot = px + PAD + Fonts.BOLD.width(title, 13) + 2.5f;
        c.circle(dot, py + 22, 1.5f, Theme.accent());
        c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(sub, 7.5f, pw - PAD * 2), px + PAD, py + 28, 7.5f, Theme.TEXT_DIM);
        c.rect(px + 1, py + HEAD - 0.5f, pw - 2, 0.5f, 0, Theme.BORDER);

        float top = py + HEAD, off = scroll.update(docH + PAD * 2, view);
        c.clip(px, top, pw, view);
        if (notes.empty()) c.text(Fonts.REGULAR, "No notes were written for this release.", px + PAD, top + PAD, 8, Theme.TEXT_MUTED);
        else notes.draw(c, px + PAD, top + PAD - off, pw - PAD * 2, top, top + view, mx, my);
        c.unclip();
        scroll.drawBar(c, px + pw - 5, top + 2, view - 4);
        c.rect(px + 1, top + view, pw - 2, 0.5f, 0, Theme.BORDER);

        drawFooter(c, top + view, state, mx, my);
        c.pop();
        c.popAlpha();
        Toasts.draw(c);
    }

    private void drawFooter(Canvas c, float y, Updater.State state, float mx, float my) {
        float mid = y + (FOOT - BUTTON_H) / 2;
        if (news == null && state == Updater.State.DOWNLOADING) {
            shown = List.of();
            float p = bar.target(Updater.progress()).update(), bw = pw - PAD * 2;
            c.text(Fonts.MEDIUM, "Downloading", px + PAD, y + 9, 7.5f, Theme.TEXT_DIM);
            c.textRight(Fonts.MEDIUM, Math.round(p * 100) + "%", px + pw - PAD, y + 9, 7.5f, Theme.TEXT_DIM);
            c.rect(px + PAD, y + 23, bw, 4, 2, Colors.withAlpha(Colors.WHITE, 0.08f));
            if (p > 0.01f) Theme.accentFill(c, px + PAD, y + 23, Math.max(4, bw * Math.clamp(p, 0f, 1f)), 4, 2);
            return;
        }

        String said;
        int color = Theme.TEXT_MUTED;
        if (news != null) {
            shown = List.of(done);
            said = "";
        } else if (state == Updater.State.READY) {
            shown = List.of(quit);
            said = "The new version takes over the next time you start the game." + (Mc.mc().level != null ? " Your world is saved first." : "");
        } else if (state == Updater.State.FAILED) {
            shown = List.of(retry, later);
            said = Updater.problem();
            color = Theme.WARN;
        } else {
            shown = List.of(update, later);
            said = "From github.com/" + Updater.REPO + ". Nothing is downloaded until you choose to.";
        }
        // Right to left, the main action at the end.
        float x = px + pw - PAD;
        for (Button b : shown) {
            b.textSize = 8.5f;
            float bw = Fonts.SEMIBOLD.width(b.label, b.textSize) + 24;
            x -= bw;
            b.bounds(x, mid, bw, BUTTON_H);
            b.draw(c, mx, my);
            x -= 6;
        }
        List<String> lines = Fonts.REGULAR.wrap(said, 6.8f, x - px - PAD - 4);
        int n = Math.min(lines.size(), 3);
        float lh = 9, ty = y + (FOOT - n * lh) / 2 + 1;
        for (int i = 0; i < n; i++) c.text(Fonts.REGULAR, lines.get(i), px + PAD, ty + i * lh, 6.8f, color);
    }

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        for (Button b : shown) if (b.mouseDown(x, y, button)) return true;
        if (button == 0 && notes.hoveredLink() != null && y >= py + HEAD && y < py + ph - FOOT) {
            if (!Nav.openUrl(notes.hoveredLink())) Toasts.warn("Could not open the link", notes.hoveredLink());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        boolean used = false;
        for (Button b : List.copyOf(shown)) used |= b.mouseUp(x, y, button);
        return used;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        scroll.scroll(amount);
        return true;
    }
}
