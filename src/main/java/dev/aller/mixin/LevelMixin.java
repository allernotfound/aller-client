package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aller.Hooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Weather changer. Level is shared with the integrated server, so only client levels are touched. */
@Mixin(Level.class)
public abstract class LevelMixin {
    @ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
    private float aller$rain(float original) {
        return (Object) this instanceof ClientLevel ? Hooks.rain(original) : original;
    }

    @ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
    private float aller$thunder(float original) {
        return (Object) this instanceof ClientLevel ? Hooks.thunder(original) : original;
    }
}
