package dev.aller.screen;

import com.google.gson.JsonPrimitive;
import dev.aller.AllerClient;
import dev.aller.ClientOptions;
import dev.aller.module.Module;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Skins;
import dev.aller.platform.Sounds;
import dev.aller.screen.onboarding.Intro;
import dev.aller.setting.Settings;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Easing;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Toggle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The first run: the {@link Intro}, then a short walk through making the client the player's own.
 * It starts drained of colour. The accent is picked while everything else is still grey, and
 * choosing a look (smooth or pixel) is what lets the colour in, as a wave from the card that was
 * clicked. After that come the two keys worth knowing (each has to be pressed for real, or rebound,
 * or skipped), the HUD editor, a few mods to start with, the fair-play note and the way out.
 *
 * <p>Forward only. The intro cannot be skipped; from the greeting on, holding Escape ends it all.
 */
public final class OnboardingScreen extends AllerScreen {
    public enum Stage { INTRO, HELLO, ACCENT, THEME, PALETTE, LAUNCHER, HUD, MODS, FAIR, DONE }

    private static final String FLAG = "onboarded";
    private static final float HEAD_TOP = 20, HEAD_SIZE = 15, TAU = 6.2831855f;
    private static final float WAVE_HOLD = 0.12f, WAVE_RUN = 1.15f;

    private record Swatch(String name, int color) {}

    private static final Swatch[] SWATCHES = {
            new Swatch("Violet", 0xFF8B5CF6), new Swatch("Blue", 0xFF3B82F6), new Swatch("Cyan", 0xFF22D3EE), new Swatch("Green", 0xFF34D399),
            new Swatch("Amber", 0xFFFBBF24), new Swatch("Orange", 0xFFFB923C), new Swatch("Red", 0xFFF2555A), new Swatch("Pink", 0xFFF472B6),
    };
    /** The mods offered up front, by id; one that is not registered is left out. */
    private static final String[] STARTERS = {
            "fps", "coordinates", "keystrokes", "cps", "armor", "potions",
            "zoom", "toggle_sprint", "crosshair", "fullbright", "freelook", "waypoints",
    };

    /** Whether to show this in place of the title screen: once, and never during a harness run. */
    public static boolean due() {
        if (System.getProperty("aller.dev.shots") != null) return false;
        var flag = AllerClient.config().extra(FLAG);
        return flag == null || !flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean() || !flag.getAsBoolean();
    }

    private static void markDone() {
        AllerClient.config().setExtra(FLAG, new JsonPrimitive(true));
        AllerClient.config().save();
    }

    private static final class Piece {
        float x, y, vx, vy, rot, spin, size, age, life, gravity, drag;
        int color, sides;
    }

    private record Hit(float x, float y, float w, float h, Runnable action) {}

    private final Screen parent;
    private final Intro intro = new Intro();
    private final Random random = new Random();
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Module> starters = new ArrayList<>();
    private final Toggle[] starterToggles;
    private final Spring[] starterHover;
    private final Spring[] swatchHover = new Spring[SWATCHES.length + 1];
    private final Spring[] cardHover = {Spring.snappy(0), Spring.snappy(0)};
    private final Spring dotPulse = Spring.bouncy(1), pip = new Spring(0, 300f, 26f), capPress = Spring.snappy(0), passPop = Spring.bouncy(0);
    private final Button primary = new Button("Continue", this::advance).style(Button.Style.PRIMARY);
    private final Button rebind = new Button("Rebind", this::listen);
    private final Button skipStep = new Button("Skip this", this::next).style(Button.Style.GHOST);

    private Stage stage = Stage.INTRO, leaving;
    /** Seconds in the current stage, and since the screen opened. */
    private float since, clock;
    /** While a stage is drawn on its way out, its pieces are simply all there. */
    private boolean drawingOld;
    private float k = 1, vw, vh;
    private boolean started;

    private boolean accentPicked, customOpen;
    private float hue = 0.72f, richness = 0.62f;
    private int dragBar;

    /** The look chosen in this run; until then everything is drawn smooth, whatever the options say. */
    private Theme.Look picked;
    private boolean coloured;
    private float waveT = -1, waveX, waveY, waveFar;
    private boolean waveThrown;

    /** The key being rebound, or null. */
    private Settings.Key listening;
    private boolean keyOpened, keyPassed, keyArmed, keyWasDown;
    private float holdEsc;
    private boolean finished;

    public OnboardingScreen(Screen parent) {
        this.parent = parent;
        for (String id : STARTERS) {
            Module m = AllerClient.modules().get(id);
            if (m != null) starters.add(m);
        }
        starterToggles = new Toggle[starters.size()];
        starterHover = new Spring[starters.size()];
        for (int i = 0; i < starters.size(); i++) {
            starterToggles[i] = new Toggle();
            starterHover[i] = Spring.snappy(0);
        }
        for (int i = 0; i < swatchHover.length; i++) swatchHover[i] = Spring.bouncy(0);
        primary.textSize = 9.5f;
        rebind.textSize = skipStep.textSize = 8f;
        float[] hsv = Colors.toHsv(Theme.accent());
        hue = hsv[0];
        richness = Math.clamp(hsv[1], 0.3f, 1f);
    }

    // ---- screen plumbing -------------------------------------------------------------------------

    @Override
    protected void layout() {
        k = Math.clamp(Math.min(height / 330f, width / 500f), 0.5f, 1f);
        vw = width / k;
        vh = height / k;
    }

    @Override
    public void opened() {
        super.opened();
        if (Motion.reduced()) intro.seek(Intro.SNAP + 0.5f);
    }

    /** Back in front after the palette or the launcher closed over it: the key has done its job. */
    @Override
    public void reshown() {
        if ((stage == Stage.PALETTE || stage == Stage.LAUNCHER) && keyOpened && !keyPassed) pass();
        keyArmed = false;
    }

    @Override
    public boolean closeOnEscape() {
        return false;
    }

    @Override
    public boolean blurBehind() {
        return false;
    }

    /** Only the launcher step lets the launcher's own shortcut through. */
    @Override
    public boolean capturing() {
        return stage != Stage.LAUNCHER || listening != null || since < 0.5f;
    }

    private float st() {
        return drawingOld ? 99f : since;
    }

    /** How far the nth piece of a stage has arrived, 0 to 1. */
    private float appear(float order) {
        return Easing.OUT_EXPO.apply(Math.clamp((st() - 0.2f - order * 0.07f) / 0.55f, 0f, 1f));
    }

    private void go(Stage to) {
        leaving = stage;
        stage = to;
        since = 0;
        listening = null;
        keyOpened = keyPassed = keyArmed = keyWasDown = false;
        passPop.snap(0);
        if (to == Stage.DONE) celebrate();
        else if (to != Stage.HELLO) Sounds.cue("ui.button.click", 1.4f, 0.12f);
    }

    private void next() {
        if (stage == Stage.DONE) finish();
        else go(Stage.values()[stage.ordinal() + 1]);
    }

    /** The big button, or Enter. */
    private void advance() {
        if (!primaryReady()) return;
        next();
    }

    private boolean primaryReady() {
        return switch (stage) {
            case INTRO -> false;
            case THEME -> picked != null && waveT > 1.0f;
            case PALETTE, LAUNCHER -> keyPassed;
            default -> since > 0.3f;
        };
    }

