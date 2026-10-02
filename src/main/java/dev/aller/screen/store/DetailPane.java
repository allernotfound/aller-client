package dev.aller.screen.store;

import dev.aller.feature.store.Images;
import dev.aller.feature.store.Modrinth;
import dev.aller.feature.store.Modrinth.Project;
import dev.aller.feature.store.Modrinth.Shot;
import dev.aller.feature.store.Modrinth.Version;
import dev.aller.feature.store.Store;
import dev.aller.feature.store.Text;
import dev.aller.platform.Canvas;
import dev.aller.platform.Sounds;
import dev.aller.ui.Colors;
import dev.aller.ui.Icons;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.doc.Doc;
import dev.aller.ui.doc.DocView;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Button;
import dev.aller.ui.widget.IconButton;
import dev.aller.ui.widget.Scroll;
import org.lwjgl.glfw.GLFW;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A project's page: what it is and the button to fetch it across the top, then its description
 * (Markdown, with its pictures), its gallery, and its versions, any of which can be fetched instead
 * of the newest.
 */
final class DetailPane extends Pane {
    private static final float PAD = 12, HEAD = 70, TABS = 20, SIDE = 118, VERSION = 32;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(ZoneId.systemDefault());

    private enum Tab { DESCRIPTION, GALLERY, VERSIONS }

    private record Hit(float x, float y, float w, float h, int index, String link) {}

    private final Project project;
    private final Store.Details details;
    private final InstallButton install;
    private final Button site;
    private final Scroll[] scrolls = {new Scroll(), new Scroll(), new Scroll()};
    private final Spring[] tabHover = {Spring.snappy(0), Spring.snappy(0), Spring.snappy(0)};
    private final Spring underlineX = Spring.snappy(0), underlineW = Spring.snappy(0), light = Spring.smooth(0), onlyHover = Spring.snappy(0);
    private final float[] tabX = new float[3], tabW = new float[3];
    private final Map<String, InstallButton> versionButtons = new HashMap<>();
    private final Map<String, DocView> changelogs = new HashMap<>();
    private final Map<String, Spring> hovers = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final IconButton previous = new IconButton(Icons.CHEVRON_LEFT, "Previous", () -> step(-1));
    private final IconButton next = new IconButton(Icons.CHEVRON_RIGHT, "Next", () -> step(1));
    private Tab tab = Tab.DESCRIPTION;
    private DocView body;
    private String expanded;
    private boolean onlyThisVersion = true, versionChoiceMade, underlineSet;
    private int shot = -1;
    private float contentY, contentH, onlyX, onlyW;

    DetailPane(StoreScreen screen, Project project) {
        super(screen);
        this.project = project;
        this.details = Store.details(screen.kind, project);
        this.install = new InstallButton(screen.kind, project);
        this.install.textSize = 9f;
        this.site = new Button("Modrinth page", () -> screen.link(project.page(screen.kind))).style(Button.Style.GHOST).icon(Icons.EXTERNAL);
        this.site.textSize = 7.4f;
    }

    private boolean ready() {
        return details.state == Store.State.READY;
    }

