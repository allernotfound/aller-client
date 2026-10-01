package dev.aller.ui.font;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a signed-distance-field atlas for one font face. A distance field stores "how far is this
 * texel from the glyph edge" instead of coverage, so the fragment shader can reconstruct a crisp,
 * anti-aliased edge at any size from a single texture.
 *
 * <p>Pure Java (AWT in headless mode): no Minecraft or GL types, so it is shared by every version.
 */
public final class SdfAtlas {
    /** Em size glyphs are stored at. Text is scaled from this. */
    public static final int EM = 44;
    /** Distance range in atlas pixels encoded on each side of the edge. */
    public static final int SPREAD = 6;
    /** Supersampling factor used when measuring distances. */
    private static final int SS = 4;
    public static final int SIZE = 1024;

    public static final String CHARSET = buildCharset();

    public record Glyph(float u0, float v0, float u1, float v1, float xOff, float yOff, float w, float h, float advance) {}

    /** RGBA, white with the distance in alpha (0.5 = edge). Row-major, {@link #SIZE} squared. */
    public final int[] pixels = new int[SIZE * SIZE];
    public final float ascent;
    public final float descent;
    public final float lineHeight;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Long, Float> kerning = new HashMap<>();
    private final Font font;
    private final FontRenderContext frc = new FontRenderContext(null, true, true);

    public SdfAtlas(Font base) {
        this.font = base.deriveFont((float) EM);
        LineMetrics lm = font.getLineMetrics("Hg", frc);
        this.ascent = lm.getAscent();
        this.descent = lm.getDescent();
        this.lineHeight = lm.getHeight();
        build();
    }

    private static String buildCharset() {
        StringBuilder sb = new StringBuilder();
        for (char c = 32; c < 127; c++) sb.append(c);
        for (char c = 161; c < 256; c++) sb.append(c);
        sb.append("–—‘’“”‹›•…←↑→↓✓✕⇧⌘⏎★▶●");
        return sb.toString();
    }

    private void build() {
        Font big = font.deriveFont((float) EM * SS);
        int penX = 1, penY = 1, rowH = 0;
        for (int i = 0; i < CHARSET.length(); i++) {
            char c = CHARSET.charAt(i);
            if (!font.canDisplay(c)) continue;
            GlyphVector gv = big.createGlyphVector(frc, new char[] {c});
            float advance = (float) gv.getGlyphMetrics(0).getAdvanceX() / SS;
            Rectangle2D b = gv.getVisualBounds();
            if (b.isEmpty()) {
                glyphs.put((int) c, new Glyph(0, 0, 0, 0, 0, 0, 0, 0, advance));
                continue;
            }
            // Output cell in atlas pixels, padded by the spread on every side.
            int ox = (int) Math.floor(b.getX() / SS) - SPREAD;
            int oy = (int) Math.floor(b.getY() / SS) - SPREAD;
            int ow = (int) Math.ceil(b.getMaxX() / SS) + SPREAD - ox;
            int oh = (int) Math.ceil(b.getMaxY() / SS) + SPREAD - oy;
            if (penX + ow + 1 > SIZE) {
                penX = 1;
                penY += rowH + 1;
                rowH = 0;
            }
            if (penY + oh + 1 > SIZE) break; // atlas full; remaining glyphs fall back to '?'
            float[] dist = distanceField(gv, ox, oy, ow, oh);
            for (int y = 0; y < oh; y++) {
                for (int x = 0; x < ow; x++) {
                    float d = dist[y * ow + x] / SPREAD; // -1..1, positive inside
                    int a = Math.clamp(Math.round((0.5f + 0.5f * d) * 255), 0, 255);
                    pixels[(penY + y) * SIZE + penX + x] = (a << 24) | 0xFFFFFF;
                }
            }
            glyphs.put((int) c, new Glyph(
                    penX / (float) SIZE, penY / (float) SIZE,
                    (penX + ow) / (float) SIZE, (penY + oh) / (float) SIZE,
                    ox, oy, ow, oh, advance));
            penX += ow + 1;
            rowH = Math.max(rowH, oh);
        }
    }

