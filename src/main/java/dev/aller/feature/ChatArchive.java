package dev.aller.feature;

import dev.aller.platform.Mc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Everything ever said in chat that is still on disk: Aller's own {@link ChatLog} files, and the
 * chat lines in the game's logs (latest.log and the dated .log.gz archives) for the days before
 * Aller was keeping its own. Reading is plain file work with no game state, so it can run off
 * the render thread.
 */
public final class ChatArchive {
    private static final Pattern DATED = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:-(\\d+))?\\.log(?:\\.gz)?");
    private static final Pattern CLOCK = Pattern.compile("\\[(?:[^\\]]*? )?(\\d{2}:\\d{2}:\\d{2})");
    private static final String CHAT = "[CHAT] ";

    /**
     * One file.
     *
     * @param server the folder name for one of Aller's logs; null for a game log, which does not say
     * @param order  the game's number for a day's logs, to keep them in order
     */
    public record Source(Path file, LocalDate date, String server, int order) {}

    /** @param flags see {@link ChatLog}; empty for lines from a game log */
    public record Entry(LocalDate date, String time, String server, String coded, String flags) {
        public boolean has(char flag) {
            return flags.indexOf(flag) >= 0;
        }
    }

    private ChatArchive() {}

    /** The files to search, oldest first. {@code server} narrows it to one of Aller's folders; null takes everything. */
    public static List<Source> sources(String server) {
        List<Source> out = new ArrayList<>();
        Set<LocalDate> kept = new HashSet<>();
        Path root = ChatLog.dir();
        if (Files.isDirectory(root)) {
            try (Stream<Path> folders = Files.list(root)) {
                for (Path folder : folders.filter(Files::isDirectory).toList()) {
                    String name = folder.getFileName().toString();
                    try (Stream<Path> files = Files.list(folder)) {
                        for (Path file : files.toList()) {
                            Matcher m = DATED.matcher(file.getFileName().toString());
                            if (!m.matches()) continue;
                            LocalDate date = date(m.group(1));
                            if (date == null) continue;
                            kept.add(date);
                            if (server == null || server.equals(name)) out.add(new Source(file, date, name, 0));
                        }
                    }
                }
            } catch (IOException ignored) {
                // search whatever could be listed
            }
        }
        Path logs = Mc.mc().gameDirectory.toPath().resolve("logs");
        if (server == null && Files.isDirectory(logs)) {
            try (Stream<Path> files = Files.list(logs)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    LocalDate date;
                    int order;
                    Matcher m = DATED.matcher(name);
                    if (m.matches()) {
                        date = date(m.group(1));
                        order = m.group(2) == null ? 0 : Integer.parseInt(m.group(2).length() > 6 ? "999999" : m.group(2));
                    } else if (name.equals("latest.log")) {
                        date = Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).atZone(ZoneId.systemDefault()).toLocalDate();
                        order = Integer.MAX_VALUE;
                    } else {
                        continue;
                    }
                    // A day Aller logged itself is already there, with colours and its server.
                    if (date != null && !kept.contains(date)) out.add(new Source(file, date, null, order));
                }
            } catch (IOException ignored) {
                // as above
            }
        }
        out.sort(Comparator.comparing(Source::date).thenComparingInt(Source::order)
                .thenComparing(s -> s.server() == null ? "" : s.server()));
        return out;
    }

    private static LocalDate date(String text) {
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** What to look for: plain text or a regular expression, tested against a line without its colour codes. */
    public static Predicate<String> matcher(String query, boolean regex, boolean matchCase) {
        if (query.isEmpty()) return line -> true;
        if (regex) return Pattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).asPredicate();
        if (matchCase) return line -> line.contains(query);
        String lower = query.toLowerCase(Locale.ROOT);
        return line -> line.toLowerCase(Locale.ROOT).contains(lower);
    }

    /** The chat lines of one file that match, in the order they were said. */
    public static List<Entry> read(Source source, Predicate<String> match) {
        List<Entry> out = new ArrayList<>();
        boolean zipped = source.file().getFileName().toString().endsWith(".gz");
        try (InputStream raw = Files.newInputStream(source.file());
             BufferedReader in = new BufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Entry e = source.server() != null ? own(source, line) : game(source, line);
                if (e != null && match.test(ChatText.strip(e.coded()))) out.add(e);
                if (Thread.interrupted()) break;
            }
        } catch (IOException | RuntimeException ignored) {
            // a truncated or unreadable file gives what it had
        }
        return out;
    }

    private static Entry own(Source source, String line) {
        String[] parts = line.split("\t", 3);
        if (parts.length < 3) return null;
        return new Entry(source.date(), parts[0], source.server(), parts[2], parts[1].equals("-") ? "" : parts[1]);
    }

    /** "[12:34:56] [Render thread/INFO]: [System] [CHAT] text", with small differences between launchers. */
    private static Entry game(Source source, String line) {
        int at = line.indexOf(CHAT);
        if (at < 0 || at > 96 || !line.startsWith("[")) return null;
        Matcher clock = CLOCK.matcher(line);
        String time = clock.lookingAt() ? clock.group(1) : "";
        return new Entry(source.date(), time, "", line.substring(at + CHAT.length()), "");
    }
}
