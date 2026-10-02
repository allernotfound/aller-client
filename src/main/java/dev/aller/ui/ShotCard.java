package dev.aller.ui;

import dev.aller.AllerClient;
import dev.aller.command.Launcher;
import dev.aller.feature.Shots;
import dev.aller.feature.Shots.Shot;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Os;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Sounds;
import dev.aller.platform.Tex;
import dev.aller.screen.shots.ShotsScreen;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * The card that slides in at the top right when a screenshot has been taken: the picture, and what
 * can be done with it straight away. In a world there is no pointer, so each action has a key with
 * Ctrl that only works while the card is up; wherever there is a pointer the card can be clicked.
 * A screenshot taken while it shows waits one frame for it to step aside, and then takes its place.
 */
public final class ShotCard {
    private static final float W = 168, PAD = 6, CELLS = 24, TOP = 8;

    private enum Act {
        OPEN(Icons.IMAGE, "O", GLFW.GLFW_KEY_O), COPY(Icons.COPY, "C", GLFW.GLFW_KEY_C),
        DELETE(Icons.TRASH, "Del", GLFW.GLFW_KEY_DELETE), FOLDER(Icons.FOLDER, "E", GLFW.GLFW_KEY_E);

        final Icons icon;
        final String legend;
        final int key;

        Act(Icons icon, String legend, int key) {
            this.icon = icon;
            this.legend = "Ctrl " + legend;
            this.key = key;
        }
    }

    private static final Spring slide = Spring.bouncy(0), pop = Spring.snappy(1);
    private static final Spring[] hovers = {Spring.snappy(0), Spring.snappy(0), Spring.snappy(0), Spring.snappy(0)};
    private static Shot shot;
    /** Deleted from the card: it stays, offering to take that back, for as long as that can be done. */
    private static boolean removed;
    /** Stepped aside for a screenshot being taken; the next one carries the count on. */
    private static boolean aside;
    private static float age, reach, noteAge;
    private static int count, lastFrame = -10, hot = -1;
    private static boolean over;
    private static String note;

    private ShotCard() {}

    public static void show(Shot taken) {
        count = shot != null || aside ? count + 1 : 1;
        aside = false;
        shot = taken;
        removed = false;
        note = null;
        age = 0;
        if (Motion.reduced()) pop.snap(1);
        else pop.snap(0.9f).target(1);
    }

    /** Whether the card was on screen in the frame a screenshot would capture. */
    public static boolean inFrame() {
        return shot != null && lastFrame >= Shots.frame() - 1 && slide.get() > 0.02f;
    }

    /** Takes the card off screen at once, for the screenshot about to be taken. */
    public static void hide() {
        shot = null;
        aside = true;
        slide.snap(0);
    }

    /** True while Ctrl with one of the card's keys belongs to the card, so nothing else should act on that key. */
    public static boolean claiming() {
        return shot != null && Mc.ctrlDown() && taking();
    }

    private static boolean taking() {
        if (shot == null || slide.target() < 0.5f) return false;
        // The screenshots screen has the same keys for the picture in front.
        if (Mc.current() instanceof ShotsScreen) return false;
        Screen screen = Mc.screen();
        return screen == null ? Mc.mc().player != null : Launcher.allowed(screen, true);
    }

    private static float life() {
        return AllerClient.options().shotCardTime.get();
    }

    // ---- drawing ---------------------------------------------------------------------------------

