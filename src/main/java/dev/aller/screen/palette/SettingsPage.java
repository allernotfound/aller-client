package dev.aller.screen.palette;

import dev.aller.AllerClient;
import dev.aller.hud.HudModule;
import dev.aller.module.Module;
import dev.aller.module.mods.VisualMods;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.screen.HudEditorScreen;
import dev.aller.setting.Setting;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.SettingsView;
import dev.aller.ui.widget.Toggle;

import java.util.ArrayList;
import java.util.List;

/** Settings for one module, or (with no module) the client-wide options. */
public final class SettingsPage extends Page {
    private final Module module;
    private final String title, blurb;
    private final SettingsView view = new SettingsView();
    private final Scroll scroll = new Scroll();
    private final Toggle toggle = new Toggle();
    private final List<Button> buttons = new ArrayList<>();
    private float bx, by, bw, bh;
    private float toggleX, toggleY;

    public SettingsPage(Module module) {
        this.module = module;
        this.title = module.name;
        this.blurb = module.description;
        List<Setting<?>> ordered = new ArrayList<>(module.settings());
        // The keybind is created first by the base class but reads better at the end.
        if (ordered.remove(module.keybind)) ordered.add(module.keybind);
        view.set(ordered);
        view.onChange = AllerClient.config()::markDirty;
        if (module instanceof HudModule && Game.inWorld() && !(Mc.current() instanceof HudEditorScreen)) {
            buttons.add(new Button("Move on screen", () -> Mc.open(new HudEditorScreen(Mc.screen()))));
        }
        buttons.add(new Button("Reset to defaults", () -> {
            module.resetToDefaults();
            AllerClient.config().markDirty();
        }).style(Button.Style.GHOST));
    }

    /** Client options. */
    public SettingsPage() {
        this.module = null;
        this.title = "Client settings";
        this.blurb = "Look, motion and behaviour of Aller itself. These apply to every profile.";
        view.set(AllerClient.options().settings());
        view.onChange = AllerClient.config()::markDirty;
        buttons.add(new Button("Reset to defaults", () -> {
            AllerClient.options().resetSettings();
            AllerClient.config().markDirty();
        }).style(Button.Style.GHOST));
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String subtitle() {
        return module == null ? "" : module.category.label;
    }

    @Override
    public void header(Canvas c, float right, float y, float h, float mx, float my) {
        if (module == null) return;
        toggleX = right - Toggle.W;
        toggleY = y + (h - Toggle.H) / 2;
        boolean over = mx >= toggleX - 4 && mx < right + 4 && my >= y && my < y + h;
        toggle.draw(c, toggleX, toggleY, module.enabled(), over);
    }

    @Override
    public boolean headerClick(float mx, float my, int button) {
        if (module == null || button != 0) return false;
        if (mx >= toggleX - 4 && mx < toggleX + Toggle.W + 4 && my >= toggleY - 6 && my < toggleY + Toggle.H + 6) {
            AllerClient.modules().userToggle(module);
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        float pad = 12, cw = w - pad * 2;
        List<String> lines = Fonts.REGULAR.wrap(blurb, 8f, cw - 4);
        List<String> note = module != null && module.fairPlayNote != null
                ? Fonts.REGULAR.wrap(module.fairPlayNote + " Aller never blocks it; the choice is yours.", 7.5f, cw - 30)
                : List.of();
        boolean preview = module instanceof VisualMods.Crosshair;
        float previewH = preview ? 58 : 0;
        float descH = lines.size() * 11f + 6 + previewH;
        float noteH = note.isEmpty() ? 0 : note.size() * 10f + 12 + 8;
        float buttonsH = 30;
        float content = 8 + descH + noteH + view.height() + buttonsH + 6;
        float off = scroll.update(content, h);
        float cy = y + 8 - off;

        for (String line : lines) {
            c.text(Fonts.REGULAR, line, x + pad + 2, cy, 8f, Theme.TEXT_DIM);
            cy += 11f;
        }
        cy += 6;
        if (preview) {
            // The crosshair over a bright and a dark scene, so both cases can be judged at once.
            float ph = previewH - 8, half = cw / 2;
            c.gradientV(x + pad, cy, cw, ph, Theme.R_MD, 0xFF8FB4E8, 0xFFBFD6F2);
            c.clip(x + pad + half, cy, half, ph);
            c.gradientV(x + pad, cy, cw, ph, Theme.R_MD, 0xFF1C2B1A, 0xFF0E150D);
            c.unclip();
            c.stroke(x + pad, cy, cw, ph, Theme.R_MD, 1, Theme.BORDER);
            c.clip(x + pad, cy, cw, ph);
            VisualMods.Crosshair cross = (VisualMods.Crosshair) module;
            cross.drawAt(c, x + pad + half / 2, cy + ph / 2, 0, 0);
            cross.drawAt(c, x + pad + half * 1.5f, cy + ph / 2, 0, 0);
            c.unclip();
            cy += previewH;
        }
        if (!note.isEmpty()) {
            float nh = noteH - 8;
            c.rect(x + pad, cy, cw, nh, Theme.R_MD, Colors.withAlpha(Theme.WARN, 0.10f));
            c.stroke(x + pad, cy, cw, nh, Theme.R_MD, 1, Colors.withAlpha(Theme.WARN, 0.30f));
            c.circle(x + pad + 11, cy + nh / 2, 2.5f, Theme.WARN);
            float ny = cy + 6;
            for (String line : note) {
                c.text(Fonts.REGULAR, line, x + pad + 22, ny, 7.5f, Colors.mix(Theme.WARN, Colors.WHITE, 0.55f));
                ny += 10f;
            }
            cy += noteH;
        }
        view.draw(c, x + pad - 4, cy, cw + 8, mx, my, hot);
        cy += view.height() + 8;

        float bxp = x + pad;
        for (Button b : buttons) {
            b.textSize = 8f;
            float w0 = Fonts.MEDIUM.width(b.label, 8f) + 22;
            b.bounds(bxp, cy, w0, 18);
            b.draw(c, hot ? mx : -999, my);
            bxp += w0 + 6;
        }
        scroll.drawBar(c, x + w - 5, y + 4, h - 8);
    }

    private boolean inBody(float mx, float my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        if (!inBody(mx, my)) return false;
        for (Button b : buttons) if (b.mouseDown(mx, my, button)) return true;
        return view.mouseDown(mx, my, button);
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        boolean used = view.mouseUp(mx, my, button);
        for (Button b : buttons) used |= b.mouseUp(inBody(mx, my) ? mx : -999, my, button);
        return used;
    }

    @Override
    public boolean mouseScroll(float mx, float my, float amount) {
        scroll.scroll(amount);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        return view.keyDown(key, mods);
    }

    @Override
    public boolean charTyped(int codepoint) {
        return view.charTyped(codepoint);
    }

    @Override
    public boolean capturing() {
        return view.capturing();
    }
}
