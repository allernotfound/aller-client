package dev.aller.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aller.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.1 {
/*import net.minecraft.client.multiplayer.ClientLevel;
*///?} else {
import net.minecraft.client.ClientClockManager;
//?}

/** Time changer: only the client's view of the clock is altered. */
//? if <26.1 {
/*@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientTimeMixin {
    @ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
    private long aller$time(long original) {
        return Hooks.dayTime(original);
    }
}
*///?} else {
@Mixin(ClientClockManager.class)
public abstract class ClientTimeMixin {
    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    private long aller$time(long original) {
        return Hooks.dayTime(original);
    }
}
//?}
