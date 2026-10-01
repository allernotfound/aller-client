package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Block outline: recolours the selection box. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    //? if <26.1 {
    /*private static final String OUTLINE = "renderHitOutline";
    *///?} else {
    private static final String OUTLINE = "submitHitOutline";
    //?}

    @ModifyVariable(method = OUTLINE, at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int aller$outlineColor(int color) {
        return Hooks.blockOutlineColor(color);
    }
}
