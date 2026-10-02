package dev.aller.mixin.pocket;

import org.spongepowered.asm.mixin.Mixin;
//? if >=26.1 {
import dev.aller.Hooks;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/** A screen opened by the world that is not on screen is dropped. On 1.21.8 that is in {@code MinecraftPocketMixin}. */
//? if <26.1 {
/*@Mixin(net.minecraft.client.gui.Gui.class)
public abstract class ScreenPocketMixin {
}
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
public abstract class ScreenPocketMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void pocket$setScreen(Screen screen, CallbackInfo ci) {
        if (Hooks.pocketScreen(screen)) ci.cancel();
    }
}
//?}
