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

**Files:** `client/WorkModeButton.kt`, `client/PastureScreenHooks.kt` (`ScreenEvent.Init.Post` and `Closing`),
`client/PanelLayout.kt` (pure geometry), lang keys `cobblemon_dynamics.ui.work_mode.on` = `Work Mode: On`,
`.off` = `Work Mode: Off`, `.locked` = `Only the pasture's owner can change Work Mode`,
`src/test/kotlin/.../client/PanelLayoutTest.kt`.

On `Init.Post` for a `PCGUI` whose `configuration is PasturePCGUIConfiguration`, add the button at panel
offset `(6, 153 - buttonHeight - 2)`, directly above Recall (R35), and send `pasture_panel_open`. The label
and the enabled state come from `ClientState.panel` when `pasture_panel_state` arrives (CD-UI-02); a viewer
who is neither the placer nor an operator sees it disabled with the tooltip (CD-UI-03). A click sends
`WorkModeTogglePayload(!current)`. `Closing` sends `pasture_panel_close`.

- [ ] **Steps:** `PanelLayoutTest` (for 1920 × 1080 and 854 × 480 scaled sizes, the button rectangle sits
  inside the 82 × 169 panel, ends 2 pixels above Recall at (6, 153), and overlaps no other control
  rectangle) → write the code → `./gradlew build` → record the Demo steps in the manual checklist (Task 8) →
  commit `"Add the Work Mode button above Recall in the pasture panel"`.

---

### Task 2: Panel stamina bars and state icons

**Rows:** CD-UI-04, CD-UI-05, CD-UI-07, CD-UI-08, CD-UI-10, CD-UI-11, CD-UI-13, CD-ART-11.

**Files:** `mixin/cobblemon/client/PastureWidgetMixin.java` (TAIL of `renderWidget`), `client/PanelBars.kt`,
`client/StateIcons.kt`, placeholder sprites `textures/gui/state/{idle,traveling,working,standby,tired,sleeping,healing}.png`
and `textures/gui/stamina_bar.png` from `scripts/placeholder_art.py` (each icon a different shape: dot, arrow,
gear, pause bars, droop, Z, plus), `src/test/kotlin/.../client/StateIconsTest.kt`.

`StateIcons.iconFor(status)` maps the eight states to seven icons: ToBed and RestingInPlace share Tired
(CD-UI-07). The server sends `pasture_panel_state` every 20 ticks while the panel is open
(`PanelCadence`, CD-UI-05). Hovering a bar shows `NN% Work Stamina` and the state name (CD-UI-10, CD-UI-11).
If `MixinState.applied(PASTURE_WIDGET)` is false, the bars are skipped with one warning, and the button and
head bars still work (CD-UI-13).

- [ ] **Steps:** `StateIconsTest` (eight states, seven distinct textures, ToBed and RestingInPlace share one;
  the seven PNGs differ pixel by pixel in their alpha mask, so shape differs, not only color — CD-UI-08) →
  `MixinGateTest`-style unit for UI-13 (flag false: no exception, one warning) → write the code →
  `./gradlew build` → commit `"Draw Work Stamina bars and state icons in the pasture panel"`.

---

### Task 3: Head bars and bound-target outlines

**Rows:** CD-UI-06, CD-UI-09, CD-UI-11.

**Files:** `client/HeadBars.kt` (`RenderLivingEvent.Post` for `PokemonEntity`, reads the synced
`stamina_view`), `client/TargetOutlines.kt` (`RenderLevelStageEvent` after translucent blocks,
`LevelRenderer.renderLineBox` around each target in `ClientState.leashOverview`), `client/HeldLeash.kt`
(sends `leash_overview_request` when the main hand changes to the leash), `src/test/kotlin/.../client/HeadBarRangeTest.kt`.

Head bars draw only while the main hand holds the Work Leash, for workers within `hud.headBarRange` blocks
(16.0 draws, 16.1 does not) (CD-UI-06). Outlines show only the targets of the viewer's own bound workers,
because the server's overview reply holds only those (CD-UI-09).

- [ ] **Steps:** `HeadBarRangeTest` (16.0 in, 16.1 out); a GameTest `cdUi09OverviewHoldsOnlyOwnTargets` (two
  players with one binding each: each overview reply holds one entry) → write the code → `./gradlew build
  runGameTestServer` → commit `"Show head bars and bound-target outlines while the Work Leash is held"`.

---

### Task 4: Cobbleworkers and Cobblebase compatibility

**Rows:** CD-CMP-01, CD-CMP-02, CD-CMP-03, CD-CMP-04, CD-CMP-05, CD-CMP-06, CD-CMP-07, CD-PLAT-08 (still
optional).

**Files:** `compat/CobbleworkersRest.kt` (pure flag logic over a `CompoundTag`), `compat/CobbleworkersCompat.kt`
(loaded only when `cobbleworkers` is present), `compat/CobblebaseCompat.kt` and
`mixin/compat/cobblebase/CobblebasePastureTickMixin.java` (`@Pseudo`, cancels Cobblebase's pasture tick entry
for a pasture in Work Mode), `src/test/kotlin/.../compat/CobbleworkersRestTest.kt`, test pack scenarios
`compat_cobbleworkers` and `compat_cobblebase`.

`CobbleworkersRest.mark(data)` sets `cobbleworkers.resting = true` and our ownership key
`cobblemon_dynamics.set_cw_rest = true` only when the flag was not already set; `unmark(data)` clears the
flag only when our key is present (CD-CMP-02). The engine calls `mark` on each Work Mode worker at its
20-tick poll (CD-CMP-01) and `unmark` when Work Mode turns off or the Pokémon leaves. The Cobblebase mixin
targets the method that Cobblebase's own `PokemonPastureBlockEntityMixin` calls at `TICKER$lambda$0` TAIL;
when it fails to apply, `MixinState` records nothing and the compat logs one warning (CD-CMP-05). Class
loading: the compat classes sit behind `ModList.isLoaded` checks in their own packages (CD-CMP-06,
CD-CMP-07).

