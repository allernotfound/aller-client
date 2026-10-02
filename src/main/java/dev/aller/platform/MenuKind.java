package dev.aller.platform;

import dev.aller.AllerClient;
import dev.aller.screen.MenuSkin.Menu;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.TestBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.TestInstanceBlockEditScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.WarningScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.ExperimentsScreen;
import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.realms.RealmsScreen;
//? if <26.1 {
/*import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
*///?} else {
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.worldselection.AbstractGameRulesScreen;
//?}

/**
 * Sorts a screen into the group whose switch decides whether it is restyled. Vanilla screens are
 * matched by class, not by name: on 1.21.8 their names are obfuscated in a real install.
 */
public final class MenuKind {
    private MenuKind() {}

    /** @return null for screens that are never restyled (Aller's own, inventories, chat, in-world editors) */
    public static Menu of(Screen s) {
        if (s == null || s instanceof ScreenHost) return null;
        String name = s.getClass().getName();
        if (name.startsWith("net.caffeinemc.")) return Menu.VIDEO;
        if (name.startsWith("net.irisshaders.")) return Menu.SHADERS;
        if (name.startsWith("com.terraformersmc.modmenu.")) return Menu.MOD_LIST;
        // Essential draws its screens itself, through its own toolkit.
        if (name.startsWith("gg.essential.")) return null;

        if (s instanceof AbstractContainerScreen<?> || s instanceof ChatScreen || s instanceof TitleScreen
                || s instanceof WinScreen || s instanceof AccessibilityOnboardingScreen) return null;
        // Books, signs and the technical block editors draw on their own textures.
        if (s instanceof BookViewScreen || s instanceof BookEditScreen || s instanceof BookSignScreen
                || s instanceof AbstractSignEditScreen || s instanceof AbstractCommandBlockEditScreen
                || s instanceof JigsawBlockEditScreen || s instanceof StructureBlockEditScreen
                || s instanceof TestBlockEditScreen || s instanceof TestInstanceBlockEditScreen) return null;
        if (AllerClient.options().customLoading.get() && LoadingInfo.of(s) != null) return null;
        // What is left that does not pause is an in-world overlay (death screen, game mode switcher).
        if (!s.isPauseScreen()) return null;

        if (s instanceof RealmsScreen) return Menu.REALMS;
        if (s instanceof VideoSettingsScreen) return Menu.VIDEO;
        if (s instanceof OptionsScreen || s instanceof OptionsSubScreen) return Menu.OPTIONS;
        if (s instanceof SelectWorldScreen || s instanceof CreateWorldScreen || s instanceof EditWorldScreen
                || s instanceof ExperimentsScreen || s instanceof OptimizeWorldScreen
                || s instanceof ConfirmExperimentalFeaturesScreen || gameRules(s)) return Menu.WORLDS;
        if (s instanceof JoinMultiplayerScreen || s instanceof DirectJoinServerScreen || s instanceof WarningScreen
                || serverEditor(s)) return Menu.MULTIPLAYER;
        if (s instanceof PackSelectionScreen) return Menu.PACKS;
        return name.startsWith("net.minecraft.") || name.startsWith("com.mojang.") ? Menu.OTHER : Menu.OTHER_MODS;
    }

    /** Screens drawn from plain rectangles (Sodium's), where those rectangles are restyled too. */
    public static boolean flat(Screen s) {
        return s.getClass().getName().startsWith("net.caffeinemc.");
    }

    private static boolean gameRules(Screen s) {
        //? if <26.1 {
        /*return s instanceof EditGameRulesScreen;
        *///?} else {
        return s instanceof AbstractGameRulesScreen;
        //?}
    }

    private static boolean serverEditor(Screen s) {
        //? if <26.1 {
        /*return s instanceof EditServerScreen;
        *///?} else {
        return s instanceof ManageServerScreen;
        //?}
    }
}
