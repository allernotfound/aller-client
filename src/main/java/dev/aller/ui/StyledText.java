package dev.aller.ui;

import dev.aller.feature.ChatText;
import dev.aller.feature.ChatText.Run;
import dev.aller.platform.Canvas;
import dev.aller.ui.font.Fonts;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Text a server coloured (a name in the tab list, its header), drawn in Aller Client's font: each
 * run keeps its colour and weight, and characters Inter lacks fall back to Minecraft's font.
 */
public final class StyledText {
    private StyledText() {}

    /** The text as lines of styled runs, split where it has line breaks. */
    public static List<List<Run>> lines(Component text) {
        List<List<Run>> out = new ArrayList<>();
        List<Run> line = new ArrayList<>();
        for (Run run : ChatText.runs(text)) {
            String rest = run.text();
            for (int at; (at = rest.indexOf('\n')) >= 0; rest = rest.substring(at + 1)) {
                if (at > 0) line.add(new Run(rest.substring(0, at), run.style()));
                out.add(line);
                line = new ArrayList<>();
            }
            if (!rest.isEmpty()) line.add(new Run(rest, run.style()));
        }
        out.add(line);
        return out;
    }

    private static Fonts font(Run run) {
        return run.style().isBold() ? Fonts.BOLD : Fonts.MEDIUM;
    }

    public static float width(List<Run> runs, float size) {
        float w = 0;
        for (Run run : runs) w += font(run).widthAny(run.text(), size);
        return w;
    }

    /** @param plain the colour of runs the server gave none */
    public static void draw(Canvas c, List<Run> runs, float x, float y, float size, int plain) {
        for (Run run : runs) {
            var colour = run.style().getColor();
            int color = colour == null ? plain : 0xFF000000 | colour.getValue();
            float w = c.textAny(font(run), run.text(), x, y, size, color);
            if (run.style().isStrikethrough()) c.rect(x, y + size * 0.62f, w, 0.8f, 0, color);
            if (run.style().isUnderlined()) c.rect(x, y + size * 1.12f, w, 0.8f, 0, color);
            x += w;
        }
    }
}
