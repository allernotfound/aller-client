package dev.aller.platform;

import dev.aller.mixin.GuiTextRenderStateAccessor;
import dev.aller.screen.MenuSkin;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.font.SdfAtlas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
//? if <26.1 {
/*import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
*///?} else {
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.FontDescription;
//?}

/**
 * Redraws Minecraft's own text in Inter on restyled menus. Two halves that must agree:
 * {@link #advance} replaces the width of each character wherever vanilla measures text, so
 * centring, wrapping and truncation are worked out for Inter, and {@link #redraw} takes each line
 * as it is queued for drawing and lays it out with those same advances (no kerning, so a text
 * field's cursor lands exactly between letters).
 *
 * <p>Characters Inter has no glyph for, obfuscated text and text in a non-default font keep their
 * vanilla width and are passed through to the vanilla renderer in place.
 */
public final class VanillaText {
    private VanillaText() {}

    /** Set while a run is handed back to vanilla, so it is not caught again. */
    private static boolean passThrough;

    private static int[] codepoints = new int[256];
    private static Style[] styles = new Style[256];
    private static int count;

    /** One mesh per face: the faces live in different atlas textures. */
    private static final class Batch {
        float[] verts = new float[1024];
        int[] colors = new int[256];
        int n;
        float minX, minY, maxX, maxY;

        void reset() {
            n = 0;
            minX = minY = Float.MAX_VALUE;
            maxX = maxY = -Float.MAX_VALUE;
        }

        void vertex(Matrix3x2fc m, float x, float y, float u, float v, int color) {
            if (n + 4 > verts.length) {
                verts = java.util.Arrays.copyOf(verts, verts.length * 2);
                colors = java.util.Arrays.copyOf(colors, colors.length * 2);
            }
            float tx = m.m00() * x + m.m10() * y + m.m20();
            float ty = m.m01() * x + m.m11() * y + m.m21();
            colors[n / 4] = color;
            verts[n++] = tx;
            verts[n++] = ty;
            verts[n++] = u;
            verts[n++] = v;
            minX = Math.min(minX, tx);
            minY = Math.min(minY, ty);
            maxX = Math.max(maxX, tx);
            maxY = Math.max(maxY, ty);
        }
    }

    private static final Batch REGULAR = new Batch(), BOLD = new Batch();

    /** @return the width to measure this character at, or a negative number to keep vanilla's */
    public static float advance(int codepoint, Style style) {
        if (!MenuSkin.active() || Fonts.vanilla() || !plain(style)) return -1;
        float a = (style.isBold() ? Fonts.BOLD : Fonts.REGULAR).atlas().advance(codepoint);
        return a == a ? a * MenuSkin.TEXT_SIZE / SdfAtlas.EM : -1;
    }

    private static boolean plain(Style style) {
        if (style.isObfuscated()) return false;
        //? if <26.1 {
        /*return style.getFont().equals(Style.DEFAULT_FONT);
        *///?} else {
        return style.getFont().equals(FontDescription.DEFAULT);
        //?}
    }

