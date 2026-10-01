package dev.aller;

import dev.aller.feature.View;
import dev.aller.module.Modules;
import dev.aller.module.mods.ChatMods;
import net.minecraft.network.chat.Component;

/**
 * The single surface mixins call into. Keeping every injected call here means the mixin classes
 * stay trivial (and therefore easy to port), while the behaviour lives in ordinary module code.
 */
public final class Hooks {
    private Hooks() {}

    /** World field of view in degrees, after vanilla's own modifiers. */
    public static float fov(float original) {
        float fov = Modules.ZOOM.apply(original);
        View.fov = fov;
        return fov;
    }

    public static float gamma(float original) {
        return Modules.FULLBRIGHT.enabled() ? Modules.FULLBRIGHT.gamma(original) : original;
    }

    public static boolean cancelHurtCam() {
        return Modules.NO_HURT_CAM.enabled();
    }

    /** How far to push the first-person fire overlay down, in overlay units. */
    public static float fireOffset() {
        return Modules.LOW_FIRE.enabled() ? Modules.LOW_FIRE.offset.get() : 0f;
    }

    public static long dayTime(long original) {
        return Modules.TIME_CHANGER.enabled() ? Modules.TIME_CHANGER.time() : original;
    }

    public static float rain(float original) {
        return Modules.WEATHER_CHANGER.enabled() ? Modules.WEATHER_CHANGER.rain() : original;
    }

    public static float thunder(float original) {
        return Modules.WEATHER_CHANGER.enabled() ? Modules.WEATHER_CHANGER.thunder() : original;
    }

    public static boolean cancelCrosshair() {
        return Modules.CROSSHAIR.enabled();
    }

    public static boolean hideScoreboard() {
        return Modules.SCOREBOARD.enabled() && Modules.SCOREBOARD.hide.get();
    }

    public static boolean replaceTabList() {
        return Modules.PLAYER_LIST.enabled();
    }

    public static Component chat(Component message) {
        return ChatMods.decorate(message);
    }

    /** @return true if the mouse movement was consumed by freelook and must not turn the player */
    public static boolean freelookTurn(double dx, double dy) {
        return Modules.FREELOOK.turn(dx, dy);
    }

    public static float cameraYaw(float original) {
        return Modules.FREELOOK.active() ? Modules.FREELOOK.yaw() : original;
    }

    public static float cameraPitch(float original) {
        return Modules.FREELOOK.active() ? Modules.FREELOOK.pitch() : original;
    }

    /** @return true if the scroll was consumed (zoom adjustment) and must not change the hotbar slot */
    public static boolean scroll(double amount) {
        return Modules.ZOOM.scroll(amount);
    }

    public static int blockOutlineColor(int original) {
        // Leave the black secondary pass of the high-contrast outline alone.
        if (!Modules.BLOCK_OUTLINE.enabled() || original == 0xFF000000) return original;
        return Modules.BLOCK_OUTLINE.color.get();
    }
}
