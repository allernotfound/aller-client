package dev.aller.mixin;

import dev.aller.Frame;
import dev.aller.Hooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
*///?}

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void aller$frameStart(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        Frame.begin();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void aller$frameEnd(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        Frame.end();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void aller$hurtCam(CallbackInfo ci) {
        if (Hooks.cancelHurtCam()) ci.cancel();
    }

    //? if <26.1 {
    /*@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float aller$fov(float original, Camera camera, float partialTick, boolean useFovSetting) {
        // The hand is drawn with a fixed FOV (useFovSetting == false); leave it alone.
        return useFovSetting ? Hooks.fov(original) : original;
    }
    *///?}
}
