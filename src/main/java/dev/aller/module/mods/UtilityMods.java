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
        public final Settings.Bool sneak = bool("sneak", "Also toggle sneak", false)
                .describe("Tap your sneak key once to stay crouched, again to stand");
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
                boolean physical = Mc.isDown(Mc.boundCode(options.keyShift));
                if (physical && !sneakKeyWas) sneakLatched = !sneakLatched;
                sneakKeyWas = physical;
                if (sneakLatched) options.keyShift.setDown(true);
            } else {
                sneakLatched = false;
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
        public enum View { BEHIND, FRONT }

        public final Settings.Choice<View> view = choice("view", "Camera", View.BEHIND);
        public final Settings.Bool invert = bool("invert_pitch", "Invert vertical", false);
        private float yaw, pitch;
        private CameraType restore;

        public Freelook() {
            super("freelook", "Freelook", "Hold to look around in third person without turning your character", Category.UTILITY);
            keywords("perspective", "360", "camera");
            restricted("Freelook is not allowed on some servers, including Hypixel.");
            holdKey(GLFW.GLFW_KEY_LEFT_ALT);
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
                options.setCameraType(view.get() == View.FRONT ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
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

    /** The web browser. Its key opens the window rather than switching the mod on and off; see {@code feature/Browser}. */
    public static final class WebBrowser extends Module {
        public final Settings.Choice<dev.aller.feature.Browser.Engine> engine = choice("engine", "Search engine", dev.aller.feature.Browser.Engine.GOOGLE)
                .describe("Where the address bar and the start page search");
        public final Settings.Bool chatLinks = bool("chat_links", "Open chat links here", true)
                .describe("Links clicked in chat open in this browser instead of your system one");
        public final Settings.Bool restore = bool("restore_tabs", "Reopen tabs from last time", true);
        public final Settings.Bool background = bool("background_audio", "Keep playing when closed", true)
                .describe("Music and videos carry on while you play. Off silences every page when the browser is shut");

        public WebBrowser() {
            super("browser", "Web browser", "Tabs, bookmarks and search in a panel over the game, using the system's own web engine", Category.UTILITY);
            keywords("internet", "web", "google", "wiki", "tabs", "bookmarks", "search", "incognito", "private");
            onByDefault();
            ownKey = true;
            keybind.chord();
            keybind.describe("Opens and closes the browser. With Ctrl or Alt in it, it works on menus too");
            // Not Ctrl+B: that is Minecraft's narrator key, which cannot be rebound.
            bind(Settings.Key.pack(GLFW.GLFW_KEY_B, GLFW.GLFW_MOD_ALT));
        }

        @Override
        public void load(com.google.gson.JsonObject o) {
            super.load(o);
            // Profiles saved while the default was still Ctrl+B move to the new one.
            if (keybind.get() == Settings.Key.pack(GLFW.GLFW_KEY_B, GLFW.GLFW_MOD_CONTROL)) keybind.reset();
        }
    }

    public static final class ReplayMod extends Module {
        public final Settings.Key saveKey = key("save_key", "Save clip", GLFW.GLFW_KEY_F8);
        public final Settings.Num seconds = num("seconds", "Clip length", 30f, 10f, 90f, 5f).suffix("s");
        public final Settings.Num fps = num("fps", "Frame rate", 20f, 10f, 30f, 5f).suffix(" fps");
        public final Settings.Num height = num("height", "Resolution", 480f, 360f, 1080f, 120f).suffix("p")
                .describe("The clip is at least this tall. Higher uses more memory and frame time");
        public final Settings.Num quality = num("quality", "Quality", 70f, 40f, 95f, 5f).suffix("%")
                .describe("JPEG quality of each frame. Higher means larger clips");
        private boolean saveWasDown;

        public ReplayMod() {
            super("replay", "Instant replay", "Keep the last moments of gameplay in memory and save them as a video with one key", Category.UTILITY);
            seconds.describe("How far back a saved clip reaches");
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
