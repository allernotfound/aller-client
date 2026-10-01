package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets chat mods decorate a message just before it is added to the chat window. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    //? if <26.1 {
    /*private static final String ADD = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";
    *///?} else {
    private static final String ADD = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";
    //?}

    @ModifyVariable(method = ADD, at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private Component aller$decorate(Component message) {
        return Hooks.chat(message);
    }
}
