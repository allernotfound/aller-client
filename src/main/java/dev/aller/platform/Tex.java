package dev.aller.platform;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=26.1 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
//?}

/** A GPU texture owned by Aller, clamped and smoothly filtered unless asked otherwise. Create and use on the render thread. */
public final class Tex implements AutoCloseable {
    private final DynamicTexture texture;
    private final TextureSetup setup;
    public final int width;
    public final int height;

    /** @param argb row-major pixels, {@code width * height} long */
    public Tex(String label, int width, int height, int[] argb) {
        this(label, toImage(width, height, argb), true);
    }

    /** @param smooth false to sample the nearest texel, for pixel art */
    public Tex(String label, int width, int height, int[] argb, boolean smooth) {
        this(label, toImage(width, height, argb), smooth);
    }

    public Tex(String label, NativeImage image) {
        this(label, image, true);
    }

    private Tex(String label, NativeImage image, boolean smooth) {
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.texture = new DynamicTexture(() -> label, image);
        //? if <26.1 {
        /*texture.setFilter(smooth, false);
        texture.setClamp(true);
        setup = TextureSetup.singleTexture(texture.getTextureView());
        *///?} else {
        setup = TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(smooth ? FilterMode.LINEAR : FilterMode.NEAREST));
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
