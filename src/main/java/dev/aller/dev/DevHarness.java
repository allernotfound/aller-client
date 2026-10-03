package dev.aller.dev;

import dev.aller.AllerClient;
import dev.aller.module.Module;
import dev.aller.module.Modules;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import dev.aller.screen.HudEditorScreen;
import dev.aller.screen.LauncherScreen;
import dev.aller.screen.MainMenuScreen;
import dev.aller.screen.PaletteScreen;
import dev.aller.ui.AllerScreen;
import dev.aller.ui.anim.Motion;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Development aid, inactive unless launched with {@code -Daller.dev.shots=<dir>}. It walks through
 * the main screens and a creative test world (skip the world with {@code -Daller.dev.noWorld=true}),
 * saves a framebuffer capture of each and exits, so the UI can be checked without anyone clicking
 * through it.
 */
public final class DevHarness {
    /** Waits for {@code ready}, then {@code delay} seconds, then captures and/or runs the action. */
    private record Step(BooleanSupplier ready, float delay, String shot, Runnable action, float timeout) {}

    private static Path dir;
    private static final List<Step> steps = new ArrayList<>();
    private static final Map<Module, Boolean> savedState = new HashMap<>();
    private static final List<dev.aller.feature.Waypoints.Waypoint> harnessWaypoints = new ArrayList<>();
    private static float mark = -1;
    private static float waitingSince = -1;
    private static int next;
    private static int pending;

    private DevHarness() {}

