package dev.aller.module.mods;

import dev.aller.feature.Chat;
import dev.aller.feature.ChatFormats;
import dev.aller.feature.ChatText;
import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.ChatView;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.Sounds;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** The chat mods. {@link Chat} runs each incoming message through whichever of them are on. */
public final class ChatMods {
    private ChatMods() {}

    public static final class Timestamps extends Module {
        public final Settings.Bool twentyFour = bool("24h", "24-hour clock", true);
        public final Settings.Bool seconds = bool("seconds", "Show seconds", false);
        public final Settings.Color color = add(new Settings.Color("color", "Colour", 0xFF7A7490, false));

        public Timestamps() {
            super("chat_timestamps", "Chat timestamps", "Prefix every chat message with the time it arrived", Category.CHAT);
            keywords("time", "messages");
        }

        public Component stamp(Component message) {
            String pattern = (twentyFour.get() ? "HH:mm" : "h:mm") + (seconds.get() ? ":ss" : "") + (twentyFour.get() ? "" : " a");
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern));
            int rgb = color.get() & 0xFFFFFF;
            return Component.empty()
                    .append(Component.literal("[" + time + "] ").withStyle(style -> style.withColor(rgb)))
                    .append(message);
        }
    }

    public static final class History extends Module {
        public final Settings.Num lines = num("lines", "Messages kept", 10250, 250, 10250, 250)
                .format(v -> v >= 10250 ? "Unlimited" : Integer.toString(Math.round(v)))
                .describe("Also how many of your own sent messages the up arrow goes back through");

        public History() {
            super("chat_history", "Chat history", "Scroll back further than Minecraft's 100 messages", Category.CHAT);
            keywords("infinite", "unlimited", "scrollback", "longer", "limit");
            onByDefault();
        }

        public int limit(int original) {
            return lines.get() >= lines.max ? Integer.MAX_VALUE : Math.max(original, lines.asInt());
        }
    }

    public static final class Mentions extends Module {
        public enum Kind { NONE, MENTION, PRIVATE }

        public enum Notify { NEVER, WHEN_AWAY, ALWAYS }

        /** @param ranges where the trigger words are in the line, as start and end positions */
        public record Hit(Kind kind, List<int[]> ranges) {
            static final Hit NONE = new Hit(Kind.NONE, List.of());
        }

        public final Settings.Bool username;
        public final Settings.Text words;
        public final Settings.Bool everyone;
        public final Settings.Text everyoneWords;
        public final Settings.Bool wholeWord;
        public final Settings.Bool matchCase;
        public final Settings.Bool privateMessages;
        public final Settings.Bool systemLines;
        public final Settings.Bool ignoreAuthor;
        public final Settings.Bool ignoreEcho;
        public final Settings.Num echoWindow;
        public final Settings.Bool highlight;
        public final Settings.Num strength;
        public final Settings.Bool recolour;
        public final Settings.Bool useAccent;
        public final Settings.Color color;
        public final Settings.Choice<Sounds.Alert> sound;
        public final Settings.Choice<Sounds.Alert> privateSound;
        public final Settings.Num volume;
        public final Settings.Num pitch;
        public final Settings.Choice<Notify> toast;
        public final Settings.Bool flash;
        private long lastSound;

        public Mentions() {
            super("mentions", "Mentions", "A sound and a highlight when someone says your name or messages you", Category.CHAT);
            keywords("ping", "notify", "highlight", "name", "alert", "whisper", "dm");
            section("Triggers");
            username = bool("username", "Your username", true);
            words = text("words", "Other words", "", 256).describe("Nicknames, a clan tag, anything else that should ping you. Separate them with commas");
            everyone = bool("everyone", "Server-wide pings", true);
            everyoneWords = text("everyone_words", "Server-wide words", "@everyone, @here", 128).visibleWhen(everyone::get);
            wholeWord = bool("whole_word", "Whole words only", true).describe("Sam does not match Samuel");
            matchCase = bool("match_case", "Match capitals", false);
            privateMessages = bool("private", "Private messages", true)
                    .describe("Lines that match a private message format. Add your server's under Chat formats");
            systemLines = bool("system_lines", "Lines nobody wrote", false)
                    .describe("Joins, deaths and broadcasts that contain a trigger");
            section("Your own messages");
            ignoreAuthor = bool("ignore_author", "Ignore your name as the author", true)
                    .describe("A line is yours when your name sits before the separator, as in Name: message");
            ignoreEcho = bool("ignore_echo", "Ignore what you just sent", true)
                    .describe("The server showing your own message back to you never pings");
            echoWindow = num("echo_window", "For this long after sending", 5, 1, 15, 1).suffix(" s").visibleWhen(ignoreEcho::get);
            section("Highlight");
            highlight = bool("highlight", "Tint the line", true).describe("A coloured background and a bar down its left edge");
            strength = num("strength", "Tint strength", 22, 5, 60, 1).suffix("%").visibleWhen(highlight::get);
            recolour = bool("recolour", "Colour the word", true);
            useAccent = bool("use_accent", "Use the accent colour", true);
            color = add(new Settings.Color("color", "Colour", 0xFFF5B74E, false)).visibleWhen(() -> !useAccent.get());
            section("Sound");
            sound = choice("sound", "Mention", Sounds.Alert.DING);
            privateSound = choice("private_sound", "Private message", Sounds.Alert.PLING).visibleWhen(privateMessages::get);
            volume = num("volume", "Volume", 80, 0, 100, 5).suffix("%");
            pitch = num("pitch", "Pitch", 1f, 0.5f, 2f, 0.1f);
            section("Notify");
            toast = choice("toast", "Show a toast", Notify.WHEN_AWAY).describe("Away means the window is in the background or chat is hidden");
            flash = bool("flash", "Flash the taskbar", true).describe("Only while the window is in the background");
        }

        /** The highlight colour, opaque. */
        public int tint() {
            return (useAccent.get() ? Theme.accent() : color.get()) | 0xFF000000;
        }

        public Hit check(String line, ChatFormats.Line parsed) {
            if (parsed != null && parsed.whisper() && privateMessages.get()) return new Hit(Kind.PRIVATE, List.of());
            int from = 0;
            if (parsed == null) {
                if (!systemLines.get()) return Hit.NONE;
            } else {
                if (!parsed.whisper() && ignoreAuthor.get() && Chat.mine(line, parsed)) return Hit.NONE;
                from = parsed.bodyStart();
            }
            List<int[]> ranges = new ArrayList<>();
            if (username.get()) Chat.find(line, Nav.playerName(), from, wholeWord.get(), matchCase.get(), ranges);
            for (String w : words.get().split(",")) Chat.find(line, w.strip(), from, wholeWord.get(), matchCase.get(), ranges);
            if (everyone.get()) {
                for (String w : everyoneWords.get().split(",")) Chat.find(line, w.strip(), from, wholeWord.get(), matchCase.get(), ranges);
            }
            if (ranges.isEmpty()) return Hit.NONE;
            if (ignoreEcho.get() && Chat.echoed(line, echoWindow.get())) return Hit.NONE;
            return new Hit(Kind.MENTION, ranges);
        }

        public void alert(Kind kind, String line, ChatFormats.Line parsed) {
            long now = System.currentTimeMillis();
            if (now - lastSound > 300) {
                Sounds.alert(kind == Kind.PRIVATE ? privateSound.get() : sound.get(), pitch.get(), volume.get() / 100f);
                lastSound = now;
            }
            boolean active = Mc.mc().isWindowActive();
            boolean away = !active || Mc.hudHidden() || ChatView.hidden();
            if (toast.get() == Notify.ALWAYS || toast.get() == Notify.WHEN_AWAY && away) {
                String who = "", body = line;
                if (parsed != null) {
                    int[] name = ChatFormats.nameIn(line, parsed);
                    who = name == null ? parsed.author(line).strip() : line.substring(name[0], name[1]);
                    body = line.substring(Math.min(parsed.bodyStart(), line.length()));
                }
                String title = kind == Kind.PRIVATE ? "Message" + (who.isEmpty() ? "" : " from " + who)
                        : who.isEmpty() ? "Mentioned in chat" : who + " mentioned you";
                Toasts.info(title, body.strip());
            }
            if (flash.get() && !active) GLFW.glfwRequestWindowAttention(Mc.window());
        }
    }

    public static final class StackRepeats extends Module {
        public final Settings.Num window = num("window", "Only repeats within", 0, 0, 120, 5)
                .format(v -> v < 1 ? "Any time" : Math.round(v) + " s");
        private String last;
        private int count;
        private long lastAt;

        public StackRepeats() {
            super("chat_stack", "Stack repeats", "The same message again adds to a counter instead of a new line", Category.CHAT);
            keywords("compact", "spam", "duplicate", "collapse");
        }

        /** How many times in a row this line has now arrived; a repeat takes the earlier copy out of the window. */
        public int count(String line) {
            long now = System.currentTimeMillis();
            boolean repeat = line.equals(last) && !line.isBlank() && ChatView.messageCount() > 0
                    && (window.get() < 1 || now - lastAt <= window.get() * 1000);
            if (repeat) {
                ChatView.removeNewest();
                count++;
            } else {
                last = line;
                count = 1;
            }
            lastAt = now;
            return count;
        }

        public void forget() {
            last = null;
        }

        @Override
        protected void onDisable() {
            forget();
        }
    }

    public static final class Filters extends Module {
        private static final String ABOUT = "Separate phrases with commas. Put a regular expression between slashes: /^\\[Ad\\]/";
        public final Settings.Text phrases = text("phrases", "Hide lines containing", "", 512).describe(ABOUT);
        public final Settings.Bool keepOwn = bool("keep_own", "Never hide your own messages", true);
        private String builtFrom;
        private List<Predicate<String>> tests = List.of();
        private int hidden;

        public Filters() {
            super("chat_filters", "Chat filters", "Hide lines you never want to see: adverts, vote reminders, join spam", Category.CHAT);
            keywords("mute", "block", "ignore", "spam", "hide", "regex");
        }

        public boolean hides(String line) {
            if (!phrases.get().equals(builtFrom)) build();
            for (Predicate<String> t : tests) {
                if (!t.test(line)) continue;
                hidden++;
                phrases.description = ABOUT + ". Hidden this session: " + hidden;
                return true;
            }
            return false;
        }

        private void build() {
            builtFrom = phrases.get();
            tests = new ArrayList<>();
            String s = builtFrom;
            int i = 0;
            while (i < s.length()) {
                while (i < s.length() && (s.charAt(i) == ',' || s.charAt(i) == ' ')) i++;
                if (i >= s.length()) break;
                int end;
                if (s.charAt(i) == '/') {
                    // An expression may hold commas of its own, so it runs to a slash that ends the entry.
                    end = i + 1;
                    while (end < s.length() && !(s.charAt(end) == '/' && (end + 1 == s.length() || s.substring(end + 1).stripLeading().startsWith(",")
                            || s.substring(end + 1).isBlank()))) end++;
                    if (end < s.length() && end > i + 1) {
                        try {
                            tests.add(Pattern.compile(s.substring(i + 1, end), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).asPredicate());
                        } catch (PatternSyntaxException ignored) {
                            // a broken expression hides nothing
                        }
                        i = end + 1;
                        continue;
                    }
                }
                end = s.indexOf(',', i);
                if (end < 0) end = s.length();
                String phrase = s.substring(i, end).strip().toLowerCase(Locale.ROOT);
                if (!phrase.isEmpty()) tests.add(line -> line.toLowerCase(Locale.ROOT).contains(phrase));
                i = end + 1;
            }
        }
    }

    public static final class NameColours extends Module {
        public final Settings.Num tint = num("tint", "Tint the message", 20, 0, 100, 5).suffix("%")
                .describe("How far the message's own colour is pulled towards the name's. 0 leaves it as the server sent it");
        public final Settings.Num saturation = num("saturation", "Saturation", 55, 20, 100, 5).suffix("%");
        public final Settings.Num brightness = num("brightness", "Brightness", 100, 50, 100, 5).suffix("%");
        public final Settings.Bool ownAccent = bool("own_accent", "Accent colour for your own name", true);

        public NameColours() {
            super("chat_name_colours", "Name colours", "Give every player's name its own colour, and tint what they say with it", Category.CHAT);
            keywords("color", "colour", "players", "tint", "rainbow");
        }

        public int colorOf(String name) {
            if (ownAccent.get() && name.equalsIgnoreCase(Nav.playerName())) return Theme.accent() & 0xFFFFFF;
            float hue = Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode() * 0x9E3779B9, 3600) / 3600f;
            return Colors.hsv(hue, saturation.get() / 100f, brightness.get() / 100f, 1f) & 0xFFFFFF;
        }

        public List<ChatText.Run> apply(List<ChatText.Run> runs, String line, ChatFormats.Line parsed) {
            int[] name = ChatFormats.nameIn(line, parsed);
            if (name == null) return runs;
            int rgb = colorOf(line.substring(name[0], name[1]));
            runs = ChatText.restyle(runs, name[0], name[1], s -> s.withColor(rgb));
            float t = tint.get() / 100f;
            if (t <= 0) return runs;
            return ChatText.restyle(runs, parsed.bodyStart(), line.length(),
                    s -> s.withColor(Colors.mix(ChatText.color(s) | 0xFF000000, rgb | 0xFF000000, t) & 0xFFFFFF));
        }
    }

    public static final class Smooth extends Module {
        public final Settings.Bool opening = bool("opening", "Ease in when chat opens", true);

        public Smooth() {
            super("chat_smooth", "Smooth chat", "New messages slide up into place instead of snapping in", Category.CHAT);
            keywords("animation", "animated", "slide");
        }
    }

    public static final class Look extends Module {
        public final Settings.Num background = num("background", "Background", 50, 0, 100, 5)
                .format(v -> v < 1 ? "None" : Math.round(v) + "%")
                .describe("Only the chat's: Minecraft's own option also changes name tags and subtitles");
        public final Settings.Bool hideIndicators = bool("hide_indicators", "Hide message indicators", true)
                .describe("The bar and icon Minecraft puts beside unsigned or modified messages");

        public Look() {
            super("chat_look", "Chat look", "A chat background of its own and no signing indicators", Category.CHAT);
            keywords("appearance", "background", "opacity", "transparent", "indicator", "secure");
        }
    }

    public static final class Unread extends Module {
        public final Settings.Bool divider = bool("divider", "Line above new messages", true);
        public final Settings.Bool button = bool("button", "Jump to latest button", true);

        public Unread() {
            super("chat_unread", "Unread marker", "Scrolled up? See what arrived since, and jump back down", Category.CHAT);
            keywords("new", "messages", "scroll", "latest", "bottom");
        }
    }

    public static final class Copy extends Module {
        public final Settings.Bool codes = bool("codes", "Shift keeps colour codes", true)
                .describe("Shift and right-click copies the message with & codes for its colours");

        public Copy() {
            super("chat_copy", "Copy messages", "Right-click a chat message to copy it", Category.CHAT);
            keywords("clipboard", "right click");
            onByDefault();
        }
    }

    public static final class Log extends Module {
        public final Settings.Bool hidden = bool("hidden", "Include filtered lines", true)
                .describe("Lines hidden by Chat filters are still kept, marked as hidden");

        public Log() {
            super("chat_log", "Chat log", "Keep each server's chat in dated files, with colours, for the history search", Category.CHAT);
            keywords("history", "record", "save", "file", "archive");
            onByDefault();
        }

        @Override
        protected void onDisable() {
            dev.aller.feature.ChatLog.close();
        }
    }

    public static final class Bubbles extends Module {
        public final Settings.Num duration = num("duration", "Stay for", 5f, 2f, 12f, 0.5f).suffix("s")
                .describe("Longer messages stay a little longer than this");
        public final Settings.Num range = num("range", "Within", 24f, 8f, 64f, 4f).suffix(" blocks");
        public final Settings.Num size = num("size", "Size", 1f, 0.6f, 1.6f, 0.05f).suffix("x");
        public final Settings.Bool own = bool("own", "Over your own head too", true).describe("Seen in third person");

        public Bubbles() {
            super("chat_bubbles", "Chat bubbles", "What players say appears over their heads, wherever you can see them", Category.CHAT);
            keywords("speech", "balloon", "overhead", "talk", "say", "head");
        }
    }

    public static final class Search extends Module {
        public final Settings.Key shortcut = add(new Settings.Key("shortcut", "Shortcut while chat is open",
                Settings.Key.pack(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)).chord());

        public Search() {
            super("chat_search", "Chat search", "Search everything ever said, back through old game logs, from the chat box", Category.CHAT);
            keywords("find", "history", "regex", "logs", "ctrl f");
            onByDefault();
        }
    }
}
