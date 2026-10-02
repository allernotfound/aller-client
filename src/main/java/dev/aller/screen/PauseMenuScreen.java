package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.feature.Session;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Skins;
import dev.aller.screen.palette.SettingsPage;
import dev.aller.screen.palette.StatsPage;
import dev.aller.screen.palette.WaypointsPage;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Tween;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.IconButton;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game pause menu: the same left column as the main menu, over the blurred world, with a
 * card summarising the session so far. Less common entries sit in a strip of icon buttons beside
 * the column.
 */
public final class PauseMenuScreen extends AllerScreen {
    private static final float COLUMN = 232, CARD_H = 74, ROW = 26, BUTTON_H = 22, ICON = 20, ICON_GAP = 5, STRIP = 28, SEGMENT_GAP = 4;

    private final List<Button> buttons = new ArrayList<>();
    /** The button stack, a line at a time: most lines hold one button, the mods line is split. */
    private final List<Button[]> rows = new ArrayList<>();
    private final List<float[]> shares = new ArrayList<>();
    /** Fades the menu out before handing over to a vanilla screen. */
    private final Handover handover = new Handover(this);
    private final List<IconButton> icons = new ArrayList<>();
    /** Another mod's destinations (Essential's), in a strip on the other side of the column. */
    private final List<IconButton> extras = new ArrayList<>();
    private final List<Tween> buttonIn = new ArrayList<>();
    private final Tween cardIn = new Tween(0.45f, Easing.OUT_EXPO);
    private float leftX, topY, colW, k = 1;

