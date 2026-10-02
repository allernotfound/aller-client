package dev.aller.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.aller.AllerClient;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.font.Fonts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved positions, stored per world or server. Markers are projected onto the HUD rather than
 * rendered into the world, so they work identically under any renderer or shader pack.
 */
public final class Waypoints {
    public static final int[] PALETTE = {0xFF8B5CF6, 0xFF38BDF8, 0xFF34D399, 0xFFFB7185, 0xFFF59E0B, 0xFFE2E8F0};

    public enum Shape { CIRCLE, DIAMOND, TRIANGLE, SQUARE, HEXAGON, STAR }

    public static final class Waypoint {
        /** Marker shape, stored by name so unknown values from newer versions fall back to a circle. */
        public String icon = "CIRCLE";

        public Shape shape() {
            try {
                return icon == null ? Shape.CIRCLE : Shape.valueOf(icon);
            } catch (IllegalArgumentException e) {
                return Shape.CIRCLE;
            }
        }

        public String name = "Waypoint";
        public double x, y, z;
        public String dimension = "overworld";
        public int color = PALETTE[0];
        public boolean death;
        public boolean visible = true;
        public long created = System.currentTimeMillis();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Waypoint> list = new ArrayList<>();
    private static String loadedKey;

    private Waypoints() {}

    /** All waypoints for the world the player is currently in. */
    public static List<Waypoint> all() {
        String key = Game.worldKey();
        if (!key.equals(loadedKey)) {
            loadedKey = key;
            list = new ArrayList<>();
            Path file = file(key);
            if (Files.exists(file)) {
                try {
                    List<Waypoint> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                            new TypeToken<List<Waypoint>>() {}.getType());
                    if (loaded != null) list.addAll(loaded);
                } catch (Exception e) {
                    AllerClient.LOG.warn("Could not read waypoints for {}", key, e);
                }
            }
        }
        return list;
    }

    public static Waypoint addHere(String name, int color) {
        var p = Game.player();
        Waypoint w = new Waypoint();
        w.name = name;
        w.x = Math.floor(p.getX()) + 0.5;
        w.y = Math.floor(p.getY());
        w.z = Math.floor(p.getZ()) + 0.5;
        w.dimension = Game.dimensionId();
        w.color = color;
        all().add(w);
        save();
        return w;
    }

    public static void remove(Waypoint w) {
        all().remove(w);
        save();
    }

    public static void onDeath() {
        if (!Modules.WAYPOINTS.enabled() || !Modules.WAYPOINTS.deathMarkers.get()) return;
        Waypoint w = addHere("Death", Theme.DANGER);
        w.death = true;
        // Keep only the most recent few so old deaths don't pile up.
        int keep = Modules.WAYPOINTS.deathsKept.asInt();
        List<Waypoint> deaths = new ArrayList<>();
        for (Waypoint each : all()) if (each.death) deaths.add(each);
        for (int i = 0; i < deaths.size() - keep; i++) all().remove(deaths.get(i));
        save();
        Toasts.info("Death marker saved", String.format("%d, %d, %d", (int) w.x, (int) w.y, (int) w.z));
    }

