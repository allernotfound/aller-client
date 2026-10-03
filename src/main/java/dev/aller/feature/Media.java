package dev.aller.feature;

import dev.aller.AllerClient;
import dev.aller.platform.MediaNative;
import dev.aller.platform.WebNative;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * What is playing on the computer (Spotify, a browser tab, a media player), as Windows reports it,
 * and the play, pause and skip buttons it offers. Asked once a second on a worker thread, and only
 * while something on screen wants to know.
 */
public final class Media {
    /** @param updated when {@code position} was true, in milliseconds since 1970 */
    public record Now(String title, String artist, boolean playing, long position, long length, long updated) {
        /** Where the track is now: players report their position only now and then. */
        public long at() {
            long at = playing && updated > 0 ? position + System.currentTimeMillis() - updated : position;
            return length > 0 ? Math.clamp(at, 0, length) : Math.max(0, at);
        }
    }

    private static ScheduledExecutorService worker;
    private static volatile Now now;
    private static volatile long wantedAt;
    private static boolean tried;
    private static volatile boolean usable;

    private Media() {}

    /** Whether this system can be asked at all (64-bit Windows, with the native library in the build). */
    public static boolean usable() {
        if (!tried) {
            tried = true;
            usable = WebNative.load(Browser.dir());
        }
        return usable;
    }

    /** What is playing, or null. Calling this is what keeps it up to date. */
    public static Now now() {
        if (!usable()) return null;
        wantedAt = System.currentTimeMillis();
        if (worker == null) {
            worker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Aller media");
                t.setDaemon(true);
                return t;
            });
            worker.scheduleWithFixedDelay(Media::ask, 0, 1, TimeUnit.SECONDS);
        }
        return now;
    }

    private static void ask() {
        if (!usable || System.currentTimeMillis() - wantedAt > 3000) return;
        try {
            String[] lines = MediaNative.now().split("\n", -1);
            if (lines.length < 6 || lines[0].isBlank() && lines[1].isBlank()) {
                now = null;
                return;
            }
            now = new Now(lines[0], lines[1], lines[2].equals("1"), Long.parseLong(lines[3]), Long.parseLong(lines[4]), Long.parseLong(lines[5]));
        } catch (Throwable e) {
            // An older library without the media calls, or a system that will not answer: stop asking.
            now = null;
            usable = false;
            AllerClient.LOG.warn("Could not ask the system what is playing", e);
        }
    }

    /** @param what one of {@link MediaNative}'s commands */
    public static void command(int what) {
        if (!usable() || worker == null) return;
        worker.execute(() -> {
            try {
                MediaNative.command(what);
                ask();
            } catch (Throwable e) {
                AllerClient.LOG.warn("Could not send a media command", e);
            }
        });
    }
}
