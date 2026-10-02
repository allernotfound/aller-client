package dev.aller.platform;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if <26.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.resources.Identifier;
//?}

/** A decoded image as a texture (a page still, a site icon). Create, draw and close on the render thread. */
public final class Picture implements AutoCloseable {
    private static int count;

    //? if <26.1 {
    /*private final ResourceLocation id;
    *///?} else {
    private final Identifier id;
    //?}
    public final int width, height;

    /** Takes ownership of the image. */
    public Picture(NativeImage image) {
        width = image.getWidth();
        height = image.getHeight();
        int n = count++;
        id = Ids.of("picture/" + n);
        Mc.mc().getTextureManager().register(id, new DynamicTexture(() -> "aller-picture-" + n, image));
    }

    /** Stretched over the rectangle, faded with the canvas. */
    public void draw(Canvas c, float x, float y, float w, float h) {
        int alpha = Math.round(c.alpha() * 255);
        if (alpha < 4 || width == 0 || height == 0) return;
        c.push();
        c.translate(x, y);
        c.raw().pose().scale(w / width, h / height);
        c.raw().blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, width, height, width, height, alpha << 24 | 0xFFFFFF);
        c.pop();
    }

    @Override
    public void close() {
        Mc.mc().getTextureManager().release(id);
    }
}
