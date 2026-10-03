package dev.aller.feature;

import com.google.gson.JsonObject;
import dev.aller.module.Modules;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.screen.PhotoScreen;
import net.minecraft.client.CameraType;

/**
 * Photo mode's camera. While its screen is open the view is turned, rolled and zoomed here instead
 * of by the player: it looks around from where the player's own camera already is (their eyes, or
 * the third-person spot behind or in front of them), exactly as freelook does, and never leaves it.
 */
public final class Photo {
    public enum View { FIRST_PERSON, BEHIND, IN_FRONT }

    private static boolean active;
    private static float yaw, pitch, roll, fov = 70f;
    private static View view = View.BEHIND;
    private static CameraType cameraBefore;
    private static boolean hidHud, keyWasDown;
    private static JsonObject focusBefore, gradingBefore;
    /** A picture is wanted: the screen leaves itself out of the next frame, and that frame is the one saved. */
    private static boolean pending, cleanFrame;
    private static int quiet;

    private Photo() {}

    public static boolean active() {
        return active;
    }

    /** Once a frame: the mod's key opens the screen from the world. */
    public static void poll() {
        var mod = Modules.PHOTO_MODE;
        boolean down = mod.enabled() && Game.inWorld() && Mc.screen() == null && !dev.aller.ui.ShotCard.claiming() && Mc.isDown(mod.keybind.get());
        if (down && !keyWasDown) open();
        keyWasDown = down;
    }

    public static void open() {
        if (active || !Game.inWorld()) return;
        Mc.setScreen(new ScreenHost(new PhotoScreen()));
    }

    /** The screen has opened: the camera starts where the player is looking. */
    public static void begin() {
        var p = Game.player();
        var options = Mc.mc().options;
        yaw = p.getYRot();
        pitch = p.getXRot();
        roll = 0;
        fov = options.fov().get();
        cameraBefore = options.getCameraType();
        view = cameraBefore == CameraType.FIRST_PERSON ? View.FIRST_PERSON : cameraBefore == CameraType.THIRD_PERSON_FRONT ? View.IN_FRONT : View.BEHIND;
        hidHud = !Mc.hudHidden();
        if (hidHud) Mc.toggleHud();
        // What the sliders change is put back on the way out: a picture's look is not the look to play in.
        focusBefore = Modules.DEPTH_OF_FIELD.save();
        gradingBefore = Modules.COLOUR_GRADING.save();
        active = true;
    }

    public static void end() {
        if (!active) return;
        active = false;
        pending = false;
        var options = Mc.mc().options;
        if (cameraBefore != null && options != null) options.setCameraType(cameraBefore);
        if (hidHud && Mc.hudHidden()) Mc.toggleHud();
        if (focusBefore != null) Modules.DEPTH_OF_FIELD.load(focusBefore);
        if (gradingBefore != null) Modules.COLOUR_GRADING.load(gradingBefore);
    }

    public static View view() {
        return view;
    }

    public static void view(View v) {
        view = v;
        Mc.mc().options.setCameraType(switch (v) {
            case FIRST_PERSON -> CameraType.FIRST_PERSON;
            case BEHIND -> CameraType.THIRD_PERSON_BACK;
            case IN_FRONT -> CameraType.THIRD_PERSON_FRONT;
        });
    }

    /** Turns the camera by a drag across the screen, in degrees. */
    public static void turn(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch = Math.clamp(pitch + dPitch, -90f, 90f);
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static float roll() {
        return roll;
    }

    public static void roll(float degrees) {
        roll = degrees;
    }

    public static float fov() {
        return fov;
    }

    public static void fov(float degrees) {
        fov = Math.clamp(degrees, 10f, 120f);
    }

    /** How far away the picture's subject is, for the depth of field: the player in third person, otherwise what they look at. */
    public static float focus(float manual) {
        if (manual > 0) return manual;
        if (view == View.FIRST_PERSON) return Game.lookDistance(96f);
        return (float) dev.aller.feature.View.camera().position().distanceTo(Game.player().getEyePosition());
    }

    // ---- taking the picture --------------------------------------------------------------------

    public static void shoot() {
        if (active) pending = true;
    }

    /** Whether the screen must leave itself out of the frame being drawn. */
    public static boolean hiding() {
        if (pending) cleanFrame = true;
        return pending || quiet > 0;
    }

    /** The frame is finished: if it was drawn clean for a picture, this is when it is saved. */
    public static void frameEnd() {
        if (quiet > 0) quiet--;
        if (!pending || !cleanFrame) return;
        pending = false;
        cleanFrame = false;
        // A few more clean frames: a screenshot that waits for the card to go is taken a frame late.
        quiet = 3;
        Nav.screenshot(message -> {});
    }
}
