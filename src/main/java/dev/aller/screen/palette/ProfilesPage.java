package dev.aller.screen.palette;

import dev.aller.AllerClient;
import dev.aller.config.Config;
import dev.aller.feature.AutoProfiles;
import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import dev.aller.ui.widget.Toggle;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Switch, create and delete profiles, and edit the rules that switch them automatically. */
public final class ProfilesPage extends Page {
    private static final float ROW = 24;

    private static final class RuleRow {
        final Button kind, profile;
        final TextField match = new TextField("");
        float y;

        RuleRow(AutoProfiles.Rule rule, ProfilesPage page) {
            kind = new Button("", () -> {
                var kinds = AutoProfiles.Kind.values();
                rule.kind = kinds[(rule.kind.ordinal() + 1) % kinds.length];
                AutoProfiles.save();
            });
            profile = new Button("", () -> {
                List<String> names = page.names;
                rule.profile = names.get((Math.max(0, names.indexOf(rule.profile)) + 1) % names.size());
                AutoProfiles.save();
            });
            kind.textSize = profile.textSize = 7.5f;
            match.textSize = 7.5f;
            match.maxLength = 48;
            match.setText(rule.match);
            match.onChange = v -> {
                rule.match = v;
                AutoProfiles.save();
            };
        }
    }

    private final Scroll scroll = new Scroll();
    private final TextField newName = new TextField("New profile name");
    private final Button create = new Button("Create", this::createProfile).style(Button.Style.PRIMARY);
    private final Button addRule = new Button("Add rule", this::addRule);
    private final Toggle autoToggle = new Toggle();
    private final Map<String, Spring> hover = new HashMap<>();
    private final Map<AutoProfiles.Rule, RuleRow> ruleRows = new IdentityHashMap<>();
    private List<String> names = new ArrayList<>();
    private float bx, by, bw, bh;
    private float listY, autoY;
    private int refresh;

    public ProfilesPage() {
        names = AllerClient.config().profileNames();
        newName.maxLength = 24;
        newName.textSize = 8f;
        create.textSize = addRule.textSize = 8f;
    }

    @Override
    public String title() {
        return "Profiles";
    }

    @Override
    public String subtitle() {
        return "Active: " + AllerClient.config().activeProfile();
    }

    private void createProfile() {
        String name = Config.sanitise(newName.text);
        if (name.isEmpty()) return;
        AutoProfiles.manualSwitch(name);
        newName.setText("");
        names = AllerClient.config().profileNames();
        Toasts.info("Profile: " + name, "Created as a copy of your current setup");
    }

    private void addRule() {
        AutoProfiles.Rule r = new AutoProfiles.Rule();
        r.profile = AllerClient.config().activeProfile();
        AutoProfiles.rules().add(r);
        AutoProfiles.save();
    }

    @Override
    public void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        if (refresh++ % 120 == 0) names = AllerClient.config().profileNames();
        if (!hot) mx = -999;
        List<AutoProfiles.Rule> rules = AutoProfiles.rules();
        float pad = 12, cw = w - pad * 2;
        float content = 8 + names.size() * ROW + 6 + 22 + 16 + ROW + 14 + rules.size() * ROW + 6 + 20 + 10;
        float cy = y + 8 - scroll.update(content, h);
        String active = AllerClient.config().activeProfile();

        listY = cy;
        for (String name : names) {
            boolean isActive = name.equals(active);
            boolean over = mx >= x + pad && mx < x + pad + cw && my >= cy && my < cy + ROW;
            float hv = hover.computeIfAbsent(name, k -> Spring.snappy(0)).target(over ? 1 : 0).update();
            c.rect(x + pad, cy + 1, cw, ROW - 2, Theme.R_SM,
                    isActive ? Colors.withAlpha(Theme.accent(), 0.16f + 0.06f * hv) : Colors.withAlpha(Colors.WHITE, 0.03f + 0.05f * hv));
            if (isActive) c.rect(x + pad, cy + 6, 2, ROW - 12, 1, Theme.accent());
            c.textMiddle(Fonts.MEDIUM, name, x + pad + 10 + 2 * hv, cy, ROW, 8.5f, isActive ? Theme.TEXT : Theme.TEXT_DIM);
            if (isActive) {
                c.textRight(Fonts.SEMIBOLD, "ACTIVE", x + pad + cw - 10, cy + (ROW - Fonts.SEMIBOLD.height(6.5f)) / 2, 6.5f, Theme.accent());
            } else if (!name.equals(Config.DEFAULT_PROFILE)) {
                boolean overX = over && mx >= x + pad + cw - 22;
                Icons.CLOSE.draw(c, x + pad + cw - 12, cy + ROW / 2, 9, Colors.fade(overX ? Theme.DANGER : Theme.TEXT_MUTED, hv));
            }
            cy += ROW;
        }
        cy += 6;
        newName.bounds(x + pad, cy, cw - 62, 20);
        newName.draw(c, mx, my);
        create.enabled = !Config.sanitise(newName.text).isEmpty();
        create.bounds(x + pad + cw - 56, cy, 56, 20);
        create.draw(c, mx, my);
        cy += 22 + 16;

