package dev.aller.platform;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.aller.compat.ModMenuCompat;
import net.fabricmc.loader.api.FabricLoader;
import dev.aller.screen.Screens;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CommonLinks;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** Navigation into the vanilla screens Aller leaves untouched. Each returns to {@code parent} on back. */
public final class Nav {
    private Nav() {}

    public static void singleplayer(Screen parent) {
        Mc.setScreen(new SelectWorldScreen(parent));
    }

    public static void multiplayer(Screen parent) {
        Mc.setScreen(new JoinMultiplayerScreen(parent));
    }

    public static void realms(Screen parent) {
        Mc.setScreen(new RealmsMainScreen(parent));
    }

    public static void options(Screen parent) {
        //? if <26.1 {
        /*Mc.setScreen(new OptionsScreen(parent, Mc.mc().options));
        *///?} else {
        Mc.setScreen(new OptionsScreen(parent, Mc.mc().options, Mc.mc().level != null));
        //?}
    }

    public static void advancements(Screen parent) {
        var player = Mc.mc().player;
        if (player != null) Mc.setScreen(new AdvancementsScreen(player.connection.getAdvancements(), parent));
    }

    public static void statistics(Screen parent) {
        var player = Mc.mc().player;
        if (player != null) Mc.setScreen(new StatsScreen(parent, player.getStats()));
    }

    /** Whether "Open to LAN" applies: a singleplayer world that is not shared yet. */
    public static boolean canOpenLan() {
        var mc = Mc.mc();
        //? if <26.1 {
        /*return mc.hasSingleplayerServer() && !mc.getSingleplayerServer().isPublished();
        *///?} else {
        return mc.hasSingleplayerServer();
        //?}
    }

    public static void lan(Screen parent) {
        //? if <26.1 {
        /*Mc.setScreen(new net.minecraft.client.gui.screens.ShareToLanScreen(parent));
        *///?} else {
        Mc.setScreen(new net.minecraft.client.gui.screens.MultiplayerOptionsScreen(parent));
        //?}
    }

