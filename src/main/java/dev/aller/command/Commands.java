package dev.aller.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aller.AllerClient;
import dev.aller.command.Command.Group;
import dev.aller.config.Config;
import dev.aller.feature.AutoProfiles;
import dev.aller.feature.Browser;
import dev.aller.feature.Replay;
import dev.aller.feature.Waypoints;
import dev.aller.feature.Waypoints.Waypoint;
import dev.aller.hud.Hud;
import dev.aller.hud.HudModule;
import dev.aller.module.Module;
import dev.aller.module.Modules;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.platform.Nav;
import dev.aller.platform.ScreenHost;
import dev.aller.screen.HudEditorScreen;
import dev.aller.screen.OnboardingScreen;
import dev.aller.screen.PaletteScreen;
import dev.aller.screen.WardrobeScreen;
import dev.aller.screen.ChatHistoryScreen;
import dev.aller.screen.palette.ChatFormatsPage;
import dev.aller.screen.palette.CustomActionsPage;
import dev.aller.screen.palette.Page;
import dev.aller.screen.palette.ProfilesPage;
import dev.aller.screen.palette.SettingsPage;
import dev.aller.screen.palette.StatsPage;
import dev.aller.screen.palette.WaypointsPage;
import dev.aller.setting.Configurable;
import dev.aller.setting.Setting;
import dev.aller.setting.Settings;
import dev.aller.ui.Toasts;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The launcher's catalogue. The fixed part (screens, game actions, Minecraft's options, every mod
 * and every setting) is built once; profiles, waypoints, custom actions and the like are read
 * fresh each time the launcher opens. To add an action, add a line to one of the sections below.
 */
