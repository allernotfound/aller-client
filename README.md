<div align="center">

# Aller Client

**A polished, fair-play Minecraft client that is just a Fabric mod.**

No launcher to install. No account to make. No ads. No paid cosmetics. No telemetry.
Drop one jar in your `mods` folder and the game you already have becomes Aller Client.

Minecraft **1.21.8** and **26.2** · Fabric · Works with Sodium, Iris, Mod Menu and Essential

</div>

---

## Why Aller Client

Most custom clients ask you to trust a closed launcher that downloads code, wants you to sign in to
their service, and sells you capes. Aller Client is different:

- **It is just a mod.** One `.jar` for Fabric. It runs in your normal launcher (the official one,
  Prism, MultiMC, Modrinth App, anything that runs Fabric), next to the mods you already use.
  Nothing else gets installed on your computer.
- **Open source.** Every line is in this repository. Read it, build it yourself, check what it does.
- **Free, all of it.** No ads, no premium tier, no cosmetics shop, no "supporter" capes. Everything
  in this README is in the one download.
- **Fair play.** Aller Client is not a hack client and never will be. Nothing in it gives you an
  unfair advantage. The few features some servers restrict (freelook, fullbright, waypoint markers,
  the pocket dimension) carry a badge and warn you once, so you always know before you join.
- **Your data stays yours.** No account system, no analytics. Settings, chat logs, screenshots and
  skins live in plain files in your game folder.
