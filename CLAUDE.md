# Aller Client

A Fabric client mod (mod id `aller`, package `dev.aller`): a polished custom client in the style of
Lunar Client. It is **not a hack client**. Features that some servers restrict (freelook,
fullbright, waypoints) are marked with `Module.restricted(note)`, which shows a badge and a one-time
warning; Aller never blocks them and never adds anything that gives an unfair advantage.

## Build and run

One source tree builds for several Minecraft versions through Stonecutter. Currently `1.21.8` and
`26.2`; the active (uncommented) version is `26.2`.

```sh
./gradlew :26.2:compileJava :1.21.8:compileJava   # always compile BOTH after a change
./gradlew :26.2:runClient                         # play (also :1.21.8:runClient)
./gradlew :26.2:build                             # jar in versions/26.2/build/libs
```

- Gradle 9.8 wrapper. 26.2 needs JDK 25, 1.21.8 needs JDK 21 (both installed; toolchains pick).
- 1.21.8 uses `net.fabricmc.fabric-loom-remap` (Mojang mappings); 26.2 is unobfuscated and uses
  `net.fabricmc.fabric-loom`. `build.gradle.kts` picks by `stonecutter.eval(mc, "<26.1")`.
- Per-version dependency versions live in `versions/<mc>/gradle.properties`.
- `native/` is a Rust crate (the browser's webview bridge). `cargoBuild` runs `cargo build --release`
  before `processResources` and packs the DLL into the jar under `natives/windows-x64`. Without
  cargo the build still passes and the browser says it is unavailable.
- Do not fetch stonecutter.kikugie.dev (it blocks automated clients).

### Self-test harness

`./gradlew :26.2:runClient -Paller.shots=<dir>` (or `:1.21.8:`) launches the game, captures the
splash, walks the main menu, palette pages, a creative test world (`aller-dev`), the HUD editor and
the pause menu, writes a PNG of each to `<dir>` and exits. Use it to verify UI or mixin changes,
then read the PNGs and grep the log for `ERROR|Mixin apply|Exception`. The script is
`dev/DevHarness.java`; add a step when you add a screen. It opens a real game window for about a
minute.

- `-Paller.compat` also loads Sodium and Iris (versions in `versions/<mc>/gradle.properties`).
  Leave it off while their jars sit in `versions/<mc>/run/mods` (they are there now, for manual
  testing), or each loads twice. The harness captures their screens whenever they are loaded.
- `-Paller.world=<name>` uses a different test world. Needed when the user has the game open in
  `aller-dev` themselves: the world is then locked and the harness gives up after the menus.
- `-Paller.noWorld` skips the world entirely.
- `-Paller.pocket` runs the pocket dimension script instead (in, chat and a pocket command, out by
  the door, in again, out by key; `POCKET` lines in the log). It also lets the pocket open from the
  singleplayer test world, which then stands in for a server.
- `-Paller.bench` measures average FPS in the test world with Aller idle, at defaults and with
  heavier module sets, and logs `BENCH` lines (`-Paller.bench=each` also times every non-HUD mod
  alone). It runs windowed: a fullscreen window that loses focus is minimised and vanilla then caps
  it at 10 fps. Numbers are only comparable within one run, and noisy if another game is open.

A mixin that compiles can still fail at class load, so run the harness on **both** versions after
touching anything in `mixin/`.

## Stonecutter rules

Version-specific code uses comment directives. The 26.2 branch is live code; the older branch sits
inside a block comment:

```java
//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}
```

- Never put `/* */` comments inside a versioned block (they would end the outer comment).
- Keep version differences inside `dev.aller.platform` (and `mixin/`). Everything else should be
  version-independent and talk to Minecraft through `Canvas`, `Mc`, `Game`, `Nav`, `Skins`, `Sounds`.
- Known API differences are already wrapped there (GuiGraphics vs GuiGraphicsExtractor, Screen
  render vs extractRenderState, input event records, ResourceLocation vs Identifier, `mc.screen`
  vs `mc.gui.screen()`, Gui vs Hud, and so on). Check `platform/` before adding a new branch.

## Architecture

```
AllerClient        entry point; options(), modules(), config(), defer(); client tick
Frame              per-frame hooks: begin/end, and the in-game HUD layer
Hooks              the ONLY surface mixins call; delegates to modules
mixin/             thin, versioned injectors. No logic here.
platform/          version adapters + Canvas (the drawing API)
ui/                Theme tokens, Colors, AllerScreen base, Toasts, Graph
ui/anim/           Spring, Tween, Easing, Motion (global clock, speed, reduce-motion)
ui/font/           runtime SDF atlas built from the bundled Inter TTFs
ui/widget/         Button, Toggle, TextField, Scroll, SettingsView
setting/           Setting types (Bool, Num, Color, Choice, Key, Text) + Configurable
module/            Module, ModuleManager, Modules (the catalogue), mods/*
hud/               HudModule (anchored, scalable), TextHud, elements/*
feature/           Session, Waypoints, Replay, AutoProfiles, Combat, Clicks, View,
                   Chat (+ ChatText, ChatFormats, ChatLog, ChatArchive), Wardrobe, Browser,
                   Pocket (+ PocketRoom)
command/           the launcher's model: Command, Step (Num/Text/Pick), Commands (the catalogue),
                   Launcher (shortcut polling), History (recent, pinned, use counts), CustomActions,
                   Calc, Search
screen/            MainMenuScreen, PaletteScreen (+ palette/* pages), LauncherScreen, HudEditorScreen,
                   PauseMenuScreen, ChatHistoryScreen, WardrobeScreen, BrowserScreen, Screens (which vanilla screens get replaced),
                   Splash (startup overlay), LoadingSkin (paints over vanilla loading screens),
                   MenuSkin (restyles the vanilla and other mods' menus in place)
config/            client.json (options + extras) and profiles/<name>.json (modules + HUD layout)
```

### Rendering

All custom UI goes through `platform/Canvas`: rounded rects, strokes, shadows, gradients, lines, SDF
text, clipping, alpha and transform stacks. It submits custom `GuiElementRenderState` meshes to the
vanilla GUI renderer using pipelines in `Pipelines` and shaders in
`assets/aller/shaders/core/{shape,text,backdrop}`. No raw OpenGL, NanoVG or Skia: this must keep
working on the Vulkan backend and alongside Sodium and Iris.

- Shape parameters are packed into the ENTITY vertex format (UV0 local px, UV1 half size, UV2 radius
  and param, Normal.x mode). There are no custom uniforms.
- Shaders are written as `#version 150` with `#moj_import` lines; the build rewrites the version
  to 330 for 26.2 and inlines the two uniform blocks, so the pipelines can be compiled at startup
  (`Pipelines.preload`) and the splash can already draw with them. Do not add other imports.
- Only glyphs in `SdfAtlas.CHARSET` render (ASCII, Latin-1 and a few arrows and symbols); anything
  else shows as `?`, and so does a charset entry Inter has no glyph for. For pictograms use
  `ui/Icons` (drawn from Canvas primitives) rather than hunting for a Unicode symbol.
- Besides rounded boxes the shape shader draws regular polygons and a star (`Canvas.polygon`,
  `Canvas.star`); the kind travels in `Normal.y`.
- Blur is vanilla's whole-screen `blurBeforeThisStratum()`, once per frame, for modal screens. HUD
  chips use a translucent tint instead.
- The Write tool turns `\uXXXX` escapes into the real character in Java sources.

### Restyled vanilla menus

Options, video settings, world and server lists, packs, Realms, Mod Menu, Sodium, Iris and other
mods' screens keep their own layout and logic; `screen/MenuSkin` changes how their pieces are
drawn. It works at the draw call, not per screen:

- `GuiGraphicsMixin` hands every GUI sprite (`blitSprite`), texture (`innerBlit`) and rectangle
  (`fill`) to `MenuSkin` first. Widget sprites are matched by id (`widget/button_highlighted`...)
  and drawn as Aller buttons, sliders, fields, tick boxes, tabs and scrollbars; the menu
  background, list background and separator textures become the dim and the list panel.
- `ScreenMixin` brackets the screen's drawing (`MenuSkin.drawing()`), replaces the panorama with
  `Theme.scene` and drops vanilla's blur outside a world (in a world the blur stays).
- Text: `FontMixin` wraps the width provider so a restyled screen measures in Inter, and
  `GuiRenderStateMixin` hands each queued line to `platform/VanillaText`, which lays it out with
  the same advances (no kerning). Glyphs Inter lacks fall back to the vanilla font in place.
  The line equal to the screen title is drawn larger and bold.
- Sodium draws only flat rectangles, so on its screens fills are rounded and recoloured (its teal
  becomes the accent); `SodiumWidgetMixin` and `IrisGuiMixin` are `@Pseudo` with `require = 0`.
- `platform/MenuKind` sorts a screen into a `MenuSkin.Menu` group, each with a switch under
  "Minecraft menus" in the client settings. Vanilla screens must be matched with `instanceof`,
  never by class or package name (1.21.8 is obfuscated outside dev). Inventories, chat, books,
  signs and anything that does not pause are never restyled.
- Vanilla widgets keep no animation state: hover springs are keyed by the rectangle drawn.

To restyle another vanilla piece, add its sprite id to `MenuSkin.sprite`.

### UI conventions

- Screens extend `AllerScreen` (version-independent) and are shown with `Mc.open(...)`, which wraps
  them in `ScreenHost`. Opening and closing run through the `openness()` spring; call `close()` or
  `close(Runnable)` rather than swapping screens directly so the exit animation plays.
- Everything animates: use `Spring` for interactive state (hover, toggles, selection, panels) and
  `Tween` for staged intros. Never animate with raw frame counts; `Motion` applies the user's speed
  and reduce-motion settings.
- Colours, radii and surfaces come from `Theme`. The accent is user-changeable: use
  `Theme.accent()`, never a hard-coded violet.
- Widgets are immediate-style: the owner sets bounds each frame, then forwards draw and input.
- Screens lay out in scaled units: `ScreenHost` applies `AllerScreen.scale()` (the "Menu and
  palette size" option; the HUD editor uses the HUD size instead) and divides mouse coordinates,
  so screen code never multiplies by a scale itself. Use `c.width()`, not the raw GUI width.
- A panel opened from another Aller screen returns that screen from `underlay()`; the host keeps
  drawing it underneath and blurs it. Do not fade the parent out and cut to the panel.
- Main actions are big buttons; secondary destinations go in the `IconButton` strip beside them.
- Aller screens call `Toasts.draw(c)` last so toasts sit above the blur.
- `ClientOptions` is shown as two pages: `ui()` (look, sizes, motion, screens; everything declared
  above `uiCount`) and `client()` (keys, behaviour). Put a new interface setting above that line.
- The "Font" option swaps Inter for Minecraft's font inside `Canvas.text` and `Fonts.width`
  (`Fonts.vanilla()`); its scale is snapped to whole screen pixels by `Canvas.pixelFontScale`, so
  measure text at the transform it will be drawn at.
- "Pixelated corners" is done in the shape shader (cell size in `Normal.z`). In "Buttons" mode only
  shapes drawn between `c.pixel(true)` and `c.pixel(false)` are stepped.
- The layer behind a menu is `Theme.veil` (the "Behind menus" option), not a hand-rolled dim rect.
  The blur radius follows `AllerScreen.blurAmount()` through `Hooks.blurRadius`.
- A menu that opens a Minecraft screen goes through `screen/Handover` (fade out, fade back in on
  `reshown()`); the vanilla screen then eases in via `screen/Entrance`.

### Launcher (Ctrl+K)

`LauncherScreen` is a compact run-and-close bar, separate from the palette (Right Shift), which
stays the mod browser. `Launcher.poll()` reads the shortcut once a frame (no mixin), so it opens over
a world, any Aller screen and any restyled or title menu; never over chat, inventories, or while a
vanilla text box is focused. The shortcut is `ClientOptions.launcherKey`, a `Settings.Key` with
`.chord()`: modifiers are packed above the key code (`Key.pack/code/mods`), and `Mc.isDown` and
`Mc.keyName` understand the packed value.

- To add an action, add a `Command` in `Commands.build()` (fluent: `detail`, `keywords`, `when`,
  `run`, `after()` for anything that opens a screen, `danger("...")` for Enter twice, `state`/`value`
  for what shows at the right, `suggest(inWorld, onMenus)` for the empty state). Every mod and every
  setting gets a command automatically; lists that change (profiles, waypoints, custom actions) are
  rebuilt in `Commands.snapshot()`.
- A command that needs input sets a `Step`: `Num` (slider, applied live, Escape reverts), `Text`
  (may return a further step) or `Pick` (a list). An `alias` allows the value on one line
  ("fov 90", "waypoint Base"); `Command.withArg` builds that form, and its key (`opt.fov=90`) is what
  history stores.
- Prefixes: `>` actions, `#` settings, `@` waypoints, `/` sends the rest as a command. Sums and
  "x y z" coordinates are answered by `Commands.results`. Tab lists more for an entry (pin, a mod's
  settings, edit a custom action); Ctrl+1..9 run pinned commands.
- Custom actions send exactly one chat line or command per run and have no key of their own. Keep
  it that way: a bound key that sends commands is what servers call a macro.
- The palette's search also finds launcher commands and hands anything with a step, a confirmation
  or `after` to `LauncherScreen.invoke`.
- Over a vanilla menu the launcher returns it from `vanillaUnderlay()`: `ScreenHost` draws that
  screen underneath (it paints its own background and blur, so the host adds none), and
  `MenuSkin.sync` keeps treating it as the restyled screen. Aller underlays now nest (launcher over
  palette over main menu).

### Chat

Minecraft's chat window stays vanilla; the chat mods (`ChatMods`) work on it from three places, all in
`feature/Chat`:

- `Chat.incoming` sees every message once, from a `@WrapMethod` on `ChatComponent.addMessage`. In
  order: parse the line, filters (return null to hide), mentions, chat log, stack repeats, name
  colours, mention recolour, repeat counter, timestamp. A message is rebuilt from flat styled runs
  (`ChatText`) only if something recoloured it.
- `Chat.begin`/`end` bracket the window's drawing (the slide for Smooth chat, the mention tint under
  the text, the unread divider and jump button). Geometry comes from `platform/ChatView`, which
  mirrors vanilla's own layout sums; nothing injects into vanilla's drawing lambdas.
- `Chat.poll` runs once a frame while `ChatScreen` is open: Ctrl+F, right-click to copy, the jump
  button. No mixin touches `ChatScreen`.

A server sends chat as formatted text with no author, so `ChatFormats.parse` finds one: the
player's own formats first (per server, then for all; `{name}`, `{message}`, `{any}` or `regex:`),
then the first separator (`Name: message`). A mention never fires when the player's name is the
author, nor on a line containing what they sent in the last few seconds (`Chat.sent`, fed by
`ClientPacketListenerMixin`). Formats and separators are edited on the "Chat formats" palette page
and saved in `client.json`, not per profile.

A mention or private message is marked by swapping the message's `GuiMessageTag` for one of Aller's
(`Chat.MENTION`/`PRIVATE` as its log tag): vanilla then draws the bar, and `Chat.begin` recognises
the line by that tag, which works on both versions (1.21.8 lines keep no link to their message).

`ChatLog` writes `aller-chat/<world key>/<date>.log` (time, flags, text with `§` codes and `§#RRGGBB`).
`ChatHistoryScreen` searches those plus the `[CHAT]` lines of the game's own logs for days Aller has
no file for, reading blocks of 300 matches on a worker thread and keeping about 1500 rows loaded.
Message text there is drawn with `Canvas.vanillaText`, since chat is full of glyphs Inter lacks.

### Wardrobe

`WardrobeScreen` (the hanger button on the main menu's profile card, or "Skin wardrobe" in the
launcher) shows one skin on a player model beside a grid of tiles. `feature/Wardrobe` holds the data:

- The library is the PNG files in `<game dir>/aller-skins` (added by the file picker, by dropping a
  file on the window through `AllerScreen.filesDropped`, or by copying a player's skin by name).
- The skin being worn comes from `api.minecraftservices.com/minecraft/profile`, and wearing one is a
  multipart POST to `.../profile/skins`, both with the session's access token. That token goes to
  that host and nowhere else. A dev launch has no real token: `Wardrobe.offline()`.
- Mojang keeps no skin history, so past skins come from laby.net (`/api/v3/user/<uuid>/textures`,
  images from `texture.laby.net/<image_hash>.png`, cached in `aller-skins/.cache`). It is an
  unofficial API that only knows accounts it has seen; `-Paller.skinUuid=<uuid>` looks up another
  account when testing.
- Skins are decoded and compared as pixels (`Wardrobe.decode`, which also converts 64x32 skins the
  way vanilla does). Which arms a picture goes with is guessed from it and remembered in
  `client.json` (`wardrobe_arms`) once the player chooses.
- `platform/SkinTex` draws a skin: `model` goes through vanilla's GUI skin renderer, which holds one
  picture per frame, so only one 3D model may be drawn a frame and it cannot fade; tiles use `flat`.
- After a change `Skins.wear` overrides the local player's skin for the session (the game only
  fetches its own at startup); other players see it after a rejoin.

### Browser

A web browser in a panel (`BrowserScreen`), opened by the "Web browser" mod's key (Alt+B; Ctrl+B is the narrator, read in
`Browser.frame()` the way the launcher's is; `Module.ownKey` stops the manager toggling the mod with
it), the launcher ("Web browser", "web <query>", "Private browsing", bookmarks) or a link clicked
in chat (`UtilOsMixin` on `Util.OS.openUri`, taken only while chat or its confirm screen is up).

