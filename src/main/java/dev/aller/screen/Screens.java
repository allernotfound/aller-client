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
        Entrance.opened(Mc.screen(), next);
        // Before the screen lays itself out: a restyled menu has to measure its text in Inter from the start.
        MenuSkin.sync(next);
        return next;
    }

    private static Screen choose(Screen screen) {
        if (passThrough) return screen;
        var opt = AllerClient.options();
        // Vanilla falls back to the title screen when asked to show "nothing" outside a world.
        boolean title = screen instanceof TitleScreen || screen == null && Mc.mc().level == null;
        if (title && OnboardingScreen.due()) return new ScreenHost(new OnboardingScreen(null));
        if (opt.customPauseMenu.get() && screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
            return new ScreenHost(new PauseMenuScreen());
        }
        if (opt.customMainMenu.get()) {
            if (title) return new ScreenHost(new MainMenuScreen());
        }
        return screen;
    }
}
