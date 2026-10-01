package dev.aller.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aller.Hooks;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held item view: transforms the first-person hand and item. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    //? if <26.1 {
    /*private static final String ARM = "renderArmWithItem";
    *///?} else {
    private static final String ARM = "submitArmWithItem";
    //?}

    @Inject(method = ARM, at = @At("HEAD"))
    private void aller$viewmodel(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose, @Local(argsOnly = true) InteractionHand hand) {
        pose.pushPose();
        Hooks.viewmodel(pose, hand == InteractionHand.MAIN_HAND);
    }

    @Inject(method = ARM, at = @At("RETURN"))
    private void aller$viewmodelRestore(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        pose.popPose();
    }
}
