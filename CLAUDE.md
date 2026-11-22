# Bit by Bit — CLAUDE.md

A single-player mobile learning game. The player builds a computer from the bottom up:
transistor → logic gates → adders → calculator → memory → mini computer.
Each level the player finishes becomes a **chip** they use as one block in later levels.

## Where things are

| Path | What it is |
|---|---|
| `docs/SPEC.md` | Product spec: core loop, screens, interaction rules, simulation rules, test rules. **Read first.** |
| `docs/levels.json` | All 24 levels as data: chapter, title, inputs/outputs, sockets, tray chips, starting state, test, unlocked chip. |
| `docs/design-tokens.json` | Colors, type, radii, spacing, touch sizes. |
| `design/*.dc.html` | Approved screen mockups (390×844). Each file is HTML markup plus a `<script type="text/x-dc">` block whose `renderVals()` holds **working reference logic** (simulation, testing, state). |
| `design/canvas.json` | Canvas index: screen order, titles and the design notes (`notes`), which explain each level's intent. |

The mockups were made on a design canvas. They load `./support.js`, which is not in this
folder, so they will **not** run when opened directly. Use them as the visual and logic reference.
Live canvas (owner only): https://claude.ai/artifact/U8FtXyf25n2eJCpLmKPARn

## Screen → mockup map

| Screen | File | Level |
|---|---|---|
| Start: continue or new game (first screen) | `design/Start.dc.html` | — |
| New game: choose difficulty | `design/NewGame.dc.html` | — |
| Journey map (home) | `design/Main.dc.html` | — |
| Lesson intro | `design/Lesson.dc.html` | 1 |
| Transistor workbench (AND), shared layout | `design/Workbench.dc.html` | 1 |
| Lesson (OR) | `design/Lesson2.dc.html` | 2 |
| Transistor workbench (OR), same file with `level()` = 2 | `design/Workbench2.dc.html` | 2 |
| Hard mode: build it yourself (AND / OR / half adder), same file with `level()` = 1 / 2 / 6 | `design/WorkbenchHard.dc.html`, `design/WorkbenchHard2.dc.html`, `design/WorkbenchHard6.dc.html` | 1, 2, 6 |
| Lesson (half adder) | `design/Lesson6.dc.html` | 6 |
| Level complete | `design/Complete.dc.html` | any |
| Chip toolbox | `design/Toolbox.dc.html` | — |
| Half adder workbench | `design/HalfAdder.dc.html` | 6 |
| Full adder workbench | `design/FullAdder.dc.html` | 7 |
| 4-bit adder workbench | `design/Adder4.dc.html` | 8 |
| Subtractor workbench | `design/Subtractor.dc.html` | 9 |
| Calculator milestone | `design/Calculator.dc.html` | 14 |
| SR latch workbench | `design/SRLatch.dc.html` | 15 |

Not designed yet: levels 3–5, 10–13 and 16–24. Build them from `docs/levels.json` using the
patterns of the nearest designed workbench (see SPEC §4).

## Stack

**Kotlin, native Android, Jetpack Compose (Material 3, dark theme only).** Gradle Kotlin DSL with a version catalog.

| Concern | Use |
|---|---|
| UI | Jetpack Compose. Draw the board (wires, LEDs, 7-segment display, timeline) on a `Canvas`; sockets, links and toggles are normal clickable composables placed on top of it. |
| Navigation | Navigation Compose: `journey`, `lesson/{n}`, `workbench/{n}`, `complete/{n}`, `toolbox`, `sandbox`. |
| State | One `ViewModel` per screen exposing `StateFlow<UiState>`; the UI is a pure function of that state. |
| Level data | Copy `docs/levels.json` into `app/src/main/assets/` and parse it with kotlinx.serialization. |
| Progress save | DataStore (Preferences or Proto): finished levels, unlocked chips, the current level. Local only: no accounts, no backend. |
| Fonts | Space Grotesk, Inter and JetBrains Mono bundled in `res/font` (all three are OFL-licensed). |
| Timing | Pulse buttons use `delay(450)` in `viewModelScope` (no `Handler`). |

Modules:

- `:sim` is a **pure Kotlin/JVM module** with no Android dependencies. It holds gate functions, chip evaluation,
  null-propagation, feedback settling (max 8 passes, then mark it oscillating), the 8-bit ALU, and the test runners.
  Port the logic from each mockup's `renderVals()` and its helper methods (`gate`, `chip`, `sim`, `settle`, `runAll`, `runTest`, `alu`).
  Represent a signal as `Boolean?`, where `null` = unpowered.
