package dev.aller.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aller.Hooks;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/** Fog and sky: the fog's distances and colour, where they are written for the shaders. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    private static final String WRITE = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

    @Inject(method = WRITE, at = @At("HEAD"))
    private void aller$fogColor(ByteBuffer buffer, int offset, Vector4f color, float start, float end, float renderStart,
                                float renderEnd, float skyEnd, float cloudEnd, CallbackInfo ci) {
        Hooks.fogColor(color, renderEnd);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float aller$fogStart(float start, @Local(ordinal = 3, argsOnly = true) float renderEnd) {
        return Hooks.fog(0, start, renderEnd);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float aller$fogEnd(float end, @Local(ordinal = 3, argsOnly = true) float renderEnd) {
        return Hooks.fog(1, end, renderEnd);
    }

    @ModifyVariable(method = WRITE, at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float aller$fogRenderStart(float renderStart, @Local(ordinal = 3, argsOnly = true) float renderEnd) {
        return Hooks.fog(2, renderStart, renderEnd);
    }
}
