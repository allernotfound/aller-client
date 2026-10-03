package dev.aller.feature;

import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Speech bubbles over the heads of players who say something in chat.
 *
 * <p>A server's chat is formatted text that does not say who wrote it, so the author is found the
 * way the other chat mods find one ({@link ChatFormats}), and then matched to somebody in the tab
 * list by their account name or the nickname shown there. Where the server's format is not one
 * that is known, the start of the line is searched for an online player's name followed by a
 * separator.
 *
 * <p>A bubble is only drawn where the player it belongs to can be seen: in range, not invisible,
 * and with nothing between. It never shows where somebody is.
 */
public final class Bubbles {
    private static final float SIZE = 7.5f, WRAP = 132f, LINE = 9.5f;
    private static final int MAX_LINES = 3, MAX_CHARS = 140;
    /** What may stand between a name and what was said, when no format matched. */
    private static final String SEPARATORS = ":>»›→➜➤|";

    private static final class Bubble {
        final List<String> lines = new ArrayList<>();
        float width;
        long born, ends;
    }

    private static final Map<UUID, Bubble> bubbles = new HashMap<>();

    private Bubbles() {}

    /** A chat line as it arrives, with what {@link ChatFormats} made of it (null if nothing). */
    public static void feed(String line, ChatFormats.Line parsed) {
        var mod = Modules.CHAT_BUBBLES;
        var connection = Mc.mc().getConnection();
        if (!mod.enabled() || connection == null || Game.level() == null) return;
        PlayerInfo author = null;
        String body = null;
        if (parsed != null && parsed.authorEnd() > parsed.authorStart()) {
            author = named(connection.getOnlinePlayers(), line.substring(parsed.authorStart(), parsed.authorEnd()));
            if (author != null) body = line.substring(Math.min(parsed.bodyStart(), line.length()));
        }
        if (author == null) {
            // No format fits this server: look for "Name:" near the start of the line.
            String head = line.substring(0, Math.min(line.length(), 64));
            int best = Integer.MAX_VALUE;
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                for (String name : names(info)) {
                    int at = word(head, name);
                    if (at < 0 || at >= best) continue;
                    int after = separator(line, at + name.length());
                    if (after < 0) continue;
                    best = at;
                    author = info;
                    body = line.substring(after);
                }
            }
        }
        if (author == null || body == null) return;
        String text = body.strip();
        if (text.isEmpty()) return;
        if (!mod.own.get() && Game.profileId(author).equals(Game.player().getUUID())) return;
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS).stripTrailing() + "…";

        Bubble bubble = new Bubble();
        List<String> wrapped = Fonts.MEDIUM.wrap(text, SIZE, WRAP);
        for (int i = 0; i < wrapped.size() && i < MAX_LINES; i++) {
            String each = wrapped.get(i);
            if (i == MAX_LINES - 1 && wrapped.size() > MAX_LINES) each = each.stripTrailing() + "…";
            bubble.lines.add(each);
            bubble.width = Math.max(bubble.width, Fonts.MEDIUM.widthAny(each, SIZE));
        }
        bubble.born = System.currentTimeMillis();
        // Longer messages stay a little longer.
        bubble.ends = bubble.born + (long) ((mod.duration.get() + text.length() * 0.04f) * 1000);
        bubbles.put(Game.profileId(author), bubble);
    }

    /** The names a tab list entry may go by in chat: the account's, and the last word of what the server shows for it. */
    private static List<String> names(PlayerInfo info) {
        List<String> out = new ArrayList<>(2);
        String account = Game.profileName(info);
        if (account != null && account.length() >= 2) out.add(account);
        if (info.getTabListDisplayName() != null) {
            String shown = ChatText.strip(info.getTabListDisplayName().getString());
            int end = shown.length();
            while (end > 0 && !nameChar(shown.charAt(end - 1))) end--;
            int start = end;
            while (start > 0 && nameChar(shown.charAt(start - 1))) start--;
            if (end - start >= 3 && !shown.substring(start, end).equalsIgnoreCase(account)) out.add(shown.substring(start, end));
        }
        return out;
    }

    /** Whoever in the list the author part of a line names; the longest name wins, so "Sam" does not take "Samuel"'s line. */
    private static PlayerInfo named(Iterable<PlayerInfo> players, String author) {
        PlayerInfo found = null;
        int longest = 0;
        for (PlayerInfo info : players) {
            for (String name : names(info)) {
                if (name.length() > longest && word(author, name) >= 0) {
                    found = info;
                    longest = name.length();
                }
            }
        }
        return found;
    }

    /** Where {@code name} stands as a whole word in {@code text}, ignoring case; -1 if it does not. */
    private static int word(String text, String name) {
        int n = name.length();
        for (int i = 0; i + n <= text.length(); i++) {
            if (!text.regionMatches(true, i, name, 0, n)) continue;
            if (i > 0 && nameChar(text.charAt(i - 1))) continue;
            if (i + n < text.length() && nameChar(text.charAt(i + n))) continue;
            return i;
        }
        return -1;
    }

    private static boolean nameChar(char c) {
        return c == '_' || c < 128 && Character.isLetterOrDigit(c);
    }

    /** Where the message starts if a separator follows the name at {@code from} (after any closing bracket); -1 if none does. */
    private static int separator(String line, int from) {
        int i = from;
        while (i < line.length() && (line.charAt(i) == ' ' || "])}".indexOf(line.charAt(i)) >= 0)) i++;
        if (i >= line.length() || SEPARATORS.indexOf(line.charAt(i)) < 0) return -1;
        while (i < line.length() && SEPARATORS.indexOf(line.charAt(i)) >= 0) i++;
        // "Sam: hello" is chat; "Sam:hello" and "12:30" are not.
        return i < line.length() && line.charAt(i) == ' ' ? i + 1 : -1;
    }

    /** Drawn on the HUD layer, each over its player's head. */
    public static void draw(Canvas c) {
        if (bubbles.isEmpty()) return;
        var mod = Modules.CHAT_BUBBLES;
        var level = Game.level();
        var self = Game.player();
        if (!mod.enabled() || level == null || self == null) {
            bubbles.clear();
            return;
        }
        long now = System.currentTimeMillis();
        float partial = Game.partialTick();
        boolean firstPerson = Mc.mc().options.getCameraType().isFirstPerson();
        float range = mod.range.get();
        for (Iterator<Map.Entry<UUID, Bubble>> it = bubbles.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Bubble bubble = entry.getValue();
            if (now >= bubble.ends) {
                it.remove();
                continue;
            }
            Player speaker = level.getPlayerByUUID(entry.getKey());
            if (speaker == null || !speaker.isAlive() || speaker.isInvisible()) continue;
            boolean own = speaker == self;
            if (own && firstPerson) continue;
            double distance = View.camera().position().distanceTo(speaker.getEyePosition(partial));
            if (distance > range || !own && !self.hasLineOfSight(speaker)) continue;

            Vec3 at = speaker.getPosition(partial);
            float[] p = View.project(at.x, at.y + speaker.getBbHeight() + 0.85, at.z, c.width(), c.height());
            if (p[2] <= 0.1f) continue;

            float age = (now - bubble.born) / 1000f, left = (bubble.ends - now) / 1000f;
            float alpha = Math.clamp(age / 0.15f, 0f, 1f) * Math.clamp(left / 0.4f, 0f, 1f);
            // Smaller with distance, but never too small to read.
            float size = mod.size.get() * Math.clamp(1.15f - (float) distance / range * 0.55f, 0.6f, 1.15f) * (0.9f + 0.1f * Math.clamp(age / 0.15f, 0f, 1f));
            float w = bubble.width + 12, h = bubble.lines.size() * LINE + 7;
            c.push();
            c.translate(p[0], p[1]);
            c.scale(size, 0, 0);
            c.pushAlpha(alpha);
            int fill = 0xE6100E18;
            c.shadow(-w / 2, -h - 4 + 2, w, h, 6, 8, 0x55000000);
            c.rect(-w / 2, -h - 4, w, h, 6, fill);
            c.stroke(-w / 2, -h - 4, w, h, 6, 1, Colors.withAlpha(Theme.accent(), 0.55f));
            c.push();
            c.rotate((float) Math.PI, 0, -3.2f);
            c.polygon(0, -3.2f, 3.4f, 3, 0.5f, fill);
            c.pop();
            float y = -h - 4 + 3.5f;
            for (String each : bubble.lines) {
                c.textAny(Fonts.MEDIUM, each, -Fonts.MEDIUM.widthAny(each, SIZE) / 2, y, SIZE, Theme.TEXT);
                y += LINE;
            }
            c.popAlpha();
            c.pop();
        }
    }
}
