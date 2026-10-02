package dev.aller.screen.palette;

import dev.aller.feature.Chat;
import dev.aller.feature.ChatFormats;
import dev.aller.feature.ChatFormats.Kind;
import dev.aller.feature.ChatFormats.Rule;
import dev.aller.platform.Canvas;
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
 * How chat lines are read on each server: the separators that end the author's name, and the
 * player's own formats for private messages and chat lines. A box at the bottom shows how any
 * pasted line would be read.
 */
public final class ChatFormatsPage extends Page {
    private static final float PAD = 12, FIELD = 20, ROW = 24, KIND_W = 74, SERVER_W = 92;

    private static final class RowState {
        final TextField server = new TextField("Any server");
        final TextField format = new TextField("{name} whispers to you: {message}");
        final Spring hover = Spring.snappy(0);
        float y;
    }

    private final Scroll scroll = new Scroll();
    private final TextField separators = new TextField(ChatFormats.DEFAULT_SEPARATORS);
    private final TextField test = new TextField("Paste a chat line to see how it is read");
    private final Button add = new Button("Add format", this::add).style(Button.Style.PRIMARY);
    private final Button restore = new Button("Restore defaults", this::restore).style(Button.Style.GHOST);
    private final Map<Rule, RowState> states = new IdentityHashMap<>();
    private float bx, by, bw, bh;

    public ChatFormatsPage() {
        separators.maxLength = ChatFormats.SEPARATORS_MAX;
        separators.textSize = test.textSize = 8f;
        test.maxLength = 256;
        add.textSize = restore.textSize = 8f;
        separators.setText(ChatFormats.separators());
        separators.onChange = ChatFormats::setSeparators;
    }

    @Override
    public String title() {
        return "Chat formats";
    }

    @Override
    public String subtitle() {
        return "How mentions and name colours find who wrote a line";
    }

    private void add() {
        Rule r = ChatFormats.add();
        for (TextField f : fields()) f.focused = false;
        state(r).format.focused = true;
    }

    private void restore() {
        ChatFormats.restoreDefaults();
        states.clear();
        separators.setText(ChatFormats.separators());
    }

    private RowState state(Rule rule) {
        return states.computeIfAbsent(rule, r -> {
            RowState st = new RowState();
            st.server.maxLength = ChatFormats.SERVER_MAX;
            st.format.maxLength = ChatFormats.FORMAT_MAX;
            st.server.textSize = st.format.textSize = 7.5f;
            st.server.setText(r.server);
            st.format.setText(r.format);
            st.server.onChange = text -> {
                r.server = text;
                ChatFormats.save();
            };
            st.format.onChange = text -> {
                r.format = text;
                ChatFormats.save();
            };
            return st;
        });
    }

    private static float label(Canvas c, String text, float x, float y) {
        c.text(Fonts.SEMIBOLD, text.toUpperCase(), x + 2, y, 6.4f, Theme.TEXT_MUTED);
        return y + 11;
    }

    private static float paragraph(Canvas c, String text, float x, float y, float w) {
        for (String line : Fonts.REGULAR.wrap(text, 7.5f, w - 4)) {
            c.text(Fonts.REGULAR, line, x + 2, y, 7.5f, Theme.TEXT_DIM);
            y += 10f;
        }
        return y;
    }

    @Override
    public void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        if (!hot) mx = -999;
        float cw = w - PAD * 2, left = x + PAD;
        List<Rule> all = ChatFormats.all();
        states.keySet().removeIf(k -> !all.contains(k));

        float cy = y + 8 - scroll.get();
        float top = cy;
        cy = paragraph(c, "Servers format chat however they like and never say who wrote a line, so Aller Client works it out from the text. "
                + "A line is read as author, separator, message; your own name as the author never pings you.", left, cy, cw);
        cy += 8;
        cy = label(c, "Separators", left, cy);
        separators.bounds(left, cy, cw, FIELD);
        separators.draw(c, mx, my);
        cy += FIELD + 4;
        cy = paragraph(c, "What comes between a name and the message, each followed by a space in chat. Separate them with spaces.", left, cy, cw);
        cy += 10;

