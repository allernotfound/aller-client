package dev.aller.mixin.pocket;

import dev.aller.Hooks;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The world that is not on screen makes no sound. */
@Mixin(SoundManager.class)
public abstract class SoundPocketMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void pocket$play(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (Hooks.pocketAway()) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void pocket$playDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (Hooks.pocketAway()) ci.cancel();
    }

    @Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
    private void pocket$ticking(TickableSoundInstance sound, CallbackInfo ci) {
        if (Hooks.pocketAway()) ci.cancel();
    }
}
