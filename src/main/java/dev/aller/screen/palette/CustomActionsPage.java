package dev.aller.screen.palette;

import dev.aller.AllerClient;
import dev.aller.command.CustomActions;
import dev.aller.command.CustomActions.Custom;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's own launcher actions: a name and the one chat line or command it sends. Click an
 * action to change either.
 */
public final class CustomActionsPage extends Page {
    private static final float ROW = 28, EDITOR = 50, PAD = 12, FIELD = 20;

    private static final class RowState {
        final Spring hover = Spring.snappy(0);
        final Spring expand = Spring.snappy(0);
        float y, h;
    }

    private final Scroll scroll = new Scroll();
    private final TextField name = new TextField("Name, for example Home");
    private final TextField message = new TextField("Message, or /command");
    private final TextField editName = new TextField("Name");
    private final TextField editMessage = new TextField("Message, or /command");
    private final Button add = new Button("Add", this::add).style(Button.Style.PRIMARY);
    private final Map<Custom, RowState> states = new IdentityHashMap<>();
    private Custom editing;
    private float bx, by, bw, bh;

    public CustomActionsPage() {
        name.maxLength = editName.maxLength = CustomActions.NAME_MAX;
        message.maxLength = editMessage.maxLength = CustomActions.MESSAGE_MAX;
        name.textSize = message.textSize = editName.textSize = editMessage.textSize = 8f;
        add.textSize = 8f;
        editName.onChange = text -> {
            if (editing != null && !text.isBlank()) CustomActions.rename(editing, text);
        };
        editMessage.onChange = text -> {
            if (editing == null || text.isBlank()) return;
            editing.message = CustomActions.clean(text, CustomActions.MESSAGE_MAX);
            CustomActions.save();
        };
    }

    @Override
    public String title() {
        return "Custom actions";
    }

    @Override
    public String subtitle() {
        int n = CustomActions.all().size();
        return n == 0 ? "" : n + (n == 1 ? " action" : " actions") + "  ·  run them from the launcher";
    }

    private void add() {
        if (name.text.isBlank() || message.text.isBlank()) return;
        CustomActions.add(name.text, message.text);
        name.setText("");
        message.setText("");
        name.focused = message.focused = false;
    }

    private void edit(Custom c) {
        editing = editing == c ? null : c;
        editName.focused = editMessage.focused = false;
        if (editing != null) {
            editName.setText(editing.name);
            editMessage.setText(editing.message);
        }
        Sounds.click();
    }

    @Override
    public void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        if (!hot) mx = -999;
        float cw = w - PAD * 2, left = x + PAD;
        List<Custom> all = CustomActions.all();
        states.keySet().removeIf(k -> !all.contains(k));
        if (editing != null && !all.contains(editing)) editing = null;

        String key = Mc.keyName(AllerClient.options().launcherKey.get());
        List<String> intro = Fonts.REGULAR.wrap("Each action sends one chat line or one command when you run it from the launcher (" + key
                + "). Nothing is sent by itself and actions have no key of their own, so servers see ordinary typing.", 7.5f, cw - 4);
        float content = 8 + intro.size() * 10f + 8 + FIELD * 2 + 4 + 10;
        for (Custom each : all) content += ROW + EDITOR * Math.clamp(states.computeIfAbsent(each, k -> new RowState()).expand.get(), 0f, 1f);
        float cy = y + 8 - scroll.update(content + 8, h);

        for (String line : intro) {
            c.text(Fonts.REGULAR, line, left + 2, cy, 7.5f, Theme.TEXT_DIM);
            cy += 10f;
        }
        cy += 8;
        name.bounds(left, cy, cw, FIELD);
        name.draw(c, mx, my);
        cy += FIELD + 4;
        message.bounds(left, cy, cw - 54, FIELD);
        message.draw(c, mx, my);
        add.enabled = !name.text.isBlank() && !message.text.isBlank();
        add.bounds(left + cw - 48, cy, 48, FIELD);
        add.draw(c, mx, my);
        cy += FIELD + 10;

