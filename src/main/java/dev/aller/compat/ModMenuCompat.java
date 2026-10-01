package dev.aller.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.aller.platform.ScreenHost;
import dev.aller.screen.PaletteScreen;
import net.minecraft.client.gui.screens.Screen;

/** Mod Menu integration. Only classloaded when Mod Menu is installed. */
public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ScreenHost(new PaletteScreen(parent));
    }

    public static Screen modsScreen(Screen parent) {
        return ModMenuApi.createModsScreen(parent);
    }
}
