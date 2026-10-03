package dev.aller.module;

import dev.aller.hud.elements.Coordinates;
import dev.aller.hud.elements.GearHuds;
import dev.aller.hud.elements.InfoHuds;
import dev.aller.hud.elements.Keystrokes;
import dev.aller.hud.elements.StatusHuds;
import dev.aller.hud.elements.WideHuds;
import dev.aller.module.mods.AlertMods;
import dev.aller.module.mods.ChatMods;
import dev.aller.module.mods.CosmeticMods;
import dev.aller.module.mods.EffectMods;
import dev.aller.module.mods.ItemMods;
import dev.aller.module.mods.UtilityMods;
import dev.aller.module.mods.VisualMods;
import dev.aller.module.mods.WorldMods;

/** The module catalogue. Order here is the order shown in the UI. */
public final class Modules {
    private Modules() {}

    // Modules that hooks or other features need to reach directly.
    public static final VisualMods.Zoom ZOOM = new VisualMods.Zoom();
    public static final VisualMods.Fullbright FULLBRIGHT = new VisualMods.Fullbright();
    public static final VisualMods.NoHurtCam NO_HURT_CAM = new VisualMods.NoHurtCam();
    public static final VisualMods.LowFire LOW_FIRE = new VisualMods.LowFire();
    public static final VisualMods.TimeChanger TIME_CHANGER = new VisualMods.TimeChanger();
    public static final VisualMods.WeatherChanger WEATHER_CHANGER = new VisualMods.WeatherChanger();
    public static final VisualMods.Crosshair CROSSHAIR = new VisualMods.Crosshair();
    public static final VisualMods.BlockOutline BLOCK_OUTLINE = new VisualMods.BlockOutline();
    public static final VisualMods.Scoreboard SCOREBOARD = new VisualMods.Scoreboard();
    public static final EffectMods.Bloom BLOOM = new EffectMods.Bloom();
    public static final EffectMods.MotionBlur MOTION_BLUR = new EffectMods.MotionBlur();
    public static final EffectMods.RimLight RIM_LIGHT = new EffectMods.RimLight();
    public static final EffectMods.DepthOfField DEPTH_OF_FIELD = new EffectMods.DepthOfField();
    public static final EffectMods.ColourGrading COLOUR_GRADING = new EffectMods.ColourGrading();
    public static final EffectMods.Sharpen SHARPEN = new EffectMods.Sharpen();
    public static final EffectMods.Atmosphere ATMOSPHERE = new EffectMods.Atmosphere();
    public static final UtilityMods.ToggleSprint TOGGLE_SPRINT = new UtilityMods.ToggleSprint();
    public static final UtilityMods.Freelook FREELOOK = new UtilityMods.Freelook();
    public static final UtilityMods.ReplayMod REPLAY = new UtilityMods.ReplayMod();
    public static final UtilityMods.PhotoMode PHOTO_MODE = new UtilityMods.PhotoMode();
    public static final UtilityMods.WebBrowser BROWSER = new UtilityMods.WebBrowser();
    public static final WorldMods.WaypointsMod WAYPOINTS = new WorldMods.WaypointsMod();
    public static final WorldMods.PocketDimension POCKET = new WorldMods.PocketDimension();
    public static final ChatMods.Timestamps CHAT_TIMESTAMPS = new ChatMods.Timestamps();
    public static final ChatMods.History CHAT_HISTORY = new ChatMods.History();
    public static final ChatMods.Mentions MENTIONS = new ChatMods.Mentions();
    public static final ChatMods.StackRepeats CHAT_STACK = new ChatMods.StackRepeats();
    public static final ChatMods.Filters CHAT_FILTERS = new ChatMods.Filters();
    public static final ChatMods.NameColours NAME_COLOURS = new ChatMods.NameColours();
    public static final ChatMods.Smooth SMOOTH_CHAT = new ChatMods.Smooth();
    public static final ChatMods.Look CHAT_LOOK = new ChatMods.Look();
    public static final ChatMods.Unread CHAT_UNREAD = new ChatMods.Unread();
    public static final ChatMods.Copy CHAT_COPY = new ChatMods.Copy();
    public static final ChatMods.Search CHAT_SEARCH = new ChatMods.Search();
    public static final ChatMods.Log CHAT_LOG = new ChatMods.Log();
    public static final ChatMods.Bubbles CHAT_BUBBLES = new ChatMods.Bubbles();
    public static final UtilityMods.PlayerList PLAYER_LIST = new UtilityMods.PlayerList();
    public static final ItemMods.ContainerPreview CONTAINER_PREVIEW = new ItemMods.ContainerPreview();
    public static final ItemMods.ItemDetails ITEM_DETAILS = new ItemMods.ItemDetails();
    public static final ItemMods.EnchantNotes ENCHANT_NOTES = new ItemMods.EnchantNotes();
    public static final ItemMods.InventorySearch INVENTORY_SEARCH = new ItemMods.InventorySearch();
    public static final ItemMods.ItemLock ITEM_LOCK = new ItemMods.ItemLock();
    public static final ItemMods.ChestMemory CHEST_MEMORY = new ItemMods.ChestMemory();
    public static final AlertMods.Vitals VITALS = new AlertMods.Vitals();
    public static final CosmeticMods.Cape CAPE = new CosmeticMods.Cape();
    public static final CosmeticMods.OwnNametag OWN_NAMETAG = new CosmeticMods.OwnNametag();
    public static final CosmeticMods.HitParticles HIT_PARTICLES = new CosmeticMods.HitParticles();
    public static final CosmeticMods.Viewmodel VIEWMODEL = new CosmeticMods.Viewmodel();