- `:app` holds the Compose UI, ViewModels and persistence, and depends on `:sim`.

## Commands

```
./gradlew :sim:test              # simulation unit tests (fast, no Android)
./gradlew :app:assembleDebug     # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug      # install on a connected device/emulator
```

Toolchain as scaffolded: Gradle 9.9.1 (wrapper), AGP 9.5.1 with built-in Kotlin, Kotlin 2.4.21, Compose BOM 2026.09.00,
compileSdk/targetSdk 37, minSdk 26, Java 17 bytecode (it builds with the local JDK 25). `local.properties` points at the
Android SDK and is gitignored.

## Current state

- `:sim` is done for the designed levels: `TransistorPairBoard` (Levels 1–2), `HalfAdderBoard`, `FullAdderBoard`, `Adder4Board`,
  `SubtractorBoard`, `SrLatchBoard`, `Alu8` + `CalculatorState` + `SevenSegment`, and `Circuit` for the Sandbox.
  Tests are in `sim/src/test/` and all pass.
- `:app` has: the theme (`ui/theme/Theme.kt`, tokens as `Bb.*`), navigation (`ui/AppNav.kt`), level loading (`data/LevelRepository.kt`),
  progress saving (`data/ProgressStore.kt`), Journey, Toolbox, Complete, Lesson and Sandbox.
- **Levels 1 and 2 share one screen**, `TransistorWorkbench` (`ui/screens/TransistorWorkbench.kt`). A level is just a
  `TransistorLevel` config in `TRANSISTOR_LEVELS`: its title, goal, target truth table, and `fixedWiring`.
  - Level 1 fixes the wiring to Stacked (AND). The wiring row shows Stacked "fixed in this level" and Side by side dashed with
    "you'll choose in Level 2".
  - Level 2 lets the player choose. Stacked rebuilds AND, so rows 01 and 10 show red "got 0" with a hint. Changing the wiring clears the results.
  - Both use the same layout: goal line → 220 dp board → wiring row → A/B/Output → "want / got" truth table ("N/4 correct") →
    one button. The middle section scrolls on short phones.
  **This is the pattern for every new workbench:** one shared screen plus a per-level config, not a new screen per level.
  Shared drawing pieces live in `WorkbenchScreen.kt` (`drawTransistor`, `wire` (a null wire is drawn dashed), `InputToggle`, `OutputBox`).
- **Start flow** (`ui/screens/StartScreens.kt`). The app opens on **Start** (`Routes.START`, the start destination; Back from
  Journey returns here):
  - "Continue" appears only when a save exists. Its card shows the current level, progress, the difficulty chip and the Hard ★ count.
  - "New game" leads to **Choose your difficulty** (Normal or Hard). Starting over an existing save shows an
    "Erase your progress?" dialog. `ProgressStore.newGame(difficulty)` wipes progress and stores the difficulty.
  - One save slot (`SaveGame`: difficulty, completed, completedHard). Saves from before this existed load as Normal games.
  - The difficulty is the game's default. A lesson's main button starts in that mode ("Start · Hard"), with the other one below it
    ("Play this level in Normal"). Levels without a lesson open in the game's mode when a Hard version exists, otherwise Normal.
    Journey shows a NORMAL/HARD chip next to the level count.