        autoY = cy;
        c.textMiddle(Fonts.SEMIBOLD, "Switch automatically", x + pad + 2, cy, ROW - 8, 9f, Theme.TEXT);
        c.textMiddle(Fonts.REGULAR, "The first matching rule wins. Your own choice returns when none match.",
                x + pad + 2, cy + 11, ROW - 8, 7f, Theme.TEXT_MUTED);
        boolean overAuto = mx >= x + pad + cw - Toggle.W - 6 && mx < x + pad + cw && my >= cy && my < cy + ROW;
        autoToggle.draw(c, x + pad + cw - Toggle.W, cy + 2, AllerClient.options().autoProfiles.get(), overAuto);
        cy += ROW + 14;

        ruleRows.keySet().removeIf(r -> !rules.contains(r));
        AutoProfiles.Rule matched = AutoProfiles.applied();
        for (AutoProfiles.Rule rule : rules) {
            RuleRow row = ruleRows.computeIfAbsent(rule, r -> new RuleRow(r, this));
            row.y = cy;
            float rx = x + pad;
            if (rule == matched) c.circle(rx - 5, cy + ROW / 2, 1.8f, Theme.SUCCESS);
            row.kind.label = rule.kind.label;
            row.kind.bounds(rx, cy + 2, 86, ROW - 4);
            row.kind.draw(c, mx, my);
            rx += 90;
            float profW = 84, matchW = cw - 90 - profW - 4 - 18 - 16;
            if (rule.kind.hint != null) {
                row.match.placeholder = rule.kind.hint;
                row.match.bounds(rx, cy + 2, matchW, ROW - 4);
                row.match.draw(c, mx, my);
            } else {
                row.match.focused = false;
            }
            rx += matchW + 2;
            Icons.FORWARD.draw(c, rx + 6, cy + ROW / 2, 9f, Theme.TEXT_MUTED);
            rx += 14;
            row.profile.label = Fonts.MEDIUM.truncate(rule.profile, 7.5f, profW - 14);
            row.profile.bounds(rx, cy + 2, profW, ROW - 4);
            row.profile.draw(c, mx, my);
            rx += profW + 4;
            boolean overX = mx >= rx && mx < rx + 18 && my >= cy && my < cy + ROW;
            Icons.CLOSE.draw(c, rx + 9, cy + ROW / 2, 9, overX ? Theme.DANGER : Theme.TEXT_MUTED);
            cy += ROW;
        }
        cy += 6;
        addRule.bounds(x + pad, cy, 64, 18);
        addRule.draw(c, mx, my);
        scroll.drawBar(c, x + w - 5, y + 4, h - 8);
    }

    private boolean inBody(float mx, float my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        newName.focused = false;
        for (RuleRow r : ruleRows.values()) r.match.focused = false;
        if (!inBody(mx, my) || button != 0) return false;
        float pad = 12, cw = bw - pad * 2, x = bx;

        float cy = listY;
        String active = AllerClient.config().activeProfile();
        for (String name : new ArrayList<>(names)) {
            if (my >= cy && my < cy + ROW && mx >= x + pad && mx < x + pad + cw) {
                boolean deletable = !name.equals(active) && !name.equals(Config.DEFAULT_PROFILE);
                if (deletable && mx >= x + pad + cw - 22) {
                    AllerClient.config().deleteProfile(name);
                    names = AllerClient.config().profileNames();
                    Toasts.info("Profile deleted", name);
                } else if (!name.equals(active)) {
                    AutoProfiles.manualSwitch(name);
                    Toasts.info("Profile: " + name, "Switched");
                }
                Sounds.click();
                return true;
            }
            cy += ROW;
        }
        if (newName.mouseDown(mx, my, button) || create.mouseDown(mx, my, button) || addRule.mouseDown(mx, my, button)) return true;
        if (my >= autoY && my < autoY + ROW && mx >= x + pad + cw - Toggle.W - 6) {
            var opt = AllerClient.options().autoProfiles;
            opt.toggle();
            Sounds.toggle(opt.get());
            AllerClient.config().markDirty();
            return true;
        }
        List<AutoProfiles.Rule> rules = AutoProfiles.rules();
        for (AutoProfiles.Rule rule : new ArrayList<>(rules)) {
            RuleRow row = ruleRows.get(rule);
            if (row == null || my < row.y || my >= row.y + ROW) continue;
            if (mx >= x + pad + cw - 18) {
                rules.remove(rule);
                AutoProfiles.save();
                Sounds.click();
                return true;
            }
            if (row.kind.mouseDown(mx, my, button) || row.profile.mouseDown(mx, my, button)) return true;
            if (rule.kind.hint != null && row.match.mouseDown(mx, my, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        if (!inBody(mx, my)) mx = -999;
        boolean used = create.mouseUp(mx, my, button) | addRule.mouseUp(mx, my, button);
        for (RuleRow r : new ArrayList<>(ruleRows.values())) {
            used |= r.kind.mouseUp(mx, my, button) | r.profile.mouseUp(mx, my, button);
        }
        return used;
    }

    @Override
    public boolean mouseScroll(float mx, float my, float amount) {
        scroll.scroll(amount);
        return true;
    }

    private TextField focused() {
        if (newName.focused) return newName;
        for (RuleRow r : ruleRows.values()) if (r.match.focused) return r.match;
        return null;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        TextField f = focused();
        if (f == null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            f.focused = false;
        } else if (key == GLFW.GLFW_KEY_ENTER) {
            if (f == newName) createProfile();
            f.focused = false;
        } else {
            f.keyDown(key, mods);
        }
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        TextField f = focused();
        return f != null && f.charTyped(codepoint);
    }

    @Override
    public boolean capturing() {
        return focused() != null;
    }
}
