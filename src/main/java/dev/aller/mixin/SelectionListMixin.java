package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the two fills that make up a list's selection box, so they can be redrawn as one highlight. */
@Mixin(AbstractSelectionList.class)
public abstract class SelectionListMixin {
    //? if <26.1 {
    /*@Inject(method = "renderSelection", at = @At("HEAD"))
    *///?} else {
    @Inject(method = "extractSelection", at = @At("HEAD"))
    //?}
    private void aller$selectionStart(CallbackInfo ci) {
        Hooks.menuSelection(true);
    }

    //? if <26.1 {
    /*@Inject(method = "renderSelection", at = @At("RETURN"))
    *///?} else {
    @Inject(method = "extractSelection", at = @At("RETURN"))
    //?}
    private void aller$selectionEnd(CallbackInfo ci) {
        Hooks.menuSelection(false);
    }
}