    private List<Version> shownVersions() {
        if (!onlyThisVersion) return details.versions;
        String game = Store.gameVersion();
        return details.versions.stream().filter(v -> v.gameVersions.contains(game)).toList();
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    void draw(Canvas c, float mx, float my) {
        surface(c, x, y, w, h, Theme.R_LG);
        if (ready() && !versionChoiceMade) {
            versionChoiceMade = true;
            // With nothing made for this game version, listing only those would be an empty list.
            onlyThisVersion = Store.suggested(details) != null;
        }
        boolean blocked = shot >= 0;
        head(c, blocked ? -10000 : mx, my);
        tabs(c, blocked ? -10000 : mx, my);
        contentY = y + HEAD + TABS + 6;
        contentH = y + h - 8 - contentY;
        hits.clear();
        float px = blocked ? -10000 : mx;
        if (details.state == Store.State.FAILED) {
            c.textCentered(Fonts.SEMIBOLD, details.error, x + w / 2, contentY + contentH / 2 - 10, 9.5f, Theme.TEXT);
            c.textCentered(Fonts.REGULAR, "Go back and open it again to retry.", x + w / 2, contentY + contentH / 2 + 4, 7.8f, Theme.TEXT_MUTED);
        } else if (!ready()) {
            Bits.dots(c, x + w / 2, contentY + contentH / 2, Theme.TEXT_DIM);
        } else {
            switch (tab) {
                case DESCRIPTION -> description(c, px, my);
                case GALLERY -> gallery(c, px, my);
                case VERSIONS -> versions(c, px, my);
            }
        }
    }

    private void head(Canvas c, float mx, float my) {
        float icon = 46, ix = x + PAD, iy = y + PAD;
        Bits.icon(c, project, ix, iy, icon);

        float aw = Math.min(124, w * 0.3f), ax = x + w - PAD - aw;
        Version pick = ready() ? Store.suggested(details) : null;
        String caption = "";
        if (ready()) {
            if (pick != null) {
                caption = pick.number + " for " + Store.gameVersion();
            } else if (!details.versions.isEmpty()) {
                pick = details.versions.get(0);
                caption = "Newest is for " + Text.span(pick.gameVersions);
            } else {
                caption = "No files to download";
            }
            Store.Installed have = Store.installed(screen.kind, project.id);
            if (have != null && have.version != null) caption = "You have " + have.version.number;
        }
        install.version = pick;
        install.bounds(ax, iy, aw, 24);
        if (ready() && !details.versions.isEmpty() || !ready()) install.draw(c, ready() ? mx : -10000, my);
        c.textCentered(Fonts.REGULAR, Fonts.REGULAR.truncateAny(caption, 6.8f, aw), ax + aw / 2, iy + 28, 6.8f, Theme.TEXT_MUTED);
        site.bounds(ax, iy + 39, aw, 15);
        site.draw(c, mx, my);

        float tx = ix + icon + 10, tw = ax - 12 - tx;
        String title = Fonts.BOLD.truncateAny(project.title, 13.5f, tw * 0.72f);
        float used = c.textAny(Fonts.BOLD, title, tx, iy - 1, 13.5f, Theme.TEXT);
        if (!project.author.isEmpty()) {
            c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny("by " + project.author, 7.8f, tw - used - 7), tx + used + 7, iy + 4.5f, 7.8f, Theme.TEXT_MUTED);
        }
        float sy = iy + 17;
        for (String line : BrowsePane.wrap(project.summary, Fonts.REGULAR, 7.8f, tw, 2)) {
            c.textAny(Fonts.REGULAR, line, tx, sy, 7.8f, Theme.TEXT_DIM);
            sy += 10.5f;
        }
        float fx = tx, fy = iy + icon - 7;
        fx += Bits.stat(c, Icons.DOWNLOAD, Text.count(project.downloads), fx, fy, 7.2f, Theme.TEXT_DIM) + 9;
        fx += Bits.stat(c, Icons.HEART, Text.count(project.follows), fx, fy, 7.2f, Theme.TEXT_DIM) + 9;
        String updated = Text.ago(project.updated);
        if (!updated.isEmpty()) fx += Bits.stat(c, Icons.CLOCK, updated, fx, fy, 7.2f, Theme.TEXT_DIM) + 9;
        for (String category : project.categories) {
            String label = Bits.label(category);
            float cw = Bits.chipWidth(label, 6.4f);
            if (fx + cw > tx + tw) break;
            Bits.chip(c, label, fx, fy - 1.5f, 6.4f, 0x16FFFFFF, Theme.TEXT_DIM);
            fx += cw + 3;
        }
    }

