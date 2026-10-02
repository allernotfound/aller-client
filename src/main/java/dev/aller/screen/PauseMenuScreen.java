package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.feature.Session;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Skins;
import dev.aller.screen.palette.StatsPage;
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
    private static final float COLUMN = 232, CARD_H = 74, ROW = 26, BUTTON_H = 22, ICON = 20, ICON_GAP = 5, STRIP = 28;

    private final List<Button> buttons = new ArrayList<>();
    private final List<IconButton> icons = new ArrayList<>();
    private final List<Tween> buttonIn = new ArrayList<>();
    private final Tween cardIn = new Tween(0.45f, Easing.OUT_EXPO);
    private float leftX, topY, colW, k = 1;

    public PauseMenuScreen() {
        boolean local = Mc.mc().isLocalServer();
        add("Back to game", this::close).style(Button.Style.PRIMARY).hint("esc");
        add("Mods", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen()))))
                .hint(Mc.keyName(AllerClient.options().menuKey.get()));
        add("HUD", () -> Mc.setScreen(new ScreenHost(new HudEditorScreen(Mc.screen())))).hint("edit layout");
        add("Statistics", () -> Nav.statistics(Mc.screen()));
        add("Options", () -> Nav.options(Mc.screen()));
        add(local ? "Save and quit to title" : "Disconnect", Nav::disconnect).style(Button.Style.DANGER);

        icons.add(new IconButton(Icons.ADVANCEMENTS, "Advancements", () -> Nav.advancements(Mc.screen())));
        if (Nav.canOpenLan()) icons.add(new IconButton(Icons.LAN, "Open to LAN", () -> Nav.lan(Mc.screen())));
        if (!local) icons.add(new IconButton(Icons.REPORT, "Player reporting", () -> Nav.playerReporting(Mc.screen())));
        if (Nav.hasModMenu()) icons.add(new IconButton(Icons.MODS, "Installed mods", () -> Nav.mods(Mc.screen())));
        icons.add(new IconButton(Icons.FEEDBACK, "Give feedback", () -> Nav.feedback(Mc.screen())));
        icons.add(new IconButton(Icons.BUG, "Report a bug", () -> Nav.reportBug(Mc.screen())));

        for (int i = 0; i < buttons.size(); i++) {
            buttonIn.add(new Tween(0.4f, Easing.OUT_EXPO).delay(0.05f + i * 0.035f));
        }
    }

    private Button add(String label, Runnable action) {
        Button b = new Button(label, action).left();
        b.textSize = 9.2f;
        buttons.add(b);
        return b;
    }

    @Override
    protected void layout() {
        float columnH = 30 + CARD_H + 10 + buttons.size() * ROW;
        k = Math.clamp(Math.min(height / (columnH + 30), width / 360f), 0.5f, 1f);
        float vw = width / k, vh = height / k;
        colW = Math.min(COLUMN, vw - 40 - STRIP);
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
        // Darken towards the column so it has contrast over any scene.
        c.rect(0, 0, width, height, 0, Colors.withAlpha(0xFF050409, 0.30f * fade));
        c.gradientH(0, 0, width * 0.65f, height, 0, Colors.withAlpha(0xFF07060B, 0.75f * fade), 0x0007060B);

        c.push();
        c.scale(k, 0, 0);
        mx /= k;
        my /= k;
        if (isClosing()) mx = -1000;
        c.pushAlpha(fade);
        c.push();
        c.translate(-18 * (1 - open), 0);

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
        for (int i = 0; i < buttons.size(); i++) {
            float t = buttonIn.get(i).update();
            Button b = buttons.get(i);
            b.bounds(leftX - (1 - t) * 22, y, colW, BUTTON_H);
            c.pushAlpha(t);
            b.draw(c, mx, my);
            c.popAlpha();
            y += ROW;
        }

        // Icon strip, centred against the button stack.
        float stripH = icons.size() * ICON + (icons.size() - 1) * ICON_GAP;
        float iy = buttonsTop + (buttons.size() * ROW - (ROW - BUTTON_H) - stripH) / 2;
        for (int i = 0; i < icons.size(); i++) {
            float t = buttonIn.get(Math.min(i, buttonIn.size() - 1)).get();
            IconButton b = icons.get(i);
            b.bounds(leftX - STRIP - (1 - t) * 14, iy + i * (ICON + ICON_GAP), ICON, ICON);
            c.pushAlpha(t);
            b.draw(c, mx, my);
            c.popAlpha();
        }
        for (IconButton b : icons) b.drawTip(c, true);
        c.pop();
        c.popAlpha();
        c.pop();
        Toasts.draw(c);
    }

    private void drawCard(Canvas c, float x, float y) {
        Theme.panel(c, x, y, colW, CARD_H, Theme.R_LG);
        int face = 28;
        float fx = x + 11, fy = y + 10;
        c.rect(fx - 2, fy - 2, face + 4, face + 4, 5, 0x33000000);
        Skins.drawOwnFace(c, fx, fy, face);
        c.stroke(fx - 2, fy - 2, face + 4, face + 4, 5, 1.5f, Colors.withAlpha(Theme.accent(), 0.75f));

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
        if (isClosing()) return false;
        for (Button b : buttons) if (b.mouseDown(x / k, y / k, button)) return true;
        for (IconButton b : icons) if (b.mouseDown(x / k, y / k, button)) return true;
        return false;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        boolean used = false;
        for (Button b : buttons) used |= b.mouseUp(x / k, y / k, button);
        for (IconButton b : icons) used |= b.mouseUp(x / k, y / k, button);
        return used;
    }
}
