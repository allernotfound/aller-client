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
    private record Step(BooleanSupplier ready, float delay, String shot, Runnable action) {}

    private static Path dir;
    private static final List<Step> steps = new ArrayList<>();
    private static final Map<Module, Boolean> savedState = new HashMap<>();
    private static float mark = -1;
    private static int next;
    private static int pending;

    private DevHarness() {}

    public static void init() {
        String out = System.getProperty("aller.dev.shots");
        if (out == null) return;
        dir = Path.of(out);

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

        if (canEnterWorld()) {
            run(0.1f, DevHarness::enterWorld);
            until(() -> Mc.mc().player != null && Mc.mc().level != null && !Mc.loadingOverlay() && Mc.screen() == null);
            shot(6.0f, "hud");
            run(0.1f, () -> {
                for (Module m : AllerClient.modules().all()) {
                    savedState.put(m, m.enabled());
                    m.setEnabled(true);
                }
            });
            shot(2.5f, "hud-all");
            run(0.1f, () -> Mc.open(new HudEditorScreen(null)));
            shot(1.0f, "hud-editor");
            run(0.1f, () -> key(GLFW.GLFW_KEY_E));
            shot(0.8f, "hud-editor-drawer");
            run(0.1f, () -> Mc.open(new PaletteScreen()));
            shot(1.2f, "palette-world");
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

    private static void until(BooleanSupplier ready) {
        steps.add(new Step(ready, 0, null, null));
    }

    private static void shot(float delay, String name) {
        steps.add(new Step(null, delay, name, null));
    }

    private static void run(float delay, Runnable action) {
        steps.add(new Step(null, delay, null, action));
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
        String id = "aller-dev";
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
        if (dir == null || next >= steps.size()) return;
        Step s = steps.get(next);
        if (s.ready != null && !s.ready.getAsBoolean()) return;
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