- **Two modes per level: Normal and Hard.** The lesson screen offers "Start · Normal" and "Hard mode · build it yourself"
  (when the level is in `HARD_LEVELS`). Either mode completes the level and unlocks the chip; Hard is also saved in
  `ProgressStore.completedHard` and shows a "HARD ★" badge on Journey and "HARD MODE ★" on the completion screen.
  - Hard mode (`ui/screens/HardWorkbench.kt`) is the shared circuit editor with a goal, drawn as a **live circuit board**
    (design: `design/HardBoardConcept.dc.html`). Layout, top to bottom: goal → tool bar (**Place / Wire / Probe / Erase**) →
    5×4 board → action line → palette → **logic analyzer** (waveforms A, B, then "want X" and X per output; ✓/✗ per step;
    "Run test" button) → one main button. The header shows the live outputs ("OUT 1", or "C 1 · S 0").
  - Fixed parts that can't be removed: switches A (A1) and B (A4), the output LEDs, and for transistor levels the power input
    **IN (+5V) at C1**. The palette holds only the level's parts, with a count ("Transistor, 2 left").
  - Levels with Hard mode: **1, 2** (Transistor ×2, both required, IN at C1, LED "OUT" at E3) and **6** (one each of
    AND/OR/NAND/XOR, nothing required, so the player must work out which chips to use; no IN; LEDs C at E2 and S at E3).
  - Rules are in `:sim` `HardMode` (tested in `HardModeTest.kt`): every required part must be placed; switches may only
    drive a transistor's base or a gate input; every LED must be driven by a placed part, never straight from a switch or IN;
    IN must be wired (there is no hidden power). Any change to parts or wires clears old results; flipping a switch doesn't.
  - To add Hard mode to a level, add a `HardLevel` to `HARD_LEVELS`: title, goal, `palette` (id → count), `required`
    (kind → count, optional), `outputs` (label + cell, in analyzer order), `hasPower`, `target` (inputs → list of LED values).
