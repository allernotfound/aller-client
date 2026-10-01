package dev.aller.mixin;

import dev.aller.screen.Screens;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//? if <26.1 {
/*@Mixin(net.minecraft.client.Minecraft.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
public abstract class SetScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen aller$replaceScreen(Screen screen) {
        return Screens.replace(screen);
    }
}
