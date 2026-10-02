package dev.aller.mixin.pocket;

import dev.aller.Hooks;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs each packet with its own connection's world in place; see {@code platform/Worlds}. Plain
 * injects at both ends of the method that handles one packet (it catches what the handler throws,
 * so the second always runs). Not a redirect or a MixinExtras wrap: a redirect here made
 * MixinExtras fail the whole class in a real launcher, and wraps go in after {@code PocketPlugin}
 * has looked.
 */
//? if <26.1 {
/*@Mixin(net.minecraft.network.protocol.PacketUtils.class)
public abstract class PacketPocketMixin {
    // The task ensureRunningOnSameThread hands to the game thread.
    @Inject(method = "method_11072", at = @At("HEAD"))
    private static void pocket$before(PacketListener listener, Packet<?> packet, CallbackInfo ci) {
        Hooks.pocketPacket(listener, packet);
    }

    @Inject(method = "method_11072", at = @At("RETURN"))
    private static void pocket$after(PacketListener listener, Packet<?> packet, CallbackInfo ci) {
        Hooks.pocketPacketDone();
    }
}
*///?} else {
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketPocketMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private PacketListener listener;
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private Packet<?> packet;

    @Inject(method = "handle", at = @At("HEAD"))
    private void pocket$before(CallbackInfo ci) {
        Hooks.pocketPacket(listener, packet);
    }

    @Inject(method = "handle", at = @At("RETURN"))
    private void pocket$after(CallbackInfo ci) {
        Hooks.pocketPacketDone();
    }
}
//?}
