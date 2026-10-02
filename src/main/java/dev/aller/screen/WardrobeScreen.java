package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.feature.Wardrobe;
import dev.aller.feature.Wardrobe.Outfit;
import dev.aller.feature.Wardrobe.Source;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.platform.SkinTex;
import dev.aller.platform.Sounds;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.IconButton;
import dev.aller.ui.widget.Scroll;
import dev.aller.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The skin wardrobe: the chosen skin on a player model that can be turned, beside a grid of the
 * skins in the library and the ones worn before. Picking a tile only tries it on; nothing is sent
 * to Minecraft services until "Wear this skin" is pressed.
 */
public final class WardrobeScreen extends AllerScreen {
    private static final float HEADER = 32, FOOTER = 20, PAD = 12, STAGE = 164, CONTROLS = 100, TOOL = 22, GAP = 6;
    private static final float TILE_W = 50, TILE_H = 84, HEADING = 18;

    private record Tile(Outfit outfit, float x, float y) {}

    private record Heading(String text, float y) {}

    private final Screen parent;
    private final Scroll scroll = new Scroll();
    private final TextField player = new TextField("Copy a player's skin by name");
    private final Button upload = new Button("Add skin", this::choose).icon(Icons.UPLOAD);
    private final Button wear = new Button("Wear this skin", this::wear).style(Button.Style.PRIMARY);
    private final Button extra = new Button("Save to library", this::extra).style(Button.Style.GHOST);
    private final IconButton folder = new IconButton(Icons.FOLDER, "Open the skins folder", () -> Mc.openFolder(Wardrobe.dir()));
    private final IconButton closeButton = new IconButton(Icons.CLOSE, "Close", this::close);
    private final Map<Outfit, Spring> hovers = new IdentityHashMap<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();
    private final Spring pop = Spring.bouncy(0), spin = Spring.smooth(0), arms = Spring.snappy(0), idle = Spring.smooth(1);
    private final Spring[] armsHover = {Spring.snappy(0), Spring.snappy(0)};
    private Outfit selected;
    private boolean slim;
    private float yaw = 22, pitch = 6;
    private boolean dragging, turned, removeArmed;
    private float lastX, lastY;
    private volatile boolean choosing;
    private float px, py, pw, ph;
    private float stageX, stageY, wellH, armsY;
    private float gridX, gridY, gridW, gridH, tileW, contentH, noteY;
    private int columns = 1;

    public WardrobeScreen(Screen parent) {
        this.parent = parent;
        player.maxLength = 16;
        player.textSize = 8f;
        upload.textSize = 8.2f;
        wear.textSize = 9f;
        extra.textSize = 7.5f;
    }

    @Override
    public AllerScreen underlay() {
        return parent instanceof ScreenHost host ? host.screen : null;
    }

    @Override
    public void opened() {
        super.opened();
        Wardrobe.refresh();
    }

    @Override
    public void close() {
        close(() -> Mc.setScreen(parent));
    }

    @Override
    public boolean closeOnEscape() {
        return false; // Escape leaves the name field first
    }

    @Override
    public boolean capturing() {
        return player.focused;
    }

    // ---- selection -----------------------------------------------------------------------------

    private void select(Outfit o) {
        if (o == selected) return;
        selected = o;
        slim = o.slim;
        arms.target(slim ? 1 : 0);
        removeArmed = false;
        turned = false;
        yaw = 22;
        pitch = 6;
        if (!Motion.reduced()) spin.snap(-150).target(0);
    }

    /** Keeps the selection pointing at something that still exists: the lists are rebuilt when they are read again. */
    private void sync() {
        Outfit added = Wardrobe.takeFresh();
        if (added != null) select(added);
        Outfit current = Wardrobe.current();
        boolean gone = selected == null
                || selected.source == Source.CURRENT && selected != current
                || selected.source == Source.SAVED && !Wardrobe.saved().contains(selected)
                || selected.source == Source.HISTORY && !Wardrobe.history().contains(selected);
        if (!gone) return;
        Outfit same = null;
        if (selected != null && selected.hash != null && selected.source != Source.CURRENT) {
            for (Outfit o : Wardrobe.history()) if (selected.hash.equals(o.hash)) same = o;
            for (Outfit o : Wardrobe.saved()) if (selected.hash.equals(o.hash)) same = o;
        }
        // Not a new choice, so no flourish.
        boolean keepArms = same != null;
        selected = same != null ? same : current;
        if (!keepArms) slim = selected.slim;
        arms.target(slim ? 1 : 0);
        removeArmed = false;
    }

