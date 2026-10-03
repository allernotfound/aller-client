package dev.aller.feature;

import dev.aller.platform.Sounds;
import dev.aller.ui.Toasts;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Countdown timers and a stopwatch, started from the launcher and counted on the HUD. Nothing is kept between launches. */
public final class Timers {
    public static final class Timer {
        public final String name;
        public final long ends;

        Timer(String name, long ends) {
            this.name = name;
            this.ends = ends;
        }

        public long left() {
            return Math.max(0, ends - System.currentTimeMillis());
        }
    }

    private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(?:\\s*(h|m|s)(?![a-z]))?", Pattern.CASE_INSENSITIVE);
    private static final List<Timer> timers = new ArrayList<>();
    /** When the stopwatch was started, or 0; what it had counted when it was stopped. */
    private static long watchStarted, watchHeld;

    private Timers() {}

    public static List<Timer> all() {
        return timers;
    }

    /**
     * Reads "5m", "90s", "1h30m", "2:30" or a bare number of minutes, with an optional name after
     * it ("10m furnace").
     *
     * @return {milliseconds, where the name starts} or null if it does not start with a time
     */
    public static long[] parse(String text) {
        String s = text.strip();
        Matcher clock = Pattern.compile("^(\\d+):(\\d{1,2})(?::(\\d{1,2}))?").matcher(s);
        if (clock.find()) {
            long a = Long.parseLong(clock.group(1)), b = Long.parseLong(clock.group(2));
            long seconds = clock.group(3) == null ? a * 60 + b : a * 3600 + b * 60 + Long.parseLong(clock.group(3));
            return seconds <= 0 ? null : new long[] {seconds * 1000, clock.end()};
        }
        double total = 0;
        int at = 0;
        boolean any = false;
        while (at < s.length()) {
            Matcher m = PART.matcher(s).region(at, s.length());
            if (!m.lookingAt()) break;
            String unit = m.group(2);
            // A number with no unit is minutes, but only on its own: "1h30" would be ambiguous.
            if (unit == null && any) break;
            double n = Double.parseDouble(m.group(1));
            total += n * (unit == null || unit.equalsIgnoreCase("m") ? 60 : unit.equalsIgnoreCase("h") ? 3600 : 1);
            any = true;
            at = m.end();
            if (unit == null) break;
            while (at < s.length() && s.charAt(at) == ' ' && at + 1 < s.length() && Character.isDigit(s.charAt(at + 1))) at++;
        }
        if (!any || total <= 0 || total > 24 * 3600) return null;
        return new long[] {Math.round(total * 1000), at};
    }

    /** @return the timer, or null if the text does not say how long */
    public static Timer start(String text) {
        long[] parsed = parse(text);
        if (parsed == null) return null;
        String name = text.strip().substring((int) parsed[1]).strip();
        Timer t = new Timer(name.isEmpty() ? clock(parsed[0]) + " timer" : name, System.currentTimeMillis() + parsed[0]);
        timers.add(t);
        while (timers.size() > 8) timers.remove(0);
        return t;
    }

    public static void clear() {
        timers.clear();
    }

    public static boolean watchRunning() {
        return watchStarted != 0;
    }

    /** Whether the stopwatch has anything to show. */
    public static boolean watchShown() {
        return watchStarted != 0 || watchHeld != 0;
    }

    public static long watch() {
        return watchHeld + (watchStarted == 0 ? 0 : System.currentTimeMillis() - watchStarted);
    }

    public static void watchToggle() {
        if (watchStarted == 0) {
            watchStarted = System.currentTimeMillis();
        } else {
            watchHeld = watch();
            watchStarted = 0;
        }
    }

    public static void watchReset() {
        watchStarted = 0;
        watchHeld = 0;
    }

    /** Once a client tick: timers that have run out say so and go. */
    public static void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Timer> it = timers.iterator(); it.hasNext(); ) {
            Timer t = it.next();
            if (t.ends > now) continue;
            it.remove();
            Toasts.warn("Time is up", t.name);
            Sounds.alert(Sounds.Alert.BELL, 1f, 0.8f);
        }
    }

    /** "4:32", "1:02:05". */
    public static String clock(long millis) {
        long s = (millis + 999) / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }
}
