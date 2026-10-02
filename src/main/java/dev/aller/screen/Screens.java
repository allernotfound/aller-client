package dev.aller.screen;

import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Decides which vanilla screens are swapped for Aller ones. Called for every screen change. */
public final class Screens {
    private Screens() {}

    /** Set while a vanilla screen is being opened on purpose, so it is not swapped back. */
    public static boolean passThrough;

    public static Screen replace(Screen screen) {
        Screen next = choose(screen);
        // Before the screen lays itself out: a restyled menu has to measure its text in Inter from the start.
        MenuSkin.sync(next);
        return next;
    }

    private static Screen choose(Screen screen) {
        if (passThrough) return screen;
        var opt = AllerClient.options();
        if (opt.customPauseMenu.get() && screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
            return new ScreenHost(new PauseMenuScreen());
        }
        if (opt.customMainMenu.get()) {
            // Vanilla falls back to the title screen when asked to show "nothing" outside a world.
            boolean impliedTitle = screen == null && Mc.mc().level == null;
            if (impliedTitle || screen instanceof TitleScreen) {
                return new ScreenHost(new MainMenuScreen());
            }
        }
        return screen;
    }
}
