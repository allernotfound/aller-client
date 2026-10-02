package dev.aller.feature;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * A chat message as a flat list of styled runs, so parts of it can be recoloured by character
 * position, and the one-line form of it kept in the chat log: formatting codes, with
 * {@code §#RRGGBB} for colours that have no code of their own.
 */
public final class ChatText {
    private static final Pattern CODES = Pattern.compile("§(#[0-9A-Fa-f]{6}|.)");

    public record Run(String text, Style style) {}

    private ChatText() {}

    public static List<Run> runs(Component message) {
        List<Run> out = new ArrayList<>();
        message.visit((style, text) -> {
            if (!text.isEmpty()) out.add(new Run(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** The text the runs spell out; positions in it are what {@link #restyle} takes. */
    public static String plain(List<Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (Run r : runs) sb.append(r.text());
        return sb.toString();
    }

    public static MutableComponent join(List<Run> runs) {
        MutableComponent out = Component.empty();
        for (Run r : runs) out.append(Component.literal(r.text()).setStyle(r.style()));
        return out;
    }

    /** Changes the style of the characters from {@code from} to {@code to}, splitting runs where needed. */
    public static List<Run> restyle(List<Run> runs, int from, int to, UnaryOperator<Style> change) {
        List<Run> out = new ArrayList<>(runs.size() + 2);
        int at = 0;
        for (Run r : runs) {
            int end = at + r.text().length();
            int a = Math.clamp(from, at, end) - at, b = Math.clamp(to, at, end) - at;
            if (a >= b) {
                out.add(r);
            } else {
                if (a > 0) out.add(new Run(r.text().substring(0, a), r.style()));
                out.add(new Run(r.text().substring(a, b), change.apply(r.style())));
                if (b < r.text().length()) out.add(new Run(r.text().substring(b), r.style()));
            }
            at = end;
        }
        return out;
    }

    /** A style's colour, or white where the server set none. */
    public static int color(Style style) {
        TextColor c = style.getColor();
        return c == null ? 0xFFFFFF : c.getValue();
    }

    public static String encode(Component message) {
        StringBuilder sb = new StringBuilder();
        Style last = Style.EMPTY;
        for (Run r : runs(message)) {
            Style s = r.style();
            if (!same(s, last)) {
                sb.append("§r");
                TextColor c = s.getColor();
                if (c != null) {
                    int named = named(c.getValue());
                    if (named >= 0) sb.append('§').append(Character.forDigit(named, 16));
                    else sb.append(String.format("§#%06X", c.getValue()));
                }
                if (s.isBold()) sb.append("§l");
                if (s.isItalic()) sb.append("§o");
                if (s.isUnderlined()) sb.append("§n");
                if (s.isStrikethrough()) sb.append("§m");
                if (s.isObfuscated()) sb.append("§k");
                last = s;
            }
            sb.append(r.text().replace("\r", "").replace("\n", "\\n"));
        }
        return sb.toString();
    }

    private static boolean same(Style a, Style b) {
        return java.util.Objects.equals(a.getColor(), b.getColor()) && a.isBold() == b.isBold() && a.isItalic() == b.isItalic()
                && a.isUnderlined() == b.isUnderlined() && a.isStrikethrough() == b.isStrikethrough() && a.isObfuscated() == b.isObfuscated();
    }

    /** The sixteen colours that have a code, in code order (0 to f). */
    private static final int[] NAMED = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private static int named(int rgb) {
        for (int i = 0; i < NAMED.length; i++) if (NAMED[i] == rgb) return i;
        return -1;
    }

    public static Component decode(String coded) {
        MutableComponent out = Component.empty();
        StringBuilder text = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < coded.length(); i++) {
            char ch = coded.charAt(i);
            if (ch != '§' || i + 1 >= coded.length()) {
                text.append(ch);
                continue;
            }
            Style next = style;
            char code = coded.charAt(i + 1);
            int skip = 1;
            if (code == '#' && i + 8 <= coded.length()) {
                try {
                    next = Style.EMPTY.withColor(Integer.parseInt(coded.substring(i + 2, i + 8), 16));
                    skip = 7;
                } catch (NumberFormatException e) {
                    // not a colour after all: drop the two characters like any unknown code
                }
            } else {
                ChatFormatting f = ChatFormatting.getByCode(code);
                if (f != null) next = style.applyLegacyFormat(f);
            }
            if (!text.isEmpty()) {
                out.append(Component.literal(text.toString()).setStyle(style));
                text.setLength(0);
            }
            style = next;
            i += skip;
        }
        if (!text.isEmpty()) out.append(Component.literal(text.toString()).setStyle(style));
        return out;
    }

    public static String strip(String coded) {
        return coded.indexOf('§') < 0 ? coded : CODES.matcher(coded).replaceAll("");
    }
}
