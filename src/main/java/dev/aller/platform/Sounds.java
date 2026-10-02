package dev.aller.platform;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
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

    /** Notification sounds a mod can let the player choose between. */
    public enum Alert { DING, PLING, BELL, CHIME, LEVEL_UP, NONE }

    /** Plays an alert at its own volume (0 to 1), not scaled by the interface sound setting. */
    public static void alert(Alert alert, float pitch, float volume) {
        var event = switch (alert) {
            case DING -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
            case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
            case CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case NONE -> null;
        };
        if (event == null || volume <= 0) return;
        Mc.mc().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    /**
     * One of Minecraft's own sounds by id ("block.beacon.activate"), for set pieces built from several
     * of them. Scaled by the interface sound setting.
     */
    public static void cue(String id, float pitch, float volume) {
        volume *= dev.aller.AllerClient.options().uiVolume.get() / 100f;
        if (volume <= 0) return;
        Mc.mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Ids.vanilla(id)), pitch, volume));
    }

    private static void play(float pitch, float volume) {
        volume *= dev.aller.AllerClient.options().uiVolume.get() / 100f;
        if (volume <= 0) return;
        Mc.mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }
}
