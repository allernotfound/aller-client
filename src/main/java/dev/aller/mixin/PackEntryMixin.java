package dev.aller.mixin;

import dev.aller.Hooks;
import dev.aller.platform.Canvas;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

/** A row of the pack list is about to draw: the store marks the ones it has just downloaded. */
@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class PackEntryMixin {
    @Shadow
    public abstract String getPackId();

    //? if <26.1 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void aller$mark(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
        Hooks.packEntry(Canvas.of(g), getPackId(), left, top, width - 4, height);
    }
    *///?} else {
    @Inject(method = "extractContent", at = @At("HEAD"))
    private void aller$mark(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
        TransferableSelectionList.PackEntry entry = (TransferableSelectionList.PackEntry) (Object) this;
        Hooks.packEntry(Canvas.of(g), getPackId(), entry.getContentX(), entry.getContentY(), entry.getContentWidth(), entry.getContentHeight());
    }
    //?}
}
