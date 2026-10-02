package dev.aller.hud.elements;

import dev.aller.feature.Clicks;
import dev.aller.feature.Combat;
import dev.aller.feature.Session;
import dev.aller.hud.TextHud;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** The single-value HUD chips. Each is a label plus a value computed from game state. */
public final class InfoHuds {
    private InfoHuds() {}

    public static final class Fps extends TextHud {
        public Fps() {
            super("fps", "FPS", "Frames per second", AnchorH.LEFT, AnchorV.TOP, 6, 6);
            keywords("frames", "framerate", "performance");
            onByDefault();
        }

        @Override
        protected String label() {
            return "FPS";
        }

        @Override
        protected String value(boolean editing) {
            return Integer.toString(Mc.mc().getFps());
        }
    }

    public static final class Cps extends TextHud {
        public final Settings.Bool rightClick = bool("right", "Show right clicks", true);

        public Cps() {
            super("cps", "CPS", "Clicks per second", AnchorH.LEFT, AnchorV.TOP, 6, 72);
            keywords("clicks", "mouse");
        }

        @Override
        protected String label() {
            return "CPS";
        }

        @Override
        protected String value(boolean editing) {
            return rightClick.get() ? Clicks.left() + " | " + Clicks.right() : Integer.toString(Clicks.left());
        }
    }

    public static final class Ping extends TextHud {
        public Ping() {
            super("ping", "Ping", "Latency to the server", AnchorH.LEFT, AnchorV.TOP, 6, 92);
            keywords("latency", "ms", "lag");
        }

        @Override
        protected String label() {
            return "PING";
        }

        @Override
        protected String value(boolean editing) {
            int ping = Game.ping();
            if (ping < 0) return editing ? "24 ms" : null;
            return ping + " ms";
        }
    }

    public static final class Clock extends TextHud {
        public final Settings.Bool twentyFour = bool("24h", "24-hour clock", true);
        public final Settings.Bool seconds = bool("seconds", "Show seconds", false);

        public Clock() {
            super("clock", "Clock", "Real-world time", AnchorH.RIGHT, AnchorV.TOP, 6, 6);
            keywords("time");
        }

        @Override
        protected String label() {
            return null;
        }

