package dev.aller.screen.store;

import dev.aller.feature.store.Kind;
import dev.aller.feature.store.Modrinth.Project;
import dev.aller.feature.store.Modrinth.Version;
import dev.aller.feature.store.Store;
import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Widget;

/**
 * The button that fetches a project: "Download", then a bar that fills, then "Installed". With a
 * version it fetches that one; without, it finds the newest version for this game version first
 * (a search result does not say which file that is).
 */
final class InstallButton extends Widget {
    private final Kind kind;
    final Project project;
    /** The version to fetch, or null for the newest that suits the game. */
    Version version;
    /** Only the icon, for a card with no room for words. */
    boolean compact;
    float textSize = 8f;
    private final Spring hover = Spring.snappy(0), press = Spring.snappy(0), fill = Spring.smooth(0);
    private boolean down, waiting;

    InstallButton(Kind kind, Project project) {
        this.kind = kind;
        this.project = project;
    }

    private enum Phase { GET, UPDATE, SWITCH, HAVE, NONE, PREPARING, LOADING }

    private Phase phase() {
        if (Store.download(project.id) != null) return Phase.LOADING;
        if (waiting) return Phase.PREPARING;
        Store.Installed have = Store.installed(kind, project.id);
        if (version == null) {
            if (have == null) return Phase.GET;
            return have.update != null ? Phase.UPDATE : Phase.HAVE;
        }
        if (version.file == null) return Phase.NONE;
        if (have == null || have.version == null) return Phase.GET;
        if (have.version.id.equals(version.id)) return Phase.HAVE;
        return version.published != null && have.version.published != null && version.published.isAfter(have.version.published)
                ? Phase.UPDATE : Phase.SWITCH;
    }

    @Override
    public void draw(Canvas c, float mx, float my) {
        // A click on a search result waits for the project's versions to arrive, then goes ahead.
        if (waiting) {
            Store.Details d = Store.details(kind, project);
            if (d.state == Store.State.READY) {
                waiting = false;
                Version pick = Store.suggested(d);
                if (pick != null) Store.install(kind, project, pick);
                else Toasts.warn("Nothing for " + Store.gameVersion(), "Open " + project.title + " to pick one of its other versions.");
            } else if (d.state == Store.State.FAILED) {
                waiting = false;
                Toasts.warn("Not downloaded", d.error);
            }
        }
        Phase phase = phase();
        boolean enabled = phase == Phase.GET || phase == Phase.UPDATE || phase == Phase.SWITCH;
        boolean over = enabled && hit(mx, my);
        float hv = hover.target(over ? 1 : 0).update();
        float pr = press.target(down && over ? 1 : 0).update();
        Store.Download download = Store.download(project.id);
        float done = fill.target(download != null ? Math.max(0, download.progress) : 0).update();
        if (download == null) fill.snap(0);

        float r = Math.min(Theme.R_MD, h / 2);
        c.push();
        c.pixel(true);
        if (!c.pixelated()) c.scale(1f + 0.03f * hv - 0.05f * pr, x + w / 2, y + h / 2);
        int textColor;
        if (phase == Phase.GET || phase == Phase.UPDATE) {
            c.shadow(x, y + 2, w, h, r, 7 + 6 * hv, Colors.withAlpha(Theme.accent(), 0.2f + 0.25f * hv));
            Theme.accentFill(c, x, y, w, h, r);
            c.gradientV(x, y, w, h, r, Colors.withAlpha(Colors.WHITE, 0.10f + 0.10f * hv), 0x00FFFFFF);
            textColor = Theme.onAccent();
        } else {
            c.rect(x, y, w, h, r, 0xB80D0B14);
            c.rect(x, y, w, h, r, Colors.mix(Theme.RAISED, Theme.RAISED_HOVER, hv));
            if (phase == Phase.LOADING && done > 0.01f) {
                c.clip(x, y, Math.max(1, w * done), h);
                Theme.accentFill(c, x, y, w, h, r);
                c.unclip();
            }
            c.stroke(x, y, w, h, r, 1, phase == Phase.HAVE ? Colors.withAlpha(Theme.SUCCESS, 0.5f) : Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, hv));
            textColor = phase == Phase.HAVE ? Theme.SUCCESS : phase == Phase.NONE ? Theme.TEXT_MUTED : Theme.TEXT;
        }
        c.pixel(false);

        Icons icon = switch (phase) {
            case HAVE -> Icons.CHECK;
            case UPDATE, SWITCH -> Icons.RELOAD;
            case NONE -> Icons.ALERT;
            default -> Icons.DOWNLOAD;
        };
        String label = switch (phase) {
            case GET -> "Download";
            case UPDATE -> "Update";
            case SWITCH -> "Switch to this";
            case HAVE -> "Installed";
            case NONE -> "No file";
            case PREPARING -> "Finding…";
            case LOADING -> download != null && download.progress >= 0 ? Math.round(download.progress * 100) + "%" : "Starting…";
        };
        if (phase == Phase.PREPARING || phase == Phase.LOADING && compact) {
            Bits.dots(c, x + w / 2, y + h / 2, textColor);
        } else if (compact) {
            icon.draw(c, x + w / 2, y + h / 2, Math.min(w, h) * 0.5f, textColor);
        } else {
            float mark = phase == Phase.LOADING ? 0 : textSize * 0.9f, gap = mark > 0 ? textSize * 0.45f : 0;
            float tx = x + (w - Fonts.SEMIBOLD.width(label, textSize) - mark - gap) / 2;
            if (mark > 0) icon.draw(c, tx + mark / 2, y + h / 2, mark, textColor);
            c.textMiddle(Fonts.SEMIBOLD, label, tx + mark + gap, y, h, textSize, textColor);
        }
        c.pop();
    }

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        if (button != 0 || !hit(mx, my)) return false;
        down = true;
        return true;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        boolean wasDown = down;
        down = false;
        if (button != 0 || !wasDown || !hit(mx, my)) return false;
        Phase phase = phase();
        if (phase != Phase.GET && phase != Phase.UPDATE && phase != Phase.SWITCH) return true;
        Sounds.click();
        Store.Installed have = Store.installed(kind, project.id);
        if (version != null) Store.install(kind, project, version);
        else if (have != null && have.update != null) Store.install(kind, project, have.update);
        else waiting = true;
        return true;
    }
}
