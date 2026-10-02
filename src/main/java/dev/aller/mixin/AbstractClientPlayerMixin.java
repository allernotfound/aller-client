package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aller.platform.Capes;
import dev.aller.platform.Skins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.1 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?} else {
import net.minecraft.world.entity.player.PlayerSkin;
//?}

/** The local player's own skin: one put on from the wardrobe this session, and the Aller cape. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin aller$cape(PlayerSkin original) {
        return (Object) this instanceof LocalPlayer ? Capes.apply(Skins.apply(original)) : original;
    }
}
