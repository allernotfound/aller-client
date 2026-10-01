package dev.aller.module.mods;

import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.module.Modules;
import dev.aller.setting.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ChatMods {
    private ChatMods() {}

    public static final class Timestamps extends Module {
        public final Settings.Bool twentyFour = bool("24h", "24-hour clock", true);
        public final Settings.Bool seconds = bool("seconds", "Show seconds", false);

        public Timestamps() {
            super("chat_timestamps", "Chat timestamps", "Prefix every chat message with the time it arrived", Category.CHAT);
            keywords("time", "messages");
        }

        Component stamp(Component message) {
            String pattern = (twentyFour.get() ? "HH:mm" : "h:mm") + (seconds.get() ? ":ss" : "");
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern));
            return Component.empty()
                    .append(Component.literal("[" + time + "] ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(message);
        }
    }

    /** Applied to every message as it enters the chat history. */
    public static Component decorate(Component message) {
        return Modules.CHAT_TIMESTAMPS.enabled() ? Modules.CHAT_TIMESTAMPS.stamp(message) : message;
    }
}
