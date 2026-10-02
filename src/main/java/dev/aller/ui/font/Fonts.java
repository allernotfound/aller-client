package dev.aller.ui.font;

import dev.aller.AllerClient;
import dev.aller.platform.Mc;
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

    /** Scale of Minecraft's font per unit of text size, before snapping to whole pixels: its capitals then stand about as tall as Inter's. */
    public static final float VANILLA = 0.1f;

    /** Whether text is set in Minecraft's own font rather than Inter (the "Font" option). */
    public static boolean vanilla() {
        return AllerClient.options().typeface.get() == dev.aller.ClientOptions.Typeface.MINECRAFT && Mc.fontReady();
    }

    /** Minecraft's font has one heavier weight, kept for the heaviest face. */
    public boolean bold() {
        return this == BOLD;
    }

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
        if (vanilla()) return Mc.mc().font.width(Mc.styled(text, bold())) * dev.aller.platform.Canvas.pixelFontScale(size);
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

    /** Whether every character has a glyph in this face. */
    public boolean covers(CharSequence text) {
        SdfAtlas a = atlas();
        for (int i = 0; i < text.length(); i++) if (!a.has(text.charAt(i))) return false;
        return true;
    }

    /** Where the run starting at {@code from} ends: a run is all in this face, or all outside it. */
    public int runEnd(CharSequence text, int from) {
        SdfAtlas a = atlas();
        boolean mine = a.has(text.charAt(from));
        int end = from + 1;
        while (end < text.length() && a.has(text.charAt(end)) == mine) end++;
        return end;
    }

    /** Width of a line drawn with {@code Canvas.textAny}: what this face lacks is measured in Minecraft's font. */
    public float widthAny(CharSequence text, float size) {
        if (vanilla() || text.isEmpty() || covers(text)) return width(text, size);
        float w = 0, k = dev.aller.platform.Canvas.pixelFontScale(size);
        SdfAtlas a = atlas();
        for (int i = 0; i < text.length(); ) {
            int end = runEnd(text, i);
            CharSequence run = text.subSequence(i, end);
            w += a.has(text.charAt(i)) ? width(run, size) : Mc.mc().font.width(Mc.styled(run, bold())) * k;
            i = end;
        }
        return w;
    }

    /** {@link #truncate} for text that may leave this face. */
    public String truncateAny(String text, float size, float maxWidth) {
        if (widthAny(text, size) <= maxWidth) return text;
        float budget = maxWidth - width("…", size);
        int end = text.length();
        while (end > 0 && widthAny(text.substring(0, end), size) > budget) end--;
        return text.substring(0, end).stripTrailing() + "…";
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
