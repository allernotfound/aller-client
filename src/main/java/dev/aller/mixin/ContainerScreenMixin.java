package dev.aller.mixin;

import dev.aller.Hooks;
import dev.aller.platform.Canvas;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.ClickType;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.ContainerInput;
//?}

/**
 * Tells the item mods where a container screen's slots are once it has drawn them (the contents, which the
 * screens with a recipe book draw without the rest), and lets a locked slot refuse to be thrown.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    //? if <26.1 {
    /*@Shadow protected int imageWidth;

    @Inject(method = "renderContents", at = @At("TAIL"))
    private void aller$drawn(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hooks.containerDrawn(Canvas.of(g), (AbstractContainerScreen<?>) (Object) this, leftPos, topPos, imageWidth, hoveredSlot);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void aller$throw(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
        if (type == ClickType.THROW && Hooks.slotThrow(slot)) ci.cancel();
    }
    *///?} else {
    @Shadow @org.spongepowered.asm.mixin.Final protected int imageWidth;

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void aller$drawn(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hooks.containerDrawn(Canvas.of(g), (AbstractContainerScreen<?>) (Object) this, leftPos, topPos, imageWidth, hoveredSlot);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void aller$throw(Slot slot, int slotId, int button, ContainerInput type, CallbackInfo ci) {
        if (type == ContainerInput.THROW && Hooks.slotThrow(slot)) ci.cancel();
    }
    //?}
}
