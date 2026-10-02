package dev.aller.ui.doc;

import dev.aller.feature.store.Images;
import dev.aller.platform.Canvas;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.doc.Doc.Align;
import dev.aller.ui.doc.Doc.Block;
import dev.aller.ui.font.Fonts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a {@link Doc} out to a width and draws it: wrapped text in the usual styles, headings,
 * lists, quotes, code, tables, rules and pictures in the flow of the text. The owner scrolls and
 * clips; this only needs to know which part is on show. Laid out again when the width changes or
 * a picture arrives and turns out to be a different size from its placeholder.
 */
public final class DocView {
    /** GUI units per pixel of a web page: 16 px body text comes out at Aller's usual 8. */
    private static final float PX = 0.5f;
    private static final float BODY = 8f, GAP = 6f;
    private static final float[] HEADINGS = {14f, 12f, 10.5f, 9.5f, 8.5f, 8.5f};
    private static final int TEXT = 0, PICTURE = 1, RECT = 2, BULLET = 3;

    private static final class Piece {
        int kind;
        float x, y, w, h, size, radius;
        String text, link;
        Fonts font;
        int color, style;
        boolean accent;
        Doc.Pic pic;
    }

    private final Doc doc;
    private final List<Piece> pieces = new ArrayList<>();
    private final Map<String, Float> widths = new HashMap<>();
    private float width = -1, height, density = 2;
    private int seen = -1;
    private String hovered;

    // The line being filled.
    private final List<Piece> line = new ArrayList<>();
    private float y, lineLeft, lineWidth, penX, lineH;
    private Align lineAlign;
    private boolean space;

    public DocView(Doc doc) {
        this.doc = doc;
    }

    /** Height at this width, laying out first if anything has changed. */
    public float height(float w) {
        boolean stale = w != width || doc.pictures && seen != Images.version();
        if (stale) {
            width = w;
            seen = Images.version();
            density = dev.aller.ui.AllerScreen.density();
            pieces.clear();
            y = 0;
            blocks(doc.blocks, 0, w);
            height = y;
        }
        return height;
    }

    public boolean empty() {
        return doc.blocks.isEmpty();
    }

    // ---- layout ----------------------------------------------------------------------------------

    private void blocks(List<Block> blocks, float left, float w) {
        boolean first = true;
        for (Block b : blocks) {
            if (!first) y += b.type == Doc.Type.HEADING ? GAP + 4 : GAP;
            first = false;
            block(b, left, Math.max(24, w));
        }
    }

    private void block(Block b, float left, float w) {
        switch (b.type) {
            case TEXT -> flow(b.inlines, left, w, b.align, Fonts.REGULAR, BODY, Colors.mix(Theme.TEXT_DIM, Theme.TEXT, 0.55f));
            case HEADING -> {
                int level = Math.clamp(b.level, 1, 6);
                flow(b.inlines, left, w, b.align, level <= 2 ? Fonts.BOLD : Fonts.SEMIBOLD, HEADINGS[level - 1], Theme.TEXT);
                if (level <= 2) {
                    y += 3;
                    rect(left, y, w, 0.5f, 0, 0x22FFFFFF);
                    y += 0.5f;
                }
            }
            case LIST -> {
                boolean first = true;
                for (Block item : b.children) {
                    if (!first) y += 2.5f;
                    first = false;
                    String marker = b.level == 1 ? item.level + "." : "•";
                    float indent = b.level == 1 ? Math.max(12, Fonts.REGULAR.width(marker, BODY) + 5) : 10;
                    Piece bullet = new Piece();
                    bullet.kind = BULLET;
                    bullet.text = marker;
                    bullet.x = left + (b.level == 1 ? indent - 4 - Fonts.REGULAR.width(marker, BODY) : 2);
                    bullet.y = y;
                    bullet.h = BODY * 1.45f;
                    pieces.add(bullet);
                    float top = y;
                    blocks(item.children, left + indent, w - indent);
                    if (y == top) y += bullet.h;
                }
            }
            case QUOTE -> {
                int at = pieces.size();
                float top = y;
                blocks(b.children, left + 9, w - 9);
                Piece bar = new Piece();
                bar.kind = RECT;
                bar.x = left + 1;
                bar.y = top;
                bar.w = 2;
                bar.h = y - top;
                bar.radius = 1;
                bar.color = 0x40FFFFFF;
                pieces.add(at, bar);
            }
            case CODE -> {
                float size = 7.2f, pad = 6, lh = size * 1.5f;
                List<String> lines = new ArrayList<>();
                for (String raw : b.code.split("\n", -1)) {
                    String rest = raw.replace("\t", "    ");
                    while (lines.size() < 400) {
                        int fit = fit(rest, Fonts.REGULAR, size, w - pad * 2);
                        lines.add(rest.substring(0, fit));
                        if (fit >= rest.length()) break;
                        rest = rest.substring(fit);
                    }
                }
                Piece box = rect(left, y, w, lines.size() * lh + pad * 2 - 2, 4, 0x38000000);
                float ty = y + pad;
                for (String each : lines) {
                    if (!each.isBlank()) text(each, left + pad, ty, Fonts.REGULAR, size, Theme.TEXT_DIM, 0, null);
                    ty += lh;
                }
                y += box.h;
            }
            case RULE -> {
                y += 2;
                rect(left, y, w, 0.5f, 0, 0x30FFFFFF);
                y += 2.5f;
            }
            case TABLE -> table(b, left, w);
            default -> blocks(b.children, left, w);
        }
    }

