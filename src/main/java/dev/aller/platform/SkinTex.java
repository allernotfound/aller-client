package dev.aller.platform;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix3x2fStack;
//? if <26.1 {
/*import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?}

/**
 * A player skin as a texture, drawn either on the 3D player model or as a flat front view. The
 * model goes through vanilla's own GUI skin renderer, which keeps one picture per frame: draw at
 * most one model a frame and use {@link #flat} for everything else.
 */
public final class SkinTex {
    /** Model height in blocks and how much of its box it fills, as vanilla's skin widget has them. */
    private static final float MODEL_HEIGHT = 2.125f, FIT = 0.97f;

    //? if <26.1 {
    /*private final ResourceLocation id;
    private static PlayerModel wide, slender;
    *///?} else {
    private final Identifier id;
    private static Model.Simple wide, slender;
    //?}
    private static EntityModelSet bakedFrom;

    /**
     * @param key  unique name for the texture (lower case letters and digits)
     * @param argb 64 by 64 pixels, row-major
     */
    public SkinTex(String key, int[] argb) {
        NativeImage image = new NativeImage(64, 64, false);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) image.setPixel(x, y, argb[y * 64 + x]);
        }
        id = Ids.of("wardrobe/" + key);
        Mc.mc().getTextureManager().register(id, new DynamicTexture(() -> "aller-skin-" + key, image));
    }

    private SkinTex() {
        id = null;
    }

    /** The skin the game itself loaded for the player, whichever texture that is at the moment. */
    public static SkinTex fetched() {
        return new SkinTex();
    }

    //? if <26.1 {
    /*ResourceLocation id() {
        return id != null ? id : Skins.fetchedTexture();
    }
    *///?} else {
    Identifier id() {
        return id != null ? id : Skins.fetchedTexture();
    }
    //?}

    /** Height above the bottom of its box at which the model's feet stand. */
    public static float feet(float h, float zoom) {
        return 0.101f * FIT * h / MODEL_HEIGHT * zoom;
    }

    /** Width of the model across the shoulders, for a box of the given height. */
    public static float span(float h, float zoom) {
        return FIT * h / MODEL_HEIGHT * zoom;
    }

    /**
     * The player model standing on the bottom edge of the box. It cannot be faded, so it is left
     * out while the canvas is mostly transparent.
     *
     * @param pitch degrees, positive looks down on the model
     * @param yaw   degrees
     * @param zoom  size relative to filling the box
     * @param sway  -1 to 1, a little idle movement of the arms
     */
    public void model(Canvas c, boolean slim, float x, float y, float w, float h, float pitch, float yaw, float zoom, float sway) {
        if (c.alpha() < 0.5f || zoom <= 0.02f) return;
        Matrix3x2fStack m = c.raw().pose();
        int x0 = Math.round(m.m00() * x + m.m20()), y0 = Math.round(m.m11() * y + m.m21());
        int x1 = Math.round(m.m00() * (x + w) + m.m20()), y1 = Math.round(m.m11() * (y + h) + m.m21());
        if (x1 - x0 < 4 || y1 - y0 < 4) return;
        float scale = FIT * (y1 - y0) / MODEL_HEIGHT * zoom;
        bake();
        var model = slim ? slender : wide;
        pose(model.root(), sway);
        //? if <26.1 {
        /*c.raw().submitSkinRenderState(model, id(), scale, -pitch, yaw, -MODEL_HEIGHT / 2, x0, y0, x1, y1);
        *///?} else {
        c.raw().skin(model, id(), scale, -pitch, yaw, -MODEL_HEIGHT / 2, x0, y0, x1, y1);
        //?}
    }

    private static void bake() {
        EntityModelSet models = Mc.mc().getEntityModels();
        if (models == bakedFrom && wide != null) return;
        bakedFrom = models;
        //? if <26.1 {
        /*wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
        slender = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        *///?} else {
        wide = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
        slender = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
        //?}
    }

    private static void pose(ModelPart root, float sway) {
        try {
            ModelPart right = root.getChild("right_arm"), left = root.getChild("left_arm");
            right.zRot = 0.07f + 0.025f * sway;
            left.zRot = -0.07f - 0.025f * sway;
            right.xRot = 0.05f * sway;
            left.xRot = -0.05f * sway;
        } catch (RuntimeException ignored) {
            // a model without those parts just stands still
        }
    }

    /** The skin's front, both layers, as a flat figure {@code h} tall and half as wide, with its top left at (x, y). */
    public void flat(Canvas c, boolean slim, float x, float y, float h) {
        int alpha = Math.round(c.alpha() * 255);
        if (alpha < 4) return;
        int tint = alpha << 24 | 0xFFFFFF;
        int arm = slim ? 3 : 4;
        c.push();
        c.translate(x, y);
        c.raw().pose().scale(h / 32f, h / 32f);
        // Base layer, then the hat, jacket, sleeves and trousers over it.
        part(c, 8, 8, 4, 0, 8, 8, tint);
        part(c, 20, 20, 4, 8, 8, 12, tint);
        part(c, 44, 20, 4 - arm, 8, arm, 12, tint);
        part(c, 36, 52, 12, 8, arm, 12, tint);
        part(c, 4, 20, 4, 20, 4, 12, tint);
        part(c, 20, 52, 8, 20, 4, 12, tint);
        part(c, 40, 8, 4, 0, 8, 8, tint);
        part(c, 20, 36, 4, 8, 8, 12, tint);
        part(c, 44, 36, 4 - arm, 8, arm, 12, tint);
        part(c, 52, 52, 12, 8, arm, 12, tint);
        part(c, 4, 36, 4, 20, 4, 12, tint);
        part(c, 4, 52, 8, 20, 4, 12, tint);
        c.pop();
    }

    private void part(Canvas c, int u, int v, int x, int y, int w, int h, int tint) {
        c.raw().blit(RenderPipelines.GUI_TEXTURED, id(), x, y, u, v, w, h, w, h, 64, 64, tint);
    }
}
