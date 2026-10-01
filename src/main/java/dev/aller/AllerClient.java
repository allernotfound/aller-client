package dev.aller;

import dev.aller.config.Config;
import dev.aller.dev.DevHarness;
import dev.aller.module.ModuleManager;
import dev.aller.module.Modules;
import dev.aller.platform.Mc;
import dev.aller.platform.Pipelines;
import dev.aller.screen.HudEditorScreen;
import dev.aller.screen.PaletteScreen;
import dev.aller.ui.font.Fonts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;

public final class AllerClient implements ClientModInitializer {
    public static final String ID = "aller";
    public static final String NAME = "Aller Client";
    public static final String VERSION = "0.1.0";
    public static final Logger LOG = LoggerFactory.getLogger(NAME);

    private static final ClientOptions OPTIONS = new ClientOptions();
    private static final ModuleManager MODULES = new ModuleManager();
    private static final Config CONFIG = new Config();
    private static final Queue<Runnable> DEFERRED = new ArrayDeque<>();
    private static boolean menuKeyWasDown, editKeyWasDown;

    public static ClientOptions options() {
        return OPTIONS;
    }

    public static ModuleManager modules() {
        return MODULES;
    }

    public static Config config() {
        return CONFIG;
    }

    /** Runs a task at the end of the current client tick, outside of rendering and input dispatch. */
    public static void defer(Runnable task) {
        DEFERRED.add(task);
    }

    @Override
    public void onInitializeClient() {
        Fonts.preload();
        Pipelines.init();
        Modules.registerAll(MODULES);
        CONFIG.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CONFIG.save());
        dev.aller.feature.Combat.init();
        DevHarness.init();
        LOG.info("{} {} ready with {} modules", NAME, VERSION, MODULES.all().size());
    }

    private static void tick() {
        Runnable task;
        while ((task = DEFERRED.poll()) != null) task.run();

        dev.aller.feature.Combat.tick();
        dev.aller.feature.Session.tick();
        dev.aller.feature.AutoProfiles.tick();
        MODULES.tick();
        CONFIG.tick();

        boolean free = Mc.mc().player != null && Mc.screen() == null;
        boolean down = free && Mc.isDown(OPTIONS.menuKey.get());
        if (down && !menuKeyWasDown) Mc.open(new PaletteScreen());
        menuKeyWasDown = down;
        boolean edit = free && Mc.isDown(OPTIONS.hudEditorKey.get());
        if (edit && !editKeyWasDown) Mc.open(new HudEditorScreen(null));
        editKeyWasDown = edit;
    }
}
