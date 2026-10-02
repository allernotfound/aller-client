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
}
