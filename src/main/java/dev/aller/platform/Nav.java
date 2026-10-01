package dev.aller.platform;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.aller.compat.ModMenuCompat;
import net.fabricmc.loader.api.FabricLoader;
import dev.aller.screen.Screens;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
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

    public static String minecraftVersion() {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
    }

    public static String playerName() {
        return Mc.mc().getUser().getName();
    }
}
