package dev.aller.mixin.pocket;

import dev.aller.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the world that is not on screen from reaching the renderer: while its packets run, a new
 * level is only stored, and nothing draws a frame or swaps the screen.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPocketMixin {
    @Shadow public ClientLevel level;

    //? if <26.1 {
    /*@Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
    private void pocket$setLevel(ClientLevel level, net.minecraft.client.gui.screens.ReceivingLevelScreen.Reason reason, CallbackInfo ci) {
        if (!Hooks.pocketAway()) return;
        this.level = level;
        ci.cancel();
    }

    @Inject(method = "updateScreenAndTick", at = @At("HEAD"), cancellable = true)
    private void pocket$showScreen(Screen screen, CallbackInfo ci) {
        if (Hooks.pocketScreen(screen)) ci.cancel();
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void pocket$setScreen(Screen screen, CallbackInfo ci) {
        if (Hooks.pocketScreen(screen)) ci.cancel();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void pocket$disconnect(Screen screen, boolean keepPacks, CallbackInfo ci) {
        Hooks.pocketDisconnect();
    }
    *///?} else {
    @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
    private void pocket$setLevel(ClientLevel level, CallbackInfo ci) {
        if (!Hooks.pocketAway()) return;
        this.level = level;
        ci.cancel();
    }

    @Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
    private void pocket$showScreen(Screen screen, CallbackInfo ci) {
        if (Hooks.pocketScreen(screen)) ci.cancel();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void pocket$disconnect(Screen screen, boolean keepPacks, boolean stopSound, CallbackInfo ci) {
        Hooks.pocketDisconnect();
    }
    //?}
}
