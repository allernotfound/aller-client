package dev.aller.ui.font;

import dev.aller.AllerClient;
import dev.aller.platform.Tex;

import java.awt.Font;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/** The bundled Inter weights. Atlases are rasterised off-thread at startup and uploaded on first use. */
public enum Fonts {
    REGULAR("Inter-Regular"),
    MEDIUM("Inter-Medium"),
    SEMIBOLD("Inter-SemiBold"),
    BOLD("Inter-Bold");

    private final String file;
    private CompletableFuture<SdfAtlas> pending;
    private SdfAtlas atlas;
    private Tex texture;

    Fonts(String file) {
        this.file = file;
    }

    /** Kicks off atlas generation in the background so the first frame doesn't pay for it. */
    public static void preload() {
        for (Fonts f : values()) {
            f.pending = CompletableFuture.supplyAsync(f::rasterise);
        }
    }

    private SdfAtlas rasterise() {
        try (InputStream in = AllerClient.class.getResourceAsStream("/assets/aller/fonts/" + file + ".ttf")) {
            if (in == null) throw new IllegalStateException("Missing bundled font " + file);
            return new SdfAtlas(Font.createFont(Font.TRUETYPE_FONT, in));
        } catch (Exception e) {
            AllerClient.LOG.error("Could not load {}, falling back to a system font", file, e);
            return new SdfAtlas(new Font(Font.SANS_SERIF, this == BOLD || this == SEMIBOLD ? Font.BOLD : Font.PLAIN, 1));
        }
    }

    public SdfAtlas atlas() {
        if (atlas == null) {
            if (pending == null) pending = CompletableFuture.completedFuture(rasterise());
            atlas = pending.join();
            pending = null;
        }
        return atlas;
    }

    /** Render thread only. */
    public Tex texture() {
        if (texture == null) {
            SdfAtlas a = atlas();
            texture = new Tex("aller-font-" + file, SdfAtlas.SIZE, SdfAtlas.SIZE, a.pixels);
        }
        return texture;
    }

    public float width(CharSequence text, float size) {
        SdfAtlas a = atlas();
        float w = 0;
        char prev = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (prev != 0) w += a.kern(prev, c);
            w += a.glyph(c).advance();
            prev = c;
        }
        return w * size / SdfAtlas.EM;
    }

    /** Height of the ascent-to-descent box at this size; use it to centre text vertically. */
    public float height(float size) {
        SdfAtlas a = atlas();
        return (a.ascent + a.descent) * size / SdfAtlas.EM;
    }

    /** Cuts text to fit, ending in an ellipsis. */
    public String truncate(String text, float size, float maxWidth) {
        if (width(text, size) <= maxWidth) return text;
        String ellipsis = "…";
        float budget = maxWidth - width(ellipsis, size);
        int end = text.length();
        while (end > 0 && width(text.substring(0, end), size) > budget) end--;
        return text.substring(0, end).stripTrailing() + ellipsis;
    }

    /** Breaks text into lines no wider than {@code maxWidth}, splitting at spaces. */
    public java.util.List<String> wrap(String text, float size, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(candidate, size) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
