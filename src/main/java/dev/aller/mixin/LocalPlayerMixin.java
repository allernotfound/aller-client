package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item lock: the drop key does nothing while the selected slot is locked. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void aller$drop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.blockDrop()) cir.setReturnValue(false);
    }
}
