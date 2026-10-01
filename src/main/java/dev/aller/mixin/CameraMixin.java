package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//? if >=26.1 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//?}

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float aller$yaw(float yaw) {
        return Hooks.cameraYaw(yaw);
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float aller$pitch(float pitch) {
        return Hooks.cameraPitch(pitch);
    }

    //? if >=26.1 {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float aller$fov(float original) {
        return Hooks.fov(original);
    }
    //?}
}
