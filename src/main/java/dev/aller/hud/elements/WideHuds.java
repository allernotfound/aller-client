package dev.aller.hud.elements;

import dev.aller.feature.Session;
import dev.aller.feature.Waypoints;
import dev.aller.hud.HudModule;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import dev.aller.ui.Colors;
import dev.aller.ui.Graph;
import dev.aller.ui.Theme;
import dev.aller.ui.anim.Spring;
import dev.aller.ui.font.Fonts;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The larger HUD elements: compass strip, live graph and player list. */
public final class WideHuds {
    private WideHuds() {}

    public static final class Compass extends HudModule {
        public final Settings.Num width = num("width", "Width", 190f, 100f, 360f, 10f).suffix("px");
        public final Settings.Bool waypoints = bool("waypoints", "Show waypoints", true);
        public final Settings.Bool degrees = bool("degrees", "Show heading", true);
        private static final String[] CARDINALS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        /** Degrees visible across the strip. */
        private static final float SPAN = 150f;

        public Compass() {
            super("compass", "Compass", "A heading strip with cardinal points and your waypoints", AnchorH.CENTER, AnchorV.TOP, 0, 6);
            keywords("heading", "direction", "bearing", "navigation");
        }

        @Override
        protected boolean measure(boolean editing) {
            w = width.get();
            h = degrees.get() ? 26 : 18;
            return true;
        }

        private static float wrap(float deg) {
            deg %= 360;
            if (deg >= 180) deg -= 360;
            if (deg < -180) deg += 360;
            return deg;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            float yaw = Game.player().getYRot();
            float mid = w / 2;
            float perDeg = w / SPAN;
            c.clip(4, 0, w - 8, h);
            // Ticks every 15 degrees, labelled at the eight compass points.
            for (int a = 0; a < 360; a += 15) {
                float off = wrap(a - yaw);
                if (Math.abs(off) > SPAN / 2 + 8) continue;
                float x = mid + off * perDeg;
                float edge = 1f - Math.clamp((Math.abs(off) - SPAN / 2 + 22) / 22f, 0f, 1f);
                c.pushAlpha(edge);
                if (a % 45 == 0) {
                    String name = CARDINALS[a / 45];
                    boolean major = a % 90 == 0;
                    int col = name.equals("N") ? Theme.accent() : major ? Theme.TEXT : Theme.TEXT_DIM;
                    c.textCentered(major ? Fonts.BOLD : Fonts.MEDIUM, name, x, 3.5f, major ? 8.5f : 6.5f, col);
                } else {
                    c.rect(x - 0.4f, 6, 0.8f, 5, 0.4f, 0x55FFFFFF);
                }
                c.popAlpha();
            }
            if (waypoints.get() && Modules.WAYPOINTS.enabled()) {
                var p = Game.player();
                String dim = Game.dimensionId();
                for (Waypoints.Waypoint wp : Waypoints.all()) {
                    if (!wp.visible || !wp.dimension.equals(dim)) continue;
                    float bearing = (float) Math.toDegrees(Math.atan2(-(wp.x - p.getX()), wp.z - p.getZ()));
                    float off = Math.clamp(wrap(bearing - yaw), -SPAN / 2 + 5, SPAN / 2 - 5);
                    float x = mid + off * perDeg;
                    c.circle(x, 14.5f, 2.6f, Colors.withAlpha(Colors.BLACK, 0.5f));
                    c.circle(x, 14.5f, 1.9f, wp.color);
                }
            }
            c.unclip();
            c.rect(mid - 0.5f, 1.5f, 1, 3, 0.5f, Theme.accent());
            if (degrees.get()) {
                String heading = Math.round(((yaw % 360) + 540) % 360) + "°";
                c.textCentered(Fonts.MEDIUM, heading, mid, 17.5f, 6.5f, Theme.TEXT_DIM);
            }
        }
    }

    public static final class LiveGraph extends HudModule {
        public enum Metric { FPS, PING, CPS }

        public final Settings.Choice<Metric> metric = choice("metric", "Metric", Metric.FPS);
        public final Settings.Num seconds = num("seconds", "Time window", 60f, 15f, 300f, 15f).suffix("s");
        public final Settings.Num width = num("width", "Width", 110f, 70f, 240f, 10f).suffix("px");

