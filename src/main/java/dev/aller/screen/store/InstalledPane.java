package dev.aller.screen.store;

import dev.aller.feature.store.Store;
import dev.aller.feature.store.Store.Installed;
import dev.aller.feature.store.Text;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.IconButton;
import dev.aller.ui.widget.Scroll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is in the folder already. Files Modrinth recognises (by their hash) show as the project
 * they are, with a button to fetch a newer version where there is one; the rest are listed by name.
 */
final class InstalledPane extends Pane {
    private static final float BAR = 22, ROW = 38;

    private record Hit(Installed entry, float y) {}

    private final Scroll scroll = new Scroll();
    private final Button check = new Button("Check again", () -> Store.scan(screen.kind)).icon(Icons.RELOAD);
    private final Button updateAll = new Button("Update all", this::updateAll).style(Button.Style.PRIMARY).icon(Icons.DOWNLOAD);
    private final IconButton folder = new IconButton(Icons.FOLDER, "Open the folder", () -> Mc.openFolder(screen.kind.dir()));
    private final Map<String, InstallButton> buttons = new HashMap<>();
    private final Map<String, Spring> hovers = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    /** The file whose bin was clicked once: a second click deletes it. */
    private String armed;
    private float listY, listH;

    InstalledPane(StoreScreen screen) {
        super(screen);
        check.textSize = 7.8f;
        updateAll.textSize = 7.8f;
    }

    @Override
    void shown() {
        armed = null;
        if (Store.scanState(screen.kind) == Store.State.FAILED) Store.scan(screen.kind);
    }

    private void updateAll() {
        for (Installed entry : Store.installed(screen.kind)) {
            if (entry.update != null && entry.project != null) Store.install(screen.kind, entry.project, entry.update);
        }
    }

    @Override
    void draw(Canvas c, float mx, float my) {
        surface(c, x, y, w, h, Theme.R_LG);
        List<Installed> list = Store.installed(screen.kind);
        Store.State state = Store.scanState(screen.kind);
        int known = 0, updates = Store.updates(screen.kind);
        for (Installed entry : list) if (entry.project != null) known++;

        float pad = 12, top = y + 10;
        String noun = screen.kind.noun;
        String summary = state == Store.State.LOADING && list.isEmpty() ? "Reading the folder…"
                : list.size() + (list.size() == 1 ? " " + noun : " " + noun + "s") + " in the folder, " + known + " from Modrinth";
        c.textMiddle(Fonts.SEMIBOLD, summary, x + pad, top, BAR, 9f, Theme.TEXT);
        if (state == Store.State.FAILED) {
            float sw = Fonts.SEMIBOLD.width(summary, 9f);
            c.textMiddle(Fonts.REGULAR, "Modrinth could not be reached, so updates are unknown.", x + pad + sw + 10, top, BAR, 7.4f, Theme.WARN);
        }
        float right = x + w - pad;
        folder.bounds(right - BAR, top, BAR, BAR);
        folder.draw(c, mx, my);
        check.enabled = state != Store.State.LOADING;
        check.label = state == Store.State.LOADING ? "Checking…" : "Check again";
        check.bounds(folder.x - 6 - 82, top, 82, BAR);
        check.draw(c, mx, my);
        if (updates > 0) {
            updateAll.label = updates == 1 ? "Update 1" : "Update all " + updates;
            updateAll.enabled = !Store.busy();
            updateAll.bounds(check.x - 6 - 88, top, 88, BAR);
            updateAll.draw(c, mx, my);
        } else {
            updateAll.bounds(0, -100, 0, 0);
        }

        listY = top + BAR + 8;
        listH = y + h - 8 - listY;
        hits.clear();
        if (list.isEmpty()) {
            if (state == Store.State.LOADING || state == Store.State.IDLE) {
                Bits.dots(c, x + w / 2, listY + listH / 2, Theme.TEXT_DIM);
            } else {
                Icons.PACKAGE.draw(c, x + w / 2, listY + listH / 2 - 16, 18, Theme.TEXT_MUTED);
                c.textCentered(Fonts.SEMIBOLD, "Nothing here yet", x + w / 2, listY + listH / 2, 9.5f, Theme.TEXT);
                c.textCentered(Fonts.REGULAR, "What you download from Browse shows up here.", x + w / 2, listY + listH / 2 + 13, 7.8f, Theme.TEXT_MUTED);
            }
            folder.drawTip(c, false);
            return;
        }
        float lx = x + pad, lw = w - pad * 2 - 4;
        float off = scroll.update(list.size() * (ROW + 4), listH);
        boolean within = inside(mx, my, lx, listY, lw, listH);
        c.clip(lx - 2, listY, lw + 4, listH);
        float ry = listY - off;
        for (Installed entry : list) {
            if (ry + ROW >= listY && ry <= listY + listH) row(c, entry, lx, ry, lw, within ? mx : -10000, my);
            ry += ROW + 4;
        }
        c.unclip();
        scroll.drawBar(c, x + w - 6, listY + 2, listH - 4);
        folder.drawTip(c, false);
    }

