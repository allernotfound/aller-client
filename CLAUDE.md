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
- Do not fetch stonecutter.kikugie.dev (it blocks automated clients).

### Self-test harness

`./gradlew :26.2:runClient -Paller.shots=<dir>` (or `:1.21.8:`) launches the game, captures the
splash, walks the main menu, palette pages, a creative test world (`aller-dev`), the HUD editor and
the pause menu, writes a PNG of each to `<dir>` and exits. Use it to verify UI or mixin changes,
then read the PNGs and grep the log for `ERROR|Mixin apply|Exception`. The script is
`dev/DevHarness.java`; add a step when you add a screen. It opens a real game window for about a
minute.

- `-Paller.compat` also loads Sodium and Iris (versions in `versions/<mc>/gradle.properties`).
- `-Paller.world=<name>` uses a different test world. Needed when the user has the game open in
  `aller-dev` themselves: the world is then locked and the harness gives up after the menus.
- `-Paller.noWorld` skips the world entirely.
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
feature/           Session, Waypoints, Replay, AutoProfiles, Combat, Clicks, View
screen/            MainMenuScreen, PaletteScreen (+ palette/* pages), HudEditorScreen,
                   PauseMenuScreen, Screens (which vanilla screens get replaced),
                   Splash (startup overlay), LoadingSkin (paints over vanilla loading screens)
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
auto profiles.

The harness only proves things load and draw. Not yet exercised by a person: anything that needs
held keys or a server (zoom, freelook, toggle sprint, replay saving, the connecting screen, ping,
tab list with many players, auto-profile rules), and Iris with a shader pack actually enabled.

Not built yet: README, more novel features (quick wheel, notes).
