package dev.aller.module.mods;

import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;

/** Quality-of-life mods. */
public final class UtilityMods {
    private UtilityMods() {}

    public static final class ToggleSprint extends Module {
        public final Settings.Bool sneak = bool("sneak", "Also toggle sneak", false);
        private boolean sneakLatched, sneakKeyWas;

        public ToggleSprint() {
            super("toggle_sprint", "Toggle sprint", "Always sprint without holding the key", Category.UTILITY);
            keywords("auto sprint", "sneak", "crouch");
        }

        @Override
        public void tick() {
            var options = Mc.mc().options;
            if (Mc.screen() != null) return;
            // Hold the sprint key down for the player; vanilla still decides whether sprinting is possible.
            options.keySprint.setDown(true);
            if (sneak.get()) {
                boolean physical = Mc.isDown(GLFW.GLFW_KEY_LEFT_SHIFT);
                if (physical && !sneakKeyWas) sneakLatched = !sneakLatched;
                sneakKeyWas = physical;
                if (sneakLatched) options.keyShift.setDown(true);
            }
        }

        @Override
        protected void onDisable() {
            sneakLatched = false;
            var options = Mc.mc().options;
            if (options != null) {
                options.keySprint.setDown(false);
                options.keyShift.setDown(false);
            }
        }

        public boolean sneaking() {
            return enabled() && sneakLatched;
        }
    }

    public static final class Freelook extends Module {
        public final Settings.Bool invert = bool("invert_pitch", "Invert vertical", false);
        private float yaw, pitch;
        private CameraType restore;

        public Freelook() {
            super("freelook", "Freelook", "Hold to look around in third person without turning your character", Category.UTILITY);
            keywords("perspective", "360", "camera");
            restricted("Freelook is not allowed on some servers, including Hypixel.");
            keybind.set(GLFW.GLFW_KEY_LEFT_ALT);
        }

        @Override
        public boolean holdToActivate() {
            return true;
        }

        public boolean active() {
            return held() && Game.player() != null;
        }

        @Override
        protected void onHeldChanged(boolean down) {
            var options = Mc.mc().options;
            var player = Game.player();
            if (player == null) return;
            if (down) {
                yaw = player.getYRot();
                pitch = player.getXRot();
                restore = options.getCameraType();
                options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else if (restore != null) {
                options.setCameraType(restore);
                restore = null;
            }
        }

        /** Same scaling vanilla applies to mouse deltas when turning an entity. */
        public boolean turn(double dx, double dy) {
            if (!active()) return false;
            yaw += (float) dx * 0.15f;
            pitch = Math.clamp(pitch + (float) dy * 0.15f * (invert.get() ? -1 : 1), -90f, 90f);
            return true;
        }

        public float yaw() {
            return yaw;
        }

        public float pitch() {
            return pitch;
        }
    }

    public static final class ReplayMod extends Module {
        public final Settings.Key saveKey = key("save_key", "Save clip", GLFW.GLFW_KEY_F8);
        public final Settings.Num seconds = num("seconds", "Clip length", 30f, 10f, 90f, 5f).suffix("s");
        public final Settings.Num fps = num("fps", "Frame rate", 20f, 10f, 30f, 5f).suffix(" fps");
        public final Settings.Num height = num("height", "Minimum height", 480f, 360f, 1080f, 120f).suffix("p");
        public final Settings.Num quality = num("quality", "Quality", 70f, 40f, 95f, 5f).suffix("%");
        private boolean saveWasDown;

        public ReplayMod() {
            super("replay", "Instant replay", "Keep the last moments of gameplay in memory and save them as a video with one key", Category.UTILITY);
            keywords("clip", "record", "video", "highlight", "capture");
        }

        @Override
        public void tick() {
            boolean down = Mc.screen() == null && Mc.isDown(saveKey.get());
            if (down && !saveWasDown) dev.aller.feature.Replay.save();
            saveWasDown = down;
        }

        @Override
        protected void onDisable() {
            dev.aller.feature.Replay.clear();
        }
    }
}
