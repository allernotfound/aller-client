package dev.aller.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aller.ui.Colors;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.font.SdfAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
//?}

/**
 * Aller's drawing surface: resolution-independent rounded shapes, shadows and smooth text, layered
 * on top of Minecraft's GUI renderer. All coordinates are GUI pixels (floats; sub-pixel positions
 * are fine). This class is the only place UI code meets the version-specific graphics API.
 */
public final class Canvas {
    private static final int FILL = 0, STROKE = 1, SHADOW = 2;

    //? if <26.1 {
    /*private final GuiGraphics g;

    public Canvas(GuiGraphics graphics) {
        this.g = graphics;
    }

    public GuiGraphics raw() {
        return g;
    }
    *///?} else {
    private final GuiGraphicsExtractor g;

    public Canvas(GuiGraphicsExtractor graphics) {
        this.g = graphics;
    }

    public GuiGraphicsExtractor raw() {
        return g;
    }
    //?}

    private final float[] alphaStack = new float[32];
    private int alphaDepth;
    private float alpha = 1f;

    /** Width of the drawing area in the current units (GUI pixels divided by any active UI scale). */
    public float width() {
        return g.guiWidth() / unit;
    }

    public float height() {
        return g.guiHeight() / unit;
    }

    private float unit = 1f;
    private final float[] unitStack = new float[8];
    private int unitDepth;

    /** Scales everything drawn until {@link #endScale()} and shrinks {@link #width()}/{@link #height()} to match. */
    public void beginScale(float factor) {
        unitStack[unitDepth++] = unit;
        unit *= factor;
        g.pose().pushMatrix();
        g.pose().scale(factor, factor);
    }

    public void endScale() {
        g.pose().popMatrix();
        unit = unitStack[--unitDepth];
    }

    /** Device pixels per GUI pixel. */
    public static float scale() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    // ---- state ---------------------------------------------------------------------------------

    /** Multiplies the opacity of everything drawn until the matching {@link #popAlpha()}. */
    public void pushAlpha(float factor) {
        alphaStack[alphaDepth++] = alpha;
        alpha *= Math.clamp(factor, 0f, 1f);
    }

    public void popAlpha() {
        alpha = alphaStack[--alphaDepth];
    }

    public float alpha() {
        return alpha;
    }

    public void push() {
        g.pose().pushMatrix();
    }

    public void pop() {
        g.pose().popMatrix();
    }

    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    /** Scales around a pivot, e.g. the centre of a panel that pops open. */
    public void scale(float factor, float pivotX, float pivotY) {
        g.pose().translate(pivotX, pivotY).scale(factor, factor).translate(-pivotX, -pivotY);
    }

    /** Rotates clockwise by {@code radians} around a pivot. */
    public void rotate(float radians, float pivotX, float pivotY) {
        g.pose().translate(pivotX, pivotY).rotate(radians).translate(-pivotX, -pivotY);
    }

