package dev.aller.mixin;

import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProgressScreen.class)
public interface ProgressScreenAccessor {
    @Accessor("header")
    Component aller$header();

    @Accessor("stage")
    Component aller$stage();

    @Accessor("progress")
    int aller$progress();
}
