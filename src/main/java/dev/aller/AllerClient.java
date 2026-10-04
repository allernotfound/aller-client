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
    /** As in the jar's name, without the Minecraft version after the plus ("1.0.1"). */
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(ID)
            .map(m -> m.getMetadata().getVersion().getFriendlyString().split("[+]")[0]).orElse("dev");
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
        dev.aller.ui.Icons.preload();
        Pipelines.init();
        Modules.registerAll(MODULES);
        CONFIG.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            dev.aller.feature.Pocket.close(true);
            CONFIG.save();
            dev.aller.feature.Updater.stopping();
            dev.aller.feature.Browser.shutdown();
        });
        dev.aller.feature.Combat.init();
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                dev.aller.feature.Tooltips.append(stack, type.isAdvanced(), lines));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) dev.aller.feature.ChestMemory.used(hit.getBlockPos());
            return net.minecraft.world.InteractionResult.PASS;
        });
        dev.aller.feature.Pocket.init();
        dev.aller.feature.Updater.init();
        DevHarness.init();
        LOG.info("{} {} ready with {} modules", NAME, VERSION, MODULES.all().size());
    }

    private static void tick() {
        Runnable task;
        while ((task = DEFERRED.poll()) != null) task.run();

        dev.aller.feature.Combat.tick();
        dev.aller.feature.Session.tick();
        dev.aller.feature.AutoProfiles.tick();
        dev.aller.feature.Pocket.tick();
        dev.aller.feature.Shots.tick();
        dev.aller.feature.Timers.tick();
        dev.aller.feature.Updater.tick();
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
