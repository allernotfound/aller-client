package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Skins;
import dev.aller.platform.Sounds;
import dev.aller.screen.palette.SettingsPage;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Icons;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.anim.Tween;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.IconButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The title screen: an animated ASCII noise field with a single column on the left holding the
 * wordmark, a profile card (the player's own face, name and setup) and the actions, each with a
 * live detail on its right edge. The first time it appears after launch it plays a staged intro;
 * later visits use a quicker fade.
 */
public final class MainMenuScreen extends AllerScreen {
    private static final int[] ACCENTS = {0xFF8B5CF6, 0xFF38BDF8, 0xFF34D399, 0xFFFB7185, 0xFFF59E0B, 0xFFE2E8F0};
    private static final float COLUMN = 232, CARD_H = 74, ROW = 27, BUTTON_H = 23;
    private static boolean introPlayed;

    private static final float ICON = 20, ICON_GAP = 5, STRIP = 28, SEGMENT_GAP = 4, CARD_ICON = 15;
    private final List<Button> buttons = new ArrayList<>();
    /** The button stack, a line at a time: most lines hold one button, the mods line is split. */
    private final List<Button[]> rows = new ArrayList<>();
    private final List<float[]> shares = new ArrayList<>();
    private final List<IconButton> icons = new ArrayList<>();
    /** Small square buttons in the corner of the profile card, for things about the player. */
    private final List<IconButton> cardIcons = new ArrayList<>();
    /** Another mod's destinations (Essential's), in a strip on the other side of the column. */
    private final List<IconButton> extras = new ArrayList<>();
    private final List<Tween> buttonIn = new ArrayList<>();
    private final boolean intro;
    private final Tween backdropIn;
    private final Tween markIn;
    private final Tween cardIn;
    private final Tween footerIn;
    private final Spring[] swatchHover = new Spring[ACCENTS.length];
    private final Button worlds, servers;
    private float leftX, topY, colW;
    /** Content scale: the column is laid out at full size and shrinks to fit small windows. */
    private float k = 1;
    /** Canvas size in layout units (window size divided by {@link #k}). */
    private float vw, vh;
    /** Fades the whole menu out before handing over to a vanilla screen. */
    private final Handover handover = new Handover(this);

    public MainMenuScreen() {
        intro = !introPlayed;
        introPlayed = true;
        float pace = intro ? 1f : 0.35f;
        backdropIn = new Tween(1.6f * pace, Easing.OUT_CUBIC);
        markIn = new Tween(0.9f * pace, Easing.OUT_EXPO).delay(0.35f * pace);
        cardIn = new Tween(0.75f * pace, Easing.OUT_EXPO).delay(0.7f * pace);
        footerIn = new Tween(0.7f * pace, Easing.OUT_CUBIC).delay(1.5f * pace);

        worlds = add("Singleplayer", () -> go(() -> Nav.singleplayer(Mc.screen()))).style(Button.Style.PRIMARY);
        servers = add("Multiplayer", () -> go(() -> Nav.multiplayer(Mc.screen())));
        // The palette opens over the menu (which stays visible, blurred), so no fade-out here.
        row(new float[] {0.5f, 0.25f, 0.25f},
                segment("Mods", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen())))),
                segment("Client", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen(), new SettingsPage())))).icon(Icons.SETTINGS),
                segment("UI", () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen(), SettingsPage.ui())))).icon(Icons.SETTINGS));
        add("Options", () -> go(() -> Nav.options(Mc.screen())));
        add("Quit game", Nav::quit).style(Button.Style.DANGER);

        icons.add(new IconButton(Icons.REALMS, "Realms", () -> go(() -> Nav.realms(Mc.screen()))));
        if (Nav.hasModMenu()) {
            icons.add(new IconButton(Icons.MODS, "Installed mods (" + Nav.countMods() + ")", () -> go(() -> Nav.mods(Mc.screen()))));
        }

        extras.addAll(dev.aller.compat.EssentialCompat.buttons(true, this::go));

        cardIcons.add(new IconButton(Icons.WARDROBE, "Wardrobe", () -> Mc.setScreen(new ScreenHost(new WardrobeScreen(Mc.screen())))));

        // Counting worlds and servers reads from disk, so fill those details in when they arrive.
        CompletableFuture.supplyAsync(Nav::countWorlds).thenAccept(n -> worlds.hint = plural(n, "world"));
        CompletableFuture.supplyAsync(Nav::countServers).thenAccept(n -> servers.hint = plural(n, "server"));

        for (int i = 0; i < rows.size(); i++) {
            buttonIn.add(new Tween(0.6f * pace, Easing.OUT_EXPO).delay((0.9f + i * 0.07f) * pace));
        }
        for (int i = 0; i < swatchHover.length; i++) swatchHover[i] = Spring.bouncy(0);
    }

    private static String plural(int n, String noun) {
        if (n < 0) return null;
        return n == 0 ? "none yet" : n + " " + noun + (n == 1 ? "" : "s");
    }

    private Button add(String label, Runnable action) {
        Button b = new Button(label, action).left();
        b.textSize = 9.5f;
        row(new float[] {1f}, b);
        return b;
    }

    /** One of several buttons sharing a line. */
    private static Button segment(String label, Runnable action) {
        Button b = new Button(label, action);
        b.textSize = 8.5f;
        return b;
    }

    private void row(float[] share, Button... line) {
        rows.add(line);
        shares.add(share);
        buttons.addAll(List.of(line));
    }

    /** Fade out, run the navigation, and be ready to fade back in when the user returns. */
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
        float columnH = 34 + CARD_H + 10 + rows.size() * ROW;
        k = Math.clamp(Math.min(height / (columnH + 56), width / 360f), 0.5f, 1f);
        vw = width / k;
        vh = height / k;
        colW = Math.min(COLUMN, vw - 40 - STRIP - (extras.isEmpty() ? 0 : STRIP));
        leftX = Math.max(20 + STRIP, Math.min(vw * 0.085f + STRIP, (vw - colW) / 2));
        topY = Math.max(14, (vh - columnH) / 2 - 10);
    }

    @Override
    public boolean closeOnEscape() {
        return false;
    }

    @Override
    public boolean blurBehind() {
        return false;
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        var opt = AllerClient.options();
        float lv = handover.update();
        float present = 1 - lv;
        // Drawn over the screen it is leaving for, the menu brings only its content and its shading.
        boolean over = drawingLeaving;

        float bd = backdropIn.update();
        if (!over) {
            // Backdrop. A solid base first so nothing shows through if the shader is still loading.
            c.plainRect(0, 0, width, height, Theme.BG);
            c.backdrop(0, 0, width, height, Theme.accent(), opt.backdropIntensity.get() * bd, Motion.time(), opt.backdropCell.get());
        }
        // Darken towards the left so the column always has contrast.
        c.pushAlpha(over ? present : 1);
        c.gradientH(0, 0, width * 0.6f, height, 0, 0xCC07060B, 0x0007060B);
        c.gradientV(0, height - 70, width, 70, 0, 0x0007060B, 0xCC07060B);
        c.popAlpha();

        c.push();
        c.scale(k, 0, 0);
        mx /= k;
        my /= k;
        if (handover.leaving()) mx = -1000; // no hover feedback while leaving
        c.pushAlpha(present);
        c.push();
        c.translate(-14 * lv, 0);

        float y = topY;
        drawWordmark(c, leftX, y, markIn.update());
        y += 34;

        float cd = cardIn.update();
        c.pushAlpha(cd);
        drawProfileCard(c, leftX - (1 - cd) * 26, y, mx, my);
        c.popAlpha();
        y += CARD_H + 10;

        // Buttons slide in from the left one after another.
        float buttonsTop = y;
        for (int i = 0; i < rows.size(); i++) {
            float t = buttonIn.get(i).update();
            Button[] line = rows.get(i);
            float room = colW - SEGMENT_GAP * (line.length - 1), bx = leftX - (1 - t) * 26;
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
        // Secondary destinations: a strip of icon buttons centred against the stack.
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
        for (IconButton b : cardIcons) b.drawTip(c, true);
        for (IconButton b : extras) b.drawTip(c, true);
        c.pop();
        c.popAlpha();

        float ft = footerIn.update() * present;
        c.pushAlpha(ft);
        c.text(Fonts.REGULAR, "Minecraft " + Nav.minecraftVersion() + "  •  Aller Client " + AllerClient.VERSION, leftX, vh - 16, 7f, Theme.TEXT_MUTED);
        String legal = "Not an official Minecraft product. Not affiliated with Mojang or Microsoft.";
        if (vw > leftX * 2 + 170 + Fonts.REGULAR.width(legal, 7f)) {
            c.textRight(Fonts.REGULAR, legal, vw - leftX, vh - 16, 7f, Theme.TEXT_MUTED);
        }
        c.popAlpha();
        c.pop();

        // Intro curtain: the whole scene emerges from black on first launch.
        if (over) return;
        if (intro && bd < 1) c.plainRect(0, 0, width, height, Colors.withAlpha(Colors.BLACK, (1 - bd) * (1 - bd)));
        Toasts.draw(c);
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

    /** "Aller Client" with each letter rising into place slightly after the previous one, then a version tag. */
    private void drawWordmark(Canvas c, float x, float y, float progress) {
        String word = AllerClient.NAME;
        float size = 24;
        float pen = x;
        // The letters share the time the five of "Aller" used to take, so the full stop still lands last.
        float step = 0.6f / word.length();
        for (int i = 0; i < word.length(); i++) {
            float eased = Easing.OUT_CUBIC.apply(Math.clamp(progress * 1.6f - i * step, 0f, 1f));
            String ch = word.substring(i, i + 1);
            c.pushAlpha(eased);
            c.text(Fonts.BOLD, ch, pen, y + (1 - eased) * 12, size, Theme.TEXT);
            c.popAlpha();
            pen += Fonts.BOLD.width(ch, size) - 0.4f;
        }
        // The accent full stop lands last with a little bounce.
        float dot = Easing.OUT_BACK.apply(Math.clamp(progress * 1.6f - 0.6f, 0f, 1f));
        float r = 2.3f * dot;
        // Clear of the "t", which ends closer to its edge than the "r" of "Aller" did.
        float dy = y + 20.5f, dotX = pen + 4.5f;
        c.shadow(dotX - r, dy - r, r * 2, r * 2, r, 8, Colors.withAlpha(Theme.accent(), 0.7f * dot));
        c.circle(dotX, dy, r, Theme.accent());
    }

    private void drawProfileCard(Canvas c, float x, float y, float mx, float my) {
        Theme.panel(c, x, y, colW, CARD_H, Theme.R_LG);

        // The player's own face, framed.
        int face = 32;
        float fx = x + 11, fy = y + 10;
        Skins.drawFramedFace(c, fx, fy, face);

        float tx = fx + face + 11;
        String name = Nav.playerName();
        float strip = cardIcons.size() * (CARD_ICON + 3) + 3;
        c.text(Fonts.BOLD, Fonts.BOLD.truncate(name, 13, x + colW - tx - 10 - strip), tx, y + 9, 13, Theme.TEXT);
        for (int i = 0; i < cardIcons.size(); i++) {
            IconButton b = cardIcons.get(i);
            b.bounds(x + colW - 9 - (cardIcons.size() - i) * (CARD_ICON + 3) + 3, y + 9, CARD_ICON, CARD_ICON);
            b.draw(c, mx, my);
        }

        int enabled = 0;
        for (var m : AllerClient.modules().all()) if (m.enabled()) enabled++;
        float lx = tx;
        c.circle(lx + 2.5f, y + 31.5f, 2.2f, Theme.accent());
        lx += 8;
        lx += c.text(Fonts.SEMIBOLD, Integer.toString(enabled), lx, y + 27, 8, Theme.TEXT) + 3;
        lx += c.text(Fonts.REGULAR, "of " + AllerClient.modules().all().size() + " mods on", lx, y + 27.4f, 7.5f, Theme.TEXT_DIM);
        Icons.CHECK.draw(c, tx + 3.5f, y + 38.5f + Fonts.REGULAR.height(7) / 2, 7.5f, Theme.TEXT_MUTED);
        c.text(Fonts.REGULAR, Skins.ownModel() + "  •  " + AllerClient.config().activeProfile() + " profile", tx + 10, y + 38.5f, 7, Theme.TEXT_MUTED);

        // Accent strip: click a swatch to recolour the whole client live.
        float sy = y + CARD_H - 13;
        c.rect(x + 10, y + 51, colW - 20, 1, 0, Theme.BORDER);
        c.textMiddle(Fonts.MEDIUM, "ACCENT", x + 11, sy - 6, 12, 6.2f, Theme.TEXT_MUTED);
        for (int i = 0; i < ACCENTS.length; i++) {
            float cx = swatchX(x, i);
            boolean over = Math.hypot(mx - cx, my - sy) < 7;
            boolean active = (AllerClient.options().accent.get() | 0xFF000000) == ACCENTS[i];
            float hv = swatchHover[i].target(over ? 1 : 0).update();
            float r = 4.2f + 1.3f * hv;
            if (active) c.ring(cx, sy, r + 2.4f, 1f, Colors.withAlpha(ACCENTS[i], 0.9f));
            c.circle(cx, sy, r, ACCENTS[i]);
        }
    }

    private float swatchX(float cardX, int index) {
        return cardX + colW - 16 - (ACCENTS.length - 1 - index) * 15;
    }

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (handover.leaving()) return false;
        x /= k;
        y /= k;
        for (Button b : buttons) if (b.mouseDown(x, y, button)) return true;
        for (IconButton b : icons) if (b.mouseDown(x, y, button)) return true;
        for (IconButton b : cardIcons) if (b.mouseDown(x, y, button)) return true;
        for (IconButton b : extras) if (b.mouseDown(x, y, button)) return true;
        if (button == 0) {
            float sy = topY + 34 + CARD_H - 13;
            for (int i = 0; i < ACCENTS.length; i++) {
                if (Math.hypot(x - swatchX(leftX, i), y - sy) < 7) {
                    AllerClient.options().accent.set(ACCENTS[i]);
                    AllerClient.config().markDirty();
                    Sounds.click();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        boolean used = false;
        for (Button b : buttons) used |= b.mouseUp(x / k, y / k, button);
        for (IconButton b : icons) used |= b.mouseUp(x / k, y / k, button);
        for (IconButton b : cardIcons) used |= b.mouseUp(x / k, y / k, button);
        for (IconButton b : extras) used |= b.mouseUp(x / k, y / k, button);
        return used;
    }
}