    /**
     * Draws the card in the canvas's own units, once a frame whoever asks.
     *
     * @return how far down it reaches, for the toasts that queue beneath it
     */
    public static float draw(Canvas c) {
        if (shot == null) return 0;
        if (lastFrame == Shots.frame()) return reach;
        lastFrame = Shots.frame();

        Shots.Deleted undo = Shots.lastDeleted();
        if (removed && (undo == null || undo.shot() != shot)) removed = false;
        boolean pointer = Mc.screen() != null;
        if (!(over && pointer)) age += Motion.realDelta();
        boolean leaving = !removed && age > life();
        float s = slide.target(leaving ? 0 : 1).update();
        if (leaving && s < 0.02f) {
            shot = null;
            over = false;
            return reach = 0;
        }

        float iw = W - PAD * 2, shape = shot.width > 0 ? Math.clamp(shot.height / (float) shot.width, 0.42f, 0.75f) : 0.5625f;
        float ih = iw * shape, h = PAD + ih + 3 + CELLS + 6;
        float x = c.width() - 8 - W + (1 - s) * (W + 16), y = TOP;
        over = pointer && c.hovered(x, y, W, h);

        c.pushAlpha(Math.clamp(s, 0f, 1f));
        Theme.panel(c, x, y, W, h, Theme.R_LG);

        float ix = x + PAD, iy = y + PAD;
        c.push();
        c.scale(pop.update(), ix + iw / 2, iy + ih / 2);
        Tex tex = Shots.thumb(shot);
        if (tex != null) {
            c.pushAlpha(removed ? 0.3f : 1f);
            c.picture(tex, ix, iy, iw, ih, Theme.R_MD, true);
            c.popAlpha();
        } else {
            float pulse = 0.06f + 0.04f * (float) Math.sin(Motion.time() * 4);
            c.rect(ix, iy, iw, ih, Theme.R_MD, Colors.withAlpha(Colors.WHITE, pulse));
        }
        c.gradientV(ix, iy + ih - 22, iw, 22, Theme.R_MD, 0x00000000, 0xB3000000);
        c.stroke(ix, iy, iw, ih, Theme.R_MD, 1, Theme.BORDER);
        if (note != null && (noteAge += Motion.realDelta()) > 1.8f) note = null;
        String title = note != null ? note : removed ? "Deleted" : count > 1 ? count + " screenshots" : "Screenshot saved";
        c.text(Fonts.SEMIBOLD, title, ix + 6, iy + ih - 12.5f, 7.6f, Theme.TEXT);
        c.pop();

        hot = -1;
        float cy = iy + ih + 3;
        if (removed) {
            cell(c, 0, Icons.RELOAD, "Undo  •  Ctrl Z", ix, cy, iw, pointer);
        } else {
            Act[] acts = Act.values();
            float cw = iw / acts.length;
            for (int i = 0; i < acts.length; i++) cell(c, i, acts[i].icon, acts[i].legend, ix + i * cw, cy, cw, pointer);
        }

        float left = removed && undo != null ? undo.left() : Math.clamp(1 - age / life(), 0f, 1f);
        c.rect(x + 8, y + h - 3.5f, (W - 16) * left, 1.2f, 0.6f, Colors.withAlpha(removed ? Theme.DANGER : Theme.accent(), 0.6f));
        c.popAlpha();
        return reach = (h + 5) * Math.clamp(s, 0f, 1f);
    }

    private static void cell(Canvas c, int index, Icons icon, String legend, float x, float y, float w, boolean pointer) {
        boolean on = pointer && c.hovered(x, y, w, CELLS);
        if (on) hot = index;
        float hv = hovers[index].target(on ? 1 : 0).update();
        c.rect(x + 1, y, w - 2, CELLS, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.08f * hv));
        int danger = !removed && index == Act.DELETE.ordinal() ? Colors.lighten(Theme.DANGER, 0.2f) : Theme.TEXT;
        icon.draw(c, x + w / 2, y + 8, 9.5f, Colors.mix(Theme.TEXT_DIM, danger, hv));
        c.textCentered(Fonts.MEDIUM, legend, x + w / 2, y + 15, 5.8f, Colors.mix(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv));
    }

    // ---- input -----------------------------------------------------------------------------------

    /** A key press on its way to the game. @return true if the card took it */
    public static boolean key(int key, int mods) {
        if ((mods & GLFW.GLFW_MOD_CONTROL) == 0 || (mods & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT)) != 0 || !taking()) return false;
        if (removed) {
            if (key != GLFW.GLFW_KEY_Z) return false;
            undo();
            return true;
        }
        for (Act act : Act.values()) {
            if (act.key == key) {
                run(act);
                return true;
            }
        }
        return false;
    }

    /** A left click on its way to the screen in front. @return true if it landed on the card */
    public static boolean click() {
        if (shot == null || Mc.screen() == null || lastFrame < Shots.frame() - 1 || !over) return false;
        if (hot >= 0) {
            if (removed) undo();
            else run(Act.values()[hot]);
        }
        return true;
    }

    private static void run(Act act) {
        Shot target = shot;
        Sounds.click();
        switch (act) {
            case OPEN -> {
                Screen parent = Mc.screen();
                age = life() + 1;
                // Between frames, not from inside a key or mouse event.
                AllerClient.defer(() -> Mc.setScreen(new ScreenHost(new ShotsScreen(parent, target))));
            }
            case COPY -> {
                say("Copying…");
                age = Math.min(age, life() - 2.5f);
                Shots.copy(target, ok -> {
                    if (shot == target) say(ok ? "Copied to the clipboard" : "Could not copy it");
                });
            }
            case DELETE -> removed = Shots.delete(target);
            case FOLDER -> Os.reveal(target.file);
        }
    }

    private static void undo() {
        Sounds.click();
        if (Shots.undo() == null) return;
        removed = false;
        age = 0;
    }

    private static void say(String text) {
        note = text;
        noteAge = 0;
    }

    /** Stands in for the keys the self-test cannot press. */
    public static void dev(String action) {
        if (shot == null) return;
        switch (action) {
            case "open" -> run(Act.OPEN);
            case "copy" -> run(Act.COPY);
            case "delete" -> run(Act.DELETE);
            case "undo" -> undo();
            default -> {}
        }
    }
}
