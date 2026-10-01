package dev.aller.screen.palette;

import dev.aller.feature.Session;
import dev.aller.platform.Canvas;
import dev.aller.ui.Colors;
import dev.aller.ui.Graph;
import dev.aller.ui.Theme;
import dev.aller.ui.font.Fonts;
import dev.aller.ui.widget.Scroll;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** The session dashboard: live counters and graphs for this session, and a history of past ones. */
public final class StatsPage extends Page {
    private static final SimpleDateFormat DAY = new SimpleDateFormat("d MMM, HH:mm");
    private final Scroll scroll = new Scroll();

    @Override
    public String title() {
        return "Session stats";
    }

    @Override
    public String subtitle() {
        return Session.active() ? Session.current().where : "Not in a world";
    }

    public static String duration(long seconds) {
        long h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        return h > 0 ? String.format("%dh %02dm", h, m) : String.format("%dm %02ds", m, s);
    }

    private static String distance(double blocks) {
        return blocks >= 1000 ? String.format("%.1f km", blocks / 1000) : Math.round(blocks) + " m";
    }

    @Override
    public void draw(Canvas c, float x, float y, float w, float h, float mx, float my, boolean hot) {
        float pad = 12, cw = w - pad * 2;
        List<Session.Summary> history = Session.history();
        int shown = Math.min(history.size(), 30);
        float liveH = Session.active() ? 46 + 6 + 46 + 6 + 62 + 14 : 40;
        float content = 8 + liveH + 14 + (shown == 0 ? 20 : 50 + shown * 20) + 8;
        float cy = y + 8 - scroll.update(content, h);

        if (Session.active()) {
            Session.Live live = Session.current();
            float[] fps = Session.recent(live.fps, 120);
            float[] ping = Session.recent(live.ping, 120);
            float avgFps = 0;
            for (float v : fps) avgFps += v;
            if (fps.length > 0) avgFps /= fps.length;
            String kd = live.deaths == 0 ? String.valueOf(live.kills) : String.format("%.2f", live.kills / (float) live.deaths);

            String[][] tiles = {
                    {"Time played", duration(Session.seconds())},
                    {"Average FPS", fps.length == 0 ? "–" : String.valueOf(Math.round(avgFps))},
                    {"Travelled", distance(live.distance)},
                    {"Hits landed", String.valueOf(live.hits)},
                    {"Kills / deaths", live.kills + " / " + live.deaths},
                    {"K/D", kd},
                    {"Peak CPS", String.valueOf(live.peakCps)},
                    {"Ping", ping.length == 0 || ping[ping.length - 1] <= 0 ? "–" : Math.round(ping[ping.length - 1]) + " ms"},
            };
            float gap = 6, tw = (cw - gap * 3) / 4;
            for (int i = 0; i < tiles.length; i++) {
                float tx = x + pad + (i % 4) * (tw + gap), ty = cy + (i / 4) * 52;
                tile(c, tx, ty, tw, 46, tiles[i][0], tiles[i][1], i == 0);
            }
            cy += 104;

            float gw = (cw - gap) / 2;
            chart(c, x + pad, cy, gw, 62, "FPS", "last " + fps.length + "s", fps, Theme.accent());
            chart(c, x + pad + gw + gap, cy, gw, 62, "Ping", ping.length == 0 ? "" : "ms", ping, Theme.SUCCESS);
            cy += 62 + 14;
        } else {
            c.rect(x + pad, cy, cw, 34, Theme.R_MD, 0x0AFFFFFF);
            c.textMiddle(Fonts.REGULAR, "Join a world to start a session. Stats are recorded while you play.",
                    x + pad + 10, cy, 34, 8f, Theme.TEXT_MUTED);
            cy += 40;
        }

        c.text(Fonts.SEMIBOLD, "History", x + pad + 2, cy, 9f, Theme.TEXT);
        c.textRight(Fonts.REGULAR, history.size() + (history.size() == 1 ? " session" : " sessions"), x + pad + cw - 2, cy + 1, 7.5f, Theme.TEXT_MUTED);
        cy += 14;
        if (shown == 0) {
            c.text(Fonts.REGULAR, "Sessions longer than 30 seconds will be listed here.", x + pad + 2, cy + 4, 8f, Theme.TEXT_MUTED);
            return;
        }
        // Playtime per session, oldest to newest, so trends are visible at a glance.
        float[] minutes = new float[shown];
        for (int i = 0; i < shown; i++) minutes[i] = history.get(history.size() - shown + i).seconds / 60f;
        c.rect(x + pad, cy, cw, 42, Theme.R_MD, 0x0AFFFFFF);
        Graph.bars(c, x + pad + 6, cy + 6, cw - 12, 30, minutes, Theme.accent(), shown - 1);
        cy += 50;
        for (int i = 0; i < shown; i++) {
            Session.Summary s = history.get(history.size() - 1 - i);
            float ry = cy + i * 20;
            if (ry > y + h || ry + 20 < y) continue;
            if (i % 2 == 0) c.rect(x + pad, ry, cw, 20, Theme.R_SM, 0x08FFFFFF);
            c.textMiddle(Fonts.MEDIUM, Fonts.MEDIUM.truncate(s.where, 8f, cw * 0.34f), x + pad + 8, ry, 20, 8f, Theme.TEXT);
            c.textMiddle(Fonts.REGULAR, DAY.format(new Date(s.started)), x + pad + cw * 0.38f, ry, 20, 7.5f, Theme.TEXT_MUTED);
            c.textMiddle(Fonts.REGULAR, duration(s.seconds), x + pad + cw * 0.62f, ry, 20, 7.5f, Theme.TEXT_DIM);
            String right = s.avgFps + " fps  ·  " + s.kills + "/" + s.deaths;
            c.textRight(Fonts.REGULAR, right, x + pad + cw - 8, ry + (20 - Fonts.REGULAR.height(7.5f)) / 2, 7.5f, Theme.TEXT_DIM);
        }
        scroll.drawBar(c, x + w - 5, y + 4, h - 8);
    }

