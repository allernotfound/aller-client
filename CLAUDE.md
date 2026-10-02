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
- `-Paller.skin` runs the restyled-menu script instead: it switches the restyling on for the run, leaves
  the main menu and the pause menu for the options with captures part way through the hand-over
  (`skin-handover-*`), walks every skinned menu (`skin-*`), repeats a few in the smooth look
  (`smooth-*`: Inter, round corners) whatever the player has chosen, and presses each Essential
  button (`essential-*`, `ESSENTIAL` lines in the log) when Essential is loaded.
- `-Paller.store` runs the pack store script instead: the button on the pack list, a search, a
  project's page, gallery and versions, a real download (deleted again), the installed list and the
  pack list with the download pinned, then the same in short for shader packs when Iris is loaded
  (`store-*`, `STORE` lines in the log). Needs the network.
- `-Paller.onboarding` runs the onboarding script instead: the intro once at speed (its frame rate is
  logged as an `ONBOARDING` line), then stills of its moments and of every step (`onboarding-*`),
  driven by `OnboardingScreen.dev`. So the intro is seen twice in the window, the second time silent
  and in jumps. The accent, font and corners it changes are put back.
- `-Paller.gallery` runs the screenshots script instead: three real screenshots in the test world (the
  second while the card is up), the card and its copy, delete and undo, then the screen's grid,
  favourites, a picture full size, zoomed and renamed, delete and undo (`gallery-*`, `GALLERY` lines
  in the log). What it takes ends in the recycle bin; the card only appears once the PNG is saved,
  about a second after the key.
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
                   Pocket (+ PocketRoom), Effects
command/           the launcher's model: Command, Step (Num/Text/Pick), Commands (the catalogue),
                   Launcher (shortcut polling), History (recent, pinned, use counts), CustomActions,
                   Calc, Search
