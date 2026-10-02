package dev.aller.module;

import dev.aller.hud.elements.Coordinates;
import dev.aller.hud.elements.InfoHuds;
import dev.aller.hud.elements.Keystrokes;
import dev.aller.hud.elements.StatusHuds;
import dev.aller.hud.elements.WideHuds;
import dev.aller.module.mods.ChatMods;
import dev.aller.module.mods.CosmeticMods;
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
    public static final UtilityMods.ToggleSprint TOGGLE_SPRINT = new UtilityMods.ToggleSprint();
    public static final UtilityMods.Freelook FREELOOK = new UtilityMods.Freelook();
    public static final UtilityMods.ReplayMod REPLAY = new UtilityMods.ReplayMod();
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
    public static final WideHuds.PlayerList PLAYER_LIST = new WideHuds.PlayerList();
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
        m.register(PLAYER_LIST);
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

        // Utility
        m.register(TOGGLE_SPRINT);
        m.register(FREELOOK);
        m.register(REPLAY);
        m.register(BROWSER);

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

        // Cosmetic
        m.register(CAPE);
        m.register(VIEWMODEL);
        m.register(HIT_PARTICLES);
        m.register(OWN_NAMETAG);
    }
}
