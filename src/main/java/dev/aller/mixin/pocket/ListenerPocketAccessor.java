package dev.aller.mixin.pocket;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which connection a packet's listener belongs to. */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ListenerPocketAccessor {
    @Accessor("connection")
    Connection allerConnection();
}
