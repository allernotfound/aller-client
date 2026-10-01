package dev.aller.mixin;

import dev.aller.Frame;
import dev.aller.Hooks;
import dev.aller.platform.Canvas;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
//?}

/** Draws Aller's HUD layer after the vanilla one, and hides the vanilla pieces Aller replaces. */
//? if <26.1 {
/*@Mixin(Gui.class)
public abstract class HudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void aller$hud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        Frame.hud(new Canvas(graphics));
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void aller$crosshair(CallbackInfo ci) {
        if (Hooks.cancelCrosshair()) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void aller$sidebar(CallbackInfo ci) {
        if (Hooks.hideScoreboard()) ci.cancel();
    }

    @Inject(method = "renderTabList", at = @At("HEAD"), cancellable = true)
    private void aller$tabList(CallbackInfo ci) {
        if (Hooks.replaceTabList()) ci.cancel();
    }
}
*///?} else {
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aller$hud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        Frame.hud(new Canvas(graphics));
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void aller$crosshair(CallbackInfo ci) {
        if (Hooks.cancelCrosshair()) ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void aller$sidebar(CallbackInfo ci) {
        if (Hooks.hideScoreboard()) ci.cancel();
    }

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void aller$tabList(CallbackInfo ci) {
        if (Hooks.replaceTabList()) ci.cancel();
    }
}
//?}