    private void tabs(Canvas c, float mx, float my) {
        float ty = y + HEAD;
        String[] names = {"Description", "Gallery", "Versions"};
        String[] counts = {"", ready() && !project.gallery.isEmpty() ? Integer.toString(project.gallery.size()) : "",
                ready() ? Integer.toString(details.versions.size()) : ""};
        float tx = x + PAD;
        for (int i = 0; i < 3; i++) {
            float nw = Fonts.MEDIUM.width(names[i], 8.4f), cw = counts[i].isEmpty() ? 0 : Fonts.REGULAR.width(counts[i], 7f) + 4;
            tabX[i] = tx;
            tabW[i] = nw + cw;
            boolean on = tab.ordinal() == i;
            float hv = tabHover[i].target(inside(mx, my, tx - 4, ty, tabW[i] + 8, TABS) ? 1 : 0).update();
            c.textMiddle(on ? Fonts.SEMIBOLD : Fonts.MEDIUM, names[i], tx, ty, TABS - 2, 8.4f, on ? Theme.TEXT : Colors.mix(Theme.TEXT_MUTED, Theme.TEXT, hv));
            if (cw > 0) c.textMiddle(Fonts.REGULAR, counts[i], tx + nw + 4, ty + 0.5f, TABS - 2, 7f, Theme.TEXT_MUTED);
            tx += tabW[i] + 16;
        }
        c.rect(x + 1, ty + TABS - 0.5f, w - 2, 0.5f, 0, Theme.BORDER);
        int at = tab.ordinal();
        if (!underlineSet) {
            underlineSet = true;
            underlineX.snap(tabX[at]);
            underlineW.snap(tabW[at]);
        }
        c.rect(underlineX.target(tabX[at]).update(), ty + TABS - 1.5f, Math.max(4, underlineW.target(tabW[at]).update()), 1.5f, 0.75f, Theme.accent());
    }

    private void description(Canvas c, float mx, float my) {
        if (body == null) body = new DocView(Doc.markdown(project.body, Modrinth.SITE));
        boolean side = w >= 430;
        float dx = x + PAD, dw = w - PAD * 2 - (side ? SIDE + 14 : 0);
        Scroll scroll = scrolls[0];
        float total = body.empty() ? 30 : body.height(dw) + 8;
        float off = scroll.update(total, contentH);
        c.clip(dx - 2, contentY, dw + 4, contentH);
        if (body.empty()) c.text(Fonts.REGULAR, "This project has no description.", dx, contentY + 4, 8, Theme.TEXT_MUTED);
        else body.draw(c, dx, contentY + 2 - off, dw, contentY, contentY + contentH, mx, my);
        c.unclip();
        scroll.drawBar(c, dx + dw + 4, contentY + 2, contentH - 4);
        if (!side) return;

        // The facts beside it.
        float sx = x + w - PAD - SIDE, sy = contentY + 2;
        c.rect(sx - 8, contentY, 0.5f, contentH, 0, Theme.BORDER);
        sy = fact(c, Icons.LICENCE, "Licence", project.license.isEmpty() ? "Not stated" : project.license, sx, sy);
        if (project.created != null) sy = fact(c, Icons.CALENDAR, "Published", DAY.format(project.created), sx, sy);
        if (project.updated != null) sy = fact(c, Icons.CLOCK, "Updated", DAY.format(project.updated), sx, sy);
        if (!project.gameVersions.isEmpty()) sy = fact(c, Icons.TAG, "Minecraft", Text.span(project.gameVersions), sx, sy);
        if (!project.links.isEmpty()) {
            sy += 4;
            c.text(Fonts.SEMIBOLD, "Links", sx, sy, 7.2f, Theme.TEXT_MUTED);
            sy += 12;
            for (Modrinth.Link link : project.links) {
                boolean over = inside(mx, my, sx - 3, sy, SIDE + 6, 14);
                float hv = hovers.computeIfAbsent("link" + link.url(), k -> Spring.snappy(0)).target(over ? 1 : 0).update();
                if (hv > 0.01f) c.rect(sx - 3, sy, SIDE + 6, 14, 4, Colors.withAlpha(Colors.WHITE, 0.07f * hv));
                int color = Colors.mix(Theme.TEXT_DIM, Colors.mix(Theme.accent(), Colors.WHITE, 0.3f), hv);
                Icons.EXTERNAL.draw(c, sx + 5, sy + 7, 7.5f, color);
                c.textMiddle(Fonts.MEDIUM, Fonts.MEDIUM.truncateAny(link.label(), 7.6f, SIDE - 14), sx + 13, sy, 14, 7.6f, color);
                hits.add(new Hit(sx - 3, sy, SIDE + 6, 14, -1, link.url()));
                sy += 15;
            }
        }
    }

    private float fact(Canvas c, Icons icon, String label, String value, float sx, float sy) {
        icon.draw(c, sx + 5, sy + 5, 8, Theme.TEXT_MUTED);
        c.text(Fonts.REGULAR, label, sx + 14, sy, 6.8f, Theme.TEXT_MUTED);
        float ly = sy + 9.5f;
        for (String line : BrowsePane.wrap(value, Fonts.MEDIUM, 7.6f, SIDE - 14, 2)) {
            c.textAny(Fonts.MEDIUM, line, sx + 14, ly, 7.6f, Theme.TEXT);
            ly += 10;
        }
        return ly + 5;
    }

