package dev.aller.platform;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** UI feedback sounds, routed through the game's own sound engine so they respect volume settings. */
public final class Sounds {
    private Sounds() {}

    public static void click() {
        play(1.0f, 0.22f);
    }

    /** Higher pitch when turning something on, lower when turning it off. */
    public static void toggle(boolean on) {
        play(on ? 1.5f : 1.1f, 0.18f);
    }

    private static void play(float pitch, float volume) {
        Mc.mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }
}
