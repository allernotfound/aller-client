package dev.aller.module.mods;

import dev.aller.feature.Waypoints;
import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import dev.aller.ui.Toasts;
import org.lwjgl.glfw.GLFW;

public final class WorldMods {
    private WorldMods() {}

    public static final class WaypointsMod extends Module {
        public final Settings.Key addKey = key("add_key", "Quick-add waypoint", GLFW.GLFW_KEY_B);
        public final Settings.Bool deathMarkers = bool("death_markers", "Mark where you die", true);
        public final Settings.Num deathsKept = num("deaths_kept", "Death markers kept", 3f, 1f, 10f, 1f)
                .visibleWhen(() -> this.deathMarkers.get());
        public final Settings.Bool edgeMarkers = bool("edge_markers", "Show off-screen markers at the edge", true);
        public final Settings.Bool alwaysLabel = bool("always_label", "Always show names", false)
                .describe("Otherwise a name appears when you look towards its marker");
        public final Settings.Bool showDistance = bool("show_distance", "Show distance in names", true);
        public final Settings.Num maxDistance = num("max_distance", "Hide beyond", 0f, 0f, 5000f, 100f)
                .format(v -> v <= 0 ? "Never" : Math.round(v) + "m")
                .describe("Death markers always stay visible");
        public final Settings.Num markerScale = num("marker_scale", "Marker size", 1f, 0.6f, 2f, 0.1f).suffix("x");
        private boolean addWasDown;
        private int counter;

        public WaypointsMod() {
            super("waypoints", "Waypoints", "Save places and see markers pointing back to them, including where you died", Category.WORLD);
            keywords("markers", "death", "beacon", "location", "home");
            restricted("Waypoint markers are treated like a minimap on some servers.");
            onByDefault();
        }

        @Override
        public void tick() {
            boolean down = Mc.screen() == null && Mc.isDown(addKey.get());
            if (down && !addWasDown) {
                int n = Waypoints.all().size();
                var w = Waypoints.addHere("Waypoint " + (n + 1), Waypoints.PALETTE[counter++ % Waypoints.PALETTE.length]);
                Toasts.info("Waypoint added", w.name + " at " + Waypoints.coords(w));
            }
            addWasDown = down;
        }
    }

    /** A private room to step into without leaving the server. Its key goes in and out; see {@code feature/Pocket}. */
    public static final class PocketDimension extends Module {
        public enum Mode { CREATIVE, SURVIVAL }

        public final Settings.Choice<Mode> mode = choice("mode", "Game mode", Mode.CREATIVE)
                .describe("Set each time you step in. Items never cross between the server and the pocket");
        public final Settings.Bool bright = bool("bright", "Light the room", true)
                .describe("The room is black and has no lamps. Off leaves it as dark as you build it");
        public final Settings.Text prefix = text("prefix", "Pocket command prefix", "\\", 3)
                .describe("Chat and /commands still go to the server. Start a line with this to run a command in the pocket");

        public PocketDimension() {
            super("pocket", "Pocket dimension", "Step into a private room that keeps what you build, while you stay connected to the server", Category.WORLD);
            keywords("room", "base", "private", "storage", "chest", "void", "afk");
            restricted("While you are in the pocket the server sees you standing still, which some servers treat as being AFK.");
            experimental("It runs a second world beside the server's. If something goes wrong it puts you back and switches itself off.");
            ownKey = true;
            keybind.describe("Steps into the pocket, and back out from anywhere inside it");
            bind(GLFW.GLFW_KEY_O);
        }

        @Override
        protected void onDisable() {
            dev.aller.feature.Pocket.close(false);
        }
    }
}
