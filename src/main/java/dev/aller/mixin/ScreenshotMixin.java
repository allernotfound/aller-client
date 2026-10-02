package dev.aller.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.aller.Hooks;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

/** Every screenshot saved to the folder ends in this one method, whoever asked for it: Aller notices the file there. */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    private static final String GRAB = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V";

    @Inject(method = GRAB, at = @At("HEAD"), cancellable = true)
    private static void aller$hold(File workDir, String name, RenderTarget target, int factor, Consumer<Component> callback, CallbackInfo ci) {
        if (Hooks.screenshotHeld(workDir, name, target, factor, callback)) ci.cancel();
    }

    @ModifyVariable(method = GRAB, at = @At("HEAD"), argsOnly = true)
    private static Consumer<Component> aller$report(Consumer<Component> callback) {
        return Hooks.screenshot(callback);
    }
}
