package dev.aller.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if <26.1 {
/*import net.minecraft.server.level.progress.StoringChunkProgressListener;
*///?} else {
import net.minecraft.client.multiplayer.LevelLoadTracker;
//?}

@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
    //? if <26.1 {
    /*@Accessor("progressListener")
    StoringChunkProgressListener aller$progress();
    *///?} else {
    @Accessor("loadTracker")
    LevelLoadTracker aller$progress();
    //?}
}
