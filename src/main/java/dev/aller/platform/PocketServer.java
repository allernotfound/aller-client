package dev.aller.platform;

import com.mojang.serialization.Dynamic;
import dev.aller.AllerClient;
import dev.aller.mixin.pocket.MinecraftPocketAccessor;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
//? if <26.1 {
/*import net.minecraft.Util;
*///?} else {
import net.minecraft.util.Util;
//?}
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The pocket's world and the integrated server that runs it. Vanilla only starts that server on
 * its way out of whatever the client was connected to, so the same steps are taken here by hand:
 * the save lives in {@code <game dir>/aller-pocket}, outside the singleplayer list, and nothing
 * here touches the connection the player already has.
 */
public final class PocketServer {
    private PocketServer() {}

    private static final String LEVEL = "pocket";

    private static CompletableFuture<IntegratedServer> starting;
    private static volatile IntegratedServer server;
    private static volatile Throwable failure;
    private static boolean stopping;
    private static java.util.List<Registry.PendingTags<?>> serverTags;

    public static Path dir() {
        return Mc.mc().gameDirectory.toPath().resolve("aller-pocket");
    }

    /** Nothing running and nothing still being saved. */
    public static boolean idle() {
        settle();
        return server == null && starting == null;
    }

    /** Moves a finished start or stop along; both happen off the game thread. */
    private static void settle() {
        if (starting != null && starting.isDone()) {
            try {
                server = starting.join();
            } catch (Throwable t) {
                if (failure == null) failure = t;
            }
            starting = null;
            if (server != null && stopping) server.halt(false);
        }
        if (server != null && stopping && server.isShutdown()) server = null;
        if (server == null && starting == null) stopping = false;
    }

    /** Begins loading the world and starting its server. Poll {@link #ready()} and {@link #failure()}. */
    public static void start(GameType mode) {
        failure = null;
        stopping = false;
        try {
            keepTags();
            Files.createDirectories(dir());
            LevelStorageSource source = LevelStorageSource.createDefault(dir());
            boolean fresh = !source.levelExists(LEVEL);
            LevelStorageSource.LevelStorageAccess access = source.validateAndCreateAccess(LEVEL);
            try {
                PackRepository packs = ServerPacksSource.createPackRepository(access);
                CompletableFuture<WorldStem> stem = fresh ? create(packs, mode) : load(access, packs);
                starting = stem.thenApplyAsync(s -> spin(access, packs, s), Mc.mc()).whenComplete((s, error) -> {
                    if (error != null) {
                        failure = error;
                        access.safeClose();
                    }
                });
            } catch (Throwable t) {
                access.safeClose();
                throw t;
            }
        } catch (Throwable t) {
            failure = t;
        }
    }

    public static boolean ready() {
        settle();
        return !stopping && server != null && server.isReady() && failure == null;
    }

    /**
     * Block, item and other built-in tags are bound globally, and loading a world rebinds them to
     * its own data packs. The ones the real server sent are kept here and put back on the way out.
     */
    private static void keepTags() {
        java.util.List<Registry.PendingTags<?>> kept = new java.util.ArrayList<>();
        for (Registry<?> registry : net.minecraft.core.registries.BuiltInRegistries.REGISTRY) kept.add(tagsOf(registry));
        serverTags = kept;
    }

