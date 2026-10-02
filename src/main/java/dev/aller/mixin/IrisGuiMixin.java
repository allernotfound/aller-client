package dev.aller.mixin;

import dev.aller.Hooks;
import dev.aller.platform.Canvas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

/**
 * Iris draws its buttons and panels from its own texture through two helpers; restyled, they use
 * Aller's. Optional, like {@code SodiumWidgetMixin}.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.GuiUtil", remap = false)
public abstract class IrisGuiMixin {
    //? if <26.1 {
    /*@Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aller$button(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered, boolean disabled, CallbackInfo ci) {
        if (Hooks.menuButton(Canvas.of(graphics), x, y, width, height, hovered, disabled)) ci.cancel();
    }

    @Inject(method = "drawPanel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aller$panel(GuiGraphics graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (Hooks.menuPanel(Canvas.of(graphics), x, y, width, height)) ci.cancel();
    }
    *///?} else {
    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aller$button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean hovered, boolean disabled, CallbackInfo ci) {
        if (Hooks.menuButton(Canvas.of(graphics), x, y, width, height, hovered, disabled)) ci.cancel();
    }

    @Inject(method = "drawPanel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void aller$panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (Hooks.menuPanel(Canvas.of(graphics), x, y, width, height)) ci.cancel();
    }
    //?}
}
