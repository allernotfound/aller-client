package dev.aller.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aller.AllerClient;
import dev.aller.platform.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * How to read a chat line. Servers format chat however they like and send it without saying who
 * wrote it, so the author has to be found in the text: by the player's own formats, each for one
 * server or for all of them, and failing those by the first separator ("Name: message").
 *
 * <p>A format is the line with placeholders: {@code {name}} for the sender, {@code {message}} for
 * what they said, {@code {any}} for anything else. One starting with {@code regex:} is used as a
 * regular expression with groups called name and message.
 */
public final class ChatFormats {
    public static final int FORMAT_MAX = 160, SERVER_MAX = 64, SEPARATORS_MAX = 48;
    public static final String DEFAULT_SEPARATORS = ": » > → ➜ ➤ ›";
    /** How far into a line a separator may sit and still end the author part. */
    private static final int HEAD_MAX = 64;

    public enum Kind {
        WHISPER("Private message"), CHAT("Chat line");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public static final class Rule {
        public Kind kind = Kind.WHISPER;
        /** Part of a server address this format is for; blank for every server. */
        public String server = "";
        public String format = "";
        private String compiledFrom;
        private Pattern pattern;

        public Rule() {}

        Rule(Kind kind, String format) {
            this.kind = kind;
            this.format = format;
        }

        /** The compiled format, or null if it is blank or not a valid expression. */
        public Pattern pattern() {
            if (!format.equals(compiledFrom)) {
                compiledFrom = format;
                pattern = compile(format);
            }
            return pattern;
        }

        public boolean valid() {
            return format.isBlank() || pattern() != null;
        }

        boolean applies(String address) {
            String s = server.strip().toLowerCase();
            return s.isEmpty() || address != null && address.toLowerCase().contains(s);
        }
    }

    /**
     * One line, taken apart. Positions are indexes into the line.
     *
     * @param whisper whether it is a private message to the player
     */
    public record Line(int authorStart, int authorEnd, int bodyStart, boolean whisper) {
        public String author(String line) {
            return line.substring(authorStart, authorEnd);
        }
    }

    private static final List<Rule> rules = new ArrayList<>();
    private static String separators = DEFAULT_SEPARATORS;
    private static boolean loaded;

    private ChatFormats() {}

    public static List<Rule> all() {
        if (!loaded) {
            loaded = true;
            rules.addAll(defaults());
            try {
                JsonElement saved = AllerClient.config().extra("chat_formats");
                if (saved != null && saved.isJsonObject()) read(saved.getAsJsonObject());
            } catch (RuntimeException e) {
                AllerClient.LOG.warn("Ignoring unreadable chat formats", e);
            }
        }
        return rules;
    }

    private static void read(JsonObject o) {
        if (o.has("separators") && o.get("separators").isJsonPrimitive()) separators = clip(o.get("separators").getAsString(), SEPARATORS_MAX);
        if (!o.has("rules") || !o.get("rules").isJsonArray()) return;
        List<Rule> read = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("rules")) {
            try {
                JsonObject r = e.getAsJsonObject();
                Rule rule = new Rule();
                rule.kind = "CHAT".equals(r.get("kind").getAsString()) ? Kind.CHAT : Kind.WHISPER;
                rule.server = clip(r.get("server").getAsString(), SERVER_MAX);
                rule.format = clip(r.get("format").getAsString(), FORMAT_MAX);
                read.add(rule);
            } catch (RuntimeException ignored) {
                // skip a malformed entry rather than losing the rest
            }
        }
        rules.clear();
        rules.addAll(read);
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("separators", separators);
        JsonArray arr = new JsonArray();
        for (Rule r : all()) {
            JsonObject j = new JsonObject();
            j.addProperty("kind", r.kind.name());
            j.addProperty("server", r.server);
            j.addProperty("format", r.format);
            arr.add(j);
        }
        o.add("rules", arr);
        AllerClient.config().setExtra("chat_formats", o);
    }

    /** The whisper formats of vanilla and the most common chat plugins. */
    public static List<Rule> defaults() {
        return new ArrayList<>(List.of(
                new Rule(Kind.WHISPER, "{name} whispers to you: {message}"),
                new Rule(Kind.WHISPER, "[{name} -> me] {message}"),
                new Rule(Kind.WHISPER, "[{name} -> You] {message}"),
                new Rule(Kind.WHISPER, "From {name}: {message}")));
    }

