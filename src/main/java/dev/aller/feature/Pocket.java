package dev.aller.feature;

import dev.aller.AllerClient;
import dev.aller.mixin.pocket.PocketPlugin;
import dev.aller.module.Modules;
import dev.aller.platform.Canvas;
import dev.aller.platform.Mc;
import dev.aller.platform.PocketServer;
import dev.aller.platform.Worlds;
import dev.aller.ui.Colors;
import dev.aller.ui.Theme;
import dev.aller.ui.Toasts;
import dev.aller.ui.anim.Motion;
import dev.aller.ui.font.Fonts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pocket dimension: a private world on an integrated server that the player steps into while
 * the connection to the real server stays open. That server keeps seeing an ordinary client that
 * is standing still; its world is parked in {@code platform/Worlds} and goes on being ticked and
 * fed its packets, so walking back out needs no rejoin. Nothing crosses between the two worlds
 * except chat, which always belongs to the real server.
 *
 * <p>Whatever goes wrong (a mixin another mod displaced, an exception, the pocket's server
 * failing) ends with the player back on the server and the mod switched off, never with a crash.
 */
public final class Pocket {
    private Pocket() {}

    private static final String NAME = "Pocket dimension";
    /** Lets the harness open the pocket from its singleplayer test world. */
    private static final boolean DEV = System.getProperty("aller.dev.pocket") != null;

    private enum Phase {
        IDLE,
        /** On the server, being walled in while the pocket's server starts and logs the player in. */
        SEALING,
        /** In the pocket, behind the dark, until it has loaded and the walls break away. */
        ARRIVING,
        INSIDE,
        /** In the pocket, being walled in again. */
        LEAVING,
        /** Back on the server, the walls breaking away. */
        RETURNING
    }

    private static Phase phase = Phase.IDLE;
    private static int ticks, settled;
    private static Connection remote, pocket;
    /** The walls being built or broken now, and the ones left standing around the player's body on the server. */
    private static Cocoon cocoon, outside;
    private static float health;
    private static boolean forwarding, keyWasDown;
    private static float dark, shown;
    /** Read by the pocket's server thread. */
    private static volatile GameType mode = GameType.CREATIVE;
    private static volatile boolean arriving;

    /** True while the pocket is the world on screen. */
    public static boolean inside() {
        return phase == Phase.ARRIVING || phase == Phase.INSIDE || phase == Phase.LEAVING;
    }

    public static boolean active() {
        return phase != Phase.IDLE;
    }

    /** The stage of a visit, for the harness and the log. */
    public static String state() {
        return phase.name();
    }

