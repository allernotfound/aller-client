package dev.aller.screen;

import dev.aller.command.Command;
import dev.aller.command.Command.Group;
import dev.aller.command.Commands;
import dev.aller.command.History;
import dev.aller.command.Search;
import dev.aller.command.Step;
import dev.aller.module.Module;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.Sounds;
import dev.aller.setting.Settings;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import dev.aller.ui.widget.Toggle;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The launcher: one bar that runs anything. Where the palette is for browsing mods, this is for
 * doing a thing and getting back to the game: type a few letters, press Enter. It opens over
 * whatever is on screen (a world, an Aller screen or a Minecraft menu) and puts it back afterwards.
 *
 * <p>A command that needs a value turns the bar into a {@link Step}: a slider that changes the
 * game live, a line of text, or a list to pick from. Steps stack, and Escape walks back out.
 */
public final class LauncherScreen extends AllerScreen {
    private static final float W = 372, HEADER = 34, ROW = 26, SECTION = 18, FOOTER = 20, MAX_ROWS = 8;
    private static final float NAME = 8.5f, DETAIL = 6.8f;
    private static final int LIMIT = 40;

    private static final class Row {
        String section;
        Command command;
        boolean[] hits;
        float score;
        /** 1 to 9 for a pinned command listed with its number, else 0. */
        int pin;
        float y, h;

        boolean selectable() {
            return section == null;
        }
    }

    private static final class RowAnim {
        final Toggle toggle = new Toggle();
        final Spring hover = Spring.snappy(0);
    }

    /** A step being answered, and what to go back to if it is abandoned. */
    private record Level(Step step, Command owner, String query, float original) {}

    private final Screen parent;
    private final TextField search = new TextField("");
    private final List<Row> rows = new ArrayList<>();
    private final Deque<Level> levels = new ArrayDeque<>();
    private final Map<String, RowAnim> anims = new HashMap<>();
    private final Scroll scroll = new Scroll();
    private final Spring selY = new Spring(0, 620f, 42f), selH = new Spring(ROW, 620f, 42f);
    private final Spring bodyH = Spring.snappy(0), danger = Spring.snappy(0), knob = new Spring(0, 520f, 36f);
    private List<Command> commands;
    private Command armed;
    private String empty = "";
    private int selected = -1;
    private float contentH;
    private boolean selPrimed, bodyPrimed, knobPrimed, dragging;
    private float px, py, pw, listY, listH;
    private float lastMx = -1, lastMy = -1;
    private int frames;

    public LauncherScreen(Screen parent) {
        this.parent = parent;
        search.bare = true;
        search.focused = true;
        search.textSize = 10f;
        search.maxLength = 256;
        search.onChange = text -> changed();
        Commands.prime();
        commands = Commands.snapshot();
        field();
        rebuild();
    }