    private void finish() {
        if (finished) return;
        finished = true;
        markDone();
        close(() -> {
            if (parent != null) Mc.setScreen(parent);
            else if (Mc.mc().level == null) Mc.setScreen(new TitleScreen());
            else Mc.setScreen(null);
        });
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void draw(Canvas c, float mx, float my) {
        boolean live = !drawingUnderlay && !isClosing() && Mc.current() == this;
        Theme.Look outer = Theme.look;
        try {
            paint(c, mx / k, my / k, live);
        } finally {
            Canvas.ungrade();
            Theme.look = outer;
        }
        if (isClosing()) c.rect(0, 0, width, height, 0, Colors.withAlpha(Colors.BLACK, 1 - fade()));
        Toasts.draw(c);
    }

    private void paint(Canvas c, float mx, float my, boolean live) {
        float dt = drawingUnderlay ? 0 : Motion.delta();
        clock += dt;
        hits.clear();
        look(vw / 2, vh / 2);

        if (stage == Stage.INTRO) {
            // Nothing moves until the loading screen has gone: the first frame of this is the first thing seen.
            if (!Mc.loadingOverlay()) {
                if (!started) {
                    started = true;
                    intro.seek(Math.max(intro.time(), 0));
                }
                intro.advance(dt);
            }
            Canvas.grade(0f, 0.85f);
            float back = Math.min(1f, intro.backdrop());
            c.rect(0, 0, width, height, 0, Colors.mix(Colors.BLACK, Theme.BG, back));
            if (back > 0.01f) backdrop(c, intro.backdrop() * 0.8f);
            shade(c, back);
            intro.draw(c, width, height, width / 2, HEAD_TOP * k, HEAD_SIZE * k);
            if (intro.done()) go(Stage.HELLO);
            return;
        }

        since += dt;
        // Something has opened over this: on a key step, that is the key doing its job.
        if (drawingUnderlay && (stage == Stage.PALETTE || stage == Stage.LAUNCHER)) keyOpened = true;
        if (waveT >= 0 && !drawingUnderlay) wave(dt);
        if (!coloured) {
            Canvas.grade(0f, 0.85f);
            if (waveT >= 0) c.wave(waveX * k, waveY * k, radius() * k, 46 * k);
        }

        float spike = waveT >= WAVE_HOLD ? 1.3f * (float) Math.exp(-(waveT - WAVE_HOLD) * 2.4f) : 0;
        c.rect(0, 0, width, height, 0, Theme.BG);
        backdrop(c, 0.8f + spike);
        shade(c, 1);

        c.push();
        c.scale(k, 0, 0);
        float shake = waveT >= WAVE_HOLD ? 6 * (float) Math.exp(-(waveT - WAVE_HOLD) * 5) : 0;
        if (shake > 0.05f) c.translate((float) Math.sin(clock * 91) * shake, (float) Math.cos(clock * 77) * shake);
        c.pushAlpha(fade());

        header(c);
        if (leaving != null && since < 0.22f) {
            float out = since / 0.22f;
            drawingOld = true;
            c.pushAlpha(1 - out);
            c.push();
            c.translate(-26 * Easing.IN_CUBIC.apply(out), 0);
            stage(c, leaving, -1000, -1000, false);
            c.pop();
            c.popAlpha();
            drawingOld = false;
        } else {
            leaving = null;
            stage(c, stage, mx, my, live);
        }
        footer(c, mx, my, live);
        effects(c);
        c.popAlpha();
        c.pop();

        if (live) hold(dt);
    }

    /** The noise field: in tiles while the wave crosses it, so the colour comes in as a front and not all at once. */
    private void backdrop(Canvas c, float lift) {
        var opt = AllerClient.options();
        float strength = opt.backdropIntensity.get() * lift;
        if (waveT < 0 || coloured) {
            c.backdrop(0, 0, width, height, Theme.accent(), strength, Motion.time(), opt.backdropCell.get());
            return;
        }
        float tile = 36;
        for (float y = 0; y < height; y += tile) {
            for (float x = 0; x < width; x += tile) {
                c.backdrop(x, y, Math.min(tile, width - x), Math.min(tile, height - y), Theme.accent(), strength, Motion.time(), opt.backdropCell.get());
            }
        }
    }

    /** Darkens the edges, and the whole screen a little more while it has no colour. */
    private void shade(Canvas c, float amount) {
        float gloom = coloured ? 0 : waveT >= WAVE_HOLD ? 1 - Math.min(1f, (waveT - WAVE_HOLD) / WAVE_RUN) : 1;
        c.gradientV(0, 0, width, height * 0.45f, 0, Colors.withAlpha(0xFF050409, 0.8f * amount), 0x00050409);
        c.gradientV(0, height * 0.55f, width, height * 0.45f, 0, 0x00050409, Colors.withAlpha(0xFF050409, 0.85f * amount));
        c.rect(0, 0, width, height, 0, Colors.withAlpha(0xFF050409, (0.28f + 0.2f * gloom) * amount));
    }

    /** The wordmark where the intro left it. Its full stop takes the accent as soon as one is picked: the first colour on screen. */
    private void header(Canvas c) {
        float cx = vw / 2;
        look(cx, HEAD_TOP);
        c.push();
        c.scale(1 + kick(cx, HEAD_TOP), cx, HEAD_TOP + HEAD_SIZE / 2);
        Intro.mark(c, cx, HEAD_TOP, HEAD_SIZE, 1f, 0, Colors.WHITE, -100);
        boolean lit = accentPicked || coloured || stage.ordinal() > Stage.ACCENT.ordinal();
        float r = HEAD_SIZE * 0.095f * dotPulse.target(1).update();
        float dx = Intro.dotX(cx, HEAD_SIZE), dy = Intro.dotY(HEAD_TOP, HEAD_SIZE);
        if (lit) Canvas.ungrade();
        int color = lit ? Theme.accent() : Colors.WHITE;
        c.shadow(dx - r, dy - r, r * 2, r * 2, r, 7, Colors.withAlpha(color, lit ? 0.85f : 0.4f));
        c.circle(dx, dy, r, color);
        // The ember: what has been picked, glowing under the mark until the rest catches up.
        if (lit && !coloured && waveT < 0) {
            float ex = cx, ey = HEAD_TOP + HEAD_SIZE + 13, beat = 0.5f + 0.5f * (float) Math.sin(clock * 3.1f);
            c.oval(ex, ey, 16 + 5 * beat, 9 + 3 * beat, 0.95f, Colors.withAlpha(color, 0.3f + 0.12f * beat));
            c.circle(ex, ey, 1.9f + 0.4f * beat, Colors.lighten(color, 0.35f));
            for (int i = 0; i < 5; i++) {
                float p = (clock * 0.55f + i * 0.2f) % 1f;
                c.circle(ex + (float) Math.sin(clock * 1.7f + i * 2.1f) * (2 + 5 * p), ey - 15 * p, 0.8f * (1 - p), Colors.withAlpha(color, 0.9f * (1 - p)));
            }
        }
        regrade(c);
        c.pop();
    }

    private void stage(Canvas c, Stage s, float mx, float my, boolean live) {
        switch (s) {
            case HELLO -> hello(c);
            case ACCENT -> accent(c, mx, my, live);
            case THEME -> theme(c, mx, my, live);
            case PALETTE -> keyStep(c, mx, my, live, AllerClient.options().menuKey,
                    "Your mods are one key away",
                    "Press it now. It opens the palette: every mod, searchable, with its settings. Escape closes it again.",
                    "In a world it works at any time. It can be changed later under Client settings.");
            case LAUNCHER -> keyStep(c, mx, my, live, AllerClient.options().launcherKey,
                    "And a launcher for everything else",
                    "Press it anywhere, even on Minecraft's own menus, and type what you want: a setting, a waypoint, a sum, a command. Try it, then Escape.",
                    "Start with > for actions, # for settings, @ for waypoints or / for a command.");
            case HUD -> hud(c, mx, my, live);
            case MODS -> mods(c, mx, my, live);
            case FAIR -> fair(c);
            case DONE -> done(c);
            default -> {}
        }
    }

    private float top() {
        return Math.max(52, vh / 2 - 112);
    }

    /** A stage's title and the lines under it; returns where they end. */
    private float heading(Canvas c, String title, String sub) {
        float cx = vw / 2, y = top();
        look(cx, y);
        float a = appear(0);
        c.pushAlpha(a);
        c.push();
        c.scale(1 + kick(cx, y), cx, y + 10);
        float size = Math.min(20, 20 * (vw - 40) / Math.max(1f, Fonts.BOLD.width(title, 20)));
        c.textCentered(Fonts.BOLD, title, cx, y + (1 - a) * 10, size, Theme.TEXT);
        c.pop();
        c.popAlpha();
        y += 29;
        a = appear(1);
        c.pushAlpha(a);
        look(cx, y);
        for (String line : Fonts.REGULAR.wrap(sub, 9, Math.min(390, vw - 40))) {
            c.textCentered(Fonts.REGULAR, line, cx, y + (1 - a) * 8, 9, Theme.TEXT_DIM);
            y += 13;
        }
        c.popAlpha();
        return y;
    }

    // ---- stages ----------------------------------------------------------------------------------

    private void hello(Canvas c) {
        float cx = vw / 2, y = vh / 2 - 44;
        String line = "Hello, " + Nav.playerName() + ".";
        float size = Math.min(30, 30 * (vw - 50) / Math.max(1f, Fonts.BOLD.width(line, 30)));
        int typed = Math.clamp((int) ((st() - 0.45f) * 20), 0, line.length());
        if (!drawingOld && typed > 0 && typed < line.length() && typed != lastTyped && typed % 2 == 0) Sounds.cue("ui.button.click", 1.9f, 0.05f);
        lastTyped = typed;
        float x = cx - Fonts.BOLD.width(line, size) / 2;
        float w = c.text(Fonts.BOLD, line.substring(0, typed), x, y, size, Theme.TEXT);
        if (typed < line.length() || clock % 1f < 0.5f) c.rect(x + w + 2, y + size * 0.16f, 2, size * 0.9f, 1, Theme.TEXT_DIM);

        float after = st() - 0.45f - line.length() / 20f - 0.15f;
        String[] lines = {"Welcome to " + AllerClient.NAME + ".", "A minute to make it yours, and then you are in."};
        for (int i = 0; i < lines.length; i++) {
            float a = Easing.OUT_EXPO.apply(Math.clamp((after - i * 0.22f) / 0.6f, 0f, 1f));
            c.pushAlpha(a);
            c.textCentered(i == 0 ? Fonts.MEDIUM : Fonts.REGULAR, lines[i], cx, y + size + 16 + i * 15 + (1 - a) * 8, 10, i == 0 ? Theme.TEXT : Theme.TEXT_DIM);
            c.popAlpha();
        }
        primary.label = "Get started";
        primaryAt = Easing.OUT_EXPO.apply(Math.clamp((after - 0.6f) / 0.6f, 0f, 1f));
    }

    private int lastTyped;
    /** How far the big button has arrived this frame; the stage sets it and its label. */
    private float primaryAt;

    private void accent(Canvas c, float mx, float my, boolean live) {
        float cx = vw / 2;
        float y = heading(c, "Pick your colour", "It is the only colour in here for now. Choose the one that feels like yours.") + 26;
        int count = SWATCHES.length + 1;
        float gap = Math.min(38, (vw - 40) / count), x0 = cx - gap * (count - 1) / 2;
        int current = Theme.accent();
        String label = accentPicked ? "Custom" : "";
        Canvas.ungrade();
        for (int i = 0; i < count; i++) {
            boolean custom = i == SWATCHES.length;
            int color = custom ? Colors.hsv(hue, richness, 0.97f, 1f) : SWATCHES[i].color;
            float a = appear(2 + i * 0.5f);
            float sx = x0 + i * gap, sy = y + (float) Math.sin(clock * 1.6f + i * 0.7f) * 1.6f + (1 - a) * 14;
            boolean over = live && Math.hypot(mx - sx, my - sy) < 14;
            boolean active = accentPicked && (custom ? customOpen : !customOpen && current == color);
            if (active && !custom) label = SWATCHES[i].name;
            float hv = swatchHover[i].target(over ? 1 : active ? 0.55f : 0).update();
            float r = 10 + 3 * hv;
            c.pushAlpha(a);
            c.shadow(sx - r, sy - r + 2, r * 2, r * 2, r, 9 + 8 * hv, Colors.withAlpha(color, 0.3f + 0.4f * hv));
            if (custom) {
                // A ring of every hue stands for "any colour".
                for (int seg = 0; seg < 12; seg++) {
                    float sa = seg / 12f * TAU;
                    c.circle(sx + (float) Math.cos(sa) * r * 0.62f, sy + (float) Math.sin(sa) * r * 0.62f, r * 0.3f, Colors.hsv(seg / 12f, 0.7f, 1f, 1f));
                }
                c.circle(sx, sy, r * 0.34f, color);
            } else {
                c.circle(sx, sy, r, color);
                c.gradientV(sx - r, sy - r, r * 2, r * 2, r, 0x38FFFFFF, 0x00FFFFFF);
            }
            if (active) c.ring(sx, sy, r + 3.5f, 1.4f, Colors.withAlpha(Colors.lighten(color, 0.25f), 0.95f));
            c.popAlpha();
            int index = i;
            hits.add(new Hit(sx - 14, sy - 14, 28, 28, () -> pickSwatch(index)));
        }
        regrade(c);
        y += 26;
        c.pushAlpha(appear(7));
        c.textCentered(Fonts.SEMIBOLD, label, cx, y, 9, Theme.TEXT);
        c.popAlpha();

        if (customOpen) {
            y += 20;
            float bw = Math.min(250, vw - 60), bx = cx - bw / 2;
            if (dragBar != 0 && live) {
                if (GLFW.glfwGetMouseButton(Mc.window(), 0) != GLFW.GLFW_PRESS) dragBar = 0;
                else dragTo(mx, bx, bw);
            }
            Canvas.ungrade();
            for (int seg = 0; seg < 24; seg++) {
                c.gradientH(bx + bw * seg / 24f, y, bw / 24f + 0.3f, 8, 0,
                        Colors.hsv(seg / 24f, richness, 0.97f, 1f), Colors.hsv((seg + 1) / 24f, richness, 0.97f, 1f));
            }
            knob(c, bx + bw * hue, y + 4, Colors.hsv(hue, richness, 0.97f, 1f));
            c.gradientH(bx, y + 18, bw, 8, 4, Colors.hsv(hue, 0.3f, 0.97f, 1f), Colors.hsv(hue, 1f, 0.97f, 1f));
            knob(c, bx + bw * (richness - 0.3f) / 0.7f, y + 22, Colors.hsv(hue, richness, 0.97f, 1f));
            regrade(c);
            hits.add(new Hit(bx - 6, y - 5, bw + 12, 18, () -> dragBar = 1));
            hits.add(new Hit(bx - 6, y + 13, bw + 12, 18, () -> dragBar = 2));
        }
        primary.label = "Continue";
        primaryAt = appear(8);
    }

    private void knob(Canvas c, float x, float y, int color) {
        c.shadow(x - 6, y - 5, 12, 12, 6, 5, 0x66000000);
        c.circle(x, y, 6.5f, Colors.WHITE);
        c.circle(x, y, 4.5f, color);
    }

    private void dragTo(float mx, float bx, float bw) {
        float p = Math.clamp((mx - bx) / bw, 0f, 1f);
        if (dragBar == 1) hue = Math.min(p, 0.999f);
        else richness = 0.3f + 0.7f * p;
        setAccent(Colors.hsv(hue, richness, 0.97f, 1f), false);
    }

    private void pickSwatch(int index) {
        customOpen = index == SWATCHES.length;
        setAccent(customOpen ? Colors.hsv(hue, richness, 0.97f, 1f) : SWATCHES[index].color, true);
        Sounds.cue("block.note_block.chime", 0.8f + index * 0.09f, 0.6f);
    }

    private void setAccent(int color, boolean pulse) {
        accentPicked = true;
        AllerClient.options().accent.set(color | 0xFF000000);
        AllerClient.config().markDirty();
        if (pulse) dotPulse.snap(2.6f);
    }

    private void theme(Canvas c, float mx, float my, boolean live) {
        float cx = vw / 2;
        boolean chosen = picked != null;
        float y = heading(c, chosen ? "There it is" : "Now choose a look",
                chosen ? AllerClient.NAME + ", in your colour. The other card switches the look, and so does UI settings, any time."
                        : "Smooth or pixel. Either can be changed later in UI settings.") + 14;
        float cw = Math.min(168, (vw - 58) / 2), ch = 124, gap = 18;
        for (int i = 0; i < 2; i++) {
            Theme.Look kind = i == 0 ? Theme.Look.SMOOTH : Theme.Look.PIXEL;
            float a = appear(2 + i);
            float x = cx - cw - gap / 2 + i * (cw + gap), cy = y + ch / 2;
            boolean over = live && inside(mx, my, x, y, cw, ch);
            float hv = cardHover[i].target(over ? 1 : 0).update();
            boolean mine = picked == kind;
            // The card that was clicked draws in for a moment before it lets go.
            float squeeze = mine && waveT >= 0 && waveT < WAVE_HOLD ? 0.07f * waveT / WAVE_HOLD : 0;
            c.pushAlpha(a * (chosen && !mine ? 0.62f + 0.38f * hv : 1));
            c.push();
            c.translate(0, (1 - a) * 16 - 3 * hv);
            c.scale(1 + 0.02f * hv - squeeze + kick(x + cw / 2, cy) * 1.4f, x + cw / 2, cy);
            card(c, kind, x, y, cw, ch, hv, mine);
            c.pop();
            c.popAlpha();
            hits.add(new Hit(x, y, cw, ch, () -> choose(kind, x + cw / 2, cy)));
        }
        look(cx, vh - 60);
        primary.label = "Continue";
        primaryAt = chosen ? Easing.OUT_BACK.apply(Math.clamp((waveT - 1.0f) / 0.5f, 0f, 1f)) : 0;
    }

    /** One look, shown as a few pieces of interface drawn in it. */
    private void card(Canvas c, Theme.Look kind, float x, float y, float w, float h, float hover, boolean mine) {
        Theme.Look outer = Theme.look;
        Theme.look = kind;
        c.shadow(x, y + 6, w, h, 12, 20, 0x66000000);
        c.rect(x, y, w, h, 12, 0xD2100E18);
        c.gradientV(x, y, w, h, 12, 0x12FFFFFF, 0x00FFFFFF);
        if (mine) {
            c.shadow(x, y, w, h, 12, 16, Colors.withAlpha(Theme.accent(), 0.4f));
            c.stroke(x, y, w, h, 12, 1.6f, Theme.accent());
        } else {
            c.stroke(x, y, w, h, 12, 1, Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hover));
        }
        // A sample: a heading, a button, a switch and a slider.
        float px = x + 14, py = y + 13;
        c.text(Fonts.BOLD, "Aa", px, py, 22, Theme.TEXT);
        float bx = px + Math.max(40, Fonts.BOLD.width("Aa", 22) + 12);
        c.rect(bx, py + 3, w - 14 - (bx - x), 17, 6, 0xB80D0B14);
        Theme.accentFill(c, bx, py + 3, w - 14 - (bx - x), 17, 6);
        c.textCentered(Fonts.SEMIBOLD, "Play", bx + (w - 14 - (bx - x)) / 2, py + 3 + (17 - Fonts.SEMIBOLD.height(8)) / 2, 8, Theme.onAccent());
        float ry = py + 34;
        c.rect(px, ry, 26, 14, 7, 0x26FFFFFF);
        Theme.accentFill(c, px, ry, 26, 14, 7);
        c.rect(px + 14, ry + 2, 10, 10, 5, Colors.WHITE);
        c.rect(px + 36, ry + 5, w - 28 - 36, 4, 2, 0x26FFFFFF);
        Theme.accentFill(c, px + 36, ry + 5, (w - 28 - 36) * 0.62f, 4, 2);
        c.rect(px + 36 + (w - 28 - 36) * 0.62f - 2.5f, ry, 5, 14, 2.5f, Colors.WHITE);

        c.rect(x + 12, y + h - 42, w - 24, 1, 0, Theme.BORDER);
        c.text(Fonts.SEMIBOLD, kind == Theme.Look.SMOOTH ? "Smooth" : "Pixel", px, y + h - 34, 11, Theme.TEXT);
        c.text(Fonts.REGULAR, kind == Theme.Look.SMOOTH ? "Inter, round corners" : "Minecraft's font, stepped corners", px, y + h - 19, 7, Theme.TEXT_MUTED);
        if (mine) {
            c.circle(x + w - 20, y + h - 26, 7.5f, Theme.accent());
            Icons.CHECK.draw(c, x + w - 20, y + h - 26, 9, Theme.onAccent());
        }
        Theme.look = outer;
    }