        public LiveGraph() {
            super("graph", "Live graph", "A rolling graph of FPS, ping or CPS over the last minutes", AnchorH.RIGHT, AnchorV.TOP, 6, 46);
            keywords("chart", "stats", "performance", "history", "frametime");
        }

        @Override
        protected boolean measure(boolean editing) {
            w = width.get();
            h = 46;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            chip(c, Theme.R_MD);
            var live = Session.current();
            float[] ring = switch (metric.get()) {
                case FPS -> live.fps;
                case PING -> live.ping;
                case CPS -> live.cps;
            };
            float[] data = Session.recent(ring, seconds.asInt());
            String now = data.length == 0 ? "-" : Integer.toString(Math.round(data[data.length - 1]));
            c.text(Fonts.MEDIUM, metric.get().name(), 7, 5, 6.5f, Theme.accent());
            c.textRight(Fonts.SEMIBOLD, now, w - 7, 4, 8.5f, Theme.TEXT);
            Graph.line(c, 7, 17, w - 14, h - 23, data, Theme.accent());
        }
    }

    public static final class PlayerList extends HudModule {
        public final Settings.Bool pings = bool("pings", "Show ping", true);
        public final Settings.Num maxRows = num("max_rows", "Rows per column", 16f, 6f, 30f, 1f);
        public final Settings.Bool always = bool("always", "Always visible (not just while holding Tab)", false);

        private static final float ROW = 12f;
        private final List<String> names = new ArrayList<>();
        private final List<Integer> latencies = new ArrayList<>();
        private final Spring show = Spring.snappy(0);
        private int columns, rows;
        private float colW;

        public PlayerList() {
            super("player_list", "Player list", "A cleaner tab list with smooth text and ping bars", AnchorH.CENTER, AnchorV.TOP, 0, 34);
            keywords("tab", "players", "online", "tablist");
        }

        @Override
        protected boolean measure(boolean editing) {
            boolean wanted = editing || always.get() || Mc.mc().options.keyPlayerList.isDown();
            float s = show.target(wanted ? 1 : 0).update();
            if (s < 0.02f) return false;
            names.clear();
            latencies.clear();
            var connection = Mc.mc().getConnection();
            if (connection != null) {
                List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
                infos.sort(Comparator.comparing(i -> Game.tabName(i).toLowerCase()));
                for (PlayerInfo info : infos) {
                    names.add(Game.tabName(info));
                    latencies.add(info.getLatency());
                }
            }
            if (names.isEmpty()) return false;
            int perCol = maxRows.asInt();
            columns = Math.min(4, (names.size() + perCol - 1) / perCol);
            rows = Math.min(perCol, (names.size() + columns - 1) / columns);
            float widest = 50;
            int shown = Math.min(names.size(), columns * rows);
            for (int i = 0; i < shown; i++) widest = Math.max(widest, Fonts.MEDIUM.width(names.get(i), 7.5f));
            colW = widest + (pings.get() ? 26 : 12);
            w = columns * colW + 8;
            h = rows * ROW + 24;
            return true;
        }

        @Override
        protected void render(Canvas c, boolean editing) {
            float s = Math.clamp(show.get(), 0f, 1f);
            c.pushAlpha(s);
            c.push();
            c.translate(0, -6 * (1 - s));
            Theme.chip(c, 0, 0, w, h, Theme.R_MD, background.get() ? 0xD9100E18 : 0x00000000);
            String title = names.size() + (names.size() == 1 ? " player online" : " players online");
            c.text(Fonts.SEMIBOLD, title, 8, 6, 7.5f, Theme.TEXT);
            c.rect(6, 18, w - 12, 1, 0, Theme.BORDER);
            int shown = Math.min(names.size(), columns * rows);
            for (int i = 0; i < shown; i++) {
                float x = 4 + (i / rows) * colW, y = 21 + (i % rows) * ROW;
                c.textMiddle(Fonts.MEDIUM, names.get(i), x + 5, y, ROW, 7.5f, Theme.TEXT);
                if (pings.get()) bars(c, x + colW - 16, y + 2.5f, latencies.get(i));
            }
            if (shown < names.size()) {
                c.textRight(Fonts.REGULAR, "+" + (names.size() - shown) + " more", w - 8, 6.5f, 6.5f, Theme.TEXT_MUTED);
            }
            c.pop();
            c.popAlpha();
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
}