    /** @return true if the line was drawn here and vanilla must not draw it */
    public static boolean redraw(GuiRenderState target, GuiTextRenderState state) {
        if (passThrough) return false;
        float fade = dev.aller.screen.Entrance.alpha();
        boolean skinned = MenuSkin.drawing();
        if (!skinned && fade >= 1) return false;
        GuiTextRenderStateAccessor in = (GuiTextRenderStateAccessor) (Object) state;
        if (!skinned || Fonts.vanilla() || in.aller$backgroundColor() != 0) return recolour(target, state, in, skinned, fade);

        count = 0;
        in.aller$text().accept((index, style, codepoint) -> {
            if (count == codepoints.length) {
                codepoints = java.util.Arrays.copyOf(codepoints, count * 2);
                styles = java.util.Arrays.copyOf(styles, count * 2);
            }
            codepoints[count] = codepoint;
            styles[count++] = style;
            return true;
        });
        if (count == 0) return true;

        Matrix3x2fc pose = state.pose;
        ScreenRectangle scissor = state.scissor;
        Font font = in.aller$font();
        int base = dev.aller.ui.Colors.fade(in.aller$color(), fade);
        float x = in.aller$x(), y = in.aller$y();

        // The screen's own title is set larger and bolder, re-centred on where it was measured to sit.
        boolean heading = isTitle();
        float size = heading ? MenuSkin.TITLE_SIZE : MenuSkin.TEXT_SIZE;
        float scale = size / SdfAtlas.EM;
        float baseline = y + 7f;
        if (heading) {
            float measured = 0, wide = 0;
            SdfAtlas regular = Fonts.REGULAR.atlas(), bold = Fonts.BOLD.atlas();
            for (int i = 0; i < count; i++) {
                measured += (styles[i].isBold() ? bold : regular).advance(codepoints[i]) * MenuSkin.TEXT_SIZE / SdfAtlas.EM;
                wide += bold.advance(codepoints[i]) * scale;
            }
            x += (measured - wide) / 2;
            baseline += (size - MenuSkin.TEXT_SIZE) * 0.36f;
        }

        REGULAR.reset();
        BOLD.reset();
        StringBuilder run = null;
        Style runStyle = null;
        float pen = 0;
        for (int i = 0; i <= count; i++) {
            Style style = i < count ? styles[i] : null;
            boolean bold = heading || style != null && style.isBold();
            SdfAtlas atlas = (bold ? Fonts.BOLD : Fonts.REGULAR).atlas();
            float adv = style != null && plain(style) ? atlas.advance(codepoints[i]) : Float.NaN;
            boolean vanilla = style != null && adv != adv;

            // Close a pending vanilla run when the style changes or Inter takes over again.
            if (run != null && (!vanilla || !style.equals(runStyle))) {
                FormattedCharSequence piece = FormattedCharSequence.forward(run.toString(), runStyle);
                // Vanilla draws a colour that is all but transparent as a solid one, so those are left out.
                if (base >>> 24 >= 4) {
                    passThrough = true;
                    try {
                        submit(target, text(font, piece, pose, Math.round(x + pen), (int) y, base, scissor));
                    } finally {
                        passThrough = false;
                    }
                }
                pen += font.getSplitter().stringWidth(piece);
                run = null;
            }
            if (style == null) break;
            if (vanilla) {
                if (run == null) {
                    run = new StringBuilder();
                    runStyle = style;
                }
                run.appendCodePoint(codepoints[i]);
                continue;
            }

            TextColor tint = style.getColor();
            int color = MenuSkin.textColor(tint != null ? (base & 0xFF000000) | tint.getValue() : base);
            SdfAtlas.Glyph gl = atlas.glyph(codepoints[i]);
            float w = adv * scale;
            if (gl.w() > 0) {
                float x0 = x + pen + gl.xOff() * scale, y0 = baseline + gl.yOff() * scale;
                float x1 = x0 + gl.w() * scale, y1 = y0 + gl.h() * scale;
                // Italics: lean the quad, pivoting on the baseline.
                float top = style.isItalic() ? (baseline - y0) * 0.2f : 0, bottom = style.isItalic() ? (baseline - y1) * 0.2f : 0;
                Batch b = bold ? BOLD : REGULAR;
                b.vertex(pose, x0 + top, y0, gl.u0(), gl.v0(), color);
                b.vertex(pose, x0 + bottom, y1, gl.u0(), gl.v1(), color);
                b.vertex(pose, x1 + bottom, y1, gl.u1(), gl.v1(), color);
                b.vertex(pose, x1 + top, y0, gl.u1(), gl.v0(), color);
            }
            if (style.isUnderlined()) rule(target, pose, scissor, x + pen, x + pen + w, baseline + 1, color);
            if (style.isStrikethrough()) rule(target, pose, scissor, x + pen, x + pen + w, baseline - 3, color);
            pen += w;
        }

        flush(target, Fonts.REGULAR, REGULAR, scissor);
        flush(target, Fonts.BOLD, BOLD, scissor);
        return true;
    }