    private void row(Canvas c, Installed entry, float rx, float ry, float rw, float mx, float my) {
        boolean known = entry.project != null;
        boolean over = inside(mx, my, rx, ry, rw, ROW);
        float hv = hovers.computeIfAbsent(entry.filename, k -> Spring.snappy(0)).target(over && known ? 1 : 0).update();
        boolean fresh = Store.fresh(entry.filename);
        c.rect(rx, ry, rw, ROW, Theme.R_MD, Colors.withAlpha(Colors.WHITE, 0.035f + 0.035f * hv));
        c.stroke(rx, ry, rw, ROW, Theme.R_MD, 1, fresh ? Colors.withAlpha(Theme.accent(), 0.7f)
                : entry.update != null ? Colors.withAlpha(Theme.WARN, 0.4f) : Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hv));
        float icon = 26, ix = rx + 6, iy = ry + (ROW - icon) / 2;
        if (known) {
            Bits.icon(c, entry.project, ix, iy, icon);
        } else {
            c.rect(ix, iy, icon, icon, icon * 0.22f, 0x1AFFFFFF);
            Icons.ARCHIVE.draw(c, ix + icon / 2, iy + icon / 2, icon * 0.5f, Theme.TEXT_MUTED);
        }

        // The bin on the right, then the update button, then the text takes what is left.
        float bin = 18, bx = rx + rw - 8 - bin, by = ry + (ROW - bin) / 2;
        boolean binOver = inside(mx, my, bx, by, bin, bin), binArmed = entry.filename.equals(armed);
        float bhv = hovers.computeIfAbsent("bin" + entry.filename, k -> Spring.snappy(0)).target(binOver || binArmed ? 1 : 0).update();
        c.rect(bx, by, bin, bin, 5, Colors.withAlpha(Theme.DANGER, (binArmed ? 0.3f : 0.16f) * bhv));
        Icons.TRASH.draw(c, bx + bin / 2, by + bin / 2, 9, Colors.mix(Theme.TEXT_MUTED, Colors.lighten(Theme.DANGER, 0.3f), bhv));
        float edge = bx - 8;
        if (binArmed) {
            String sure = "Click again to delete";
            c.textRight(Fonts.MEDIUM, sure, edge, ry + (ROW - Fonts.MEDIUM.height(7.2f)) / 2, 7.2f, Colors.lighten(Theme.DANGER, 0.3f));
            edge -= Fonts.MEDIUM.width(sure, 7.2f) + 8;
        } else if (known && (entry.update != null || Store.download(entry.project.id) != null)) {
            float bw = 78;
            InstallButton button = buttons.computeIfAbsent(entry.project.id, k -> new InstallButton(screen.kind, entry.project));
            button.textSize = 7.4f;
            button.bounds(edge - bw, ry + (ROW - 18) / 2, bw, 18);
            button.draw(c, mx, my);
            edge -= bw + 8;
        } else if (known) {
            String ok = fresh ? "Just downloaded" : "Up to date";
            int color = fresh ? Colors.mix(Theme.accent(), Colors.WHITE, 0.3f) : Theme.TEXT_MUTED;
            c.textRight(Fonts.MEDIUM, ok, edge, ry + (ROW - Fonts.MEDIUM.height(7.2f)) / 2, 7.2f, color);
            edge -= Fonts.MEDIUM.width(ok, 7.2f) + 8;
        }

        float tx = ix + icon + 8, tw = edge - tx;
        c.textAny(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncateAny(entry.title(), 8.8f, tw), tx, ry + 7, 8.8f, known ? Theme.TEXT : Theme.TEXT_DIM);
        String detail;
        if (!known) detail = "Not from Modrinth  •  " + Text.size(entry.size);
        else if (entry.update != null) detail = entry.version.number + "  →  " + entry.update.number + "  •  " + entry.filename;
        else detail = entry.version.number + "  •  " + entry.filename;
        c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny(detail, 7.2f, tw), tx, ry + 20, 7.2f,
                entry.update != null ? Colors.mix(Theme.WARN, Colors.WHITE, 0.25f) : Theme.TEXT_MUTED);
        hits.add(new Hit(entry, ry));
    }

    @Override
    boolean mouseDown(float mx, float my, int button) {
        if (button != 0) return false;
        if (check.mouseDown(mx, my, button) || updateAll.mouseDown(mx, my, button) || folder.mouseDown(mx, my, button)) return true;
        float rx = x + 12, rw = w - 28;
        String was = armed;
        armed = null;
        if (!inside(mx, my, rx, listY, rw, listH)) return false;
        for (InstallButton b : buttons.values()) if (b.mouseDown(mx, my, button)) return true;
        for (Hit hit : hits) {
            if (!inside(mx, my, rx, hit.y, rw, ROW)) continue;
            float bin = 18, bx = rx + rw - 8 - bin;
            if (inside(mx, my, bx, hit.y + (ROW - bin) / 2, bin, bin)) {
                if (hit.entry.filename.equals(was)) {
                    Sounds.click();
                    Store.remove(screen.kind, hit.entry);
                } else {
                    armed = hit.entry.filename;
                }
            } else if (hit.entry.project != null) {
                Sounds.click();
                screen.open(hit.entry.project);
            }
            return true;
        }
        return false;
    }

    @Override
    void mouseUp(float mx, float my, int button) {
        check.mouseUp(mx, my, button);
        updateAll.mouseUp(mx, my, button);
        folder.mouseUp(mx, my, button);
        for (InstallButton b : buttons.values()) b.mouseUp(mx, my, button);
    }

    @Override
    void scroll(float mx, float my, float amount) {
        scroll.scroll(amount * 1.6f);
    }
}
