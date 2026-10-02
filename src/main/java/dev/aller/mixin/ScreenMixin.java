package dev.aller.mixin;

import dev.aller.Hooks;
import dev.aller.platform.Canvas;
import dev.aller.screen.LoadingSkin;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

/**
 * Brackets a vanilla screen's drawing so restyled menus know when it is their turn, swaps the
 * panorama and blur for Aller's background, and lets Aller paint over the loading screens.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    //? if <26.1 {
    /*@Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void aller$begin(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hooks.menuBegin((Screen) (Object) this);
        Hooks.screenBegin(Canvas.of(g), (Screen) (Object) this);
    }

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void aller$skin(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hooks.menuEnd(Canvas.of(g));
        Hooks.screenEnd(Canvas.of(g));
        LoadingSkin.draw(new Canvas(g), (Screen) (Object) this);
    }

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void aller$panorama(GuiGraphics g, float delta, CallbackInfo ci) {
        if (Hooks.menuBackdrop(Canvas.of(g))) ci.cancel();
    }

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void aller$blur(GuiGraphics g, CallbackInfo ci) {
        if (Hooks.menuSkipBlur()) ci.cancel();
    }
    *///?} else {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
    private void aller$begin(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hooks.menuBegin((Screen) (Object) this);
        Hooks.screenBegin(Canvas.of(g), (Screen) (Object) this);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void aller$skin(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hooks.menuEnd(Canvas.of(g));
        Hooks.screenEnd(Canvas.of(g));
        LoadingSkin.draw(new Canvas(g), (Screen) (Object) this);
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void aller$panorama(GuiGraphicsExtractor g, float delta, CallbackInfo ci) {
        if (Hooks.menuBackdrop(Canvas.of(g))) ci.cancel();
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void aller$blur(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (Hooks.menuSkipBlur()) ci.cancel();
    }
    //?}
}