    /** The choice that ends the grey: sets the look and throws the wave from the card. */
    private void choose(Theme.Look kind, float x, float y) {
        if (picked == kind || waveT >= 0 && !coloured) return;
        var opt = AllerClient.options();
        opt.typeface.set(kind == Theme.Look.PIXEL ? ClientOptions.Typeface.MINECRAFT : ClientOptions.Typeface.INTER);
        opt.pixelate.set(kind == Theme.Look.PIXEL ? ClientOptions.Pixelate.EVERYTHING : ClientOptions.Pixelate.OFF);
        AllerClient.config().markDirty();
        boolean first = picked == null;
        picked = kind;
        waveX = x;
        waveY = y;
        waveFar = 50;
        for (float px : new float[] {0, vw}) for (float py : new float[] {0, vh}) waveFar = Math.max(waveFar, (float) Math.hypot(px - x, py - y) + 50);
        if (first && !coloured) {
            waveT = 0;
            waveThrown = false;
            Sounds.cue("block.respawn_anchor.charge", 1.7f, 0.7f);
        } else {
            // Already in colour: the look changes with a ripple and nothing more.
            Sounds.cue("block.amethyst_block.chime", 1.2f, 0.7f);
            burst(x, y, 26, 160);
        }
    }

    private float radius() {
        return waveFar * Easing.OUT_CUBIC.apply(Math.clamp((waveT - WAVE_HOLD) / WAVE_RUN, 0f, 1f));
    }