    private void table(Block table, float left, float w) {
        int columns = 1;
        for (Block row : table.children) columns = Math.max(columns, row.children.size());
        float cw = w / columns, pad = 4, top = y;
        int frame = pieces.size();
        for (Block row : table.children) {
            int at = pieces.size();
            float rowTop = y, bottom = y + BODY * 1.45f + pad * 2;
            boolean header = false;
            for (int i = 0; i < row.children.size(); i++) {
                Block cell = row.children.get(i);
                header |= cell.header;
                y = rowTop + pad;
                blocks(cell.children, left + i * cw + pad, cw - pad * 2);
                bottom = Math.max(bottom, y + pad);
            }
            y = bottom;
            if (header) pieces.add(at, shape(left, rowTop, w, bottom - rowTop, 0, 0x14FFFFFF));
            pieces.add(shape(left, bottom, w, 0.5f, 0, 0x26FFFFFF));
        }
        for (int i = 1; i < columns; i++) pieces.add(shape(left + i * cw, top, 0.5f, y - top, 0, 0x1AFFFFFF));
        pieces.add(frame, shape(left, top, w, y - top + 0.5f, 3, 0x12000000));
        pieces.add(shape(left, top, w, 0.5f, 0, 0x26FFFFFF));
        y += 0.5f;
    }

    /** Sets a run of words and pictures as wrapped lines. */
    private void flow(List<Doc.Inline> inlines, float left, float w, Align align, Fonts base, float size, int color) {
        line.clear();
        lineLeft = left;
        lineWidth = w;
        lineAlign = align;
        penX = 0;
        lineH = 0;
        space = false;
        float bodyH = size * 1.45f;
        for (Doc.Inline in : inlines) {
            if (in instanceof Doc.Break) {
                if (line.isEmpty()) lineH = bodyH;
                endLine();
            } else if (in instanceof Doc.Pic pic) {
                picture(pic);
            } else if (in instanceof Doc.Run run) {
                boolean bold = (run.style() & Doc.BOLD) != 0;
                Fonts font = bold ? (base == Fonts.REGULAR ? Fonts.SEMIBOLD : Fonts.BOLD) : base;
                float s = run.small() ? size * 0.82f : (run.style() & Doc.CODE) != 0 ? size * 0.94f : size;
                String text = run.text();
                int from = 0;
                while (from < text.length()) {
                    int end = text.indexOf(' ', from);
                    if (end < 0) end = text.length();
                    if (end > from) word(text.substring(from, end), font, s, color, run.style(), run.link(), bodyH);
                    if (end < text.length()) space = true;
                    from = end + 1;
                }
            }
        }
        endLine();
    }

