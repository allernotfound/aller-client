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
 * Sodium draws focus rings and tick boxes as four one-pixel lines; restyled, they become one
 * rounded outline. Its filled rectangles need no hook of their own (see {@code GuiGraphicsMixin}).
 * Optional: nothing here is required, so a Sodium update that renames this cannot stop the game.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.widgets.AbstractWidget", remap = false)
public abstract class SodiumWidgetMixin {
    //? if <26.1 {
    /*@Inject(method = "drawBorder", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aller$border(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if (Hooks.menuBorder(Canvas.of(graphics), x1, y1, x2, y2, color)) ci.cancel();
    }
    *///?} else {
    @Inject(method = "drawBorder", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void aller$border(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if (Hooks.menuBorder(Canvas.of(graphics), x1, y1, x2, y2, color)) ci.cancel();
    }
    //?}
}
