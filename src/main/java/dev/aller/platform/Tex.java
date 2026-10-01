package dev.aller.platform;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=26.1 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
//?}

/** A GPU texture owned by Aller, smoothly filtered and clamped. Create and use on the render thread. */
public final class Tex implements AutoCloseable {
    private final DynamicTexture texture;
    private final TextureSetup setup;
    public final int width;
    public final int height;

    /** @param argb row-major pixels, {@code width * height} long */
    public Tex(String label, int width, int height, int[] argb) {
        this(label, toImage(width, height, argb));
    }

    public Tex(String label, NativeImage image) {
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.texture = new DynamicTexture(() -> label, image);
        //? if <26.1 {
        /*texture.setFilter(true, false);
        texture.setClamp(true);
        setup = TextureSetup.singleTexture(texture.getTextureView());
        *///?} else {
        setup = TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        //?}
    }

    private static NativeImage toImage(int width, int height, int[] argb) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[y * width + x]);
            }
        }
        return image;
    }

    public TextureSetup setup() {
        return setup;
    }

    @Override
    public void close() {
        texture.close();
    }
}
