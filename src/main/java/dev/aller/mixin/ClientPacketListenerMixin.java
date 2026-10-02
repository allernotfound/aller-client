package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells chat mods what the player sends, so the server's echo of it is not mistaken for a mention. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"))
    private void aller$chat(String message, CallbackInfo ci) {
        Hooks.chatSent(message, false);
    }

    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void aller$command(String command, CallbackInfo ci) {
        Hooks.chatSent(command, true);
    }
}
