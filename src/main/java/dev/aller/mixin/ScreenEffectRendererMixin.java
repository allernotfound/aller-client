package dev.aller.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aller.Hooks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire: shifts the first-person fire overlay down. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    //? if <26.1 {
    /*private static final String FIRE = "renderFire";
    *///?} else {
    private static final String FIRE = "submitFire";
    //?}

    @Inject(method = FIRE, at = @At("HEAD"))
    private static void aller$fireDown(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        pose.pushPose();
        pose.translate(0f, -Hooks.fireOffset(), 0f);
    }

    @Inject(method = FIRE, at = @At("RETURN"))
    private static void aller$fireRestore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        pose.popPose();
    }
}