        for (Custom each : all) {
            RowState st = states.get(each);
            boolean isEditing = each == editing;
            float e = Math.clamp(st.expand.target(isEditing ? 1 : 0).update(), 0f, 1f);
            st.y = cy;
            st.h = ROW + EDITOR * e;
            boolean over = mx >= left && mx < left + cw && my >= cy && my < cy + ROW;
            float hv = st.hover.target(over || isEditing ? 1 : 0).update();
            c.rect(left, cy + 1, cw, st.h - 2, Theme.R_SM, Colors.withAlpha(Colors.WHITE, 0.03f + 0.05f * hv));
            if (e > 0.02f) c.stroke(left, cy + 1, cw, st.h - 2, Theme.R_SM, 1, Colors.withAlpha(Theme.accent(), 0.45f * e));
            c.rect(left + 8, cy + 7, 14, 14, 4.5f, Colors.withAlpha(Theme.accent(), 0.16f + 0.14f * hv));
            c.textCentered(Fonts.SEMIBOLD, "→", left + 15, cy + 7 + (14 - Fonts.SEMIBOLD.height(7.5f)) / 2, 7.5f, Colors.lighten(Theme.accent(), 0.35f));
            c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(each.name, 8.5f, cw - 62), left + 30, cy + 5, 8.5f, Theme.TEXT);
            c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(each.message, 7f, cw - 62), left + 30, cy + 16, 7f, Theme.TEXT_MUTED);
            boolean overX = over && mx >= left + cw - 22;
            Icons.CLOSE.draw(c, left + cw - 12, cy + ROW / 2, 9, overX ? Theme.DANGER : Colors.fade(Theme.TEXT_MUTED, 0.5f + 0.5f * hv));

            if (e > 0.02f) {
                c.pushAlpha(e);
                c.clip(left, cy + ROW, cw, Math.max(0, st.h - ROW));
                if (isEditing) {
                    editName.bounds(left + 10, cy + ROW + 2, cw - 20, FIELD);
                    editName.draw(c, mx, my);
                    editMessage.bounds(left + 10, cy + ROW + 2 + FIELD + 4, cw - 20, FIELD);
                    editMessage.draw(c, mx, my);
                }
                c.unclip();
                c.popAlpha();
            }
            cy += st.h;
        }
        scroll.drawBar(c, x + w - 5, y + 4, h - 8);
    }

    private boolean inBody(float mx, float my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private TextField[] fields() {
        return new TextField[] {name, message, editName, editMessage};
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        for (TextField f : fields()) f.focused = false;
        if (!inBody(mx, my) || button != 0) return false;
        if (name.mouseDown(mx, my, button) || message.mouseDown(mx, my, button) || add.mouseDown(mx, my, button)) return true;
        float cw = bw - PAD * 2, left = bx + PAD;
        for (Custom each : new ArrayList<>(CustomActions.all())) {
            RowState st = states.get(each);
            if (st == null || my < st.y || my >= st.y + st.h || mx < left || mx >= left + cw) continue;
            if (my < st.y + ROW) {
                if (mx >= left + cw - 22) {
                    CustomActions.remove(each);
                    Sounds.click();
                } else {
                    edit(each);
                }
                return true;
            }
            if (each == editing && !editName.mouseDown(mx, my, button)) editMessage.mouseDown(mx, my, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        return add.mouseUp(inBody(mx, my) ? mx : -999, my, button);
    }

    @Override
    public boolean mouseScroll(float mx, float my, float amount) {
        scroll.scroll(amount);
        return true;
    }

    private TextField focused() {
        for (TextField f : fields()) if (f.focused) return f;
        return null;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        TextField f = focused();
        if (f == null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            f.focused = false;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_TAB) {
            // Name leads on to the message; Enter on the message adds the action.
            f.focused = false;
            if (f == name) message.focused = true;
            else if (f == message && key == GLFW.GLFW_KEY_ENTER) add();
            else if (f == editName) editMessage.focused = true;
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