    private void word(String word, Fonts font, float size, int color, int style, String link, float bodyH) {
        float gap = space && !line.isEmpty() ? measure(" ", font, size) : 0;
        float w = measure(word, font, size);
        if (penX + gap + w > lineWidth && !line.isEmpty()) {
            endLine();
            gap = 0;
        }
        // A word longer than the line (an address, usually) is cut where it runs out.
        while (w > lineWidth && word.length() > 1) {
            int fit = Math.max(1, fit(word, font, size, lineWidth));
            add(word.substring(0, fit), font, size, color, style, link, 0, measure(word.substring(0, fit), font, size), bodyH);
            endLine();
            word = word.substring(fit);
            w = measure(word, font, size);
        }
        add(word, font, size, color, style, link, gap, w, bodyH);
        space = false;
    }

    private void add(String word, Fonts font, float size, int color, int style, String link, float gap, float w, float bodyH) {
        Piece last = line.isEmpty() ? null : line.get(line.size() - 1);
        // Words in the same style join up, so a paragraph is a few pieces a line rather than one a word.
        if (last != null && last.kind == TEXT && last.font == font && last.size == size && last.style == style
                && java.util.Objects.equals(last.link, link) && last.color == color) {
            last.text = gap > 0 ? last.text + " " + word : last.text + word;
            last.w += gap + w;
        } else {
            Piece p = new Piece();
            p.kind = TEXT;
            p.text = word;
            p.font = font;
            p.size = size;
            p.color = color;
            p.style = style;
            p.link = link;
            p.accent = link != null;
            p.x = penX + gap;
            p.w = w;
            p.h = font.height(size);
            line.add(p);
        }
        penX += gap + w;
        lineH = Math.max(lineH, bodyH);
    }

    private void picture(Doc.Pic pic) {
        float w = pic.width() * PX, h = pic.height() * PX;
        Images.Image image = Images.get(pic.url(), Math.max(w, 96) * density);
        if (image.failed) {
            // What the picture was meant to say, if it says anything.
            if (!pic.alt().isEmpty()) word("[" + pic.alt() + "]", Fonts.REGULAR, BODY * 0.9f, Theme.TEXT_MUTED, 0, pic.link(), BODY * 1.45f);
            return;
        }
        if (image.width > 0) {
            float shape = image.height / (float) image.width;
            if (w <= 0 && h > 0) w = h / shape;
            else if (w <= 0) w = image.width * PX;
            // Wider than it was asked to be drawn at: ask again for one that will stay sharp.
            if (w * density > 96 * density) Images.get(pic.url(), Math.min(w, lineWidth) * density);
            h = w * shape;
        } else {
            if (w <= 0) w = h > 0 ? h * 1.6f : 40;
            if (h <= 0) h = pic.width() > 0 ? w * 0.56f : 11;
        }
        if (w > lineWidth) {
            h *= lineWidth / w;
            w = lineWidth;
        }
        float gap = space && !line.isEmpty() ? measure(" ", Fonts.REGULAR, BODY) : 0;
        if (penX + gap + w > lineWidth && !line.isEmpty()) {
            endLine();
            gap = 0;
        }
        Piece p = new Piece();
        p.kind = PICTURE;
        p.pic = pic;
        p.link = pic.link();
        p.x = penX + gap;
        p.w = w;
        p.h = h;
        p.radius = Math.min(4, Math.min(w, h) * 0.12f);
        line.add(p);
        penX += gap + w;
        lineH = Math.max(lineH, h + 2);
        space = false;
    }

    private void endLine() {
        float shift = lineAlign == Align.CENTER ? (lineWidth - penX) / 2 : lineAlign == Align.RIGHT ? lineWidth - penX : 0;
        for (Piece p : line) {
            p.x += lineLeft + Math.max(0, shift);
            p.y = y + (lineH - p.h) / 2;
            pieces.add(p);
        }
        y += lineH;
        line.clear();
        penX = 0;
        lineH = 0;
        space = false;
    }

    private Piece rect(float x, float y, float w, float h, float radius, int color) {
        Piece p = shape(x, y, w, h, radius, color);
        pieces.add(p);
        return p;
    }