public final class Commands {
    private static final Pattern ITEMS = Pattern.compile("(\\d{1,9})\\s*(items?|stacks?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COORDS = Pattern.compile("(-?\\d{1,8})[ ,]+(-?\\d{1,8})(?:[ ,]+(-?\\d{1,8}))?");

    private static List<Command> fixed;
    private static List<Nav.World> worlds = List.of();
    private static long worldsAt;
    private static String seenServer;

    private Commands() {}

    /** Everything the launcher can run right now, available or not. */
    public static List<Command> snapshot() {
        if (fixed == null) fixed = build();
        List<Command> out = new ArrayList<>(fixed);
        profiles(out);
        custom(out);
        if (Game.inWorld()) for (Waypoint w : Waypoints.all()) out.add(waypoint(w));
        if (Browser.usable()) {
            for (Browser.Mark mark : Browser.bookmarks()) {
                out.add(new Command("bookmark." + mark.url(), mark.title(), Group.NAVIGATE).detail(Browser.host(mark.url())).keywords("bookmark web site")
                        .hidePalette().after().run(parent -> Browser.open(parent, mark.url())));
            }
        }
        return out;
    }

    /** The command a remembered key names, with its value if it had one ("opt.fov=90"); null if it is gone. */
    public static Command find(List<Command> from, String key) {
        for (Command c : from) if (key.equals(c.key)) return c;
        int eq = key.indexOf('=');
        if (eq <= 0) return null;
        String base = key.substring(0, eq);
        for (Command c : from) if (base.equals(c.key)) return c.withArg(key.substring(eq + 1));
        return null;
    }

    /** Called when the launcher opens: refreshes what has to be read from disk, off the render thread. */
    public static void prime() {
        if (Game.inWorld() || System.currentTimeMillis() - worldsAt < 5000) return;
        worldsAt = System.currentTimeMillis();
        Nav.worlds().thenAccept(list -> Mc.mc().execute(() -> worlds = list));
    }

    /** Remembers the server being played on, for "Reconnect". Cheap enough to call every frame. */
    public static void noteServer() {
        String address = Game.serverAddress();
        if (address == null || address.equals(seenServer)) return;
        seenServer = address;
        var server = Mc.mc().getCurrentServer();
        JsonObject o = new JsonObject();
        o.addProperty("name", server == null || server.name == null ? address : server.name);
        o.addProperty("address", address);
        AllerClient.config().setExtra("last_server", o);
    }

    private static Nav.Server lastServer() {
        try {
            JsonElement saved = AllerClient.config().extra("last_server");
            if (saved == null) return null;
            JsonObject o = saved.getAsJsonObject();
            return new Nav.Server(o.get("name").getAsString(), o.get("address").getAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- the catalogue ---------------------------------------------------------------------------

    private static List<Command> build() {
        List<Command> c = new ArrayList<>();
        BooleanSupplier world = Game::inWorld, menus = () -> !Game.inWorld();
        BooleanSupplier online = () -> Game.inWorld() && Game.serverAddress() != null;

        // Go to: Aller's own screens and pages.
        c.add(new Command("go.mods", "Browse mods", Group.NAVIGATE).detail("The full palette, with every mod by category")
                .keywords("palette modules list all").hidePalette().after()
                .run(parent -> Mc.setScreen(new ScreenHost(new PaletteScreen(parent)))));
        c.add(new Command("go.hud", "Edit HUD layout", Group.NAVIGATE).detail("Drag, resize and snap everything on your HUD")
                .keywords("hud editor move position arrange").when(world).suggest(true, false).hidePalette().after()
                .run(parent -> Mc.setScreen(new ScreenHost(new HudEditorScreen(parent)))));
        page(c, "ui", "UI settings", "Accent colour, font, sizes, backgrounds and motion", "options preferences aller theme interface look accent blur zoom", SettingsPage::ui)
                .suggest(false, true);
        page(c, "client", "Client settings", "Aller Client's keys and behaviour", "options preferences aller keybinds", SettingsPage::new);
        page(c, "profiles", "Profiles", "Switch between setups, or let rules switch them for you", "profile preset rules auto", ProfilesPage::new);
        page(c, "waypoints", "Waypoints", "Manage saved places and death markers", "waypoint marker list places", WaypointsPage::new);
        page(c, "stats", "Session stats", "Playtime, FPS and combat numbers", "statistics graph history playtime", StatsPage::new);
        page(c, "custom", "Custom actions", "Your own chat and command shortcuts", "macro message command shortcut manage edit", CustomActionsPage::new);
        page(c, "chatformats", "Chat formats", "How each server's chat lines and private messages are read", "mention whisper separator author regex server", ChatFormatsPage::new);
        c.add(new Command("go.chathistory", "Search chat history", Group.NAVIGATE).detail("Everything said, back through old game logs")
                .keywords("find messages log regex past").suggest(true, false).hidePalette().after()
                .run(parent -> Mc.setScreen(new ScreenHost(new ChatHistoryScreen(parent)))));

        c.add(new Command("go.onboarding", "Replay onboarding", Group.NAVIGATE).detail("The intro and the first-run setup, from the start")
                .keywords("welcome tutorial intro setup first run tour guide animation").after()
                .run(parent -> Mc.setScreen(new ScreenHost(new OnboardingScreen(parent)))));
        c.add(new Command("go.wardrobe", "Skin wardrobe", Group.NAVIGATE).detail("Change skin, arm width, and see the skins you wore before")
                .keywords("skins upload slim classic alex steve outfit history namemc").suggest(false, true).hidePalette().after()
                .run(parent -> Mc.setScreen(new ScreenHost(new WardrobeScreen(parent)))));

        c.add(new Command("go.screenshots", "Screenshots", Group.NAVIGATE).detail("Every screenshot, by day and by world or server")
                .keywords("pictures photos gallery images captures f2 manager favourites").suggest(true, true).hidePalette().after()
                .run(parent -> Mc.setScreen(new ScreenHost(new dev.aller.screen.shots.ShotsScreen(parent)))));

        c.add(new Command("go.browser", "Web browser", Group.NAVIGATE).detail("Tabs, bookmarks and search in a panel over the game")
                .keywords("internet web page site tabs bookmarks wiki").when(Browser::usable).suggest(true, true).after()
                .run(parent -> Browser.open(parent, null)));
        c.add(new Command("web", "Search the web", Group.NAVIGATE).detail("Or type: web minecraft wiki").alias("web")
                .keywords("google internet browse address url site look up").when(Browser::usable).after()
                .step(new Step.Text("Web", "Search, or an address", "Opens in Aller Client's browser.", text -> {
                    Browser.open(Mc.screen(), text);
                    return null;
                }).max(512).inline(text -> Browser.isAddress(text) ? "Open " + text : "Search " + Browser.engine().label() + " for " + quote(text))));
        c.add(new Command("go.private", "Private browsing", Group.NAVIGATE).detail("A separate set of tabs that keeps no history, cookies or suggestions")
                .keywords("incognito inprivate secret web browser").when(Browser::usable).after()
                .run(parent -> {
                    Browser.setIncognito(true);
                    Browser.open(parent, null);
                }));

        // Go to: Minecraft's screens.
        go(c, "options", "Options", "Minecraft's settings", "settings preferences", Nav::options).suggest(false, true);
        go(c, "video", "Video settings", "Graphics, render distance, GUI scale", "graphics display sodium quality", Nav::video);
        go(c, "controls", "Controls", "Mouse, keys and movement", "input sneak sprint", Nav::controls);
        go(c, "keys", "Key binds", "Every Minecraft key", "keybinds bindings hotkeys shortcuts", Nav::keyBinds);
        go(c, "mouse", "Mouse settings", "Sensitivity, scrolling, raw input", "sensitivity invert scroll", Nav::mouse);
        go(c, "sound", "Music and sounds", "Volume for each kind of sound", "audio volume subtitles device", Nav::sound);
        go(c, "language", "Language", "", "locale translation", Nav::language);
        go(c, "chat", "Chat settings", "Visibility, size, links", "messages", Nav::chatSettings);
        go(c, "accessibility", "Accessibility settings", "", "narrator contrast subtitles", Nav::accessibility);
        go(c, "skin", "Skin customisation", "Cape, jacket, sleeves, hat", "customization layers main hand", Nav::skin);
        go(c, "online", "Online options", "", "realms difficulty telemetry", Nav::online);
        go(c, "packs", "Resource packs", "", "texture pack", Nav::resourcePacks);
        go(c, "getpacks", "Get resource packs", "Browse and download from Modrinth", "modrinth download texture pack store shop more", parent -> {
            Nav.resourcePacks(parent);
            dev.aller.screen.store.StoreScreen.open(Mc.screen(), dev.aller.feature.store.Kind.RESOURCE_PACKS);
        });
        go(c, "getshaders", "Get shader packs", "Browse and download from Modrinth", "modrinth download iris shaders store shop more", parent -> {
            Nav.shaderPacks(parent);
            if (dev.aller.screen.store.PackListExtras.kind(Mc.screen()) != null) {
                dev.aller.screen.store.StoreScreen.open(Mc.screen(), dev.aller.feature.store.Kind.SHADER_PACKS);
            }
        }).when(Nav::hasShaders);
        go(c, "shaders", "Shader packs", "Iris", "iris shaders", Nav::shaderPacks).when(Nav::hasShaders);
        go(c, "installed", "Installed mods", "Mod Menu's list", "modmenu mod list fabric", Nav::mods).when(Nav::hasModMenu);
        go(c, "singleplayer", "Singleplayer", "Your worlds", "worlds saves create", Nav::singleplayer).when(menus).suggest(false, true);
        go(c, "multiplayer", "Multiplayer", "The server list", "servers join", Nav::multiplayer).when(menus).suggest(false, true);
        go(c, "realms", "Realms", "", "minecraft realms", Nav::realms).when(menus);
        go(c, "pause", "Pause menu", "", "game menu", parent -> Nav.pause()).when(world);
        go(c, "statistics", "Statistics", "Minecraft's own counters for this world", "stats blocks mined", Nav::statistics).when(world);
        go(c, "advancements", "Advancements", "", "achievements progress", Nav::advancements).when(world);
        go(c, "lan", "Open to LAN", "Let others on your network join", "share local multiplayer", Nav::lan).when(() -> Game.inWorld() && Nav.canOpenLan());
        go(c, "social", "Player reporting", "Hide, mute or report players", "social interactions block", Nav::playerReporting).when(online);

        // Go to: folders.
        var mc = Mc.mc();
        folder(c, "screenshots", "Open screenshots folder", mc.gameDirectory.toPath().resolve("screenshots"));
        folder(c, "clips", "Open replay clips folder", mc.gameDirectory.toPath().resolve("aller-clips"));
        folder(c, "packs", "Open resource packs folder", mc.getResourcePackDirectory());
        folder(c, "chat", "Open chat logs folder", dev.aller.feature.ChatLog.dir());
        folder(c, "config", "Open Aller Client's config folder", AllerClient.config().dir());
        folder(c, "logs", "Open logs folder", mc.gameDirectory.toPath().resolve("logs"));
        folder(c, "game", "Open game folder", mc.gameDirectory.toPath());

        // Game and session.
        c.add(new Command("game.screenshot", "Take screenshot", Group.GAME).detail("Without the launcher in it").keywords("capture picture f2")
                .suggest(true, false).after().run(() -> Nav.screenshot(message -> {
                    // With the preview on, the card says it.
                    if (!AllerClient.options().shotCard.get()) Toasts.info("Screenshot", message);
                })));
        c.add(new Command("game.fullscreen", "Fullscreen", Group.GAME).keywords("window windowed f11 toggle")
                .state(() -> Mc.mc().getWindow().isFullscreen()).run(Nav::toggleFullscreen));
        c.add(new Command("game.hidehud", "Hide HUD", Group.GAME).detail("Hotbar, chat and every HUD element").keywords("f1 gui interface clean")
                .when(world).state(Mc::hudHidden).run(Mc::toggleHud));
        c.add(new Command("game.camera", "Change camera view", Group.GAME).detail("First person, behind, in front").keywords("third person perspective f5")
                .when(world).value(() -> Settings.Choice.label(Mc.mc().options.getCameraType()))
                .run(() -> Mc.mc().options.setCameraType(Mc.mc().options.getCameraType().cycle())));
        c.add(new Command("game.coords", "Copy coordinates", Group.GAME).keywords("position location xyz clipboard").when(world)
                .suggest(true, false).value(Commands::here).run(() -> copy(here(), "Coordinates copied")));
        c.add(new Command("game.nether", "Nether coordinates for here", Group.GAME).detail("Enter copies them").keywords("portal convert divide overworld")
                .when(() -> Game.inWorld() && Game.dimensionId().equals("overworld"))
                .value(() -> scaled(1 / 8.0)).run(() -> copy(scaled(1 / 8.0), "Nether coordinates copied")));
        c.add(new Command("game.overworld", "Overworld coordinates for here", Group.GAME).detail("Enter copies them").keywords("portal convert multiply nether")
                .when(() -> Game.inWorld() && Game.dimensionId().equals("the_nether"))
                .value(() -> scaled(8)).run(() -> copy(scaled(8), "Overworld coordinates copied")));
        c.add(new Command("copy.address", "Copy server IP", Group.GAME).keywords("address clipboard host").when(online)
                .value(Game::serverAddress).run(() -> copy(Game.serverAddress(), "Server IP copied")));
        c.add(new Command("copy.seed", "Copy world seed", Group.GAME).detail("Singleplayer only: servers do not tell the client their seed")
                .keywords("clipboard generation").when(() -> Game.inWorld() && Game.seed() != null)
                .value(() -> String.valueOf(Game.seed())).run(() -> copy(String.valueOf(Game.seed()), "Seed copied")));
        c.add(new Command("copy.tp", "Copy teleport command", Group.GAME).detail("A /tp to where you are standing").keywords("clipboard position share tp")
                .when(world).value(() -> "/tp " + here()).run(() -> copy("/tp @s " + here(), "Teleport command copied")));
        c.add(new Command("copy.place", "Copy location with dimension", Group.GAME).detail("For pasting to someone: coordinates and where they are")
                .keywords("clipboard position share coordinates").when(world).value(Commands::place).run(() -> copy(place(), "Location copied")));
        c.add(new Command("copy.biome", "Copy biome", Group.GAME).keywords("clipboard").when(world)
                .value(() -> Game.pretty(Game.biomeId())).run(() -> copy(Game.pretty(Game.biomeId()), "Biome copied")));
        c.add(new Command("copy.facing", "Copy facing direction", Group.GAME).detail("Compass direction, yaw and pitch").keywords("clipboard look angle rotation")
                .when(world).value(Commands::facing).run(() -> copy(facing(), "Direction copied")));
        c.add(new Command("copy.world", "Copy world name", Group.GAME).keywords("clipboard save").when(() -> Game.inWorld() && Game.worldName() != null)
                .value(Game::worldName).run(() -> copy(Game.worldName(), "World name copied")));
        c.add(new Command("copy.name", "Copy your username", Group.GAME).keywords("clipboard player account ign")
                .value(Nav::playerName).run(() -> copy(Nav.playerName(), "Username copied")));
        c.add(new Command("copy.uuid", "Copy your UUID", Group.GAME).keywords("clipboard player account id")
                .run(() -> copy(String.valueOf(Mc.mc().getUser().getProfileId()), "UUID copied")));
        c.add(new Command("copy.version", "Copy game version", Group.GAME).detail("Minecraft and Aller Client versions, for bug reports").keywords("clipboard about")
                .value(Nav::minecraftVersion).run(() -> copy("Minecraft " + Nav.minecraftVersion() + ", " + AllerClient.NAME + " " + AllerClient.VERSION, "Version copied")));
        c.add(new Command("copy.mods", "Copy mod list", Group.GAME).detail("Every installed mod and its version, one per line").keywords("clipboard installed fabric bug report")
                .value(() -> Nav.countMods() + " mods").run(() -> {
                    Mc.setClipboard(Nav.modList());
                    Toasts.info("Mod list copied", Nav.countMods() + " mods");
                }));
        c.add(new Command("game.clearchat", "Clear chat", Group.GAME).detail("Only on your screen").keywords("messages wipe").when(world).run(Mc::clearChat));
        c.add(new Command("game.reload", "Reload resource packs", Group.GAME).detail("Textures, models, sounds and shaders").keywords("f3 t refresh textures")
                .after().run(() -> Mc.mc().reloadResourcePacks()));
        c.add(new Command("send", "Send a message or command", Group.GAME).detail("One chat line, or a command starting with /").alias("say")
                .keywords("chat run").when(world).after()
                .step(new Step.Text("Send", "Message, or /command", "Sent once, exactly as if typed in chat.", text -> {
                    Game.send(CustomActions.clean(text, CustomActions.MESSAGE_MAX));
                    return null;
                }).max(CustomActions.MESSAGE_MAX).inline(text -> text.startsWith("/") ? "Run " + text : "Say " + quote(text))));
        c.add(new Command("game.reconnect", "Reconnect to last server", Group.GAME).keywords("rejoin join back").suggest(false, true)
                .when(() -> !Game.inWorld() && lastServer() != null).value(() -> lastServer() == null ? "" : lastServer().name())
                .after().run(parent -> Nav.join(lastServer(), parent)));
        c.add(new Command("game.join", "Join server", Group.GAME).detail("Pick one from your server list").keywords("connect multiplayer play")
                .when(menus).step(new Step.Pick("Join", "No saved servers yet. Add one from Multiplayer.", Commands::servers)));
        c.add(new Command("game.continue", "Continue last world", Group.GAME).keywords("resume play singleplayer").suggest(false, true)
                .when(() -> !Game.inWorld() && !worlds.isEmpty()).value(() -> worlds.isEmpty() ? "" : worlds.get(0).name())
                .after().run(parent -> Nav.openWorld(worlds.get(0), parent)));
        c.add(new Command("game.world", "Open world", Group.GAME).detail("Pick one of your singleplayer worlds").keywords("load play singleplayer save")
                .when(menus).step(new Step.Pick("Open", "No worlds yet. Create one from Singleplayer.", Commands::worldOptions)));
        c.add(new Command("game.leave", "Save and quit to title", Group.GAME).keywords("exit leave world main menu disconnect")
                .when(() -> Game.inWorld() && Mc.mc().isLocalServer() && !dev.aller.feature.Pocket.inside()).danger("leave this world").after().run(Nav::disconnect));
        c.add(new Command("game.disconnect", "Disconnect", Group.GAME).keywords("leave server exit main menu")
                .when(() -> Game.inWorld() && !Mc.mc().isLocalServer()).danger("disconnect").after().run(Nav::disconnect));
        c.add(new Command("pocket.enter", "Enter the pocket", Group.GAME).detail("Your private room, beside the server").keywords("pocket dimension room base")
                .when(() -> Game.inWorld() && dev.aller.module.Modules.POCKET.enabled() && !dev.aller.feature.Pocket.active())
                .run(dev.aller.feature.Pocket::toggle));
        c.add(new Command("pocket.leave", "Leave the pocket", Group.GAME).detail("Back to where you stand on the server").keywords("pocket dimension exit return")
                .when(dev.aller.feature.Pocket::inside).run(dev.aller.feature.Pocket::toggle));
        c.add(new Command("game.quit", "Quit game", Group.GAME).keywords("exit close minecraft").danger("quit Minecraft").after().run(Nav::quit));

        options(c);

        // Aller features.
        c.add(new Command("wp.here", "Add waypoint here", Group.ALLER).detail("Save your current position").keywords("waypoint new mark place")
                .when(world).suggest(true, false).hidePalette().run(() -> waypointHere("Waypoint " + (Waypoints.all().size() + 1))));
        c.add(new Command("wp.named", "Add named waypoint here", Group.ALLER).detail("Or type: waypoint Base").alias("waypoint")
                .keywords("waypoint new mark place name").when(world)
                .step(new Step.Text("Waypoint", "Name", "Saved at your feet in this dimension.", name -> {
                    waypointHere(name.trim());
                    return null;
                }).max(28).inline(name -> "Add waypoint " + quote(name) + " here")));
        c.add(new Command("wp.show", "Show all waypoints", Group.ALLER).keywords("markers visible unhide").when(world).run(() -> showWaypoints(true)));
        c.add(new Command("wp.hide", "Hide all waypoints", Group.ALLER).keywords("markers invisible").when(world).run(() -> showWaypoints(false)));
        c.add(new Command("wp.deaths", "Remove death markers", Group.ALLER).keywords("waypoints clear died").when(world)
                .danger("remove every death marker here").run(() -> {
                    List<Waypoint> deaths = Waypoints.all().stream().filter(w -> w.death).toList();
                    deaths.forEach(Waypoints::remove);
                    Toasts.info("Death markers removed", deaths.size() + " in this world");
                }));
        c.add(new Command("wp.clear", "Remove every waypoint here", Group.ALLER).keywords("waypoints clear delete all").when(world)
                .danger("delete all waypoints in this world").run(() -> {
                    List<Waypoint> all = new ArrayList<>(Waypoints.all());
                    all.forEach(Waypoints::remove);
                    Toasts.info("Waypoints removed", all.size() + " in this world");
                }));
        c.add(new Command("replay.save", "Save replay clip", Group.ALLER).detail("Write the last moments of gameplay to a video file")
                .keywords("clip record video highlight").when(() -> Game.inWorld() && Modules.REPLAY.enabled()).suggest(true, false)
                .hidePalette().run(Replay::save));
        c.add(new Command("replay.clear", "Clear replay buffer", Group.ALLER).detail("Forget what has been recorded so far").keywords("clip discard")
                .when(() -> Game.inWorld() && Modules.REPLAY.enabled()).run(Replay::clear));
        c.add(new Command("profile.new", "New profile", Group.ALLER).detail("Starts as a copy of your current setup").keywords("create duplicate copy preset")
                .step(new Step.Text("New profile", "Name", "Letters, numbers, spaces and dashes. It becomes the active profile.", name -> {
                    String id = Config.sanitise(name);
                    AutoProfiles.manualSwitch(id);
                    Toasts.info("Profile: " + id, "Created as a copy of your current setup");
                    return null;
                }).max(24)));
        c.add(new Command("profile.delete", "Delete profile", Group.ALLER).keywords("remove preset")
                .step(new Step.Pick("Delete", "Only the default and the active profile exist; neither can be deleted.", Commands::deletableProfiles)));
        c.add(new Command("config.save", "Save settings now", Group.ALLER).detail("They also save by themselves two seconds after a change")
                .keywords("config write").run(() -> {
                    AllerClient.config().save();
                    Toasts.info("Settings saved", "Profile: " + AllerClient.config().activeProfile());
                }));
        c.add(new Command("hud.reset", "Reset HUD layout", Group.ALLER).detail("Every element back to its default spot").keywords("positions arrange")
                .danger("move every HUD element back").run(() -> {
                    for (HudModule h : Hud.modules()) h.resetPosition();
                    AllerClient.config().markDirty();
                    Toasts.info("HUD layout reset", "Every element is back in its default spot");
                }));
        c.add(new Command("mods.hudoff", "Turn every HUD element off", Group.ALLER).keywords("disable hide all clean")
                .danger("switch off every HUD element").run(() -> setAll(m -> m instanceof HudModule, false, "HUD elements off")));
        c.add(new Command("mods.off", "Turn every mod off", Group.ALLER).keywords("disable all vanilla")
                .danger("switch off every mod in this profile").run(() -> setAll(m -> true, false, "Every mod off")));
        c.add(new Command("mods.defaults", "Reset every mod to defaults", Group.ALLER).keywords("factory restore settings")
                .danger("reset every mod in this profile").run(() -> {
                    for (Module m : AllerClient.modules().all()) m.resetToDefaults();
                    AllerClient.config().markDirty();
                    Toasts.info("Mods reset", "Profile " + AllerClient.config().activeProfile() + " is back to defaults");
                }));
        c.add(new Command("browser.clear", "Clear browsing data", Group.ALLER).detail("History, cookies, sign-ins and cache. Bookmarks stay")
                .keywords("web browser history cookies cache forget privacy").when(Browser::usable)
                .danger("sign out of every site and forget your history").run(Browser::clearData));
        c.add(new Command("custom.new", "New custom action", Group.ALLER).detail("A name for a chat line or command you send often")
                .keywords("create add macro shortcut message command").step(newCustom()));

        // Client settings, then every mod and its settings.
        settings(c, "aller.", "", "Aller Client setting", Group.ALLER, AllerClient.options(), null);
        for (Module m : AllerClient.modules().all()) {
            c.add(new Command("mod." + m.id, m.name, Group.MOD).detail(m.description).keywords(String.join(" ", m.keywords) + " " + m.category.label.toLowerCase())
                    .hidePalette().run(() -> AllerClient.modules().userToggle(m)).module(m)
                    .menu(() -> List.of(new Command(null, "Open settings", Group.RESULT).detail(m.name).after()
                            .run(parent -> Mc.setScreen(new ScreenHost(new PaletteScreen(parent, m)))))));
            settings(c, "set." + m.id + ".", m.name + ": ", m.category.label + " mod", Group.SETTING, m, m);
        }
        return c;
    }

    private static Command go(List<Command> c, String key, String name, String detail, String keywords, Consumer<Screen> open) {
        Command command = new Command("go." + key, name, Group.NAVIGATE).detail(detail).keywords(keywords).after().run(open);
        c.add(command);
        return command;
    }

    private static Command page(List<Command> c, String key, String name, String detail, String keywords, Supplier<Page> page) {
        Command command = new Command("go." + key, name, Group.NAVIGATE).detail(detail).keywords(keywords).page(page).hidePalette().after()
                .run(parent -> Mc.setScreen(new ScreenHost(new PaletteScreen(parent, page.get()))));
        c.add(command);
        return command;
    }

    private static void folder(List<Command> c, String key, String name, java.nio.file.Path dir) {
        c.add(new Command("folder." + key, name, Group.NAVIGATE).detail("In your file manager").keywords("directory files explorer finder")
                .run(() -> Mc.openFolder(dir)));
    }

    // ---- Minecraft's options ---------------------------------------------------------------------

    private static void options(List<Command> c) {
        whole(c, "fov", "FOV", "fov", "field of view", Options::fov, 30, 110, 1, v -> Math.round(v) + "°");
        whole(c, "render", "Render distance", "render", "chunks view far", Options::renderDistance, 2, 32, 1, v -> Math.round(v) + " chunks");
        whole(c, "simulation", "Simulation distance", "simulation", "chunks ticking", Options::simulationDistance, 5, 32, 1, v -> Math.round(v) + " chunks");
        percent(c, "brightness", "Brightness", "brightness", "gamma light dark", Options::gamma, 100, 0, 100, 5);
        whole(c, "maxfps", "Max framerate", "maxfps", "fps limit cap frame rate", Options::framerateLimit, 10, 260, 10,
                v -> v >= 260 ? "Unlimited" : Math.round(v) + " fps");
        whole(c, "guiscale", "GUI scale", "gui", "interface size", Options::guiScale, 0, 8, 1, v -> v < 0.5f ? "Auto" : Math.round(v) + "x");
        percent(c, "sensitivity", "Mouse sensitivity", "sensitivity", "sens look speed", Options::sensitivity, 200, 0, 200, 1);
        percent(c, "foveffects", "FOV effects", null, "sprint speed zoom", Options::fovEffectScale, 100, 0, 100, 5);
        percent(c, "distortion", "Distortion effects", null, "nausea portal wobble", Options::screenEffectScale, 100, 0, 100, 5);
        percent(c, "entitydistance", "Entity distance", null, "mobs render far", Options::entityDistanceScaling, 100, 50, 500, 25);
        percent(c, "chatopacity", "Chat text opacity", null, "transparency", Options::chatOpacity, 100, 0, 100, 5);
        percent(c, "chatscale", "Chat size", null, "text scale", Options::chatScale, 100, 0, 100, 5);
        whole(c, "blur", "Menu background blur", null, "blurriness", Options::menuBackgroundBlurriness, 0, 10, 1, v -> v < 0.5f ? "Off" : Integer.toString(Math.round(v)));
        whole(c, "biomeblend", "Biome blend", null, "smooth colours grass", Options::biomeBlendRadius, 0, 7, 1,
                v -> v < 0.5f ? "Off" : (Math.round(v) * 2 + 1) + "x" + (Math.round(v) * 2 + 1));
        for (SoundSource source : SoundSource.values()) {
            String name = switch (source) {
                case RECORDS -> "Jukebox and note block";
                case HOSTILE -> "Hostile creature";
                case NEUTRAL -> "Friendly creature";
                case BLOCKS -> "Block";
                case PLAYERS -> "Player";
                case UI -> "Interface";
                default -> Settings.Choice.label(source);
            } + " volume";
            percent(c, "volume." + source.name().toLowerCase(), name, source == SoundSource.MASTER ? "volume" : null, "sound audio loud quiet mute",
                    o -> o.getSoundSourceOptionInstance(source), 100, 0, 100, 5);
        }
        flag(c, "vsync", "VSync", "vertical sync tearing", Options::enableVsync);
        flag(c, "bobbing", "View bobbing", "camera shake walk", Options::bobView);
        flag(c, "shadows", "Entity shadows", "", Options::entityShadows);
        flag(c, "autojump", "Auto-jump", "step", Options::autoJump);
        flag(c, "subtitles", "Subtitles", "captions sound", Options::showSubtitles);
        flag(c, "togglesprint", "Sprint: toggle instead of hold", "", Options::toggleSprint);
        flag(c, "togglesneak", "Sneak: toggle instead of hold", "crouch", Options::toggleCrouch);
        choice(c, "clouds", "Clouds", "sky", Options::cloudStatus, net.minecraft.client.CloudStatus.values());
        choice(c, "particles", "Particles", "effects", Options::particles, net.minecraft.server.level.ParticleStatus.values());
    }

    private static void whole(List<Command> c, String key, String name, String alias, String keywords,
            Function<Options, OptionInstance<Integer>> option, int min, int max, int step, Function<Float, String> format) {
        Supplier<OptionInstance<Integer>> o = () -> option.apply(Mc.mc().options);
        Step.Num num = new Step.Num(name, min, max, step, () -> (float) o.get().get(), v -> {
            int value = Math.round(v);
            // The largest GUI scale depends on the window; anything above it would be thrown away.
            if (key.equals("guiscale")) value = Math.min(value, Mc.mc().getWindow().calculateScale(0, Mc.mc().options.forceUnicodeFont().get()));
            o.get().set(value);
        }, format).saved(() -> Mc.mc().options.save());
        c.add(new Command("opt." + key, name, Group.OPTION).alias(alias).keywords(keywords).value(() -> format.apply(num.get.get())).step(num));
    }

    /** @param scale what the option's 0 to 1 is multiplied by to read as a percentage */
    private static void percent(List<Command> c, String key, String name, String alias, String keywords,
            Function<Options, OptionInstance<Double>> option, float scale, int min, int max, int step) {
        Supplier<OptionInstance<Double>> o = () -> option.apply(Mc.mc().options);
        Function<Float, String> format = v -> Math.round(v) + "%";
        Step.Num num = new Step.Num(name, min, max, step, () -> (float) (o.get().get() * scale), v -> o.get().set((double) v / scale), format)
                .saved(() -> Mc.mc().options.save());
        c.add(new Command("opt." + key, name, Group.OPTION).alias(alias).keywords(keywords).value(() -> format.apply(num.get.get())).step(num));
    }

    private static void flag(List<Command> c, String key, String name, String keywords, Function<Options, OptionInstance<Boolean>> option) {
        c.add(new Command("opt." + key, name, Group.OPTION).keywords(keywords).state(() -> option.apply(Mc.mc().options).get()).run(() -> {
            OptionInstance<Boolean> o = option.apply(Mc.mc().options);
            o.set(!o.get());
            Mc.mc().options.save();
        }));
    }

    private static <E extends Enum<E>> void choice(List<Command> c, String key, String name, String keywords,
            Function<Options, OptionInstance<E>> option, E[] values) {
        c.add(new Command("opt." + key, name, Group.OPTION).keywords(keywords)
                .value(() -> Settings.Choice.label(option.apply(Mc.mc().options).get()))
                .step(new Step.Pick(name, "", () -> {
                    List<Command> out = new ArrayList<>();
                    for (E e : values) {
                        out.add(new Command(null, Settings.Choice.label(e), Group.RESULT).run(() -> {
                            option.apply(Mc.mc().options).set(e);
                            Mc.mc().options.save();
                        }));
                    }
                    return out;
                })));
    }

    // ---- Aller's settings ------------------------------------------------------------------------

    /** One command per setting of a mod or of the client, so any of them can be changed by name. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void settings(List<Command> c, String keyPrefix, String namePrefix, String detail, Group group, Configurable owner, Module module) {
        Runnable dirty = AllerClient.config()::markDirty;
        for (Setting<?> s : owner.settings()) {
            String name = namePrefix + (s.id.startsWith("restyle_") && !s.id.equals("restyle_menus") ? "Restyle menu: " + s.name : s.name);
            String about = s.description.isEmpty() ? detail : s.description;
            Command command = new Command(keyPrefix + s.id, name, group).detail(about).when(s::visible)
                    .keywords(s.id.replace('_', ' ') + (s.id.endsWith("scale") ? " size ui gui zoom" : ""));
            if (s instanceof Settings.Bool b) {
                command.state(b::get).run(() -> {
                    b.toggle();
                    dirty.run();
                });
            } else if (s instanceof Settings.Num n) {
                command.value(n::display).step(new Step.Num(name, n.min, n.max, n.step, n::get, n::set, n::display).saved(dirty));
            } else if (s instanceof Settings.Choice ch) {
                command.value(() -> Settings.Choice.label((Enum<?>) ch.get())).step(new Step.Pick(name, "", () -> {
                    List<Command> out = new ArrayList<>();
                    for (Object option : ch.options) {
                        out.add(new Command(null, Settings.Choice.label((Enum<?>) option), Group.RESULT).run(() -> {
                            ch.set((Enum) option);
                            dirty.run();
                        }));
                    }
                    return out;
                }));
            } else if (s instanceof Settings.Color col) {
                command.color(col.get() | 0xFF000000).value(() -> hex(col.get(), col.alpha))
                        .step(new Step.Text(name, col.alpha ? "#AARRGGBB" : "#RRGGBB", "A hex colour, with or without the #.", text -> {
                            col.set(parseColor(text, col.alpha));
                            dirty.run();
                            return null;
                        }).max(9).initial(() -> hex(col.get(), col.alpha)).valid(text -> parseColor(text, col.alpha) != null)
                                .inline(text -> "Set " + name.toLowerCase() + " to " + text));
                if (s == AllerClient.options().accent) command.alias("accent");
            } else if (s instanceof Settings.Text t) {
                command.value(t::get).step(new Step.Text(name, t.name, "", text -> {
                    t.set(text.length() > t.maxLength ? text.substring(0, t.maxLength) : text);
                    dirty.run();
                    return null;
                }).max(t.maxLength).initial(t::get));
            } else {
                continue; // keys are bound on the settings page, where the capture and clash warning live
            }
            c.add(command);
        }
    }

    private static String hex(int argb, boolean alpha) {
        return alpha ? String.format("#%08X", argb) : String.format("#%06X", argb & 0xFFFFFF);
    }

    private static Integer parseColor(String text, boolean alpha) {
        String hex = text.trim().replace("#", "");
        if (!hex.matches("[0-9a-fA-F]{6}") && !(alpha && hex.matches("[0-9a-fA-F]{8}"))) return null;
        int argb = (int) Long.parseLong(hex, 16);
        return hex.length() == 6 ? argb | 0xFF000000 : argb;
    }

    // ---- lists read when asked for ---------------------------------------------------------------

    private static void profiles(List<Command> out) {
        String active = AllerClient.config().activeProfile();
        for (String name : AllerClient.config().profileNames()) {
            if (name.equals(active)) continue;
            out.add(new Command("profile." + name, "Switch to profile: " + name, Group.ALLER).detail("Load this setup now").keywords("preset config")
                    .hidePalette().run(() -> {
                        AutoProfiles.manualSwitch(name);
                        Toasts.info("Profile: " + name, "Switched");
                    }));
        }
    }

    private static List<Command> deletableProfiles() {
        List<Command> out = new ArrayList<>();
        String active = AllerClient.config().activeProfile();
        for (String name : AllerClient.config().profileNames()) {
            if (name.equals(active) || name.equals(Config.DEFAULT_PROFILE)) continue;
            out.add(new Command(null, name, Group.RESULT).danger("delete the profile " + name).run(() -> {
                AllerClient.config().deleteProfile(name);
                Toasts.info("Profile deleted", name);
            }));
        }
        return out;
    }

    private static List<Command> servers() {
        List<Command> out = new ArrayList<>();
        for (Nav.Server server : Nav.servers()) {
            out.add(new Command(null, server.name(), Group.RESULT).detail(server.address()).after()
                    .run(parent -> Nav.join(server, parent)));
        }
        return out;
    }

    private static List<Command> worldOptions() {
        List<Command> out = new ArrayList<>();
        for (Nav.World w : worlds) {
            out.add(new Command(null, w.name(), Group.RESULT).detail(w.id().equals(w.name()) ? "" : w.id()).after()
                    .run(parent -> Nav.openWorld(w, parent)));
        }
        return out;
    }

    private static void custom(List<Command> out) {
        for (CustomActions.Custom custom : CustomActions.all()) {
            out.add(new Command(CustomActions.key(custom), custom.name, Group.CUSTOM).detail(custom.message).keywords("custom")
                    .when(Game::inWorld).after().run(() -> Game.send(custom.message))
                    .menu(() -> List.of(
                            new Command(null, "Rename", Group.RESULT).stay().step(new Step.Text("Rename", "Name", "", name -> {
                                CustomActions.rename(custom, name);
                                return null;
                            }).max(CustomActions.NAME_MAX).initial(() -> custom.name)),
                            new Command(null, "Change what it sends", Group.RESULT).detail(custom.message).stay()
                                    .step(new Step.Text("Message", "Message, or /command", "Sent once per run, exactly as if typed in chat.", text -> {
                                        custom.message = CustomActions.clean(text, CustomActions.MESSAGE_MAX);
                                        CustomActions.save();
                                        return null;
                                    }).max(CustomActions.MESSAGE_MAX).initial(() -> custom.message)),
                            new Command(null, "Delete", Group.RESULT).danger("delete " + custom.name).stay().run(() -> CustomActions.remove(custom)))));
        }
    }

    private static Step newCustom() {
        return new Step.Text("New action", "Name, for example Home", "What you will type in the launcher to find it.", name -> {
            String cleaned = CustomActions.clean(name, CustomActions.NAME_MAX);
            return new Step.Text(cleaned, "Message, or /command", "Sent once per run, exactly as if typed in chat.", text -> {
                CustomActions.Custom made = CustomActions.add(cleaned, text);
                Toasts.info("Custom action added", made.name + " sends " + made.message);
                return null;
            }).max(CustomActions.MESSAGE_MAX);
        }).max(CustomActions.NAME_MAX);
    }

    private static Command waypoint(Waypoint w) {
        boolean here = w.dimension.equals(Game.dimensionId());
        String where = Waypoints.coords(w) + "  ·  " + (here ? Waypoints.distanceText(Waypoints.distance(w)) + " away" : Game.pretty(w.dimension));
        return new Command("wp." + w.name.toLowerCase(), w.name, Group.WAYPOINT).detail(where).keywords("waypoint marker").color(w.color | 0xFF000000)
                .state(() -> w.visible).run(() -> {
                    w.visible = !w.visible;
                    Waypoints.save();
                })
                .menu(() -> List.of(
                        new Command(null, "Copy coordinates", Group.RESULT).detail(Waypoints.coords(w))
                                .run(() -> copy(Waypoints.coords(w).replace(",", ""), "Coordinates copied")),
                        new Command(null, "Remove", Group.RESULT).danger("remove " + w.name).stay().run(() -> Waypoints.remove(w))));
    }

    // ---- results worked out from what was typed --------------------------------------------------

    /** Things the query itself asks for: a sum, a count of items, a set of coordinates. */
    public static List<Command> results(String query) {
        List<Command> out = new ArrayList<>();
        String q = query.trim();
        Matcher items = ITEMS.matcher(q);
        if (items.matches()) {
            long n = Long.parseLong(items.group(1));
            boolean stacks = items.group(2).toLowerCase().startsWith("stack");
            String answer = stacks ? n * 64 + " items" : Calc.stacks(n).isEmpty() ? "Less than a stack" : Calc.stacks(n);
            out.add(new Command(null, q + " = " + answer, Group.RESULT).detail("Enter copies the answer")
                    .run(() -> copy(stacks ? Long.toString(n * 64) : answer, "Copied")));
        }
        Double value = Calc.eval(q);
        if (value != null) {
            String text = Calc.format(value), stacks = Calc.stacks(value);
            out.add(new Command(null, "= " + text, Group.RESULT).detail(stacks.isEmpty() ? "Enter copies the answer" : stacks)
                    .run(() -> copy(text, "Copied " + text)));
        }
        Matcher m = COORDS.matcher(q);
        if (m.matches() && Game.inWorld()) {
            boolean three = m.group(3) != null;
            int x = Integer.parseInt(m.group(1)), z = Integer.parseInt(m.group(three ? 3 : 2));
            int y = three ? Integer.parseInt(m.group(2)) : (int) Math.floor(Game.player().getY());
            String at = x + ", " + y + ", " + z;
            out.add(new Command(null, "Add waypoint at " + at, Group.RESULT).detail(three ? "In this dimension" : "At your height, in this dimension")
                    .run(() -> {
                        int n = Waypoints.all().size();
                        Waypoint w = Waypoints.addAt("Waypoint " + (n + 1), x, y, z, Waypoints.PALETTE[n % Waypoints.PALETTE.length]);
                        Toasts.info("Waypoint added", w.name + " at " + Waypoints.coords(w));
                    }));
            String dim = Game.dimensionId();
            if (dim.equals("overworld")) {
                String nether = Math.floorDiv(x, 8) + " " + Math.floorDiv(z, 8);
                out.add(new Command(null, "In the Nether: " + nether.replace(" ", ", "), Group.RESULT).detail("Enter copies them")
                        .run(() -> copy(nether, "Nether coordinates copied")));
            } else if (dim.equals("the_nether")) {
                String over = x * 8L + " " + z * 8L;
                out.add(new Command(null, "In the Overworld: " + over.replace(" ", ", "), Group.RESULT).detail("Enter copies them")
                        .run(() -> copy(over, "Overworld coordinates copied")));
            }
        }
        return out;
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static String here() {
        var p = Game.player();
        return (int) Math.floor(p.getX()) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ());
    }

    /** The player's horizontal position in the other dimension's scale. */
    private static String scaled(double factor) {
        var p = Game.player();
        return (long) Math.floor(p.getX() * factor) + " " + (long) Math.floor(p.getZ() * factor);
    }

    /** "100 64 -200 in the Nether". */
    private static String place() {
        String dim = Game.dimensionId();
        return here() + " in " + (dim.equals("overworld") ? "the Overworld" : dim.equals("the_nether") ? "the Nether"
                : dim.equals("the_end") ? "the End" : Game.pretty(dim));
    }

    private static String facing() {
        var p = Game.player();
        float yaw = net.minecraft.util.Mth.wrapDegrees(p.getYRot());
        return Settings.Choice.label(p.getDirection()) + ", yaw " + String.format(java.util.Locale.ROOT, "%.1f", yaw)
                + ", pitch " + String.format(java.util.Locale.ROOT, "%.1f", p.getXRot());
    }

    private static void copy(String text, String title) {
        Mc.setClipboard(text);
        Toasts.info(title, text);
    }

    private static String quote(String text) {
        return "“" + text + "”";
    }

    private static void waypointHere(String name) {
        int n = Waypoints.all().size();
        Waypoint w = Waypoints.addHere(name.isBlank() ? "Waypoint " + (n + 1) : name, Waypoints.PALETTE[n % Waypoints.PALETTE.length]);
        Toasts.info("Waypoint added", w.name + " at " + Waypoints.coords(w));
    }

    private static void showWaypoints(boolean visible) {
        for (Waypoint w : Waypoints.all()) w.visible = visible;
        Waypoints.save();
        Toasts.info(visible ? "Waypoints shown" : "Waypoints hidden", Waypoints.all().size() + " in this world");
    }

    private static void setAll(java.util.function.Predicate<Module> which, boolean on, String title) {
        int changed = 0;
        for (Module m : AllerClient.modules().all()) {
            if (which.test(m) && m.enabled() != on) {
                m.setEnabled(on);
                changed++;
            }
        }
        AllerClient.config().markDirty();
        Toasts.info(title, changed + (changed == 1 ? " mod" : " mods") + " changed");
    }
}