    private void wave(float dt) {
        waveT += dt;
        if (!waveThrown && waveT >= WAVE_HOLD) {
            waveThrown = true;
            Sounds.cue("entity.generic.explode", 0.7f, 0.4f);
            Sounds.cue("block.beacon.activate", 1.1f, 0.9f);
            Sounds.cue("entity.player.levelup", 1.25f, 0.5f);
            Sounds.cue("block.amethyst_cluster.break", 1.1f, 0.9f);
            burst(waveX, waveY, 130, 420);
        }
        if (waveThrown && waveT - dt < WAVE_HOLD + 0.45f && waveT >= WAVE_HOLD + 0.45f) Sounds.cue("entity.firework_rocket.twinkle", 1.1f, 0.5f);
        if (waveT >= WAVE_HOLD + WAVE_RUN + 0.05f) coloured = true;
    }

    /** Which look a thing at this place is drawn in: the chosen one once the wave has reached it. */
    private void look(float x, float y) {
        if (picked == null) Theme.look = Theme.Look.SMOOTH;
        else if (coloured || waveT < 0) Theme.look = null;
        else Theme.look = Math.hypot(x - waveX, y - waveY) < radius() ? picked : Theme.Look.SMOOTH;
    }

    /** The jolt the wave gives whatever it passes, as an addition to its scale. */
    private float kick(float x, float y) {
        if (waveT < WAVE_HOLD) return 0;
        float reach = Math.min(0.999f, (float) Math.hypot(x - waveX, y - waveY) / waveFar);
        float arrives = WAVE_HOLD + WAVE_RUN * (1 - (float) Math.cbrt(1 - reach));
        float s = waveT - arrives;
        return s < 0 ? 0 : 0.11f * (float) (Math.exp(-s * 5) * Math.sin(s * 17));
    }