    private void gallery(Canvas c, float mx, float my) {
        List<Shot> shots = project.gallery;
        if (shots.isEmpty()) {
            Icons.IMAGE.draw(c, x + w / 2, contentY + contentH / 2 - 14, 18, Theme.TEXT_MUTED);
            c.textCentered(Fonts.REGULAR, "This project has no gallery.", x + w / 2, contentY + contentH / 2 + 2, 8, Theme.TEXT_MUTED);
            return;
        }
        float gx = x + PAD, gw = w - PAD * 2 - 4, gap = 8;
        int columns = Math.max(1, (int) ((gw + gap) / (160 + gap)));
        float tw = (gw - gap * (columns - 1)) / columns, th = Math.round(tw * 9 / 16f), cell = th + 16;
        int rows = (shots.size() + columns - 1) / columns;
        Scroll scroll = scrolls[1];
        float off = scroll.update(rows * (cell + gap), contentH);
        boolean within = inside(mx, my, gx, contentY, gw, contentH);
        c.clip(gx - 3, contentY, gw + 6, contentH);
        for (int i = 0; i < shots.size(); i++) {
            Shot s = shots.get(i);
            float sx = gx + (i % columns) * (tw + gap), sy = contentY + 2 - off + (i / columns) * (cell + gap);
            if (sy + cell < contentY || sy > contentY + contentH) continue;
            boolean over = within && inside(mx, my, sx, sy, tw, th);
            float hv = hovers.computeIfAbsent("shot" + i, k -> Spring.snappy(0)).target(over ? 1 : 0).update();
            c.push();
            c.scale(1 + 0.015f * hv, sx + tw / 2, sy + th / 2);
            c.rect(sx, sy, tw, th, Theme.R_MD, 0x40000000);
            Bits.cover(c, s.url(), sx, sy, tw, th, Theme.R_MD);
            c.stroke(sx, sy, tw, th, Theme.R_MD, 1, Colors.mix(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0.8f), hv));
            c.pop();
            String title = s.title().isEmpty() ? "" : Fonts.REGULAR.truncateAny(s.title(), 7.2f, tw - 2);
            c.textAny(Fonts.REGULAR, title, sx + 1, sy + th + 4, 7.2f, Colors.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
            hits.add(new Hit(sx, sy, tw, th, i, null));
        }
        c.unclip();
        scroll.drawBar(c, x + w - 6, contentY + 2, contentH - 4);
    }

