package dev.aller.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.aller.AllerClient;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Session statistics. A session runs from joining a world or server until leaving it. Samples are
 * taken once a second into fixed-size rings for the live graphs; a summary of each finished
 * session is appended to {@code sessions.json} for the history view.
 */
public final class Session {
    public static final int WINDOW = 300;

    /** What gets persisted for each finished session. */
    public static final class Summary {
        public long started;
        public long seconds;
        public String where = "";
        public int avgFps, minFps, avgPing;
        public int hits, kills, deaths;
        public double distance;
        public int peakCps;
    }

    /** Live counters for the session in progress. */
    public static final class Live {
        public long started = System.currentTimeMillis();
        public String where = "";
        public int hits, kills, deaths;
        public double distance;
        public int peakCps;
        public final float[] fps = new float[WINDOW];
        public final float[] ping = new float[WINDOW];
        public final float[] cps = new float[WINDOW];
        public int samples;
        long fpsSum, pingSum;
        int pingSamples, minFps = Integer.MAX_VALUE;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Live live = new Live();
    private static boolean active;
    private static int tickCounter;
    private static double lastX, lastY, lastZ;
    private static boolean wasDead;
    private static List<Summary> history;

    private Session() {}

    public static Live current() {
        return live;
    }

    public static boolean active() {
        return active;
    }

    public static long seconds() {
        return (System.currentTimeMillis() - live.started) / 1000;
    }

    public static void tick() {
        var player = Game.player();
        if (player == null) {
            if (active) end();
            return;
        }
        if (!active) begin();

        double dx = player.getX() - lastX, dy = player.getY() - lastY, dz = player.getZ() - lastZ;
        double step = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (step < 20) live.distance += step; // ignore teleports and respawns
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();

        boolean dead = player.isDeadOrDying();
        if (dead && !wasDead) {
            live.deaths++;
            Waypoints.onDeath();
        }
        wasDead = dead;

        live.peakCps = Math.max(live.peakCps, Clicks.left());
        if (++tickCounter >= 20) {
            tickCounter = 0;
            sample();
        }
    }

    private static void sample() {
        int fps = Mc.mc().getFps();
        int ping = Math.max(0, Game.ping());
        int i = live.samples % WINDOW;
        live.fps[i] = fps;
        live.ping[i] = ping;
        live.cps[i] = Clicks.left();
        live.samples++;
        live.fpsSum += fps;
        live.minFps = Math.min(live.minFps, fps);
        if (ping > 0) {
            live.pingSum += ping;
            live.pingSamples++;
        }
    }

    /** Copies the most recent {@code count} samples of a ring, oldest first. */
    public static float[] recent(float[] ring, int count) {
        int n = Math.min(Math.min(count, WINDOW), live.samples);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = ring[Math.floorMod(live.samples - n + i, WINDOW)];
        return out;
    }

    private static void begin() {
        live = new Live();
        String address = Game.serverAddress();
        live.where = address != null ? address : "Singleplayer";
        active = true;
        wasDead = false;
        var p = Game.player();
        lastX = p.getX();
        lastY = p.getY();
        lastZ = p.getZ();
    }

    private static void end() {
        active = false;
        if (seconds() < 30) return; // not worth recording
        Summary s = new Summary();
        s.started = live.started;
        s.seconds = seconds();
        s.where = live.where;
        s.avgFps = live.samples == 0 ? 0 : (int) (live.fpsSum / live.samples);
        s.minFps = live.minFps == Integer.MAX_VALUE ? 0 : live.minFps;
        s.avgPing = live.pingSamples == 0 ? 0 : (int) (live.pingSum / live.pingSamples);
        s.hits = live.hits;
        s.kills = live.kills;
        s.deaths = live.deaths;
        s.distance = live.distance;
        s.peakCps = live.peakCps;
        history().add(s);
        while (history.size() > 200) history.remove(0);
        save();
    }

    public static List<Summary> history() {
        if (history == null) {
            history = new ArrayList<>();
            Path file = file();
            if (Files.exists(file)) {
                try {
                    List<Summary> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                            new TypeToken<List<Summary>>() {}.getType());
                    if (loaded != null) {
                        for (Summary s : loaded) {
                            if (s == null) continue;
                            if (s.where == null) s.where = "";
                            history.add(s);
                        }
                    }
                } catch (Exception e) {
                    AllerClient.LOG.warn("Could not read session history", e);
                }
            }
        }
        return history;
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(history), StandardCharsets.UTF_8);
        } catch (Exception e) {
            AllerClient.LOG.warn("Could not save session history", e);
        }
    }

    private static Path file() {
        return AllerClient.config().dir().resolve("sessions.json");
    }
}
