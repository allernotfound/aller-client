package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aller.Hooks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Own nametag: vanilla never shows a name over the entity the camera belongs to. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
    private boolean aller$ownName(boolean original, LivingEntity entity, double distanceSq) {
        return original || Hooks.showOwnName(entity);
    }
}
