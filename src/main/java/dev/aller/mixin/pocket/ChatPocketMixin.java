package dev.aller.mixin.pocket;

import dev.aller.Hooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What the player types in the pocket goes to the server they are still connected to. */
@Mixin(ClientPacketListener.class)
public abstract class ChatPocketMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void pocket$chat(String message, CallbackInfo ci) {
        if (Hooks.pocketChat(this, message, false)) ci.cancel();
    }

    // A command run by clicking chat; the chat is the server's.
    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void pocket$clicked(String command, net.minecraft.client.gui.screens.Screen after, CallbackInfo ci) {
        if (Hooks.pocketChat(this, command, true)) ci.cancel();
    }

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void pocket$command(String command, CallbackInfo ci) {
        if (Hooks.pocketChat(this, command, true)) ci.cancel();
    }
}
