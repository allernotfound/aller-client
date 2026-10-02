package dev.aller.hud.elements;

import dev.aller.hud.HudModule;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.setting.Settings;
import dev.aller.ui.Theme;
import dev.aller.ui.font.Fonts;

public final class Coordinates extends HudModule {
    public enum Layout { VERTICAL, HORIZONTAL }

    public final Settings.Choice<Layout> layout = choice("layout", "Layout", Layout.VERTICAL);
    public final Settings.Bool decimals = bool("decimals", "Decimals", false);
    public final Settings.Bool otherDimension = bool("other_dimension", "Show Nether/Overworld equivalent", false);
    public final Settings.Color textColor = color("text_color", "Text colour", 0xFFF5F3FB);

    private static final float SIZE = 8f, PAD = 6f, LINE = 11f;
    private final String[] labels = {"X", "Y", "Z", ""};
    private final String[] values = new String[4];
    private int lines;

    public Coordinates() {
        super("coordinates", "Coordinates", "Your position in the world", AnchorH.LEFT, AnchorV.TOP, 6, 26);
        keywords("xyz", "position", "coords", "location");
        onByDefault();
    }

    private String fmt(double v) {
        return decimals.get() ? String.format("%.1f", v) : Integer.toString((int) Math.floor(v));
    }

    @Override
    protected boolean measure(boolean editing) {
        var p = Game.player();
        values[0] = fmt(p.getX());
        values[1] = fmt(p.getY());
        values[2] = fmt(p.getZ());
        lines = 3;
        if (otherDimension.get()) {
            String dim = Game.dimensionId();
            // One Nether block is eight Overworld blocks.
            if (dim.equals("the_nether")) {
                labels[3] = "OW";
                values[3] = fmt(p.getX() * 8) + ", " + fmt(p.getZ() * 8);
                lines = 4;
            } else if (dim.equals("overworld")) {
                labels[3] = "NE";
                values[3] = fmt(p.getX() / 8) + ", " + fmt(p.getZ() / 8);
                lines = 4;
            }
        }
        if (layout.get() == Layout.HORIZONTAL) {
            float total = PAD * 2;
            for (int i = 0; i < lines; i++) {
                total += Fonts.MEDIUM.width(labels[i], SIZE * 0.82f) + 3 + Fonts.SEMIBOLD.width(values[i], SIZE) + (i < lines - 1 ? 8 : 0);
            }
            w = total;
            h = Fonts.SEMIBOLD.height(SIZE) + 8;
        } else {
            float widest = 0;
            for (int i = 0; i < lines; i++) widest = Math.max(widest, Fonts.SEMIBOLD.width(values[i], SIZE));
            w = Math.max(54, PAD * 2 + 14 + widest);
            h = PAD * 2 - 2 + lines * LINE;
        }
        return true;
    }

    @Override
    protected void render(Canvas c, boolean editing) {
        chip(c, layout.get() == Layout.HORIZONTAL ? h / 2.6f : Theme.R_MD);
        if (layout.get() == Layout.HORIZONTAL) {
            float x = PAD;
            for (int i = 0; i < lines; i++) {
                x += c.textMiddle(Fonts.MEDIUM, labels[i], x, 0.3f, h, SIZE * 0.82f, Theme.accent()) + 3;
                x += c.textMiddle(Fonts.SEMIBOLD, values[i], x, 0, h, SIZE, textColor.get()) + 8;
            }
        } else {
            float y = PAD - 1;
            for (int i = 0; i < lines; i++) {
                c.textMiddle(Fonts.MEDIUM, labels[i], PAD, y + 0.3f, LINE, SIZE * 0.82f, Theme.accent());
                c.textMiddle(Fonts.SEMIBOLD, values[i], PAD + 14, y, LINE, SIZE, textColor.get());
                y += LINE;
            }
        }
    }
}
