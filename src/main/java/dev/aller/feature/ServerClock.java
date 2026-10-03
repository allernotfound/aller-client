package dev.aller.feature;

import dev.aller.platform.Game;
import dev.aller.platform.Mc;

/**
 * The server's tick rate, estimated from its time updates. It sends the world's age about once a
 * second; how far that age moved against how much real time passed is the rate it is ticking at.
 */
public final class ServerClock {
    private static long lastTicks = -1, lastAt, heardAt;
    private static float tps = 20f;
    private static int samples;
    private static Object level;

    private ServerClock() {}

    /** A time update arrived, carrying the world's age in ticks. */
    public static void time(long gameTime) {
        long now = System.nanoTime();
        if (Game.level() != level) {
            level = Game.level();
            lastTicks = -1;
            samples = 0;
            tps = 20f;
        }
        heardAt = now;
        if (lastTicks >= 0 && gameTime > lastTicks) {
            double seconds = (now - lastAt) / 1e9;
            // Updates bunch up after a hitch on the way here; too close together says nothing.
            if (seconds < 0.4) return;
            if (seconds < 10) {
                float sample = (float) Math.min(20.0, (gameTime - lastTicks) / seconds);
                tps = samples == 0 ? sample : tps + (sample - tps) * 0.35f;
                samples++;
            }
        }
        lastTicks = gameTime;
        lastAt = now;
    }

    /** Ticks per second, at most 20; -1 until two updates have been seen. */
    public static float tps() {
        return samples == 0 || Game.level() != level ? -1 : tps;
    }

    /** Seconds the server has been silent for, once that is longer than it should ever be; otherwise 0. */
    public static float silence() {
        if (heardAt == 0 || Game.level() != level || Mc.mc().isPaused()) return 0;
        float s = (System.nanoTime() - heardAt) / 1e9f;
        return s > 2.5f ? s : 0;
    }
}