- [ ] **Steps:** `CobbleworkersRestTest` (the flag within one poll; a player-set flag survives `unmark`; a
  Dynamics-set flag is cleared) → write the code → add test pack mod pins for Cobbleworkers 2.0.7 and
  Cobblebase 2.0.1 (Modrinth) → scenarios: a server with Cobblebase and no Cobbleworkers starts with no error
  naming a Cobbleworkers compat class (CD-CMP-06), and the reverse (CD-CMP-07) → `./gradlew build
  runGameTestServer && py scripts/testpack/testpack.py compat` → commit
  `"Switch Cobbleworkers and Cobblebase off in Work Mode pastures"`. CD-CMP-01, CD-CMP-03 and CD-CMP-04 Demo
  parts (a worker that stays at its machine for 1,200 ticks with each mod) go to the manual checklist and
  the approver's play-test.

---

### Task 5: Lang and logging gates

**Rows:** CD-UI-11, CD-UI-12, CD-PERF-05 (static part).

**Files:** `src/test/kotlin/.../build/LangTest.kt`.

`LangTest` scans `src/main/kotlin/**` and `src/main/java/**`: every `Component.translatable("...")` key and
every `cobblemon_dynamics.` key string in code exists in `en_us.json`; no file under `client/` or `leash/`
or `command/` calls `Component.literal`; every `en_us` value that names the stat reads `Work Stamina`, never
a bare `Stamina`; no `LOGGER.info`, `LOGGER.warn` or `LOGGER.error` call sits in a file under `engine/`,
`handler/` or `fx/` except through `OnceLogger`, the helper that logs each cause once.

- [ ] **Steps:** write `LangTest` → run it and fix what it finds → commit `"Gate lang keys, literals and per-tick logging in tests"`.

---

### Task 6: Performance measurement and real-restart scenarios

**Rows:** CD-PERF-01, CD-PERF-02, CD-PERF-05 (runtime part), plus real-restart checks for CD-CLM-07,
CD-LSH-07, CD-WHL-07, CD-CRK-08, CD-BED-14, CD-STA-04 and a real `/reload` for CD-CFG-08.

**Files:** `engine/EngineTiming.kt` (nanoTime around each `tickLevel`, a ring buffer of per-tick totals),
`src/gametest/kotlin/.../test/PerfTests.kt` (namespace `cobblemon_dynamics_perf`), run
`gameTestServerPerf` in `build.gradle`, test pack scenarios `restart` and `reload`.

`PerfTests.cdPerf01And02ReferenceWorld` builds 8 pastures with 8 Working workers each (64 furnaces with
input), waits until all 64 are Working, records `EngineTiming` for 6,000 ticks, and asserts mean ≤ 1.0 ms
and p95 ≤ 2.0 ms. It also counts Dynamics log lines written after the warm-up and asserts that each one is
a once-per-cause warning (CD-PERF-05). The approver can repeat the measurement with spark in the test pack.

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

- [ ] **Steps:** `ReleaseWorkflowTest` (the publish job's `if:` holds the no-suffix pattern; a suffix tag
  reaches only the GitHub release step) → write the workflow → commit
  `"Add the release workflow: suffix tags never publish"`. The live CD-REL-12 demo (pushing
  `v1.0.0-alpha.1`) waits for the approver's go-ahead at the gate, because it creates a public GitHub
  pre-release.

---

### Task 8: Player guide and manual checklist

**Rows:** CD-REL-08, CD-REL-09, CD-PLAT-06 (manual check).

**Files:** `docs/player-guide.md`, `docs/testing/manual-checklist.md`, `scripts/check_manual_checklist.py`.

The guide has a section for Work Mode, the Work Leash, Work Stamina, beds, healers and each v1 role, written
with the `ste-writing` skill. The checklist has one entry per `Demo:` test in the spec sheet, with its row
id, setup, steps and the fail condition copied from the row. `check_manual_checklist.py` fails when a Demo
row id is missing from the checklist.

- [ ] **Steps:** write the script → run it and see it fail → write the checklist and the guide → run it and
  see it pass → commit `"Add the player guide and the manual checklist for every Demo test"`.

---

### Task 9: Phase 1 gate

**Rows:** CD-REL-13 (the Phase 1 record), every Phase 1 `MUST` and `MUST NOT` row.

**Files:** `docs/testing/phase-1-gate.md`, `scripts/gate_report.py`.

- [ ] **Step 1:** `gate_report.py` reads the spec sheet, the JUnit XML reports and the GameTest logs of every
  run, and writes a table of each Phase 1 `MUST` and `MUST NOT` row with its test names, result and the
  commit hash. A row whose evidence is a Demo is marked `play-test`.
- [ ] **Step 2:** run `./gradlew build runGameTestServer runGameTestServerCore runGameTestServerPerf` and every
  test pack scenario on one commit; generate the report; every automated row passes.
- [ ] **Step 3:** assemble the approver's play-test instance with `py scripts/testpack/testpack.py client` and
  write its path and steps at the top of the gate report.
- [ ] **Step 4:** commit the report, push, open the M5 pull request, resolve reviews and merge.
- [ ] **Step 5: Stop.** Hand off to the approver for the play-test. CD-REL-13 completes when the approver's
  play-test note is recorded in the gate report, before any M6 work merges.

CD-REL-14 (the Phase 2 gate before 1.0.0 publishes) and CD-REL-15 (1.0.0 joins the approver's pack) belong to
M8 and M9. The gate report lists them as open, out of this plan's scope.
