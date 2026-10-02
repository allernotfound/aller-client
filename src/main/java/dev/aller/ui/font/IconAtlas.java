package dev.aller.ui.font;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bundled Lucide icons as two atlases. Each icon is an SVG of stroked outlines on a 24-unit
 * grid; it is read with the small parser below, stroked, and stored once as a distance field (drawn
 * by the text shader, so it stays sharp at any size) and once as a hard 24 by 24 picture for the
 * pixel look.
 *
 * <p>Pure Java (AWT in headless mode), like {@link SdfAtlas}.
 */
public final class IconAtlas {
    /** The grid Lucide draws on. */
    public static final int UNIT = 24;
    /** Atlas pixels per grid unit in the smooth atlas. */
    private static final int PER_UNIT = 2;
    private static final int CELL = UNIT * PER_UNIT + SdfAtlas.SPREAD * 2, PITCH = CELL + 2, COLUMNS = 8;
    public static final int SIZE = 512;
    private static final int PIXEL_PITCH = UNIT + 2;
    public static final int PIXEL_SIZE = 256;
    /** How far the smooth quad reaches beyond the 24-unit box, in units: room for the distance field's falloff. */
    public static final float MARGIN = SdfAtlas.SPREAD / (float) PER_UNIT;

    /** White with the distance in alpha, as the glyph atlases are. */
    public final int[] pixels = new int[SIZE * SIZE];
    /** White, opaque where the icon is. */
    public final int[] pixelArt = new int[PIXEL_SIZE * PIXEL_SIZE];

    /** @param files one resource name per icon ("arrow-left"), in the order they are indexed by */
    public IconAtlas(List<String> files) {
        if (files.size() > COLUMNS * (SIZE / PITCH)) throw new IllegalArgumentException("Too many icons for the atlas");
        for (int i = 0; i < files.size(); i++) {
            Shape outline;
            try {
                outline = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(read(files.get(i)));
            } catch (Exception e) {
                // A missing or unreadable file leaves an empty cell rather than stopping the client.
                dev.aller.AllerClient.LOG.error("Could not read icon {}", files.get(i), e);
                continue;
            }
            smooth(i, outline);
            hard(i, outline);
        }
    }

    /** Texture coordinates of an icon's cell: u0, v0, u1, v1. */
    public float[] uv(int index, boolean pixel) {
        int col = index % COLUMNS, row = index / COLUMNS;
        if (pixel) {
            float x = 1 + col * PIXEL_PITCH, y = 1 + row * PIXEL_PITCH;
            return new float[] {x / PIXEL_SIZE, y / PIXEL_SIZE, (x + UNIT) / PIXEL_SIZE, (y + UNIT) / PIXEL_SIZE};
        }
        float x = 1 + col * PITCH, y = 1 + row * PITCH;
        return new float[] {x / SIZE, y / SIZE, (x + CELL) / SIZE, (y + CELL) / SIZE};
    }

