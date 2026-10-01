package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.aller.platform.Canvas;
import dev.aller.screen.Splash;
import dev.aller.ui.Theme;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
//?}

/** Swaps the Mojang splash for Aller's. The overlay's loading logic is untouched; only its drawing changes. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    private float currentProgress;

    //? if <26.1 {
    /*private static final String RENDER = "render";
    private static final String BAR = "drawProgressBar";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIFFIIIIIII)V";
    *///?} else {
    private static final String RENDER = "extractRenderState";
    private static final String BAR = "extractProgressBar";
    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V";
    //?}

    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I", remap = false))
    private int aller$background(int original) {
        return Splash.enabled() ? Theme.BG : original;
    }

    //? if <26.1 {
    /*@WrapWithCondition(method = RENDER, at = @At(value = "INVOKE", target = BLIT))
    private boolean aller$hideLogo(GuiGraphics g, RenderPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v,
            int w, int h, int regionW, int regionH, int texW, int texH, int color) {
        return !Splash.enabled();
    }

    @Inject(method = BAR, at = @At("HEAD"), cancellable = true)
    private void aller$splash(GuiGraphics g, int x0, int y0, int x1, int y1, float alpha, CallbackInfo ci) {
        if (!Splash.enabled()) return;
        Splash.draw(new Canvas(g), currentProgress, alpha);
        ci.cancel();
    }
    *///?} else {
    @WrapWithCondition(method = RENDER, at = @At(value = "INVOKE", target = BLIT))
    private boolean aller$hideLogo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
            int w, int h, int regionW, int regionH, int texW, int texH, int color) {
        return !Splash.enabled();
    }

    @Inject(method = BAR, at = @At("HEAD"), cancellable = true)
    private void aller$splash(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float alpha, CallbackInfo ci) {
        if (!Splash.enabled()) return;
        Splash.draw(new Canvas(g), currentProgress, alpha);
        ci.cancel();
    }
    //?}
}
