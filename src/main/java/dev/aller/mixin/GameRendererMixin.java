package dev.aller.mixin;

import dev.aller.Frame;
import dev.aller.Hooks;
import dev.aller.platform.Pipelines;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void aller$worldDepth(DeltaTracker delta, CallbackInfo ci) {
        Hooks.worldDepth();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void aller$worldDrawn(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        Hooks.worldDrawn();
    }

    @Inject(method = "preloadUiShader", at = @At("TAIL"))
    private void aller$preloadShaders(CallbackInfo ci) {
        Pipelines.preload();
    }

    //? if <26.1 {
    /*@ModifyExpressionValue(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I"))
    *///?} else {
    @ModifyExpressionValue(method = "extractOptions",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I"))
    //?}
    private int aller$blurRadius(int original) {
        return Hooks.blurRadius(original);
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