- Pages are the system webview, not an embedded engine: `native/src/lib.rs` (wry over WebView2,
  Windows x64 only) exposes it to `platform/WebNative` over JNI. Every native call is made on the
  render thread; WebView2's callbacks arrive through the message loop GLFW already pumps and only
  queue text lines, which `Browser.frame()` collects with `WebNative.poll()`. Nothing calls up into Java.
- A page is a real window: a borderless popup owned by the game window and placed over it in
  screen coordinates (not a child window: a fullscreen game presents straight to the display,
  which hides its own children). It always sits above what the game draws and cannot fade, move
  or be covered. So `BrowserScreen.sync()` shows it only while the panel is at
  rest, this screen is in front and nothing overlaps the page area (address suggestions, toasts).
  Otherwise the page area shows a spinner (opening) or `Tab.still`, a PNG from WebView2's
  `CapturePreview` drawn through `platform/Picture`. Closing waits up to 0.25 s for a fresh still.
  Anything new that draws over the page area must be added to `covered()`.
- While a page has the keyboard GLFW sees no keys. Shortcuts Aller keeps (Ctrl+T/W/L/D/H/Tab/1-9,
  Ctrl+Shift+N, F6, the open key) are registered with `WebNative.keys` and come back as `key`
  events; Escape goes to the page first and returns as `esc` from the script in `Browser.SCRIPT`.
