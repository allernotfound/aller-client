package dev.aller.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if <26.1 {
/*import net.minecraft.client.gui.render.state.GuiTextRenderState;
*///?} else {
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
//?}

@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccessor {
    @Accessor("font")
    Font aller$font();

    @Accessor("text")
    FormattedCharSequence aller$text();

    @Accessor("x")
    int aller$x();

    @Accessor("y")
    int aller$y();

    @Accessor("color")
    int aller$color();

    @Accessor("backgroundColor")
    int aller$backgroundColor();

    @Accessor("dropShadow")
    boolean aller$dropShadow();

    //? if >=26.1 {
    @Accessor("includeEmpty")
    boolean aller$includeEmpty();
    //?}
}
