package dev.aller.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if <26.1 {
/*import net.minecraft.client.GuiMessage;
*///?} else {
import net.minecraft.client.multiplayer.chat.GuiMessage;
//?}

import java.util.List;

/** The chat window's message and line lists, newest first, and its scroll position. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> aller$messages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> aller$lines();

    @Accessor("chatScrollbarPos")
    int aller$scroll();
}
