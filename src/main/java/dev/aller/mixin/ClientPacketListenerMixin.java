package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells chat mods what the player sends, so the server's echo of it is not mistaken for a mention; and times the server's ticks. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"))
    private void aller$chat(String message, CallbackInfo ci) {
        Hooks.chatSent(message, false);
    }

    /** At the tail: the handler first runs off the client thread and leaves at once. */
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void aller$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
        Hooks.serverTime(packet.gameTime());
    }

    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void aller$command(String command, CallbackInfo ci) {
        Hooks.chatSent(command, true);
    }
}