    private boolean inLibrary(Outfit o) {
        if (o.hash == null) return false;
        for (Outfit s : Wardrobe.saved()) if (o.hash.equals(s.hash)) return true;
        return false;
    }

    private boolean chosen(Outfit o) {
        return o == selected || o.hash != null && o.hash.equals(selected.hash);
    }

    // ---- actions -------------------------------------------------------------------------------

    private void wear() {
        if (selected != null) Wardrobe.wear(selected, slim);
    }

    private void extra() {
        if (selected == null) return;
        if (selected.source != Source.SAVED) {
            Wardrobe.save(selected);
        } else if (!removeArmed) {
            removeArmed = true;
        } else {
            Wardrobe.remove(selected);
            selected = null;
            sync();
        }
    }

    private void setArms(boolean value) {
        if (slim == value) return;
        slim = value;
        arms.target(slim ? 1 : 0);
        Sounds.toggle(value);
        if (selected.source == Source.SAVED) {
            selected.slim = value;
            Wardrobe.rememberArms(selected.hash, value);
        }
    }

    /** The system's file picker. It blocks until it is answered, so it gets a thread of its own. */
    private void choose() {
        if (choosing) return;
        choosing = true;
        Thread thread = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.mallocPointer(1);
                filter.put(stack.UTF8("*.png")).flip();
                String path = TinyFileDialogs.tinyfd_openFileDialog("Choose a skin", "", filter, "Skin images (*.png)", false);
                if (path != null) Wardrobe.importFile(Path.of(path));
            } catch (Throwable e) {
                AllerClient.LOG.warn("Could not show a file picker", e);
                Mc.mc().execute(() -> Toasts.warn("No file picker here", "Drop the PNG onto the game window instead."));
            } finally {
                choosing = false;
            }
        }, "Aller Client file picker");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void filesDropped(List<Path> files) {
        for (Path file : files.subList(0, Math.min(files.size(), 8))) Wardrobe.importFile(file);
    }

    // ---- drawing -------------------------------------------------------------------------------

    @Override
    protected void layout() {
        pw = Math.min(548, width - 24);
        ph = Math.min(336, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        stageX = px + PAD;
        stageY = py + HEADER + 8;
        wellH = Math.max(50, ph - HEADER - FOOTER - 16 - CONTROLS);
        gridX = stageX + STAGE + 12;
        gridY = stageY + TOOL + 8;
        gridW = px + pw - PAD - gridX;
        gridH = py + ph - FOOTER - 4 - gridY;
        columns = Math.max(1, (int) ((gridW + GAP) / (TILE_W + GAP)));
        tileW = (gridW - GAP * (columns - 1)) / columns;
    }

    @Override
    protected void draw(Canvas c, float mx, float my) {
        sync();
        float open = openness(), fade = fade();
        if (Mc.mc().level == null && underlay() == null) Theme.scene(c, width, height);
        Theme.veil(c, width, height, fade, 0.42f);
        c.pushAlpha(fade);
        c.push();
        c.scale(0.94f + 0.06f * open, width / 2, height / 2);
        c.translate(0, (1 - open) * 10);
        Theme.panel(c, px, py, pw, ph, Theme.R_LG);

        float tw = c.text(Fonts.BOLD, "Wardrobe", px + 14, py + 10, 12, Theme.TEXT);
        c.text(Fonts.REGULAR, Nav.playerName(), px + 14 + tw + 8, py + 13.2f, 8.5f, Theme.TEXT_MUTED);
        closeButton.bounds(px + pw - 8 - 18, py + 7, 18, 18);
        closeButton.draw(c, mx, my);
        c.rect(px + 1, py + HEADER - 0.5f, pw - 2, 0.5f, 0, Theme.BORDER);

        drawStage(c, mx, my, fade);
        drawTools(c, mx, my);
        drawGrid(c, mx, my);
        drawFooter(c);
        folder.drawTip(c, false);
        c.pop();
        c.popAlpha();
        Toasts.draw(c);
    }

    private void drawStage(Canvas c, float mx, float my, float fade) {
        float x = stageX, y = stageY, w = STAGE;
        c.rect(x, y, w, wellH, Theme.R_MD, 0x3D000000);
        c.stroke(x, y, w, wellH, Theme.R_MD, 1, Theme.BORDER);

        if (dragging) {
            if (!Mc.isDown(-2)) {
                dragging = false;
            } else {
                yaw += (mx - lastX) * 1.6f;
                pitch = Math.clamp(pitch + (my - lastY) * 0.6f, -20f, 32f);
                lastX = mx;
                lastY = my;
            }
        }
        boolean ready = selected.tex != null;
        float grown = Math.max(0, pop.target(ready && fade > 0.6f && !isClosing() ? 1 : 0).update());
        float zoom = grown < 0.02f ? 0 : 0.6f + 0.4f * grown;
        float drift = idle.target(turned || Motion.reduced() ? 0 : 1).update();
        float time = Motion.time();
        float turn = yaw + spin.update() + drift * 16 * (float) Math.sin(time * 0.7f);
        float sway = Motion.reduced() ? 0 : (float) Math.sin(time * 1.3f);

        float boxY = y + 8, boxH = wellH - 18, cx = x + w / 2;
        float floor = boxY + boxH - SkinTex.feet(boxH, zoom);
        // A lit patch of floor, so the contact shadow has something to fall on: wide and faint, small and dark.
        c.oval(cx, floor + 1, w * 0.44f, w * 0.11f, 0.55f, Colors.withAlpha(Colors.mix(Colors.WHITE, Theme.accent(), 0.45f), 0.16f));
        if (zoom > 0) {
            float facing = (float) Math.abs(Math.cos(Math.toRadians(turn)));
            float reach = SkinTex.span(boxH, zoom) * (0.22f + 0.26f * facing);
            c.oval(cx, floor + 1, reach * 1.3f, reach * 0.32f, 0.7f, 0x66000000);
            c.oval(cx, floor + 0.5f, reach * 0.8f, reach * 0.17f, 0.5f, 0x8C000000);
            selected.tex.model(c, slim, x + 4, boxY, w - 8, boxH, pitch, turn, zoom, sway);
        } else if (!ready) {
            c.textCentered(Fonts.REGULAR, "Fetching…", cx, y + wellH / 2 - 4, 8, Theme.TEXT_MUTED);
        }

        // Name, where it is from, arm width, and the buttons.
        float ty = y + wellH + 8;
        boolean worn = Wardrobe.wearing(selected);
        String detail = worn ? selected.detail.isEmpty() || selected.source != Source.HISTORY ? "Wearing now" : "Wearing now  •  " + selected.detail
                : selected.detail;
        c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(selected.name, 10, w - 2), x + 1, ty, 10, Theme.TEXT);
        c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(detail, 7.5f, w - 2), x + 1, ty + 13.5f, 7.5f, worn ? Theme.accent() : Theme.TEXT_MUTED);

        armsY = ty + 28;
        float half = w / 2, slide = arms.update();
        c.rect(x, armsY, w, 18, 9, 0x33000000);
        c.stroke(x, armsY, w, 18, 9, 1, Theme.BORDER);
        Theme.accentFill(c, x + 2 + slide * (half - 2), armsY + 2, half - 2, 14, 7);
        String[] labels = {"Classic arms", "Slim arms"};
        for (int i = 0; i < 2; i++) {
            boolean over = inside(mx, my, x + i * half, armsY, half, 18);
            float hv = armsHover[i].target(over ? 1 : 0).update();
            float on = i == 0 ? 1 - slide : slide;
            int color = Colors.mix(Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv), Theme.onAccent(), Math.clamp(on, 0f, 1f));
            c.textMiddle(Fonts.MEDIUM, labels[i], x + i * half + (half - Fonts.MEDIUM.width(labels[i], 7.8f)) / 2, armsY, 18, 7.8f, color);
        }

        String busy = Wardrobe.busy();
        Outfit current = Wardrobe.current();
        boolean same = worn && current.slim == slim;
        wear.label = busy != null ? busy + "…" : same ? "Wearing now" : worn ? "Switch to " + (slim ? "slim" : "classic") + " arms" : "Wear this skin";
        wear.enabled = busy == null && ready && selected.png != null && !same && !Wardrobe.offline();
        wear.bounds(x, armsY + 24, w, 22);
        wear.draw(c, mx, my);

        boolean kept = selected.source == Source.SAVED;
        extra.style = kept ? Button.Style.DANGER : Button.Style.GHOST;
        extra.label = kept ? removeArmed ? "Click again to remove" : "Remove from library" : inLibrary(selected) ? "In your library" : "Save to library";
        extra.enabled = kept || selected.png != null && !inLibrary(selected);
        extra.bounds(x, armsY + 50, w, 16);
        if (kept || selected.png != null) extra.draw(c, mx, my);
    }

    private void drawTools(Canvas c, float mx, float my) {
        float y = stageY;
        upload.bounds(gridX, y, 74, TOOL);
        upload.draw(c, mx, my);
        folder.bounds(gridX + gridW - TOOL, y, TOOL, TOOL);
        folder.draw(c, mx, my);
        player.bounds(gridX + 80, y, gridW - 80 - TOOL - 6, TOOL);
        player.draw(c, mx, my);
    }

    /** Lays the tiles out in content coordinates: the library first, then the skins worn before. */
    private void arrange() {
        tiles.clear();
        headings.clear();
        float y = 0;
        List<Outfit> mine = new ArrayList<>();
        Outfit current = Wardrobe.current();
        if (!inLibrary(current)) mine.add(current);
        mine.addAll(Wardrobe.saved());
        headings.add(new Heading("Your skins", y));
        y = place(mine, y + HEADING);
        headings.add(new Heading("Worn before", y));
        y += HEADING;
        noteY = y;
        if (Wardrobe.history().isEmpty()) y += 34;
        else y = place(Wardrobe.history(), y);
        contentH = y;
    }

    private float place(List<Outfit> outfits, float y) {
        for (int i = 0; i < outfits.size(); i++) {
            tiles.add(new Tile(outfits.get(i), (i % columns) * (tileW + GAP), y + (i / columns) * (TILE_H + GAP)));
        }
        return y + ((outfits.size() + columns - 1) / columns) * (TILE_H + GAP);
    }

    private void drawGrid(Canvas c, float mx, float my) {
        arrange();
        float off = scroll.update(contentH, gridH);
        boolean within = inside(mx, my, gridX, gridY, gridW, gridH);
        c.clip(gridX - 2, gridY, gridW + 4, gridH);
        for (Heading h : headings) {
            float y = gridY + h.y - off;
            float tw = c.text(Fonts.SEMIBOLD, h.text, gridX + 1, y + 5, 7.2f, Theme.TEXT_DIM);
            c.rect(gridX + tw + 8, y + 9, gridW - tw - 8, 0.5f, 0, 0x14FFFFFF);
        }
        for (Tile t : tiles) {
            float x = gridX + t.x, y = gridY + t.y - off;
            if (y + TILE_H < gridY || y > gridY + gridH) continue;
            drawTile(c, t.outfit, x, y, within && inside(mx, my, x, y, tileW, TILE_H));
        }
        if (Wardrobe.history().isEmpty()) {
            String note = switch (Wardrobe.lookup()) {
                case EMPTY -> "laby.net has no record of skins for this account yet.";
                case FAILED -> "Could not reach laby.net. Open the wardrobe again to retry.";
                default -> "Looking up past skins…";
            };
            float y = gridY + noteY - off + 3;
            for (String line : Fonts.REGULAR.wrap(note, 7.8f, gridW - 4)) {
                c.text(Fonts.REGULAR, line, gridX + 1, y, 7.8f, Theme.TEXT_MUTED);
                y += 11;
            }
        }
        c.unclip();
        scroll.drawBar(c, px + pw - 6, gridY + 2, gridH - 4);
    }

    private void drawTile(Canvas c, Outfit o, float x, float y, boolean over) {
        float hv = hovers.computeIfAbsent(o, k -> Spring.snappy(0)).target(over ? 1 : 0).update();
        boolean picked = chosen(o);
        c.push();
        c.translate(0, -1.5f * hv);
        c.rect(x, y, tileW, TILE_H, Theme.R_MD, Colors.withAlpha(Colors.WHITE, 0.04f + 0.05f * hv));
        if (picked) {
            c.rect(x, y, tileW, TILE_H, Theme.R_MD, Colors.withAlpha(Theme.accent(), 0.16f));
            c.stroke(x, y, tileW, TILE_H, Theme.R_MD, 1.2f, Theme.accent());
        } else {
            c.stroke(x, y, tileW, TILE_H, Theme.R_MD, 1, Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hv));
        }
        float figure = TILE_H - 28, fx = x + (tileW - figure / 2) / 2, fy = y + 8;
        if (o.tex != null) {
            c.oval(x + tileW / 2, fy + figure + 0.5f, figure * 0.24f, 2.2f, 0.7f, 0x50000000);
            o.tex.flat(c, o.slim, fx, fy, figure);
        } else {
            // Still downloading: a figure-sized block that breathes.
            float pulse = 0.05f + 0.04f * (float) Math.sin(Motion.time() * 4 + x * 0.05f);
            c.rect(fx, fy, figure / 2, figure, 3, Colors.withAlpha(Colors.WHITE, pulse));
        }
        if (Wardrobe.wearing(o)) {
            c.shadow(x + tileW - 9.5f, y + 4.5f, 5, 5, 2.5f, 5, Colors.withAlpha(Theme.accent(), 0.7f));
            c.circle(x + tileW - 7, y + 7, 2.3f, Theme.accent());
        }
        String label = Fonts.REGULAR.truncate(o.name, 6.8f, tileW - 6);
        c.textCentered(picked ? Fonts.MEDIUM : Fonts.REGULAR, label, x + tileW / 2, y + TILE_H - 14, 6.8f,
                picked ? Theme.TEXT : Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
        c.pop();
    }

    private void drawFooter(Canvas c) {
        float fy = py + ph - FOOTER;
        c.rect(px + 1, fy, pw - 2, 0.5f, 0, Theme.BORDER);
        String credit = "Past skins from laby.net";
        float cw = Fonts.REGULAR.width(credit, 7.2f);
        c.textRight(Fonts.REGULAR, credit, px + pw - 12, fy + (FOOTER - Fonts.REGULAR.height(7.2f)) / 2 + 0.5f, 7.2f, Theme.TEXT_MUTED);
        boolean offline = Wardrobe.offline();
        String hint = offline ? "Not signed in to Minecraft: skins can be tried on here, but not worn."
                : "Drop a PNG onto the window to add it. Drag the model to turn it.";
        c.textMiddle(Fonts.REGULAR, Fonts.REGULAR.truncate(hint, 7.2f, pw - 36 - cw), px + 12, fy + 0.5f, FOOTER, 7.2f,
                offline ? Theme.WARN : Theme.TEXT_MUTED);
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseDown(float x, float y, int button) {
        if (isClosing()) return false;
        if (!inside(x, y, px, py, pw, ph)) {
            close();
            return true;
        }
        player.mouseDown(x, y, button);
        if (button != 0) return true;
        if (closeButton.mouseDown(x, y, button) || upload.mouseDown(x, y, button) || folder.mouseDown(x, y, button)
                || wear.mouseDown(x, y, button) || extra.mouseDown(x, y, button)) return true;
        if (!extra.hit(x, y)) removeArmed = false;
        if (inside(x, y, stageX, stageY, STAGE, wellH)) {
            dragging = true;
            turned = true;
            lastX = x;
            lastY = y;
            return true;
        }
        if (inside(x, y, stageX, armsY, STAGE, 18)) {
            setArms(x >= stageX + STAGE / 2);
            return true;
        }
        if (inside(x, y, gridX, gridY, gridW, gridH)) {
            float off = scroll.get();
            for (Tile t : tiles) {
                if (inside(x, y, gridX + t.x, gridY + t.y - off, tileW, TILE_H)) {
                    if (!chosen(t.outfit)) Sounds.click();
                    select(t.outfit);
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseUp(float x, float y, int button) {
        dragging = false;
        boolean used = closeButton.mouseUp(x, y, button);
        used |= upload.mouseUp(x, y, button);
        used |= folder.mouseUp(x, y, button);
        used |= wear.mouseUp(x, y, button);
        used |= extra.mouseUp(x, y, button);
        return used;
    }

    @Override
    public boolean mouseScroll(float x, float y, float amount) {
        scroll.scroll(amount * 1.5f);
        return true;
    }

    /** Arrow keys walk the grid. */
    private void step(int by) {
        if (tiles.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).outfit == selected) at = i;
        if (at < 0) for (int i = tiles.size() - 1; i >= 0; i--) if (chosen(tiles.get(i).outfit)) at = i;
        Tile next = tiles.get(Math.clamp(at + by, 0, tiles.size() - 1));
        select(next.outfit);
        scroll.reveal(next.y - HEADING, TILE_H + HEADING + GAP, gridH);
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (isClosing()) return false;
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (player.focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                player.focused = false;
            } else if (enter) {
                if (!player.text.isBlank()) {
                    Wardrobe.importName(player.text);
                    player.setText("");
                }
                player.focused = false;
            } else {
                player.keyDown(key, mods);
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> close();
            case GLFW.GLFW_KEY_LEFT -> step(-1);
            case GLFW.GLFW_KEY_RIGHT -> step(1);
            case GLFW.GLFW_KEY_UP -> step(-columns);
            case GLFW.GLFW_KEY_DOWN -> step(columns);
            case GLFW.GLFW_KEY_TAB -> setArms(!slim);
            default -> {
                if (enter && wear.enabled) {
                    Sounds.click();
                    wear();
                }
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        return !isClosing() && player.charTyped(codepoint);
    }
}
