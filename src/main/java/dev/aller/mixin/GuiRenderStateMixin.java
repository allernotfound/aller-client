package dev.aller.mixin;

import dev.aller.platform.VanillaText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
*///?} else {
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
//?}

/** Every line of GUI text passes through here on its way to be drawn; restyled menus get it in Inter. */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
    //? if <26.1 {
    /*@Inject(method = "submitText", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "addText", at = @At("HEAD"), cancellable = true)
    //?}
    private void aller$text(GuiTextRenderState text, CallbackInfo ci) {
        if (VanillaText.redraw((GuiRenderState) (Object) this, text)) ci.cancel();
    }
}