    public void clip(float x, float y, float w, float h) {
        g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    public void unclip() {
        g.disableScissor();
    }

    /** Starts a new layer: everything drawn after this is composited above everything drawn before. */
    public void layer() {
        g.nextStratum();
    }

    /** Blurs everything drawn so far this frame (the world and HUD); may be called once per frame. */
    public void blurBehind() {
        g.blurBeforeThisStratum();
    }

    // ---- shapes --------------------------------------------------------------------------------

    public void rect(float x, float y, float w, float h, float radius, int color) {
        shape(x, y, w, h, radius, 0, FILL, 1f, color, color, color, color);
    }

    public void gradientV(float x, float y, float w, float h, float radius, int top, int bottom) {
        shape(x, y, w, h, radius, 0, FILL, 1f, top, bottom, bottom, top);
    }

    public void gradientH(float x, float y, float w, float h, float radius, int left, int right) {
        shape(x, y, w, h, radius, 0, FILL, 1f, left, left, right, right);
    }

    public void stroke(float x, float y, float w, float h, float radius, float thickness, int color) {
        shape(x, y, w, h, radius, thickness, STROKE, 1f, color, color, color, color);
    }

    /** A soft shadow whose falloff extends {@code blur} pixels beyond the given box. */
    public void shadow(float x, float y, float w, float h, float radius, float blur, int color) {
        shape(x, y, w, h, radius, blur, SHADOW, blur, color, color, color, color);
    }

    public void circle(float cx, float cy, float r, int color) {
        rect(cx - r, cy - r, r * 2, r * 2, r, color);
    }

    public void ring(float cx, float cy, float r, float thickness, int color) {
        stroke(cx - r, cy - r, r * 2, r * 2, r, thickness, color);
    }

    /**
     * A regular polygon with a vertex pointing up ({@code sides} 3 to 8), fitting inside radius {@code r}.
     *
     * @param rounding corner radius
     */
    public void polygon(float cx, float cy, float r, int sides, float rounding, int color) {
        quad(cx, cy, r, r, rounding, 0, FILL, 1f, 1f, 0f, color, color, color, color, sides);
    }

    public void polygonStroke(float cx, float cy, float r, int sides, float rounding, float thickness, int color) {
        quad(cx, cy, r, r, rounding, thickness, STROKE, 1f, 1f, 0f, color, color, color, color, sides);
    }

    /** A five-pointed star with tips at radius {@code r}. */
    public void star(float cx, float cy, float r, int color) {
        quad(cx, cy, r, r, r * 0.08f, 0, FILL, 1f, 1f, 0f, color, color, color, color, 1);
    }

    /** A round-capped line segment, for graphs and tick marks. */
    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            circle(x1, y1, thickness / 2, color);
            return;
        }
        float cos = dx / len, sin = dy / len;
        float hw = len / 2 + thickness / 2, hh = thickness / 2;
        quad((x1 + x2) / 2, (y1 + y2) / 2, hw, hh, hh, 0, FILL, 1f, cos, sin, color, color, color, color, 0);
    }

    private void shape(float x, float y, float w, float h, float radius, float param, int mode, float pad,
            int tl, int bl, int br, int tr) {
        if (w <= 0 || h <= 0 || alpha <= 0.002f) return;
        quad(x + w / 2, y + h / 2, w / 2, h / 2, radius, param, mode, pad, 1f, 0f, tl, bl, br, tr, 0);
    }

    private void quad(float cx, float cy, float hw, float hh, float radius, float param, int mode, float pad,
            float cos, float sin, int tl, int bl, int br, int tr, int kind) {
        if (alpha <= 0.002f) return;
        Matrix3x2fStack m = g.pose();
        float ex = hw + pad, ey = hh + pad;
        float[] v = new float[16];
        int[] colors = {Colors.fade(tl, alpha), Colors.fade(bl, alpha), Colors.fade(br, alpha), Colors.fade(tr, alpha)};
        if ((colors[0] | colors[1] | colors[2] | colors[3]) >>> 24 == 0) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            // Corners in order: top-left, bottom-left, bottom-right, top-right.
            float lx = i < 2 ? -ex : ex, ly = i == 1 || i == 2 ? ey : -ey;
            float px = cx + lx * cos - ly * sin;
            float py = cy + lx * sin + ly * cos;
            float tx = m.m00() * px + m.m10() * py + m.m20();
            float ty = m.m01() * px + m.m11() * py + m.m21();
            v[i * 4] = tx;
            v[i * 4 + 1] = ty;
            v[i * 4 + 2] = lx;
            v[i * 4 + 3] = ly;
            minX = Math.min(minX, tx);
            minY = Math.min(minY, ty);
            maxX = Math.max(maxX, tx);
            maxY = Math.max(maxY, ty);
        }
        submit(new Mesh(Pipelines.SHAPE, TextureSetup.noTexture(), g.scissorStack.peek(), bounds(minX, minY, maxX, maxY),
                v, colors, q4(hw), q4(hh), q4(radius), q4(param), mode * 0.5f, kind / 8f));
    }

    /** Quarter-pixel fixed point, the precision the shape shader unpacks. */
    private static int q4(float value) {
        return Math.clamp(Math.round(value * 4), 0, Short.MAX_VALUE);
    }

    // ---- text ----------------------------------------------------------------------------------

    /** Draws a single line with its top at {@code y}. Returns the advance width. */
    public float text(Fonts font, CharSequence text, float x, float y, float size, int color) {
        if (text.isEmpty()) return 0;
        SdfAtlas atlas = font.atlas();
        float s = size / SdfAtlas.EM;
        float pen = 0;
        int c = Colors.fade(color, alpha);
        if (c >>> 24 == 0) return font.width(text, size);

        Matrix3x2fStack m = g.pose();
        float m00 = m.m00(), m01 = m.m01(), m10 = m.m10(), m11 = m.m11(), m20 = m.m20(), m21 = m.m21();
        float baseline = y + atlas.ascent * s;
        float[] v = new float[text.length() * 16];
        int n = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        char prev = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (prev != 0) pen += atlas.kern(prev, ch);
            prev = ch;
            SdfAtlas.Glyph gl = atlas.glyph(ch);
            if (gl.w() > 0) {
                float x0 = x + (pen + gl.xOff()) * s, y0 = baseline + gl.yOff() * s;
                float x1 = x0 + gl.w() * s, y1 = y0 + gl.h() * s;
                for (int k = 0; k < 4; k++) {
                    float qx = k < 2 ? x0 : x1, qy = k == 1 || k == 2 ? y1 : y0;
                    float tx = m00 * qx + m10 * qy + m20;
                    float ty = m01 * qx + m11 * qy + m21;
                    v[n++] = tx;
                    v[n++] = ty;
                    v[n++] = k < 2 ? gl.u0() : gl.u1();
                    v[n++] = k == 1 || k == 2 ? gl.v1() : gl.v0();
                    minX = Math.min(minX, tx);
                    minY = Math.min(minY, ty);
                    maxX = Math.max(maxX, tx);
                    maxY = Math.max(maxY, ty);
                }
            }
            pen += gl.advance();
        }
        if (n > 0) {
            float[] verts = n == v.length ? v : java.util.Arrays.copyOf(v, n);
            submit(new Mesh(Pipelines.TEXT, font.texture().setup(), g.scissorStack.peek(), bounds(minX, minY, maxX, maxY),
                    verts, new int[] {c}, 0, 0, 0, 0, 0f, 0f));
        }
        return pen * s;
    }

    public void textCentered(Fonts font, CharSequence text, float cx, float y, float size, int color) {
        text(font, text, cx - font.width(text, size) / 2, y, size, color);
    }

    public void textRight(Fonts font, CharSequence text, float right, float y, float size, int color) {
        text(font, text, right - font.width(text, size), y, size, color);
    }

    /** Draws text vertically centred inside a row of the given height. */
    public float textMiddle(Fonts font, CharSequence text, float x, float y, float rowHeight, float size, int color) {
        return text(font, text, x, y + (rowHeight - font.height(size)) / 2, size, color);
    }

    // ---- special -------------------------------------------------------------------------------

    /** Fills the area with the animated ASCII noise field. */
    public void backdrop(float x, float y, float w, float h, int accent, float intensity, float seconds, float cellGuiPx) {
        Matrix3x2fStack m = g.pose();
        float sc = scale();
        float[] qx = {x, x, x + w, x + w}, qy = {y, y + h, y + h, y};
        float[] v = new float[16];
        for (int i = 0; i < 4; i++) {
            v[i * 4] = m.m00() * qx[i] + m.m10() * qy[i] + m.m20();
            v[i * 4 + 1] = m.m01() * qx[i] + m.m11() * qy[i] + m.m21();
            v[i * 4 + 2] = qx[i] * sc * unit;
            v[i * 4 + 3] = qy[i] * sc * unit;
        }
        int cs = (int) (seconds * 100) & 0x3FFFFFFF;
        int color = Colors.withAlpha(accent, intensity * alpha);
        submit(new Mesh(Pipelines.BACKDROP, TextureSetup.noTexture(), g.scissorStack.peek(), bounds(x, y, x + w, y + h),
                v, new int[] {color}, cs & 0x7FFF, cs >> 15, Math.max(4, Math.round(cellGuiPx * sc)), 0, 0f, 0f));
    }

    /** A plain vanilla rectangle; usable before Aller's shaders have loaded (startup overlay). */
    public void plainRect(float x, float y, float w, float h, int color) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Colors.fade(color, alpha));
    }

    public void item(ItemStack stack, float x, float y) {
        //? if <26.1 {
        /*g.renderItem(stack, Math.round(x), Math.round(y));
        *///?} else {
        g.item(stack, Math.round(x), Math.round(y));
        //?}
    }

    /** Minecraft's own pixel font, for content that must match vanilla formatting. */
    public void vanillaText(net.minecraft.network.chat.Component text, float x, float y, int color) {
        var font = Minecraft.getInstance().font;
        //? if <26.1 {
        /*g.drawString(font, text, Math.round(x), Math.round(y), Colors.fade(color, alpha), true);
        *///?} else {
        g.text(font, text, Math.round(x), Math.round(y), Colors.fade(color, alpha), true);
        //?}
    }

    // ---- submission ----------------------------------------------------------------------------

    private void submit(Mesh mesh) {
        //? if <26.1 {
        /*g.guiRenderState.submitGuiElement(mesh);
        *///?} else {
        g.guiRenderState.addGuiElement(mesh);
        //?}
    }

    private ScreenRectangle bounds(float minX, float minY, float maxX, float maxY) {
        int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY);
        ScreenRectangle r = new ScreenRectangle(x0, y0, (int) Math.ceil(maxX) - x0, (int) Math.ceil(maxY) - y0);
        ScreenRectangle scissor = g.scissorStack.peek();
        return scissor != null ? scissor.intersection(r) : r;
    }

    /**
     * One batch of quads handed to Minecraft's GUI renderer. Positions are already transformed, so
     * the element is independent of the pose stack by the time it is drawn.
     *
     * @param verts  x, y, u, v per vertex
     * @param colors one per vertex, or a single colour for the whole mesh
     */
    private record Mesh(RenderPipeline pipeline, TextureSetup textureSetup, ScreenRectangle scissorArea, ScreenRectangle bounds,
            float[] verts, int[] colors, int u1, int v1, int u2, int v2, float mode, float kind) implements GuiElementRenderState {

        //? if <26.1 {
        /*@Override
        public void buildVertices(VertexConsumer consumer, float z) {
            emit(consumer, z);
        }
        *///?} else {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            emit(consumer, 0f);
        }
        //?}

        private void emit(VertexConsumer consumer, float z) {
            for (int i = 0, vi = 0; i < verts.length; i += 4, vi++) {
                consumer.addVertex(verts[i], verts[i + 1], z)
                        .setColor(colors.length == 1 ? colors[0] : colors[vi % colors.length])
                        .setUv(verts[i + 2], verts[i + 3])
                        .setUv1(u1, v1)
                        .setUv2(u2, v2)
                        .setNormal(mode, kind, 0f);
            }
        }
    }
}
