package dev.aller.mixin;

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

/** Lets Aller paint over vanilla's loading screens once they have drawn. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    //? if <26.1 {
    /*@Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void aller$skin(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        LoadingSkin.draw(new Canvas(g), (Screen) (Object) this);
    }
    *///?} else {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void aller$skin(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        LoadingSkin.draw(new Canvas(g), (Screen) (Object) this);
    }
    //?}
}
