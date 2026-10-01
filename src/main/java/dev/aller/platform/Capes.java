package dev.aller.platform;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aller.module.Modules;
import dev.aller.module.mods.CosmeticMods;
import dev.aller.ui.Colors;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if <26.1 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?} else {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
//?}

/** Generates the Aller cape texture and swaps it into the local player's skin. */
public final class Capes {
    private static final int W = 64, H = 32;
    private static int builtKey;
    private static boolean built;
    private static PlayerSkin lastIn, lastOut;

    private Capes() {}

    /** Called for the local player's skin every time it is looked up. */
    public static PlayerSkin apply(PlayerSkin skin) {
        CosmeticMods.Cape mod = Modules.CAPE;
        if (!mod.enabled()) return skin;
        int key = mod.tint() * 31 + mod.style.get().ordinal();
        if (!built || key != builtKey) {
            Mc.mc().getTextureManager().register(Ids.of("cape"), new DynamicTexture(() -> "aller-cape", paint(mod.style.get(), mod.tint())));
            built = true;
            builtKey = key;
            lastIn = null;
        }
        if (skin != lastIn) {
            lastIn = skin;
            //? if <26.1 {
            /*lastOut = new PlayerSkin(skin.texture(), skin.textureUrl(), Ids.of("cape"), Ids.of("cape"), skin.model(), skin.secure());
            *///?} else {
            ClientAsset.Texture cape = new ClientAsset.ResourceTexture(Ids.of("cape"), Ids.of("cape"));
            lastOut = new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure());
            //?}
        }
        return lastOut;
    }

    private static NativeImage paint(CosmeticMods.Cape.Style style, int tint) {
        NativeImage image = new NativeImage(W, H, false);
        float[] hsv = Colors.toHsv(tint);
        int deep = Colors.hsv((hsv[0] + 0.94f) % 1f, Math.min(1f, hsv[1] * 1.05f), hsv[2] * 0.45f, 1f);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                // The cape panel is 16 rows tall starting at row 1; the elytra area uses the full height.
                float v = Math.clamp((y - 1) / 16f, 0f, 1f);
                int c = switch (style) {
                    case GRADIENT -> y >= 15 && y <= 16 ? Colors.lighten(tint, 0.35f) : Colors.mix(Colors.lighten(tint, 0.12f), deep, v);
                    case MIDNIGHT -> {
                        boolean trim = y >= 15 && y <= 16 || x == 1 || x == 10 || x == 12 || x == 21;
                        boolean mark = x < 22 && Math.abs((x % 11) - 5.5f) + Math.abs(y - 7) < 2.6f;
                        yield trim || mark ? tint : Colors.mix(0xFF191624, 0xFF0C0A12, v);
                    }
                    case AURORA -> Colors.hsv((hsv[0] + (x * 0.6f + y) * 0.012f) % 1f, 0.62f, 0.96f - 0.3f * v, 1f);
                };
                image.setPixel(x, y, c | 0xFF000000);
            }
        }
        return image;
    }
}