        cy = label(c, "Formats", left, cy);
        cy = paragraph(c, "For lines the separators get wrong, and for private messages. Write the line with {name} for the sender, "
                + "{message} for what was said and {any} for anything else, or start with regex: for a regular expression with groups "
                + "called name and message. Leave the server blank for a format used everywhere.", left, cy, cw);
        cy += 6;
        for (Rule rule : all) {
            RowState st = state(rule);
            st.y = cy;
            boolean over = mx >= left && mx < left + cw && my >= cy && my < cy + ROW;
            float hv = st.hover.target(over ? 1 : 0).update();
            float ry = cy + (ROW - FIELD) / 2;
            boolean overKind = over && mx < left + KIND_W;
            c.pixel(true);
            c.rect(left, ry, KIND_W, FIELD, Theme.R_MD, Colors.withAlpha(Theme.accent(), rule.kind == Kind.WHISPER ? 0.22f : 0.10f));
            c.stroke(left, ry, KIND_W, FIELD, Theme.R_MD, 1, overKind ? Colors.withAlpha(Theme.accent(), 0.9f) : Theme.BORDER);
            c.pixel(false);
            c.textCentered(Fonts.MEDIUM, rule.kind.label, left + KIND_W / 2, ry + (FIELD - Fonts.MEDIUM.height(7f)) / 2, 7f, Theme.TEXT);
            st.server.bounds(left + KIND_W + 4, ry, SERVER_W, FIELD);
            st.server.draw(c, mx, my);
            float fx = left + KIND_W + SERVER_W + 8, fw = cw - KIND_W - SERVER_W - 8 - 20;
            st.format.bounds(fx, ry, fw, FIELD);
            st.format.draw(c, mx, my);
            if (!rule.valid()) c.stroke(fx, ry, fw, FIELD, Theme.R_MD, 1, Theme.DANGER);
            boolean overX = over && mx >= left + cw - 18;
            Icons.CLOSE.draw(c, left + cw - 9, cy + ROW / 2, 9, overX ? Theme.DANGER : Colors.fade(Theme.TEXT_MUTED, 0.5f + 0.5f * hv));
            cy += ROW;
        }
        cy += 4;
        add.bounds(left, cy, 70, FIELD);
        add.draw(c, mx, my);
        restore.bounds(left + 74, cy, 96, FIELD);
        restore.draw(c, mx, my);
        cy += FIELD + 14;

        cy = label(c, "Try a line", left, cy);
        test.bounds(left, cy, cw, FIELD);
        test.draw(c, mx, my);
        cy += FIELD + 5;
        if (!test.text.isBlank()) {
            String[] verdict = verdict(test.text);
            c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(verdict[0], 8f, cw - 4), left + 2, cy, 8f, Theme.TEXT);
            c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(verdict[1], 7.5f, cw - 4), left + 2, cy + 11, 7.5f, Theme.TEXT_DIM);
        }
        cy += 26;
        scroll.update(cy - top + 8, h);
        scroll.drawBar(c, x + w - 5, y + 4, h - 8);
    }

    /** What Aller makes of a line, in two short sentences. */
    private static String[] verdict(String line) {
        ChatFormats.Line parsed = ChatFormats.parse(line);
        if (parsed == null) return new String[] {"Nobody wrote this", "No format or separator matches, so it counts as a server line."};
        int[] name = ChatFormats.nameIn(line, parsed);
        String who = name == null ? parsed.author(line).strip() : line.substring(name[0], name[1]);
        String body = "Message: " + line.substring(Math.min(parsed.bodyStart(), line.length())).strip();
        if (parsed.whisper()) return new String[] {"Private message from " + (who.isEmpty() ? "someone" : who), body};
        return new String[] {Chat.mine(line, parsed) ? "Your own message: it will not ping" : "Chat line by " + (who.isEmpty() ? "an unknown author" : who), body};
    }

    private boolean inBody(float mx, float my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private List<TextField> fields() {
        List<TextField> out = new ArrayList<>();
        out.add(separators);
        for (Rule r : ChatFormats.all()) {
            RowState st = states.get(r);
            if (st == null) continue;
            out.add(st.server);
            out.add(st.format);
        }
        out.add(test);
        return out;
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        for (TextField f : fields()) f.focused = false;
        if (!inBody(mx, my) || button != 0) return false;
        if (separators.mouseDown(mx, my, button) || test.mouseDown(mx, my, button)) return true;
        if (add.mouseDown(mx, my, button) || restore.mouseDown(mx, my, button)) return true;
        float cw = bw - PAD * 2, left = bx + PAD;
        for (Rule rule : new ArrayList<>(ChatFormats.all())) {
            RowState st = states.get(rule);
            if (st == null || my < st.y || my >= st.y + ROW || mx < left || mx >= left + cw) continue;
            if (mx >= left + cw - 18) {
                ChatFormats.remove(rule);
                Sounds.click();
            } else if (mx < left + KIND_W) {
                rule.kind = rule.kind == Kind.WHISPER ? Kind.CHAT : Kind.WHISPER;
                ChatFormats.save();
                Sounds.click();
            } else if (!st.server.mouseDown(mx, my, button)) {
                st.format.mouseDown(mx, my, button);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        float x = inBody(mx, my) ? mx : -999;
        return add.mouseUp(x, my, button) | restore.mouseUp(x, my, button);
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
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            f.focused = false;
        } else if (key == GLFW.GLFW_KEY_TAB) {
            // On to the next box: server, then its format, then the next row.
            List<TextField> all = fields();
            f.focused = false;
            all.get((all.indexOf(f) + 1) % all.size()).focused = true;
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