    private void regrade(Canvas c) {
        if (coloured) return;
        Canvas.grade(0f, 0.85f);
        if (waveT >= 0) {
            // Stored in screen pixels, so it has to be set from outside this screen's own scale.
            c.push();
            c.scale(1 / k, 0, 0);
            c.wave(waveX * k, waveY * k, radius() * k, 46 * k);
            c.pop();
        }
    }

    private void keyStep(Canvas c, float mx, float my, boolean live, Settings.Key key, String title, String sub, String tip) {
        float cx = vw / 2;
        float y = heading(c, title, sub) + 18;
        int bound = key.get();
        boolean waiting = listening == key;

        // The key itself, pressed for real.
        boolean down = live && !waiting && bound != Settings.Key.NONE && Mc.isDown(bound);
        if (!down) keyArmed = true;
        if (live && down && !keyWasDown && keyArmed && since > 0.5f && !drawingOld) pressed(key);
        keyWasDown = down;

        String[] parts = waiting ? new String[] {key.chord ? "Press keys…" : "Press a key…"} : Mc.keyName(bound).split("\\+");
        if (parts.length == 0) parts = new String[] {Mc.keyName(bound)};
        float capH = 46, plus = 16, total = -plus;
        float[] widths = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            widths[i] = Math.max(capH * 1.15f, Fonts.BOLD.width(parts[i], 15) + 34);
            total += widths[i] + plus;
        }
        float a = appear(2), pop = passPop.target(keyPassed ? 1 : 0).update();
        float press = capPress.target(down ? 1 : 0).update();
        float x = cx - total / 2;
        c.pushAlpha(a);
        c.push();
        c.translate(0, (1 - a) * 14);
        c.scale(1 + 0.1f * (float) Math.sin(Math.clamp(pop, 0f, 1f) * Math.PI), cx, y + capH / 2);
        for (int i = 0; i < parts.length; i++) {
            boolean last = i == parts.length - 1;
            boolean held = waiting ? false : last ? down || partDown(bound, parts[i]) : partDown(bound, parts[i]);
            cap(c, parts[i], x, y, widths[i], capH, last ? Math.max(press, held ? 1 : 0) : held ? 1 : 0, waiting, keyPassed);
            x += widths[i];
            if (!last) c.textCentered(Fonts.SEMIBOLD, "+", x + plus / 2, y + (capH - Fonts.SEMIBOLD.height(12)) / 2, 12, Theme.TEXT_MUTED);
            x += plus;
        }
        c.pop();
        c.popAlpha();
        y += capH + 16;

        a = appear(3);
        c.pushAlpha(a);
        if (keyPassed) {
            String ok = "That works";
            float w = Fonts.SEMIBOLD.width(ok, 9.5f) + 14;
            Icons.CHECK.draw(c, cx - w / 2 + 4, y + Fonts.SEMIBOLD.height(9.5f) / 2, 10, Theme.SUCCESS);
            c.text(Fonts.SEMIBOLD, ok, cx - w / 2 + 14, y, 9.5f, Theme.SUCCESS);
        } else if (waiting) {
            c.textCentered(Fonts.MEDIUM, key.chord ? "Hold Ctrl, Alt or Shift and press a key. Escape cancels." : "Any key will do. Escape cancels.", cx, y, 9, Theme.TEXT_DIM);
        } else {
            String dots = ".".repeat(1 + (int) (clock * 2.5f) % 3);
            c.textCentered(Fonts.MEDIUM, bound == Settings.Key.NONE ? "No key is set: rebind it to try" : "Waiting for you to press it" + dots, cx, y, 9, Theme.TEXT_DIM);
        }
        y += 17;
        String clash = waiting || bound == Settings.Key.NONE || key.chord ? null : Mc.vanillaUse(bound);
        c.textCentered(Fonts.REGULAR, clash != null ? "Minecraft also uses this key for " + clash + ". Rebinding it here avoids the clash." : tip, cx, y, 7.5f,
                clash != null ? Theme.WARN : Theme.TEXT_MUTED);
        c.popAlpha();
        y += 20;

        a = appear(4);
        c.pushAlpha(a);
        rebind.label = waiting ? "Cancel" : keyPassed ? "Change key" : "Not working? Rebind";
        float rw = Math.max(86, Fonts.MEDIUM.width(rebind.label, 8) + 26), sw = 64;
        boolean both = !keyPassed;
        float bx = cx - (rw + (both ? sw + 6 : 0)) / 2;
        rebind.bounds(bx, y, rw, 19).draw(c, mx, my);
        skipStep.enabled = both;
        if (both) skipStep.bounds(bx + rw + 6, y, sw, 19).draw(c, mx, my);
        else skipStep.bounds(-1000, -1000, 0, 0);
        c.popAlpha();

