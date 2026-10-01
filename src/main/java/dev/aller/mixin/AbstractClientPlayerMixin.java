package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aller.platform.Capes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.1 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?} else {
import net.minecraft.world.entity.player.PlayerSkin;
//?}

/** Aller cape: swaps the cape texture on the local player's own skin. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin aller$cape(PlayerSkin original) {
        return (Object) this instanceof LocalPlayer ? Capes.apply(original) : original;
    }
}