    public static void registerAll(ModuleManager m) {
        // HUD
        m.register(new InfoHuds.Fps());
        m.register(new Coordinates());
        m.register(new InfoHuds.Cps());
        m.register(new InfoHuds.Ping());
        m.register(new Keystrokes());
        m.register(new StatusHuds.Armor());
        m.register(new StatusHuds.Potions());
        m.register(new WideHuds.Compass());
        m.register(new StatusHuds.Target());
        m.register(new WideHuds.LiveGraph());
        m.register(new InfoHuds.Clock());
        m.register(new InfoHuds.Direction());
        m.register(new InfoHuds.Speed());
        m.register(new InfoHuds.Day());
        m.register(new InfoHuds.Biome());
        m.register(new InfoHuds.Light());
        m.register(new InfoHuds.Memory());
        m.register(new InfoHuds.Server());
        m.register(new InfoHuds.SessionTime());
        m.register(new InfoHuds.ItemCount());
        m.register(new InfoHuds.Saturation());
        m.register(new InfoHuds.Combo());
        m.register(new InfoHuds.Reach());
        m.register(new GearHuds.LookingAt());
        m.register(new GearHuds.HeldItem());
        m.register(new GearHuds.InventoryView());
        m.register(new InfoHuds.FreeSlots());
        m.register(new GearHuds.Cooldowns());
        m.register(new GearHuds.Elytra());
        m.register(new GearHuds.Mount());
        m.register(new InfoHuds.Experience());
        m.register(new InfoHuds.Rotation());
        m.register(new InfoHuds.ServerTps());
        m.register(new InfoHuds.Timer());
        m.register(new GearHuds.SessionStats());
        m.register(new GearHuds.PackDisplay());
        m.register(new GearHuds.NowPlaying());
        m.register(new AlertMods.Durability());
        m.register(VITALS);

        // Visual
        m.register(ZOOM);
        m.register(CROSSHAIR);
        m.register(FULLBRIGHT);
        m.register(BLOCK_OUTLINE);
        m.register(NO_HURT_CAM);
        m.register(LOW_FIRE);
        m.register(TIME_CHANGER);
        m.register(WEATHER_CHANGER);
        m.register(SCOREBOARD);
        // Bloom, rim lighting and sharpen are built but left out of the catalogue.
        m.register(MOTION_BLUR);
        m.register(DEPTH_OF_FIELD);
        m.register(COLOUR_GRADING);
        m.register(ATMOSPHERE);

        // Utility
        m.register(PLAYER_LIST);
        m.register(TOGGLE_SPRINT);
        m.register(FREELOOK);
        m.register(REPLAY);
        m.register(PHOTO_MODE);
        m.register(BROWSER);
        m.register(INVENTORY_SEARCH);
        m.register(CONTAINER_PREVIEW);
        m.register(ITEM_DETAILS);
        m.register(ENCHANT_NOTES);
        m.register(ITEM_LOCK);
        m.register(CHEST_MEMORY);

        // World
        m.register(WAYPOINTS);
        m.register(POCKET);

        // Chat
        m.register(MENTIONS);
        m.register(CHAT_HISTORY);
        m.register(CHAT_SEARCH);
        m.register(CHAT_LOG);
        m.register(CHAT_COPY);
        m.register(CHAT_TIMESTAMPS);
        m.register(NAME_COLOURS);
        m.register(CHAT_STACK);
        m.register(CHAT_FILTERS);
        m.register(CHAT_UNREAD);
        m.register(SMOOTH_CHAT);
        m.register(CHAT_LOOK);
        m.register(CHAT_BUBBLES);

        // Cosmetic
        m.register(CAPE);
        m.register(VIEWMODEL);
        m.register(HIT_PARTICLES);
        m.register(OWN_NAMETAG);
    }
}
