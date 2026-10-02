package dev.aller.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.aller.Hooks;
import dev.aller.platform.Canvas;
import org.spongepowered.asm.mixin.Mixin;
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

/**
 * The draw calls every vanilla widget funnels through. On a restyled menu Aller draws its own
 * version of the sprite, texture or rectangle and the vanilla one is dropped.
 */
//? if <26.1 {
/*@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void aller$sprite(RenderPipeline pipeline, ResourceLocation id, int x, int y, int w, int h, int color, CallbackInfo ci) {
        if (Hooks.menuSprite(Canvas.of((GuiGraphics) (Object) this), id.getNamespace(), id.getPath(), x, y, w, h, color)) ci.cancel();
    }

    @Inject(method = "innerBlit", at = @At("HEAD"), cancellable = true)
    private void aller$texture(RenderPipeline pipeline, ResourceLocation id, int x0, int x1, int y0, int y1,
            float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
        if (Hooks.menuTexture(Canvas.of((GuiGraphics) (Object) this), id.getNamespace(), id.getPath(), x0, y0, x1, y1)) ci.cancel();
    }

    @Inject(method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V", at = @At("HEAD"), cancellable = true)
    private void aller$fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
        if (Hooks.menuFill(Canvas.of((GuiGraphics) (Object) this), x0, y0, x1, y1, color)) ci.cancel();
    }

    @Inject(method = "fillGradient", at = @At("HEAD"), cancellable = true)
    private void aller$gradient(int x0, int y0, int x1, int y1, int top, int bottom, CallbackInfo ci) {
        if (Hooks.menuFill(Canvas.of((GuiGraphics) (Object) this), x0, y0, x1, y1, bottom)) ci.cancel();
    }
}
*///?} else {
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void aller$sprite(RenderPipeline pipeline, Identifier id, int x, int y, int w, int h, int color, CallbackInfo ci) {
        if (Hooks.menuSprite(Canvas.of((GuiGraphicsExtractor) (Object) this), id.getNamespace(), id.getPath(), x, y, w, h, color)) ci.cancel();
    }

    @Inject(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
            at = @At("HEAD"), cancellable = true)
    private void aller$texture(RenderPipeline pipeline, Identifier id, int x0, int x1, int y0, int y1,
            float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
        if (Hooks.menuTexture(Canvas.of((GuiGraphicsExtractor) (Object) this), id.getNamespace(), id.getPath(), x0, y0, x1, y1)) ci.cancel();
    }

    @Inject(method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V", at = @At("HEAD"), cancellable = true)
    private void aller$fill(RenderPipeline pipeline, int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
        if (Hooks.menuFill(Canvas.of((GuiGraphicsExtractor) (Object) this), x0, y0, x1, y1, color)) ci.cancel();
    }

    @Inject(method = "fillGradient", at = @At("HEAD"), cancellable = true)
    private void aller$gradient(int x0, int y0, int x1, int y1, int top, int bottom, CallbackInfo ci) {
        if (Hooks.menuFill(Canvas.of((GuiGraphicsExtractor) (Object) this), x0, y0, x1, y1, bottom)) ci.cancel();
    }
}
//?}