    /**
     * A line that stays in Minecraft's font: only its colour changes, to the theme's on a restyled
     * menu and fainter while the screen eases in.
     */
    private static boolean recolour(GuiRenderState target, GuiTextRenderState state, GuiTextRenderStateAccessor in, boolean skinned, float fade) {
        int color = in.aller$color();
        int wanted = dev.aller.ui.Colors.fade(skinned ? MenuSkin.textColor(color) : color, fade);
        if (wanted == color) return false;
        if (wanted >>> 24 < 4) return true;
        passThrough = true;
        try {
            //? if <26.1 {
            /*submit(target, new GuiTextRenderState(in.aller$font(), in.aller$text(), state.pose, in.aller$x(), in.aller$y(), wanted,
                    in.aller$backgroundColor(), in.aller$dropShadow(), state.scissor));
            *///?} else {
            submit(target, new GuiTextRenderState(in.aller$font(), in.aller$text(), state.pose, in.aller$x(), in.aller$y(), wanted,
                    in.aller$backgroundColor(), in.aller$dropShadow(), in.aller$includeEmpty(), state.scissor));
            //?}
        } finally {
            passThrough = false;
        }
        return true;
    }

    private static boolean isTitle() {
        String title = MenuSkin.title();
        if (title.isEmpty() || title.length() != count) return false;
        SdfAtlas bold = Fonts.BOLD.atlas();
        for (int i = 0; i < count; i++) {
            if (codepoints[i] != title.charAt(i) || !plain(styles[i]) || !bold.has(codepoints[i])) return false;
        }
        return true;
    }

    private static void flush(GuiRenderState target, Fonts face, Batch b, ScreenRectangle scissor) {
        if (b.n == 0) return;
        int x0 = (int) Math.floor(b.minX), y0 = (int) Math.floor(b.minY);
        ScreenRectangle bounds = new ScreenRectangle(x0, y0, (int) Math.ceil(b.maxX) - x0, (int) Math.ceil(b.maxY) - y0);
        if (scissor != null) bounds = scissor.intersection(bounds);
        submit(target, new Canvas.Mesh(Pipelines.TEXT, face.texture().setup(), scissor, bounds,
                java.util.Arrays.copyOf(b.verts, b.n), java.util.Arrays.copyOf(b.colors, b.n / 4), 0, 0, 0, 0, 0f, 0f, 0f));
    }

    /** An underline or strikethrough: a one-pixel vanilla rectangle. */
    private static void rule(GuiRenderState target, Matrix3x2fc pose, ScreenRectangle scissor, float x0, float x1, float y, int color) {
        int top = Math.round(y);
        submit(target, new ColoredRectangleRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose),
                (int) Math.floor(x0), top, (int) Math.ceil(x1), top + 1, color, color, scissor));
    }

    private static GuiTextRenderState text(Font font, FormattedCharSequence text, Matrix3x2fc pose, int x, int y, int color, ScreenRectangle scissor) {
        //? if <26.1 {
        /*return new GuiTextRenderState(font, text, new Matrix3x2f(pose), x, y, color, 0, true, scissor);
        *///?} else {
        return new GuiTextRenderState(font, text, pose, x, y, color, 0, true, false, scissor);
        //?}
    }

    private static void submit(GuiRenderState target, GuiTextRenderState text) {
        //? if <26.1 {
        /*target.submitText(text);
        *///?} else {
        target.addText(text);
        //?}
    }

    //? if <26.1 {
    /*private static void submit(GuiRenderState target, net.minecraft.client.gui.render.state.GuiElementRenderState element) {
        target.submitGuiElement(element);
    }
    *///?} else {
    private static void submit(GuiRenderState target, net.minecraft.client.renderer.state.gui.GuiElementRenderState element) {
        target.addGuiElement(element);
    }
    //?}
}