    public PauseMenuScreen() {
        boolean local = Mc.mc().isLocalServer();
        add("Back to game", this::close).style(Button.Style.PRIMARY).hint("esc");
        row(new float[] {0.3f, 0.26f, 0.22f, 0.22f},
                segment("Mods", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen())))),
                segment("Layout", () -> Mc.setScreen(new ScreenHost(new HudEditorScreen(Mc.screen())))),
                segment("Client", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen(), new SettingsPage())))).icon(Icons.SETTINGS),
                segment("UI", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen(), SettingsPage.ui())))).icon(Icons.SETTINGS));
        add("Waypoints", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen(), new WaypointsPage()))));
        add("Statistics", () -> go(() -> Nav.statistics(Mc.screen())));
        add("Options", () -> go(() -> Nav.options(Mc.screen())));
        add(dev.aller.feature.Pocket.inside() ? "Leave the pocket" : local ? "Save and quit to title" : "Disconnect", Nav::disconnect).style(Button.Style.DANGER);

        icons.add(new IconButton(Icons.ADVANCEMENTS, "Advancements", () -> go(() -> Nav.advancements(Mc.screen()))));
        if (Nav.canOpenLan()) icons.add(new IconButton(Icons.LAN, "Open to LAN", () -> go(() -> Nav.lan(Mc.screen()))));
        if (!local) icons.add(new IconButton(Icons.REPORT, "Player reporting", () -> go(() -> Nav.playerReporting(Mc.screen()))));
        if (Nav.hasModMenu()) icons.add(new IconButton(Icons.MODS, "Installed mods", () -> go(() -> Nav.mods(Mc.screen()))));
        icons.add(new IconButton(Icons.FEEDBACK, "Give feedback", () -> go(() -> Nav.feedback(Mc.screen()))));
        icons.add(new IconButton(Icons.BUG, "Report a bug", () -> go(() -> Nav.reportBug(Mc.screen()))));

        extras.addAll(dev.aller.compat.EssentialCompat.buttons(false, this::go));

        for (int i = 0; i < rows.size(); i++) {
            buttonIn.add(new Tween(0.4f, Easing.OUT_EXPO).delay(0.05f + i * 0.035f));
        }
    }

    private Button add(String label, Runnable action) {
        Button b = new Button(label, action).left();
        b.textSize = 9.2f;
        row(new float[] {1f}, b);
        return b;
    }

    /** One of several buttons sharing a line. */
    private static Button segment(String label, Runnable action) {
        Button b = new Button(label, action);
        b.textSize = 8.2f;
        return b;
    }

    private void row(float[] share, Button... line) {
        rows.add(line);
        shares.add(share);
        buttons.addAll(List.of(line));
    }

    /** Hands over to a Minecraft screen opened by {@code action}. */
    public void go(Runnable action) {
        handover.go(action);
    }

    @Override
    public void opened() {
        super.opened();
        handover.reset();
    }

    @Override
    public void reshown() {
        handover.back();
    }

    @Override
    protected void layout() {
        float columnH = 30 + CARD_H + 10 + rows.size() * ROW;
        k = Math.clamp(Math.min(height / (columnH + 30), width / 360f), 0.5f, 1f);
        float vw = width / k, vh = height / k;
        colW = Math.min(COLUMN, vw - 40 - STRIP - (extras.isEmpty() ? 0 : STRIP));
        leftX = Math.max(20 + STRIP, Math.min(vw * 0.085f + STRIP, (vw - colW) / 2));
        topY = Math.max(10, (vh - columnH) / 2);
    }

    @Override
    public boolean pausesGame() {
        return true;
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        float fade = fade(), open = openness();
        float lv = handover.update();
        // Drawn over the screen it is leaving for, its shading goes with its content.
        float shade = drawingLeaving ? 1 - lv : fade;
        Theme.veil(c, width, height, shade, 0.30f);
        // Darken towards the column so it has contrast over any scene.
        if (AllerClient.options().background.get() != dev.aller.ClientOptions.Background.NONE) {
            c.gradientH(0, 0, width * 0.65f, height, 0, Colors.withAlpha(0xFF07060B, 0.75f * shade), 0x0007060B);
        }

        c.push();
        c.scale(k, 0, 0);
        mx /= k;
        my /= k;
        if (isClosing() || handover.leaving()) mx = -1000;
        c.pushAlpha(fade * (1 - lv));
        c.push();
        c.translate(-18 * (1 - open) - 14 * lv, 0);

        float y = topY;
        c.text(Fonts.BOLD, "Paused", leftX, y, 20, Theme.TEXT);
        float dotX = leftX + Fonts.BOLD.width("Paused", 20) + 3;
        c.shadow(dotX - 2, y + 15, 4, 4, 2, 7, Colors.withAlpha(Theme.accent(), 0.7f));
        c.circle(dotX, y + 17, 2, Theme.accent());
        y += 30;

        float cd = cardIn.update();
        c.pushAlpha(cd);
        drawCard(c, leftX - (1 - cd) * 22, y);
        c.popAlpha();
        y += CARD_H + 10;

        float buttonsTop = y;
        for (int i = 0; i < rows.size(); i++) {
            float t = buttonIn.get(i).update();
            Button[] line = rows.get(i);
            float room = colW - SEGMENT_GAP * (line.length - 1), bx = leftX - (1 - t) * 22;
            c.pushAlpha(t);
            for (int j = 0; j < line.length; j++) {
                float bw = room * shares.get(i)[j];
                line[j].bounds(bx, y, bw, BUTTON_H);
                line[j].draw(c, mx, my);
                bx += bw + SEGMENT_GAP;
            }
            c.popAlpha();
            y += ROW;
        }

        // Icon strip, centred against the button stack.
        float stripH = icons.size() * ICON + (icons.size() - 1) * ICON_GAP;
        float iy = buttonsTop + (rows.size() * ROW - (ROW - BUTTON_H) - stripH) / 2;
        for (int i = 0; i < icons.size(); i++) {
            float t = buttonIn.get(Math.min(i, buttonIn.size() - 1)).get();
            IconButton b = icons.get(i);
            b.bounds(leftX - STRIP - (1 - t) * 14, iy + i * (ICON + ICON_GAP), ICON, ICON);
            c.pushAlpha(t);
            b.draw(c, mx, my);
            c.popAlpha();
        }
        drawExtras(c, buttonsTop, mx, my);
        for (IconButton b : icons) b.drawTip(c, true);
        for (IconButton b : extras) b.drawTip(c, true);
        c.pop();
        c.popAlpha();
        c.pop();
        if (!drawingLeaving) Toasts.draw(c);
    }

    /** The strip to the right of the column, centred against the button stack like the one on its left. */
    private void drawExtras(Canvas c, float buttonsTop, float mx, float my) {
        float stripH = extras.size() * ICON + (extras.size() - 1) * ICON_GAP;
        float iy = buttonsTop + (rows.size() * ROW - (ROW - BUTTON_H) - stripH) / 2;
        for (int i = 0; i < extras.size(); i++) {
            float t = buttonIn.get(Math.min(i, buttonIn.size() - 1)).get();
            IconButton b = extras.get(i);
            b.bounds(leftX + colW + STRIP - ICON + (1 - t) * 14, iy + i * (ICON + ICON_GAP), ICON, ICON);
            c.pushAlpha(t);
            b.draw(c, mx, my);
            c.popAlpha();
        }
    }

    private void drawCard(Canvas c, float x, float y) {
        Theme.panel(c, x, y, colW, CARD_H, Theme.R_LG);
        int face = 28;
        float fx = x + 11, fy = y + 10;
        Skins.drawFramedFace(c, fx, fy, face);

        float tx = fx + face + 10;
        String address = Game.serverAddress();
        String where = address != null ? address : "Singleplayer";
        c.text(Fonts.BOLD, Fonts.BOLD.truncate(Nav.playerName(), 12, x + colW - tx - 10), tx, y + 9, 12, Theme.TEXT);
        c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(where + "  •  " + Game.pretty(Game.dimensionId()), 7.5f, x + colW - tx - 10),
                tx, y + 25, 7.5f, Theme.TEXT_DIM);

        c.rect(x + 10, y + 46, colW - 20, 1, 0, Theme.BORDER);
        Session.Live live = Session.current();
        String[][] stats = {
                {"PLAYED", StatsPage.duration(Session.seconds())},
                {"FPS", String.valueOf(Mc.mc().getFps())},
                {"K / D", live.kills + " / " + live.deaths},
                {"PROFILE", AllerClient.config().activeProfile()},
        };
        float sw = (colW - 20) / stats.length;
        for (int i = 0; i < stats.length; i++) {
            float sx = x + 11 + i * sw;
            c.text(Fonts.MEDIUM, stats[i][0], sx, y + 51, 5.6f, Theme.TEXT_MUTED);
            c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(stats[i][1], 8.5f, sw - 4), sx, y + 59, 8.5f, Theme.TEXT);
        }
    }

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing() || handover.leaving()) return false;
        for (Button b : buttons) if (b.mouseDown(x / k, y / k, button)) return true;
        for (IconButton b : icons) if (b.mouseDown(x / k, y / k, button)) return true;
        for (IconButton b : extras) if (b.mouseDown(x / k, y / k, button)) return true;
        return false;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        boolean used = false;
        for (Button b : buttons) used |= b.mouseUp(x / k, y / k, button);
        for (IconButton b : icons) used |= b.mouseUp(x / k, y / k, button);
        for (IconButton b : extras) used |= b.mouseUp(x / k, y / k, button);
        return used;
    }
}