    public static void save() {
        if (loadedKey == null) return;
        try {
            Path file = file(loadedKey);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(list), StandardCharsets.UTF_8);
        } catch (Exception e) {
            AllerClient.LOG.warn("Could not save waypoints", e);
        }
    }

    private static Path file(String key) {
        return AllerClient.config().dir().resolve("waypoints").resolve(key + ".json");
    }

    public static double distance(Waypoint w) {
        var p = Game.player();
        double dx = w.x - p.getX(), dy = w.y - p.getY(), dz = w.z - p.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static String distanceText(double d) {
        return d >= 1000 ? String.format("%.1fkm", d / 1000) : Math.round(d) + "m";
    }

    /** Draws a marker shape centred on (cx, cy); {@code halo} adds a dark rim so it reads over any scene. */
    public static void drawShape(Canvas c, Shape shape, float cx, float cy, float r, int color, boolean halo) {
        if (halo) paint(c, shape, cx, cy, r + 1f, Colors.withAlpha(Colors.BLACK, 0.5f));
        paint(c, shape, cx, cy, r, color);
    }

    private static void paint(Canvas c, Shape shape, float cx, float cy, float r, int color) {
        switch (shape) {
            case CIRCLE -> c.circle(cx, cy, r, color);
            case DIAMOND -> c.polygon(cx, cy, r * 1.2f, 4, r * 0.18f, color);
            case TRIANGLE -> c.polygon(cx, cy + r * 0.15f, r * 1.3f, 3, r * 0.2f, color);
            case SQUARE -> c.rect(cx - r * 0.9f, cy - r * 0.9f, r * 1.8f, r * 1.8f, r * 0.3f, color);
            case HEXAGON -> c.polygon(cx, cy, r * 1.12f, 6, r * 0.18f, color);
            case STAR -> c.star(cx, cy + r * 0.05f, r * 1.35f, color);
        }
    }

    /** Draws a marker for each visible waypoint in the current dimension. */
    public static void draw(Canvas c) {
        var mod = Modules.WAYPOINTS;
        String dim = Game.dimensionId();
        float sw = c.width(), sh = c.height();
        float max = mod.maxDistance.get();
        float s = mod.markerScale.get();
        for (Waypoint w : all()) {
            if (!w.visible || !w.dimension.equals(dim)) continue;
            double dist = distance(w);
            if (max > 0 && dist > max && !w.death) continue;
            float[] p = View.project(w.x, w.y + 1.2, w.z, sw, sh);
            boolean front = p[2] > 0.05f;
            float x = p[0], y = p[1];
            float margin = 16;
            boolean onScreen = front && x > margin && x < sw - margin && y > margin && y < sh - margin;
            if (!onScreen) {
                if (!mod.edgeMarkers.get()) continue;
                // The projection divides by the absolute depth, so a point behind and to the right
                // already lands to the right: follow that direction out to the screen edge.
                float dx = x - sw / 2, dy = y - sh / 2;
                if (Math.abs(dx) < 0.001f && Math.abs(dy) < 0.001f) dy = 1;
                float k = Math.min((sw / 2 - margin) / Math.max(Math.abs(dx), 0.001f), (sh / 2 - margin) / Math.max(Math.abs(dy), 0.001f));
                x = sw / 2 + dx * k;
                y = sh / 2 + dy * k;
                float len = (float) Math.hypot(dx, dy);
                float ux = dx / len, uy = dy / len;
                // The shape sits just inside the edge with an arrowhead beyond it, pointing at the waypoint.
                drawShape(c, w.shape(), x - ux * 7 * s, y - uy * 7 * s, 3.2f * s, w.color, true);
                c.push();
                c.rotate((float) (Math.atan2(uy, ux) + Math.PI / 2), x, y);
                c.polygon(x, y, 3.6f * s, 3, 0.7f, Colors.withAlpha(Colors.BLACK, 0.5f));
                c.polygon(x, y, 2.8f * s, 3, 0.6f, w.color);
                c.pop();
                continue;
            }
            // Markers near the crosshair expand to show their label; the rest stay quiet.
            float off = (float) Math.hypot(x - sw / 2, y - sh / 2);
            float focus = 1f - Math.clamp((off - 20) / 70f, 0f, 1f);
            float r = (3.2f + 1.5f * focus) * s;
            c.shadow(x - r, y - r, r * 2, r * 2, r, 6, Colors.withAlpha(w.color, 0.5f));
            drawShape(c, w.shape(), x, y, r, w.color, true);
            float labelAlpha = mod.alwaysLabel.get() ? Math.max(0.6f, focus) : focus;
            if (labelAlpha > 0.02f) {
                String label = w.name + "  " + distanceText(dist);
                float size = 7.5f * s;
                float tw = Fonts.MEDIUM.width(label, size);
                c.pushAlpha(labelAlpha);
                c.rect(x - tw / 2 - 5, y + r + 4, tw + 10, size + 6, (size + 6) / 2, Theme.GLASS_HUD);
                c.textCentered(Fonts.MEDIUM, label, x, y + r + 4 + (size + 6 - Fonts.MEDIUM.height(size)) / 2, size, Theme.TEXT);
                c.popAlpha();
            }
        }
    }
}