    private void smooth(int index, Shape outline) {
        int ss = SdfAtlas.SS;
        Shape scaled = AffineTransform.getScaleInstance(PER_UNIT * ss, PER_UNIT * ss).createTransformedShape(outline);
        float[] dist = SdfAtlas.distanceField(scaled, -SdfAtlas.SPREAD, -SdfAtlas.SPREAD, CELL, CELL);
        int ox = 1 + index % COLUMNS * PITCH, oy = 1 + index / COLUMNS * PITCH;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                float d = dist[y * CELL + x] / SdfAtlas.SPREAD;
                int a = Math.clamp(Math.round((0.5f + 0.5f * d) * 255), 0, 255);
                pixels[(oy + y) * SIZE + ox + x] = (a << 24) | 0xFFFFFF;
            }
        }
    }

    /** One cell per grid unit: a cell is on when the stroke covers enough of it. */
    private void hard(int index, Shape outline) {
        int ss = 8, n = UNIT * ss;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(ss, ss);
        g.fill(outline);
        g.dispose();
        byte[] src = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        int ox = 1 + index % COLUMNS * PIXEL_PITCH, oy = 1 + index / COLUMNS * PIXEL_PITCH;
        for (int y = 0; y < UNIT; y++) {
            for (int x = 0; x < UNIT; x++) {
                int sum = 0;
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) sum += src[(y * ss + sy) * n + x * ss + sx] & 0xFF;
                }
                pixelArt[(oy + y) * PIXEL_SIZE + ox + x] = sum >= 255 * ss * ss * 0.42f ? 0xFFFFFFFF : 0x00FFFFFF;
            }
        }
    }

    // ---- SVG -------------------------------------------------------------------------------------

    private static final Pattern ELEMENT = Pattern.compile("<(path|circle|ellipse|rect|line|polyline|polygon)\\s([^>]*?)/?>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-z-]+)=\"([^\"]*)\"");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?");

    private static Path2D read(String name) throws java.io.IOException {
        String svg;
        try (InputStream in = IconAtlas.class.getResourceAsStream("/assets/aller/icons/" + name + ".svg")) {
            if (in == null) throw new java.io.FileNotFoundException(name);
            svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path2D.Float out = new Path2D.Float();
        Matcher el = ELEMENT.matcher(svg);
        while (el.find()) {
            java.util.Map<String, String> at = new java.util.HashMap<>();
            Matcher am = ATTRIBUTE.matcher(el.group(2));
            while (am.find()) at.put(am.group(1), am.group(2));
            switch (el.group(1)) {
                case "path" -> path(out, at.getOrDefault("d", ""));
                case "circle" -> {
                    float r = num(at, "r");
                    out.append(new Ellipse2D.Float(num(at, "cx") - r, num(at, "cy") - r, r * 2, r * 2), false);
                }
                case "ellipse" -> {
                    float rx = num(at, "rx"), ry = num(at, "ry");
                    out.append(new Ellipse2D.Float(num(at, "cx") - rx, num(at, "cy") - ry, rx * 2, ry * 2), false);
                }
                case "rect" -> {
                    float rx = at.containsKey("rx") ? num(at, "rx") : num(at, "ry"), ry = at.containsKey("ry") ? num(at, "ry") : rx;
                    out.append(new RoundRectangle2D.Float(num(at, "x"), num(at, "y"), num(at, "width"), num(at, "height"), rx * 2, ry * 2), false);
                }
                case "line" -> {
                    out.moveTo(num(at, "x1"), num(at, "y1"));
                    out.lineTo(num(at, "x2"), num(at, "y2"));
                }
                default -> {
                    Matcher n = NUMBER.matcher(at.getOrDefault("points", ""));
                    boolean first = true;
                    while (n.find()) {
                        float x = Float.parseFloat(n.group());
                        if (!n.find()) break;
                        float y = Float.parseFloat(n.group());
                        if (first) out.moveTo(x, y);
                        else out.lineTo(x, y);
                        first = false;
                    }
                    if (!first && el.group(1).equals("polygon")) out.closePath();
                }
            }
        }
        return out;
    }

    private static float num(java.util.Map<String, String> at, String key) {
        String v = at.get(key);
        return v == null ? 0 : Float.parseFloat(v);
    }

    /** Reads path data one number at a time; arc flags are single digits that may run together. */
    private static final class Cursor {
        final String d;
        int at;

        Cursor(String d) {
            this.d = d;
        }

        void skip() {
            while (at < d.length() && (d.charAt(at) == ',' || Character.isWhitespace(d.charAt(at)))) at++;
        }

        /** The next command letter, or 0 if a number comes next (the previous command repeats). */
        char command() {
            skip();
            if (at < d.length() && Character.isLetter(d.charAt(at)) && d.charAt(at) != 'e' && d.charAt(at) != 'E') return d.charAt(at++);
            return 0;
        }

        boolean done() {
            skip();
            return at >= d.length();
        }

        float next() {
            skip();
            Matcher m = NUMBER.matcher(d).region(at, d.length());
            if (!m.lookingAt()) throw new IllegalArgumentException("Bad path data at " + at + ": " + d);
            at = m.end();
            return Float.parseFloat(m.group());
        }

        boolean flag() {
            skip();
            return d.charAt(at++) == '1';
        }
    }

    private static void path(Path2D.Float out, String d) {
        Cursor in = new Cursor(d);
        char cmd = 0;
        float x = 0, y = 0, startX = 0, startY = 0, ctrlX = 0, ctrlY = 0;
        while (!in.done()) {
            char next = in.command();
            if (next != 0) cmd = next;
            // After a move, further pairs are lines.
            else if (cmd == 'M') cmd = 'L';
            else if (cmd == 'm') cmd = 'l';
            boolean rel = Character.isLowerCase(cmd);
            float bx = rel ? x : 0, by = rel ? y : 0;
            char previous = cmd;
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    x = bx + in.next();
                    y = by + in.next();
                    out.moveTo(x, y);
                    startX = x;
                    startY = y;
                }
                case 'L' -> {
                    x = bx + in.next();
                    y = by + in.next();
                    out.lineTo(x, y);
                }
                case 'H' -> {
                    x = bx + in.next();
                    out.lineTo(x, y);
                }
                case 'V' -> {
                    y = by + in.next();
                    out.lineTo(x, y);
                }
                case 'C' -> {
                    float x1 = bx + in.next(), y1 = by + in.next();
                    ctrlX = bx + in.next();
                    ctrlY = by + in.next();
                    x = bx + in.next();
                    y = by + in.next();
                    out.curveTo(x1, y1, ctrlX, ctrlY, x, y);
                }
                case 'S' -> {
                    float x1 = 2 * x - ctrlX, y1 = 2 * y - ctrlY;
                    ctrlX = bx + in.next();
                    ctrlY = by + in.next();
                    x = bx + in.next();
                    y = by + in.next();
                    out.curveTo(x1, y1, ctrlX, ctrlY, x, y);
                }
                case 'Q' -> {
                    ctrlX = bx + in.next();
                    ctrlY = by + in.next();
                    x = bx + in.next();
                    y = by + in.next();
                    out.quadTo(ctrlX, ctrlY, x, y);
                }
                case 'T' -> {
                    ctrlX = 2 * x - ctrlX;
                    ctrlY = 2 * y - ctrlY;
                    x = bx + in.next();
                    y = by + in.next();
                    out.quadTo(ctrlX, ctrlY, x, y);
                }
                case 'A' -> {
                    float rx = in.next(), ry = in.next(), turn = in.next();
                    boolean large = in.flag(), sweep = in.flag();
                    float ex = bx + in.next(), ey = by + in.next();
                    arc(out, x, y, rx, ry, turn, large, sweep, ex, ey);
                    x = ex;
                    y = ey;
                }
                case 'Z' -> {
                    out.closePath();
                    x = startX;
                    y = startY;
                }
                default -> throw new IllegalArgumentException("Unknown path command " + cmd);
            }
            // A smooth curve mirrors the previous control point only after a curve of its own kind.
            char kind = Character.toUpperCase(previous);
            if (kind != 'C' && kind != 'S' && kind != 'Q' && kind != 'T') {
                ctrlX = x;
                ctrlY = y;
            }
        }
    }

    /** An SVG elliptical arc, as cubic curves of at most a quarter turn each. */
    private static void arc(Path2D.Float out, float x0, float y0, float rx, float ry, float degrees, boolean large, boolean sweep, float x1, float y1) {
        if (x0 == x1 && y0 == y1) return;
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0 || ry == 0) {
            out.lineTo(x1, y1);
            return;
        }
        double phi = Math.toRadians(degrees), cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x0 - x1) / 2.0, dy = (y0 - y1) / 2.0;
        double px = cos * dx + sin * dy, py = -sin * dx + cos * dy;
        double scale = px * px / (rx * rx) + py * py / (ry * ry);
        double a = rx, b = ry;
        if (scale > 1) {
            a *= Math.sqrt(scale);
            b *= Math.sqrt(scale);
        }
        double num = a * a * b * b - a * a * py * py - b * b * px * px, den = a * a * py * py + b * b * px * px;
        double k = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
        double cxp = k * a * py / b, cyp = -k * b * px / a;
        double cx = cos * cxp - sin * cyp + (x0 + x1) / 2.0, cy = sin * cxp + cos * cyp + (y0 + y1) / 2.0;
        double start = Math.atan2((py - cyp) / b, (px - cxp) / a);
        double sweepAngle = Math.atan2((-py - cyp) / b, (-px - cxp) / a) - start;
        if (sweep && sweepAngle < 0) sweepAngle += 2 * Math.PI;
        if (!sweep && sweepAngle > 0) sweepAngle -= 2 * Math.PI;

        int pieces = Math.max(1, (int) Math.ceil(Math.abs(sweepAngle) / (Math.PI / 2) - 1e-6));
        double step = sweepAngle / pieces, handle = 4.0 / 3.0 * Math.tan(step / 4);
        for (int i = 0; i < pieces; i++) {
            double t0 = start + step * i, t1 = t0 + step;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
            double p0x = cx + a * cos * c0 - b * sin * s0, p0y = cy + a * sin * c0 + b * cos * s0;
            double p1x = cx + a * cos * c1 - b * sin * s1, p1y = cy + a * sin * c1 + b * cos * s1;
            double d0x = -a * cos * s0 - b * sin * c0, d0y = -a * sin * s0 + b * cos * c0;
            double d1x = -a * cos * s1 - b * sin * c1, d1y = -a * sin * s1 + b * cos * c1;
            out.curveTo(p0x + handle * d0x, p0y + handle * d0y, p1x - handle * d1x, p1y - handle * d1y,
                    i == pieces - 1 ? x1 : p1x, i == pieces - 1 ? y1 : p1y);
        }
    }
}
