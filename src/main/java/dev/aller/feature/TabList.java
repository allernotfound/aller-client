package dev.aller.feature;

import dev.aller.feature.ChatText.Run;
import dev.aller.module.Modules;
import dev.aller.module.mods.UtilityMods.PlayerList;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Skins;
import dev.aller.ui.Colors;
import dev.aller.ui.StyledText;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The player list shown while Tab is held, in place of Minecraft's: faces, the names as the server
 * coloured them, its header and footer, the list score, ping, and a mark on players close enough
 * to be loaded on this side.
 */
public final class TabList {
    private static final float ROW = 12f, NAME = 7.5f, HEAD = 8f, PAD = 6f;
    /** Minecraft's own limit. */
    private static final int MAX = 80;

    private record Entry(PlayerInfo info, List<Run> name, float nameW, int ping, boolean self, boolean near, boolean spectator, String score) {}

    private static final Comparator<PlayerInfo> ORDER = Comparator.<PlayerInfo>comparingInt(p -> -p.getTabListOrder())
            .thenComparingInt(p -> p.getGameMode() == GameType.SPECTATOR ? 1 : 0)
            .thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName())
            .thenComparing(Game::profileName, String::compareToIgnoreCase);

    private static final Spring show = Spring.snappy(0);
    private static final List<Entry> entries = new ArrayList<>();
    private static List<List<Run>> header = List.of(), footer = List.of();
    private static int total, nearby;
    private static boolean hearts;
    private static float readAt = -1;

    private TabList() {}

    /** Reads the list a few times a second: names and scores do not change faster than that. */
    private static void read(PlayerList mod) {
        float now = Motion.time();
        if (readAt >= 0 && now >= readAt && now - readAt < 0.2f) return;
        readAt = now;
        entries.clear();
        nearby = 0;
        var connection = Mc.mc().getConnection();
        var level = Game.level();
        var self = Game.player();
        if (connection == null || level == null || self == null) return;
        List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
        infos.sort(ORDER);
        total = infos.size();
        Objective objective = mod.scores.get() ? level.getScoreboard().getDisplayObjective(DisplaySlot.LIST) : null;
        hearts = objective != null && objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS;
        for (PlayerInfo info : infos) {
            if (entries.size() >= MAX) break;
            List<Run> name = StyledText.lines(Game.tabNameStyled(info)).get(0);
            boolean me = Game.profileId(info).equals(self.getUUID());
            // Near means the server is sending this player's character: an invisible one is not given away.
            var body = me ? null : level.getPlayerByUUID(Game.profileId(info));
            boolean near = mod.nearby.get() && body != null && !body.isInvisible();
            if (near) nearby++;
            String score = "";
            if (objective != null) {
                var held = level.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(Game.profileName(info)), objective);
                if (held != null) score = Integer.toString(held.value());
            }
            entries.add(new Entry(info, name, StyledText.width(name, NAME), info.getLatency(), me, near, info.getGameMode() == GameType.SPECTATOR, score));
        }
        header = mod.headerFooter.get() ? lines(Game.tabHeader()) : List.of();
        footer = mod.headerFooter.get() ? lines(Game.tabFooter()) : List.of();
    }

    private static List<List<Run>> lines(Component text) {
        if (text == null) return List.of();
        List<List<Run>> lines = StyledText.lines(text);
        // Servers pad with blank lines for vanilla's layout; one at either end is plenty.
        while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        while (lines.size() > 1 && lines.get(0).isEmpty()) lines.remove(0);
        return lines.size() == 1 && lines.get(0).isEmpty() ? List.of() : lines;
    }

    /** Drawn over the HUD, in GUI units times the mod's own size. */
    public static void draw(Canvas c) {
        PlayerList mod = Modules.PLAYER_LIST;
        boolean wanted = mod.enabled() && (mod.always.get() || Mc.mc().options.keyPlayerList.isDown());
        float s = show.target(wanted ? 1 : 0).update();
        if (s < 0.02f) {
            readAt = -1;
            return;
        }
        if (wanted) read(mod);
        if (entries.isEmpty()) return;
        c.beginScale(mod.size.get());
        layout(c, mod, Math.clamp(s, 0f, 1f));
        c.endScale();
    }

    private static void layout(Canvas c, PlayerList mod, float s) {
        boolean heads = mod.heads.get();
        PlayerList.Ping ping = mod.ping.get();
        int perCol = mod.maxRows.asInt();
        int columns = Math.clamp((entries.size() + perCol - 1) / perCol, 1, 4);
        int rows = Math.min(perCol, (entries.size() + columns - 1) / columns);
        int shown = Math.min(entries.size(), columns * rows);

        float widestName = 40, widestScore = 0;
        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(i);
            widestName = Math.max(widestName, e.nameW);
            if (!e.score.isEmpty()) widestScore = Math.max(widestScore, Fonts.MEDIUM.width(e.score, 7f) + 6);
        }
        float pingW = ping == PlayerList.Ping.OFF ? 0 : ping == PlayerList.Ping.BARS ? 15 : 24;
        float lead = 6 + (heads ? HEAD + 4 : 0);
        float fixed = lead + widestScore + pingW + 5;
        // Too many long names for the screen: the names give way, never the rest.
        float room = (c.width() - 16 - PAD * 2 - (columns - 1) * 4) / columns;
        float nameW = Math.max(30, Math.min(widestName, room - fixed));
        float colW = fixed + nameW;

        float headerH = block(header), footerH = block(footer);
        float w = PAD * 2 + columns * colW + (columns - 1) * 4;
        for (List<Run> line : header) w = Math.max(w, StyledText.width(line, 7.5f) + PAD * 4);
        for (List<Run> line : footer) w = Math.max(w, StyledText.width(line, 7.5f) + PAD * 4);
        w = Math.min(w, c.width() - 16);
        float listX = (w - columns * colW - (columns - 1) * 4) / 2;
        float h = PAD + 13 + headerH + rows * ROW + footerH + PAD - 2;
        float x = (c.width() - w) / 2, y = 10 - 8 * (1 - s);

        c.pushAlpha(s);
        c.push();
        c.translate(x, y);
        Theme.chip(c, 0, 0, w, h, Theme.R_MD, 0xF2100E18);

        // What the list holds, at a glance.
        String count = total + (total == 1 ? " player" : " players");
        float cx = PAD + 2 + c.text(Fonts.SEMIBOLD, count, PAD + 2, PAD, 7f, Theme.TEXT_DIM);
        if (nearby > 0) {
            c.rect(cx + 7, PAD + 1.5f, 2, 6, 1, Theme.accent());
            c.text(Fonts.SEMIBOLD, nearby + " nearby", cx + 12, PAD, 7f, Theme.accent());
        }
        if (shown < total) c.textRight(Fonts.MEDIUM, "+" + (total - shown) + " more", w - PAD - 2, PAD, 7f, Theme.TEXT_MUTED);
        float top = PAD + 13;

        for (List<Run> line : header) {
            StyledText.draw(c, line, (w - StyledText.width(line, 7.5f)) / 2, top, 7.5f, Theme.TEXT);
            top += 10;
        }
        if (!header.isEmpty()) top += 4;

        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(i);
            float rx = listX + (i / rows) * (colW + 4), ry = top + (i % rows) * ROW;
            if (e.near) {
                c.rect(rx, ry + 0.5f, colW, ROW - 1, 3, Colors.withAlpha(Theme.accent(), 0.2f));
                c.rect(rx + 1, ry + 2.5f, 2, ROW - 5, 1, Theme.accent());
            } else {
                c.rect(rx, ry + 0.5f, colW, ROW - 1, 3, e.self ? 0x1CFFFFFF : 0x0AFFFFFF);
            }
            c.pushAlpha(e.spectator ? 0.55f : 1f);
            float tx = rx + 6;
            if (heads) {
                Skins.drawFace(c, e.info, tx, ry + (ROW - HEAD) / 2, (int) HEAD);
                tx += HEAD + 4;
            }
            boolean cut = e.nameW > nameW;
            if (cut) c.clip(tx, ry, nameW, ROW);
            StyledText.draw(c, e.name, tx, ry + (ROW - Fonts.MEDIUM.height(NAME)) / 2, NAME, Theme.TEXT);
            if (cut) c.unclip();
            float right = rx + colW - 5;
            if (ping == PlayerList.Ping.BARS) {
                bars(c, right - 10, ry + 2.5f, e.ping);
                right -= pingW;
            } else if (ping == PlayerList.Ping.NUMBERS) {
                c.textRight(Fonts.MEDIUM, e.ping <= 0 ? "-" : Integer.toString(Math.min(e.ping, 9999)), right,
                        ry + (ROW - Fonts.MEDIUM.height(6.5f)) / 2, 6.5f, grade(e.ping));
                right -= pingW;
            }
            if (!e.score.isEmpty()) {
                c.textRight(Fonts.MEDIUM, e.score, right - 2, ry + (ROW - Fonts.MEDIUM.height(7f)) / 2, 7f, hearts ? Theme.DANGER : Theme.WARN);
            }
            c.popAlpha();
        }
        top += rows * ROW;

        if (!footer.isEmpty()) top += 4;
        for (List<Run> line : footer) {
            StyledText.draw(c, line, (w - StyledText.width(line, 7.5f)) / 2, top, 7.5f, Theme.TEXT);
            top += 10;
        }
        c.pop();
        c.popAlpha();
    }

    private static float block(List<List<Run>> lines) {
        return lines.isEmpty() ? 0 : lines.size() * 10 + 4;
    }

    private static int grade(int ping) {
        return ping <= 0 ? Theme.TEXT_MUTED : ping < 80 ? Theme.SUCCESS : ping < 150 ? Colors.mix(Theme.SUCCESS, Theme.WARN, 0.5f) : ping < 300 ? Theme.WARN : Theme.DANGER;
    }

    /** Four signal bars, coloured by latency. */
    private static void bars(Canvas c, float x, float y, int ping) {
        int level = ping < 0 ? 0 : ping < 80 ? 4 : ping < 150 ? 3 : ping < 300 ? 2 : 1;
        int color = level >= 3 ? Theme.SUCCESS : level == 2 ? Theme.WARN : Theme.DANGER;
        for (int i = 0; i < 4; i++) {
            float bh = 2 + i * 1.6f;
            c.rect(x + i * 2.6f, y + 7 - bh, 1.7f, bh, 0.5f, i < level ? color : 0x33FFFFFF);
        }
    }
}
