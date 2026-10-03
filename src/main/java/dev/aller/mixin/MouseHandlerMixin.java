package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aller.Hooks;
import dev.aller.platform.Mc;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import net.minecraft.client.input.MouseButtonInfo;
//?}

/**
 * Scroll-to-adjust zoom (swallows the wheel so the hotbar slot does not change), the zoom's smooth
 * camera, and clicks on the screenshot card.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean aller$smoothCamera(boolean original) {
        return Hooks.smoothCamera(original);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void aller$scroll(long window, double dx, double dy, CallbackInfo ci) {
        if (dy != 0 && Mc.screen() == null && Mc.mc().player != null && Hooks.scroll(dy)) ci.cancel();
    }

    //? if <26.1 {
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void aller$click(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button == 0 && action == 1 && Hooks.click()) ci.cancel();
    }
    *///?} else {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void aller$click(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (info.button() == 0 && action == 1 && Hooks.click()) ci.cancel();
    }
    //?}
}