        primary.label = "Continue";
        primaryAt = appear(5);
    }

    /** Whether the modifier a keycap stands for is held. */
    private static boolean partDown(int bound, String part) {
        return switch (part) {
            case "Ctrl" -> Mc.ctrlDown();
            case "Alt" -> Mc.altDown();
            case "Shift" -> Mc.shiftDown();
            default -> Mc.isDown(Settings.Key.code(bound));
        };
    }

    /** A key drawn large: it sinks and lights while held, breathes while it waits. */
    private void cap(Canvas c, String label, float x, float y, float w, float h, float press, boolean waiting, boolean passed) {
        float breathe = passed ? 1 : 0.5f + 0.5f * (float) Math.sin(clock * (waiting ? 6f : 2.4f));
        int glow = passed ? Theme.SUCCESS : Theme.accent();
        float sink = 4 * press;
        c.pixel(true);
        c.shadow(x, y + 4, w, h, 9, 14 + 8 * breathe, Colors.withAlpha(glow, (0.16f + 0.2f * breathe) * (1 - press) + 0.5f * press));
        c.rect(x, y + 5, w, h, 9, 0xFF0B0A11);
        c.rect(x, y + sink, w, h, 9, Colors.mix(0xFF1E1B2B, Theme.accent(), press * 0.85f));
        c.gradientV(x, y + sink, w, h, 9, 0x1EFFFFFF, 0x00FFFFFF);
        c.stroke(x, y + sink, w, h, 9, 1.2f, Colors.mix(Colors.withAlpha(glow, 0.35f + 0.4f * breathe), Colors.WHITE, press * 0.5f));
        c.pixel(false);
        float size = waiting ? 9.5f : 15;
        c.textCentered(waiting ? Fonts.MEDIUM : Fonts.BOLD, label, x + w / 2, y + sink + (h - Fonts.BOLD.height(size)) / 2, size,
                Colors.mix(Theme.TEXT, Theme.onAccent(), press));
    }

    /** The key under test went down: open what it opens. Coming back from it is the pass. */
    private void pressed(Settings.Key key) {
        boolean launcher = key == AllerClient.options().launcherKey;
        keyOpened = true;
        AllerClient.defer(() -> {
            // The launcher's own polling may already have opened it this frame.
            if (Mc.current() != this) return;
            Mc.setScreen(new ScreenHost(launcher ? new LauncherScreen(Mc.screen()) : new PaletteScreen(Mc.screen())));
        });
    }

    private void pass() {
        keyPassed = true;
        passPop.snap(0);
        Sounds.cue("entity.experience_orb.pickup", 1.1f, 0.5f);
        Sounds.cue("block.note_block.chime", 1.4f, 0.6f);
        burst(vw / 2, top() + 90, 40, 190);
    }

    private void listen() {
        Settings.Key key = switch (stage) {
            case PALETTE -> AllerClient.options().menuKey;
            case LAUNCHER -> AllerClient.options().launcherKey;
            case HUD -> AllerClient.options().hudEditorKey;
            default -> null;
        };
        listening = listening == key ? null : key;
    }

    private void bind(int code) {
        listening.set(code);
        listening = null;
        keyArmed = false;
        AllerClient.config().markDirty();
        Sounds.toggle(true);
    }

    private void hud(Canvas c, float mx, float my, boolean live) {
        float cx = vw / 2;
        float y = heading(c, "Put your HUD where you want it",
                "Every element drags, resizes and snaps into line in the HUD editor. It is on the pause menu under Layout, and in the launcher as Edit HUD layout.") + 12;
        float w = Math.min(248, vw - 60), h = w * 0.46f, x = cx - w / 2;
        float a = appear(2);
        c.pushAlpha(a);
        c.push();
        c.translate(0, (1 - a) * 14);
        // A small screen with a game's worth of HUD on it.
        c.shadow(x, y + 6, w, h, 9, 20, 0x66000000);
        c.rect(x, y, w, h, 9, 0xE00C0B12);
        c.gradientV(x, y, w, h, 9, Colors.withAlpha(Theme.accent(), 0.10f), 0x00000000);
        c.stroke(x, y, w, h, 9, 1, Theme.BORDER_STRONG);
        c.rect(cx - 5, y + h / 2 - 0.5f, 10, 1, 0, 0x88FFFFFF);
        c.rect(cx - 0.5f, y + h / 2 - 5, 1, 10, 0, 0x88FFFFFF);
        chip(c, x + 8, y + 22, 62, 11, "X 128  Y 64  Z -40");
        for (int i = 0; i < 4; i++) {
            float kx = x + 8 + (i == 0 ? 12 : (i - 1) * 12), ky = y + h - (i == 0 ? 31 : 19);
            Theme.chip(c, kx, ky, 11, 11, 3, i == 0 ? Colors.withAlpha(Theme.accent(), 0.7f) : Theme.GLASS_HUD);
            c.textCentered(Fonts.SEMIBOLD, "WASD".substring(i, i + 1), kx + 5.5f, ky + 2.6f, 5.5f, Theme.TEXT);
        }
        for (int i = 0; i < 4; i++) Theme.chip(c, x + w - 20, y + h - 20 - i * 13, 12, 11, 3, Theme.GLASS_HUD);
        // One element carried across by a pointer and snapped into the corner, over and over.
        float loop = clock % 9f, half = loop % 4.5f;
        boolean back = loop >= 4.5f;
        float carry = Easing.IN_OUT_CUBIC.apply(Math.clamp((half - 0.9f) / 1.5f, 0f, 1f));
        float from = x + 8, to = x + w - 8 - 40;
        float ex = back ? to + (from - to) * carry : from + (to - from) * carry, ey = y + 8;
        boolean held = half > 0.7f && half < 2.6f;
        float snap = Math.clamp(1 - Math.abs(half - 2.4f) / 0.35f, 0f, 1f);
        if (snap > 0.01f) {
            float gx = back ? x + 8 : x + w - 8;
            c.rect(gx - 0.5f, y + 3, 1, h - 6, 0, Colors.withAlpha(Theme.accent(), 0.9f * snap));
            c.rect(x + 3, y + 7.5f, w - 6, 1, 0, Colors.withAlpha(Theme.accent(), 0.9f * snap));
        }
        c.push();
        c.scale(held ? 1.08f : 1f, ex + 20, ey + 5.5f);
        if (held) c.shadow(ex, ey + 2, 40, 11, 3, 8, Colors.withAlpha(Theme.accent(), 0.5f));
        chip(c, ex, ey, 40, 11, "144 FPS");
        if (held) c.stroke(ex - 1, ey - 1, 42, 13, 4, 1, Theme.accent());
        c.pop();
        // The pointer arrives, carries, and leaves.
        float reach = Easing.OUT_CUBIC.apply(Math.clamp(half / 0.7f, 0f, 1f)), leave = Easing.IN_CUBIC.apply(Math.clamp((half - 2.8f) / 0.9f, 0f, 1f));
        float px = ex + 24 + (1 - reach) * 50 + leave * 36, py = ey + 7 + (1 - reach) * 40 + leave * 44;
        c.pushAlpha(Math.min(1f, half / 0.3f) * (1 - leave));
        c.push();
        c.rotate(-0.42f, px, py);
        c.polygon(px, py + 4.6f, 5.6f, 3, 0.8f, 0xFF0B0A11);
        c.polygon(px, py + 4.6f, 4.2f, 3, 0.6f, Colors.WHITE);
        c.pop();
        c.popAlpha();
        c.pop();
        c.popAlpha();
        y += h + 14;

        a = appear(3);
        c.pushAlpha(a);
        var key = AllerClient.options().hudEditorKey;
        boolean waiting = listening == key;
        String text = waiting ? "Press a key… (Escape cancels)" : key.get() == Settings.Key.NONE ? "Give the editor a key" : "Editor key: " + Mc.keyName(key.get());
        rebind.label = text;
        float rw = Fonts.MEDIUM.width(text, 8) + 28;
        rebind.bounds(cx - rw / 2, y, rw, 19).draw(c, mx, my);
        skipStep.bounds(-1000, -1000, 0, 0);
        c.popAlpha();
        primary.label = "Continue";
        primaryAt = appear(4);
    }

    private static void chip(Canvas c, float x, float y, float w, float h, String text) {
        Theme.chip(c, x, y, w, h, 3, Theme.GLASS_HUD);
        c.textCentered(Fonts.MEDIUM, text, x + w / 2, y + (h - Fonts.MEDIUM.height(5.5f)) / 2, 5.5f, Theme.TEXT);
    }

    private void mods(Canvas c, float mx, float my, boolean live) {
        float cx = vw / 2;
        float y = heading(c, "Start with a few mods",
                "Switch on what you like the sound of. There are " + AllerClient.modules().all().size() + " in all, a key press away in the palette.") + 12;
        int cols = vw > 520 ? 3 : 2;
        float gap = 6, tw = Math.min(152, (vw - 40 - gap * (cols - 1)) / cols), th = 30;
        float x0 = cx - (tw * cols + gap * (cols - 1)) / 2;
        int rows = Math.min((starters.size() + cols - 1) / cols, Math.max(1, (int) ((vh - 70 - y) / (th + gap))));
        for (int i = 0; i < Math.min(starters.size(), rows * cols); i++) {
            Module m = starters.get(i);
            float x = x0 + (i % cols) * (tw + gap), ty = y + (i / cols) * (th + gap);
            float a = appear(2 + i * 0.35f);
            boolean over = live && inside(mx, my, x, ty, tw, th);
            float hv = starterHover[i].target(over ? 1 : 0).update();
            c.pushAlpha(a);
            c.push();
            c.translate(0, (1 - a) * 12);
            c.pixel(true);
            c.rect(x, ty, tw, th, 7, 0xB80D0B14);
            c.rect(x, ty, tw, th, 7, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
            c.stroke(x, ty, tw, th, 7, 1, m.enabled() ? Colors.withAlpha(Theme.accent(), 0.7f) : Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hv));
            c.pixel(false);
            float room = tw - 16 - Toggle.W - 8;
            float nw = c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(m.name, 8.5f, room), x + 9, ty + 5.5f, 8.5f, Theme.TEXT);
            if (m.fairPlayNote != null && nw + 46 < room) c.text(Fonts.MEDIUM, "RESTRICTED", x + 9 + nw + 6, ty + 7.3f, 5.4f, Theme.WARN);
            c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(m.description, 6.6f, room), x + 9, ty + 17.5f, 6.6f, Theme.TEXT_MUTED);
            starterToggles[i].draw(c, x + tw - Toggle.W - 8, ty + (th - Toggle.H) / 2, m.enabled(), over);
            c.pop();
            c.popAlpha();
            hits.add(new Hit(x, ty, tw, th, () -> {
                m.setEnabled(!m.enabled());
                Sounds.toggle(m.enabled());
                AllerClient.config().markDirty();
            }));
        }
        primary.label = "Continue";
        primaryAt = appear(7);
    }

    private void fair(Canvas c) {
        float cx = vw / 2;
        float y = heading(c, "Fair play", AllerClient.NAME + " is a client, not a cheat. What that means in practice:") + 12;
        String[][] rows = {
                {"Nothing here gives an unfair advantage.", "No reach, no auto-anything, no seeing what you should not. It makes the game look and feel better, and that is all."},
                {"A few mods are restricted on some servers.", "Freelook, fullbright and waypoints, for instance. They carry a Restricted badge, and warn you once when switched on."},
                {"It never blocks them for you.", "Servers differ, so the choice stays yours: check the rules of the one you play on."},
        };
        Icons[] icons = {Icons.CHECK, Icons.REPORT, Icons.LICENCE};
        float w = Math.min(400, vw - 40), x = cx - w / 2;
        for (int i = 0; i < rows.length; i++) {
            float a = appear(2 + i);
            List<String> body = Fonts.REGULAR.wrap(rows[i][1], 7.6f, w - 50);
            float h = 22 + body.size() * 10.5f;
            c.pushAlpha(a);
            c.push();
            c.translate((1 - a) * -18, 0);
            Theme.panel(c, x, y, w, h, Theme.R_MD);
            int tint = i == 1 ? Theme.WARN : Theme.accent();
            c.circle(x + 19, y + h / 2, 10, Colors.withAlpha(tint, 0.18f));
            icons[i].draw(c, x + 19, y + h / 2, 11, tint);
            c.text(Fonts.SEMIBOLD, rows[i][0], x + 38, y + 7, 8.8f, Theme.TEXT);
            for (int l = 0; l < body.size(); l++) c.text(Fonts.REGULAR, body.get(l), x + 38, y + 19 + l * 10.5f, 7.6f, Theme.TEXT_DIM);
            c.pop();
            c.popAlpha();
            y += h + 7;
        }
        primary.label = "Understood";
        primaryAt = appear(5);
    }

    private void done(Canvas c) {
        float cx = vw / 2, y = Math.max(54, vh / 2 - 104);
        float a = Easing.OUT_BACK.apply(Math.clamp((st() - 0.2f) / 0.6f, 0f, 1f));
        c.pushAlpha(Math.min(1f, a));
        c.push();
        c.scale(0.6f + 0.4f * a, cx, y + 20);
        c.oval(cx, y + 20, 46, 46, 0.95f, Colors.withAlpha(Theme.accent(), 0.35f));
        Skins.drawFramedFace(c, cx - 20, y, 40);
        c.pop();
        c.popAlpha();
        y += 54;
        a = appear(2);
        String title = "You are set, " + Nav.playerName() + ".";
        float size = Math.min(22, 22 * (vw - 40) / Math.max(1f, Fonts.BOLD.width(title, 22)));
        c.pushAlpha(a);
        c.textCentered(Fonts.BOLD, title, cx, y + (1 - a) * 10, size, Theme.TEXT);
        c.popAlpha();
        y += 36;

        var opt = AllerClient.options();
        String[][] keys = {
                {Mc.keyName(opt.menuKey.get()), "The palette", "every mod and its settings"},
                {Mc.keyName(opt.launcherKey.get()), "The launcher", "any action, from anywhere"},
        };
        float w = Math.min(300, vw - 40), x = cx - w / 2;
        for (int i = 0; i < keys.length; i++) {
            a = appear(3 + i);
            c.pushAlpha(a);
            c.push();
            c.translate((1 - a) * (i == 0 ? -20 : 20), 0);
            Theme.panel(c, x, y, w, 26, Theme.R_MD);
            float kw = Theme.keycap(c, keys[i][0], x + 8, y + 6, 14);
            float tx = x + 16 + Math.max(kw, 44);
            tx += c.textMiddle(Fonts.SEMIBOLD, keys[i][1], tx, y, 26, 8.8f, Theme.TEXT) + 7;
            c.textMiddle(Fonts.REGULAR, keys[i][2], tx, y, 26, 7.6f, Theme.TEXT_MUTED);
            c.pop();
            c.popAlpha();
            y += 32;
        }
        a = appear(5);
        c.pushAlpha(a);
        c.textCentered(Fonts.REGULAR, "All of this can be changed later. To see it again, run Replay onboarding from the launcher.", cx, y + 4, 7.5f, Theme.TEXT_MUTED);
        c.popAlpha();
        primary.label = Game.inWorld() ? "Back to the game" : "Enter " + AllerClient.NAME;
        primaryAt = appear(6);
    }

    // ---- footer, effects, input ------------------------------------------------------------------

    private void footer(Canvas c, float mx, float my, boolean live) {
        float cx = vw / 2;
        look(cx, vh - 50);
        boolean ready = primaryReady();
        primary.enabled = ready;
        // An accent button drained of its colour is a pale slab with pale text on it: glass until the colour is in.
        primary.style = coloured || waveT >= 0 ? Button.Style.PRIMARY : Button.Style.GLASS;
        float pa = leaving != null ? 0 : primaryAt;
        if (pa > 0.01f) {
            float w = Math.max(140, Fonts.SEMIBOLD.width(primary.label, 9.5f) + 44);
            c.pushAlpha(Math.min(1f, pa));
            c.push();
            c.scale(0.85f + 0.15f * pa + kick(cx, vh - 50), cx, vh - 50);
            primary.bounds(cx - w / 2, vh - 62, w, 24).draw(c, live ? mx : -1000, live ? my : -1000);
            c.pop();
            c.popAlpha();
        } else {
            primary.bounds(-1000, -1000, 0, 0);
        }

        // Where this is in the walk: one pip a stage, the current one drawn out.
        int count = Stage.values().length - 1, at = stage.ordinal() - 1;
        float spacing = 9, x0 = cx - spacing * (count - 1) / 2, py = vh - 24;
        float pos = pip.target(at).update();
        for (int i = 0; i < count; i++) {
            float near = Math.max(0f, 1 - Math.abs(i - pos));
            float w = 3.2f + 8 * near;
            // Its neighbours make room.
            float px = x0 + i * spacing + Math.clamp(i - pos, -1f, 1f) * 4;
            int color = i <= at ? Colors.mix(Colors.withAlpha(Theme.accent(), 0.55f), Theme.accent(), near) : 0x30FFFFFF;
            c.rect(px - w / 2, py, w, 3.2f, 1.6f, color);
        }

        float show = Math.max(0.55f, Math.min(1f, holdEsc * 4));
        c.pushAlpha(show);
        float hx = 22, hy = vh - 22.4f;
        c.ring(hx, hy, 5.5f, 1.2f, 0x30FFFFFF);
        if (holdEsc > 0.01f) {
            // The ring fills as a row of beads: there is no arc in the shape shader.
            int beads = Math.round(holdEsc * 16);
            for (int i = 0; i < beads; i++) {
                float ang = -TAU / 4 + i / 16f * TAU;
                c.circle(hx + (float) Math.cos(ang) * 5.5f, hy + (float) Math.sin(ang) * 5.5f, 1.1f, Theme.TEXT);
            }
        }
        c.text(Fonts.REGULAR, "Hold Esc to skip", hx + 11, hy - Fonts.REGULAR.height(7) / 2, 7, Theme.TEXT_MUTED);
        c.popAlpha();
    }

    /** Holding Escape for a second ends the whole thing, keeping what was chosen so far. */
    private void hold(float dt) {
        boolean down = listening == null && Mc.isDown(GLFW.GLFW_KEY_ESCAPE) && since > 0.3f;
        holdEsc = Math.clamp(holdEsc + (down ? Motion.realDelta() / 1.0f : -Motion.realDelta() * 2.5f), 0f, 1f);
        if (holdEsc >= 1f) finish();
    }

    /** Everything that flies: the wave's rings, rays and flash, and the pieces thrown by it and by the ending. */
    private void effects(Canvas c) {
        Canvas.ungrade();
        float dt = drawingUnderlay ? 0 : Motion.delta();
        int accent = Theme.accent(), accent2 = Theme.accent2();
        if (waveT >= 0 && waveT < WAVE_HOLD) {
            // Drawing breath: the screen darkens round the card.
            c.rect(-20, -20, vw + 40, vh + 40, 0, Colors.withAlpha(Colors.BLACK, 0.4f * waveT / WAVE_HOLD));
            c.oval(waveX, waveY, 120, 120, 0.95f, Colors.withAlpha(accent, 0.5f * waveT / WAVE_HOLD));
        }
        if (waveT >= WAVE_HOLD && waveT < WAVE_HOLD + WAVE_RUN + 0.6f) {
            float s = waveT - WAVE_HOLD, p = Math.min(1f, s / WAVE_RUN), r = radius();
            float gone = 1 - p * p;
            for (int i = 0; i < 30; i++) {
                float ang = i * 2.399f + s * (i % 2 == 0 ? 0.5f : -0.35f);
                float inner = r * 0.2f, outer = r * (0.5f + 0.45f * ((i * 37) % 10) / 10f);
                c.line(waveX + (float) Math.cos(ang) * inner, waveY + (float) Math.sin(ang) * inner,
                        waveX + (float) Math.cos(ang) * outer, waveY + (float) Math.sin(ang) * outer,
                        1.1f, Colors.withAlpha(i % 3 == 0 ? Colors.WHITE : accent, 0.3f * gone * gone));
            }
            c.ring(waveX, waveY, r * 0.8f, 2 + 5 * (1 - p), Colors.withAlpha(accent2, 0.5f * gone));
            c.ring(waveX, waveY, r, 4 + 16 * (1 - p), Colors.withAlpha(accent, 0.9f * gone));
            c.ring(waveX, waveY, r + 5, 1.6f, Colors.withAlpha(Colors.WHITE, 0.9f * gone));
            c.oval(waveX, waveY, 90 + r * 0.3f, 90 + r * 0.3f, 0.95f, Colors.withAlpha(accent, 0.4f * (float) Math.exp(-s * 3)));
            float flash = 0.7f * (float) Math.exp(-s * 7);
            if (flash > 0.01f) c.rect(-20, -20, vw + 40, vh + 40, 0, Colors.withAlpha(Colors.lighten(accent, 0.6f), flash));
        }
        for (Iterator<Piece> it = pieces.iterator(); it.hasNext(); ) {
            Piece p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            float slow = (float) Math.exp(-p.drag * dt);
            p.vx *= slow;
            p.vy = p.vy * slow + p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.spin * dt;
            float a = Math.min(1f, (p.life - p.age) / (p.life * 0.4f));
            c.push();
            c.rotate(p.rot, p.x, p.y);
            if (p.sides == 1) c.star(p.x, p.y, p.size * 1.3f, Colors.fade(p.color, a));
            else if (p.sides == 2) c.rect(p.x - p.size, p.y - p.size * 0.4f, p.size * 2, p.size * 0.8f, p.size * 0.2f, Colors.fade(p.color, a));
            else c.polygon(p.x, p.y, p.size, p.sides, p.size * 0.14f, Colors.fade(p.color, a));
            c.pop();
        }
        regrade(c);
    }

    /** Throws {@code count} pieces outwards from a point, in the accent and white. */
    private void burst(float x, float y, int count, float speed) {
        int[] palette = {Theme.accent(), Theme.accent2(), Colors.lighten(Theme.accent(), 0.45f), Colors.WHITE};
        for (int i = 0; i < count; i++) {
            Piece p = new Piece();
            float ang = random.nextFloat() * TAU, v = speed * (0.25f + 0.75f * random.nextFloat());
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(ang) * v;
            p.vy = (float) Math.sin(ang) * v;
            p.spin = (random.nextFloat() - 0.5f) * 12;
            p.size = 1.6f + 4.2f * random.nextFloat() * random.nextFloat();
            p.life = 0.8f + 1.1f * random.nextFloat();
            p.drag = 2.2f;
            p.gravity = 30;
            p.color = palette[random.nextInt(palette.length)];
            p.sides = new int[] {1, 3, 4, 5, 6}[random.nextInt(5)];
            pieces.add(p);
        }
    }

    /** The ending: confetti from both lower corners. */
    private void celebrate() {
        Sounds.cue("ui.toast.challenge_complete", 1f, 0.5f);
        Sounds.cue("entity.firework_rocket.twinkle", 1f, 0.5f);
        int[] palette = {Theme.accent(), Theme.accent2(), Colors.lighten(Theme.accent(), 0.5f), Colors.WHITE, Theme.SUCCESS, Theme.WARN};
        for (int i = 0; i < 170; i++) {
            Piece p = new Piece();
            boolean left = i % 2 == 0;
            float ang = (left ? -1.05f : -2.09f) + (random.nextFloat() - 0.5f) * 0.8f, v = 260 + 380 * random.nextFloat();
            p.x = left ? -6 : vw + 6;
            p.y = vh + 4;
            p.vx = (float) Math.cos(ang) * v;
            p.vy = (float) Math.sin(ang) * v;
            p.spin = (random.nextFloat() - 0.5f) * 16;
            p.size = 2 + 3.4f * random.nextFloat();
            p.life = 2.2f + 1.6f * random.nextFloat();
            p.drag = 1.1f;
            p.gravity = 330;
            p.color = palette[random.nextInt(palette.length)];
            p.sides = random.nextFloat() < 0.55f ? 2 : new int[] {1, 3, 4, 6}[random.nextInt(4)];
            pieces.add(p);
        }
    }

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (stage == Stage.INTRO || isClosing() || leaving != null) return true;
        x /= k;
        y /= k;
        if (listening != null) {
            // A side or middle button can be the binding; a plain click on the button cancels, anywhere else too.
            if (button >= 2 && !listening.chord) bind(-2 - button);
            else if (!rebind.mouseDown(x, y, button)) listening = null;
            return true;
        }
        if (primary.mouseDown(x, y, button) || rebind.mouseDown(x, y, button) || skipStep.mouseDown(x, y, button)) return true;
        if (button != 0) return true;
        for (Hit h : hits) {
            if (inside(x, y, h.x, h.y, h.w, h.h)) {
                h.action.run();
                if (dragBar == 0) Sounds.click();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        dragBar = 0;
        if (stage == Stage.INTRO || leaving != null) return true;
        x /= k;
        y /= k;
        boolean keys = stage == Stage.PALETTE || stage == Stage.LAUNCHER || stage == Stage.HUD;
        primary.mouseUp(x, y, button);
        if (keys) rebind.mouseUp(x, y, button);
        if (keys && stage != Stage.HUD) skipStep.mouseUp(x, y, button);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (stage == Stage.INTRO || isClosing()) return true;
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE && (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT)) == 0) {
                listening = null;
            } else if (listening.chord) {
                // A modifier on its own is the start of a chord: keep waiting for the key that completes it.
                if (key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER) return true;
                bind(Settings.Key.pack(key, mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT)));
            } else {
                bind(key);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) advance();
        return true;
    }

    // ---- development -----------------------------------------------------------------------------

    /**
     * Drives the screen for the harness, which has no pointer: {@code intro:<seconds>}, a stage's
     * name in lower case, {@code smooth} or {@code pixel} to make the choice, {@code pass} for a key test.
     */
    public static void dev(String action) {
        if (!(Mc.current() instanceof OnboardingScreen s)) return;
        if (action.startsWith("intro:")) {
            s.stage = Stage.INTRO;
            s.started = true;
            s.intro.seek(Float.parseFloat(action.substring(6)));
        } else if (action.equals("smooth") || action.equals("pixel")) {
            s.choose(action.equals("pixel") ? Theme.Look.PIXEL : Theme.Look.SMOOTH, s.vw / 2 + (action.equals("pixel") ? 93 : -93), s.top() + 120);
        } else if (action.equals("pass")) {
            s.pass();
        } else if (action.equals("accent:pick")) {
            s.pickSwatch(2);
        } else {
            Stage to = Stage.valueOf(action.toUpperCase());
            if (to.ordinal() > Stage.THEME.ordinal() && s.picked == null) {
                s.picked = Theme.Look.SMOOTH;
                s.coloured = true;
            }
            if (to.ordinal() > Stage.ACCENT.ordinal()) s.accentPicked = true;
            s.go(to);
        }
    }
}