    /** Opens with a command already chosen: its step ready to answer, or its confirmation showing. */
    public LauncherScreen(Screen parent, Command start) {
        this(parent);
        if (start.step != null) {
            push(start, start.step);
        } else {
            search.setText(start.name);
            rebuild();
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).command == start) selected = i;
            if (start.danger) armed = start;
        }
    }

    /** Runs a command chosen somewhere else (the palette), opening the launcher only if it has to ask something. */
    public static void invoke(Screen parent, Command command) {
        if (command.step != null || command.danger) {
            Mc.setScreen(new ScreenHost(new LauncherScreen(parent, command)));
        } else {
            History.used(command.key);
            command.run.accept(parent);
        }
    }

    @Override
    public AllerScreen underlay() {
        return parent instanceof ScreenHost host ? host.screen : null;
    }

    @Override
    public Screen vanillaUnderlay() {
        return parent instanceof ScreenHost ? null : parent;
    }

    @Override
    public boolean pausesGame() {
        return parent != null && parent.isPauseScreen();
    }

    /** Over a bare world nothing is blurred: a value being dialled in shows its effect on the game behind. */
    @Override
    public boolean blurBehind() {
        return parent != null;
    }

    @Override
    public void close() {
        close(this::restore);
    }

    private void restore() {
        // Settings changed here may have switched a menu's restyling on or off.
        MenuSkin.invalidate();
        Mc.setScreen(parent);
    }

    private Step step() {
        return levels.isEmpty() ? null : levels.peek().step;
    }

    // ---- what is listed --------------------------------------------------------------------------

    private void refresh() {
        commands = Commands.snapshot();
        rebuild();
    }

    private void changed() {
        armed = null;
        if (step() instanceof Step.Num n) {
            Float typed = number(search.text);
            if (typed != null) n.set.accept(n.clamp(typed));
            return;
        }
        rebuild();
        scroll.reset();
    }

    private static Float number(String text) {
        try {
            return Float.parseFloat(text.trim().replace("%", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void rebuild() {
        rows.clear();
        empty = "";
        String raw = search.text.trim(), q = raw.toLowerCase();
        Step step = step();
        if (step instanceof Step.Pick pick) {
            List<Row> found = new ArrayList<>();
            for (Command c : pick.options.get()) {
                if (!c.available.getAsBoolean()) continue;
                boolean[] hits = new boolean[c.name.length()];
                float score = q.isEmpty() ? 1 : Search.match(q, c.name, hits);
                if (score <= 0 && !q.isEmpty() && c.detail.toLowerCase().contains(q)) score = 20;
                if (score > 0) found.add(row(c, q.isEmpty() ? null : hits, score));
            }
            if (!q.isEmpty()) found.sort((a, b) -> Float.compare(b.score, a.score));
            rows.addAll(found);
            if (rows.isEmpty()) empty = q.isEmpty() ? pick.empty : "Nothing matches";
        } else if (step == null) {
            if (q.isEmpty()) home();
            else if (q.startsWith("/")) command(raw);
            else if (q.startsWith(">")) filtered(q.substring(1).trim(), false);
            else if (q.startsWith("#")) filtered(q.substring(1).trim(), true);
            else if (q.startsWith("@")) waypoints(q.substring(1).trim());
            else results(raw, q);
        }
        selected = -1;
        for (int i = 0; i < rows.size() && selected < 0; i++) if (rows.get(i).selectable()) selected = i;
        arrange();
    }

    private static Row row(Command c, boolean[] hits, float score) {
        Row r = new Row();
        r.command = c;
        r.hits = hits;
        r.score = score;
        return r;
    }

    private void section(String label) {
        Row r = new Row();
        r.section = label;
        rows.add(r);
    }

    /** Before anything is typed: pinned commands, the last few run, then what fits the moment. */
    private void home() {
        List<String> shown = new ArrayList<>();
        List<Row> pinned = new ArrayList<>();
        List<String> pins = History.pins();
        for (int i = 0; i < pins.size(); i++) {
            Command c = Commands.find(commands, pins.get(i));
            if (c == null || !c.available.getAsBoolean()) continue;
            Row r = row(c, null, 0);
            r.pin = i + 1;
            pinned.add(r);
            shown.add(pins.get(i));
        }
        if (!pinned.isEmpty()) {
            section("Pinned");
            rows.addAll(pinned);
        }
        List<Row> recent = new ArrayList<>();
        for (String key : History.recent()) {
            if (shown.contains(key) || recent.size() >= 4) continue;
            Command c = Commands.find(commands, key);
            if (c == null || !c.available.getAsBoolean()) continue;
            recent.add(row(c, null, 0));
            shown.add(key);
        }
        if (!recent.isEmpty()) {
            section("Recent");
            rows.addAll(recent);
        }
        boolean world = Game.inWorld();
        List<Row> suggested = new ArrayList<>();
        for (Command c : commands) {
            if (suggested.size() >= 6 || !(world ? c.suggestWorld : c.suggestMenu) || shown.contains(c.key)) continue;
            if (c.available.getAsBoolean()) suggested.add(row(c, null, 0));
        }
        if (!suggested.isEmpty()) {
            section("Suggested");
            rows.addAll(suggested);
        }
        // Then everything there is, a group at a time, for scrolling through rather than searching.
        for (Group group : Group.values()) {
            if (group == Group.RESULT) continue;
            boolean headed = false;
            for (Command c : commands) {
                if (c.group != group || !c.available.getAsBoolean()) continue;
                if (!headed) section(group.label);
                headed = true;
                rows.add(row(c, null, 0));
            }
        }
    }

    /** "/home": the rest of the line is sent as a command. */
    private void command(String raw) {
        Command send = Commands.find(commands, "send");
        Command ready = send == null || !send.available.getAsBoolean() || raw.length() < 2 ? null : send.withArg(raw);
        if (ready != null) rows.add(row(ready, null, 1));
        else empty = Game.inWorld() ? "Type the command to send" : "Join a world to send commands";
    }

    private static boolean isSetting(Command c) {
        return c.group == Group.OPTION || c.group == Group.SETTING || c.key != null && c.key.startsWith("aller.");
    }

    /** ">" lists actions and "#" settings; with nothing after the prefix, all of them under headings. */
    private void filtered(String q, boolean settings) {
        List<Row> found = new ArrayList<>();
        Group last = null;
        for (Command c : commands) {
            if (c.group == Group.MOD || c.group == Group.WAYPOINT || isSetting(c) != settings || !c.available.getAsBoolean()) continue;
            if (q.isEmpty()) {
                // Mod settings only when asked for by name: there are hundreds.
                if (c.group == Group.SETTING) continue;
                if (c.group != last) section(c.group.label);
                last = c.group;
                rows.add(row(c, null, 0));
            } else {
                boolean[] hits = new boolean[c.name.length()];
                float score = score(c, q, hits);
                if (score > 0) found.add(row(c, hits, score));
            }
        }
        add(found);
        if (rows.isEmpty()) empty = settings ? "No setting matches" : "No action matches";
    }

    private void waypoints(String q) {
        List<Row> found = new ArrayList<>();
        for (Command c : commands) {
            if (c.group != Group.WAYPOINT) continue;
            boolean[] hits = new boolean[c.name.length()];
            float score = q.isEmpty() ? 1 : Search.match(q, c.name, hits);
            if (score > 0) found.add(row(c, q.isEmpty() ? null : hits, score));
        }
        if (!q.isEmpty()) found.sort((a, b) -> Float.compare(b.score, a.score));
        rows.addAll(found);
        if (rows.isEmpty()) empty = !Game.inWorld() ? "Join a world to see its waypoints" : q.isEmpty() ? "No waypoints in this world yet" : "No waypoint matches";
    }

    private void results(String raw, String q) {
        List<Row> found = new ArrayList<>();
        for (Command c : Commands.results(raw)) found.add(row(c, null, 400));

        // A value on the same line: "fov 90", "waypoint Base".
        int space = raw.lastIndexOf(' ');
        String head = space > 0 ? q.substring(0, space).trim() : "", tail = space > 0 ? raw.substring(space + 1) : "";
        boolean numeric = number(tail) != null && !head.isEmpty();
        for (Command c : commands) {
            if (!c.available.getAsBoolean()) continue;
            if (numeric && c.step instanceof Step.Num) {
                float fit = head.equals(c.alias) ? 220 : Search.match(head, c.name, null);
                if (c.group == Group.SETTING) fit -= 18;
                Command ready = fit >= 100 ? c.withArg(tail) : null;
                if (ready != null) found.add(row(ready, null, fit + 40));
            }
            if (c.alias != null && c.step instanceof Step.Text && q.startsWith(c.alias + " ")) {
                Command ready = c.withArg(raw.substring(c.alias.length() + 1));
                if (ready != null) found.add(row(ready, null, 300));
            }
            boolean[] hits = new boolean[c.name.length()];
            float score = score(c, q, hits);
            if (score > 0) found.add(row(c, hits, score));
        }
        add(found);
        if (rows.isEmpty()) empty = "Nothing matches";
    }

    private void add(List<Row> found) {
        found.sort((a, b) -> Float.compare(b.score, a.score));
        rows.addAll(found.size() > LIMIT ? found.subList(0, LIMIT) : found);
    }

    private static float score(Command c, String q, boolean[] hits) {
        float s = Search.match(q, c.name, hits);
        // Mod settings run to hundreds, so loose letter-by-letter matches on them would drown everything else.
        if (c.group == Group.SETTING && s < 100) s = 0;
        if (c.alias != null && c.alias.startsWith(q)) s = Math.max(s, 130);
        if (!c.keywords.isEmpty() && c.keywords.contains(q)) s = Math.max(s, 60);
        if (s <= 0 && q.length() >= 3 && c.group != Group.SETTING && c.detail.toLowerCase().contains(q)) s = 25;
        if (s <= 0) return 0;
        if (c.group == Group.SETTING) s -= 18;
        return s + Math.min(History.uses(c.key), 6) * 2f;
    }

    private void arrange() {
        float y = 3;
        for (Row r : rows) {
            r.y = y;
            r.h = r.section != null ? SECTION : ROW;
            y += r.h;
        }
        contentH = y + 3;
    }

    private void select(int index, boolean reveal) {
        if (index < 0 || index >= rows.size() || !rows.get(index).selectable()) return;
        if (index != selected) armed = null;
        selected = index;
        if (reveal) {
            Row r = rows.get(index);
            float top = index > 0 && !rows.get(index - 1).selectable() ? rows.get(index - 1).y : r.y;
            scroll.reveal(top - 3, r.y + r.h - top + 6, listH);
        }
    }

    private void move(int direction) {
        int i = selected;
        for (int n = 0; n < rows.size(); n++) {
            i = Math.floorMod(i + direction, rows.size());
            if (rows.get(i).selectable()) {
                select(i, true);
                return;
            }
        }
    }

    private Command current() {
        return selected >= 0 && selected < rows.size() ? rows.get(selected).command : null;
    }

    // ---- running ---------------------------------------------------------------------------------

    private void activate(Command c, boolean keep) {
        if (c == null || !c.available.getAsBoolean()) return;
        if (c.danger && armed != c) {
            armed = c;
            Sounds.click();
            return;
        }
        armed = null;
        if (c.step != null) {
            Sounds.click();
            push(c, c.step);
            return;
        }
        Sounds.click();
        // A choice from a list counts as a use of the command that offered it.
        History.used(c.key != null || levels.isEmpty() ? c.key : levels.peekLast().owner.key);
        if (c.after) {
            close(() -> {
                restore();
                c.run.accept(parent);
            });
            return;
        }
        c.run.accept(parent);
        done(c, keep);
    }

    /** After a command has finished: close, or back to an empty bar when it asked to stay or Shift was held. */
    private void done(Command c, boolean keep) {
        boolean stay = keep || c.stay || !levels.isEmpty() && levels.peekLast().owner.stay;
        if (!stay) {
            close();
            return;
        }
        levels.clear();
        search.setText("");
        field();
        scroll.reset();
        refresh();
    }

    private void push(Command owner, Step s) {
        float original = s instanceof Step.Num n ? n.get.get() : 0;
        levels.push(new Level(s, owner, levels.isEmpty() ? search.text : levels.peek().query, original));
        search.setText(s instanceof Step.Text t ? t.initial.get() : "");
        field();
        knobPrimed = false;
        scroll.reset();
        rebuild();
    }

    /** Escape inside a step: undo a value being previewed and go back one level. */
    private void pop() {
        Level level = levels.pop();
        if (level.step instanceof Step.Num n) n.set.accept(level.original);
        search.setText(levels.isEmpty() ? level.query : "");
        field();
        scroll.reset();
        refresh();
    }

    /** Sets the bar up for what it is asking now. */
    private void field() {
        Step s = step();
        search.maxLength = s instanceof Step.Text t ? t.maxLength : s instanceof Step.Num ? 12 : 256;
        search.placeholder = s instanceof Step.Text t ? t.placeholder : s instanceof Step.Num ? "Type a value"
                : s instanceof Step.Pick ? "Filter" : "Type a command, or  >  #  @  /";
        search.focused = true;
    }

    private void submit(boolean keep) {
        Level level = levels.peek();
        if (level.step instanceof Step.Num n) {
            if (!search.text.isBlank() && number(search.text) == null) return;
            n.saved.run();
            Sounds.click();
            if (level.owner.key != null) History.used(level.owner.key + "=" + n.plain(n.get.get()));
            done(level.owner, keep);
        } else if (level.step instanceof Step.Text t) {
            String text = search.text.trim();
            if (!t.valid.test(text)) return;
            Sounds.click();
            Step next = t.submit.apply(text);
            if (next != null) {
                levels.pop();
                levels.push(new Level(next, level.owner, level.query, 0));
                search.setText(next instanceof Step.Text more ? more.initial.get() : "");
                field();
                rebuild();
                return;
            }
            if (level.owner.key != null) History.used(t.inline != null ? level.owner.key + "=" + text : level.owner.key);
            done(level.owner, keep);
        }
    }

    private void nudge(Step.Num n, int steps) {
        float unit = n.step > 0 ? n.step : (n.max - n.min) / 100f;
        n.set.accept(n.clamp(n.get.get() + unit * steps));
        if (!search.text.isEmpty()) search.setText("");
    }

    /** Tab on an entry: what else can be done with it. */
    private void more(Command c) {
        if (c == null) return;
        List<Command> options = new ArrayList<>();
        if (c.menu != null) options.addAll(c.menu.get());
        if (c.key != null) {
            boolean pinned = History.pinned(c.key);
            options.add(new Command(null, pinned ? "Unpin" : "Pin to the top", Group.RESULT)
                    .detail(pinned ? "" : "Pinned commands run with Ctrl+1 to Ctrl+9 while the launcher is open").stay().run(() -> {
                        if (!History.togglePin(c.key)) Toasts.warn("No room for another pin", "Unpin one of the nine first.");
                    }));
        }
        if (options.isEmpty()) return;
        Sounds.click();
        push(c, new Step.Pick(c.name, "", () -> options));
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void layout() {
        pw = Math.min(W / zoom(), width - 24);
        px = (width - pw) / 2;
        py = Math.max(12, Math.round(height * 0.19f));
    }

    /** The bar keeps its size on screen; a smaller zoom only makes more fit inside it. */
    private static float zoom() {
        return dev.aller.AllerClient.options().launcherZoom.get();
    }

    @Override
    public float scale() {
        return super.scale() * zoom();
    }

    private float maxList() {
        return Math.max(ROW * 2, Math.min(Math.round(MAX_ROWS / zoom()) * ROW + 6, height - py - HEADER - FOOTER - 12));
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        float open = openness(), fade = fade();
        boolean bare = parent == null;
        if (Mc.mc().level == null && bare) Theme.scene(c, width, height);
        // Over a bare world the game stays in view, so only "None" changes anything there.
        if (!bare) Theme.veil(c, width, height, fade, 0.42f);
        else if (dev.aller.AllerClient.options().background.get() != dev.aller.ClientOptions.Background.NONE) {
            c.rect(0, 0, width, height, 0, Colors.withAlpha(0xFF050409, 0.24f * fade));
        }

        Step step = step();
        // A list that is still being read from disk fills in when it arrives.
        if (++frames % 15 == 0 && step instanceof Step.Pick && rows.isEmpty() && search.text.isBlank()) rebuild();

        float want = step instanceof Step.Num ? 46 : step instanceof Step.Text t ? (t.hint.isEmpty() ? 0 : 24)
                : rows.isEmpty() ? (empty.isEmpty() ? 0 : 34) : Math.min(contentH, maxList());
        if (!bodyPrimed) {
            bodyH.snap(want);
            bodyPrimed = true;
        }
        float body = Math.max(0, bodyH.target(want).update());
        float ph = HEADER + body + FOOTER;

        c.pushAlpha(fade);
        c.push();
        c.scale(0.96f + 0.04f * open, width / 2, py + HEADER / 2);
        c.translate(0, (1 - open) * -8);
        Theme.panel(c, px, py, pw, ph, Theme.R_LG);
        // Nothing is blurred over a bare world or a Minecraft menu, so the glass needs more body to stay readable.
        if (bare) c.rect(px, py, pw, ph, Theme.R_LG, 0x70100E18);
        else if (vanillaUnderlay() != null) c.rect(px, py, pw, ph, Theme.R_LG, 0xF20D0B14);

        float fieldX = drawHeader(c, step);
        search.bounds(fieldX, py, px + pw - 44 - fieldX, HEADER);
        search.draw(c, 0, 0);
        float kw = Fonts.SEMIBOLD.width("esc", 12 * 0.56f) + 12 * 0.6f;
        Theme.keycap(c, "esc", px + pw - 12 - kw, py + (HEADER - 12) / 2, 12);

        listY = py + HEADER;
        listH = body;
        c.rect(px + 1, listY - 0.5f, pw - 2, 0.5f, 0, Theme.BORDER);
        c.clip(px, listY, pw, body);
        if (step instanceof Step.Num n) drawNumber(c, n, mx, my);
        else if (step instanceof Step.Text t) c.textMiddle(Fonts.REGULAR, Fonts.REGULAR.truncate(t.hint, 7.5f, pw - 28), px + 14, listY, 24, 7.5f, Theme.TEXT_MUTED);
        else drawList(c, mx, my);
        c.unclip();

        c.rect(px + 1, py + ph - FOOTER, pw - 2, 0.5f, 0, Theme.BORDER);
        drawFooter(c, step, py + ph - FOOTER);
        c.pop();
        c.popAlpha();

        lastMx = mx;
        lastMy = my;
        Toasts.draw(c);
    }

    /** The search glass, or the name of the step being answered; returns where the text field starts. */
    private float drawHeader(Canvas c, Step step) {
        if (step == null) {
            float cx = px + 17, cy = py + HEADER / 2;
            int icon = Colors.mix(Theme.TEXT_MUTED, Theme.accent(), search.text.isEmpty() ? 0 : 1);
            Icons.SEARCH.draw(c, cx + 0.5f, cy + 0.5f, 11f, icon);
            return px + 31;
        }
        String title = Fonts.SEMIBOLD.truncate(step.title, 8f, pw * 0.42f);
        float tw = Fonts.SEMIBOLD.width(title, 8f) + 14, x = px + 10, h = 16, y = py + (HEADER - h) / 2;
        c.rect(x, y, tw, h, h / 2, Colors.withAlpha(Theme.accent(), 0.20f));
        c.stroke(x, y, tw, h, h / 2, 1, Colors.withAlpha(Theme.accent(), 0.45f));
        c.textMiddle(Fonts.SEMIBOLD, title, x + 7, y, h, 8f, Colors.lighten(Theme.accent(), 0.45f));
        Icons.CHEVRON_RIGHT.draw(c, x + tw + 7.5f, py + HEADER / 2, 9.5f, Theme.TEXT_MUTED);
        return x + tw + 14;
    }

    private void drawNumber(Canvas c, Step.Num n, float mx, float my) {
        float tx = px + 18, tw = pw - 36, ty = listY + 17;
        if (dragging) {
            n.set.accept(n.clamp(n.min + Math.clamp((mx - tx) / tw, 0f, 1f) * (n.max - n.min)));
            if (!search.text.isEmpty()) search.setText("");
        }
        float value = n.get.get(), t = Math.clamp((value - n.min) / (n.max - n.min), 0f, 1f);
        if (!knobPrimed) {
            knob.snap(t);
            knobPrimed = true;
        }
        float p = Math.clamp(knob.target(t).update(), 0f, 1f);
        c.rect(tx, ty - 1.5f, tw, 3, 1.5f, 0x26FFFFFF);
        if (p > 0.005f) c.gradientH(tx, ty - 1.5f, tw * p, 3, 1.5f, Theme.accent2(), Theme.accent());
        float kx = tx + tw * p, kr = dragging ? 5.5f : 4.5f;
        c.shadow(kx - kr, ty - kr + 1, kr * 2, kr * 2, kr, 6, Colors.withAlpha(Theme.accent(), 0.5f));
        c.circle(kx, ty, kr, Colors.WHITE);
        float ly = listY + 29;
        c.text(Fonts.REGULAR, n.format.apply(n.min), tx, ly, 7f, Theme.TEXT_MUTED);
        c.textRight(Fonts.REGULAR, n.format.apply(n.max), tx + tw, ly, 7f, Theme.TEXT_MUTED);
        c.textCentered(Fonts.SEMIBOLD, n.format.apply(value), tx + tw / 2, ly - 1.5f, 9f, Theme.TEXT);
    }

    private void drawList(Canvas c, float mx, float my) {
        if (rows.isEmpty()) {
            c.textCentered(Fonts.REGULAR, Fonts.REGULAR.truncate(empty, 8f, pw - 28), px + pw / 2, listY + (34 - Fonts.REGULAR.height(8f)) / 2, 8f, Theme.TEXT_MUTED);
            return;
        }
        float off = scroll.update(contentH, listH);
        float rx = px + 6, rw = pw - 12;
        boolean inList = !isClosing() && mx >= rx && mx < rx + rw && my >= listY && my < listY + listH;
        if (inList && (mx != lastMx || my != lastMy)) {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                float ry = listY + r.y - off;
                if (my >= ry && my < ry + r.h) select(i, false);
            }
        }
        float red = danger.target(armed != null ? 1 : 0).update();
        if (selected >= 0 && selected < rows.size()) {
            Row s = rows.get(selected);
            if (!selPrimed) {
                selY.snap(s.y);
                selPrimed = true;
            }
            float sy = listY + selY.target(s.y).update() - off, sh = selH.target(s.h).update();
            int tint = Colors.mix(Theme.accent(), Theme.DANGER, red);
            c.rect(rx, sy + 1, rw, sh - 2, Theme.R_MD, Colors.withAlpha(tint, 0.13f + 0.08f * red));
            c.stroke(rx, sy + 1, rw, sh - 2, Theme.R_MD, 1, Colors.withAlpha(tint, 0.30f + 0.3f * red));
            c.rect(rx, sy + 7, 2, sh - 14, 1, tint);
        }
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = listY + r.y - off;
            if (ry + r.h < listY || ry > listY + listH) continue;
            if (r.section != null) {
                c.text(Fonts.SEMIBOLD, r.section.toUpperCase(), rx + 10, ry + 8, 6.2f, Theme.TEXT_MUTED);
                float lx = rx + 16 + Fonts.SEMIBOLD.width(r.section.toUpperCase(), 6.2f);
                c.rect(lx, ry + 11.5f, rx + rw - 10 - lx, 0.5f, 0, 0x14FFFFFF);
            } else {
                drawRow(c, r, i == selected, rx, ry, rw);
            }
        }
        scroll.drawBar(c, px + pw - 5, listY + 3, listH - 6);
    }

    private void drawRow(Canvas c, Row r, boolean isSelected, float x, float y, float w) {
        Command cmd = r.command;
        // Keyed by name so the switch keeps its place while the list is rebuilt around it.
        RowAnim anim = anims.computeIfAbsent(cmd.key != null ? cmd.key : cmd.name, k -> new RowAnim());
        float hv = anim.hover.target(isSelected ? 1 : 0).update();
        boolean isArmed = armed == cmd && isSelected;
        Module m = cmd.module;
        float right = x + w - 10;

        float cx = x + 16, cy = y + ROW / 2;
        if (m != null) {
            if (m.enabled()) {
                c.shadow(cx - 3, cy - 3, 6, 6, 3, 6, Colors.withAlpha(Theme.accent(), 0.7f));
                c.circle(cx, cy, 3, Theme.accent());
            } else {
                c.ring(cx, cy, 3, 1.1f, 0x55FFFFFF);
            }
        } else if (cmd.color != 0) {
            c.circle(cx, cy, 4.2f, Colors.withAlpha(Colors.BLACK, 0.45f));
            c.circle(cx, cy, 3.4f, cmd.color);
        } else {
            int tint = cmd.danger ? Theme.DANGER : Theme.accent();
            c.rect(x + 9, y + 6, 14, 14, 4.5f, Colors.withAlpha(tint, 0.16f + 0.14f * hv));
            Icons.FORWARD.draw(c, cx + 1.1f * hv, y + 13, 8.5f, Colors.lighten(tint, 0.35f));
        }

        if (isSelected) {
            c.pushAlpha(Math.clamp(hv, 0f, 1f));
            float kw = Theme.keycap(c, "⏎", right - 12, y + (ROW - 12) / 2, 12);
            c.popAlpha();
            right -= kw + 7;
        }
        if (r.pin > 0) {
            String label = Integer.toString(r.pin);
            float kw = Math.max(12, Fonts.SEMIBOLD.width(label, 12 * 0.56f) + 12 * 0.6f);
            Theme.keycap(c, label, right - kw, y + (ROW - 12) / 2, 12);
            right -= kw + 7;
        } else if (History.pinned(cmd.key)) {
            Icons.PIN.draw(c, right - 5, cy, 9.5f, Colors.withAlpha(Theme.accent(), 0.85f));
            right -= 14;
        }
        if (m != null || cmd.state != null) {
            boolean on = m != null ? m.enabled() : cmd.state.getAsBoolean();
            anim.toggle.draw(c, right - Toggle.W, y + (ROW - Toggle.H) / 2, on, isSelected);
            right -= Toggle.W + 8;
        }
        if (m != null && m.keybind.get() != Settings.Key.NONE) {
            String label = Mc.keyName(m.keybind.get());
            float kw = Math.max(12, Fonts.SEMIBOLD.width(label, 12 * 0.56f) + 12 * 0.6f);
            Theme.keycap(c, label, right - kw, y + (ROW - 12) / 2, 12);
            right -= kw + 8;
        }
        if (cmd.value != null && !isArmed) {
            String value = Fonts.MEDIUM.truncate(String.valueOf(cmd.value.get()), 7.6f, w * 0.34f);
            c.textRight(Fonts.MEDIUM, value, right, y + (ROW - Fonts.MEDIUM.height(7.6f)) / 2, 7.6f, Theme.TEXT_DIM);
            right -= Fonts.MEDIUM.width(value, 7.6f) + 8;
        }
        if (cmd.step != null) {
            Icons.CHEVRON_RIGHT.draw(c, right - 2.5f, y + ROW / 2, 9.5f, Colors.fade(Theme.TEXT_MUTED, 0.5f + 0.5f * hv));
            right -= 12;
        }

        float tx = x + 30 + 1.5f * hv, room = right - tx - 4;
        if (isArmed) {
            c.textMiddle(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate("Press Enter again to " + cmd.confirm, NAME, room), tx, y, ROW, NAME,
                    Colors.mix(Theme.DANGER, Colors.WHITE, 0.35f));
            return;
        }
        int base = Colors.mix(Theme.TEXT_DIM, Theme.TEXT, m == null || m.enabled() ? 1f : 0.4f + 0.6f * hv);
        boolean fits = Fonts.SEMIBOLD.width(cmd.name, NAME) <= room;
        String name = fits ? cmd.name : Fonts.SEMIBOLD.truncate(cmd.name, NAME, room);
        if (cmd.detail.isEmpty()) {
            drawName(c, name, fits ? r.hits : null, tx, y + (ROW - Fonts.SEMIBOLD.height(NAME)) / 2, base);
        } else {
            drawName(c, name, fits ? r.hits : null, tx, y + 4f, base);
            c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(cmd.detail, DETAIL, room), tx, y + 15f, DETAIL, Theme.TEXT_MUTED);
        }
    }

    /** Draws a name with the characters that matched the query picked out in the accent colour. */
    private void drawName(Canvas c, String name, boolean[] hits, float x, float y, int color) {
        if (hits == null || hits.length != name.length()) {
            c.text(Fonts.SEMIBOLD, name, x, y, NAME, color);
            return;
        }
        int accent = Colors.lighten(Theme.accent(), 0.3f);
        int start = 0;
        while (start < name.length()) {
            int end = start;
            while (end < name.length() && hits[end] == hits[start]) end++;
            x += c.text(Fonts.SEMIBOLD, name.substring(start, end), x, y, NAME, hits[start] ? accent : color);
            start = end;
        }
    }

    private void drawFooter(Canvas c, Step step, float fy) {
        Command sel = current();
        String[][] hints = step instanceof Step.Num ? new String[][] {{"←→", "adjust"}, {"⏎", "keep"}, {"esc", "put back"}}
                : step instanceof Step.Text ? new String[][] {{"⏎", "done"}, {"esc", "back"}}
                : step != null ? new String[][] {{"↑↓", "move"}, {"⏎", "choose"}, {"esc", "back"}}
                : search.text.isEmpty() ? new String[][] {{"⏎", "run"}, {"tab", "more"}}
                : new String[][] {{"↑↓", "move"}, {"⏎", sel != null && (sel.module != null || sel.state != null) ? "toggle" : sel != null && sel.step != null ? "open" : "run"},
                        {"⇧⏎", "stay open"}, {"tab", "more"}};
        String first = step == null && search.text.length() == 1 ? search.text : "";
        String tip = first.equals(">") ? "actions only" : first.equals("#") ? "settings only" : first.equals("@") ? "waypoints"
                : first.equals("/") ? "send a command" : step == null && search.text.isEmpty() ? ">  actions   #  settings   @  waypoints   /  command" : "";
        float tipW = tip.isEmpty() ? 0 : Fonts.REGULAR.width(tip, 6.8f);
        float kh = 11, ky = fy + (FOOTER - kh) / 2 + 0.5f, x = px + 10, limit = px + pw - 12;
        float used = 0;
        for (String[] hint : hints) {
            float kw = Math.max(kh, Fonts.SEMIBOLD.width(hint[0], kh * 0.56f) + kh * 0.6f);
            float lw = Fonts.REGULAR.width(hint[1], 7.2f);
            if (x + kw + 4 + lw > limit) break;
            Theme.keycap(c, hint[0], x, ky, kh);
            c.textMiddle(Fonts.REGULAR, hint[1], x + kw + 4, fy + 0.5f, FOOTER, 7.2f, Theme.TEXT_MUTED);
            x += kw + lw + 14;
            used = x;
        }
        if (!tip.isEmpty() && used + tipW + 6 < limit) {
            c.textRight(Fonts.REGULAR, tip, limit, fy + (FOOTER - Fonts.REGULAR.height(6.8f)) / 2 + 0.5f, 6.8f, Colors.fade(Theme.TEXT_MUTED, 0.8f));
        }
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        float ph = HEADER + bodyH.get() + FOOTER;
        if (x < px || x >= px + pw || y < py || y >= py + ph) {
            // Clicking away from a value being previewed abandons it, like Escape.
            while (!levels.isEmpty()) pop();
            close();
            return true;
        }
        if (y < listY || y >= listY + listH) return true;
        if (step() instanceof Step.Num) {
            if (button == 0) dragging = true;
            return true;
        }
        float off = scroll.get();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = listY + r.y - off;
            if (!r.selectable() || y < ry || y >= ry + r.h) continue;
            select(i, false);
            if (button == 0) activate(r.command, Mc.shiftDown());
            else if (button == 1 && step() == null) more(r.command);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        if (Mc.ctrlDown()) {
            var zoom = dev.aller.AllerClient.options().launcherZoom;
            zoom.set(zoom.get() + (amount > 0 ? zoom.step : -zoom.step));
            dev.aller.AllerClient.config().markDirty();
        } else if (step() instanceof Step.Num n) nudge(n, amount > 0 ? 1 : -1);
        else scroll.scroll(amount);
        return true;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (isClosing()) return false;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0, ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        Step step = step();
        if (!enter && key != GLFW.GLFW_KEY_LEFT_SHIFT && key != GLFW.GLFW_KEY_RIGHT_SHIFT && armed != null) {
            // Any other key calls a pending confirmation off; Escape does only that.
            armed = null;
            if (key == GLFW.GLFW_KEY_ESCAPE) return true;
        }
        if (ctrl && step == null && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            List<String> pins = History.pins();
            int index = key - GLFW.GLFW_KEY_1;
            if (index < pins.size()) activate(Commands.find(commands, pins.get(index)), shift);
            return true;
        }
        int big = shift ? 5 : 1;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (step != null) pop();
                else if (!search.text.isEmpty()) search.setText("");
                else close();
                if (step == null && !isClosing()) changed();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (step instanceof Step.Num || step instanceof Step.Text) submit(shift);
                else activate(current(), shift);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (step instanceof Step.Num n) nudge(n, -big);
                else if (!(step instanceof Step.Text)) move(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (step instanceof Step.Num n) nudge(n, big);
                else if (!(step instanceof Step.Text)) move(-1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (step instanceof Step.Num n) {
                    nudge(n, -big);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (step instanceof Step.Num n) {
                    nudge(n, big);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                for (int i = 0; i < 6; i++) move(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                for (int i = 0; i < 6; i++) move(-1);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (step == null) more(current());
                return true;
            }
            default -> {}
        }
        search.focused = true;
        search.keyDown(key, mods);
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (isClosing()) return false;
        // A value step takes only what a number can be made of.
        if (step() instanceof Step.Num && !(Character.isDigit(codepoint) || codepoint == '.' || codepoint == '-' || codepoint == '%')) return true;
        search.focused = true;
        return search.charTyped(codepoint);
    }

    @Override
    public boolean closeOnEscape() {
        return false; // Escape is contextual here: back out of a step, clear the query, then close
    }
}