    public static void playerReporting(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.social.SocialInteractionsScreen(parent));
    }

    public static void feedback(Screen parent) {
        boolean stable = net.minecraft.SharedConstants.getCurrentVersion().stable();
        ConfirmLinkScreen.confirmLinkNow(parent, stable ? CommonLinks.RELEASE_FEEDBACK : CommonLinks.SNAPSHOT_FEEDBACK);
    }

    public static void reportBug(Screen parent) {
        ConfirmLinkScreen.confirmLinkNow(parent, CommonLinks.SNAPSHOT_BUGS_FEEDBACK);
    }

    /** Vanilla's own pause menu, which has the less common entries (LAN, reporting, feedback). */
    public static void vanillaPause() {
        Screens.passThrough = true;
        try {
            Mc.setScreen(new PauseScreen(true));
        } finally {
            Screens.passThrough = false;
        }
    }

    /** Leaves the current world or server, exactly as the vanilla pause menu button does. */
    public static void disconnect() {
        if (dev.aller.feature.Pocket.quit()) return;
        if (dev.aller.feature.Pocket.returns()) {
            dev.aller.feature.Pocket.back();
            return;
        }
        if (Mc.mc().getReportingContext().hasDraftReport()) {
            // Vanilla asks what to do with an unsent chat report; let it.
            vanillaPause();
            return;
        }
        //? if <26.1 {
        /*PauseScreen.disconnectFromWorld(Mc.mc(), ClientLevel.DEFAULT_QUIT_MESSAGE);
        *///?} else {
        Mc.mc().disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        //?}
    }

    /** Whether {@link #rejoin} applies: on a multiplayer server that can be joined again by its address. */
    public static boolean canRejoin() {
        var mc = Mc.mc();
        var server = mc.getCurrentServer();
        return mc.level != null && server != null && !mc.isLocalServer() && !server.isRealm()
                && !dev.aller.feature.Pocket.inside() && !mc.getReportingContext().hasDraftReport();
    }

    /** Leaves the server and connects to it again, as a fresh join. */
    public static void rejoin() {
        var mc = Mc.mc();
        var server = mc.getCurrentServer();
        if (server != null) leaveFor(server);
    }

    /** Leaves the current world or server (a singleplayer world is saved first) and joins a server; null just leaves. */
    public static void leaveFor(net.minecraft.client.multiplayer.ServerData server) {
        var mc = Mc.mc();
        //? if <26.1 {
        /*PauseScreen.disconnectFromWorld(mc, ClientLevel.DEFAULT_QUIT_MESSAGE);
        *///?} else {
        mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        //?}
        if (server == null) return;
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new JoinMultiplayerScreen(new net.minecraft.client.gui.screens.TitleScreen()),
                mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip), server, false, null);
    }

    // The options sub-screens, for jumping straight to one from the launcher.

    public static void video(Screen parent) {
        // Sodium replaces the video settings button rather than the screen, so ask it for its own.
        if (FabricLoader.getInstance().isModLoaded("sodium") && openOther(parent, "createScreen",
                "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI")) return;
        Mc.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(parent, Mc.mc(), Mc.mc().options));
    }

    public static void controls(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.controls.ControlsScreen(parent, Mc.mc().options));
    }

    public static void keyBinds(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(parent, Mc.mc().options));
    }

    public static void mouse(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.MouseSettingsScreen(parent, Mc.mc().options));
    }

    public static void sound(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.SoundOptionsScreen(parent, Mc.mc().options));
    }

    public static void language(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.LanguageSelectScreen(parent, Mc.mc().options, Mc.mc().getLanguageManager()));
    }

    public static void chatSettings(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.ChatOptionsScreen(parent, Mc.mc().options));
    }

    public static void accessibility(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen(parent, Mc.mc().options));
    }

    public static void skin(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.SkinCustomizationScreen(parent, Mc.mc().options));
    }

    public static void online(Screen parent) {
        Mc.setScreen(new net.minecraft.client.gui.screens.options.OnlineOptionsScreen(parent, Mc.mc().options));
    }

    public static void resourcePacks(Screen parent) {
        var mc = Mc.mc();
        Mc.setScreen(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(mc.getResourcePackRepository(), repository -> {
            mc.options.updateResourcePacks(repository);
            Mc.setScreen(parent);
        }, mc.getResourcePackDirectory(), net.minecraft.network.chat.Component.translatable("resourcePack.title")));
    }

    /** Hands an address to the system's browser. @return false if it is not an address */
    public static boolean openUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            //? if <26.1 {
            /*net.minecraft.Util.getPlatform().openUri(uri);
            *///?} else {
            net.minecraft.util.Util.getPlatform().openUri(uri);
            //?}
            return true;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    public static boolean hasShaders() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static void shaderPacks(Screen parent) {
        openOther(parent, null, "net.irisshaders.iris.gui.screen.ShaderPackScreen");
    }

    /** Opens another mod's screen by name: a static factory taking the parent, or (null method) a constructor. */
    private static boolean openOther(Screen parent, String method, String... classes) {
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name);
                Object screen = method != null
                        ? type.getMethod(method, Screen.class).invoke(null, parent)
                        : type.getConstructor(Screen.class).newInstance(parent);
                Mc.setScreen((Screen) screen);
                return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // a different version of that mod: try the next name
            }
        }
        return false;
    }

    public static void pause() {
        Mc.mc().pauseGame(false);
    }

    /** A saved multiplayer server. */
    public record Server(String name, String address) {}

    /** The saved server list, in the player's order. Does file IO. */
    public static java.util.List<Server> servers() {
        java.util.List<Server> out = new java.util.ArrayList<>();
        try {
            var list = new net.minecraft.client.multiplayer.ServerList(Mc.mc());
            list.load();
            for (int i = 0; i < list.size(); i++) out.add(new Server(list.get(i).name, list.get(i).ip));
        } catch (Exception ignored) {
            // an unreadable list is an empty one
        }
        return out;
    }

    public static void join(Server server, Screen parent) {
        var data = new net.minecraft.client.multiplayer.ServerData(server.name(), server.address(),
                net.minecraft.client.multiplayer.ServerData.Type.OTHER);
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(parent != null ? parent : Mc.screen(), Mc.mc(),
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.address()), data, false, null);
    }

    /** A singleplayer world on disk. */
    public record World(String id, String name, long lastPlayed) {}

    /** The worlds that can be opened, most recently played first. Reads from disk on a background thread. */
    public static java.util.concurrent.CompletableFuture<java.util.List<World>> worlds() {
        try {
            var source = Mc.mc().getLevelSource();
            return source.loadLevelSummaries(source.findLevelCandidates()).thenApply(summaries -> {
                java.util.List<World> out = new java.util.ArrayList<>();
                for (var s : summaries) {
                    if (!s.isDisabled() && !s.isLocked()) out.add(new World(s.getLevelId(), s.getLevelName(), s.getLastPlayed()));
                }
                out.sort((a, b) -> Long.compare(b.lastPlayed(), a.lastPlayed()));
                return out;
            }).exceptionally(e -> java.util.List.of());
        } catch (Exception e) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of());
        }
    }

    public static void openWorld(World world, Screen parent) {
        Mc.mc().createWorldOpenFlows().openWorld(world.id(), () -> Mc.setScreen(parent));
    }

    /** Saves a screenshot the way F2 does and reports where it went. */
    public static void screenshot(java.util.function.Consumer<String> done) {
        var mc = Mc.mc();
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, Mc.mainTarget(), message -> mc.execute(() -> done.accept(message.getString())));
    }

    public static void toggleFullscreen() {
        var mc = Mc.mc();
        mc.getWindow().toggleFullScreen();
        mc.options.fullscreen().set(mc.getWindow().isFullscreen());
        mc.options.save();
    }

    public static boolean hasModMenu() {
        return FabricLoader.getInstance().isModLoaded("modmenu");
    }

    public static void mods(Screen parent) {
        if (hasModMenu()) Mc.setScreen(ModMenuCompat.modsScreen(parent));
    }

    public static void quit() {
        Mc.mc().stop();
    }

    /** Number of singleplayer worlds on disk. Does file IO: call off the render thread. */
    public static int countWorlds() {
        try {
            return Mc.mc().getLevelSource().findLevelCandidates().levels().size();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Number of saved multiplayer servers. Does file IO: call off the render thread. */
    public static int countServers() {
        try {
            var list = new net.minecraft.client.multiplayer.ServerList(Mc.mc());
            list.load();
            return list.size();
        } catch (Exception e) {
            return -1;
        }
    }

    public static int countMods() {
        // Count what a player would call a mod: skip the loader's built-ins and nested library jars.
        return (int) FabricLoader.getInstance().getAllMods().stream()
                .filter(m -> m.getContainingMod().isEmpty())
                .filter(m -> !m.getMetadata().getType().equals("builtin"))
                .count();
    }

    /** "Name version" for each mod a player would count, one per line. */
    public static String modList() {
        return FabricLoader.getInstance().getAllMods().stream()
                .filter(m -> m.getContainingMod().isEmpty())
                .filter(m -> !m.getMetadata().getType().equals("builtin"))
                .map(m -> m.getMetadata().getName() + " " + m.getMetadata().getVersion().getFriendlyString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public static String minecraftVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
    }

    public static String playerName() {
        return Mc.mc().getUser().getName();
    }
}