    public static void init() {
        String out = System.getProperty("aller.dev.shots");
        if (out == null) return;
        dir = Path.of(out);
        if (Boolean.getBoolean("aller.dev.bench")) {
            bench();
            return;
        }
        if (System.getProperty("aller.dev.pocket") != null) {
            pocket();
            return;
        }
        if (Boolean.getBoolean("aller.dev.skin")) {
            skin();
            return;
        }
        if (Boolean.getBoolean("aller.dev.store")) {
            store();
            return;
        }
        if (Boolean.getBoolean("aller.dev.gallery")) {
            gallery();
            return;
        }
        if (Boolean.getBoolean("aller.dev.mods")) {
            mods();
            return;
        }
        if (Boolean.getBoolean("aller.dev.onboarding")) {
            onboarding();
            return;
        }

        until(Mc::loadingOverlay);
        shot(0.7f, "splash");
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        shot(1.0f, "menu-intro");
        shot(3.5f, "menu");
        run(0.1f, () -> Mc.setScreen(new ScreenHost(new PaletteScreen(Mc.screen()))));
        shot(1.2f, "palette");
        run(0.1f, () -> type("fps"));
        shot(0.8f, "palette-search");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            type("crosshair");
            key(GLFW.GLFW_KEY_RIGHT);
        });
        shot(0.9f, "palette-module");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            key(GLFW.GLFW_KEY_ESCAPE);
            type("client");
            key(GLFW.GLFW_KEY_ENTER);
        });
        shot(0.9f, "palette-client");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            key(GLFW.GLFW_KEY_ESCAPE);
            type("profiles");
            key(GLFW.GLFW_KEY_ENTER);
        });
        shot(0.9f, "palette-profiles");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            key(GLFW.GLFW_KEY_ESCAPE);
            AllerClient.options().gridView.set(true);
            key(GLFW.GLFW_KEY_TAB);
        });
        shot(0.9f, "palette-grid");
        run(0.1f, () -> {
            AllerClient.options().gridView.set(false);
            // Tab back round to "All" so later searches are not filtered.
            for (int i = 0; i < dev.aller.module.Category.values().length; i++) {
                if (AllerClient.modules().in(dev.aller.module.Category.values()[i]).isEmpty()) continue;
                key(GLFW.GLFW_KEY_TAB);
            }
        });

        // The launcher over the main menu: suggestions, a value on one line, a value step, the action list.
        run(0.1f, () -> key(GLFW.GLFW_KEY_ESCAPE));
        until(() -> Mc.current() instanceof MainMenuScreen, 5);
        run(0.3f, () -> {
            home = Mc.screen();
            Mc.setScreen(new ScreenHost(new LauncherScreen(home)));
        });
        shot(0.8f, "launcher-menu");
        run(0.1f, () -> type("fov 90"));
        shot(0.6f, "launcher-inline");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            type("brightness");
            key(GLFW.GLFW_KEY_ENTER);
        });
        shot(0.6f, "launcher-value");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            key(GLFW.GLFW_KEY_ESCAPE);
            type(">");
        });
        shot(0.6f, "launcher-actions");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            type("64*27+5");
        });
        shot(0.6f, "launcher-calc");
        run(0.1f, () -> Mc.setScreen(home));

        run(0.1f, () -> Mc.setScreen(new ScreenHost(new dev.aller.screen.WardrobeScreen(home))));
        shot(4.0f, "wardrobe");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_DOWN);
            key(GLFW.GLFW_KEY_TAB);
        });
        shot(1.5f, "wardrobe-picked");
        run(0.1f, () -> {
            if (Mc.current() instanceof dev.aller.screen.WardrobeScreen w) w.dev("ask");
        });
        shot(0.8f, "wardrobe-rejoin");
        run(0.1f, () -> Mc.setScreen(home));

        // The browser. A live page is a native window the capture cannot see, so the page is
        // checked through the still drawn in its place while the address bar's suggestions are open.
        if (dev.aller.feature.Browser.usable()) {
            run(0.1f, () -> dev.aller.feature.Browser.open(home, null));
            shot(1.2f, "browser-start");
            run(0.1f, () -> dev.aller.feature.Browser.open(home, "https://example.com"));
            shot(5.0f, "browser-live");
            run(0.1f, () -> {
                var tab = dev.aller.feature.Browser.active();
                AllerClient.LOG.info("BROWSER view={} loading={} title='{}' url={}", tab.view, tab.loading, tab.title, tab.url);
                key(GLFW.GLFW_KEY_F6);
            });
            run(0.6f, () -> type("exa"));
            shot(0.8f, "browser-still");
            run(0.1f, () -> {
                key(GLFW.GLFW_KEY_ESCAPE);
                AllerScreen s = Mc.current();
                if (s != null) s.keyDown(GLFW.GLFW_KEY_N, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
            });
            shot(1.0f, "browser-private");
            run(0.1f, () -> {
                AllerScreen s = Mc.current();
                if (s != null) s.keyDown(GLFW.GLFW_KEY_H, GLFW.GLFW_MOD_CONTROL);
            });
            shot(1.0f, "browser-history");
            run(0.1f, () -> {
                dev.aller.feature.Browser.setIncognito(true);
                dev.aller.feature.Browser.close(dev.aller.feature.Browser.active());
                Mc.setScreen(home);
            });
        }

        // Vanilla and other mods' menus, restyled in place.
        menuShot("vanilla-options", dev.aller.platform.Nav::options);
        run(0.1f, () -> {
            Mc.setScreen(new ScreenHost(new LauncherScreen(Mc.screen())));
            type("volume");
        });
        shot(0.8f, "launcher-over-options");
        menuShot("vanilla-video", home -> Mc.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(home, Mc.mc(), Mc.mc().options)));
        menuShot("vanilla-language", home -> Mc.setScreen(new net.minecraft.client.gui.screens.options.LanguageSelectScreen(home, Mc.mc().options, Mc.mc().getLanguageManager())));
        menuShot("vanilla-worlds", dev.aller.platform.Nav::singleplayer);
        menuShot("vanilla-multiplayer", dev.aller.platform.Nav::multiplayer);
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isModLoaded("sodium")) {
            menuShot("sodium-video", home -> openStatic(home, "createScreen",
                    "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"));
        }
        if (loader.isModLoaded("iris")) {
            menuShot("iris-shaders", home -> openStatic(home, null, "net.irisshaders.iris.gui.screen.ShaderPackScreen"));
        }
        if (dev.aller.platform.Nav.hasModMenu()) menuShot("modmenu", dev.aller.platform.Nav::mods);
        run(0.1f, () -> {
            if (home != null) Mc.setScreen(home);
        });

        if (canEnterWorld()) {
            run(0.1f, DevHarness::enterWorld);
            until(() -> dev.aller.platform.LoadingInfo.of(Mc.screen()) != null, 10);
            shot(0.5f, "loading");
            // Gives up (and just exits) if the world cannot be opened, e.g. another instance has it locked.
            until(() -> Mc.mc().player != null && Mc.mc().level != null && !Mc.loadingOverlay() && Mc.screen() == null, 90);
            shot(6.0f, "hud");
            // The effect mods on their own, each visible in one picture, then off again.
            Module[] effects = {Modules.DEPTH_OF_FIELD, Modules.COLOUR_GRADING, Modules.ATMOSPHERE, Modules.MOTION_BLUR};
            // In daylight, and looking up a little so there is sky and distance to work on.
            run(0.1f, () -> {
                Modules.TIME_CHANGER.setEnabled(true);
                Mc.mc().player.setXRot(-8f);
            });
            shot(1.5f, "effects-off");
            run(0.1f, () -> {
                Modules.ATMOSPHERE.skyTinted.set(true);
                for (Module m : effects) m.setEnabled(true);
            });
            shot(2.0f, "effects");
            for (Module each : effects) {
                run(0.1f, () -> {
                    for (Module m : effects) m.setEnabled(m == each);
                    // Motion blur shows only while the camera moves: turn steadily for its picture.
                    spin = each == Modules.MOTION_BLUR;
                });
                shot(1.0f, "effect-" + each.id);
            }
            run(0.1f, () -> {
                spin = false;
                Modules.ATMOSPHERE.skyTinted.set(false);
                for (Module m : effects) m.setEnabled(false);
                Modules.TIME_CHANGER.setEnabled(false);
            });
            run(0.1f, () -> {
                for (Module m : AllerClient.modules().all()) {
                    savedState.put(m, m.enabled());
                    m.setEnabled(true);
                }
            });
            shot(2.5f, "hud-all");
            run(0.1f, () -> Mc.mc().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
            shot(1.0f, "third-person");
            run(0.1f, () -> Mc.mc().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            run(0.1f, () -> {
                // One waypoint ahead, one behind to the right and one to the left, to check the edge pointers.
                var p = Mc.mc().player;
                double x = p.getX(), y = p.getY(), z = p.getZ();
                String[] shapes = {"STAR", "TRIANGLE", "DIAMOND"};
                double[][] at = {{0, 2, 14}, {-16, 0, -12}, {22, 3, 2}};
                for (int i = 0; i < 3; i++) {
                    var w = dev.aller.feature.Waypoints.addHere("Harness " + (i + 1), dev.aller.feature.Waypoints.PALETTE[i + 1]);
                    w.x = x + at[i][0];
                    w.y = y + at[i][1];
                    w.z = z + at[i][2];
                    w.icon = shapes[i];
                    harnessWaypoints.add(w);
                }
            });
            shot(1.0f, "waypoints-hud");
            run(0.1f, () -> Mc.open(new PaletteScreen()));
            run(0.3f, () -> {
                type("marker location");
                key(GLFW.GLFW_KEY_ENTER);
            });
            run(0.6f, () -> {
                // Click the first waypoint row to open its editor.
                AllerScreen s = Mc.current();
                if (s == null) return;
                float scale = s.scale();
                float w = Mc.mc().getWindow().getGuiScaledWidth() / scale, h = Mc.mc().getWindow().getGuiScaledHeight() / scale;
                float py = (h - Math.min(330, h - 24)) / 2;
                s.mouseDown(w / 2 - 60, py + 36 + 8 + 26 + 14, 0);
            });
            shot(1.0f, "palette-waypoints");
            run(0.1f, () -> {
                harnessWaypoints.forEach(dev.aller.feature.Waypoints::remove);
                Mc.setScreen(null);
            });
            run(0.3f, () -> Mc.open(new HudEditorScreen(null)));
            shot(1.0f, "hud-editor");
            run(0.1f, () -> key(GLFW.GLFW_KEY_E));
            shot(0.8f, "hud-editor-drawer");
            run(0.1f, () -> Mc.setScreen(new ScreenHost(new LauncherScreen(null))));
            shot(0.8f, "launcher-world");
            run(0.1f, () -> {
                type("render");
                key(GLFW.GLFW_KEY_ENTER);
            });
            shot(0.6f, "launcher-world-value");
            run(0.1f, () -> {
                key(GLFW.GLFW_KEY_ESCAPE);
                key(GLFW.GLFW_KEY_ESCAPE);
                type("new custom");
                key(GLFW.GLFW_KEY_ENTER);
            });
            shot(0.6f, "launcher-text");
            run(0.1f, () -> {
                key(GLFW.GLFW_KEY_ESCAPE);
                key(GLFW.GLFW_KEY_ESCAPE);
                type("quit");
                key(GLFW.GLFW_KEY_ENTER);
            });
            shot(0.6f, "launcher-confirm");
            run(0.1f, () -> Mc.setScreen(null));
            // The pixel look: Minecraft's font and stepped corners, then back to how it was.
            var look = dev.aller.AllerClient.options();
            var face = look.typeface.get();
            var corners = look.pixelate.get();
            run(0.3f, () -> {
                look.typeface.set(dev.aller.ClientOptions.Typeface.MINECRAFT);
                look.pixelate.set(dev.aller.ClientOptions.Pixelate.EVERYTHING);
                Mc.setScreen(new ScreenHost(new PaletteScreen(null, dev.aller.screen.palette.SettingsPage.ui())));
            });
            shot(1.0f, "pixel-ui-settings");
            run(0.1f, () -> Mc.setScreen(new ScreenHost(new dev.aller.screen.PauseMenuScreen())));
            shot(1.0f, "pixel-pause");
            run(0.1f, () -> {
                look.typeface.set(face);
                look.pixelate.set(corners);
                Mc.setScreen(null);
            });
            run(0.3f, () -> Mc.open(new PaletteScreen()));
            shot(1.2f, "palette-world");
            run(0.1f, () -> {
                type("coordinates");
                key(GLFW.GLFW_KEY_RIGHT);
            });
            shot(0.9f, "palette-hud-module");
            run(0.1f, () -> {
                key(GLFW.GLFW_KEY_ESCAPE);
                key(GLFW.GLFW_KEY_ESCAPE);
            });
            run(0.1f, () -> {
                type("session stats");
                key(GLFW.GLFW_KEY_ENTER);
            });
            shot(0.9f, "palette-stats");
            run(0.1f, () -> {
                Mc.setScreen(null);
                // Chat mods: a plain line, a mention, a repeat, a private message and the player's own line.
                String me = dev.aller.platform.Nav.playerName();
                String[] lines = {"<Sam> anyone seen the new spawn?", "[VIP] Alex » hey " + me + " are you coming", "<Sam> gg", "<Sam> gg", "<Sam> gg",
                        "Robin whispers to you: meet at the portal", "<" + me + "> on my way, " + me + " out", me + " joined the game"};
                for (String line : lines) dev.aller.platform.ChatView.add(net.minecraft.network.chat.Component.literal(line));
            });
            shot(1.2f, "chat");
            run(0.1f, () -> Mc.setScreen(new ScreenHost(new dev.aller.screen.ChatHistoryScreen(null))));
            shot(1.5f, "chat-history");
            run(0.1f, () -> type("gg"));
            shot(1.2f, "chat-history-search");
            run(0.1f, () -> Mc.setScreen(new ScreenHost(new PaletteScreen(null, new dev.aller.screen.palette.ChatFormatsPage()))));
            run(0.3f, () -> {
                // Type into the "Try a line" box at the bottom of the page.
                AllerScreen s = Mc.current();
                if (s == null) return;
                float scale = s.scale(), zoom = AllerClient.options().paletteZoom.get();
                float w = Mc.mc().getWindow().getGuiScaledWidth() / scale, h = Mc.mc().getWindow().getGuiScaledHeight() / scale;
                float ph = Math.min(330 / zoom, h - 24), py = (h - ph) / 2;
                s.mouseScroll(w / 2, h / 2, -20);
                harnessClickY = py + ph - 22 - 8 - 26 - 5 - 10;
            });
            run(0.6f, () -> {
                AllerScreen s = Mc.current();
                if (s == null) return;
                s.mouseDown(Mc.mc().getWindow().getGuiScaledWidth() / s.scale() / 2, harnessClickY, 0);
                type("[Mod] Jamie -> me: hello there");
            });
            shot(0.9f, "palette-chat-formats");
            run(0.1f, () -> {
                Mc.setScreen(null);
                Mc.mc().pauseGame(false);
            });
            shot(1.2f, "pause");
            run(0.1f, () -> Mc.setScreen(new ScreenHost(new dev.aller.screen.WardrobeScreen(Mc.screen()))));
            shot(2.0f, "wardrobe-world");
            run(0.1f, () -> {
                AllerScreen s = Mc.current();
                if (s != null) s.close();
            });
            until(() -> Mc.current() instanceof dev.aller.screen.PauseMenuScreen, 5);
            run(0.1f, () -> dev.aller.platform.Nav.options(Mc.screen()));
            shot(1.0f, "vanilla-options-world");
            run(0.1f, () -> {
                savedState.forEach(Module::setEnabled);
                AllerClient.config().save();
            });
        }
        run(0.5f, () -> Mc.mc().stop());
        AllerClient.LOG.info("Dev harness active, writing captures to {}", dir);
    }

    /**
     * Frame-rate comparison ({@code -Paller.bench}): the same view of the test world with Aller idle,
     * with its defaults, and with heavier sets of modules, uncapped. Results go to the log.
     */
    private static void bench() {
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        run(1f, DevHarness::enterWorld);
        until(() -> Mc.mc().player != null && Mc.mc().level != null && !Mc.loadingOverlay() && Mc.screen() == null, 90);
        run(10f, () -> {
            var o = Mc.mc().options;
            o.enableVsync().set(false);
            o.framerateLimit().set(260);
            // Vanilla throttles to 10 fps when minimised or when no input has arrived for a while.
            o.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            Mc.mc().getFramerateLimitTracker().setFramerateLimit(260);
            // A fullscreen window is minimised as soon as it loses focus, so measure windowed.
            if (Mc.mc().getWindow().isFullscreen()) {
                benchWasFullscreen = true;
                Mc.mc().getWindow().toggleFullScreen();
            }
            GLFW.glfwRestoreWindow(Mc.window());
            for (Module m : AllerClient.modules().all()) savedState.put(m, m.enabled());
        });
        measure("idle (every module off)", m -> false);
        measure("defaults", m -> m.enabledByDefault);
        measure("all HUD elements", m -> m instanceof dev.aller.hud.HudModule);
        measure("everything except replay", m -> !m.id.equals("replay"));
        measure("replay only", m -> m.id.equals("replay"));
        measure("effects (focus, grading, motion blur)", m -> m.getClass().getEnclosingClass() == dev.aller.module.mods.EffectMods.class);
        measure("idle again", m -> false);
        if (Boolean.getBoolean("aller.dev.benchEach")) {
            for (Module each : AllerClient.modules().all()) {
                if (!(each instanceof dev.aller.hud.HudModule)) measure("only " + each.id, m -> m == each);
            }
            measure("idle last", m -> false);
        }
        run(0.2f, () -> {
            savedState.forEach(Module::setEnabled);
            AllerClient.config().save();
            if (benchWasFullscreen) Mc.mc().getWindow().toggleFullScreen();
        });
        run(0.5f, () -> Mc.mc().stop());
    }

    /**
     * The pocket dimension ({@code -Paller.pocket}): from the test world, step in, send chat and a
     * pocket command, leave through the door, come back to check the build was kept, leave by key.
     * The test world stands in for a server, so two integrated servers run side by side.
     */
    private static void pocket() {
        var mod = dev.aller.module.Modules.POCKET;
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        run(1f, DevHarness::enterWorld);
        until(() -> Mc.mc().player != null && Mc.mc().level != null && !Mc.loadingOverlay() && Mc.screen() == null, 90);
        run(4f, () -> {
            savedState.put(mod, mod.enabled());
            mod.setEnabled(true);
            pocketLog("before");
            dev.aller.feature.Pocket.toggle();
        });
        shot(0.5f, "pocket-sealing");
        until(() -> dev.aller.feature.Pocket.state().equals("INSIDE"), 60);
        shot(1.0f, "pocket-inside");
        run(0.1f, () -> {
            pocketLog("inside");
            dev.aller.platform.Game.send("hello from the pocket");
            dev.aller.platform.Game.send(mod.prefix.get() + "setblock 16 64 12 minecraft:chest");
            dev.aller.platform.Game.send(mod.prefix.get() + "summon minecraft:pig 13 64 12");
        });
        shot(2.0f, "pocket-built");
        run(0.1f, () -> dev.aller.platform.Game.send(mod.prefix.get() + "tp @s 16 64 31.5 0 0"));
        shot(1.0f, "pocket-door");
        run(0.1f, () -> dev.aller.platform.Game.send(mod.prefix.get() + "tp @s 16 64 33.2 0 0"));
        until(() -> !dev.aller.feature.Pocket.active(), 30);
        shot(1.0f, "pocket-back");
        run(0.1f, () -> pocketLog("back"));
        until(dev.aller.platform.PocketServer::idle, 60);
        run(0.5f, dev.aller.feature.Pocket::toggle);
        until(() -> dev.aller.feature.Pocket.state().equals("INSIDE"), 60);
        shot(1.5f, "pocket-again");
        run(0.1f, () -> {
            pocketLog("again");
            dev.aller.feature.Pocket.toggle();
        });
        until(() -> !dev.aller.feature.Pocket.active(), 30);
        shot(1.0f, "pocket-out");
        run(0.1f, () -> {
            pocketLog("out");
            savedState.forEach(Module::setEnabled);
            AllerClient.config().save();
        });
        run(1.5f, () -> Mc.mc().stop());
    }

    /**
     * The restyled menus ({@code -Paller.skin}): switches the restyling on for the run, leaves the main
     * menu and the pause menu for the options with captures part way through the hand-over, then
     * walks the menus that are skinned.
     */
    private static void skin() {
        var o = AllerClient.options();
        var mc = Mc.mc();
        boolean[] was = new boolean[1];
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        run(3.5f, () -> {
            was[0] = o.restyleMenus.get();
            o.restyleMenus.set(true);
            home = Mc.screen();
            if (Mc.current() instanceof MainMenuScreen menu) menu.go(() -> dev.aller.platform.Nav.options(Mc.screen()));
        });
        shot(0.1f, "skin-handover-1");
        shot(0.1f, "skin-handover-2");
        shot(0.12f, "skin-handover-3");
        shot(1.0f, "skin-options");
        // Into a sub-screen from a focused button and back, as a click does.
        run(0.1f, () -> {
            var options = Mc.screen();
            for (var child : options.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage().getString().contains("Music")) options.setFocused(w);
            }
            Mc.setScreen(new net.minecraft.client.gui.screens.options.SoundOptionsScreen(options, mc.options));
        });
        shot(0.12f, "skin-sub-entering");
        shot(1.0f, "skin-sound");
        run(0.1f, () -> Mc.screen().onClose());
        shot(1.0f, "skin-options-back");
        skinShot("skin-video", parent -> Mc.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(parent, mc, mc.options)));
        skinShot("skin-controls", dev.aller.platform.Nav::controls);
        skinShot("skin-keys", dev.aller.platform.Nav::keyBinds);
        skinShot("skin-language", dev.aller.platform.Nav::language);
        skinShot("skin-chat", dev.aller.platform.Nav::chatSettings);
        skinShot("skin-accessibility", dev.aller.platform.Nav::accessibility);
        skinShot("skin-skin", dev.aller.platform.Nav::skin);
        skinShot("skin-packs", dev.aller.platform.Nav::resourcePacks);
        skinShot("skin-worlds", dev.aller.platform.Nav::singleplayer);
        skinShot("skin-multiplayer", dev.aller.platform.Nav::multiplayer);
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isModLoaded("sodium")) {
            skinShot("skin-sodium", parent -> openStatic(parent, "createScreen",
                    "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"));
        }
        if (loader.isModLoaded("iris")) skinShot("skin-iris", parent -> openStatic(parent, null, "net.irisshaders.iris.gui.screen.ShaderPackScreen"));
        if (dev.aller.platform.Nav.hasModMenu()) skinShot("skin-modmenu", dev.aller.platform.Nav::mods);
        run(0.1f, () -> Mc.setScreen(home));
        shot(1.2f, "skin-menu");
        // Essential's destinations, each pressed from the main menu.
        // The smooth look (Inter, round corners), whatever the player has chosen: menu, options, sliders, Sodium.
        var face = o.typeface.get();
        var corners = o.pixelate.get();
        run(0.1f, () -> {
            o.typeface.set(dev.aller.ClientOptions.Typeface.values()[0]);
            o.pixelate.set(dev.aller.ClientOptions.Pixelate.OFF);
        });
        shot(0.8f, "smooth-menu");
        skinShot("smooth-options", dev.aller.platform.Nav::options);
        skinShot("smooth-sound", dev.aller.platform.Nav::sound);
        skinShot("smooth-video", parent -> Mc.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(parent, mc, mc.options)));
        if (loader.isModLoaded("sodium")) {
            skinShot("smooth-sodium", parent -> openStatic(parent, "createScreen",
                    "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"));
        }
        run(0.1f, () -> Mc.setScreen(new ScreenHost(new PaletteScreen(home))));
        shot(1.0f, "smooth-palette");
        run(0.1f, () -> {
            o.typeface.set(face);
            o.pixelate.set(corners);
            Mc.setScreen(home);
        });
        List<dev.aller.ui.widget.IconButton> essential = new ArrayList<>();
        run(0.1f, () -> essential.addAll(dev.aller.compat.EssentialCompat.buttons(true, Runnable::run)));
        if (dev.aller.compat.EssentialCompat.present()) {
            for (int i = 0; i < 6; i++) {
                int index = i;
                run(0.1f, () -> {
                    if (index >= essential.size()) return;
                    AllerClient.LOG.info("ESSENTIAL pressing '{}'", essential.get(index).label);
                    essential.get(index).press();
                });
                shot(1.5f, "essential-" + i);
                run(0.1f, () -> {
                    if (index >= essential.size()) return;
                    AllerClient.LOG.info("ESSENTIAL '{}' gave {}", essential.get(index).label, Mc.screen() == null ? null : Mc.screen().getClass().getName());
                    Mc.setScreen(home);
                });
            }
        }

        if (canEnterWorld()) {
            run(0.1f, DevHarness::enterWorld);
            until(() -> mc.player != null && mc.level != null && !Mc.loadingOverlay() && Mc.screen() == null, 90);
            run(4f, () -> mc.pauseGame(false));
            shot(1.2f, "skin-pause");
            run(0.1f, () -> {
                home = Mc.screen();
                if (Mc.current() instanceof dev.aller.screen.PauseMenuScreen menu) menu.go(() -> dev.aller.platform.Nav.options(Mc.screen()));
            });
            shot(0.1f, "skin-world-handover-1");
            shot(0.1f, "skin-world-handover-2");
            shot(0.12f, "skin-world-handover-3");
            shot(1.0f, "skin-world-options");
            skinShot("skin-world-sound", dev.aller.platform.Nav::sound);
            if (loader.isModLoaded("sodium")) {
                skinShot("skin-world-sodium", parent -> openStatic(parent, "createScreen",
                        "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI"));
            }
            run(0.1f, () -> Mc.setScreen(home));
            shot(1.0f, "skin-pause-back");
        }
        run(0.2f, () -> {
            o.restyleMenus.set(was[0]);
            AllerClient.config().save();
        });
        run(0.5f, () -> mc.stop());
    }

    /**
     * Onboarding ({@code -Paller.onboarding}): stills from through the intro, then every step, with
     * the colour wave caught part way for each look. What it changes (accent, font, corners) is put back.
     */
    private static void onboarding() {
        var o = AllerClient.options();
        Object[] was = new Object[3];
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        run(1.0f, () -> {
            was[0] = o.accent.get();
            was[1] = o.typeface.get();
            was[2] = o.pixelate.get();
            home = Mc.screen();
            introFrames = benchFrames;
            introStart = System.nanoTime();
            Mc.setScreen(new ScreenHost(new dev.aller.screen.OnboardingScreen(home)));
        });
        // The intro once at speed, for the log and the frame rate, then stills of its moments.
        shot(2.0f, "onboarding-intro-live-2");
        shot(4.0f, "onboarding-intro-live-6");
        shot(5.0f, "onboarding-intro-live-11");
        shot(5.0f, "onboarding-intro-live-16");
        shot(4.0f, "onboarding-intro-live-gate");
        shot(4.0f, "onboarding-intro-live-held");
        run(0.05f, () -> AllerClient.LOG.info("ONBOARDING intro ran at {} fps", Math.round((benchFrames - introFrames) / ((System.nanoTime() - introStart) / 1e9))));
        String[] moments = {"1.8", "2.8", "3.4", "5.5", "8.0", "8.7", "10.5", "12.8", "13.6", "14.5", "17.5", "gate", "19.6", "20.2", "20.33", "20.6", "22.5", "24.5", "26.3"};
        for (String at : moments) {
            run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("intro:" + at));
            shot(0.1f, "onboarding-intro-" + at.replace('.', '_'));
        }
        run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("hello"));
        shot(2.6f, "onboarding-hello");
        run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("accent"));
        shot(1.4f, "onboarding-accent");
        run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("accent:pick"));
        shot(0.6f, "onboarding-accent-picked");
        run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("theme"));
        shot(1.4f, "onboarding-theme");
        run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("pixel"));
        shot(0.3f, "onboarding-wave-1");
        shot(0.25f, "onboarding-wave-2");
        shot(0.3f, "onboarding-wave-3");
        shot(1.6f, "onboarding-theme-pixel");
        run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("smooth"));
        shot(0.8f, "onboarding-theme-smooth");
        for (String step : new String[] {"palette", "launcher", "hud", "mods", "fair"}) {
            run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev(step));
            shot(1.6f, "onboarding-" + step);
            if (step.equals("palette")) {
                run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("pass"));
                shot(0.5f, "onboarding-palette-passed");
            }
        }
        run(0.05f, () -> dev.aller.screen.OnboardingScreen.dev("done"));
        shot(0.7f, "onboarding-done-burst");
        shot(1.6f, "onboarding-done");
        run(0.2f, () -> {
            o.accent.set((Integer) was[0]);
            o.typeface.set((dev.aller.ClientOptions.Typeface) was[1]);
            o.pixelate.set((dev.aller.ClientOptions.Pixelate) was[2]);
            AllerClient.config().save();
        });
        run(0.5f, () -> Mc.mc().stop());
    }

    /**
     * The pack store ({@code -Paller.store}): the button on the pack list, then a search, a project's
     * page with its gallery and versions, a download, the installed list, and the pack list again
     * with the download pinned and marked. It needs the network; what it downloads is deleted again.
     */
    private static void store() {
        var o = AllerClient.options();
        boolean[] was = new boolean[1];
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        run(3.5f, () -> {
            home = Mc.screen();
            dev.aller.platform.Nav.resourcePacks(home);
        });
        shot(1.2f, "store-button");
        run(0.1f, () -> dev.aller.screen.store.StoreScreen.open(Mc.screen(), dev.aller.feature.store.Kind.RESOURCE_PACKS));
        shot(6.0f, "store-browse");
        run(0.1f, () -> store("rows"));
        shot(1.5f, "store-rows");
        run(0.1f, () -> {
            store("grid");
            type("fresh animations");
        });
        shot(5.0f, "store-search");
        run(0.1f, () -> {
            key(GLFW.GLFW_KEY_ESCAPE);
            store("open");
        });
        shot(7.0f, "store-page");
        run(0.1f, () -> {
            AllerScreen s = Mc.current();
            if (s != null) s.mouseScroll(200, 200, -9);
        });
        shot(2.5f, "store-page-scrolled");
        run(0.1f, () -> store("gallery"));
        shot(3.5f, "store-gallery");
        run(0.1f, () -> store("picture"));
        shot(5.0f, "store-picture");
        run(0.1f, () -> store("versions"));
        shot(2.0f, "store-versions");
        run(0.1f, () -> store("download"));
        shot(0.4f, "store-downloading");
        until(() -> !dev.aller.feature.store.Store.busy(), 60);
        shot(1.0f, "store-downloaded");
        run(0.1f, () -> store("installed"));
        shot(4.0f, "store-installed");
        run(0.1f, () -> key(GLFW.GLFW_KEY_ESCAPE));
        shot(1.5f, "store-pinned");
        // Take the download back, from the same list: a store opened from another forgets what was new.
        run(0.1f, () -> dev.aller.screen.store.StoreScreen.open(Mc.screen(), dev.aller.feature.store.Kind.RESOURCE_PACKS));
        run(1.0f, () -> store("tidy"));
        // The same list restyled, with the button on it.
        run(0.1f, () -> {
            was[0] = o.restyleMenus.get();
            o.restyleMenus.set(true);
            Mc.setScreen(home);
            dev.aller.platform.Nav.resourcePacks(home);
        });
        shot(1.5f, "store-button-restyled");
        // Shader packs, from Iris's list.
        if (dev.aller.platform.Nav.hasShaders()) {
            run(1.0f, () -> {
                Mc.setScreen(home);
                dev.aller.platform.Nav.shaderPacks(home);
            });
            shot(1.5f, "store-shaders-button");
            run(0.1f, () -> {
                var kind = dev.aller.screen.store.PackListExtras.kind(Mc.screen());
                AllerClient.LOG.info("STORE list {} is {}", Mc.screen() == null ? null : Mc.screen().getClass().getName(), kind);
                if (kind != null) dev.aller.screen.store.StoreScreen.open(Mc.screen(), kind);
            });
            shot(6.0f, "store-shaders-browse");
            run(0.1f, () -> store("open"));
            shot(7.0f, "store-shaders-page");
            run(0.1f, () -> store("download"));
            until(() -> !dev.aller.feature.store.Store.busy(), 120);
            run(0.5f, () -> key(GLFW.GLFW_KEY_ESCAPE));
            run(0.3f, () -> key(GLFW.GLFW_KEY_ESCAPE));
            shot(2.0f, "store-shaders-listed");
            run(0.1f, () -> {
                var kind = dev.aller.screen.store.PackListExtras.kind(Mc.screen());
                if (kind != null) dev.aller.screen.store.StoreScreen.open(Mc.screen(), kind);
            });
            run(1.0f, () -> store("tidy"));
        }
        run(1.0f, () -> {
            o.restyleMenus.set(was[0]);
            AllerClient.config().save();
        });
        run(0.5f, () -> Mc.mc().stop());
    }

    /**
     * The screenshots ({@code -Paller.gallery}): three real screenshots in the test world (the second
     * while the card of the first is up, which must not be in it), the card, its copy and delete with
     * the undo, then the screen: grid, favourites, a picture full size, zoomed, renamed, deleted and
     * brought back. What it took is deleted again at the end, through the recycle bin.
     */
    private static void gallery() {
        List<dev.aller.feature.Shots.Shot> mine = new ArrayList<>();
        Runnable take = () -> dev.aller.platform.Nav.screenshot(message -> AllerClient.LOG.info("GALLERY chat line: {}", message));
        Runnable note = () -> {
            var all = dev.aller.feature.Shots.all();
            if (!all.isEmpty() && !mine.contains(all.get(0))) mine.add(all.get(0));
            AllerClient.LOG.info("GALLERY {} screenshots known, newest {}", all.size(), all.isEmpty() ? null : all.get(0).name);
        };
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        shot(3.5f, "gallery-menu");
        if (canEnterWorld()) {
            run(0.1f, DevHarness::enterWorld);
            until(() -> Mc.mc().player != null && Mc.mc().level != null && Mc.screen() == null, 90);
            run(3.0f, take);
            shot(0.6f, "gallery-card");
            run(0.1f, note);
            // With the card on screen: this one waits a frame for it to step aside.
            run(1.2f, take);
            shot(0.8f, "gallery-card-second");
            run(0.1f, () -> {
                note.run();
                dev.aller.ui.ShotCard.dev("copy");
            });
            shot(2.5f, "gallery-card-copied");
            run(0.1f, () -> dev.aller.ui.ShotCard.dev("delete"));
            shot(0.8f, "gallery-card-deleted");
            run(0.1f, () -> dev.aller.ui.ShotCard.dev("undo"));
            shot(0.8f, "gallery-card-undone");
            run(0.1f, () -> Mc.mc().pauseGame(false));
            shot(1.2f, "gallery-pause");
            run(0.1f, () -> Mc.setScreen(null));
            run(1.0f, take);
            run(1.0f, () -> {
                note.run();
                dev.aller.ui.ShotCard.dev("open");
            });
            shot(2.5f, "gallery-opened");
            run(0.1f, () -> gallery("zoom"));
            shot(1.0f, "gallery-zoomed");
            run(0.1f, () -> gallery("next"));
            shot(1.5f, "gallery-next");
            run(0.1f, () -> gallery("favourite"));
            run(0.2f, () -> gallery("rename"));
            shot(0.8f, "gallery-rename");
            run(0.1f, () -> {
                key(GLFW.GLFW_KEY_ESCAPE);
                gallery("grid");
            });
            shot(2.0f, "gallery-grid");
            run(0.1f, () -> gallery("favourites"));
            shot(1.0f, "gallery-favourites");
            run(0.1f, () -> {
                gallery("all");
                gallery("view");
            });
            run(1.0f, () -> gallery("delete"));
            shot(1.0f, "gallery-deleted");
            run(0.1f, () -> gallery("undo"));
            shot(1.0f, "gallery-undone");
            // Tidy up: everything this run took goes, and the wait lets it reach the recycle bin.
            run(0.1f, () -> {
                for (var s : mine) {
                    dev.aller.feature.Shots.favourite(s, false);
                    AllerClient.LOG.info("GALLERY deleting {}: {}", s.name, dev.aller.feature.Shots.delete(s));
                }
            });
            run(dev.aller.feature.Shots.UNDO + 4f, () -> AllerClient.LOG.info("GALLERY {} screenshots left", dev.aller.feature.Shots.all().size()));
        }
        run(0.5f, () -> Mc.mc().stop());
    }

    /** Runs a command on the test world's server, as the server. */
    private static void server(String command) {
        var server = Mc.mc().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    /**
     * The HUD elements, item mods, tab list, chat bubbles and photo mode ({@code -Paller.mods}), in
     * the test world with a few things given to the player and a chest put down beside them. All of
     * it is taken away again at the end.
     */
    private static void mods() {
        String[] ids = {"looking_at", "held_item", "inventory_view", "free_slots", "cooldowns", "elytra", "mount", "experience", "rotation", "tps",
                "timer", "session_stats", "pack_display", "now_playing", "durability_warnings", "vitals_warning", "player_list", "inventory_search",
                "container_preview", "item_details", "enchant_notes", "item_lock", "chest_memory", "chat_bubbles", "photo_mode"};
        int[] chest = new int[3];
        until(() -> Mc.current() instanceof MainMenuScreen && !Mc.loadingOverlay());
        if (canEnterWorld()) {
            run(1f, DevHarness::enterWorld);
            until(() -> Mc.mc().player != null && Mc.mc().level != null && !Mc.loadingOverlay() && Mc.screen() == null, 90);
            run(3f, () -> {
                for (String id : ids) {
                    Module m = AllerClient.modules().get(id);
                    savedState.put(m, m.enabled());
                    m.setEnabled(true);
                }
                var p = Mc.mc().player;
                // Survival, so the inventory is the ordinary one and not the creative tabs.
                server("gamemode survival @a");
                server("clear @a");
                server("give @a diamond_pickaxe[enchantments={\"minecraft:efficiency\":5,\"minecraft:unbreaking\":3,\"minecraft:mending\":1},damage=1480]");
                server("give @a shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:64}},{slot:1,item:{id:\"minecraft:golden_apple\",count:8}},{slot:13,item:{id:\"minecraft:ender_pearl\",count:16}}]]");
                server("give @a cooked_beef 12");
                server("give @a ender_pearl 16");
                server("give @a firework_rocket 40");
                server("give @a oak_log 64");
                server("give @a coal 20");
                chest[0] = p.blockPosition().getX() + 2;
                chest[1] = p.blockPosition().getY();
                chest[2] = p.blockPosition().getZ();
                server("setblock " + chest[0] + " " + chest[1] + " " + chest[2] + " chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:12},{Slot:4b,id:\"minecraft:iron_ingot\",count:40},{Slot:9b,id:\"minecraft:diamond_sword\",count:1}]}");
                dev.aller.feature.Timers.start("5m harness");
                // Looking down at the ground, so there is a block under the crosshair.
                p.setXRot(55f);
            });
            shot(2.5f, "mods-hud");
            run(0.1f, () -> {
                var p = Mc.mc().player;
                AllerClient.LOG.info("MODS tps={} silence={} timers={}", dev.aller.feature.ServerClock.tps(), dev.aller.feature.ServerClock.silence(),
                        dev.aller.feature.Timers.all().size());
                for (int i = 0; i < 9; i++) {
                    var stack = p.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;
                    var lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(Mc.mc().level), p, net.minecraft.world.item.TooltipFlag.NORMAL);
                    AllerClient.LOG.info("MODS tooltip of slot {}: {}", i, lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
                }
                Mc.open(new HudEditorScreen(null));
            });
            shot(1.2f, "mods-hud-editor");
            run(0.1f, () -> {
                Mc.setScreen(null);
                dev.aller.module.Modules.PLAYER_LIST.always.set(true);
            });
            shot(1.0f, "mods-tab");
            run(0.1f, () -> {
                dev.aller.module.Modules.PLAYER_LIST.always.set(false);
                Mc.mc().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                Mc.mc().player.setXRot(10f);
                String me = dev.aller.platform.Nav.playerName();
                dev.aller.platform.ChatView.add(net.minecraft.network.chat.Component.literal("[VIP] " + me + " » the bubble should sit over my head, wrapped on to a second line"));
                dev.aller.platform.ChatView.add(net.minecraft.network.chat.Component.literal(me + " joined the game"));
            });
            shot(0.8f, "mods-bubble");
            run(0.1f, () -> {
                Mc.mc().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                dev.aller.feature.Containers.dev("lock", 0);
                Mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(Mc.mc().player));
            });
            // The shulker box is in the second hotbar slot, which is slot 37 of the inventory screen.
            run(0.5f, () -> dev.aller.feature.Containers.dev("hover", 37));
            shot(0.8f, "mods-inventory-preview");
            run(0.1f, () -> {
                dev.aller.feature.Containers.dev("hover", -1);
                dev.aller.feature.Containers.dev("search:pick", 0);
            });
            shot(0.8f, "mods-inventory-search");
            run(0.1f, () -> {
                AllerClient.LOG.info("MODS drop from a locked slot refused: {}", dev.aller.feature.Containers.blockDrop());
                dev.aller.feature.Containers.dev("search:", 0);
                dev.aller.feature.Containers.dev("lock", 0);
                Mc.setScreen(null);
            });
            run(0.3f, () -> {
                var pos = new net.minecraft.core.BlockPos(chest[0], chest[1], chest[2]);
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
                var result = Mc.mc().gameMode.useItemOn(Mc.mc().player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                AllerClient.LOG.info("MODS used the chest: {}", result);
            });
            until(() -> Mc.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, 5);
            run(0.5f, () -> dev.aller.feature.Containers.dev("search:dia", 0));
            shot(0.8f, "mods-chest");
            run(0.1f, () -> {
                dev.aller.feature.Containers.dev("search:", 0);
                Mc.mc().player.closeContainer();
            });
            run(0.5f, () -> {
                AllerClient.LOG.info("MODS chests known: {}", dev.aller.feature.ChestMemory.known());
                Mc.mc().player.setYRot(-90f);
                Mc.mc().player.setXRot(20f);
                dev.aller.feature.ChestMemory.find("diamond");
            });
            shot(1.0f, "mods-chest-find");
            run(0.1f, () -> {
                dev.aller.feature.ChestMemory.clearMarks();
                dev.aller.feature.Photo.open();
            });
            shot(1.2f, "mods-photo");
            run(0.1f, () -> {
                if (Mc.current() instanceof dev.aller.screen.PhotoScreen s) s.dev();
            });
            shot(1.5f, "mods-photo-set");
            run(0.1f, dev.aller.feature.Photo::shoot);
            run(1.5f, () -> {
                // The picture it took, with the panel left out: copied beside the captures, then deleted.
                var all = dev.aller.feature.Shots.all();
                AllerClient.LOG.info("MODS photo taken: {}", all.isEmpty() ? null : all.get(0).name);
                if (!all.isEmpty()) {
                    try {
                        java.nio.file.Files.copy(dev.aller.feature.Shots.dir().resolve(all.get(0).name), dir.resolve("mods-photo-result.png"),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.io.IOException e) {
                        AllerClient.LOG.warn("MODS could not copy the photo", e);
                    }
                    dev.aller.feature.Shots.delete(all.get(0));
                }
            });
            shot(0.5f, "mods-photo-after");
            run(0.1f, () -> {
                AllerScreen s = Mc.current();
                if (s != null) s.close();
            });
            until(() -> Mc.screen() == null, 5);
            shot(0.8f, "mods-after-photo");
            run(0.1f, () -> {
                AllerClient.LOG.info("MODS photo mode left: active={} hud hidden={} camera={}", dev.aller.feature.Photo.active(), Mc.hudHidden(),
                        Mc.mc().options.getCameraType());
                server("clear @a");
                server("gamemode creative @a");
                server("setblock " + chest[0] + " " + chest[1] + " " + chest[2] + " air");
                dev.aller.feature.ChestMemory.forgetWorld();
                dev.aller.feature.Timers.clear();
                savedState.forEach(Module::setEnabled);
                AllerClient.config().save();
            });
            run(dev.aller.feature.Shots.UNDO + 4f, () -> {});
        }
        run(0.5f, () -> Mc.mc().stop());
    }

    private static void gallery(String action) {
        if (Mc.current() instanceof dev.aller.screen.shots.ShotsScreen s) s.dev(action);
        AllerClient.LOG.info("GALLERY {} on {}", action, Mc.current() == null ? null : Mc.current().getClass().getSimpleName());
    }

    private static void store(String action) {
        if (Mc.current() instanceof dev.aller.screen.store.StoreScreen s) s.dev(action);
        AllerClient.LOG.info("STORE {} on {}", action, Mc.current() == null ? null : Mc.current().getClass().getSimpleName());
    }

    /** Opens a menu over the one the script left from and captures it once it has settled. */
    private static void skinShot(String name, java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> open) {
        run(0.1f, () -> open.accept(home));
        shot(1.0f, name);
    }

    private static void pocketLog(String at) {
        var mc = Mc.mc();
        var server = mc.getSingleplayerServer();
        AllerClient.LOG.info("POCKET {}: state={} enabled={} level={} pos={} local={} world='{}' mode={}", at,
                dev.aller.feature.Pocket.state(), dev.aller.module.Modules.POCKET.enabled(),
                mc.level == null ? null : dev.aller.platform.Game.dimensionId(),
                mc.player == null ? null : mc.player.blockPosition().toShortString(), mc.isLocalServer(),
                server == null ? null : server.getWorldData().getLevelName(),
                mc.gameMode == null ? null : mc.gameMode.getPlayerMode());
    }

    private static boolean spin;
    private static boolean benchWasFullscreen;
    private static long benchStart;
    private static int benchFrames;

    private static void measure(String name, java.util.function.Predicate<Module> enabled) {
        run(0.2f, () -> {
            for (Module m : AllerClient.modules().all()) m.setEnabled(enabled.test(m));
        });
        run(2f, () -> {
            benchFrames = 0;
            benchStart = System.nanoTime();
        });
        run(5f, () -> AllerClient.LOG.info("BENCH {}: {} fps", name,
                Math.round(benchFrames / ((System.nanoTime() - benchStart) / 1e9))));
    }

    private static net.minecraft.client.gui.screens.Screen home;
    private static long introFrames, introStart;
    private static float harnessClickY;

    /** Opens a menu from the main menu (which it returns to on back) and captures it. */
    private static void menuShot(String name, java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> open) {
        run(0.1f, () -> {
            if (home == null) home = Mc.screen();
            open.accept(home);
        });
        shot(1.0f, name);
    }

    /** Opens another mod's screen by name: a static factory taking the parent, or (null method) a constructor. */
    private static void openStatic(net.minecraft.client.gui.screens.Screen parent, String method, String... classes) {
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name);
                Object screen = method != null
                        ? type.getMethod(method, net.minecraft.client.gui.screens.Screen.class).invoke(null, parent)
                        : type.getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(parent);
                Mc.setScreen((net.minecraft.client.gui.screens.Screen) screen);
                return;
            } catch (ReflectiveOperationException e) {
                AllerClient.LOG.debug("Harness could not open {}", name, e);
            }
        }
    }

    private static void until(BooleanSupplier ready) {
        until(ready, Float.MAX_VALUE);
    }

    /** Waits for a condition; if it has not happened after {@code timeout} seconds, the run ends early. */
    private static void until(BooleanSupplier ready, float timeout) {
        steps.add(new Step(ready, 0, null, null, timeout));
    }

    private static void shot(float delay, String name) {
        steps.add(new Step(null, delay, name, null, 0));
    }

    private static void run(float delay, Runnable action) {
        steps.add(new Step(null, delay, null, action, 0));
    }

    private static void type(String text) {
        AllerScreen s = Mc.current();
        if (s != null) text.chars().forEach(s::charTyped);
    }

    private static void key(int code) {
        AllerScreen s = Mc.current();
        if (s != null) s.keyDown(code, 0);
    }

    private static boolean canEnterWorld() {
        return !Boolean.getBoolean("aller.dev.noWorld");
    }

    /** Opens (creating it the first time) a creative test world. */
    private static void enterWorld() {
        var mc = Mc.mc();
        mc.options.pauseOnLostFocus = false;
        String id = System.getProperty("aller.dev.world", "aller-dev");
        if (mc.getLevelSource().levelExists(id)) {
            mc.createWorldOpenFlows().openWorld(id, () -> {});
            return;
        }
        //? if <26.1 {
        /*var settings = new net.minecraft.world.level.LevelSettings(id, net.minecraft.world.level.GameType.CREATIVE, false,
                net.minecraft.world.Difficulty.NORMAL, true,
                new net.minecraft.world.level.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS),
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        *///?} else {
        var settings = new net.minecraft.world.level.LevelSettings(id, net.minecraft.world.level.GameType.CREATIVE,
                net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        //?}
        mc.createWorldOpenFlows().createFreshLevel(id, settings,
                new net.minecraft.world.level.levelgen.WorldOptions("aller".hashCode(), true, false),
                net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, Mc.screen());
    }

    public static void frameEnd() {
        benchFrames++;
        if (spin && Mc.mc().player != null) Mc.mc().player.setYRot(Mc.mc().player.getYRot() + 3f);
        if (dir == null || next >= steps.size()) return;
        Step s = steps.get(next);
        if (s.ready != null && !s.ready.getAsBoolean()) {
            if (waitingSince < 0) waitingSince = Motion.time();
            if (Motion.time() - waitingSince > s.timeout) {
                AllerClient.LOG.warn("Dev harness gave up waiting at step {}; exiting", next);
                next = steps.size();
                savedState.forEach(Module::setEnabled);
                AllerClient.defer(() -> Mc.mc().stop());
            }
            return;
        }
        waitingSince = -1;
        if (mark < 0) mark = Motion.time();
        if (Motion.time() - mark < s.delay) return;
        if (s.action != null && pending > 0) return; // let captures land before moving on
        next++;
        mark = -1;
        if (s.shot != null) capture(s.shot);
        if (s.action != null) AllerClient.defer(s.action);
    }

    private static void capture(String name) {
        pending++;
        Screenshot.takeScreenshot(Mc.mainTarget(), image -> {
            try (image) {
                Files.createDirectories(dir);
                image.writeToFile(dir.resolve(name + ".png"));
                AllerClient.LOG.info("Captured {}", name);
            } catch (Exception e) {
                AllerClient.LOG.error("Capture {} failed", name, e);
            } finally {
                pending--;
            }
        });
    }
}