    private static <T> Registry.PendingTags<T> tagsOf(Registry<T> registry) {
        java.util.Map<net.minecraft.tags.TagKey<T>, java.util.List<net.minecraft.core.Holder<T>>> tags = new java.util.HashMap<>();
        registry.getTags().forEach(tag -> tags.put(tag.key(), java.util.List.copyOf(tag.stream().toList())));
        return registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(registry.key(), tags));
    }

    public static void restoreTags() {
        java.util.List<Registry.PendingTags<?>> kept = serverTags;
        serverTags = null;
        if (kept == null) return;
        try {
            kept.forEach(Registry.PendingTags::apply);
        } catch (Throwable t) {
            AllerClient.LOG.warn("Pocket: could not put the server's tags back", t);
        }
    }

    /** Why the server could not start or stopped by itself, or null. */
    public static Throwable failure() {
        return failure;
    }

    public static IntegratedServer server() {
        return server;
    }

    /** Opens the client's connection to the server, which must be {@link #ready()}. Its packets arrive on the game thread. */
    public static Connection connect() {
        Minecraft mc = Mc.mc();
        SocketAddress address = server.getConnection().startMemoryChannel();
        Connection connection = Connection.connectToLocalServer(address);
        //? if <26.1 {
        /*var login = new ClientHandshakePacketListenerImpl(connection, mc, null, null, false, null, status -> {}, null);
        *///?} else {
        var login = new ClientHandshakePacketListenerImpl(connection, mc, null, null, false, null, status -> {},
                new net.minecraft.client.multiplayer.LevelLoadTracker(), null);
        //?}
        connection.initiateServerboundPlayConnection(address.toString(), 0, login);
        connection.send(new ServerboundHelloPacket(mc.getUser().getName(), mc.getUser().getProfileId()));
        return connection;
    }

    /** Asks the server to save and stop; {@link #idle()} turns true once it has. */
    public static void stop() {
        stopping = true;
        if (server != null) server.halt(false);
        settle();
    }

    /** Stops the server and waits for the save, for when the game is closing. */
    public static void stopAndWait() {
        if (starting != null) {
            try {
                server = starting.join();
            } catch (Throwable ignored) {
            }
            starting = null;
        }
        if (server == null) return;
        try {
            server.halt(true);
        } catch (Throwable t) {
            AllerClient.LOG.warn("Pocket: the server did not stop cleanly", t);
        }
        server = null;
        stopping = false;
    }

    private static IntegratedServer spin(LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem) {
        Minecraft mc = Mc.mc();
        //? if <26.1 {
        /*access.saveDataTag(stem.registries().compositeAccess(), stem.worldData());
        var services = net.minecraft.server.Services.create(((MinecraftPocketAccessor) mc).allerAuth(), mc.gameDirectory);
        return MinecraftServer.spin(thread -> new Server(thread, mc, access, packs, stem, services,
                net.minecraft.server.level.progress.LoggerChunkProgressListener::createFromGameruleRadius));
        *///?} else {
        access.saveDataTag(stem.worldDataAndGenSettings().data());
        var services = ((MinecraftPocketAccessor) mc).allerServices();
        return MinecraftServer.spin(thread -> new Server(thread, mc, access, packs, stem, Optional.empty(), services,
                net.minecraft.server.level.progress.LoggingLevelLoadListener.forSingleplayer()));
        //?}
    }

    /** A void world: one layer of air, no structures, and a biome nothing spawns in. */
    private static WorldDimensions dimensions(HolderLookup.Provider registries) {
        var flat = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()),
                registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID),
                FlatLevelGeneratorSettings.createLakesList(registries.lookupOrThrow(Registries.PLACED_FEATURE)));
        flat.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        flat.updateLayers();
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()
                .createWorldDimensions().replaceOverworldGenerator(registries, new FlatLevelSource(flat));
    }

    private static CompletableFuture<WorldStem> create(PackRepository packs, GameType mode) {
        WorldOptions options = new WorldOptions(0L, false, false);
        var packConfig = new WorldLoader.PackConfig(packs, WorldDataConfiguration.DEFAULT, false, false);
        //? if <26.1 {
        /*LevelSettings settings = new LevelSettings("Pocket", mode, false, Difficulty.NORMAL, true,
                new net.minecraft.world.level.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
        return WorldLoader.load(init(packConfig), context -> {
            WorldDimensions.Complete complete = dimensions(context.datapackWorldgen())
                    .bake(context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
            return new WorldLoader.DataLoadOutput<>(
                    new PrimaryLevelData(settings, options, complete.specialWorldProperty(), complete.lifecycle()),
                    complete.dimensionsRegistryAccess());
        }, WorldStem::new, Util.backgroundExecutor(), Mc.mc());
        *///?} else {
        LevelSettings settings = new LevelSettings("Pocket", mode,
                new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
        return WorldLoader.load(init(packConfig), context -> {
            WorldDimensions dimensions = dimensions(context.datapackWorldgen());
            WorldDimensions.Complete complete = dimensions.bake(context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
            return new WorldLoader.DataLoadOutput<>(
                    new LevelDataAndDimensions.WorldDataAndGenSettings(
                            new PrimaryLevelData(settings, complete.specialWorldProperty(), complete.lifecycle()),
                            new net.minecraft.world.level.levelgen.WorldGenSettings(options, dimensions)),
                    complete.dimensionsRegistryAccess());
        }, WorldStem::new, Util.backgroundExecutor(), Mc.mc());
        //?}
    }

    private static CompletableFuture<WorldStem> load(LevelStorageSource.LevelStorageAccess access, PackRepository packs) throws Exception {
        //? if <26.1 {
        /*Dynamic<?> tag = access.getDataTag();
        return WorldLoader.load(init(LevelStorageSource.getPackConfig(tag, packs, false)), context -> {
            Registry<LevelStem> stems = context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
            LevelDataAndDimensions data = LevelStorageSource.getLevelDataAndDimensions(tag, context.dataConfiguration(), stems, context.datapackWorldgen());
            return new WorldLoader.DataLoadOutput<>(data.worldData(), data.dimensions().dimensionsRegistryAccess());
        }, WorldStem::new, Util.backgroundExecutor(), Mc.mc());
        *///?} else {
        Dynamic<?> raw = access.getUnfixedDataTagWithFallback();
        int version = net.minecraft.nbt.NbtUtils.getDataVersion(raw);
        var fixers = net.minecraft.util.datafix.DataFixers.getFileFixer();
        if (fixers.requiresFileFixing(version)) {
            throw new IllegalStateException("the pocket was saved by an older Minecraft and has to be converted first");
        }
        Dynamic<?> tag = net.minecraft.util.datafix.DataFixTypes.LEVEL.updateToCurrentVersion(
                net.minecraft.util.datafix.DataFixers.getDataFixer(), raw, version);
        return WorldLoader.load(init(LevelStorageSource.getPackConfig(tag, packs, false)), context -> {
            Registry<LevelStem> stems = context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
            LevelDataAndDimensions data = LevelStorageSource.getLevelDataAndDimensions(access, tag, context.dataConfiguration(), stems, context.datapackWorldgen());
            return new WorldLoader.DataLoadOutput<>(data.worldDataAndGenSettings(), data.dimensions().dimensionsRegistryAccess());
        }, WorldStem::new, Util.backgroundExecutor(), Mc.mc());
        //?}
    }

    private static WorldLoader.InitConfig init(WorldLoader.PackConfig packConfig) {
        //? if <26.1 {
        /*return new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.INTEGRATED, 2);
        *///?} else {
        return new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.INTEGRATED,
                net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
        //?}
    }

    /** An integrated server whose crash closes the pocket instead of the game. */
    private static final class Server extends IntegratedServer {
        //? if <26.1 {
        /*Server(Thread thread, Minecraft mc, LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem,
               net.minecraft.server.Services services, net.minecraft.server.level.progress.ChunkProgressListenerFactory progress) {
            super(thread, mc, access, packs, stem, services, progress);
        }
        *///?} else {
        Server(Thread thread, Minecraft mc, LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem,
               Optional<net.minecraft.world.level.gamerules.GameRules> rules, net.minecraft.server.Services services,
               net.minecraft.server.level.progress.LevelLoadListener progress) {
            super(thread, mc, access, packs, stem, rules, services, progress);
        }
        //?}

        @Override
        public void onServerCrash(CrashReport report) {
            AllerClient.LOG.error("Pocket: the server crashed\n{}", report.getFriendlyReport(net.minecraft.ReportType.CRASH));
            failure = report.getException();
        }
    }
}
