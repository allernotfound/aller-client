package dev.aller.mixin;

import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a button runs: the class of it says which mod put the button on a menu. */
@Mixin(Button.class)
public interface ButtonAccessor {
    @Accessor("onPress")
    Button.OnPress aller$onPress();
}