    private static Piece shape(float x, float y, float w, float h, float radius, int color) {
        Piece p = new Piece();
        p.kind = RECT;
        p.x = x;
        p.y = y;
        p.w = w;
        p.h = h;
        p.radius = radius;
        p.color = color;
        return p;
    }

    private void text(String text, float x, float y, Fonts font, float size, int color, int style, String link) {
        Piece p = new Piece();
        p.kind = TEXT;
        p.text = text;
        p.font = font;
        p.size = size;
        p.color = color;
        p.style = style;
        p.link = link;
        p.x = x;
        p.y = y;
        p.w = measure(text, font, size);
        p.h = font.height(size);
        pieces.add(p);
    }

    private float measure(String text, Fonts font, float size) {
        if (widths.size() > 20000) widths.clear();
        return widths.computeIfAbsent(font.ordinal() + "|" + size + "|" + text, k -> font.widthAny(text, size));
    }

    /** How many characters of the text fit in the width; all of them if it fits whole. */
    private int fit(String text, Fonts font, float size, float max) {
        if (measure(text, font, size) <= max) return text.length();
        int low = 1, high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (font.widthAny(text.substring(0, mid), size) <= max) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    /**
     * @param x       left edge of the page
     * @param y       where the top of the page is (above the view once scrolled)
     * @param viewTop the part on show, in the same coordinates
     */
    public void draw(Canvas c, float x, float y, float w, float viewTop, float viewBottom, float mx, float my) {
        height(w);
        hovered = null;
        boolean within = my >= viewTop && my < viewBottom;
        if (within) {
            for (Piece p : pieces) {
                if (p.link != null && mx >= x + p.x && mx < x + p.x + p.w && my >= y + p.y - 1 && my < y + p.y + p.h + 1) hovered = p.link;
            }
        }
        int accent = Colors.mix(Theme.accent(), Colors.WHITE, 0.3f);
        for (Piece p : pieces) {
            float py = y + p.y;
            if (py + p.h < viewTop || py > viewBottom) continue;
            float px = x + p.x;
            switch (p.kind) {
                case RECT -> c.rect(px, py, p.w, p.h, p.radius, p.color);
                case BULLET -> c.textMiddle(Fonts.REGULAR, p.text, px, py, p.h, BODY, Theme.TEXT_MUTED);
                case PICTURE -> {
                    Images.Image image = Images.get(p.pic.url(), Math.max(p.w, p.h) * density);
                    if (image.ready()) {
                        c.picture(image.tex, px, py, p.w, p.h, p.radius, false);
                    } else {
                        float pulse = 0.05f + 0.03f * (float) Math.sin(Motion.time() * 4 + p.x * 0.05f);
                        c.rect(px, py, p.w, p.h, p.radius, Colors.withAlpha(Colors.WHITE, pulse));
                    }
                    if (p.link != null && p.link.equals(hovered)) c.stroke(px, py, p.w, p.h, p.radius, 1, Colors.withAlpha(accent, 0.8f));
                }
                default -> {
                    int color = p.accent ? accent : p.color;
                    if ((p.style & Doc.CODE) != 0) {
                        c.rect(px - 1.5f, py - 0.5f, p.w + 3, p.h + 1, 2, 0x22FFFFFF);
                        if (!p.accent) color = Theme.TEXT;
                    }
                    if ((p.style & Doc.ITALIC) != 0) {
                        c.push();
                        c.skew(0.18f, py + p.h * 0.78f);
                        c.textAny(p.font, p.text, px, py, p.size, color);
                        c.pop();
                    } else {
                        c.textAny(p.font, p.text, px, py, p.size, color);
                    }
                    if ((p.style & Doc.STRIKE) != 0) c.rect(px, py + p.h * 0.52f, p.w, 0.6f, 0, color);
                    boolean lit = p.link != null && p.link.equals(hovered);
                    if (lit || (p.style & Doc.UNDERLINE) != 0) c.rect(px, py + p.h * 0.88f, p.w, 0.6f, 0, color);
                }
            }
        }
    }

    /** The link under the pointer when last drawn, or null. */
    public String hoveredLink() {
        return hovered;
    }
}