    private static void tile(Canvas c, float x, float y, float w, float h, String label, String value, boolean accent) {
        c.rect(x, y, w, h, Theme.R_MD, accent ? Colors.withAlpha(Theme.accent(), 0.16f) : 0x0CFFFFFF);
        c.stroke(x, y, w, h, Theme.R_MD, 1, accent ? Colors.withAlpha(Theme.accent(), 0.35f) : Theme.BORDER);
        c.text(Fonts.REGULAR, label, x + 8, y + 8, 6.8f, Theme.TEXT_MUTED);
        c.text(Fonts.SEMIBOLD, Fonts.SEMIBOLD.truncate(value, 12f, w - 14), x + 8, y + 21, 12f, Theme.TEXT);
    }

    private static void chart(Canvas c, float x, float y, float w, float h, String label, String unit, float[] data, int color) {
        c.rect(x, y, w, h, Theme.R_MD, 0x0CFFFFFF);
        c.stroke(x, y, w, h, Theme.R_MD, 1, Theme.BORDER);
        c.text(Fonts.SEMIBOLD, label, x + 8, y + 7, 7.5f, Theme.TEXT_DIM);
        c.textRight(Fonts.REGULAR, unit, x + w - 8, y + 7.5f, 6.8f, Theme.TEXT_MUTED);
        if (data.length < 2) {
            c.textCentered(Fonts.REGULAR, "Collecting…", x + w / 2, y + h / 2, 7.5f, Theme.TEXT_MUTED);
        } else {
            Graph.line(c, x + 8, y + 20, w - 16, h - 28, data, color);
        }
    }

    @Override
    public boolean mouseScroll(float mx, float my, float amount) {
        scroll.scroll(amount);
        return true;
    }
}