- **It looks and feels finished.** Every panel springs, slides and blurs. Pick an accent colour and a
  look (smooth with the Inter font, or pixel with Minecraft's own) and the whole client, including
  Minecraft's own menus, follows it.

It plays nicely with the mods people actually run: Sodium for frame rate, Iris for shaders, Mod Menu,
and Essential (whose buttons Aller Client keeps on its own menus).

---

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.8 or 26.2.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for the same version.
3. Download Aller Client from the [Releases](../../releases) page.
4. Put both jars in your `.minecraft/mods` folder and start the game with the Fabric profile.

That is the whole install. To remove it, delete the jar.

Aller Client keeps itself up to date from the Releases page. When there is a newer version, a
popup on the main menu or the pause menu shows its release notes and asks first; nothing is
downloaded until you choose "Update now", and the new version takes over the next time you start
the game. "Check for updates" in the client settings switches it off.

Optional but recommended: [Sodium](https://modrinth.com/mod/sodium) for performance,
[Iris](https://modrinth.com/mod/iris) for shaders, [Mod Menu](https://modrinth.com/mod/modmenu).

---

## The two keys to know

| Key | Opens | What it is for |
| --- | --- | --- |
| **Right Shift** | The **palette** | Browsing and configuring every mod, the HUD, profiles, waypoints, stats |
| **Ctrl+K** | The **launcher** | Doing anything in a few keystrokes, from anywhere |

Both keys can be changed in Client settings.

### The palette (Right Shift)

The palette is the control centre. Every mod is listed by category with a toggle:

- **Type to search**, Enter toggles the top result, **Right arrow** opens its settings.
- The **category dock** under the search bar filters to HUD, Visual, Utility, World, Chat or Cosmetic
  without typing.
- Pages slide in inside the same panel: a mod's settings, UI settings, client settings, profiles and
  their auto-switch rules, waypoints, session stats, custom actions, chat formats.
- Ctrl and scroll zooms the palette so more mods fit. Show mods as a list or a grid.
- The search also finds every launcher command, so you can run those from here too.

### The launcher (Ctrl+K)

The launcher is a compact command bar, like Spotlight or Raycast for Minecraft. Press Ctrl+K, type a
few letters, press Enter, and it closes again. It opens **over the world, the main menu, Minecraft's
own menus and any Aller Client screen** (never over chat, inventories or while you are typing in a
text box).

**How to use it**

- Type anything: it fuzzy-matches names and keywords. "rend" finds Render distance, "f5" finds
  Change camera view, "tex" finds Resource packs.
- **Enter** runs the selected command. Commands that need a value show a slider or a box:
  sliders apply live as you drag, **Escape puts the old value back**.
- **Type the value on one line:** `fov 90`, `render 12`, `volume 40`, `waypoint Base`,
  `web minecraft wiki`, `say hello`.
- **Tab** on a result shows more: pin it, open a mod's settings, edit or delete a custom action,
  copy or remove a waypoint.
- **Pin** your favourites; **Ctrl+1 to Ctrl+9** run pinned commands without even looking.
- With nothing typed it suggests what is useful where you are (different in a world and on the menus),
  and remembers what you use most.
- Dangerous things (quit, delete a profile) ask for Enter twice.
- Ctrl and scroll zooms the list.

**Prefixes**

| Start with | To get |
| --- | --- |
| `>` | Actions only |
| `#` | Settings only |
| `@` | Your waypoints |
| `/` | Sends the rest as a command: `/gamemode creative` |

**It answers things, too**

- **Calculator:** `64*27+5`, `(12+4)^2`, `1000/3`. Enter copies the answer, and large numbers are also
  shown as stacks of 64.
- **Stacks:** `1500 items` tells you how many stacks and leftovers; `27 stacks` tells you how many items.
- **Coordinates:** type `100 64 -200` (or just `100 -200`) to add a waypoint there, and see the matching
  Nether or Overworld coordinates for portal building.

#### Everything in the launcher

There is a lot. Every mod and every setting gets a command automatically; on top of those:

**Go to: Aller Client's own screens**
- Browse mods (the full palette)
- Edit HUD layout
- UI settings · Client settings
- Profiles (and their auto-switch rules)
- Waypoints · Session stats · Custom actions · Chat formats
- Search chat history
- Skin wardrobe
- Screenshots
- Web browser · Search the web (`web <query>`) · Private browsing · your bookmarks (Windows 11)
- Photo mode
- Replay onboarding

**Go to: Minecraft's screens**
- Options · Video settings · Controls · Key binds · Mouse settings · Music and sounds · Language
- Chat settings · Accessibility settings · Skin customisation · Online options
- Resource packs · **Get resource packs** (the Modrinth store)
- Shader packs · **Get shader packs** (with Iris)
- Installed mods (with Mod Menu)
- Singleplayer · Multiplayer · Realms
- Pause menu · Statistics · Advancements · Open to LAN · Player reporting

**Folders**
- Open screenshots, replay clips, resource packs, chat logs, Aller Client config, logs or the game folder

**Game and session**
- Take screenshot (without the launcher in it)
- Fullscreen · Hide HUD · Change camera view
- Copy coordinates · Nether coordinates for here · Overworld coordinates for here
- Copy server IP · Copy world seed (singleplayer) · Copy teleport command · Copy location with dimension
- Copy biome · Copy facing direction · Copy world name · Copy your username · Copy your UUID
- Copy game version · Copy mod list (handy for bug reports)
- Clear chat (only on your screen) · Reload resource packs
- Send a message or command (`say ...`)
- Reconnect to last server · Join server (pick from your list) · Continue last world · Open world (pick one)
- Save and quit to title · Disconnect · Quit game
- Enter the pocket · Leave the pocket

**Aller Client**
- Add waypoint here · Add named waypoint here (`waypoint Base`) · Show all waypoints · Hide all waypoints
- Remove death markers · Remove every waypoint here
- Save replay clip · Clear replay buffer
- New profile · Switch to profile: *name* · Delete profile
- Save settings now · Reset HUD layout
- Turn every HUD element off · Turn every mod off · Reset every mod to defaults
- Clear browsing data
- New custom action, and every custom action you have made

**Minecraft's settings, right in the bar** (sliders apply live, Escape reverts)
- FOV (`fov 90`) · Render distance (`render 12`) · Simulation distance · Brightness · Max framerate
- GUI scale · Mouse sensitivity · FOV effects · Distortion effects · Entity distance
- Chat text opacity · Chat size · Menu background blur · Biome blend
- Every volume: master (`volume 40`), music, jukebox and note block, weather, block, hostile creature,
  friendly creature, player, ambient, voice, interface
- VSync · View bobbing · Entity shadows · Auto-jump · Subtitles · Toggle sprint · Toggle sneak
- Clouds · Particles

**Every mod** (toggle it, or Tab for its settings) and **every Aller Client setting** (accent colour
with `accent`, font, corners, sizes, motion, menus...).

**Custom actions** are your own shortcuts for a chat line or command you send often ("/home",
"gg", "/warp shops"). Each run sends exactly one line, and they have no key of their own: a key that
sends commands is what servers call a macro, and Aller Client does not do macros.

---

## Modules

Over 80 mods, all off-switchable, all configurable, all saved per profile. Mods marked
**restricted** are allowed on most servers but not all; Aller Client tells you once and lets you decide.

### HUD (37 elements and two warnings)

Every HUD element can be dragged, resized and snapped in the **HUD editor**, and shares a set of
look settings (background, text colour, shadow, scale).

| Mod | What it shows |
| --- | --- |
| FPS | Frames per second |
| Coordinates | Your position in the world |
| CPS | Clicks per second |
| Ping | Latency to the server |
| Keystrokes | Which movement keys and mouse buttons are pressed |
| Armour status | Your armour pieces and how worn they are |
| Potion effects | Active effects with time remaining |
| Compass | A heading strip with cardinal points and your waypoints |
| Target info | Name and health of what you are looking at |
| Live graph | A rolling graph of FPS, ping or CPS over the last minutes |
| Clock | Real-world time |
| Direction | Which way you are facing |
| Speed | How fast you are moving |
| Day counter | In-game day and time of day |
| Biome | The biome you are standing in |
| Light level | Block light where you stand (mobs spawn at 0) |
| Memory | Java memory in use |
| Server address | The server you are connected to |
| Session timer | How long you have been playing |
| Item counter | Total of the held item across your inventory |
| Saturation | The hidden hunger buffer that drains before your food bar |
| Combo counter | Hits landed in a row without being hit back |
| Reach display | Distance of your last hit |
| Looking at | The block or mob under your crosshair: its name, the tool for it, how far a crop has grown, redstone power, breaking progress |
| Held item | What is in your hand: how worn it is and what is enchanted on it |
| Inventory view | The three rows of your inventory, always in sight |
| Free slots | How many inventory slots are empty, and a warning when it fills up |
| Cooldowns | Items you cannot use again yet: ender pearls, shields, chorus fruit, wind charges |
| Elytra flight | Speed, height, pitch, wear and rockets left while you glide |
| Mount stats | Your horse's health, speed and jump height |
| Experience | Your level and the points to the next one |
| Yaw and pitch | The exact angles you are facing |
| Server TPS | How fast the server is ticking, and when it stops answering |
| Timers | Countdowns and a stopwatch started from the launcher (`timer 5m`, `stopwatch`) |
| Session stats | Kills, deaths and distance since you joined |
| Pack display | The resource pack on top of your list |
| Now playing | The song your computer is playing (Spotify, a browser, any player), with keys to pause and skip. Windows only |
| Durability warnings | A toast and a sound when a tool or a piece of armour is about to break |
| Vitals warning | The edges of the screen tint when your health or hunger runs low |

### Visual

| Mod | What it does |
| --- | --- |
| Zoom | Hold C to zoom, scroll to adjust |
| Custom crosshair | A crisp, configurable crosshair |
| Fullbright | See in the dark without torches. *Restricted* |
| Block outline | Recolour the outline of the block you look at |
| No hurt shake | Stop the camera tilting when you take damage |
| Low fire | Lower the on-fire overlay so it blocks less |
| Time changer | Show the world at a fixed time of day (visual only) |
| Weather changer | Clear skies, rain or storms whatever the server says (visual only) |
| Hide scoreboard | Hide the server's scoreboard sidebar |
| Motion blur | Real camera motion blur: each pixel blurred along the way the camera moved and turned, like a shutter, with your hand kept sharp |
| Depth of field | Focus on what you look at and soften the rest |
| Colour grading | Saturation, contrast, warmth, a colour filter and a vignette |
| Fog and sky | Push fog back or pull it in, tint fog and sky (only in clear air, never under water, in lava or with blindness) |

The effects run on the finished picture of the world, work alongside Sodium, and keep running their
colour passes on top of Iris shader packs.

### Utility

| Mod | What it does |
| --- | --- |
| Toggle sprint | Always sprint without holding the key |
| Freelook | Hold Left Alt to look around in third person without turning. *Restricted* |
| Tab list | A cleaner player list while Tab is held: faces, the server's colours, header and footer, ping in numbers, and a highlight on players near you |
| Instant replay | Keeps the last moments in memory; F8 saves them as a video clip |
| Photo mode | F9: HUD off, look round your character, roll, zoom, depth of field and colour, then a picture with nothing else in it |
| Web browser | Tabs, bookmarks and search in a panel over the game (Alt+B). Windows 11 only |
| Inventory search | Ctrl+F in any chest or inventory: type, and everything else dims. Looks inside shulker boxes too |
| Container preview | Hover a shulker box or a map to see what is in it |
| Item details | Durability in numbers, hunger and saturation, what a fuel smelts, anvil cost |
| Enchantment notes | A line under each enchantment saying what it does and how high it goes |
| Item lock | Press L over a slot to lock it: the drop key will not throw what is in it |
| Chest memory | Remembers what was in the containers you opened; `find diamond` in the launcher points at the ones that hold it. *Restricted* |

### World

| Mod | What it does |
| --- | --- |
| Waypoints | Save places and see markers pointing back to them, including where you died. *Restricted* |
| Pocket dimension | A private room you can step into while staying connected to the server (O). *Restricted* |

### Chat

| Mod | What it does |
| --- | --- |
| Mentions | A sound and a highlight when someone says your name or messages you |
| Chat history | Scroll back further than Minecraft's 100 messages |
| Chat search | Ctrl+F in chat; search everything ever said, back through old game logs |
| Chat log | Keep each server's chat in dated files, with colours |
| Copy messages | Right-click a chat message to copy it |
| Chat timestamps | The time each message arrived |
| Name colours | Every player's name in its own colour, and what they say tinted with it |
| Stack repeats | The same message again adds a counter instead of a new line |
| Chat filters | Hide adverts, vote reminders, join spam, whatever you never want to see |
| Unread marker | Scrolled up? See what arrived since and jump back down |
| Smooth chat | New messages slide up into place |
| Chat look | A chat background of its own and no signing indicators |
| Chat bubbles | What players say appears over their heads, wherever you can see them |

Servers send chat with no author attached, so Aller Client works out who said what. If a server's
format confuses it, teach it on the **Chat formats** page (per server, with `{name}` and `{message}`
placeholders, or a regex).

### Cosmetic

All cosmetics are free and visible only to you.

| Mod | What it does |
| --- | --- |
| Aller Client cape | A cape in your accent colour |
| Held item view | Resize and reposition the item in your hand |
| Hit particles | A burst of particles from whatever you hit |
| Own nametag | Your own name above your head in third person |

---

## The pocket dimension

Press **O** on a multiplayer server and black blocks close in around you. A moment later you are
standing in your own **pocket**: a private 32 by 32 room with a lit doorway, running on a real world
on your computer. **You stay connected to the server the whole time.**

- **Build in it.** It keeps what you build between visits. No one else is there.
- **Chat still works.** What you type goes to the real server, and `/commands` too. Start a line with
  `\` to send a command to the pocket instead.
- **Walk out through the door** or press O again and you are back where you stood, as if you never left.
- To the server you are simply standing still. If anything happens out there (you take damage, the
  server opens a screen, the connection drops) you are pulled back at once.

It is experimental and off by default. It is **restricted**: some servers treat standing still as
being AFK, so check the rules first.

---

## More features

### HUD editor
Drag any element anywhere, pull its corner to resize, and it snaps to edges, centres and other
elements. Arrow keys nudge (Shift for 5 px), R resets, Delete hides, E or Tab opens the drawer of
everything you can add.

### Profiles and auto-switching
Keep separate setups (a PvP HUD, a building HUD, a minimal one) and switch from the palette or the
launcher. Add **rules** and Aller Client switches for you: on a server whose address contains
something, in singleplayer, in a dimension, or in combat, then back to your usual setup afterwards.

### Restyled Minecraft menus
Options, video settings, world and server lists, resource packs, Realms, Mod Menu, Sodium's and Iris's
screens: they keep their layout but take on Aller Client's backdrop, buttons, sliders, fonts and accent
colour. Opening one from Aller Client's menu slides it in instead of cutting. Each group can be turned
off on its own, and the main menu, pause menu and loading screens are Aller Client's own.

### Pack store
"Get more packs" on the resource pack list opens a full store for **Modrinth**: search, filters, grid or
rows, a project's page with its description, gallery and versions, and one-click download. Fresh
downloads are pinned to the top of your list. The Installed tab recognises your existing packs and
finds updates. With Iris, the same for **shader packs**. No account needed.

### Skin wardrobe
Change your skin without leaving the game: a 3D model beside a library of skins. Add PNGs by file
picker or by dropping them on the window, copy any player's skin by name, see the skins you wore
before, and switch between classic and slim arms. On a server it offers to rejoin so others see it.

### Screenshots
Take a screenshot and a card slides in with it. While it is up: **Ctrl+C** copies the image,
**Ctrl+O** opens it, **Ctrl+E** shows the file, **Ctrl+Delete** deletes it (Ctrl+Z to undo). The card is
never in your next screenshot. The **Screenshots** page shows them all by day and by world or server,
with favourites, a zoomable viewer and renaming. Deleted pictures go to the recycle bin, not oblivion.

### Web browser
Alt+B opens a real browser in a panel over the game, using your system's own web engine: tabs,
bookmarks, history, search suggestions and private tabs. Look up a recipe on the wiki without
alt-tabbing. Links clicked in chat open in it. Downloads are blocked and camera, microphone and
location are always denied.

> **Windows 11 only.** The browser uses the WebView2 engine built into Windows 11. On other systems
> everything else in Aller Client works as normal and the browser simply says it is unavailable.

### Instant replay
Keeps the last 10 to 90 seconds of gameplay in memory. Something great happens, press F8, and it is
saved as a video in `aller-clips`. Nothing is written to disk until you ask.

### Chat history search
Every line from every server, searchable with filters and regex, reaching back through Minecraft's
own old log files for days before you installed Aller Client.

### Waypoints
Mark a place with one command, name it, colour it, and see a marker pointing to it in the world and on
the compass. Your death point is marked for you. Type coordinates in the launcher to add one anywhere.

### Session stats
Playtime, frame rate and combat numbers for the session, with graphs.

### Make it yours
Accent colour, font (Inter or Minecraft), smooth or pixelated corners (on buttons or everything),
what sits behind menus (blur, darken, solid or nothing), menu size, HUD size, animation speed, and a **reduce motion**
switch that turns every slide and spring into an instant change.

---

## Fair play

Aller Client does not and will not include anything that gives an unfair advantage: no reach, no
auto-clickers, no X-ray, no macros, no seeing through walls. The features that some servers do not
allow are marked **restricted**:

| Feature | Why some servers restrict it |
| --- | --- |
| Freelook | Not allowed on some servers, including Hypixel |
| Fullbright | Not allowed on some competitive servers |
| Waypoints | Markers count as a minimap on some servers |
| Pocket dimension | The server sees you standing still, which some treat as AFK |
| Chest memory | It only knows what you saw yourself, but some servers do not allow storage trackers |

They are never blocked. Turning one on shows a warning once per session (you can switch the warnings
off), and the badge stays in the palette. Fog changes are skipped wherever seeing further would help
you (under water, in lava, powder snow, with blindness or darkness).

---

## Building from source

Needs JDK 21 and JDK 25 (Gradle picks the right one for each version). Rust and cargo are optional:
without them everything builds except the web browser.

```sh
./gradlew :26.2:build        # jar in versions/26.2/build/libs
./gradlew :1.21.8:build      # jar in versions/1.21.8/build/libs
./gradlew :26.2:runClient    # play from the source tree
```

One source tree builds every supported Minecraft version through
[Stonecutter](https://stonecutter.kikugie.dev/).

---

## Licence

MIT. Icons are from [Lucide](https://lucide.dev) (ISC). The interface font is
[Inter](https://rsms.me/inter/) (SIL Open Font License).

Aller Client is not affiliated with Mojang or Microsoft.