    private void versions(Canvas c, float mx, float my) {
        float vx = x + PAD, vw = w - PAD * 2 - 4;
        List<Version> list = shownVersions();

        // The switch between this game version's files and all of them.
        String only = "Only for " + Store.gameVersion();
        onlyW = Fonts.MEDIUM.width(only, 7.4f) + 24;
        onlyX = vx;
        float oh = 15;
        float hv = onlyHover.target(inside(mx, my, onlyX, contentY, onlyW, oh) ? 1 : 0).update();
        c.rect(onlyX, contentY, onlyW, oh, oh / 2, onlyThisVersion ? Colors.withAlpha(Theme.accent(), 0.22f + 0.1f * hv) : Colors.withAlpha(Colors.WHITE, 0.05f + 0.05f * hv));
        c.stroke(onlyX, contentY, onlyW, oh, oh / 2, 1, onlyThisVersion ? Colors.withAlpha(Theme.accent(), 0.8f) : Theme.BORDER_STRONG);
        if (onlyThisVersion) Icons.CHECK.draw(c, onlyX + 9, contentY + oh / 2, 7, Theme.TEXT);
        else c.ring(onlyX + 9, contentY + oh / 2, 2.6f, 1, Theme.TEXT_MUTED);
        c.textMiddle(Fonts.MEDIUM, only, onlyX + 16, contentY, oh, 7.4f, onlyThisVersion ? Theme.TEXT : Theme.TEXT_DIM);
        String count = list.size() + " of " + details.versions.size() + (details.versions.size() == 1 ? " version" : " versions");
        c.textMiddle(Fonts.REGULAR, count, onlyX + onlyW + 8, contentY, oh, 7.2f, Theme.TEXT_MUTED);

        float top = contentY + oh + 6, viewH = contentY + contentH - top;
        if (list.isEmpty()) {
            c.textCentered(Fonts.REGULAR, "No version lists " + Store.gameVersion() + ". Turn the switch off to see the rest.", x + w / 2, top + viewH / 2 - 4, 7.8f, Theme.TEXT_MUTED);
            return;
        }
        float total = 0;
        for (Version v : list) total += VERSION + 4 + (v.id.equals(expanded) ? changelog(v).height(vw - 24) + 14 : 0);
        Scroll scroll = scrolls[2];
        float off = scroll.update(total, viewH);
        boolean within = inside(mx, my, vx, top, vw, viewH);
        c.clip(vx - 2, top, vw + 4, viewH);
        Store.Installed have = Store.installed(screen.kind, project.id);
        float ry = top - off;
        for (Version v : list) {
            boolean open = v.id.equals(expanded);
            float extra = open ? changelog(v).height(vw - 24) + 14 : 0, rh = VERSION + extra;
            if (ry + rh >= top && ry <= top + viewH) {
                boolean over = within && inside(mx, my, vx, ry, vw, VERSION);
                float rhv = hovers.computeIfAbsent("v" + v.id, k -> Spring.snappy(0)).target(over ? 1 : 0).update();
                boolean mine = have != null && have.version != null && have.version.id.equals(v.id);
                c.rect(vx, ry, vw, rh, Theme.R_MD, Colors.withAlpha(Colors.WHITE, 0.035f + 0.035f * rhv));
                c.stroke(vx, ry, vw, rh, Theme.R_MD, 1, mine ? Colors.withAlpha(Theme.SUCCESS, 0.45f) : Colors.mix(Theme.BORDER, Theme.BORDER_STRONG, rhv));

                float bw = 84;
                InstallButton button = versionButtons.computeIfAbsent(v.id, k -> {
                    InstallButton b = new InstallButton(screen.kind, project);
                    b.version = v;
                    b.textSize = 7.4f;
                    return b;
                });
                button.bounds(vx + vw - 8 - bw, ry + (VERSION - 18) / 2, bw, 18);
                button.draw(c, within ? mx : -10000, my);

                // The arrow turns down while the changelog is open.
                float turn = hovers.computeIfAbsent("turn" + v.id, k -> Spring.snappy(0)).target(open ? 1 : 0).update();
                c.push();
                c.rotate(turn * (float) Math.PI / 2, vx + 10, ry + VERSION / 2);
                // The arrow turns down while the changelog is open.
                float turn = hovers.computeIfAbsent("turn" + v.id, k -> Spring.snappy(0)).target(open ? 1 : 0).update();
                c.push();
                c.rotate(turn * (float) Math.PI / 2, vx + 10, ry + VERSION / 2);
                Icons.CHEVRON_RIGHT.draw(c, vx + 10, ry + VERSION / 2, 8, Colors.mix(Theme.TEXT_MUTED, Theme.TEXT, rhv));
                c.pop();
                c.pop();
                float tx = vx + 20, tw = button.x - 10 - tx;
                String name = Fonts.SEMIBOLD.truncateAny(v.name, 8.4f, tw * 0.6f);
                float used = c.textAny(Fonts.SEMIBOLD, name, tx, ry + 5, 8.4f, Theme.TEXT);
                int typeColor = v.type.equals("release") ? Theme.SUCCESS : v.type.equals("beta") ? Theme.WARN : Theme.DANGER;
                Bits.chip(c, Bits.label(v.type), tx + used + 6, ry + 4.5f, 6f, Colors.withAlpha(typeColor, 0.16f), typeColor);
                String line = v.number + "  •  " + Text.span(v.gameVersions);
                float sx = tx + c.textAny(Fonts.REGULAR, Fonts.REGULAR.truncateAny(line, 7f, tw * 0.5f), tx, ry + 18, 7f, Theme.TEXT_DIM) + 10;
                String facts = Text.ago(v.published) + "  •  " + Text.count(v.downloads) + " downloads" + (v.file != null && v.file.size() > 0 ? "  •  " + Text.size(v.file.size()) : "");
                if (sx < tx + tw - 20) c.text(Fonts.REGULAR, Fonts.REGULAR.truncate(facts, 6.8f, tx + tw - sx), sx, ry + 18.2f, 6.8f, Theme.TEXT_MUTED);
                if (open) {
                    c.rect(vx + 10, ry + VERSION, vw - 20, 0.5f, 0, Theme.BORDER);
                    DocView log = changelog(v);
                    if (log.empty()) c.text(Fonts.REGULAR, "No changelog for this version.", vx + 12, ry + VERSION + 6, 7.6f, Theme.TEXT_MUTED);
                    else log.draw(c, vx + 12, ry + VERSION + 6, vw - 24, top, top + viewH, within ? mx : -10000, my);
                }
                hits.add(new Hit(vx, ry, vw, VERSION, -2, v.id));
            }
            ry += rh + 4;
        }
        c.unclip();
        scroll.drawBar(c, x + w - 6, top + 2, viewH - 4);
    }

