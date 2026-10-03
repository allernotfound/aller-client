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

    private static net.minecraft.client.Camera camera() {
        //? if <26.1 {
        /*return Mc.mc().gameRenderer.getMainCamera();
        *///?} else {
        return Mc.mc().gameRenderer.mainCamera();
        //?}
    }

    /** False while the view is fogged for a reason of the game's: under water or lava, in powder snow, blinded. */
    public static boolean clearView() {
        LocalPlayer p = player();
        return p != null && camera().getFluidInCamera() == net.minecraft.world.level.material.FogType.NONE
                && !p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                && !p.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
    }

    /** How far the camera is from what the crosshair is on, or {@code max} if that is further or nothing. */
    public static float lookDistance(float max) {
        LocalPlayer p = player();
        if (p == null || level() == null) return max;
        var from = camera().position();
        var dir = p.getViewVector(1f);
        if (Mc.mc().options.getCameraType().isMirrored()) dir = dir.scale(-1);
        var hit = level().clip(new net.minecraft.world.level.ClipContext(from, from.add(dir.scale(max)),
                net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        double d = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? max : hit.getLocation().distanceTo(from);
        var entity = Mc.mc().crosshairPickEntity;
        if (entity != null) d = Math.min(d, entity.getBoundingBox().getCenter().distanceTo(from));
        return (float) Math.min(d, max);
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

    /** A tab list entry's name as the server styled it: team colours, ranks, a nickname. */
    public static net.minecraft.network.chat.Component tabNameStyled(PlayerInfo info) {
        return tabOverlay().getNameForDisplay(info);
    }

    private static net.minecraft.client.gui.components.PlayerTabOverlay tabOverlay() {
        //? if <26.1 {
        /*return Mc.mc().gui.getTabList();
        *///?} else {
        return Mc.mc().gui.hud.getTabList();
        //?}
    }

    /** What the server wrote above the player list, or null. */
    public static net.minecraft.network.chat.Component tabHeader() {
        return ((dev.aller.mixin.PlayerTabOverlayAccessor) tabOverlay()).aller$header();
    }

    public static net.minecraft.network.chat.Component tabFooter() {
        return ((dev.aller.mixin.PlayerTabOverlayAccessor) tabOverlay()).aller$footer();
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

    /** How far the current tick has run, 0 to 1, for placing things between two ticks. */
    public static float partialTick() {
        return Mc.mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    /** The account name behind a tab list entry, whatever the server shows in its place. */
    public static String profileName(PlayerInfo info) {
        //? if <26.1 {
        /*return info.getProfile().getName();
        *///?} else {
        return info.getProfile().name();
        //?}
    }

    public static java.util.UUID profileId(PlayerInfo info) {
        //? if <26.1 {
        /*return info.getProfile().getId();
        *///?} else {
        return info.getProfile().id();
        //?}
    }

    /** e.g. "minecraft:diamond_sword". */
    public static String itemId(net.minecraft.world.item.ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** The path of a registry key: "sharpness" for minecraft:sharpness. */
    public static String path(net.minecraft.resources.ResourceKey<?> key) {
        //? if <26.1 {
        /*return key.location().getPath();
        *///?} else {
        return key.identifier().getPath();
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
