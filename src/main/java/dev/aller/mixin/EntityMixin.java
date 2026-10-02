package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: mouse movement turns the detached camera instead of the player. Zoom: it turns the player more slowly. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double aller$turnX(double dx) {
        return (Object) this instanceof LocalPlayer ? dx * Hooks.turnScale() : dx;
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double aller$turnY(double dy) {
        return (Object) this instanceof LocalPlayer ? dy * Hooks.turnScale() : dy;
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void aller$freelook(double dx, double dy, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && Hooks.freelookTurn(dx, dy)) ci.cancel();
    }
}
