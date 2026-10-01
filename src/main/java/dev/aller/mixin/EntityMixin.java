package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: mouse movement turns the detached camera instead of the player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void aller$freelook(double dx, double dy, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && Hooks.freelookTurn(dx, dy)) ci.cancel();
    }
}
