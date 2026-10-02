package dev.aller.mixin.pocket;

import dev.aller.Hooks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The world that is not on screen leaves no particles in the one that is. */
@Mixin(ParticleEngine.class)
public abstract class ParticlePocketMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void pocket$create(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
                               CallbackInfoReturnable<Particle> cir) {
        if (Hooks.pocketAway()) cir.setReturnValue(null);
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"), cancellable = true)
    private void pocket$emitter(Entity entity, ParticleOptions options, int life, CallbackInfo ci) {
        if (Hooks.pocketAway()) ci.cancel();
    }
    //? if <26.1 {

    /*@Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void pocket$add(Particle particle, CallbackInfo ci) {
        if (Hooks.pocketAway()) ci.cancel();
    }
    *///?}
}
