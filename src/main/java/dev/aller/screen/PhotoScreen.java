package dev.aller.screen;

import dev.aller.feature.Photo;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.setting.Setting;
import dev.aller.setting.Settings;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.SettingsView;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Photo mode: the world with the HUD off and one panel of sliders. Dragging turns the camera
 * round, the wheel zooms, Enter saves a picture with the panel left out of it. See {@code feature/Photo}.
 */
public final class PhotoScreen extends AllerScreen {
    private static final float PANEL = 236, PAD = 8;

    private final Settings.Choice<Photo.View> view = new Settings.Choice<>("view", "Camera", Photo.View.BEHIND);
    private final Settings.Num fov = new Settings.Num("fov", "Field of view", 70f, 10f, 120f, 1f).suffix("°");
    private final Settings.Num roll = new Settings.Num("roll", "Roll", 0f, -90f, 90f, 1f).suffix("°");
    private final Settings.Bool freeze = new Settings.Bool("freeze", "Freeze the world", false);
    private final Settings.Bool focusOn = new Settings.Bool("focus_on", "Depth of field", false);
    private final Settings.Num focusAt = new Settings.Num("focus_at", "Focus", 0f, 0f, 64f, 0.5f)
            .format(v -> v <= 0 ? "Automatic" : String.format("%.1f blocks", v));
    private final Settings.Bool gradeOn = new Settings.Bool("grade_on", "Colour grading", false);

    private final SettingsView settings = new SettingsView();
    private final Scroll scroll = new Scroll();
    private final Button take = new Button("Take picture", Photo::shoot).style(Button.Style.PRIMARY).icon(Icons.CAMERA);
    private final Button done = new Button("Done", this::close).style(Button.Style.GLASS);
    private final Spring panel = Spring.snappy(1);
    private boolean panelShown = true, dragging;
    private float lastX, lastY, panelX;

    public PhotoScreen() {
        view.describe("Where the camera stands. It only ever turns on the spot");
        freeze.describe("Singleplayer only: time stops while you frame the picture");
        freeze.visibleWhen(() -> Mc.mc().hasSingleplayerServer());
        focusAt.describe("Automatic keeps your character sharp, or what you look at in first person");
        focusAt.visibleWhen(focusOn::get);
        view.onChange(Photo::view);
        fov.onChange(Photo::fov);
        roll.onChange(Photo::roll);
        focusOn.onChange(Modules.DEPTH_OF_FIELD::setEnabled);
        gradeOn.onChange(Modules.COLOUR_GRADING::setEnabled);
        focusAt.onChange(v -> Modules.DEPTH_OF_FIELD.manual = v);

        var focus = Modules.DEPTH_OF_FIELD;
        var grade = Modules.COLOUR_GRADING;
        settings.set(List.of(view, fov, roll, freeze));
        settings.header("Depth of field");
        settings.add(focusOn).add(focusAt).add(focus.strength).add(focus.near);
        // The grading mod's own rows, with their own rules for when they show.
        settings.header("Colour");
        settings.add(gradeOn);
        for (Setting<?> s : grade.settings()) {
            if (s == grade.keybind) continue;
            if (s.section != null) settings.header(s.section);
            settings.row(s);
        }
    }

    @Override
    public void opened() {
        super.opened();
        Photo.begin();
        view.set(Photo.view());
        fov.set(Photo.fov());
        focusOn.set(Modules.DEPTH_OF_FIELD.enabled());
        gradeOn.set(Modules.COLOUR_GRADING.enabled());
    }

    @Override
    public void closed() {
        Modules.DEPTH_OF_FIELD.manual = 0;
        Photo.end();
    }

    @Override
    public boolean blurBehind() {
        return false;
    }

    @Override
    public boolean pausesGame() {
        return freeze.get();
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        if (dragging) {
            Photo.turn((mx - lastX) * 0.3f, (my - lastY) * 0.3f);
            lastX = mx;
            lastY = my;
        }
        if (Photo.hiding()) return;
        float t = Math.clamp(panel.target(panelShown ? 1 : 0).update(), 0f, 1f) * fade();
        String hint = panelShown ? "Drag to look round  ·  Wheel zooms  ·  Enter takes the picture  ·  H hides this" : "H brings the panel back";
        c.pushAlpha(0.75f * fade());
        float hw = Fonts.MEDIUM.width(hint, 7.5f) + 16;
        Theme.chip(c, 10, height - 26, hw, 16, 8, Theme.GLASS_HUD);
        c.textMiddle(Fonts.MEDIUM, hint, 18, height - 26, 16, 7.5f, Theme.TEXT);
        c.popAlpha();

        if (t > 0.02f) {
            float ph = height - 20;
            panelX = width - (PANEL + 10) * t;
            c.pushAlpha(t);
            Theme.panel(c, panelX, 10, PANEL, ph, Theme.R_LG);
            c.text(Fonts.BOLD, "Photo mode", panelX + PAD + 4, 10 + PAD + 1, 11f, Theme.TEXT);
            float top = 10 + PAD + 20, bottom = 10 + ph - 30;
            c.clip(panelX, top, PANEL, bottom - top);
            float off = scroll.update(settings.height() + 4, bottom - top);
            boolean hot = mx >= panelX && my >= top && my < bottom && !dragging;
            settings.draw(c, panelX + PAD - 4, top - off, PANEL - PAD * 2 + 8, mx, my, hot);
            c.unclip();
            scroll.drawBar(c, panelX + PANEL - 5, top, bottom - top);
            float bw = (PANEL - PAD * 2 - 6) * 0.62f;
            take.textSize = 8.5f;
            done.textSize = 8.5f;
            take.bounds(panelX + PAD, bottom + 6, bw, 20);
            done.bounds(panelX + PAD + bw + 6, bottom + 6, PANEL - PAD * 2 - bw - 6, 20);
            take.draw(c, mx, my);
            done.draw(c, mx, my);
            c.popAlpha();
        }
        Toasts.draw(c);
    }

    /** For the dev harness: a rolled, zoomed picture with the depth of field and a grade on. */
    public void dev() {
        roll.set(12f);
        fov.set(48f);
        focusOn.set(true);
        gradeOn.set(true);
        Photo.turn(35f, 8f);
    }

    private boolean overPanel(float mx) {
        return panelShown && mx >= panelX;
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        if (overPanel(mx)) {
            if (take.mouseDown(mx, my, button) || done.mouseDown(mx, my, button)) return true;
            return settings.mouseDown(mx, my, button);
        }
        if (button == 0 || button == 1) {
            dragging = true;
            lastX = mx;
            lastY = my;
        }
        return true;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        dragging = false;
        boolean used = settings.mouseUp(mx, my, button);
        used |= take.mouseUp(mx, my, button);
        used |= done.mouseUp(mx, my, button);
        return used;
    }

    @Override
    public boolean mouseScroll(float mx, float my, float amount) {
        if (overPanel(mx)) scroll.scroll(amount);
        else fov.set(fov.get() - amount * 3f);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (settings.capturing()) return settings.keyDown(key, mods);
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> Photo.shoot();
            case GLFW.GLFW_KEY_H -> panelShown = !panelShown;
            case GLFW.GLFW_KEY_R -> {
                roll.set(0f);
                fov.set(Mc.mc().options.fov().get().floatValue());
            }
            default -> {
                return super.keyDown(key, mods);
            }
        }
        return true;
    }
}
