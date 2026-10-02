package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import net.minecraft.client.input.KeyEvent;
//?}

/** Lets the screenshot card take its keys before the game or a screen acts on them. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    //? if <26.1 {
    /*@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void aller$key(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (action == 1 && Hooks.key(key, mods)) ci.cancel();
    }
    *///?} else {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void aller$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == 1 && Hooks.key(event.key(), event.modifiers())) ci.cancel();
    }
    //?}
}
