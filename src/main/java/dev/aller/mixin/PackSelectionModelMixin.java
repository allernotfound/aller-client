package dev.aller.mixin;

import dev.aller.Hooks;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Lets the store arrange the pack list's two columns each time it reads the folder. */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {
    @Shadow @Final private PackRepository repository;
    @Shadow @Final private List<Pack> selected;
    @Shadow @Final private List<Pack> unselected;

    @Inject(method = "findNewPacks", at = @At("HEAD"))
    private void aller$reading(CallbackInfo ci) {
        Hooks.packsReading(repository, selected);
    }

    @Inject(method = "findNewPacks", at = @At("TAIL"))
    private void aller$read(CallbackInfo ci) {
        Hooks.packsRead(repository, selected, unselected);
    }
}