screen/            MainMenuScreen, PaletteScreen (+ palette/* pages), LauncherScreen, HudEditorScreen,
                   PauseMenuScreen, ChatHistoryScreen, WardrobeScreen, BrowserScreen, Screens (which vanilla screens get replaced),
                   Splash (startup overlay), LoadingSkin (paints over vanilla loading screens),
                   MenuSkin (restyles the vanilla and other mods' menus in place),
                   OnboardingScreen (+ onboarding/Intro), the first run
feature/Shots      the screenshots folder: index, thumbnails, delete with undo; ui/ShotCard is the
                   slide-in, screen/shots/ShotsScreen the full page
feature/store/     Kind, Modrinth (the API), Store (pages, installed files, downloads), Images, Text
ui/doc/            Doc (Markdown and HTML to blocks) and DocView (lays them out and draws them)
screen/store/      StoreScreen (+ BrowsePane, DetailPane, InstalledPane), PackListExtras
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
  `ui/Icons` rather than hunting for a Unicode symbol.
- Icons are Lucide's (ISC licence): the SVG files in `assets/aller/icons`, named in the `Icons` enum.
  `ui/font/IconAtlas` reads them at startup with its own small SVG parser and strokes them into two
  atlases, both drawn by the text pipeline (`Canvas.icon`): a distance field, and a hard 24 by 24
  picture sampled without smoothing, used whenever "Pixelated corners" is on at all. To add an icon,
  copy its SVG in and add an enum entry; nothing is drawn by hand any more. Keys shown as text
  (the Enter and arrow legends in the launcher and palette) stay glyphs.
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
- A widget vanilla calls highlighted is only drawn so while the pointer is on it or the last input
  was the keyboard (`MenuSkin.lit`): vanilla keeps the button last clicked focused, which otherwise
  leaves it lit after coming back from the screen it opened. Keyboard focus gets an accent ring.
- Buttons lift on hover and sink while the mouse is held, like `ui.widget.Button` (the label does
  not move). A slider is the button plate, filled in the accent up to a tall thumb that stands a
  little proud of it; the handle sprite draws fill and thumb, from the track remembered just before.
- Only plain fills are touched: one drawn with another pipeline (a text box's selection) is left alone.
- Text: `FontMixin` wraps the width provider so a restyled screen measures in Inter, and
  `GuiRenderStateMixin` hands each queued line to `platform/VanillaText`, which lays it out with
  the same advances (no kerning). Glyphs Inter lacks fall back to the vanilla font in place.
  The line equal to the screen title is drawn larger and bold.
- Sodium draws only flat rectangles, so on its screens (`MenuKind.flat`) `MenuSkin.flatFill` tells them
  apart by colour: its shades of black are surfaces (rows, headings, buttons, by alpha and size; the
  rest state is remembered per rectangle so its hover animates), white and its pastel theme colours
  are marks, and the pastels become the accent in fills, text and texture tints. The gradient behind
  its page list becomes a panel. `SodiumWidgetMixin` and `IrisGuiMixin` are `@Pseudo` with `require = 0`.
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
- Aller's menus ignore Minecraft's GUI scale: `AllerScreen.scale()` includes `menuBase()`, which
  cancels it out and puts GUI scale 3 in its place (less if the window is too small for 3). Only the
  sliders in the UI settings size them. The HUD editor is the exception (it works in HUD units, and
  the HUD follows the GUI scale), and so is anything drawn on a vanilla screen (restyled menus, the
  pack list's store button). For device pixels per menu unit use `AllerScreen.density()`.
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
- A menu that opens a Minecraft screen goes through `screen/Handover`, and the two overlap: the screen
  is shown at once and eases in via `screen/Entrance` while the menu's content, drawn over it
  (`AllerScreen.drawLeaving`, which skips the menu's own backdrop), slides away. `Entrance` scales the
  screen down into place and fades everything it draws: `Entrance.alpha()` reaches textures and
  sprites through `Hooks.menuTint` (a `@ModifyVariable` on the one method every blit ends in), text
  through `VanillaText`, and plain fills by redrawing them through the canvas. Items and entity
  models are not faded. `Screens.replace` starts it for every menu (`MenuKind.of` not null), restyled
  or not; between two Minecraft menus it is the quicker, smaller version.

### Onboarding

`OnboardingScreen` takes the title screen's place (`Screens.choose`) until `client.json` has
`extras.onboarded`, never during a harness run, and "Replay onboarding" in the launcher or the
palette search runs it again. Stages, forward only: INTRO, HELLO, ACCENT, THEME, PALETTE, LAUNCHER,
HUD, MODS, FAIR, DONE. Holding Escape for a second ends it from HELLO on; the intro can only be
skipped that way on a replay (`replay`: the flag was already set), never on the first run.

- `onboarding/Intro` is 27 seconds as a function of one clock (`seek` jumps anywhere), plus a wait:
  a 3D scene through a moving camera (`camera`, `project`, `seg` clip lines at the near plane):
  a point of light, a winding tunnel of shaded panels, out over a floor with monoliths towards a
  black orb, the camera swinging round the orb while a disc of lit, tumbling shards (each a flat
  polygon whose corners are projected, sorted far to near with the orb among them) tightens on a
  row of beats, the gate, the collapse of the small shards into the letters (their places are
  sampled from Inter Bold's distance field), the hit, a held shot, and the glide of the wordmark up
  into the header. The pointer bends the tunnel and tips the disc.
- The gate (`Intro.GATE`): the clock stops until the player has mashed the eclipse open. Any fresh
  key press or click is `strike()`; pressure leaks (`STRIKE`, `LEAK`), and it waits for ever. What
  moves meanwhile runs on `gateT` and `whirl`. The harness mashes for itself; `intro:gate` is a
  still of it part way.
- Its sounds are Minecraft's own effects in `CUES`, played by `Sounds.cue(id, pitch, volume)`.
  No music under the intro (the user's call); the short note-block jingles of the colour wave and
  the ending stay.
- `Canvas.quad4` draws any four-cornered face with a colour per corner; its edges are hard, so
  outline a large face with lines.
- It is grey until a look is chosen. `Canvas.grade(saturation, dim)` drains every colour handed to
  the canvas (a vertex at a time; pictures and items are not touched) until `Canvas.ungrade()`, and
  `c.wave(x, y, radius, soft)` keeps the colour inside a circle. The wave from the chosen card is
  that circle growing; the backdrop is drawn in tiles while it crosses so the front shows on it too.
  The swatches, the full stop of the wordmark and the ember under it are drawn ungraded.
- `Theme.look` overrides the font and corner options while something is drawn (`Fonts.vanilla`,
  `Canvas.pixelated`, `pixelIcons`): onboarding is smooth until the choice whatever the options
  say, the two cards preview one look each, and during the wave each piece takes the chosen look
  once the front has passed it (`look(x, y)`), with a jolt (`kick`).
- Pixel sets the font to Minecraft and "Pixelated corners" to everything; Smooth sets Inter and off.
- The key steps use the real thing: the palette key is read in the step and opens `PaletteScreen`
  over it; the launcher's own polling opens the launcher (`capturing()` is true on every other
  stage, which is what keeps it shut there). Coming back (`reshown`) is the pass. Each can be
  rebound in place or skipped.
- Starter mods are the ids in `STARTERS`, switched with `setEnabled` (no toasts, no warning).

### Essential

Essential puts its buttons on the vanilla title and pause screens, which Aller replaces.
`compat/EssentialCompat` offers the same destinations (host or invite, social, wardrobe, pictures,
settings, and the account switcher on the main menu) as a strip of icon buttons to the right of the
column on both menus. Essential has no API for it, so each is found by reflection against its
internals (names checked against 1.5.0.1; classes are looked up without initialising them, which
crashes if done before the game has started). A button whose target is missing is left out, one
that fails says so in a toast, and Essential's own "menu layout: off" hides them all. Its screens
are opened through its `GuiUtil.openScreen`, which is what asks for its terms to be accepted.
Essential's screens are never restyled (`MenuKind`). Its loader jar does not start in a 26.2 dev
run (it looks for mapping files); the mod jar nested inside it does, and that is what sits in
`versions/26.2/run/mods`.

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

`WardrobeScreen` (the shirt button on the profile card of the main menu and the pause menu, or
"Skin wardrobe" in the launcher) is a full page like the pack store, not a panel: one skin on a
player model beside a grid of tiles. `feature/Wardrobe` holds the data:

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
  fetches its own at startup); other players see it after a rejoin. So on a multiplayer server
  (`Nav.canRejoin`: not a Realm, not in the pocket) the wardrobe then asks "Rejoin now" or "Later";
  `Nav.rejoin` disconnects and connects straight back. The model shrinks away while the question
  is up, since nothing can be drawn over it.

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

### Pack store

"Get more packs" on the resource pack list (or "Get resource packs" in the launcher) opens
`StoreScreen`, a full page over that list: Browse (search, Modrinth's filters down the left, grid or
rows) and Installed (the folder's files, matched to Modrinth by SHA-1, with updates), and a project's
page (description, gallery with a full-size view, versions with changelogs) that slides in over either.

- Everything takes a `feature/store/Kind`: `RESOURCE_PACKS` and `SHADER_PACKS` (Modrinth's "shader"
  projects for the iris or optifine loaders, which a search has to name; into Iris's folder). Mods
  would be another entry (project type, loaders, folder, file endings) plus a case in
  `PackListExtras.kind`, which says which of the game's screens gets the button.
- On Iris's `ShaderPackScreen` (matched by class name, it is Iris's own) the button reads "Get more
  shaders". Iris's list is reached by reflection when the store closes: `refresh()`, then
  `select(file)` so the download is picked out but not applied. No mixin touches Iris for this.
- `Modrinth` is the v2 API, blocking, called from `Store`'s workers; results land on the client
  thread. No account, and nothing is sent but the query and file hashes.
- Files are only fetched from `cdn.modrinth.com`, checked against Modrinth's hash, written as
  `<name>.part` and moved into place. Downloading never switches a pack on. A file that replaces
  another of the same project deletes the old one (when the game lets go of it, if it is switched on).
- `Images` fetches pictures on workers, caches the bytes in `aller-store/cache` (pruned at 256 MB),
  scales them to about the size asked for and frees textures not shown lately. WebP goes through
  TwelveMonkeys, made directly (ImageIO's registry does not see a mod's class loader). SVG and
  animated WebP fail and show their alt text. A description's pictures come from wherever it points,
  so the player's address reaches those hosts as it would in a browser; local addresses are refused.
- `Canvas.picture` draws a texture cropped to a rounded box (the `picture` shader: the shape
  shader's corners over a sampler, crop in `Normal.xy`). `Canvas.textAny` and `Fonts.widthAny` set
  the runs Inter lacks in Minecraft's font; `Text.clean` drops emoji first.
- `ui/doc`: commonmark renders Markdown to HTML, jsoup parses that with the HTML already in it, and
  `Doc` keeps what `DocView` can draw (text styles, links, headings, lists, quotes, code, tables,
  rules, pictures in the line). Web pixels are half a unit. It lays out again as pictures arrive.
- The pack list itself stays vanilla. `PackListExtras` draws the button from `ScreenMixin`'s tail
  and reads its click once a frame; `PackSelectionModelMixin` lets `platform/PackList` move fresh
  downloads to the top of Available and put an updated pack where the old file was selected;
  `PackEntryMixin` marks fresh rows. `PackList.is` tells resource packs from a world's data packs.
- The libraries are nested with Loom's `include`, which is not transitive: list each one's own.

### Screenshots

`ScreenshotMixin` hooks the one `Screenshot.grab` every saved screenshot ends in (F2, the launcher's
command, Essential's own key handling), and `feature/Shots` takes it from there.

- The callback is wrapped (`Shots.report`): from the "screenshot.success" message it learns the file,
  adds it to the list with where it was taken, and shows `ui/ShotCard`. With "Hide the chat message"
  on, that message goes no further.
- A screenshot taken while the card is on screen is cancelled and taken again from `Frame.end` one
  frame later, with the card gone (`Shots.hold`), so the card is never in a picture.
- The card is drawn once a frame by whoever gets there: `Toasts.draw` (Aller screens, and the HUD
  with no screen), `Frame.hud` when the HUD is hidden, `Hooks.screenExtras` on the game's own screens.
  Its keys (Ctrl+O, C, Delete, E, and Z after a delete) come from `KeyboardHandlerMixin`, which
  cancels the press so the game never sees it; `ShotCard.claiming()` keeps polled keys (module
  binds, the pocket) quiet meanwhile. Clicks come from `MouseHandlerMixin`. Keys follow
  `Launcher.allowed`, so not in chat or a text box; never while `ShotsScreen` is in front.
- What Aller Client knows about a file (place, favourite) is in `aller-screenshots/index.json`, keyed
  by file name; thumbnails are JPEGs in `aller-screenshots/thumbs`, keyed by name, size and time.
  Renaming renames the file.
- Delete moves the file to `aller-screenshots/trash`; for `Shots.UNDO` seconds it can be taken back,
  then it is moved back and sent to the recycle bin from there. Java is headless in Minecraft, so
  the recycle bin and the image clipboard go through `platform/Os`, which runs PowerShell (or
  osascript, gio, wl-copy, xclip). Imgur upload was asked for and then dropped by the user.
- Essential: `EssentialCompat.screenshots` switches its "Essential screenshots" setting off as a
  screenshot is taken while Aller Client's preview is on (remembered in `extras`, and switched back
  when the preview is turned off), and its Pictures button is left off the strip meanwhile.
  Essential still files the picture in its own browser and plays its own sound.

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

### Effect mods

Motion blur, depth of field and colour grading (`module/mods/EffectMods`) work on the picture of the
world after it and the hand are drawn and before the HUD. Bloom, rim lighting and sharpen are built
the same way but not registered in `Modules.registerAll` (the user did not want them); their code
and shader branches are still there. `feature/Effects`
decides the passes; `platform/Post` is the version-specific plumbing (a pipeline per fragment shader in
`shaders/post`, drawn through the device API the way vanilla's `PostPass` does, not through
`PostChain`: that has no per-frame uniforms and no half-size targets).

- Two hooks in `GameRendererMixin`: `worldDepth` just before `renderLevel` clears the depth for the
  hand (the world's depth is copied then), and `worldDrawn` after `renderLevel` returns.
- Bloom (a down and up chain, added in place) and depth of field (two half-size passes) prepare
  pictures; everything else, and their mixing in, is the single `composite` pass, switched by
  uniforms. So a further effect should be a branch there unless it needs a blur of its own.
- Motion blur is its own pass after the composite (`motion.fsh`): each pixel is reprojected to where
  it was a frame ago from its depth and the camera's move and turn (`Effects.camera`), and the
  picture is averaged along that path, scaled from the frame time to the shutter time. It blurs
  camera motion only (there is no velocity for moving entities) and keeps the hand sharp. It is not
  frame blending: mixing old frames in was tried first and looked like being drunk.
- Every shader reads one block, `Fx { vec4 U[12]; }`, filled from a float array. Each draw gets its
  own ring buffer (`Pass.slot`), since a recording backend would otherwise see only the last values.
- 1.21.8 draws the quad buffer with `post/quad.vsh`; 26.2 uses vanilla's `core/screenquad` triangle.
  Depth is reversed on 26.2 and may be zero-to-one: `Post.depthParams` hides that, never read raw
  depth as a distance. The hand has its own depth (near 0.05, far 100) in the main target.
- Targets are RGBA8, so bloom levels are stored at 1/n and the composite dithers.
- Depth effects (motion blur, rim, depth of field, the sky tint) stand down while an Iris shader pack is in use
  (`Post.shaderPack`, by reflection); the colour ones still run on the pack's picture.
- With every effect off nothing runs and the targets are freed. A shader that fails to compile, or
  any exception, switches the effects off for the session with a toast rather than crashing.
- "Fog and sky" changes fog distances and colour in `FogRendererMixin` (the private `updateBuffer`
  both versions share) and only in clear air (`Game.clearView`): never under water or lava, in
  powder snow, or with blindness or darkness, where seeing further would be an advantage.

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
- The product is "Aller Client" in anything the player reads (use `AllerClient.NAME`), never bare
  "Aller": that is the author's username. The mod id, package and file names stay `aller`.
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
Menu restyling is on by default and no longer marked experimental (the user's call). It is checked with `-Paller.skin` on both versions (26.2 with Sodium 0.9, Iris and Essential,
1.21.8 with Sodium 0.7 and Iris), in the pixel look and the smooth one: stills, plus the hand-over
caught part way. The harness has no pointer, so untried by hand: hover and press on buttons, the
keyboard focus ring, dragging a slider, typing in fields, tooltips, Realms, a non-Latin language,
Sodium's sliders and search, and how the cross-fade feels at speed.

The Essential buttons were pressed through the harness on 26.2 with an offline dev account: settings,
pictures and the account switcher opened; social and wardrobe got as far as Essential's own
"authentication failed" box, and hosting its "can't invite" notice. Untried: a signed-in account,
its terms prompt, the pause menu on a server (invite), 1.21.8, and Essential's unread badge, which
is not shown.

The Lucide icons are checked in captures in both looks. Pixel icons use whole cells only when that
lands within 15% of the size asked for, so at some GUI scales their cells are uneven.

The launcher is only checked through the harness, which calls `keyDown`/`charTyped` directly: the
real Ctrl+K press, rebinding the chord, mouse use, pins, custom actions being sent, joining a server
or opening a world from it, and its look over Sodium, Iris and Mod Menu screens are untried.

The wardrobe is checked through the harness on an offline dev account (model, tiles, laby.net
history for another account; as a full page over the main menu and over a world, and the rejoin
question drawn by `WardrobeScreen.dev("ask")`, on 26.2 only). Untried: wearing a skin with a real
sign-in, the file picker, dropping a file, copying by player name, the changed skin showing in a
world, and the rejoin itself on a real server (it has never run).

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

The effect mods were checked through the harness on both versions with Sodium and Iris loaded and no
shader pack (`effects-off`, `effects` and one `effect-<id>` capture each, in daylight), but that was
before motion blur was rewritten as camera reprojection: the rewrite compiles on both versions and
has not been run at all. Untried besides: the focus easing in motion, an Iris pack
actually enabled, the Vulkan backend, Fabulous graphics, a resize or fullscreen switch while they
are on, and fog in the Nether and End. One bench run on 26.2 put all of them together within the
run's own noise of the idle figure.

The pocket dimension is checked through the harness on both versions with the test world standing in
for the server (26.2 with Sodium and Iris loaded). Untried: a real multiplayer server and its
anticheat, the eject paths (damage, respawn, server-opened screens, a dropped connection), a proxy
server switch, survival mode and dying inside, the Nether from inside, a non-zero chat delay (the
server's signed chat is then acknowledged on the wrong connection), a server with custom tags, and
another mod displacing one of its mixins.

The pack store is checked through `-Paller.store` on 26.2 only (browse, rows, search, a page with
its pictures, gallery, full-size picture, versions and changelog, a download, the installed list,
the pin and "New" mark on the pack list, restyled or not; and for shader packs with Iris 1.11.4 the
button, the search and a download showing selected in Iris's list). Untried for shaders: Iris 1.9.6
on 1.21.8, applying a downloaded pack, and Iris's list with many packs. Untried: 1.21.8 beyond compiling (its
mixins have not been loaded), every click and hover (the script calls `StoreScreen.dev`), the
filters, sorting, endless scrolling, links, updates and "Update all", switching versions, replacing
a pack that is switched on, deleting, a failed or slow connection, a non-Latin description, tables,
and the store opened from a world. The GUI-scale lock on Aller's menus compiles on both versions
and has not been run at any scale.

Onboarding was checked through `-Paller.onboarding` on both versions (stills of the intro and every
step, the wave part way, both looks), but that was the ten second intro. The 3D intro (tunnel,
floor, orb, the mash gate), `Canvas.quad4`, the bigger colour wave and the bigger ending compile on both
versions and have not been run, seen or heard at all. Untried by hand: the first launch itself (the start held behind the
loading screen, the hand-over to the main menu), how the intro and its sounds feel at speed, every
click, the custom colour bars, the real key presses and what comes back from the palette and the
launcher, rebinding, hold Escape, a narrow or small window, reduce motion, and a replay from inside
a world.

Screenshots are checked through `-Paller.gallery` on both versions (26.2 with Essential, Sodium and
Iris): the card, copy to the clipboard, delete and undo, the held second screenshot coming out
without the card in it, the grid, places, favourites, the full-size view, zoom, the rename box, the
recycle bin at the end, Essential's preview switched off. Untried by hand: the real F2 and Ctrl
keys, clicking the card, a vanilla toast in the same corner (it covers the card), dragging and the
wheel in the viewer, double click, search, a rename actually committed, hundreds of screenshots,
non-Windows systems, and Essential's own key with a signed-in account.

Not built yet: README, more novel features (quick wheel, notes).

### Git
Commit to git when you are done something, if you have to bundle in other unrelated work it is fine. It's mostly just as a backup, and later to be pushed to github so people can see the source code.