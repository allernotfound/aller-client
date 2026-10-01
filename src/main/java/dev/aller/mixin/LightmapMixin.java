package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aller.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.1 {
/*import net.minecraft.client.renderer.LightTexture;
*///?} else {
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
//?}

/** Fullbright: raises the gamma value fed to the lightmap without touching the saved option. */
//? if <26.1 {
/*@Mixin(LightTexture.class)
public abstract class LightmapMixin {
    @ModifyExpressionValue(method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1, remap = false))
    private float aller$gamma(float original) {
        return Hooks.gamma(original);
    }
}
*///?} else {
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
    @ModifyExpressionValue(method = "extract",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0, remap = false))
    private float aller$gamma(float original) {
        return Hooks.gamma(original);
    }
}
//?}
