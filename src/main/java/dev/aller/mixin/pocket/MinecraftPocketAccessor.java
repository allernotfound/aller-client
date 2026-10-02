package dev.aller.mixin.pocket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The parts of Minecraft's one-world state that have no setter, for {@code platform/Worlds}. */
@Mixin(Minecraft.class)
public interface MinecraftPocketAccessor {
    @Accessor("singleplayerServer")
    void allerServer(IntegratedServer server);

    @Accessor("isLocalServer")
    void allerLocal(boolean local);

    //? if <26.1 {
    /*@Invoker("updateLevelInEngines")
    void allerEngines(ClientLevel level);

    @Accessor("authenticationService")
    com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService allerAuth();
    *///?} else {
    @Invoker("updateLevelInEngines")
    void allerEngines(ClientLevel level, boolean stopSound);

    @Accessor("services")
    net.minecraft.server.Services allerServices();
    //?}
}