    /** Signed distance (in atlas pixels, positive inside the glyph) for each output texel. */
    private static float[] distanceField(GlyphVector gv, int ox, int oy, int ow, int oh) {
        int W = ow * SS, H = oh * SS;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.translate(-ox * SS, -oy * SS);
        g.fill(gv.getOutline());
        g.dispose();
        byte[] src = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();

        float inf = 1e20f;
        float[] outside = new float[W * H]; // squared distance to nearest inside pixel
        float[] inside = new float[W * H]; // squared distance to nearest outside pixel
        for (int i = 0; i < src.length; i++) {
            boolean in = (src[i] & 0xFF) >= 128;
            outside[i] = in ? 0 : inf;
            inside[i] = in ? inf : 0;
        }
        edt2d(outside, W, H);
        edt2d(inside, W, H);

        float[] out = new float[ow * oh];
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                // Average the supersampled block for a smoother field than point sampling.
                float sum = 0;
                for (int sy = 0; sy < SS; sy++) {
                    int row = (y * SS + sy) * W + x * SS;
                    for (int sx = 0; sx < SS; sx++) {
                        int i = row + sx;
                        sum += (float) (Math.sqrt(inside[i]) - Math.sqrt(outside[i]));
                    }
                }
                out[y * ow + x] = sum / (SS * SS) / SS;
            }
        }
        return out;
    }

    /** Felzenszwalb & Huttenlocher exact Euclidean distance transform, applied to columns then rows. */
    private static void edt2d(float[] grid, int w, int h) {
        int n = Math.max(w, h);
        float[] f = new float[n], d = new float[n], z = new float[n + 1];
        int[] v = new int[n];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) f[y] = grid[y * w + x];
            edt1d(f, d, v, z, h);
            for (int y = 0; y < h; y++) grid[y * w + x] = d[y];
        }
        for (int y = 0; y < h; y++) {
            System.arraycopy(grid, y * w, f, 0, w);
            edt1d(f, d, v, z, w);
            System.arraycopy(d, 0, grid, y * w, w);
        }
    }

    private static void edt1d(float[] f, float[] d, int[] v, float[] z, int n) {
        int k = 0;
        v[0] = 0;
        z[0] = Float.NEGATIVE_INFINITY;
        z[1] = Float.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            float s;
            while (true) {
                int p = v[k];
                s = ((f[q] + (float) q * q) - (f[p] + (float) p * p)) / (2f * q - 2f * p);
                if (s <= z[k] && k > 0) k--;
                else break;
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Float.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            int p = v[k];
            d[q] = (float) (q - p) * (q - p) + f[p];
        }
    }

    public Glyph glyph(int codepoint) {
        Glyph g = glyphs.get(codepoint);
        return g != null ? g : glyphs.get((int) '?');
    }

    /** Horizontal adjustment between a pair, in em-size pixels. Looked up lazily and cached. */
    public float kern(char left, char right) {
        long key = ((long) left << 16) | right;
        Float cached = kerning.get(key);
        if (cached != null) return cached;
        float k = 0;
        try {
            GlyphVector pair = font.layoutGlyphVector(frc, new char[] {left, right}, 0, 2, Font.LAYOUT_LEFT_TO_RIGHT);
            if (pair.getNumGlyphs() == 2) {
                float laidOut = (float) pair.getGlyphPosition(1).getX();
                float plain = (float) font.createGlyphVector(frc, new char[] {left}).getGlyphMetrics(0).getAdvanceX();
                k = laidOut - plain;
            }
        } catch (RuntimeException ignored) {
            // some JREs lack the layout engine; fall back to no kerning
        }
        kerning.put(key, k);
        return k;
    }
}