    private DocView changelog(Version v) {
        return changelogs.computeIfAbsent(v.id, k -> new DocView(Doc.markdown(v.changelog, Modrinth.SITE)));
    }

    // ---- the gallery, one picture at a time --------------------------------------------------------

    private void step(int by) {
        int n = project.gallery.size();
        if (shot >= 0 && n > 0) shot = ((shot + by) % n + n) % n;
    }

    @Override
    void overlay(Canvas c, float mx, float my) {
        float t = Math.clamp(light.target(shot >= 0 ? 1 : 0).update(), 0f, 1f);
        if (t < 0.01f || project.gallery.isEmpty()) return;
        int index = Math.clamp(shot >= 0 ? shot : lastShot, 0, project.gallery.size() - 1);
        lastShot = index;
        Shot s = project.gallery.get(index);
        float sw = screen.screenWidth(), sh = screen.screenHeight();
        c.pushAlpha(t);
        c.rect(0, 0, sw, sh, 0, 0xE607060B);
        float boxW = sw - 84, boxH = sh - 74;
        // The full picture, with the thumbnail standing in until it arrives.
        Images.Image full = Images.get(s.rawUrl(), Bits.px(Math.max(boxW, boxH)));
        Images.Image thumb = Images.get(s.url(), Bits.px(160));
        Images.Image shown = full.ready() ? full : thumb.ready() ? thumb : null;
        float iw = boxW, ih = boxH;
        Images.Image sized = full.width > 0 ? full : thumb.width > 0 ? thumb : null;
        if (sized != null) {
            float fit = Math.min(boxW / sized.width, boxH / sized.height);
            iw = sized.width * fit;
            ih = sized.height * fit;
        }
        float ix = (sw - iw) / 2, iy = 22 + (boxH - ih) / 2;
        c.push();
        c.scale(0.96f + 0.04f * t, sw / 2, sh / 2);
        if (shown != null) c.picture(shown.tex, ix, iy, iw, ih, Theme.R_MD, false);
        else Bits.dots(c, sw / 2, sh / 2, Theme.TEXT_DIM);
        if (shown != null && !full.ready() && !full.failed) Bits.dots(c, sw / 2, iy + ih - 10, Colors.WHITE);
        c.pop();
        lightX = ix;
        lightY = iy;
        lightW = iw;
        lightH = ih;

        String place = (index + 1) + " of " + project.gallery.size();
        c.textCentered(Fonts.MEDIUM, place, sw / 2, 8, 7.6f, Theme.TEXT_MUTED);
        float ty = 22 + boxH + 8;
        if (!s.title().isEmpty()) {
            c.textAny(Fonts.SEMIBOLD, s.title(), (sw - Fonts.SEMIBOLD.widthAny(s.title(), 9.5f)) / 2, ty, 9.5f, Theme.TEXT);
            ty += 13;
        }
        if (!s.description().isEmpty()) {
            String text = Fonts.REGULAR.truncateAny(s.description(), 7.6f, sw - 60);
            c.textAny(Fonts.REGULAR, text, (sw - Fonts.REGULAR.widthAny(text, 7.6f)) / 2, ty, 7.6f, Theme.TEXT_DIM);
        }
        if (project.gallery.size() > 1) {
            previous.bounds(12, sh / 2 - 13, 26, 26);
            next.bounds(sw - 38, sh / 2 - 13, 26, 26);
            previous.draw(c, mx, my);
            next.draw(c, mx, my);
        }
        c.popAlpha();
    }