        @Override
        protected String value(boolean editing) {
            String pattern = (twentyFour.get() ? "HH:mm" : "h:mm") + (seconds.get() ? ":ss" : "") + (twentyFour.get() ? "" : " a");
            return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern));
        }
    }

    public static final class Direction extends TextHud {
        public final Settings.Bool degrees = bool("degrees", "Show degrees", false);
        private static final String[] NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

        public Direction() {
            super("direction", "Direction", "Which way you are facing", AnchorH.LEFT, AnchorV.TOP, 6, 112);
            keywords("facing", "compass", "yaw");
        }

        @Override
        protected String label() {
            return "FACING";
        }

        @Override
        protected String value(boolean editing) {
            float yaw = ((Game.player().getYRot() % 360) + 360) % 360;
            String name = NAMES[Math.round(yaw / 45f) % 8];
            return degrees.get() ? name + " " + Math.round(yaw) + "°" : name;
        }
    }

    public static final class Speed extends TextHud {
        public enum Unit { BLOCKS_PER_SECOND, KM_PER_HOUR }

        public final Settings.Choice<Unit> unit = choice("unit", "Unit", Unit.BLOCKS_PER_SECOND);
        public final Settings.Bool horizontalOnly = bool("horizontal", "Ignore vertical movement", true);
        private double lastX, lastY, lastZ, shown;

        public Speed() {
            super("speed", "Speed", "How fast you are moving", AnchorH.LEFT, AnchorV.TOP, 6, 132);
            keywords("velocity", "bps");
        }

        @Override
        public void tick() {
            var p = Game.player();
            double dx = p.getX() - lastX, dy = horizontalOnly.get() ? 0 : p.getY() - lastY, dz = p.getZ() - lastZ;
            double perSecond = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
            // A teleport would otherwise read as an absurd one-tick spike.
            if (perSecond < 400) shown += (perSecond - shown) * 0.35;
            lastX = p.getX();
            lastY = p.getY();
            lastZ = p.getZ();
        }

        @Override
        protected String label() {
            return "SPEED";
        }

        @Override
        protected String value(boolean editing) {
            return unit.get() == Unit.KM_PER_HOUR
                    ? String.format("%.1f km/h", shown * 3.6)
                    : String.format("%.1f b/s", shown);
        }
    }

    public static final class Memory extends TextHud {
        public final Settings.Bool percent = bool("percent", "Show as percentage", false);

        public Memory() {
            super("memory", "Memory", "Java heap in use", AnchorH.RIGHT, AnchorV.TOP, 6, 96);
            keywords("ram", "heap");
        }

        @Override
        protected String label() {
            return "MEM";
        }

        @Override
        protected String value(boolean editing) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory(), max = rt.maxMemory();
            return percent.get() ? (used * 100 / max) + "%" : (used >> 20) + " / " + (max >> 20) + " MB";
        }
    }

    public static final class Day extends TextHud {
        public final Settings.Bool gameClock = bool("game_clock", "Show in-game time", true);

        public Day() {
            super("day", "Day counter", "In-game day and time of day", AnchorH.RIGHT, AnchorV.TOP, 6, 116);
            keywords("time", "night");
        }

        @Override
        protected String label() {
            return "DAY";
        }

        @Override
        protected String value(boolean editing) {
            long time = Game.dayTime();
            String day = Long.toString(time / 24000);
            if (!gameClock.get()) return day;
            // Minecraft's day starts at 06:00.
            long t = (time % 24000 + 6000) % 24000;
            return day + "  " + String.format("%02d:%02d", t / 1000, t % 1000 * 60 / 1000);
        }
    }

    public static final class Biome extends TextHud {
        public Biome() {
            super("biome", "Biome", "The biome you are standing in", AnchorH.LEFT, AnchorV.TOP, 6, 152);
        }

        @Override
        protected String label() {
            return "BIOME";
        }

        @Override
        protected String value(boolean editing) {
            String id = Game.biomeId();
            return id == null ? null : Game.pretty(id);
        }
    }

    public static final class Server extends TextHud {
        public Server() {
            super("server", "Server address", "The server you are connected to", AnchorH.RIGHT, AnchorV.TOP, 6, 136);
            keywords("ip");
        }

        @Override
        protected String label() {
            return "SERVER";
        }

        @Override
        protected String value(boolean editing) {
            String address = Game.serverAddress();
            return address != null ? address : editing ? "Singleplayer" : null;
        }
    }

    public static final class SessionTime extends TextHud {
        public SessionTime() {
            super("session_time", "Session timer", "How long you have been playing this session", AnchorH.RIGHT, AnchorV.TOP, 6, 26);
            keywords("playtime", "stopwatch");
        }

        @Override
        protected String label() {
            return "SESSION";
        }

        @Override
        protected String value(boolean editing) {
            long s = Session.seconds();
            return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
        }
    }

    public static final class Light extends TextHud {
        public Light() {
            super("light", "Light level", "Block light where you stand (mobs spawn at 0)", AnchorH.LEFT, AnchorV.TOP, 6, 172);
            keywords("brightness", "spawn");
        }

        @Override
        protected String label() {
            return "LIGHT";
        }

        @Override
        protected String value(boolean editing) {
            BlockPos pos = Game.player().blockPosition();
            return Integer.toString(Game.level().getBrightness(LightLayer.BLOCK, pos));
        }
    }

    public static final class ItemCount extends TextHud {
        public ItemCount() {
            super("item_count", "Item counter", "Total of the held item across your inventory", AnchorH.CENTER, AnchorV.BOTTOM, 120, 26);
            keywords("blocks", "stack", "inventory");
        }

        @Override
        protected String label() {
            return "HELD";
        }

        @Override
        protected String value(boolean editing) {
            ItemStack held = Game.player().getMainHandItem();
            if (held.isEmpty()) return editing ? "64" : null;
            return Integer.toString(Game.player().getInventory().countItem(held.getItem()));
        }
    }

    public static final class Saturation extends TextHud {
        public Saturation() {
            super("saturation", "Saturation", "Hidden hunger buffer that drains before your food bar", AnchorH.CENTER, AnchorV.BOTTOM, -120, 26);
            keywords("hunger", "food");
        }

        @Override
        protected String label() {
            return "SAT";
        }

        @Override
        protected String value(boolean editing) {
            return String.format("%.1f", Game.player().getFoodData().getSaturationLevel());
        }
    }

    public static final class Combo extends TextHud {
        public Combo() {
            super("combo", "Combo counter", "Consecutive hits landed without being hit back", AnchorH.CENTER, AnchorV.MIDDLE, 0, 40);
            keywords("hits", "pvp");
        }

        @Override
        protected String label() {
            return "COMBO";
        }

        @Override
        protected String value(boolean editing) {
            int combo = Combat.combo();
            return combo > 0 ? Integer.toString(combo) : editing ? "3" : null;
        }
    }

    public static final class Reach extends TextHud {
        public Reach() {
            super("reach", "Reach display", "Distance of your last hit", AnchorH.CENTER, AnchorV.MIDDLE, 0, 58);
            keywords("distance", "range", "pvp");
        }

        @Override
        protected String label() {
            return "REACH";
        }

        @Override
        protected String value(boolean editing) {
            double reach = Combat.lastReach();
            return reach > 0 ? String.format("%.2f", reach) : editing ? "2.87" : null;
        }
    }
}