- **Shared circuit editor.** `ui/circuit/CircuitEditor.kt` holds the state and pure logic (`EditorState`, `Editor.*`, `BoardTool`),
  unit-tested in `app/src/test/.../EditorTest.kt`. `ui/circuit/CircuitBoard.kt` holds the UI (`CircuitBoard`, `BoardToolBar`,
  `EditorActionBar`), and `ui/circuit/LogicAnalyzer.kt` the analyzer. The Sandbox and Hard mode both use them, so they can't drift apart.
  - **Tools:** in PLACE, tap an empty cell to drop the picked part, tap a part to select it, tap a switch to flip it. In WIRE,
    tap the part a wire starts from (switches don't flip), then the target, then pick the input if there are several.
    PROBE reads every pin ("Q1 · B 1 · C 1 · E 1") and still flips switches. ERASE removes a part and its wires (fixed parts stay).
    Picking a palette part switches to PLACE. The Sandbox has no tool bar and stays in PLACE.
  - **Drag and drop** (`ui/circuit/DragAndDrop.kt`) works in Hard mode and the Sandbox, whatever the tool. It uses
    long-press, then drag, so a normal swipe still scrolls.
    - Drag a palette tile onto an empty cell to place it (`Editor.placeAt`, which respects the level's part limits).
    - Drag a placed part to another cell to move it; its wires follow (`Editor.move`).
    - Drop a part off the board to remove it (`Editor.dropOff`). Fixed parts can't be picked up.
    - A `DragGhost` follows the finger ("Drop here" / "Taken" / "Drop to remove"), and the cell under it turns green or red.
    - Each screen owns one `DragState` (`rememberDragState()`), passes it to `CircuitBoard` and `ComponentPalette`, and draws
      `DragGhost` last in a root `Box`. Positions are in root coordinates; the board publishes its cell grid via `dropTarget`.
    - Tested in `EditorTest` (placeAt, move keeps wires and power, taken cells, fixed parts, drop-off). On the emulator,
      `adb shell input draganddrop x1 y1 x2 y2 2500` performs a long-press drag.
  - **Board look:** dark PCB with pad dots, gold solder pads, right-angle traces (`trace()`). Lit traces glow and carry
    moving dashes (green, amber from IN). Transistors are circle NPN symbols whose pins follow the symbol: B left, C top, E bottom.
    Switches are slide toggles, IN is an amber terminal block, and LEDs glow.
- **Shared truth table** (`ui/screens/TruthTable.kt`, `WantGotTable`) is used by Normal and Hard workbenches.
- **Lessons** come from `LESSONS` in `ui/screens/OtherScreens.kt` (a headline, cards, the goal). Journey opens a level's lesson
  when it has one, otherwise the workbench. Level 2's lesson poses the problem without giving the answer away.
- **Component palette** (`ui/palette/ComponentPalette.kt`), reusable on any board. `buildPalette(data, completed)` lists
  Switch and LED (always available), the Transistor, and every chip in levels.json. A chip is available once the level
  that unlocks it is complete; locked chips are dashed with "Level N" and can't be selected. It has category filters
  and `ChipGlyph` (a chip tile with pins). It is used by the **Sandbox** (`ui/screens/SandboxScreen.kt`): pick a component,
  tap cells on a 6×5 grid to place it (it stays picked so you can place several), tap a placed part to select or remove it,
  or clear the board.
- **Sandbox wiring + live simulation.** `:sim` `Circuit` (`Circuit.kt`, tests in `CircuitTest.kt`) runs a netlist of `PartKind`s
  (SWITCH, POWER, LED, TRANSISTOR, AND, OR, NOT, NAND, NOR, XOR, HA, FA). Its rules:
  - an unconnected input reads 0, and there is no hidden power: a transistor conducts only if its collector C is wired to
    something that's on, normally the POWER part (IN, +5V, always 1; also in the Sandbox palette under Inputs & outputs);
  - several wires into one input are joined as OR;
  - feedback loops are allowed and settle within 32 passes, otherwise the result is flagged `oscillating`;
  - passing the previous outputs back in lets latches remember.
  Wiring in the UI, without dragging (pins are too small on a phone): tap a part → "Start wire" (or "Wire from S" / "Wire from C" for
  chips with several outputs) → tap the target part → choose "Input A" / "Input B" if it has more than one input. Tapping a switch flips it.
  Wires are drawn as curves, green when carrying 1. "Unwire" removes every wire on the selected part. Chips beyond FA (ADD4, ALU4, RAM16…) can be
  placed but not wired yet. The board is still not saved between app restarts.
- **Level 6 Normal** (`ui/screens/HalfAdderWorkbench.kt`, sim `HalfAdderBoard`), ported from `design/HalfAdder.dc.html`: Sum and
  Carry sockets, chip tray (XOR/AND/OR/NAND; tap a chip, then a socket; tap a filled socket to empty it), A/B inputs, a "C S"
  result box and the want/got table ("A B → C S"). Starts with XOR in Sum and Carry empty. Level 6 also has a lesson.
- **Debug builds only:** Journey lists every chapter's levels and locked levels can be opened ("Open (debug)"), so any level
  can be tested without playing everything before it. Gated by `BuildConfig.DEBUG`; release builds aren't affected.
- Every other level shows "Not built yet" with a pointer to its mockup.
- Fonts are the platform defaults until the three font files are added (`TODO(fonts)` in Theme.kt).
- No launcher icon yet.

## How to build (remaining work)

1. (Done) Scaffold the two modules. Theme colors and type come from `docs/design-tokens.json`.
2. **For every new level, extend `:sim` and its JUnit tests first.** Every level's `solution` in `levels.json` must pass its own test,
   every `alsoAccepted` answer must pass, and the `start` state must fail.
3. **Make levels data-driven.** One generic `WorkbenchScreen` reads a level definition. Write a level-specific
   composable only where the board really differs (the calculator, the timeline view).
4. Build the screens in canvas order: Journey → Lesson → Workbench (L1) → Complete → Toolbox → L6–L9 → Calculator → SR latch.
5. Compose UI tests: one per designed level, which places the reference solution, runs the test and checks that the success button appears.

## Design rules (follow them in every screen)

- Dark "breadboard" look. **Green `#4ADE80` = HIGH / 1 / correct.** **Amber `#F5B544` = active, selected, next action.**
  Red `#FF8A7A` = wrong. Gray `#3A4352` = LOW / 0 wire. Empty sockets are dashed outlines.
- Fonts: Space Grotesk (headings), Inter (body), JetBrains Mono (bits, codes, chip symbols).
- Chips are drawn with IEC symbols: `&` AND, `≥1` OR, `=1` XOR, `1○` NOT; a `○` suffix means an inverted output.
- Touch targets at least 48 dp (Android minimum; the mockups use 44 px). Icon-only controls get a `contentDescription`;
  toggles use `Modifier.toggleable` / `selectable` so TalkBack reads their on/off state. The mockups' `aria-label`s are the wording to use.
- Mockup px map 1:1 to dp (the mockups are 390 px wide = a 390 dp phone).
- Never show a wire's state with color alone: an unconnected or unpowered wire is also **dashed**.
- No emoji. Icons are simple line icons. The only decorative symbol is ★ for milestones.

## Don't

- Don't add accounts, multiplayer, ads or in-app purchases.
- Don't change the level order without asking. NOT comes before XOR on purpose, because XOR is built from NOT/NAND.
- Don't invent extra lesson content or facts. Where copy is missing, leave a `TODO(copy)` and ask the user.
