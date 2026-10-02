package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aller.Hooks;
import dev.aller.feature.Chat;
import dev.aller.platform.Canvas;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
//?}

/** Hands chat mods each message before it is shown, and brackets the chat window's drawing. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    //? if <26.1 {
    /*private static final String ADD = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";
    private static final String DRAW = "render";
    private static final String LAYOUT = "render";

    @WrapMethod(method = ADD)
    private void aller$message(Component message, MessageSignature signature, GuiMessageTag tag, Operation<Void> original) {
        Chat.Incoming in = Hooks.chat(message);
        if (in != null) original.call(in.text(), signature, aller$tag(in, tag));
    }

    @Inject(method = DRAW, at = @At("HEAD"))
    private void aller$drawBegin(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        Hooks.chatBegin(Canvas.of(graphics));
    }

    @Inject(method = DRAW, at = @At("RETURN"))
    private void aller$drawEnd(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        Hooks.chatEnd(Canvas.of(graphics));
    }
    *///?} else {
    private static final String ADD = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";
    private static final String DRAW = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";
    private static final String LAYOUT = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";

    @WrapMethod(method = ADD)
    private void aller$message(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, Operation<Void> original) {
        Chat.Incoming in = Hooks.chat(message);
        if (in != null) original.call(in.text(), signature, source, aller$tag(in, tag));
    }

    @Inject(method = DRAW, at = @At("HEAD"))
    private void aller$drawBegin(CallbackInfo ci, @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        Hooks.chatBegin(Canvas.of(graphics));
    }

    @Inject(method = DRAW, at = @At("RETURN"))
    private void aller$drawEnd(CallbackInfo ci, @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        Hooks.chatEnd(Canvas.of(graphics));
    }
    //?}

    @Unique
    private static GuiMessageTag aller$tag(Chat.Incoming in, GuiMessageTag tag) {
        if (in.bar() != 0) return new GuiMessageTag(in.bar(), null, null, in.label());
        return in.noIndicator() ? null : tag;
    }

    /** The 100-message caps on the window and on the sent-message history. */
    @ModifyExpressionValue(method = {"addMessageToDisplayQueue", "addMessageToQueue", "addRecentChat"},
            at = @At(value = "CONSTANT", args = "intValue=100"))
    private int aller$limit(int original) {
        return Hooks.chatLimit(original);
    }

    /** The second option read while laying the window out is the background opacity. */
    @ModifyExpressionValue(method = LAYOUT, at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float aller$background(float original) {
        return Hooks.chatBackground(original);
    }
}
