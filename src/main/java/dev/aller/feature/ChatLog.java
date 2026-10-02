package dev.aller.feature;

import dev.aller.AllerClient;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import net.minecraft.network.chat.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Aller's own record of chat: one folder per server or world, one file per day, one line per
 * message as "time, flags, text" separated by tabs. Unlike the game's log it keeps colours and
 * knows which server a line came from. {@link ChatArchive} reads it back.
 */
public final class ChatLog {
    /** Flags: a mention, a private message, a line a filter hid. */
    public static final char MENTION = 'm', PRIVATE = 'p', HIDDEN = 'h';
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static BufferedWriter out;
    private static String openKey;
    private static LocalDate openDate;
    private static boolean failed;

    private ChatLog() {}

    public static Path dir() {
        return Mc.mc().gameDirectory.toPath().resolve("aller-chat");
    }

    public static void write(Component message, String flags) {
        if (failed) return;
        String key = Game.worldKey();
        LocalDate today = LocalDate.now();
        try {
            if (out == null || !key.equals(openKey) || !today.equals(openDate)) {
                close();
                Path folder = dir().resolve(key);
                Files.createDirectories(folder);
                out = Files.newBufferedWriter(folder.resolve(today + ".log"), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                openKey = key;
                openDate = today;
            }
            out.write(LocalTime.now().format(TIME));
            out.write('\t');
            out.write(flags.isEmpty() ? "-" : flags);
            out.write('\t');
            out.write(ChatText.encode(message).replace('\t', ' '));
            out.newLine();
            // Written through at once, so the history search sees the line and a crash loses nothing.
            out.flush();
        } catch (IOException e) {
            failed = true;
            AllerClient.LOG.warn("Chat log stopped: could not write to {}", dir(), e);
        }
    }

    public static void close() {
        if (out == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
            // nothing left to do with it
        }
        out = null;
    }
}
