package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Skins;
import dev.aller.platform.Sounds;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.anim.Tween;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;

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

    private final List<Button> buttons = new ArrayList<>();
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
    private final Spring leave = Spring.smooth(0);
    private Runnable leaveAction;

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
        add("Realms", () -> go(() -> Nav.realms(Mc.screen())));
        if (Nav.hasModMenu()) add("Mods", () -> go(() -> Nav.mods(Mc.screen()))).hint(Nav.countMods() + " loaded");
        add("Aller settings", () -> go(() -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen())))))
                .hint(Mc.keyName(AllerClient.options().menuKey.get()) + " in game");
        add("Options", () -> go(() -> Nav.options(Mc.screen())));
        add("Quit", Nav::quit).style(Button.Style.GHOST);

        // Counting worlds and servers reads from disk, so fill those details in when they arrive.
        CompletableFuture.supplyAsync(Nav::countWorlds).thenAccept(n -> worlds.hint = plural(n, "world"));
        CompletableFuture.supplyAsync(Nav::countServers).thenAccept(n -> servers.hint = plural(n, "server"));

        for (int i = 0; i < buttons.size(); i++) {
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
        buttons.add(b);
        return b;
    }

    /** Fade out, run the navigation, and be ready to fade back in when the user returns. */
    private void go(Runnable action) {
        if (leaveAction != null) return;
        leaveAction = action;
        leave.target(1);
    }

    @Override
    public void opened() {
        super.opened();
        leave.snap(0);
        leaveAction = null;
    }

    @Override
    protected void layout() {
        float columnH = 34 + CARD_H + 10 + buttons.size() * ROW;
        k = Math.clamp(Math.min(height / (columnH + 56), width / 330f), 0.5f, 1f);
        vw = width / k;
        vh = height / k;
        colW = Math.min(COLUMN, vw - 40);
        leftX = Math.max(20, Math.min(vw * 0.085f, (vw - colW) / 2));
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
        float lv = leave.update();
        if (leaveAction != null && lv > 0.97f) {
            Runnable action = leaveAction;
            leaveAction = null;
            AllerClient.defer(() -> {
                action.run();
                leave.snap(0);
            });
        }
        float present = 1 - Math.clamp(lv, 0f, 1f);

        // Backdrop. A solid base first so nothing shows through if the shader is still loading.
        c.plainRect(0, 0, width, height, Theme.BG);
        float bd = backdropIn.update();
        c.backdrop(0, 0, width, height, Theme.accent(), opt.backdropIntensity.get() * bd, Motion.time(), opt.backdropCell.get());
        // Darken towards the left so the column always has contrast.
        c.gradientH(0, 0, width * 0.6f, height, 0, 0xCC07060B, 0x0007060B);
        c.gradientV(0, height - 70, width, 70, 0, 0x0007060B, 0xCC07060B);

        c.push();
        c.scale(k, 0, 0);
        mx /= k;
        my /= k;
        if (leaveAction != null) mx = -1000; // no hover feedback while leaving
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
        for (int i = 0; i < buttons.size(); i++) {
            float t = buttonIn.get(i).update();
            Button b = buttons.get(i);
            b.bounds(leftX - (1 - t) * 26, y, colW, BUTTON_H);
            c.pushAlpha(t);
            b.draw(c, mx, my);
            c.popAlpha();
            y += ROW;
        }
        c.pop();
        c.popAlpha();

        float ft = footerIn.update() * present;
        c.pushAlpha(ft);
        c.text(Fonts.REGULAR, "Minecraft " + Nav.minecraftVersion() + "  •  Aller " + AllerClient.VERSION, leftX, vh - 16, 7f, Theme.TEXT_MUTED);
        String legal = "Not an official Minecraft product. Not affiliated with Mojang or Microsoft.";
        if (vw > leftX * 2 + 170 + Fonts.REGULAR.width(legal, 7f)) {
            c.textRight(Fonts.REGULAR, legal, vw - leftX, vh - 16, 7f, Theme.TEXT_MUTED);
        }
        c.popAlpha();
        c.pop();

        // Intro curtain: the whole scene emerges from black on first launch.
        if (intro && bd < 1) c.plainRect(0, 0, width, height, Colors.withAlpha(Colors.BLACK, (1 - bd) * (1 - bd)));
        Toasts.draw(c);
    }

    /** "Aller" with each letter rising into place slightly after the previous one, then a version tag. */
    private void drawWordmark(Canvas c, float x, float y, float progress) {
        String word = "Aller";
        float size = 24;
        float pen = x;
        for (int i = 0; i < word.length(); i++) {
            float eased = Easing.OUT_CUBIC.apply(Math.clamp(progress * 1.6f - i * 0.12f, 0f, 1f));
            String ch = word.substring(i, i + 1);
            c.pushAlpha(eased);
            c.text(Fonts.BOLD, ch, pen, y + (1 - eased) * 12, size, Theme.TEXT);
            c.popAlpha();
            pen += Fonts.BOLD.width(ch, size) - 0.4f;
        }
        // The accent full stop lands last with a little bounce.
        float dot = Easing.OUT_BACK.apply(Math.clamp(progress * 1.6f - 0.6f, 0f, 1f));
        float r = 2.3f * dot;
        float dy = y + 20.5f;
        c.shadow(pen + 2 - r, dy - r, r * 2, r * 2, r, 8, Colors.withAlpha(Theme.accent(), 0.7f * dot));
        c.circle(pen + 2, dy, r, Theme.accent());

        float tag = Math.clamp(progress * 1.6f - 0.6f, 0f, 1f);
        c.pushAlpha(tag);
        String label = "CLIENT";
        float tw = Fonts.MEDIUM.width(label, 6.5f) + 12;
        float tx = x + colW - tw;
        c.rect(tx, y + 9, tw, 13, 6.5f, 0x1AFFFFFF);
        c.stroke(tx, y + 9, tw, 13, 6.5f, 1, Theme.BORDER);
        c.textMiddle(Fonts.MEDIUM, label, tx + 6, y + 9, 13, 6.5f, Theme.TEXT_DIM);
        c.popAlpha();
    }

    private void drawProfileCard(Canvas c, float x, float y, float mx, float my) {
        Theme.panel(c, x, y, colW, CARD_H, Theme.R_LG);

        // The player's own face, framed.
        int face = 32;
        float fx = x + 11, fy = y + 10;
        c.rect(fx - 2, fy - 2, face + 4, face + 4, 5, 0x33000000);
        Skins.drawOwnFace(c, fx, fy, face);
        c.stroke(fx - 2, fy - 2, face + 4, face + 4, 5, 1.5f, Colors.withAlpha(Theme.accent(), 0.75f));

        float tx = fx + face + 11;
        String name = Nav.playerName();
        c.text(Fonts.BOLD, Fonts.BOLD.truncate(name, 13, x + colW - tx - 10), tx, y + 9, 13, Theme.TEXT);

        int enabled = 0;
        for (var m : AllerClient.modules().all()) if (m.enabled()) enabled++;
        float lx = tx;
        c.circle(lx + 2.5f, y + 31.5f, 2.2f, Theme.accent());
        lx += 8;
        lx += c.text(Fonts.SEMIBOLD, Integer.toString(enabled), lx, y + 27, 8, Theme.TEXT) + 3;
        lx += c.text(Fonts.REGULAR, "of " + AllerClient.modules().all().size() + " mods on", lx, y + 27.4f, 7.5f, Theme.TEXT_DIM);
        c.text(Fonts.REGULAR, "✓ " + Skins.ownModel() + "  •  " + AllerClient.config().activeProfile() + " profile",
                tx, y + 38.5f, 7, Theme.TEXT_MUTED);

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
        if (leaveAction != null) return false;
        x /= k;
        y /= k;
        for (Button b : buttons) if (b.mouseDown(x, y, button)) return true;
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
        for (Button b : buttons) if (b.mouseUp(x / k, y / k, button)) return true;
        return false;
    }
}