    public static boolean bright() {
        return inside() && Modules.POCKET.bright.get();
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server != PocketServer.server()) return;
            ServerLevel room = server.overworld();
            if (arriving && !server.getPlayerList().getPlayers().isEmpty()) {
                arriving = false;
                PocketRoom.repair(room);
                // Always at the entrance: the last place they stood may be the doorway they left by.
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(room, PocketRoom.ENTRY_X, PocketRoom.ENTRY_Y, PocketRoom.ENTRY_Z, Set.of(), 0f, 0f, false);
                player.setGameMode(mode);
            } else if (server.getTickCount() % 20 == 0) {
                PocketRoom.repair(room);
            }
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) ->
                !(level instanceof ServerLevel s && isRoom(s.getServer(), s) && PocketRoom.fixed(pos)));
        // The same answer on the client, so a wall does not flicker out and back.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                level.isClientSide() && inside() && level.dimension() == Level.OVERWORLD && PocketRoom.fixed(pos)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    private static boolean isRoom(MinecraftServer server, ServerLevel level) {
        return server == PocketServer.server() && level.dimension() == Level.OVERWORLD;
    }

    /** Reads the mod's key once a frame; it steps in and out rather than switching the mod on and off. */
    public static void poll() {
        var module = Modules.POCKET;
        boolean down = module.enabled() && Mc.mc().player != null && Mc.screen() == null && Mc.isDown(module.keybind.get());
        if (down && !keyWasDown) toggle();
        keyWasDown = down;
    }

    public static void toggle() {
        try {
            switch (phase) {
                case IDLE -> enter();
                case INSIDE -> leave();
                case SEALING -> eject(null);
                default -> {}
            }
        } catch (Throwable t) {
            broke(t);
        }
    }

    private static void enter() {
        Minecraft mc = Mc.mc();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (mc.isLocalServer() && !DEV) {
            Toasts.info(NAME, "It opens from a multiplayer server, not from a singleplayer world");
            return;
        }
        String problem = PocketPlugin.problem();
        if (problem != null) {
            unavailable(problem);
            return;
        }
        if (!PocketServer.idle()) {
            Toasts.info(NAME, "Still saving from last time. Try again in a moment");
            return;
        }
        if (!player.onGround() || player.isPassenger() || player.isSleeping() || player.isDeadOrDying()) {
            Toasts.info(NAME, "Stand on the ground first");
            return;
        }
        remote = mc.getConnection().getConnection();
        pocket = null;
        mc.gameMode.stopDestroyBlock();
        if (player.isUsingItem()) mc.gameMode.releaseUsingItem(player);
        player.input = new ClientInput();
        cocoon = Cocoon.around(mc.level, player);
        outside = null;
        mode = Modules.POCKET.mode.get() == dev.aller.module.mods.WorldMods.PocketDimension.Mode.SURVIVAL ? GameType.SURVIVAL : GameType.CREATIVE;
        arriving = true;
        PocketServer.start(mode);
        set(Phase.SEALING);
    }

    private static void leave() {
        Minecraft mc = Mc.mc();
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.stopDestroyBlock();
        if (Mc.screen() != null) mc.player.closeContainer();
        mc.player.input = new ClientInput();
        cocoon = Cocoon.around(mc.level, mc.player);
        set(Phase.LEAVING);
    }

    private static void set(Phase next) {
        phase = next;
        ticks = 0;
    }

    public static void tick() {
        if (phase == Phase.IDLE) {
            dark = 0;
            return;
        }
        try {
            step();
        } catch (Throwable t) {
            broke(t);
        }
    }

    private static void step() {
        Minecraft mc = Mc.mc();
        ticks++;
        if (mc.player == null || mc.level == null) {
            eject(null);
            return;
        }
        switch (phase) {
            case SEALING -> {
                cocoon.grow(2, false);
                Throwable failure = PocketServer.failure();
                if (failure != null) {
                    AllerClient.LOG.warn("Pocket: the server could not start", failure);
                    eject("It could not be opened. The log has the reason");
                    return;
                }
                if (pocket == null && PocketServer.ready()) {
                    pocket = PocketServer.connect();
                    Worlds.open(remote, pocket);
                    Worlds.backServer(PocketServer.server());
                }
                if (pocket != null) Worlds.tickBack();
                if (phase != Phase.SEALING) return;
                dark = approach(dark, cocoon.sealed() ? 1 : 0);
                if (pocket != null && Worlds.backPlayer() != null && cocoon.sealed() && dark >= 1) {
                    outside = cocoon;
                    cocoon = null;
                    health = mc.player.getHealth();
                    Worlds.flip();
                    set(Phase.ARRIVING);
                } else if (ticks > 20 * 45) {
                    eject("It took too long to open");
                }
            }
            case ARRIVING -> {
                if (away()) return;
                LocalPlayer player = mc.player;
                if (cocoon == null) {
                    boolean there = player.distanceToSqr(PocketRoom.ENTRY_X, PocketRoom.ENTRY_Y, PocketRoom.ENTRY_Z) < 4
                            && mc.level.hasChunk(player.getBlockX() >> 4, player.getBlockZ() >> 4);
                    if (there && ticks > 4) {
                        cocoon = Cocoon.around(mc.level, player);
                        cocoon.grow(Integer.MAX_VALUE, true);
                        // A few ticks for the room to be meshed before the dark lifts.
                        settled = ticks + 8;
                    } else if (ticks > 20 * 15) {
                        eject("The pocket did not load");
                    }
                } else if (ticks >= settled) {
                    dark = approach(dark, 0);
                    cocoon.shrink(3);
                    if (cocoon.empty()) {
                        cocoon = null;
                        set(Phase.INSIDE);
                    }
                }
            }
            case INSIDE -> {
                dark = approach(dark, 0);
                if (away()) return;
                if (mc.level.dimension() == Level.OVERWORLD && PocketRoom.throughDoor(mc.player)) leave();
            }
            case LEAVING -> {
                if (away()) return;
                cocoon.grow(3, false);
                dark = approach(dark, cocoon.sealed() ? 1 : 0);
                if (cocoon.sealed() && dark >= 1) {
                    Worlds.flip();
                    shut();
                    cocoon = outside != null && outside.level == mc.level ? outside : null;
                    outside = null;
                    set(Phase.RETURNING);
                }
            }
            case RETURNING -> {
                if (ticks < 6) return;
                dark = approach(dark, 0);
                if (cocoon != null) {
                    cocoon.shrink(3);
                    if (cocoon.empty()) cocoon = null;
                }
                if (cocoon == null && dark <= 0) finish();
            }
            default -> {}
        }
    }

    private static float approach(float value, float target) {
        return target > value ? Math.min(target, value + 0.25f) : Math.max(target, value - 0.2f);
    }

    /**
     * Gives the player's body on the server its tick and checks on it.
     * @return true if that ended the visit
     */
    private static boolean away() {
        Phase was = phase;
        Worlds.tickBack();
        if (phase != was) return true;
        LocalPlayer body = Worlds.backPlayer();
        if (body == null || !remote.isConnected()) {
            eject(null);
            return true;
        }
        float now = body.getHealth();
        if (body.isDeadOrDying() || now < health - 0.01f) {
            eject("You took damage on the server");
            return true;
        }
        health = now;
        return false;
    }

    /** Back on the server at once, with no animation, from wherever the visit had got to. */
    private static void eject(String why) {
        Phase was = phase;
        if (was == Phase.IDLE) return;
        phase = Phase.IDLE;
        Minecraft mc = Mc.mc();
        try {
            if (Worlds.open()) {
                if (Worlds.front() == pocket) Worlds.flip();
                shut();
            } else {
                PocketServer.stop();
            }
            if (mc.player != null && !(mc.player.input instanceof KeyboardInput)) mc.player.input = new KeyboardInput(mc.options);
            Cocoon standing = outside != null ? outside : was == Phase.SEALING || was == Phase.RETURNING ? cocoon : null;
            if (standing != null) standing.shrink(Integer.MAX_VALUE);
        } finally {
            finish();
            if (why != null) Toasts.warn(NAME, why);
        }
    }

    private static void finish() {
        phase = Phase.IDLE;
        cocoon = outside = null;
        remote = pocket = null;
        dark = 0;
    }

    /** Closes the pocket's connection and asks its server to save and stop. The server's world must be on screen. */
    private static void shut() {
        Connection connection = pocket;
        pocket = null;
        if (connection != null) {
            try {
                if (connection.getPacketListener() instanceof ClientPacketListener listener) listener.close();
                connection.disconnect(Component.literal("Left the pocket"));
            } catch (Throwable t) {
                AllerClient.LOG.warn("Pocket: closing its connection failed", t);
            }
        }
        Worlds.close();
        PocketServer.stop();
        PocketServer.restoreTags();
    }

    /** Something threw. The pocket closes, and the mod goes off so it is not walked into again. */
    private static void broke(Throwable t) {
        AllerClient.LOG.error("Pocket: closed after an error", t);
        try {
            eject(null);
        } catch (Throwable again) {
            AllerClient.LOG.error("Pocket: could not close cleanly", again);
            finish();
        }
        unavailable("something went wrong inside it");
    }

    private static void unavailable(String problem) {
        AllerClient.LOG.warn("Pocket: unavailable, {}", problem);
        Modules.POCKET.setEnabled(false);
        AllerClient.config().markDirty();
        Toasts.warn(NAME + " switched off", "It cannot run here: " + problem);
    }

    /** The mod was switched off, or the game is closing. */
    public static void close(boolean wait) {
        try {
            eject(null);
        } catch (Throwable t) {
            AllerClient.LOG.error("Pocket: could not close cleanly", t);
            finish();
        }
        if (wait) PocketServer.stopAndWait();
    }

    // ---- called from the mixins, through Hooks ----------------------------------------------------

    /** What each packet being handled on the game thread has to undo; they nest when a handler pumps the task queue. */
    private static int[] swaps = new int[8];
    private static int depth;

    /** A packet is about to be handled: put its connection's world in place. {@link #packetDone} always follows. */
    public static void packet(Object listener, Object packet) {
        // The same code runs on server threads, for packets that are none of the client's business.
        if (!Mc.mc().isSameThread()) return;
        if (depth == swaps.length) swaps = java.util.Arrays.copyOf(swaps, depth * 2);
        swaps[depth++] = Worlds.open() ? swap(listener, packet) : -1;
    }

    private static int swap(Object listener, Object packet) {
        try {
            Connection connection = Worlds.of(listener);
            if (connection == null) return -1;
            // These rebuild the world they arrive in, renderer and all, so the server's has to be on screen first.
            if (connection == remote && Worlds.back() == remote && (packet instanceof ClientboundRespawnPacket
                    || packet instanceof ClientboundLoginPacket || packet instanceof ClientboundStartConfigurationPacket)) {
                eject("The server moved you");
            }
            return Worlds.enter(connection);
        } catch (Throwable t) {
            broke(t);
            return -1;
        }
    }

    public static void packetDone() {
        if (!Mc.mc().isSameThread() || depth == 0) return;
        int token = swaps[--depth];
        if (token >= 0) Worlds.leave(token);
    }

    /** A screen is being opened. @return true to drop it, because the world asking is not the one on screen */
    public static boolean screen(Object screen) {
        if (!Worlds.away()) return false;
        if (screen == null || Worlds.back() != remote || phase == Phase.IDLE) return true;
        // The server wants the player's attention (a container, a prompt): go back and let it open.
        try {
            eject("The server opened a window");
            return false;
        } catch (Throwable t) {
            broke(t);
            return true;
        }
    }

    /** A connection dropped. @return true if it was the pocket's, which vanilla must not treat as leaving the game */
    public static boolean lost(Object listener) {
        if (phase == Phase.IDLE) return false;
        Connection connection = Worlds.of(listener);
        if (connection == null) return false;
        boolean own = connection == pocket;
        if (!own && connection != remote) return false;
        try {
            eject(own ? "The pocket closed unexpectedly" : null);
        } catch (Throwable t) {
            broke(t);
        }
        return own;
    }

    /** "Disconnect" or "Save and quit" was chosen. @return true if that was inside the pocket, which it leaves instead */
    public static boolean quit() {
        if (!inside()) return false;
        Mc.setScreen(null);
        if (phase == Phase.INSIDE) toggle();
        return true;
    }

    /** The client is disconnecting; that has to act on the server's world. */
    public static void disconnecting() {
        if (phase != Phase.IDLE) close(false);
    }

    /** Chat typed in the pocket. @return true if it was sent on, to the server or as a pocket command */
    public static boolean chat(Object listener, String text, boolean command) {
        if (forwarding || !inside() || remote == null || Worlds.of(listener) != pocket) return false;
        if (!(remote.getPacketListener() instanceof ClientPacketListener server)) return false;
        String prefix = Modules.POCKET.prefix.get().strip();
        forwarding = true;
        try {
            if (!command && !prefix.isEmpty() && text.startsWith(prefix) && text.length() > prefix.length()) {
                ((ClientPacketListener) listener).sendCommand(text.substring(prefix.length()).strip());
            } else if (command) {
                server.sendCommand(text);
            } else {
                server.sendChat(text);
            }
        } finally {
            forwarding = false;
        }
        return true;
    }

    /** The dark that covers the change of world. Drawn over the HUD. */
    public static void draw(Canvas c) {
        shown += (dark - shown) * Math.min(1f, Motion.realDelta() * 18f);
        if (shown < 0.004f) {
            shown = 0;
            return;
        }
        c.plainRect(0, 0, c.width(), c.height(), Colors.withAlpha(Colors.BLACK, Math.min(1f, shown)));
        if (phase == Phase.SEALING && ticks > 50 && shown > 0.9f) {
            c.textCentered(Fonts.SEMIBOLD, "Opening the pocket", c.width() / 2, c.height() / 2 - 4, 9f, Theme.TEXT_DIM);
        }
    }

    /** The walls that close around the player before a change of world and break away after it. Client-side blocks only. */
    private static final class Cocoon {
        private static final BlockState WALL = dev.aller.platform.Game.blackConcrete();
        /** Client-side only: no neighbour updates, just a re-render. */
        private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

        final ClientLevel level;
        private final List<BlockPos> hull;
        private final Map<BlockPos, BlockState> before = new HashMap<>();
        private int placed;

        private Cocoon(ClientLevel level, List<BlockPos> hull) {
            this.level = level;
            this.hull = hull;
        }

        /** Every block touching the ones the player stands in, ordered as a spiral from the feet up. */
        static Cocoon around(ClientLevel level, LocalPlayer player) {
            AABB box = player.getBoundingBox().deflate(0.001);
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ), max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            List<BlockPos> hull = new ArrayList<>();
            for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
                for (int y = min.getY() - 1; y <= max.getY() + 1; y++) {
                    for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                        boolean within = x >= min.getX() && x <= max.getX() && y >= min.getY() && y <= max.getY()
                                && z >= min.getZ() && z <= max.getZ();
                        if (!within) hull.add(new BlockPos(x, y, z));
                    }
                }
            }
            double cx = player.getX(), cz = player.getZ();
            hull.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                    .thenComparingDouble(p -> Math.atan2(p.getZ() + 0.5 - cz, p.getX() + 0.5 - cx)));
            return new Cocoon(level, hull);
        }

        boolean sealed() {
            return placed >= hull.size();
        }

        boolean empty() {
            return placed <= 0;
        }

        private boolean showing() {
            return Mc.mc().level == level;
        }

        void grow(int count, boolean quiet) {
            for (; count > 0 && placed < hull.size() && showing(); count--) {
                BlockPos pos = hull.get(placed++);
                BlockState old = level.getBlockState(pos);
                // What is already solid stays as it is: the floor, a wall beside the player.
                if (!old.canBeReplaced()) continue;
                before.put(pos, old);
                level.setBlock(pos, WALL, FLAGS);
                if (!quiet) {
                    level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.STONE_PLACE,
                            SoundSource.BLOCKS, 0.55f, 0.75f + level.getRandom().nextFloat() * 0.3f, false);
                }
            }
        }

        void shrink(int count) {
            for (; count > 0 && placed > 0; count--) {
                BlockPos pos = hull.get(--placed);
                BlockState old = before.remove(pos);
                // Only where the wall still stands: the server may have sent the real block since.
                if (old == null || !showing() || level.getBlockState(pos) != WALL) continue;
                level.levelEvent(2001, pos, Block.getId(WALL));
                level.setBlock(pos, old, FLAGS);
            }
        }
    }
}
