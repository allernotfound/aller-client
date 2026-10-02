package dev.aller.platform;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/** Gameplay queries used by modules, with the handful of cross-version differences hidden. */
public final class Game {
    private Game() {}

    public static LocalPlayer player() {
        return Mc.mc().player;
    }

    public static ClientLevel level() {
        return Mc.mc().level;
    }

    public static boolean inWorld() {
        return player() != null && level() != null;
    }

    /** Time of day in ticks since the world began (divide by 24000 for the day number). */
    public static long dayTime() {
        //? if <26.1 {
        /*return level().getDayTime();
        *///?} else {
        return level().getDefaultClockTime();
        //?}
    }

    /** e.g. "dark_forest"; null if unknown. */
    public static String biomeId() {
        BlockPos pos = player().blockPosition();
        return level().getBiome(pos).unwrapKey()
                //? if <26.1 {
                /*.map(k -> k.location().getPath())
                *///?} else {
                .map(k -> k.identifier().getPath())
                //?}
                .orElse(null);
    }

    /** e.g. "overworld", "the_nether". */
    public static String dimensionId() {
        //? if <26.1 {
        /*return level().dimension().location().getPath();
        *///?} else {
        return level().dimension().identifier().getPath();
        //?}
    }

    public static String tabName(PlayerInfo info) {
        //? if <26.1 {
        /*return Mc.mc().gui.getTabList().getNameForDisplay(info).getString();
        *///?} else {
        return Mc.mc().gui.hud.getTabList().getNameForDisplay(info).getString();
        //?}
    }

    public static int ping() {
        var connection = Mc.mc().getConnection();
        if (connection == null || player() == null) return -1;
        PlayerInfo info = connection.getPlayerInfo(player().getUUID());
        return info == null ? -1 : info.getLatency();
    }

    /**
     * How far the attack cooldown has recharged, 0..1. Reports 1 unless vanilla's attack indicator
     * is set to "Crosshair", the same condition under which vanilla draws its own.
     */
    public static float attackCharge() {
        var p = player();
        if (p == null || Mc.mc().options.attackIndicator().get() != net.minecraft.client.AttackIndicatorStatus.CROSSHAIR) return 1f;
        return Math.clamp(p.getAttackStrengthScale(0f), 0f, 1f);
    }

    /** The multiplayer server address, or null in singleplayer. */
    public static String serverAddress() {
        var server = Mc.mc().getCurrentServer();
        return server == null ? null : server.ip;
    }

    /** Sends one chat line, or one command when it starts with a slash, exactly as typing it in chat would. */
    public static void send(String message) {
        var p = player();
        if (p == null || message.isBlank()) return;
        String text = message.strip();
        if (text.startsWith("/")) p.connection.sendCommand(text.substring(1));
        else p.connection.sendChat(text);
    }

    /** The world seed, known only in singleplayer (a server never sends it); otherwise null. */
    public static Long seed() {
        var server = Mc.mc().getSingleplayerServer();
        return server == null ? null : server.overworld().getSeed();
    }

    /** The singleplayer world's name, or the server's name in the server list; null if neither is known. */
    public static String worldName() {
        var server = Mc.mc().getSingleplayerServer();
        if (server != null) return server.getWorldData().getLevelName();
        var data = Mc.mc().getCurrentServer();
        return data == null ? null : data.name;
    }

    /** A stable name for the current world or server, used to key per-world data such as waypoints. */
    public static String worldKey() {
        String address = serverAddress();
        if (address != null) return "mp_" + address.replaceAll("[^A-Za-z0-9._-]", "_");
        var server = Mc.mc().getSingleplayerServer();
        if (server != null) return "sp_" + server.getWorldData().getLevelName().replaceAll("[^A-Za-z0-9._-]", "_");
        return "unknown";
    }

    /** Black concrete, which the pocket dimension is built from. */
    public static net.minecraft.world.level.block.state.BlockState blackConcrete() {
        //? if <26.1 {
        /*return net.minecraft.world.level.block.Blocks.BLACK_CONCRETE.defaultBlockState();
        *///?} else {
        return net.minecraft.world.level.block.Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
        //?}
    }

    /** "snake_case" -> "Snake Case". */
    public static String pretty(String id) {
        if (id == null || id.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
