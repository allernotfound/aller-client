package dev.aller.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;

@Mixin(PackSelectionScreen.class)
public interface PackSelectionScreenAccessor {
    @Accessor("packDir")
    Path aller$packDir();

    @Invoker("reload")
    void aller$reload();
}