- A page taking the keyboard makes the game window lose focus, and GLFW minimises a fullscreen
  window when that happens. `Browser.holdFullscreen` turns `GLFW_AUTO_ICONIFY` off while the
  browser is open and minimises the window itself when another program really comes to the front.
- Creating a webview blocks for a moment (wry pumps messages until WebView2 answers), so it is done
  from `AllerClient.defer`, after the open animation. Restored tabs get one only when first shown.
- `feature/Browser` owns tabs, bookmarks, history and the session, in `<game dir>/aller-browser`
  (`profile/` is WebView2's own data). Pages keep running while the window is closed; a tab unseen
  and silent for five minutes is suspended. Private tabs are a second list using WebView2's
  InPrivate profile, and Aller writes nothing about them (history, session, site icons).
- Downloads are cancelled; camera, microphone, location and notifications are denied; a page's new
  window becomes a tab.
- `Browser.ARGUMENTS` and the environment options in `lib.rs` switch off what can be switched off
  from the host: SmartScreen, Family Safety, crash uploads, sign-in with the Windows account, and
  Edge's telemetry features. The feature names were checked against the runtime's `msedge.dll`
  (154); they are Edge internals, so re-check them if they seem to stop working.

### Pocket dimension

A private world on a real integrated server, entered (key O) while the connection to the multiplayer
server stays open. Experimental, restricted (the server sees the player standing still), off by default.

- `platform/Worlds` is the core. Minecraft keeps one level, player, game mode, camera and integrated
  server; the connection that is not on screen has its set parked, and `PacketPocketMixin` swaps it
  into Minecraft's fields for exactly as long as one of its packets runs. `Worlds.tickBack()` gives
  the parked world a client tick (connection, entities, the tick-end packet), so to the server the
  player is an ordinary idle client. `Worlds.away()` is true during such a swap: a level set then is
  only stored, screens are dropped, and sounds and particles are discarded. `Worlds.flip()` changes
  which world is on screen.
- `platform/PocketServer` creates or loads `<game dir>/aller-pocket/pocket` (a void flat world) and
  starts the server by hand, since vanilla's `doWorldLoad` disconnects first. Loading a world rebinds
  the global block and item tags, so the server's are kept and put back on the way out.
- `feature/Pocket` is the visit: SEALING (black blocks close round the player, client-side, while the
  server starts and logs in parked), ARRIVING, INSIDE, LEAVING, RETURNING. `eject()` puts the player
  back at once: damage on the server, a respawn, login or reconfiguration packet, the server opening
  a screen, either connection dropping, any exception.
- `feature/PocketRoom` is the 32x16x32 black room and its lit doorway; the server repairs it every
  second and refuses to break it. Walking through the door or pressing the key leaves.
- Chat and `/commands` typed inside go to the real server; a line starting with the prefix setting
  (`\`) is a command for the pocket. "Disconnect" inside leaves the pocket, not the server.
- Its mixins are in `mixin/pocket/` with their own config, `aller.pocket.mixins.json`: not required,
  every injector `require = 0`. `PocketPlugin` records which mixins applied and which handlers (named
  `pocket$...`) are actually called; `Pocket.enter` refuses and switches the mod off if anything is
  missing. So use plain Mixin injectors there, not MixinExtras ones (they go in after the plugin
  looks), and keep the `pocket$` prefix. Every hook body is wrapped so an exception ejects rather
  than crashes.

### Adding a mod

1. Subclass `Module` (or `HudModule` / `TextHud`) in `module/mods/` or `hud/elements/`; declare
   settings as fields with `bool/num/color/choice/key/text`.
2. Register it in `Modules.registerAll` (order there is the order in the UI). Keep a static field in
   `Modules` if a hook needs to reach it.
3. If it needs to change vanilla behaviour, add a method to `Hooks` and a small mixin that calls it,
   with both version branches, and list the mixin in `aller.mixins.json`.
4. It appears in the palette and gets a settings page automatically.

Settings conventions: give a default key with `bind(key)` (or `holdKey(key)` for a hold-style mod,
which also adds the hold/toggle choice), never `keybind.set`, so resets and new profiles keep it.
Use `.describe(...)` for a line of small print under the name, `.visibleWhen(...)` for settings
that depend on another, and `Num.format(...)` when a bare number reads badly ("Never", "12:30").
A HUD element's shared look settings come from `appearance()` and are listed after its own;
`section("Title")` in a `Configurable` starts a headed group.

Mixin notes: `@Inject` handlers must take either all of the target's parameters or none (use
`@Local(argsOnly = true)` from MixinExtras to grab one). `Level` and `Entity` are shared with the
integrated server, so guard with `instanceof ClientLevel` / `LocalPlayer`.

## Style

- Java only. Match the surrounding code: short doc comments that say why, no banner comments.
- British spelling in user-facing text ("colour", "armour"), sentence case, no exclamation marks.
- Keep `Hooks` and mixins trivial; behaviour belongs in modules and features.
- Config must tolerate malformed or outdated JSON: skip what cannot be read, never crash.

## Status

Built, and checked with the harness on both versions (also with Sodium + Iris loaded): startup
splash, main menu, palette (search, category dock, pages for module settings, client settings,
profiles and auto-switch rules, waypoints, session stats), HUD editor, pause menu, restyled
loading/connecting screens, 24 HUD elements, visual and utility mods with their mixins, cosmetics
(cape, held item view, hit particles, own nametag), waypoints, session tracking, instant replay,
auto profiles, restyled vanilla menus (also Sodium 0.7 and 0.9, Iris, Mod Menu), the Ctrl+K
launcher (captured over the main menu, a vanilla menu and a world), the chat mods (mentions, history
limit, search, log, copy, name colours, stack repeats, filters, unread marker, smooth chat, chat look).

The harness only proves things load and draw. Not yet exercised by a person: anything that needs
held keys or a server (zoom, freelook, toggle sprint, replay saving, the connecting screen, ping,
tab list with many players, auto-profile rules), and Iris with a shader pack actually enabled.
Menu restyling is off by default and marked experimental. Restyled menus are only checked as still captures: hover, focus, dragging sliders, typing in
fields, tooltips, Realms, the pack screens and a non-Latin language have not been tried.

The launcher is only checked through the harness, which calls `keyDown`/`charTyped` directly: the
real Ctrl+K press, rebinding the chord, mouse use, pins, custom actions being sent, joining a server
or opening a world from it, and its look over Sodium, Iris and Mod Menu screens are untried.

The wardrobe is checked through the harness on an offline dev account (model, tiles, laby.net
history for another account). Untried: wearing a skin with a real sign-in, the file picker,
dropping a file, copying by player name, and the changed skin showing in a world.

The chat mods are checked through the harness with made-up lines in singleplayer (mention, private
message, repeats, own line, the history screen and the formats page). Untried: a real server's
formats and echo, the sounds, right-click copy, Ctrl+F from chat, the unread marker and slide while
scrolling, filters, the history limit past a few lines, and paging through large or old `.log.gz`
files.

The browser is checked through the harness on both versions (start page, a page loaded and its
still, suggestions, private tabs, history page). Untried by hand: typing and clicking in a real
page, the shortcuts while a page has the keyboard, Escape, video full screen, background audio and
mute, chat links, tab restore across launches, exclusive fullscreen, the Vulkan backend, and whether
the telemetry switches all take effect (they are passed; nothing was measured on the network).

The pocket dimension is checked through the harness on both versions with the test world standing in
for the server (26.2 with Sodium and Iris loaded). Untried: a real multiplayer server and its
anticheat, the eject paths (damage, respawn, server-opened screens, a dropped connection), a proxy
server switch, survival mode and dying inside, the Nether from inside, a non-zero chat delay (the
server's signed chat is then acknowledged on the wrong connection), a server with custom tags, and
another mod displacing one of its mixins.

Not built yet: README, more novel features (quick wheel, notes).
