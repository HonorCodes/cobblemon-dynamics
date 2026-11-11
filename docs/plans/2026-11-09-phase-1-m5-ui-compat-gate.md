# M5: UI bars, compatibility, polish and the Phase 1 gate

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m5-ui-compat-gate`. Builds on M1 to M4. The milestone ends at the Phase 1 gate: work stops for the
approver's play-test.

Before Task 1, check the client references in the Cobblemon jar: `PCGUI.configuration`,
`PasturePCGUIConfiguration`, the panel geometry in `PCGUI` (lines 64-69, 137-138, 181-183 of the research
snapshot), `PastureWidget.renderWidget` and its row list, and `RecallButton`. Check the Cobblebase 2.0.1
source (`notlown/cobblebase`, commit `00a05e2`) for the pasture-tick entry point that its mixin calls, and
the Cobbleworkers 2.0.7 jar for the `cobbleworkers.resting` key.

---

### Task 1: Work Mode button in the pasture panel

**Rows:** CD-UI-01, CD-UI-02, CD-UI-03.

**Files:** `client/WorkModeButton.kt`, `client/PastureScreenHooks.kt` (`ScreenEvent.Init.Post`, `Render.Pre`,
`MouseButtonPressed.Pre` and `Closing`), `client/PanelLayout.kt` (pure geometry), `client/PanelMixinCheck.kt`,
`client/ClientNet.kt`, `client/DynamicsClient.kt` (registers every client hook, on the client dist only),
`mixin/cobblemon/client/PasturePokemonScrollListMixin.java` (client list), lang keys
`cobblemon_dynamics.ui.work_mode.on` = `Work Mode: On`, `.off` = `Work Mode: Off`, `.locked` = `Only the pasture's
owner can change Work Mode`, `src/test/kotlin/.../client/PanelLayoutTest.kt`.

On `Init.Post` for a `PCGUI` whose `configuration is PasturePCGUIConfiguration`, add the button at panel
offset `(6, 153 - buttonHeight - 2)`, directly above Recall (R35), and send `pasture_panel_open`. The label
and the enabled state come from `ClientState.panel` when `pasture_panel_state` arrives (CD-UI-02); a viewer
who is neither the placer nor an operator sees it disabled with the tooltip (CD-UI-03). A click sends
`WorkModeTogglePayload(!current)`. `Closing` sends `pasture_panel_close`.

Design (checked against the Cobblemon 1.8.4 jar):

- The button is 70 × 12, as wide as Recall, at panel offset (6, 139). Its label scales down to fit, because
  "Work Mode: Off" is 73 pixels wide in the vanilla font.
- Cobblemon's list rectangle is (6, 27, 70 × 120) and reaches y = 147, under the button. A list row's move
  button there would take the same click and unpasture its Pokémon. `PasturePokemonScrollListMixin` changes the
  height that `PasturePokemonScrollList.correctSize()` passes to `setRectangle` to `PanelLayout.LIST_HEIGHT` = 110,
  so the list ends 2 pixels above the button. If that mixin does not apply, the client logs one warning.
- The button takes the clicks on its rectangle in `ScreenEvent.MouseButtonPressed.Pre` and cancels the event,
  because `Screen` gives a click to the first child that takes it and the panel's widget comes first. A click on
  the disabled button goes nowhere.
- The button stays hidden until the first `pasture_panel_state` arrives, so it never shows a guessed label.
  `Render.Pre` copies `ClientState.panel` into it on each frame.
- A window resize runs `init` again on the same screen. The client sends `pasture_panel_open` only for a new screen
  instance, and clears `ClientState.panel` on open and on close.
- Cobblebase's own button sits outside the 349 × 205 PC base, at a corner that its config picks, so the two never
  overlap.

CD-UI-01, CD-UI-02 and CD-UI-03 are Demo rows. Demo: verified in the play-test (manual checklist, M5 Task 8).

- [ ] **Steps:** `PanelLayoutTest` (for 1920 × 1080 and 854 × 480 scaled sizes, the button rectangle sits
  inside the 82 × 169 panel, ends 2 pixels above Recall at (6, 153), and overlaps no other control
  rectangle) → write the code → `./gradlew build` → record the Demo steps in the manual checklist (Task 8) →
  commit `"Add the Work Mode button above Recall in the pasture panel"`.

---

### Task 2: Panel stamina bars and state icons

**Rows:** CD-UI-04, CD-UI-05, CD-UI-07, CD-UI-08, CD-UI-10, CD-UI-11, CD-UI-13, CD-ART-11.

**Files:** `mixin/cobblemon/client/PastureWidgetMixin.java` (TAIL of `renderWidget`), `client/PanelBars.kt`,
`client/StaminaBar.kt`, `client/StateIcons.kt`, `net/PanelViewers.kt` (the 20-tick send), placeholder sprites
`textures/gui/state/{idle,traveling,working,standby,tired,sleeping,healing}.png` from `scripts/placeholder_art.py`
(each icon a different shape: dot, arrow, gear, pause bars, droop, Z, plus), `src/test/kotlin/.../client/StateIconsTest.kt`,
`StaminaBarTest.kt`, `PanelMixinCheckTest.kt`.

`StateIcons.iconFor(status)` maps the eight states to seven icons: ToBed and RestingInPlace share Tired
(CD-UI-07). The server sends `pasture_panel_state` every 20 ticks while the panel is open
(`PanelCadence`, CD-UI-05). Hovering a bar shows `NN% Work Stamina` and the state name (CD-UI-10, CD-UI-11).
If `MixinState.applied(PASTURE_WIDGET)` is false, the bars are skipped with one warning, and the button and
head bars still work (CD-UI-13).

Design (checked against the Cobblemon 1.8.4 jar):

- Each row shows its parts over Cobblemon's 62 × 29 slot, which starts at (rowLeft − 4, rowTop + 2): the state
  icon, 8 × 8, in the dark strip above the slot's move button at slot offset (2, 2), and the bar, 50 × 4, along the
  top edge of the slot's card at (11, −1). Neither covers the move button or the Defend button. `PanelLayout.rowTop`
  repeats `AbstractSelectionList.getRowTop`, which is protected, and the parts are clipped to the list's window
  (`y + 4` until `y - 1 + height`), as Cobblemon clips its rows.
- The bar is flat colour, so the planned `stamina_bar.png` is not needed: a 1-pixel frame, then the fill, green from
  50%, yellow from 25% and red below (`StaminaBar`). The icons are 16 × 16 placeholders drawn at 8 × 8.
- Hovering the icon or the bar shows two lines, `cobblemon_dynamics.ui.stamina` = `%s%% Work Stamina` and the state
  name. `Screen.setTooltipForNextRenderPass` draws it above every widget. The parts draw at z + 200, above the row
  models. `hud.panelBars = false` hides them.
- `PanelMixinCheck` warns once per game when a panel opens and `PastureWidgetMixin` did not apply. Only the bars
  depend on that mixin.
- `PanelViewers.tick` runs from `ServerTickEvent.Post`. It sends each viewer its pasture's state 20, 40, ... ticks
  after the panel opened, and drops a viewer whose Cobblemon pasture link now points elsewhere.

CD-UI-04, CD-UI-10 and the Demo parts of CD-UI-07 and CD-UI-08 are Demo rows. Demo: verified in the play-test
(manual checklist, M5 Task 8).

- [ ] **Steps:** `StateIconsTest` (eight states, seven distinct textures, ToBed and RestingInPlace share one;
  the seven PNGs differ pixel by pixel in their alpha mask, so shape differs, not only color — CD-UI-08) →
  `MixinGateTest`-style unit for UI-13 (flag false: no exception, one warning) → write the code →
  `./gradlew build` → commit `"Draw Work Stamina bars and state icons in the pasture panel"`.

---

### Task 3: Head bars and bound-target outlines

**Rows:** CD-UI-06, CD-UI-09, CD-UI-11.

**Files:** `client/HeadBars.kt` (`RenderLivingEvent.Post` for `PokemonEntity`, reads the synced
`stamina_view`), `client/HeadBarRange.kt`, `client/TargetOutlines.kt` (`RenderLevelStageEvent` after translucent
blocks, `LevelRenderer.renderLineBox` around each target in `ClientState.leashOverview`), `client/HeldLeash.kt`
(sends `leash_overview_request` when the main hand changes to the leash), `client/OverviewRequests.kt`,
`src/test/kotlin/.../client/HeadBarRangeTest.kt`, `OverviewRequestsTest.kt`.

Head bars draw only while the main hand holds the Work Leash, for workers within `hud.headBarRange` blocks
(16.0 draws, 16.1 does not) (CD-UI-06). Outlines show only the targets of the viewer's own bound workers,
because the server's overview reply holds only those (CD-UI-09).

Design (checked against the NeoForge 21.1.265 and Cobblemon 1.8.4 jars):

- Cobblemon's `PokemonRenderer.render` calls `super.render`, so `RenderLivingEvent.Post` fires for Pokémon. The head
  bar draws as vanilla draws a name tag: turned to the camera, scale 0.025, y down, 0.75 blocks above the bounding
  box. The icon, 8 × 8, sits left of the bar, 30 × 4 (`HeadBarRange.ICON`, `HeadBarRange.BAR`). The bar's frame,
  fill and empty part are quads that do not overlap (`StaminaBar.parts`), so the coplanar quads never fight over
  depth. They use `RenderType.textBackground()`, the icon `RenderType.text(icon)`, both at full brightness.
- `HeadBarRange.inRange` measures from the player's position to the worker's position in three dimensions.
- `HeldLeash` asks for the overview on the tick that the leash comes into the main hand, as planned, and again every
  20 ticks while it stays there (`OverviewRequests`), so a binding made with the leash in hand gets its outline.
- NeoForge posts `AFTER_TRANSLUCENT_BLOCKS` with no pose stack (the event makes a fresh one), while the view rotation
  is on the model-view stack. `TargetOutlines` therefore draws camera-relative lines, as vanilla draws the block
  outline: `LevelRenderer.renderLineBox` around the block's box, then `endBatch(RenderType.lines())`.

CD-UI-06 and CD-UI-09 are Demo rows. Demo: verified in the play-test (manual checklist, M5 Task 8). The
GameTest `cdUi09OverviewHoldsOnlyOwnTargets` (in `LeashTests`) checks the server side of CD-UI-09.

- [ ] **Steps:** `HeadBarRangeTest` (16.0 in, 16.1 out); a GameTest `cdUi09OverviewHoldsOnlyOwnTargets` (two
  players with one binding each: each overview reply holds one entry) → write the code → `./gradlew build
  runGameTestServer` → commit `"Show head bars and bound-target outlines while the Work Leash is held"`.

---

### Task 4: Cobbleworkers and Cobblebase compatibility

**Rows:** CD-CMP-01, CD-CMP-02, CD-CMP-03, CD-CMP-04, CD-CMP-05, CD-CMP-06, CD-CMP-07, CD-PLAT-08 (still
optional).

**Files:** `compat/CobbleworkersRest.kt` (pure flag logic over a `CompoundTag`), `compat/CobbleworkersCompat.kt`
(loaded only when `cobbleworkers` is present), `compat/WorkerEvents.kt`, `compat/CobblebaseCompat.kt`,
`compat/CobblebaseHooks.kt` and `mixin/compat/cobblebase/{AmbientBehavior,NavigationHelper,BaseManager,PassiveXp}Mixin.java`
(`@Pseudo`, in place of the planned `CobblebasePastureTickMixin`: see the design below),
`src/test/kotlin/.../compat/CobbleworkersRestTest.kt`, `CobblebaseHooksTest.kt`, test pack scenarios
`compat_cobbleworkers` and `compat_cobblebase` (command `compat`).

`CobbleworkersRest.mark(data)` sets `cobbleworkers.resting = true` and our ownership key
`cobblemon_dynamics.set_cw_rest = true` only when the flag was not already set; `unmark(data)` clears the
flag only when our key is present (CD-CMP-02). The engine calls `mark` on each Work Mode worker at its
20-tick poll (CD-CMP-01) and `unmark` when Work Mode turns off or the Pokémon leaves. The Cobblebase mixin
targets the method that Cobblebase's own `PokemonPastureBlockEntityMixin` calls at `TICKER$lambda$0` TAIL;
when it fails to apply, `MixinState` records nothing and the compat logs one warning (CD-CMP-05). Class
loading: the compat classes sit behind `ModList.isLoaded` checks in their own packages (CD-CMP-06,
CD-CMP-07).

Design (checked against the Cobbleworkers 2.0.7 and Cobblebase 2.0.1 jars, decompiled with Vineflower):

- Cobbleworkers' `RestManager` reads the boolean `cobbleworkers.resting` at the top level of
  `Pokemon.persistentData` and removes the key when rest goes off. Our ownership mark is the entry `set_cw_rest`
  in the `cobblemon_dynamics` compound, as ARCHITECTURE's data model says. The engine tells `WorkerEvents` at
  each poll of a worker and when a worker leaves (`drop`: it left the pasture, Work Mode turned off, or the
  pasture is gone). `CobbleworkersCompat` listens only when Cobbleworkers is loaded, and it uses no Cobbleworkers
  class. With `compat.cobbleworkers = false`, each poll clears the flags that Dynamics set, so none is stranded.
- The plan's single Cobblebase entry method does not exist. Cobblebase 2.0.1 runs its whole pasture loop inside
  its own TAIL handler `cobblebase$tick`. Mixin cannot target a handler that another mixin merged, and it finds
  every injection point before any mixin injects, so no mixin can wrap the call to that handler either. A
  cancellable TAIL callback ordered before Cobblebase's would skip every later TAIL hook of every mod for Work Mode
  pastures, Cobbleworkers and Cobbreeding included. So four `@Pseudo` mixins in `mixin/compat/cobblebase` hook
  the helper methods that the loop calls, 10 handlers in all:
  - `AmbientBehaviorMixin`: `isSleeping` and `shouldPreventMovement` answer false for a worker, which also turns
    off Cobblebase's sleep lock on `PokemonEntity.tick`; `tickIdle` answers "handled" for a Work Mode pasture, so
    no idle behaviour and no wander start; `tickSpecialAnimations` does nothing for a worker.
  - `NavigationHelperMixin`: `findNearbyWaterCached` finds no water for a Work Mode pasture, so no Water-type is
    moved into water; `wanderNearOrigin` and `checkAndUnstick` do nothing for a Work Mode pasture, and
    `escapeLeaves` nothing for a worker.
  - `BaseManagerMixin`: `tickPokemon`, which runs Cobblebase's jobs and its return-to-pasture teleports, does
    nothing for a Work Mode pasture.
  - `PassiveXpMixin`: `tick` gives no passive experience in a Work Mode pasture.
  `CobblebaseCompat` answers for a pasture through the level's Work Mode index, and for a Pokémon through the
  work engine, on the server thread only. Both answers are false with `compat.cobblebase = false`.
- CD-CMP-05: `MixinState.handlerApplied` records each handler that some target calls. At common setup, with
  Cobblebase loaded, `CobblebaseHooks.check` loads the four classes without initializing them, which makes Mixin
  apply the hooks, and logs one warning that names every hook that did not apply.
- The GameTest runs cannot load Cobbleworkers or Cobblebase, which are not on the development run classpath. The
  test pack's `compat` command checks CD-CMP-06 and CD-CMP-07 on real servers instead. Case `compat_cobblebase`
  installs Cobblebase 2.0.1 with Cloth Config 15.0.140, which Cobblebase needs at run time although its
  `neoforge.mods.toml` does not list it. Case `compat_cobbleworkers` installs Cobbleworkers 2.0.7. Each server
  logs its class loading (`-Xlog:class+load`). A case passes when the server starts and stops, our compat class
  for its mod loads, no compat class of the other mod loads, no error names one, and no hook warning or mixin
  failure appears.

- [ ] **Steps:** `CobbleworkersRestTest` (the flag within one poll; a player-set flag survives `unmark`; a
  Dynamics-set flag is cleared) → write the code → add test pack mod pins for Cobbleworkers 2.0.7 and
  Cobblebase 2.0.1 (Modrinth) → scenarios: a server with Cobblebase and no Cobbleworkers starts with no error
  naming a Cobbleworkers compat class (CD-CMP-06), and the reverse (CD-CMP-07) → `./gradlew build
  runGameTestServer && py scripts/testpack/testpack.py compat` → commit
  `"Switch Cobbleworkers and Cobblebase off in Work Mode pastures"`. CD-CMP-01, CD-CMP-03 and CD-CMP-04 Demo
  parts (a worker that stays at its machine for 1,200 ticks with each mod) go to the manual checklist and
  the approver's play-test. CD-CMP-03, CD-CMP-04, CD-CMP-05 and the Demo part of CD-CMP-01 are Demo rows. Demo:
  verified in the play-test (manual checklist, M5 Task 8). The play-test client needs the pinned Cobbleworkers
  jar, or the Cobblebase and Cloth Config jars, from `run/testpack/cache/`.

---

### Task 5: Lang and logging gates

**Rows:** CD-UI-11, CD-UI-12, CD-PERF-05 (static part).

**Files:** `src/test/kotlin/.../build/LangTest.kt`, `OnceLogger.kt` (root package), `src/test/kotlin/.../OnceLoggerTest.kt`.

`LangTest` scans `src/main/kotlin/**` and `src/main/java/**`: every `Component.translatable("...")` key and
every `cobblemon_dynamics.` key string in code exists in `en_us.json`; no file under `client/` or `leash/`
or `command/` calls `Component.literal`; every `en_us` value that names the stat reads `Work Stamina`, never
a bare `Stamina`; no `LOGGER.info`, `LOGGER.warn` or `LOGGER.error` call sits in a file under `engine/`,
`handler/` or `fx/` except through `OnceLogger`, the helper that logs each cause once.

Design (as built):

- A key string in code that `en_us` lacks must be the prefix of a key family that the code builds as
  `"$PREFIX.<suffix>"`. The test lists the suffixes of each family from the code's own values: the states and
  recovery rules through `StaminaReport`, the claim kinds, and the reason constants of `LeashResets`. It fails
  when the code builds a family that its list does not hold, so a new family needs its suffixes in the test.
- `en_us` is complete for names too: every item model, every block state file and every entity type that
  `DynamicsEntities` registers has its `item.`, `block.` or `entity.` key.
- The `Component.literal` check, with `Component.nullToEmpty`, covers the whole main source set, not only
  `client/`, `leash/` and `command/`, because every component that the mod builds reaches a player.
- `OnceLogger` sits in the root package, outside the scanned packages. Each instance keeps its own set of causes
  and builds a message only for a new cause. In `engine/`, `handler/` and `fx/` the test refuses
  `LOGGER.info|warn|error`, an own `getLogger` and every use of `CobblemonDynamics.LOGGER` other than `debug` and
  `trace`. Four warnings moved to `OnceLogger`: the baseAI species warning in `WorkEngine`, the missing particle
  and missing particle files warnings in `fx/`, and the crank mixin warning in `CreateCompat`.
  `RoleRegistry.disableHandler`, which `guarded` calls from the engine, already logs once per handler.
- A mutation run (a removed item name, state key and key constant entry, a bare "Stamina", and a
  `Component.literal` in a client file) failed the matching test each time.

- [ ] **Steps:** write `LangTest` → run it and fix what it finds → commit `"Gate lang keys, literals and per-tick logging in tests"`.

---

### Task 6: Performance measurement and real-restart scenarios

**Rows:** CD-PERF-01, CD-PERF-02, CD-PERF-05 (runtime part), plus real-restart checks for CD-CLM-07,
CD-LSH-07, CD-WHL-07, CD-CRK-08, CD-BED-14, CD-STA-04, a real `/reload` for CD-CFG-08 and the file
inspection for CD-CFG-01.

Before its first restart, the `restart` scenario checks CD-CFG-01: the server's
`config/cobblemon_dynamics-server.toml` exists and holds every key in the CD-CFG-01 list in
`ConfigTest`. The scenario keeps the same key list.

**Files:** `engine/EngineTiming.kt` (nanoTime around each `tickLevel`, a ring buffer of per-tick totals),
`src/gametest/kotlin/.../test/PerfTests.kt` (namespace `cobblemon_dynamics_perf`), run
`gameTestServerPerf` in `build.gradle`, test pack scenarios `restart` and `reload`.

`PerfTests.cdPerf01And02ReferenceWorld` builds 8 pastures with 8 Working workers each (64 furnaces with
input), waits until all 64 are Working, records `EngineTiming` for 6,000 ticks, and asserts mean ≤ 1.0 ms
and p95 ≤ 2.0 ms. It also counts Dynamics log lines written after the warm-up and asserts that each one is
a once-per-cause warning (CD-PERF-05). The approver can repeat the measurement with spark in the test pack.

Design (as built):

- `EngineTiming` times every per-tick entry point of Dynamics, not only `tickLevel`: the engine's level ticks,
  the leash index check, the panel sends, and the work Brain tasks (`canStillUse`, `start`, `tick` and `stop` of
  the move and look tasks, and the triggers of the two switch tasks). A section inside another counts once.
  `EngineEvents` ends each tick in `ServerTickEvent.Post`, after the panel sends. A recording starts at the next
  whole tick and keeps the per-tick totals in a ring buffer (`TickTimes`). With no recording, a section only runs
  its block. The p95 is the nearest-rank percentile. `EngineTimingTest` covers the sums, the nesting, the ring and
  both statistics on a fake clock.
- NeoForge's `GameTestRegistry` takes a test's namespace as the namespace of its template too, so
  `gen_test_structures.py` also writes `floor_41x8x41` under `data/cobblemon_dynamics_perf/structure/` in the
  full test mod. The run `gameTestServerPerf` loads the full mod set and enables only that namespace.
- The reference world: on `floor_41x8x41`, 8 pastures on a 3 × 3 grid with 16 blocks between them, one owner,
  8 level-50 Charmanders per pasture, and a ring of 8 furnaces 3 blocks out from each pasture, each with 64 raw
  iron, so that every furnace has demand for the whole run. No furnace lies in two work areas. The test waits
  until all 64 workers are Working and then 200 ticks more, records 6,000 ticks, and asserts on every recorded
  tick that all 64 are still Working.
- Until all 64 are Working, the test puts each Traveling worker on its work spot, because it measures work, not
  walks. Without that, 5 of 13 runs left a worker Idle. The worker walked along a row of cell centres
  into a Working worker that it had pushed off its spot, and the two pushed each other straight back, because
  Minecraft pushes two entities apart along the line between their centres. The walk timed out after 600 ticks,
  and CD-ASG-14 then kept the worker off its furnace for 6,000 ticks. A trace of the stuck worker showed a valid
  path to its spot. The finding was a carry-over item for the end-of-phase pass, which fixed it: two workers that the engine
  directs no longer push each other (`WorkerPushMixin`, `WorkerPushTests`). The test still puts Traveling workers on their
  spots, because it measures work, not walks. If a worker is
  not Working at 2,400 ticks, the test fails and names each idle worker's state, position, target, spot and
  skipped targets.
- CD-PERF-05, runtime part: during the 6,000 ticks a log4j appender on the root logger of the mod's logger
  context collects the lines at INFO and above from the `codes.honor.cobblemondynamics.` loggers, without the
  test mod's. Each must be a WARN, and no message may repeat.
- Fake players never join the level, so no work effect packet goes out during the measurement. The approver's
  spark run in the test pack, with a client beside the workers, covers that cost.
- Measured on the development machine, 2026-11-10, in Dynamics server time per tick over 6,000 ticks with 64
  Working workers: mean 0.120 ms and p95 0.202 ms in the first run, and mean 0.129 to 0.150 ms and p95 0.218 to
  0.244 ms over seven runs of the final test. The last of them, on commit `d595a67`, gave mean 0.137 ms and p95
  0.244 ms. No run had a Dynamics log line after the warm-up. The gate report and the M5 pull request quote these
  numbers.
- CI runs `runGameTestServerPerf` in the build job and keeps its log. The run takes about a minute, and the
  budget leaves a margin of 5 to 10 times, so a slower runner does not fail it.

Real restarts and `/reload` in the test pack (as built):

- `restart` checks for real: CD-CFG-01 (the file and every key of the `ConfigTest` list, before the first
  restart; `ConfigTest` fails when the scenario's list differs from its own), CD-WHL-07 and CD-CRK-08 (a water
  wheel and a hand crank set to direction -1 with `data merge block` still read -1 after a real stop and start,
  so `DriveBehaviour` saves and loads the direction with the block entity), and CD-CLM-07 (the claims command
  reports none after the restart).
- `reload` checks CD-CFG-08 for real: a world data pack replaces the Stoker's role file with one that names an
  unregistered handler, and after `/reload` the role registry logs that the Stoker is disabled for that handler.
- A dedicated test server has no player, so it cannot pasture a Pokémon. The checks that need a worker stay in
  the GameTests, which simulate the restart: claims held before a restart (CD-CLM-07,
  `cdClm07ClaimsAreNotSaved`), the binding (CD-LSH-07, `cdLsh07BindingSurvivesRestartAndToggle`), flips by a
  worker (CD-WHL-07, CD-CRK-08, `DriveBehaviourTests`), Work Stamina (CD-STA-04,
  `cdSta04And05StaminaStartsFullAndFollowsThePokemon`), and the bed claim after a restart (CD-BED-14, whose
  GameTest M2 deferred). Real restart with a worker: manual checklist (Task 8), listed in Task 9.

- [ ] **Steps:** write `EngineTiming` and the test → `./gradlew runGameTestServerPerf` → record mean and p95
  in the pull request → commit `"Measure engine server time with 64 Working workers"`.

---

### Task 7: Release workflow

**Rows:** CD-REL-04, CD-REL-12, CD-REL-05.

**Files:** `.github/workflows/release.yml`, `src/test/kotlin/.../build/ReleaseWorkflowTest.kt`.

On a `v*` tag: build with `-Prelease_version=${GITHUB_REF_NAME#v}`, attach the jar to a GitHub release
(marked pre-release when the tag has a suffix), and run the Modrinth and CurseForge publish job only when
the tag matches `^v\d+\.\d+\.\d+$` and the `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` secrets exist. The
publish step uses `Kir-Antipov/mc-publish` at its current major, with version `X.Y.Z+mc1.21.1`.

Design (as built):

- Three jobs. `build` refuses a `v*` tag that does not match `RELEASE_TAG` (`vMAJOR.MINOR.PATCH`, with an
  optional suffix such as `-alpha.1`). It writes the outputs `version` (the tag without its `v`) and `public`
  (true only for a tag that matches `PUBLIC_TAG`, `^v[0-9]+\.[0-9]+\.[0-9]+$`), builds with
  `-Prelease_version="$VERSION"`, checks that the jar's `neoforge.mods.toml` declares `<version>+mc1.21.1`
  (CD-REL-05 on every release), and uploads the jar. `github-release` runs for every release tag: it creates the
  release with the jar and `--prerelease=true` unless the tag is public, and when it runs again for a tag it
  replaces the jar. `publish` runs after both, only when `public` is true.
- GitHub expressions have no regular expressions, so both patterns sit in the workflow's `env`. Bash tests them
  with `[[ $TAG =~ $PATTERN ]]`, and the publish job's `if:` reads the build job's `public` output.
  `ReleaseWorkflowTest` evaluates the same two patterns on sample tags.
- A job's `if:` cannot read secrets. The publish job is the only job that names the publishing secrets, so a
  pre-release run never reads them. Its first step fails the job when `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN` or
  the repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` are missing. A public tag must
  publish (CD-REL-04), so a skip would hide the failure. This replaces "only when the secrets exist". The two
  projects do not exist yet, so their ids are repository variables.
- `Kir-Antipov/mc-publish` moved to `Kira-NT/mc-publish`. The workflow names the new repository at `@v3` (3.3.2),
  with version `X.Y.Z+mc1.21.1`, type `release`, loader `neoforge`, game version `1.21.1`, environment
  `client | server` (CD-PLAT-06) and Java 21. Cobblemon and KotlinForForge are required dependencies, Create and
  AE2 optional, with each site's slug.
- `ReleaseWorkflowTest` reads the workflow with SnakeYAML 2.7, a new test dependency. The `test` task now lists
  the files that unit tests read by path as inputs (`LICENSE`, `README.md`, `build.gradle`, `gradle.properties`,
  `.github/`, `scripts/` and `src/main/`). Without them, Gradle and the build cache reused a passing result after
  such a file changed, as a mutation run of this test showed.
- Checked without a tag: the 7 cases of `ReleaseWorkflowTest`; a mutation run that removed the publish job's
  `if:`, let a suffix tag be public, gave the GitHub release job a publishing secret and dropped
  `-Prelease_version` failed the matching case each time; actionlint with shellcheck, from the super-linter
  image, found nothing; `./gradlew jar -Prelease_version=1.0.0` wrote `cobblemon_dynamics-1.0.0+mc1.21.1.jar`,
  whose `neoforge.mods.toml` declares `version="1.0.0+mc1.21.1"`.
- The live demos need tags. CD-REL-12 (`v1.0.0-alpha.1`, then `v1.0.0-beta.1`) waits for the approver at the
  gate; Task 9 holds its steps. CD-REL-04 is the `v1.0.0` push after the Phase 2 gate (CD-REL-14).

- [ ] **Steps:** `ReleaseWorkflowTest` (the publish job's `if:` holds the no-suffix pattern; a suffix tag
  reaches only the GitHub release step) → write the workflow → commit
  `"Add the release workflow: suffix tags never publish"`. The live CD-REL-12 demo (pushing
  `v1.0.0-alpha.1`) waits for the approver's go-ahead at the gate, because it creates a public GitHub
  pre-release.

---

### Task 8: Player guide and manual checklist

**Rows:** CD-REL-08, CD-REL-09, CD-PLAT-06 (manual check).

**Files:** `docs/player-guide.md`, `docs/testing/manual-checklist.md`,
`src/test/kotlin/.../build/PlayerGuideTest.kt`, `src/test/kotlin/.../build/ManualChecklistTest.kt` (in place of the
planned `scripts/check_manual_checklist.py`), `build.gradle` (test inputs).

The guide has a section for Work Mode, the Work Leash, Work Stamina, beds, healers and each v1 role, written
with the `ste-writing` skill. The checklist has one entry per `Demo:` test in the spec sheet, with its row
id, setup, action and expected result, the negation of the row's fail condition. `ManualChecklistTest` fails
when a Demo row id is missing from the checklist.

Design (as built):

- The guide's sections: Quick start, Work Mode (workers, the work area, how a worker finds its target, Work Mode
  off, the eight states, work effects), Work Leash, Work Stamina, Beds, Healers, Roles (one section per built-in
  role, with its types, level, target, demand rule, numbers and role file parameters), Configuration (every server
  and client config key with its default and range, and role files), Commands and Compatibility. The guide takes
  its numbers from the code and the role files, for example the Generator table from `FeCurve` and the healer
  table from `StaminaMath.healerMinutes`. It describes no Phase 2 role.
- `PlayerGuideTest` (CD-REL-08) checks for a heading for Work Mode, Work Leash, Work Stamina, Beds, Healers,
  Server config, Client config and Commands, and for a heading for each built-in role file. The role name comes
  from the file name (`brew_stoker` is the Brew Stoker), so a role file that M6 or M7 adds fails the test until
  the guide covers it. The test also checks that the guide names every key of the server and client config specs
  and every subcommand that `DynamicsCommands` registers.
- `ManualChecklistTest` (CD-REL-09) reads the spec sheet. It fails when a Phase 1 row whose acceptance test holds
  `Demo:` is missing from the checklist, or when the checklist names a row that the sheet lacks. It runs in
  `./gradlew build` and so in CI, which runs no Python script. The sheet has 34 such rows: 28 whose test starts
  with `Demo:`, and the Demo parts of CD-PLAT-02, CD-PLAT-03, CD-PLAT-05, CD-FX-05, CD-UI-07 and CD-CMP-01. At
  the v1.0.0 tag, CD-REL-09 counts the Phase 2 Demo tests too, so the Phase 2 work drops the test's area filter.
- The `test` task lists `docs/player-guide.md`, `docs/testing/**` and the spec sheet as inputs.
- A mutation run (a removed role heading, a renamed config key, a removed Beds heading, a renamed command, a
  changed row id in the checklist) failed the matching test each time.
- The checklist starts with the play-test setup: the test pack client (`testpack.py client`), a clean NeoForge
  21.1.265 instance, `options.txt` for a new game folder, the optional Cobbleworkers, Cobblebase and Cloth Config
  jars in `run/testpack/cache/`, and the `smoke` server folder, started by hand, for the checks that need a
  server or a second player. It has 42 checks in 11 groups: platform 5, Work Mode and the panel 10, Work Leash 9,
  Work Stamina and beds 3, healers 1, Stoker 1, Wheel Driver 4, Cranker 1, work effects 1, compatibility 4 and
  release 3. Beside the 34 Demo rows, it holds the six real-restart checks of the Task 9 list (CD-CLM-07,
  CD-LSH-07, CD-WHL-07, CD-CRK-08, CD-STA-04, CD-BED-14: checks 13, 24, 33, 34, 26 and 27) and the two
  carry-over checks of the rehydration note (CD-ASG-09 for a wide worker, CD-ASG-14 for a crowded pasture).
- CD-CLM-07 with a real restart: a worker's first poll after a load is immediate, so a person cannot read the
  registry before the first scan. The check turns the Stoker off in the server config while the world is closed.
  A work claim that `/cobblemondynamics claims` then lists can only have come from the save.
- CD-CMP-05 needs a Cobblebase that lacks a hooked method. Cobblebase 2.0.0 has every hooked method, but 1.5.4
  (Modrinth version `arXwBcAs`) lacks `NavigationHelper.findNearbyWaterCached`, as `javap` on its jar shows, and
  its Cobblemon range `[1.7.0,)` admits 1.8.4. The check expects one warning that names `waterTeleport`. If 1.5.4
  itself fails on Cobblemon 1.8.4, the check is blocked.
- CD-LSH-20 needs the Telekinesis role (M6), so the Phase 1 play-test records it as blocked. CD-REL-12 waits for
  the approver at the gate (Task 9), and CD-REL-04 for the public release (M9).

- [ ] **Steps:** write `PlayerGuideTest` and `ManualChecklistTest` → run them and see them fail on the missing
  files → write the guide and the checklist → run them and see them pass → `./gradlew build` → commit
  `"Add the player guide and the manual play-test checklist"`.

---

### Task 9: Phase 1 gate

**Rows:** CD-REL-13 (the Phase 1 record), every Phase 1 `MUST` and `MUST NOT` row.

**Files:** `docs/testing/phase-1-gate.md`, `scripts/gate_report.py`.

- [x] **Step 1:** `gate_report.py` reads the spec sheet, the JUnit XML reports and the GameTest logs of every
  run, and writes a table of each Phase 1 `MUST` and `MUST NOT` row with its test names, result and the
  commit hash. A row whose evidence is a Demo is marked `play-test`.
- [x] **Step 2:** run `./gradlew build runGameTestServer runGameTestServerCore runGameTestServerPerf` and every
  test pack scenario on one commit; generate the report; every automated row passes.
- [x] **Step 3:** assemble the approver's play-test instance with `py scripts/testpack/testpack.py client` and
  write its path and steps at the top of the gate report.
- [x] **Step 4:** commit the report, push, open the M5 pull request, resolve reviews and merge.
- [x] **Step 5: Stop.** Hand off to the approver for the play-test. CD-REL-13 completes when the approver's
  play-test note is recorded in the gate report, before any M6 work merges. The approver reported the second
  round GREEN on 2026-11-10, after PR #12 fixed the five findings of round 1. The gate report's play-test note
  records it, and CD-REL-13 is complete.

Steps 1 to 3 as built:

- `scripts/gate_report.py` uses the standard library only. It rewrites two marked blocks of
  `docs/testing/phase-1-gate.md`, the run summary and the Rows section, and keeps the text around them. Besides the
  inputs of Step 1, it reads the deferred list below, the manual checklist, the test pack's README, `testpack.py`,
  `build.gradle`, and the saved output of the Gradle command (`--gradle-log`) and of each test pack command
  (`--testpack-log`). It exits with 1 when a row has no passing test or two inputs disagree, and with 2 when an input is
  missing. `--self-check` runs its parsers on samples.
- The GameTest runs write no JUnit-style report. A run's log names only the failed tests and ends with
  `All N required tests passed`, so the script takes the test names from the `@GameTest` methods of each run's namespace
  and source folders and checks their number.
- A GameTest cites a row through its name, its KDoc or an assertion message, also in a private helper that it calls.
  With names alone, CD-AI-08, CD-BRF-01, CD-BRF-04, CD-BRW-01, CD-BRW-03, CD-BRW-04 and CD-MUS-01 had no test. The
  Gradle tasks `verifyJar` and `verifyNoAe2` check CD-PLAT-04, CD-REL-07 and CD-CRY-05. The test pack README's tables
  give the commands and cases of seven rows, and the scripted checklist checks give the cases of the PLAT rows. The
  script marks CD-REL-04, CD-REL-14 and CD-REL-15 open after Phase 1, and CD-REL-13 play-test. It refuses to write when
  tracked files differ from the commit, and it counts a log older than the commit as stale.
- The first gate run, on `662e02c`, failed `RestTests.cdAi07NightSleepEndsWhenWorkStarts` once. Cobblemon's drowsy
  sensor rouses a drowsy Pokémon by chance (1 in 240 for each run, once a second), and its wake-up task then wakes it in
  the same Brain tick, before the test sets the memory again. A confirmation run then failed
  `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer` once, because the ally's sleep loop (CD-FX-10)
  reached the player before the heal ended. Commit `0cddb71` fixed both tests: the first waits for the next sleep, and the
  second leaves the sleep particles out of its heal check. Every row assertion stays, and two full runs passed.
- The gate run on `0cddb71` passed: 356 unit tests, `verifyJar`, `verifyNoAe2`, 162, 3 and 1 GameTests, and the test
  pack's `smoke`, `deps` (7 cases), `compat` (2 cases), `reload` and `restart`. The perf run gave a mean of 0.140 ms and
  a p95 of 0.252 ms, with no Dynamics log line after the warm-up. All 269 rows with automated tests pass, 35 rows have a
  play-test part (6 of them have no automated test), and CD-REL-04, CD-REL-14 and CD-REL-15 stay open.
- `testpack.py client` filled `run/testpack/client/mods/` with the five pinned jars and the mod jar of that commit. The
  gate report gives the launch steps for a new Modrinth App instance, the optional compatibility jars and the
  approver's steps, and it lists the known issues and open decisions.

CD-REL-14 (the Phase 2 gate before 1.0.0 publishes) and CD-REL-15 (1.0.0 joins the approver's pack) belong to
M8 and M9. The gate report lists them as open, out of this plan's scope.

**CD-REL-12 demo.** Run it only after the approver's go-ahead, because each tag makes a public GitHub pre-release.
Use the commit that the gate report names, after the M5 merge.

1. `git tag -a v1.0.0-alpha.1 -m "Cobblemon Dynamics 1.0.0-alpha.1" <commit>`, then `git push origin v1.0.0-alpha.1`.
2. Wait for the `release` run of the tag to end. `gh run list --workflow release.yml` gives its id, and
   `gh run view <id> --json jobs --jq '.jobs[] | [.name, .conclusion]'` must show `Build the release jar` and
   `GitHub release` as `success` and `Modrinth and CurseForge` as `skipped`.
3. `gh release view v1.0.0-alpha.1 --json isPrerelease,assets` must show `isPrerelease: true` and the asset
   `cobblemon_dynamics-1.0.0-alpha.1+mc1.21.1.jar`.
4. Neither site may list the version: search the Modrinth and CurseForge project pages for `1.0.0-alpha.1`, if the
   projects exist yet.
5. Repeat steps 1 to 4 with `v1.0.0-beta.1`.
6. Record both run URLs, the job conclusions and the site checks in the gate report. Fails if: a publish job runs
   for either tag, or either version appears on either site.

**End-of-phase pass for deferred GameTests.** Before Step 2, give every row in the list below a test: a
GameTest, a test pack scenario, or a manual checklist line from Task 8. Run them and fix what fails, then
generate the report. The gate report states how each of these rows was checked.

Deferred rows (the index's execution rule 6; append as milestones defer more). A row with a check after its id has
its end-of-phase test. Test names are `Class.method` in `src/gametest/kotlin/`, and checklist numbers are checks of
`docs/testing/manual-checklist.md`.

- M1 Task 10:
  - CD-FX-04: `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; manual checklist 30 (the spray).
  - CD-FX-05: `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; manual checklist 31 (no render).
  - CD-FX-06: `EffectTests.cdFx06FocusIsNeverSaved`.
  - CD-FX-07: `EffectTests.cdFx07FocusLeavesWithWorking`.
  - CD-PERF-04: `EffectTests.cdPerf04NoFocusOutlivesItsClaim`.
- M1 Tasks 11 to 13:
  - CD-LSH-05: `LeashTests.cdLsh05Lsh06RefusalsAndTheBoundMessage`; `GeneratorTests.cdLsh05PikachuOnBattery` (M4).
  - CD-LSH-06: `LeashTests.cdLsh05Lsh06RefusalsAndTheBoundMessage` (furnace); manual checklist 18 (water wheel).
  - CD-LSH-09: `LeashClaimTests.cdLsh09UnloadKeepsTheBinding`, with the unload of the Test architecture
    (`EngineEvents.onChunkUnload`). GameTest chunks are force-loaded, so no GameTest can unload a target within 8
    blocks of its pasture.
  - CD-LSH-10: `LeashTests.cdLsh10SneakClickClearsTheBinding`.
  - CD-LSH-11: `LeashTests.cdLsh11RebindReplacesTheBinding` (furnaces).
  - CD-LSH-13: the binder's message in each `LeashResetTests` test (`cdLsh08RecallAndTargetLoss`,
    `cdLsh13Lsh14EvolutionWithNoFittingRoleClearsTheBinding`, `cdLsh24MovingToAnotherPastureClearsTheBinding`,
    `cdLsh25ReleaseClearsTheBinding`, `cdLsh26TradeClearsTheBindingData`,
    `cdLsh27Wmd07BreakingThePastureClearsEveryBinding`); no message after the owner's own action
    (`LeashTests.cdLsh10SneakClickClearsTheBinding`, `LeashTests.cdLsh11RebindReplacesTheBinding`); manual checklist 19.
  - CD-LSH-14: `LeashResetTests.cdLsh13Lsh14EvolutionWithNoFittingRoleClearsTheBinding` (Cobblemon's evolution of a
    Kirlia bound as a Muse into Gallade).
  - CD-LSH-15: `LeashTests.cdLsh15RecipeCraftsOneLeash`.
  - CD-LSH-17: `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination`.
  - CD-LSH-18: `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination`.
  - CD-LSH-19: `PendingSourceTest` (599 and 600 ticks); `LeashTests.cdLsh19AirClickAndTimeoutClearThePendingSource`.
  - CD-LSH-20: `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination` (test role); manual checklist 20 (blocked until M6).
  - CD-LSH-21: `LeashClaimTests.cdLsh21CannotReachMessage`; manual checklist 21.
  - CD-LSH-24: `LeashResetTests.cdLsh24MovingToAnotherPastureClearsTheBinding`.
  - CD-LSH-25: `LeashResetTests.cdLsh25ReleaseClearsTheBinding` (Cobblemon's PC release handler).
  - CD-LSH-26: `LeashResetTests.cdLsh26TradeClearsTheBindingData` (Cobblemon's trade, for the row's unit case).
  - CD-LSH-27: `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding`.
  - CD-WMD-07: `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding`.
  - CD-CLM-05: `LeashClaimTests.cdClm05BindTakesTheTargetFromAnAutoWorker`.
  - CD-CLM-06: `LeashClaimTests.cdClm06ReservedTargetWaitsForItsWorker` (the row's whole scene: the bound worker
    asleep, then its pasture's Work Mode off with a worker of a second pasture).
  - CD-ASG-20: `LeashClaimTests.cdAsg20LeashedWorkerStaysOnItsBoundTarget`.
  - CD-SEC-07: `LeashTests.cdSec07OnlyTheOwnerSelects`.
  - CD-SEC-11: `LeashTests.cdSec11CancelledClickNeverBinds`.
  - CD-UI-14: `CommandTests.cdUi14ClaimsListsTheWorkClaimAndTheBinding` (work claims and bindings).
  - CD-SEC-09: `CommandTests.cdSec09LevelZeroPlayerIsRefused` (the claims and the stamina command).
  - CD-SEC-05 and CD-WMD-03, the payload path: `PayloadTests.cdSec05Wmd03PlacerPayloadsSetTheRequestedState`.
  - CD-SEC-06 and CD-WMD-04, the payload path: `PayloadTests.cdSec06Wmd04TwoOperatorsInOneTick`.
- M2 Tasks 1 to 3:
  - CD-STA-11: `StaminaStoreTests.cdSta11PcTimeRecoversAtBaseRate`.
  - CD-STA-12: `StaminaStoreTests.cdSta12TimeCommandsChangeNothing` (an operator's `/time set day` and `/time add 24000`, in
    batch `time_sta12`).
  - CD-BED-01: `BedContentTests.cdBed01RegistryListsFourBedsWithTheirTiers`.
  - CD-BED-02: `BedContentTests.cdBed02EachBedCraftsFromTheBedBelow`.
- M2 Tasks 4 to 6:
  - CD-STA-13: `RestTests.cdSta13TiredAutoWorkerFreesItsMachineForTheNextScan`.
  - CD-LSH-12: `RestTests.cdLsh12BindingMadeWhileTiredWaitsUntilFull`.
  - CD-BED-07: `RestTests.cdBed07OnlyABedInsideTheWorkAreaIsClaimed`.
  - CD-BED-11: `RestTests.cdBed11BrokenBedMeansRestInPlace`.
  - CD-BED-13: `RestTests.cdBed13OnlyTiredWorkersOfWorkModePasturesGoToBed`.
  - CD-BED-14: `RestTests.cdBed14SleeperClaimsABedAfterRestart` (batch `restart_bed14`).
  - CD-CLM-06 (the Work Mode off and second pasture parts): `LeashClaimTests.cdClm06ReservedTargetWaitsForItsWorker`.
  - CD-WMD-05: `AssignmentTests.cdWmd05WorkModeOffReleasesEveryClaim`, now with a real sleeper and a real healer
    (`EveryClaimScene`).
  - CD-FX-10 (demo): `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer` (the packets that a recording player
    in the level's player list gets: the two sleep particles every 40 ticks on a sleeper and on a worker that rests in place, and
    no work sound); manual checklist 25.
  - CD-HEAL-06: `HealerTests.cdHeal06OnlyNonFairyAlliesOfTheHealersPasture`.
  - CD-HEAL-07: `HealerTests.cdHeal07NearestTiredAllyFirst`.
  - CD-HEAL-09: `HealerTests.cdHeal09OneHealerPerAlly`.
  - CD-HEAL-11: `HealerTests.cdHeal11HealCancelsAtNoCost` (an ally that reaches 100% on its bed, then an ally that leaves the
    pasture).
  - CD-HEAL-15 (demo): `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer` (the healer's `special` and `cry`
    animation and the two heal particles on the ally); manual checklist 28.
  - CD-HEAL-17: `HealerTests.cdHeal17SpentHealerGoesToBedOrRestsInPlace`.
  - CD-UI-15: `CommandTests.cdUi15StaminaShowsValueStateAndRule`.
  - CD-UI-14 (bed and heal claims): `CommandTests.cdUi14ClaimsListsTheBedAndTheHealClaim`.
  - CD-SEC-09 (stamina command): `CommandTests.cdSec09LevelZeroPlayerIsRefused`.
- M3 Tasks 1 to 3, each the water-wheel version of the row's final part:
  - CD-ASG-01: `WheelRowTests.cdAsg01OnlyAHealthyPasturedWaterTypeClaimsTheWheel`.
  - CD-ASG-04: `WheelRowTests.cdAsg04AzumarillLeavesTheDryWheelAlone`.
  - CD-ASG-19: `WheelRowTests.cdAsg19WheelDriverMovesToADryWheel`.
  - CD-LSH-03, CD-LSH-06 and CD-LSH-11: `WheelRowTests.cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels`; manual checklist 18
    (CD-LSH-06 on screen).
  - CD-LSH-14: `WheelRowTests.cdLsh14EvolutionWithoutWaterClearsTheWheelBinding` (Cobblemon's evolution of a bound Surskit, Bug
    and Water, into Masquerain, Bug and Flying).
- M3 Tasks 4 to 6:
  - CD-CRK-04: `CrankerTests.cdCrk04ValveHandleIsNotATarget`.
  - CD-CRK-07: `CrankerTests.cdCrk07NoNetworkStillWorks`.
  - CD-BRN-02: `BurnerTests.cdBrn02EmptyBurnerIsNeverClaimed`.
  - CD-BRN-03: `BurnerTests.cdBrn03NoBasinMeansStandby`.
  - CD-BRN-04: `BurnerTests.cdBrn04FuelBurnsDownAsNormal`.
  - CD-SEC-10 (wheel and crank): `CreateFlipTests.cdSec10StrangersAndIdleTargetsNeverFlip`.
- M4 Tasks 1 to 2:
  - CD-GEN-04: `GeneratorTests.cdGen04NearestAcceptingFace` (the charger's buffer, read through its FE capability on a power
    face).
  - CD-GEN-07: `GeneratorTests.cdGen07Ae2RatioAtLv50`. The full test mod now has the AE2 jar as `gametestCompileOnly`, and the
    test reads the grid's one energy cell through AE2's `IAEPowerStorage`: `IEnergyService.getStoredPower` gives a total that
    AE2 recomputes only every 90 ticks or on a storage event. A test pack case could not do it, because its servers have no
    player to pasture a worker.
  - CD-CFG-03 and CD-CFG-13: `RoleDataTests.cdCfg03Cfg13DatapackSetsTheGeneratorRates` (batch `roles_gen`, through the reload
    path of `RoleRegistry`).
  - CD-CFG-05 (the Generator): `RoleDataTests.cdCfg05DisabledRoleGetsNoClaims`, which now disables the Generator beside the
    Stoker.
- M4 Tasks 3 to 5:
  - CD-BRF-05: `BrewingTests.cdBrf05ReleaseBeforeABrewLeavesFuel0` (the row's literal case).
  - CD-PMP-06: `PumpTests.cdPmp06FiveCauldronsOnRealBlocks` (the handler's five results, and five bound Pumps that agree).
- M5 Tasks 5 to 7: the real-restart parts that need a pastured worker, which the test pack cannot make without a
  player. Their GameTests simulate the restart; a manual checklist line repeats each with a real restart in the play-test.
  - CD-CLM-07: `AssignmentTests.cdClm07ClaimsAreNotSaved`, now with two work claims, a bed claim and a heal claim
    (`EveryClaimScene`); the test pack's `restart`; manual checklist 13.
  - CD-LSH-07: `LeashTests.cdLsh07BindingSurvivesRestartAndToggle`; manual checklist 24.
  - CD-WHL-07 and CD-CRK-08: `CreateFlipTests.cdWhl07Crk08FlipsByBoundWorkersSurviveARestart` (the binder's flip of a wheel and
    a crank that bound workers drive, then the engine reset and each block entity reloaded from its save);
    `DriveBehaviourTests`; the test pack's `restart`; manual checklists 33 and 34.
  - CD-STA-04: `StaminaStoreTests.cdSta04And05StaminaStartsFullAndFollowsThePokemon`; manual checklist 26.
  - CD-BED-14: `RestTests.cdBed14SleeperClaimsABedAfterRestart`; manual checklist 27.

The same pass checked the three behaviour items of the rehydration note's carry-over list, each with a GameTest that failed
before its fix:

- Worker deadlock: `WorkerPushTests.workersMeetingHeadOnInOneRowPassAndWork` and `aWalkerPassesAWorkingWorkerInOneRow` (a
  corridor one block wide), and `onlyTwoDirectedWorkersSkipTheirPush`. Fix: `WorkerPushMixin` skips the push between two
  workers that the engine directs, in any state but Idle. The delegate's preferred fix named Traveling and Working. Standby
  workers hold their spots as Working ones do, and workers that walk to a bed or an ally meet held workers in the same rows, so
  every directed state counts. An Idle worker has no place to hold, so it still pushes and is pushed. Manual checklist 15
  repeats the check in a crowded pasture.
- Creative Blaze Burner: `BurnerTests.creativeBlazeBurnerHasNoDemand`. Fix: `BurnerHeatHandler.hasDemand` is false for a
  creative burner.
- Wide worker spot (CD-ASG-09): `WideWorkerTests.cdAsg09WideWorkerGetsASpotItsBodyFits`. With one cell checked, a Camerupt 2.3
  blocks wide got a spot in a gap one block wide, where its body overlapped the walls. Fix: `SpotGrid.fitsBody` needs room for
  the body on the spot's center and where navigation stops it (`WorkerBody`). Manual checklist 14 repeats the check in the
  play-test.

## Approver rulings after the play-test (2026-11-10)

The approver reported the second play-test round GREEN (Task 9, Step 5) and ruled the changes below on the same day.
The spec sheet records them as inputs R59 to R61 and in its corrections list. Each ruling is test-first, with its own
commits on the branch `approver-changes`.

### Ruling: no Fire-types on Blaze Burners (R59)

**Rows:** CD-BRN-01 to CD-BRN-07, CD-SUP-02 and CD-ASG-16, amended. [M3 Task 5](2026-11-09-phase-1-m3-create-roles.md#task-5-superheater)
holds the design.

- `burner_keeper.json` is gone. The Superheater's file already gives `kindled` below level 50 and `seething` from level 50,
  so the handler code stays as it was.
- Tests: `HeatGateTest` (kindled below level 50), `DefaultPriorityTest` (a burner has only the Superheater, so a Charmander
  gets no role there and a Turtonator works it as the Superheater), `BuiltInRoleFilesTest` (the Role table),
  `MixinGateTest` (a data pack's own role on the burner handler still gives one warning per handler), `BurnerTests` with a
  Lv10 Dratini in place of the Charmander, and `BurnerTests.cdSup02FireTypeNeverClaimsABlazeBurner`.

### Ruling: the Charge Cell (R60)

**Rows:** CD-GEN-08, CD-GEN-09, CD-GEN-10, CD-GEN-11 and CD-GEN-12, new. CD-REL-08 and CD-ART-01, amended.

**Files:** `cell/ChargeCellRules.kt` (pure: the capacity, the split, the levelling, the signal and the FE count),
`cell/ChargeCellBlock.kt`, `cell/ChargeCellBlockEntity.kt`, `registry/DynamicsBlocks.kt` (the block, its item, its block
entity type, the `cobblemon_dynamics:stored_energy` data component and the capability), the blockstate, the block and item
models, the placeholder texture from `scripts/placeholder_art.py`, the loot table, the recipe, the `mineable/pickaxe` tag
and the lang entries. Tests: `ChargeCellRulesTest`, `ChargeCellTests` and `PlayerGuideTest`.

Design, as built:

- `RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, provider)` (checked with `javap` on
  the NeoForge 21.1.265 jar) gives every face the same `IEnergyStorage`. It takes and gives FE, and `canReceive()` stays
  true when the cell is full, so a Generator at a full cell waits in Standby (CD-GEN-06) instead of losing its claim.
- The server tick first splits the cell's FE between the faces whose blocks take FE and are not cells
  (`ChargeCellRules.split`). Each face gets an equal share up to what a simulated insert takes, what a face cannot take
  goes to the others, and the FE that does not divide evenly goes one each in turn, from a face that moves on with each
  split. Then `ChargeCellRules.levelWith` gives each neighbouring cell that holds less only enough FE to bring the giver and
  those cells to one level, and the giver keeps at least that level. FE therefore moves only from a cell that holds more to
  one that holds less, and each move lowers the sum of the squares of the stores, so FE between cells settles and stops. A
  first rule, half the difference for each neighbour, let a cell with several lower neighbours fall below them. The cell
  finds its neighbours' capabilities through `BlockCapabilityCache`, which reads nothing in an unloaded chunk.
- `BlockEntity.setChanged` calls `Level.updateNeighbourForOutputSignal`, so each real change of the store also tells the
  comparators (CD-GEN-10).
- `collectImplicitComponents` writes `stored_energy` when the cell holds FE, and the loot table's `copy_components` copies it
  to the drop. `applyImplicitComponents` loads it into a cell that an item places. The tooltip comes from
  `Block.appendHoverText`. `GameTestHelper.destroyBlock` drops nothing, so `ChargeCellTests` breaks the cell through
  `ServerLevel.destroyBlock(pos, true)`.
- The cell has no transfer limit per tick: it gives what the blocks around it take, up to its store. The ruling names none.

### Ruling: a creative tab (R61)

**Rows:** CD-UI-16, new.

**Files:** `registry/DynamicsTabs.kt` (new), `registry/DynamicsItems.kt` and `registry/DynamicsBlocks.kt` (no vanilla tab
listener now), `CobblemonDynamics.kt`, the lang entry `itemGroup.cobblemon_dynamics.main`. Test:
`CreativeTabTests.cdUi16CreativeTabListsEveryItemOfTheMod`.

- The approver did not find the items in the creative menu, although the Work Leash was in the Tools and Utilities tab
  and the beds were in the Functional Blocks tab. The tab `cobblemon_dynamics:main` comes from
  `CreativeModeTab.builder()` through a `DeferredRegister` of `Registries.CREATIVE_MODE_TAB`. Its title is
  "Cobblemon Dynamics", its icon is the Work Leash, and `DynamicsTabs.items()` gives its order: the Work Leash, the beds by
  tier and the Charge Cell.
- The items leave the vanilla tabs, because no spec row asks for them there.
- `CreativeModeTab.buildContents` is common code (checked with `javap`): it fires NeoForge's
  `BuildCreativeModeTabContentsEvent` and needs no client. So the GameTest builds the tab and the two vanilla tabs on the
  server and compares each with the items that the mod registers. A new item fails the test until the tab lists it.
