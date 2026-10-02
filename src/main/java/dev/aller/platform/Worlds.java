package dev.aller.platform;

import dev.aller.mixin.pocket.HandshakePocketAccessor;
import dev.aller.mixin.pocket.ListenerPocketAccessor;
import dev.aller.mixin.pocket.MinecraftPocketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.world.entity.Entity;

/**
 * Two connections sharing a client built for one. Minecraft keeps a single level, player, game
 * mode, camera and integrated server; here the connection that is not on screen has its set
 * parked, and it is swapped into Minecraft's fields for exactly as long as one of its packets or
 * its tick runs. Nothing is drawn while a parked world is swapped in ({@link #away()}).
 */
public final class Worlds {
    private Worlds() {}

    /** One connection's share of what Minecraft holds for "the" world. */
    private static final class Side {
        ClientLevel level;
        LocalPlayer player;
        MultiPlayerGameMode gameMode;
        Entity camera;
        IntegratedServer server;
        boolean local;
    }

    private static volatile Side parked;
    private static Connection front, back;
    private static boolean away;
    /** Bumped whenever the sides change places for good, so a swap begun before it is not undone. */
    private static int epoch;

    public static boolean open() {
        return parked != null;
    }

    /** True while the parked world is in Minecraft's fields. */
    public static boolean away() {
        return away;
    }

    /** The connection whose world is on screen. */
    public static Connection front() {
        return front;
    }

    public static Connection back() {
        return back;
    }

    /** Starts keeping a second world, for a connection that has not logged in yet; it begins parked and empty. */
    public static void open(Connection current, Connection second) {
        parked = new Side();
        front = current;
        back = second;
        away = false;
        epoch++;
    }

    /** Forgets the parked world. Its connection must already be closed. */
    public static void close() {
        parked = null;
        front = back = null;
        away = false;
        epoch++;
    }

    /** The connection a packet listener belongs to, or null if it is not one of the client's. */
    public static Connection of(Object listener) {
        if (listener instanceof ListenerPocketAccessor common) return common.allerConnection();
        if (listener instanceof HandshakePocketAccessor login) return login.allerConnection();
        return null;
    }

    /** Puts {@code connection}'s world into Minecraft's fields. @return what to hand to {@link #leave} */
    public static int enter(Connection connection) {
        if (parked == null || connection == null) return -1;
        boolean wantBack = connection == back;
        if (!wantBack && connection != front || wantBack == away) return -1;
        swap();
        away = wantBack;
        return epoch;
    }

    public static void leave(int token) {
        if (token < 0 || token != epoch || parked == null) return;
        swap();
        away = !away;
    }

    /** The parked world takes the screen and the other is parked, wherever this is called from. */
    public static void flip() {
        if (parked == null) return;
        if (!away) swap();
        away = false;
        epoch++;
        Connection was = front;
        front = back;
        back = was;

        Minecraft mc = Mc.mc();
        // The parked player must not walk with the keys that now move the other one.
        if (parked.player != null) parked.player.input = new ClientInput();
        if (mc.player != null) mc.player.input = new KeyboardInput(mc.options);
        //? if <26.1 {
        /*((MinecraftPocketAccessor) mc).allerEngines(mc.level);
        *///?} else {
        ((MinecraftPocketAccessor) mc).allerEngines(mc.level, false);
        //?}
        mc.setCameraEntity(mc.player);
    }

    public static LocalPlayer backPlayer() {
        if (parked == null) return null;
        return away ? Mc.mc().player : parked.player;
    }

    public static ClientLevel backLevel() {
        if (parked == null) return null;
        return away ? Mc.mc().level : parked.level;
    }

    /** One client tick for the parked world: its connection, its entities and the packet that ends a tick. */
    public static void tickBack() {
        if (parked == null) return;
        Connection connection = back;
        int token = enter(connection);
        try {
            Minecraft mc = Mc.mc();
            if (mc.gameMode != null) mc.gameMode.tick();
            else if (connection.isConnected()) connection.tick();
            else connection.handleDisconnection();
            // Losing the connection may have closed or flipped the worlds from inside that call.
            if (parked == null || !away || mc.level == null || mc.player == null) return;
            mc.level.tickEntities();
            mc.level.tick(() -> true);
            mc.player.connection.send(ServerboundClientTickEndPacket.INSTANCE);
        } finally {
            leave(token);
        }
    }

    private static void swap() {
        Minecraft mc = Mc.mc();
        MinecraftPocketAccessor fields = (MinecraftPocketAccessor) mc;
        Side s = parked;
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        Entity camera = camera(mc);
        IntegratedServer server = mc.getSingleplayerServer();
        boolean local = mc.isLocalServer();

        mc.level = s.level;
        mc.player = s.player;
        mc.gameMode = s.gameMode;
        camera(mc, s.camera);
        fields.allerServer(s.server);
        fields.allerLocal(s.local);

        s.level = level;
        s.player = player;
        s.gameMode = gameMode;
        s.camera = camera;
        s.server = server;
        s.local = local;
    }

    /** Sets which integrated server the world now in Minecraft's fields plays on (none for a real server). */
    public static void server(IntegratedServer server) {
        MinecraftPocketAccessor fields = (MinecraftPocketAccessor) Mc.mc();
        fields.allerServer(server);
        fields.allerLocal(server != null);
    }

    /** The parked side is given its integrated server before its connection logs in. */
    public static void backServer(IntegratedServer server) {
        if (parked == null) return;
        if (away) {
            server(server);
        } else {
            parked.server = server;
            parked.local = server != null;
        }
    }

    private static Entity camera(Minecraft mc) {
        //? if <26.1 {
        /*return mc.cameraEntity;
        *///?} else {
        return mc.gameRenderer.mainCamera().entity();
        //?}
    }

    // Not setCameraEntity: that also loads the entity's post effect, which belongs to the world on screen.
    private static void camera(Minecraft mc, Entity entity) {
        //? if <26.1 {
        /*mc.cameraEntity = entity;
        *///?} else {
        mc.gameRenderer.mainCamera().setEntity(entity);
        //?}
    }
}
