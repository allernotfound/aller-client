package dev.aller.mixin;

import dev.aller.platform.VanillaText;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Lets restyled menus measure text in Inter, so vanilla's centring and wrapping fit what is drawn. */
@Mixin(Font.class)
public abstract class FontMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/StringSplitter;<init>(Lnet/minecraft/client/StringSplitter$WidthProvider;)V"))
    private StringSplitter.WidthProvider aller$metrics(StringSplitter.WidthProvider vanilla) {
        return (codepoint, style) -> {
            float width = VanillaText.advance(codepoint, style);
            return width >= 0 ? width : vanilla.getWidth(codepoint, style);
        };
    }
}
