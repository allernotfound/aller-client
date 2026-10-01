package dev.aller.platform;

import dev.aller.mixin.ConnectScreenAccessor;
import dev.aller.mixin.LevelLoadingScreenAccessor;
import dev.aller.mixin.ProgressScreenAccessor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What a vanilla "please wait" screen is currently saying, read out so Aller can redraw it in its
 * own style.
 *
 * @param progress 0..1, or negative when the wait has no measurable progress
 */
public record LoadingInfo(String title, String detail, float progress) {
    /** @return null if the screen is not one of the loading screens Aller restyles */
    public static LoadingInfo of(Screen screen) {
        if (screen instanceof ConnectScreen) {
            return new LoadingInfo("Connecting", text(((ConnectScreenAccessor) screen).aller$status()), -1);
        }
        if (screen instanceof LevelLoadingScreen) {
            var tracker = ((LevelLoadingScreenAccessor) screen).aller$progress();
            //? if <26.1 {
            /*return new LoadingInfo("Loading world", "Preparing terrain", Math.clamp(tracker.getProgress() / 100f, 0f, 1f));
            *///?} else {
            return new LoadingInfo("Loading world", "Preparing terrain", tracker.hasProgress() ? Math.clamp(tracker.serverProgress(), 0f, 1f) : -1);
            //?}
        }
        //? if <26.1 {
        /*if (screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) {
            return new LoadingInfo("Joining world", "Loading terrain", -1);
        }
        *///?}
        if (screen instanceof GenericMessageScreen) {
            return new LoadingInfo(text(screen.getTitle()), "", -1);
        }
        if (screen instanceof ProgressScreen) {
            ProgressScreenAccessor p = (ProgressScreenAccessor) screen;
            String header = text(p.aller$header()), stage = text(p.aller$stage());
            return new LoadingInfo(header.isEmpty() ? "Working" : header, stage, stage.isEmpty() || p.aller$progress() <= 0 ? -1 : p.aller$progress() / 100f);
        }
        return null;
    }

    private static String text(Component c) {
        if (c == null) return "";
        // Vanilla status lines end in an ellipsis; the animated bar already says "in progress".
        return c.getString().replaceAll("\\.{3}$", "").replace("…", "").trim();
    }
}
