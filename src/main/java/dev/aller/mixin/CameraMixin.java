package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//?}

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow @Final private Quaternionf rotation;

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float aller$yaw(float yaw) {
        return Hooks.cameraYaw(yaw);
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float aller$pitch(float pitch) {
        return Hooks.cameraPitch(pitch);
    }

    /** The camera's orientation has just been built from yaw and pitch: the roll goes on top. */
    @Inject(method = "setRotation", at = @At("TAIL"))
    private void aller$roll(float yaw, float pitch, CallbackInfo ci) {
        float roll = Hooks.cameraRoll();
        if (roll != 0) rotation.rotateZ(roll);
    }

    //? if >=26.1 {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float aller$fov(float original) {
        return Hooks.fov(original);
    }
    //?}
}
