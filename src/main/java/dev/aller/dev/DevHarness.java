package dev.aller.dev;

import dev.aller.AllerClient;
import dev.aller.module.Module;
import dev.aller.platform.Mc;
import dev.aller.platform.ScreenHost;
import dev.aller.screen.HudEditorScreen;
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

        if (canEnterWorld()) {
            run(0.1f, DevHarness::enterWorld);
            until(() -> dev.aller.platform.LoadingInfo.of(Mc.screen()) != null, 10);
            shot(0.5f, "loading");
            // Gives up (and just exits) if the world cannot be opened, e.g. another instance has it locked.
            until(() -> Mc.mc().player != null && Mc.mc().level != null && !Mc.loadingOverlay() && Mc.screen() == null, 90);
            shot(6.0f, "hud");
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
            run(0.1f, () -> Mc.open(new PaletteScreen()));
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
                Mc.mc().pauseGame(false);
            });
            shot(1.2f, "pause");
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
