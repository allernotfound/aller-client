package dev.aller.hud;

import com.google.gson.JsonObject;
import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.Canvas;
import dev.aller.setting.Settings;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;

/**
 * A module that draws something on the HUD. Position is stored as an offset from one of nine
 * screen anchors, so an element placed near an edge stays attached to that edge when the window
 * is resized or the GUI scale changes.
 */
public abstract class HudModule extends Module {
    public enum AnchorH { LEFT, CENTER, RIGHT }

    public enum AnchorV { TOP, MIDDLE, BOTTOM }

    public final Settings.Num scale = num("scale", "Scale", 1f, 0.5f, 2.5f, 0.05f).suffix("x");
    public final Settings.Bool background = bool("background", "Background", true);

    private final AnchorH defaultH;
    private final AnchorV defaultV;
    private final float defaultX, defaultY;
    public AnchorH anchorH;
    public AnchorV anchorV;
    public float offX, offY;
    /** Unscaled content size, set by {@link #measure}. */
    public float w, h;
    /** Whether the element was laid out and drawn this frame (it may be enabled but have nothing to show). */
    public boolean shown;
    final Spring appear = Spring.bouncy(0);

    protected HudModule(String id, String name, String description, AnchorH h, AnchorV v, float offX, float offY) {
        super(id, name, description, Category.HUD);
        this.defaultH = this.anchorH = h;
        this.defaultV = this.anchorV = v;
        this.defaultX = this.offX = offX;
        this.defaultY = this.offY = offY;
    }

    /**
     * Computes {@link #w} and {@link #h} for this frame.
     *
     * @param editing true in the HUD editor, where elements show sample content even with no data
     * @return false to hide the element this frame
     */
    protected abstract boolean measure(boolean editing);

    /** Draws with the origin at the element's top-left corner. */
    protected abstract void render(Canvas c, boolean editing);

    public float scaledW() {
        return w * scale.get();
    }

    public float scaledH() {
        return h * scale.get();
    }

    public float x(float screenW) {
        return switch (anchorH) {
            case LEFT -> offX;
            case CENTER -> screenW / 2 + offX - scaledW() / 2;
            case RIGHT -> screenW - offX - scaledW();
        };
    }

    public float y(float screenH) {
        return switch (anchorV) {
            case TOP -> offY;
            case MIDDLE -> screenH / 2 + offY - scaledH() / 2;
            case BOTTOM -> screenH - offY - scaledH();
        };
    }

    /** Moves the element so its top-left is at (x, y), re-anchoring to the nearest third of the screen. */
    public void place(float x, float y, float screenW, float screenH) {
        place(x, y, screenW, screenH, null, null);
    }

    /**
     * @param h the edge to stay attached to, or null to choose by position. An element whose
     *          left edge was lined up with others must anchor left, or it would drift out of line
     *          whenever its width changes.
     */
    public void place(float x, float y, float screenW, float screenH, AnchorH h, AnchorV v) {
        float sw = scaledW(), sh = scaledH();
        x = Math.clamp(x, 0, Math.max(0, screenW - sw));
        y = Math.clamp(y, 0, Math.max(0, screenH - sh));
        float cx = x + sw / 2, cy = y + sh / 2;
        anchorH = h != null ? h : cx < screenW / 3 ? AnchorH.LEFT : cx > screenW * 2 / 3 ? AnchorH.RIGHT : AnchorH.CENTER;
        anchorV = v != null ? v : cy < screenH / 3 ? AnchorV.TOP : cy > screenH * 2 / 3 ? AnchorV.BOTTOM : AnchorV.MIDDLE;
        offX = switch (anchorH) {
            case LEFT -> x;
            case CENTER -> cx - screenW / 2;
            case RIGHT -> screenW - x - sw;
        };
        offY = switch (anchorV) {
            case TOP -> y;
            case MIDDLE -> cy - screenH / 2;
            case BOTTOM -> screenH - y - sh;
        };
    }

    public void resetPosition() {
        anchorH = defaultH;
        anchorV = defaultV;
        offX = defaultX;
        offY = defaultY;
    }

    @Override
    public void resetToDefaults() {
        super.resetToDefaults();
        resetPosition();
    }

    /** Standard chip background, honouring the Background setting. */
    protected void chip(Canvas c, float radius) {
        if (background.get()) Theme.chip(c, 0, 0, w, h, radius, Theme.GLASS_HUD);
    }

    @Override
    public JsonObject save() {
        JsonObject o = super.save();
        o.addProperty("anchor_h", anchorH.name());
        o.addProperty("anchor_v", anchorV.name());
        o.addProperty("x", offX);
        o.addProperty("y", offY);
        return o;
    }

    @Override
    public void load(JsonObject o) {
        super.load(o);
        try {
            if (o.has("anchor_h")) anchorH = AnchorH.valueOf(o.get("anchor_h").getAsString());
            if (o.has("anchor_v")) anchorV = AnchorV.valueOf(o.get("anchor_v").getAsString());
            if (o.has("x")) offX = o.get("x").getAsFloat();
            if (o.has("y")) offY = o.get("y").getAsFloat();
        } catch (RuntimeException e) {
            resetPosition();
        }
    }
}
