package dev.aller.mixin.pocket;

import dev.aller.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pause menu's way out, used inside the pocket, leaves the pocket and not the server. */
//? if <26.1 {
/*@Mixin(net.minecraft.client.gui.screens.PauseScreen.class)
public abstract class QuitPocketMixin {
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private static void pocket$quit(CallbackInfo ci) {
        if (Hooks.pocketQuit()) ci.cancel();
    }
}
*///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class QuitPocketMixin {
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void pocket$quit(CallbackInfo ci) {
        if (Hooks.pocketQuit()) ci.cancel();
    }
}
//?}
