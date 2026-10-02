package dev.aller.mixin;

import dev.aller.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

/** Lets Aller's browser take a link clicked in chat before it goes to the system browser. */
//? if <26.1 {
/*@Mixin(net.minecraft.Util.OS.class)
*///?} else {
@Mixin(net.minecraft.util.Util.OS.class)
//?}
public abstract class UtilOsMixin {
    @Inject(method = "openUri(Ljava/net/URI;)V", at = @At("HEAD"), cancellable = true)
    private void aller$openLink(URI uri, CallbackInfo ci) {
        if (Hooks.openLink(uri)) ci.cancel();
    }
}
