package dev.aller.mixin.pocket;

import dev.aller.Hooks;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Losing the pocket's connection must not take the player to the title screen; losing the server's must close the pocket first. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ListenerPocketMixin {
    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void pocket$lost(DisconnectionDetails details, CallbackInfo ci) {
        if (Hooks.pocketLost(this)) ci.cancel();
    }
}
