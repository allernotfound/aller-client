package dev.aller.platform;

import dev.aller.mixin.ChatComponentAccessor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
//? if <26.1 {
/*import net.minecraft.client.GuiMessage;
*///?} else {
import net.minecraft.client.multiplayer.chat.GuiMessage;
//?}

import java.util.List;

/**
 * Minecraft's chat window as chat mods need to see it: its wrapped lines (index 0 is the newest),
 * how far it is scrolled, and where each line sits on screen. Positions are in chat units, which
 * are GUI pixels divided by the chat size option.
 */
public final class ChatView {
    private ChatView() {}

    public static ChatComponent chat() {
        //? if <26.1 {
        /*return Mc.mc().gui.getChat();
        *///?} else {
        return Mc.mc().gui.hud.getChat();
        //?}
    }

    private static ChatComponentAccessor access() {
        return (ChatComponentAccessor) chat();
    }

    private static List<GuiMessage.Line> lines() {
        return access().aller$lines();
    }

    public static int lineCount() {
        return lines().size();
    }

    public static int messageCount() {
        return access().aller$messages().size();
    }

    /** How many lines the view is scrolled up from the newest. */
    public static int scroll() {
        return access().aller$scroll();
    }

    public static void resetScroll() {
        chat().resetChatScroll();
    }

    public static boolean focused() {
        return chat().isChatFocused();
    }

    public static boolean hidden() {
        return Mc.mc().options.chatVisibility().get() == ChatVisiblity.HIDDEN;
    }

    public static float scale() {
        return Mc.mc().options.chatScale().get().floatValue();
    }

    /** Width of the text column. */
    public static int width() {
        return Mth.ceil(ChatComponent.getWidth(Mc.mc().options.chatWidth().get()) / scale());
    }

    public static int lineHeight() {
        return (int) (9.0 * (Mc.mc().options.chatLineSpacing().get() + 1.0));
    }

    public static int linesPerPage() {
        return chat().getLinesPerPage();
    }

    /** The bottom edge of the newest visible line. */
    public static int bottom() {
        return Mth.floor((Mc.mc().getWindow().getGuiScaledHeight() - 40) / scale());
    }

    private static int ticks() {
        //? if <26.1 {
        /*return Mc.mc().gui.getGuiTicks();
        *///?} else {
        return Mc.mc().gui.hud.getGuiTicks();
        //?}
    }

    /** How visible a line is: old lines fade out unless the chat is open. */
    public static float alpha(int line, boolean focused) {
        if (focused) return 1f;
        double t = Mth.clamp((1.0 - (ticks() - lines().get(line).addedTime()) / 200.0) * 10.0, 0.0, 1.0);
        return (float) (t * t);
    }

    /** The label of the indicator beside a line ("Not Secure", or one of Aller's), or null. */
    public static String tagLabel(int line) {
        var tag = lines().get(line).tag();
        return tag == null ? null : tag.logTag();
    }

    public static int tagColor(int line) {
        var tag = lines().get(line).tag();
        return tag == null ? 0 : tag.indicatorColor();
    }

    /** The line under a point on screen while the chat is open, or -1. */
    public static int lineAt(float mouseX, float mouseY) {
        if (!focused() || hidden()) return -1;
        float s = scale();
        double x = mouseX / s - 4.0;
        if (x < -4.0 || x > Mth.floor(ChatComponent.getWidth(Mc.mc().options.chatWidth().get()) / s)) return -1;
        double row = (Mc.mc().getWindow().getGuiScaledHeight() - mouseY - 40.0) / (s * lineHeight());
        if (row < 0 || row >= Math.min(linesPerPage(), lineCount())) return -1;
        int index = Mth.floor(row + scroll());
        return index < lineCount() ? index : -1;
    }

    /** The first and last line (inclusive) of the message a line belongs to. */
    public static int[] span(int line) {
        List<GuiMessage.Line> lines = lines();
        int first = line, last = line;
        while (first > 0 && !lines.get(first).endOfEntry()) first--;
        while (last + 1 < lines.size() && !lines.get(last + 1).endOfEntry()) last++;
        return new int[] {first, last};
    }

    /** The whole message a line was wrapped from. */
    public static Component message(int line) {
        List<GuiMessage.Line> lines = lines();
        if (line < 0 || line >= lines.size()) return null;
        //? if <26.1 {
        /*// Lines carry no link to their message here; both lists run newest first, so count entries.
        int ordinal = -1;
        for (int i = 0; i <= line; i++) if (lines.get(i).endOfEntry()) ordinal++;
        List<GuiMessage> all = access().aller$messages();
        return ordinal >= 0 && ordinal < all.size() ? all.get(ordinal).content() : null;
        *///?} else {
        return lines.get(line).parent().content();
        //?}
    }

    /** Takes the newest message back out of the window, for a repeat that replaces it. */
    public static void removeNewest() {
        List<GuiMessage> all = access().aller$messages();
        List<GuiMessage.Line> lines = lines();
        if (all.isEmpty()) return;
        all.remove(0);
        if (lines.isEmpty()) return;
        lines.remove(0);
        while (!lines.isEmpty() && !lines.get(0).endOfEntry()) lines.remove(0);
    }

    /** Shows a line only this client sees. */
    public static void add(Component message) {
        //? if <26.1 {
        /*chat().addMessage(message);
        *///?} else {
        chat().addClientSystemMessage(message);
        //?}
    }
}
