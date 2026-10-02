package dev.aller.feature;

import dev.aller.module.Modules;
import dev.aller.module.mods.ChatMods.Mentions;
import dev.aller.platform.Canvas;
import dev.aller.platform.ChatView;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.screen.ChatHistoryScreen;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Where the chat mods meet Minecraft's chat window. Every message passes through {@link #incoming}
 * on its way in; {@link #begin} and {@link #end} bracket the window's drawing for what is painted
 * under and over it; {@link #poll} watches the mouse and the search shortcut while chat is open.
 */
public final class Chat {
    /** Indicator labels that mark a line as a mention or a private message for {@link #begin}. */
    public static final String MENTION = "Mention", PRIVATE = "Message";

    /**
     * What the window should show for a message.
     *
     * @param bar         colour for the indicator bar beside it, or 0 to leave Minecraft's own
     * @param label       what the indicator is called when {@code bar} is set
     * @param noIndicator drop Minecraft's indicator
     */
    public record Incoming(Component text, int bar, String label, boolean noIndicator) {}

    private record Sent(List<String> parts, long at) {}

    private static final Deque<Sent> sent = new ArrayDeque<>();
    private static final Spring slide = Spring.snappy(0);
    private static float slideNow, slideAt = -1;
    private static boolean clipped, wasFocused;
    private static int unread, readLines;
    private static float chipX, chipY, chipW, chipH;
    private static boolean chipShown;
    private static final Spring chip = Spring.snappy(0);
    private static Component flashed;
    private static float flashedAt;
    private static boolean searchWasDown, rightWasDown, leftWasDown;

    private Chat() {}

    // ---- messages ------------------------------------------------------------------------------

    /** Runs a message through the chat mods; null means it is not shown at all. */
    public static Incoming incoming(Component raw) {
        List<ChatText.Run> runs = ChatText.runs(raw);
        String line = ChatText.plain(runs);
        ChatFormats.Line parsed = ChatFormats.parse(line);

        var filters = Modules.CHAT_FILTERS;
        if (filters.enabled() && !(filters.keepOwn.get() && parsed != null && !parsed.whisper() && mine(line, parsed)) && filters.hides(line)) {
            if (Modules.CHAT_LOG.enabled() && Modules.CHAT_LOG.hidden.get()) ChatLog.write(raw, String.valueOf(ChatLog.HIDDEN));
            return null;
        }

        Mentions mentions = Modules.MENTIONS;
        Mentions.Hit hit = mentions.enabled() ? mentions.check(line, parsed) : new Mentions.Hit(Mentions.Kind.NONE, List.of());
        boolean pinged = hit.kind() != Mentions.Kind.NONE;
        if (Modules.CHAT_LOG.enabled()) {
            ChatLog.write(raw, !pinged ? "" : String.valueOf(hit.kind() == Mentions.Kind.PRIVATE ? ChatLog.PRIVATE : ChatLog.MENTION));
        }

        int repeats = 1;
        if (Modules.CHAT_STACK.enabled()) repeats = Modules.CHAT_STACK.count(line);

        boolean restyled = false;
        if (Modules.NAME_COLOURS.enabled() && parsed != null) {
            runs = Modules.NAME_COLOURS.apply(runs, line, parsed);
            restyled = true;
        }
        if (pinged && mentions.recolour.get()) {
            int rgb = mentions.tint() & 0xFFFFFF;
            for (int[] r : hit.ranges()) runs = ChatText.restyle(runs, r[0], r[1], s -> s.withColor(rgb).withBold(true));
            restyled |= !hit.ranges().isEmpty();
        }
        // Left exactly as it came unless something was recoloured: rebuilding flattens it to plain styled text.
        Component out = restyled ? ChatText.join(runs) : raw;
        if (repeats > 1) {
            out = Component.empty().append(out)
                    .append(Component.literal(" (x" + repeats + ")").withStyle(s -> s.withColor(Theme.TEXT_MUTED & 0xFFFFFF).withBold(false)));
        }
        if (Modules.CHAT_TIMESTAMPS.enabled()) out = Modules.CHAT_TIMESTAMPS.stamp(out);

        if (pinged) mentions.alert(hit.kind(), line, parsed);
        arrived();
        boolean bar = pinged && mentions.highlight.get();
        return new Incoming(out, bar ? mentions.tint() & 0xFFFFFF | 0x01000000 : 0,
                hit.kind() == Mentions.Kind.PRIVATE ? PRIVATE : MENTION,
                Modules.CHAT_LOOK.enabled() && Modules.CHAT_LOOK.hideIndicators.get());
    }

    private static void arrived() {
        boolean reading = ChatView.focused() && ChatView.scroll() > 0;
        if (reading) unread++;
        else if (Modules.SMOOTH_CHAT.enabled() && !Motion.reduced()) kick(ChatView.lineHeight());
    }

    private static void kick(float distance) {
        slide.snap(Math.min(slide.get() + distance, ChatView.lineHeight() * 2f)).target(0);
    }

    /** Whether the author part of a line names the player. */
    public static boolean mine(String line, ChatFormats.Line parsed) {
        List<int[]> found = new ArrayList<>();
        find(line.substring(0, parsed.authorEnd()), Nav.playerName(), parsed.authorStart(), true, false, found);
        return !found.isEmpty();
    }

    /** Adds every place {@code word} occurs in {@code line} at or after {@code from}. */
    public static void find(String line, String word, int from, boolean wholeWord, boolean matchCase, List<int[]> out) {
        if (word.isEmpty()) return;
        int n = word.length();
        for (int i = Math.max(0, from); i + n <= line.length(); i++) {
            if (!line.regionMatches(!matchCase, i, word, 0, n)) continue;
            if (wholeWord) {
                // Only an edge that is itself a letter or digit needs a boundary: "@here" may follow anything.
                if (wordChar(word.charAt(0)) && i > 0 && wordChar(line.charAt(i - 1))) continue;
                if (wordChar(word.charAt(n - 1)) && i + n < line.length() && wordChar(line.charAt(i + n))) continue;
            }
            out.add(new int[] {i, i + n});
            i += n - 1;
        }
    }

    private static boolean wordChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    /** Called with whatever the player sends, chat line or command (without its slash). */
    public static void sent(String text, boolean command) {
        String s = normalise(text);
        List<String> parts = new ArrayList<>();
        if (!command) {
            parts.add(s);
        } else {
            // "msg Sam hello": the server may show back everything after the command, or only the message.
            int first = s.indexOf(' ');
            if (first > 0) {
                parts.add(s.substring(first + 1));
                int second = s.indexOf(' ', first + 1);
                if (second > 0) parts.add(s.substring(second + 1));
            }
        }
        parts.removeIf(String::isBlank);
        if (parts.isEmpty()) return;
        sent.addLast(new Sent(parts, System.currentTimeMillis()));
        while (sent.size() > 16) sent.removeFirst();
    }

    /** Whether a line is the server showing back something the player sent in the last few seconds. Each send answers for one line. */
    public static boolean echoed(String line, float seconds) {
        long oldest = System.currentTimeMillis() - (long) (seconds * 1000);
        String s = normalise(line);
        for (Iterator<Sent> it = sent.iterator(); it.hasNext(); ) {
            Sent each = it.next();
            if (each.at < oldest) {
                it.remove();
                continue;
            }
            for (String part : each.parts) {
                if (!s.contains(part)) continue;
                it.remove();
                return true;
            }
        }
        return false;
    }

    private static String normalise(String text) {
        return ChatText.strip(text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    // ---- drawing -------------------------------------------------------------------------------

    /** Before Minecraft draws the chat window: shifts it for the slide and paints the mention tint beneath. */
    public static void begin(Canvas c) {
        boolean focused = ChatView.focused();
        if (slideAt != Motion.time()) {
            slideAt = Motion.time();
            if (focused && !wasFocused && Modules.SMOOTH_CHAT.enabled() && Modules.SMOOTH_CHAT.opening.get() && !Motion.reduced()) kick(6);
            wasFocused = focused;
            slideNow = slide.update();
            if (!Modules.SMOOTH_CHAT.enabled()) slideNow = slide.snap(0).get();
            if (ChatView.scroll() == 0) {
                unread = 0;
                readLines = ChatView.lineCount();
            }
        }
        float s = ChatView.scale();
        c.push();
        clipped = slideNow > 0.05f;
        if (clipped) {
            // Lines slide up from under the window's bottom edge rather than over the hotbar.
            c.clip(0, 0, c.width(), ChatView.bottom() * s);
            c.translate(0, slideNow * s);
        }
        if (ChatView.hidden()) return;

        var mentions = Modules.MENTIONS;
        int lh = ChatView.lineHeight(), bottom = ChatView.bottom(), w = ChatView.width() + 12, scroll = ChatView.scroll();
        int shown = Math.min(ChatView.lineCount() - scroll, ChatView.linesPerPage());
        // Minecraft paints its black background over this, so the tint is made that much stronger first.
        float under = 1f - Math.min(0.75f, dev.aller.Hooks.chatBackground(Mc.mc().options.textBackgroundOpacity().get().floatValue()));
        float tint = Math.min(1f, mentions.strength.get() / 100f / under);
        float flash = flashed == null ? 0 : 1f - (Motion.time() - flashedAt) / 0.5f;
        if (flash <= 0) flashed = null;
        c.push();
        c.scale(s, 0, 0);
        for (int i = 0; i < shown; i++) {
            int index = i + scroll;
            float a = ChatView.alpha(index, focused);
            if (a < 0.01f) continue;
            float y = bottom - (i + 1) * lh;
            String label = ChatView.tagLabel(index);
            if (MENTION.equals(label) || PRIVATE.equals(label)) c.rect(0, y, w, lh, 0, Colors.withAlpha(ChatView.tagColor(index), tint * a));
            if (flashed != null && ChatView.message(index) == flashed) c.rect(0, y, w, lh, 0, Colors.withAlpha(Theme.accent(), 0.5f * flash));
        }
        c.pop();
    }

    /** After Minecraft has drawn the chat window: the unread divider and the jump-to-latest button. */
    public static void end(Canvas c) {
        boolean focused = ChatView.focused() && !ChatView.hidden();
        var marker = Modules.CHAT_UNREAD;
        int scroll = ChatView.scroll();
        int newLines = Math.max(0, ChatView.lineCount() - readLines);
        if (focused && marker.enabled() && marker.divider.get() && unread > 0 && newLines > 0) {
            int row = newLines - scroll;
            if (row > 0 && row <= Math.min(ChatView.lineCount() - scroll, ChatView.linesPerPage())) {
                float s = ChatView.scale();
                float y = ChatView.bottom() - row * ChatView.lineHeight();
                c.push();
                c.scale(s, 0, 0);
                c.rect(0, y - 0.5f, ChatView.width() + 12, 1, 0, Colors.withAlpha(Theme.accent(), 0.85f));
                c.pop();
            }
        }
        if (clipped) c.unclip();
        c.pop();

        boolean show = focused && marker.enabled() && marker.button.get() && unread > 0 && scroll > 0;
        float t = chip.target(show ? 1 : 0).update();
        chipShown = show;
        if (t < 0.02f) return;
        String label = unread + (unread == 1 ? " new message" : " new messages") + "  ↓";
        chipH = 13;
        chipW = Fonts.MEDIUM.width(label, 7.5f) + 20;
        chipX = 2;
        chipY = c.height() - 40 + 3 + (1 - t) * 4;
        boolean over = Mc.mouseX() >= chipX && Mc.mouseX() < chipX + chipW && Mc.mouseY() >= chipY && Mc.mouseY() < chipY + chipH;
        c.pushAlpha(Math.clamp(t, 0f, 1f));
        Theme.chip(c, chipX, chipY, chipW, chipH, chipH / 2, over ? Colors.mix(Theme.GLASS, Theme.accent(), 0.35f) : Theme.GLASS);
        c.circle(chipX + 8, chipY + chipH / 2, 2, Theme.accent());
        c.textMiddle(Fonts.MEDIUM, label, chipX + 14, chipY, chipH, 7.5f, Theme.TEXT);
        c.popAlpha();
    }

    // ---- input ---------------------------------------------------------------------------------

    /** Once a frame: the search shortcut, right-click to copy and the jump button, all only while chat is open. */
    public static void poll() {
        boolean open = Mc.screen() instanceof ChatScreen;
        long window = Mc.window();
        boolean search = open && Modules.CHAT_SEARCH.enabled() && Mc.isDown(Modules.CHAT_SEARCH.shortcut.get());
        boolean right = open && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean left = open && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (search && !searchWasDown) Mc.setScreen(new ScreenHost(new ChatHistoryScreen(null)));
        else if (right && !rightWasDown && Modules.CHAT_COPY.enabled()) copy();
        else if (left && !leftWasDown && chipShown && Mc.mouseX() >= chipX && Mc.mouseX() < chipX + chipW
                && Mc.mouseY() >= chipY && Mc.mouseY() < chipY + chipH) ChatView.resetScroll();
        searchWasDown = search;
        rightWasDown = right;
        leftWasDown = left;
    }

    private static void copy() {
        int line = ChatView.lineAt(Mc.mouseX(), Mc.mouseY());
        Component message = line < 0 ? null : ChatView.message(line);
        if (message == null) return;
        boolean codes = Mc.shiftDown() && Modules.CHAT_COPY.codes.get();
        String text = codes ? ChatText.encode(message).replace('§', '&') : message.getString();
        Mc.setClipboard(text);
        flashed = message;
        flashedAt = Motion.time();
        Toasts.info(codes ? "Copied with colour codes" : "Copied", text);
    }
}