    public static void restoreDefaults() {
        all().clear();
        rules.addAll(defaults());
        separators = DEFAULT_SEPARATORS;
        save();
    }

    public static Rule add() {
        Rule r = new Rule();
        String address = Game.serverAddress();
        if (address != null) r.server = clip(address, SERVER_MAX);
        all().add(r);
        save();
        return r;
    }

    public static void remove(Rule r) {
        all().remove(r);
        save();
    }

    public static String separators() {
        all();
        return separators;
    }

    public static void setSeparators(String value) {
        all();
        separators = clip(value, SEPARATORS_MAX);
        save();
    }

    private static String clip(String s, int max) {
        s = s.replaceAll("\\p{Cntrl}", " ");
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Finds the author and where the message starts; null for a line nobody wrote (joins, deaths, broadcasts). */
    public static Line parse(String line) {
        String address = Game.serverAddress();
        // A format written for this server beats one written for all of them.
        for (int pass = 0; pass < 2; pass++) {
            for (Kind kind : Kind.values()) {
                for (Rule r : all()) {
                    if (r.kind != kind || r.server.isBlank() != (pass == 1) || !r.applies(address)) continue;
                    Pattern p = r.pattern();
                    if (p == null) continue;
                    Matcher m = p.matcher(line);
                    if (!m.find()) continue;
                    int[] name = group(m, "name"), message = group(m, "message");
                    if (name == null) name = new int[] {0, 0};
                    return new Line(name[0], name[1], message == null ? m.end() : message[0], kind == Kind.WHISPER);
                }
            }
        }
        int best = -1, length = 0;
        for (String sep : separators().split(" ")) {
            if (sep.isEmpty()) continue;
            int at = line.indexOf(sep + " ", 1);
            // The head of an arrow ("Sam -> Alex: hi") is not the end of the author.
            while (at > 0 && sep.equals(">") && "-=".indexOf(line.charAt(at - 1)) >= 0) at = line.indexOf(sep + " ", at + 1);
            if (at > 0 && at <= HEAD_MAX && (best < 0 || at < best)) {
                best = at;
                length = sep.length() + 1;
            }
        }
        return best < 0 ? null : new Line(0, best, best + length, false);
    }

    private static int[] group(Matcher m, String name) {
        try {
            return m.start(name) < 0 ? null : new int[] {m.start(name), m.end(name)};
        } catch (IllegalArgumentException e) {
            return null; // the format has no such group
        }
    }

    static Pattern compile(String format) {
        String f = format.strip();
        if (f.isEmpty()) return null;
        try {
            if (f.regionMatches(true, 0, "regex:", 0, 6)) return Pattern.compile(f.substring(6).strip());
            StringBuilder sb = new StringBuilder("^\\s*");
            boolean name = false, message = false;
            for (int i = 0; i < f.length(); i++) {
                char ch = f.charAt(i);
                int close = ch == '{' ? f.indexOf('}', i) : -1;
                if (close > i) {
                    String key = f.substring(i + 1, close).strip().toLowerCase();
                    if (key.equals("name") && !name) {
                        sb.append("(?<name>.+?)");
                        name = true;
                    } else if (key.equals("message") && !message) {
                        sb.append("(?<message>.*)");
                        message = true;
                    } else {
                        sb.append(".*?");
                    }
                    i = close;
                } else if (Character.isWhitespace(ch)) {
                    sb.append("\\s+");
                    while (i + 1 < f.length() && Character.isWhitespace(f.charAt(i + 1))) i++;
                } else {
                    sb.append(Pattern.quote(String.valueOf(ch)));
                }
            }
            return Pattern.compile(sb.append("\\s*$").toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /** The player name inside an author part such as "[VIP] Sam": its last run of name characters. */
    public static int[] nameIn(String line, Line parsed) {
        int end = parsed.authorEnd();
        while (end > parsed.authorStart() && !nameChar(line.charAt(end - 1))) end--;
        int start = end;
        while (start > parsed.authorStart() && nameChar(line.charAt(start - 1))) start--;
        return end - start >= 2 ? new int[] {start, end} : null;
    }

    private static boolean nameChar(char c) {
        return c == '_' || c < 128 && Character.isLetterOrDigit(c);
    }
}
