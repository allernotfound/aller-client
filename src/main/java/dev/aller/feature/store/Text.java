package dev.aller.feature.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Small formatting for what the store shows: counts, sizes, ages, and text from the web made drawable. */
public final class Text {
    private Text() {}

    /**
     * Drops what neither font can draw (emoji, joiners, control characters) and squeezes runs of
     * spaces, so a title does not come out as a row of boxes.
     */
    public static String clean(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || cp == 0xA0) {
                space = true;
                continue;
            }
            if (!drawable(cp)) continue;
            if (space && !out.isEmpty()) out.append(' ');
            space = false;
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    /** False for emoji and the invisible characters that glue them together. */
    public static boolean drawable(int cp) {
        if (cp < 32 || cp == 127) return false;
        if (cp >= 0x1F000 && cp <= 0x1FAFF) return false; // emoji, pictographs, flags
        if (cp >= 0xFE00 && cp <= 0xFE0F || cp == 0x200D || cp == 0x200B || cp == 0x20E3 || cp == 0xFEFF) return false;
        if (cp >= 0xE0000 && cp <= 0xE007F) return false; // tag characters
        if (cp >= 0x2600 && cp <= 0x27BF && cp != 0x2713 && cp != 0x2605) return false; // dingbats, bar the two Inter is given
        if (cp >= 0x2B00 && cp <= 0x2BFF || cp >= 0x2300 && cp <= 0x23FF && cp != 0x23CE) return false;
        return Character.isDefined(cp);
    }

    /** 48186994 as "48.2M", 14328 as "14.3k". */
    public static String count(long n) {
        if (n >= 1_000_000_000) return trim(n / 1e9) + "B";
        if (n >= 1_000_000) return trim(n / 1e6) + "M";
        if (n >= 1_000) return trim(n / 1e3) + "k";
        return Long.toString(n);
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, value >= 100 ? "%.0f" : "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    public static String size(long bytes) {
        if (bytes >= 1 << 30) return trim(bytes / (double) (1 << 30)) + " GB";
        if (bytes >= 1 << 20) return trim(bytes / (double) (1 << 20)) + " MB";
        if (bytes >= 1 << 10) return trim(bytes / (double) (1 << 10)) + " KB";
        return bytes + " B";
    }

    /** "3 months ago"; empty for an unknown time. */
    public static String ago(Instant when) {
        if (when == null) return "";
        long minutes = Math.max(0, Duration.between(when, Instant.now()).toMinutes());
        if (minutes < 2) return "just now";
        if (minutes < 60) return minutes + " minutes ago";
        long hours = minutes / 60;
        if (hours < 24) return hours == 1 ? "an hour ago" : hours + " hours ago";
        long days = hours / 24;
        if (days < 31) return days == 1 ? "yesterday" : days + " days ago";
        long months = days / 30;
        if (months < 12) return months == 1 ? "a month ago" : months + " months ago";
        long years = days / 365;
        return years <= 1 ? "a year ago" : years + " years ago";
    }

    /** A list of game versions cut down to its ends: "1.13.2 to 26.3". */
    public static String span(java.util.List<String> versions) {
        java.util.List<String> releases = versions.stream().filter(v -> v.matches("[0-9.]+")).toList();
        java.util.List<String> use = releases.isEmpty() ? versions : releases;
        if (use.isEmpty()) return "";
        if (use.size() == 1) return use.get(0);
        if (use.size() == 2) return use.get(0) + ", " + use.get(1);
        return use.get(0) + " to " + use.get(use.size() - 1);
    }
}