    private int lastShot;
    private float lightX, lightY, lightW, lightH;

    // ---- input -----------------------------------------------------------------------------------

    @Override
    boolean mouseDown(float mx, float my, int button) {
        if (button != 0) return false;
        if (shot >= 0) {
            if (project.gallery.size() > 1 && (previous.mouseDown(mx, my, button) || next.mouseDown(mx, my, button))) return true;
            if (!inside(mx, my, lightX, lightY, lightW, lightH)) shot = -1;
            else step(1);
            return true;
        }
        if (install.mouseDown(mx, my, button) || site.mouseDown(mx, my, button)) return true;
        for (int i = 0; i < 3; i++) {
            if (inside(mx, my, tabX[i] - 4, y + HEAD, tabW[i] + 8, TABS)) {
                if (tab.ordinal() != i) Sounds.click();
                tab = Tab.values()[i];
                return true;
            }
        }
        if (!ready() || my < contentY || my > contentY + contentH) return false;
        if (tab == Tab.VERSIONS) {
            if (inside(mx, my, onlyX, contentY, onlyW, 15)) {
                onlyThisVersion = !onlyThisVersion;
                Sounds.toggle(onlyThisVersion);
                scrolls[2].reset();
                return true;
            }
            for (InstallButton b : versionButtons.values()) if (b.mouseDown(mx, my, button)) return true;
            // A link in an open changelog.
            DocView log = expanded == null ? null : changelogs.get(expanded);
            if (log != null && log.hoveredLink() != null) {
                screen.link(log.hoveredLink());
                return true;
            }
        }
        if (tab == Tab.DESCRIPTION && body != null && body.hoveredLink() != null) {
            screen.link(body.hoveredLink());
            return true;
        }
        for (Hit hit : hits) {
            if (!inside(mx, my, hit.x, hit.y, hit.w, hit.h)) continue;
            Sounds.click();
            if (hit.index >= 0) shot = hit.index;
            else if (hit.index == -2) expanded = hit.link.equals(expanded) ? null : hit.link;
            else screen.link(hit.link);
            return true;
        }
        return false;
    }

    @Override
    void mouseUp(float mx, float my, int button) {
        install.mouseUp(mx, my, button);
        site.mouseUp(mx, my, button);
        previous.mouseUp(mx, my, button);
        next.mouseUp(mx, my, button);
        for (InstallButton b : versionButtons.values()) b.mouseUp(mx, my, button);
    }

    @Override
    void scroll(float mx, float my, float amount) {
        if (shot >= 0) {
            step(amount > 0 ? -1 : 1);
            return;
        }
        scrolls[tab.ordinal()].scroll(amount * 1.6f);
    }

    @Override
    boolean keyDown(int key, int mods) {
        if (shot >= 0) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> shot = -1;
                case GLFW.GLFW_KEY_LEFT -> step(-1);
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_SPACE -> step(1);
                default -> {}
            }
            return true;
        }
        Scroll scroll = scrolls[tab.ordinal()];
        switch (key) {
            case GLFW.GLFW_KEY_TAB -> {
                int n = Tab.values().length;
                tab = Tab.values()[(tab.ordinal() + ((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? n - 1 : 1)) % n];
            }
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> scroll.scroll(-contentH / 30f);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll.scroll(contentH / 30f);
            case GLFW.GLFW_KEY_DOWN -> scroll.scroll(-2);
            case GLFW.GLFW_KEY_UP -> scroll.scroll(2);
            case GLFW.GLFW_KEY_HOME -> scroll.reset();
            default -> {
                return false;
            }
        }
        return true;
    }

    // For the self-test, which has no pointer.

    void show(int index) {
        tab = Tab.values()[Math.clamp(index, 0, 2)];
        shot = -1;
    }

    void enlarge() {
        if (!project.gallery.isEmpty()) shot = 0;
    }

    void expand() {
        if (!details.versions.isEmpty()) expanded = details.versions.get(0).id;
    }

    void download() {
        Version pick = Store.suggested(details);
        if (pick == null && !details.versions.isEmpty()) pick = details.versions.get(0);
        if (pick != null) Store.install(screen.kind, project, pick);
    }
}
