package dev.aller.mixin;

import dev.aller.Hooks;
import dev.aller.platform.Mc;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scroll-to-adjust zoom: swallows the wheel so the hotbar slot does not change. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void aller$scroll(long window, double dx, double dy, CallbackInfo ci) {
        if (dy != 0 && Mc.screen() == null && Mc.mc().player != null && Hooks.scroll(dy)) ci.cancel();
    }
}
