# Phase 2 gate report

This report is the Phase 2 record of CD-REL-14. It names the commit on which every automated check ran. It gives the
result of each `MUST` and `MUST NOT` row of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md), the Phase 1 rows
included, because CD-REL-14 needs every such row to pass on one commit. It also holds the approver's play-test note.
CD-REL-14 completes when this report records a passing run on a named commit and the approver's play-test note. The
[art review record](art-review.md) must also approve every asset. The `v1.0.0` tag and the publication come after the
gate, in M9.

`py scripts/gate_report.py --phase 2` writes the two generated blocks: the run summary below and the Rows section at the
end. The section [How this report is made](#how-this-report-is-made) gives its inputs and rules. The script keeps the
text outside the two blocks.

<!-- gate-report summary: written by scripts/gate_report.py; edits inside this block are lost -->

## Run summary

`scripts/gate_report.py` wrote this block and the Rows section on 2026-11-11 (UTC). Every check below ran on commit `ceec1341a7a896f1809c799d82c0eca027b80070` (2026-11-11, "Add check 59 and the absolute paths to the Phase 2 gate report, and list ADR-0013 in the README [rag:2]").

| Check | Command | Result |
| --- | --- | --- |
| Unit tests | `./gradlew build` | 517 of 517 pass, in 96 classes |
| Jar and source checks | `./gradlew build` | `verifyJar` pass, `verifyNoAe2` pass. The Gradle log ends with BUILD SUCCESSFUL. |
| GameTests, full mod set | `./gradlew runGameTestServer` | 220 of 220 pass. The log counts 220 tests and 0 failures, in 48.30 s. |
| GameTests without Create and AE2 | `./gradlew runGameTestServerCore` | 4 of 4 pass. The log counts 4 tests and 0 failures, in 743.8 ms. |
| GameTests, server time of 64 Working workers | `./gradlew runGameTestServerPerf` | 1 of 1 pass. The log counts 1 tests and 0 failures, in 9.633 s. |
| Test pack | `py scripts/testpack/testpack.py smoke` | pass |
| Test pack | `py scripts/testpack/testpack.py deps` | pass, cases 7 of 7 pass |
| Test pack | `py scripts/testpack/testpack.py compat` | pass, cases 2 of 2 pass |
| Test pack | `py scripts/testpack/testpack.py reload` | pass |
| Test pack | `py scripts/testpack/testpack.py restart` | pass |
| Art checks | `py scripts/art.py check --references auto --release` | fail, cases 6 of 8 pass. `cd-art-10` and `cd-art-12` fail until the review record approves every asset. |
| Play-test client | `py scripts/testpack/testpack.py client` | `run/testpack/client/mods/` holds 6 jars: `appliedenergistics2-19.2.19.jar`, `Cobblemon-neoforge-1.8.4+1.21.1.jar`, `cobblemon_dynamics-1.0.0-beta.1+mc1.21.1.jar`, `create-1.21.1-6.0.10.jar`, `guideme-21.1.1.jar`, `kotlinforforge-5.10.0-all.jar`. Each pinned jar has its SHA-512 from `mods.json`, and the mod jar is the same file as in `build/libs/`. |

The perf run measured the server time of Dynamics per tick, with 64 Working workers over 6,000 ticks. The mean was 0.181 ms (CD-PERF-01), and the p95 was 0.282 ms (CD-PERF-02). The budgets are 1.0 ms for the mean and 2.0 ms for the p95. Dynamics wrote 0 log lines after the warm-up (CD-PERF-05).

The spec sheet has 372 `MUST` and `MUST NOT` rows. 360 of the 360 rows with automated tests pass. 51 rows have a play-test part, and 6 of them have no automated test. 5 rows wait for the approver's art review (CD-ART-02, CD-ART-03, CD-ART-04, CD-ART-10, CD-ART-12). 2 rows stay open after Phase 2 (CD-REL-04, CD-REL-15). 0 automated rows have no passing test.

<!-- gate-report summary end -->

## For the approver

### The play-test client

`py scripts/testpack/testpack.py client` fills the folder `C:\Users\admin\src\cobblemon-dynamics\run\testpack\client\mods\`
with six jars:

- `Cobblemon-neoforge-1.8.4+1.21.1.jar`
- `create-1.21.1-6.0.10.jar`
- `appliedenergistics2-19.2.19.jar`
- `guideme-21.1.1.jar`
- `kotlinforforge-5.10.0-all.jar`
- `cobblemon_dynamics-1.0.0-beta.1+mc1.21.1.jar`, the mod, built from the commit of the run summary

The test pack sets up this `mods` folder only. It installs no Minecraft or NeoForge client, and it makes no launcher
instance. The play-test therefore needs a new instance in a launcher. Never use or change an existing instance, such as
the COHO modpack.

To launch the client with the Modrinth App:

1. In the left bar of the Modrinth App, click the plus button to make a new instance.
2. Set the name to `Cobblemon Dynamics Phase 2 play-test`, the loader to NeoForge and the game version to 1.21.1.
3. In the advanced options, set the NeoForge version to 21.1.265.
4. Create the instance.
5. In the menu of the new instance, open its folder.
6. If the instance folder has no `mods` folder, make one.
7. Copy the six jars of `C:\Users\admin\src\cobblemon-dynamics\run\testpack\client\mods\` into the `mods` folder of the
   instance.
8. Write the line `onboardAccessibility:false` into `options.txt` in the instance folder. Do this before the first start,
   or the game first opens the accessibility screen, which you can also click through.
9. Click **Play**.

The CurseForge app and GDLauncher work the same way: a new Minecraft 1.21.1 instance with NeoForge 21.1.265, with the six
jars in its `mods` folder.

### The review spritesheet and the review record

`py scripts/art.py review` writes the review spritesheet
`C:\Users\admin\src\cobblemon-dynamics\build\art-review\spritesheet.png`. It also refreshes the rows of the review record,
[`docs/testing/art-review.md`](art-review.md). The sheet shows each sprite at 8x and at 1x, on a light and on a dark
inventory slot: the Work Leash, the creative tab icon, every UI sprite and the Work Stamina bars as the game draws them. It
also shows each block item from above, for reference.

The review record has one row for each asset, with its source and the SHA-256 of that source. Give each row a verdict:
`approved`, or `changes` with a note. A row whose source changes goes back to `pending`. A public tag publishes only when
every row is `approved` at its current SHA-256 (CD-ART-12).

The record also lists your logo files, `C:\Users\admin\src\cobblemon-dynamics\art\logo\logo-256.png` and
`C:\Users\admin\src\cobblemon-dynamics\art\logo\icon-512.png` (CD-ART-05). The sheet does not show them, so open the two
files. They need your approval too, because CD-ART-10 asks for an approval of every asset of CD-ART-01 to CD-ART-05.

### Steps

1. Read the run summary above. It names the commit of the run, and it holds the test counts and the perf numbers.
2. Set up the client as above.
3. Do steps 7 to 9 of the [play-test setup](manual-checklist.md#client) of the manual checklist: a creative flat world
   with cheats on, no Pokémon spawning, and your test Pokémon.
4. Do the checks of the [play-test note](#play-test-note): check 20, checks 44 to 59, and the Phase 1 checks 6, 9, 10,
   14, 15 and 22, which Phase 2 code touches. The [manual checklist](manual-checklist.md) holds each check. Check 59
   shows the end of a Quick Work order when its owner starts a battle (CD-QWK-21).
5. Open the review spritesheet and the two logo files.
6. Give each row of the review record your verdict.
7. Record each result of the checks in the play-test note: pass, fail with what you saw, or blocked with the reason.

## Known issues and open decisions

These items stay open at the gate. Each item names its source.

- **Floral Healing (CD-HEAL-16 against CD-FX-08 and CD-FX-09).** When Cobblemon's data holds the action effect
  `cobblemon:floralhealing`, a heal runs it, and Cobblemon's own keyframes pick the players that get the effect. Its
  particle keyframe reaches every player within 200 blocks of the healer or the ally, and the effect can play sounds.
  CD-FX-08 sends effects only within 64 blocks, and CD-FX-09 keeps heals silent. Cobblemon 1.8.4 has no such effect, so
  the path cannot run inside the declared range `[1.8.4,1.9)`. Before the range admits a Cobblemon version that has the
  effect, the path must send only to players within 64 blocks and play no sound. Source:
  [the multiplayer audit](multiplayer-audit.md#open-item), row 46 and the ruling of 2026-11-11.
- **Quick Work review finding 4.** A species whose Brain cannot hold a work order gets no message that names the cause.
  When `BrainBridge.holdsOrder` fails, the work engine releases the claim. The order then ends with "{Pokémon} stopped
  working: {target} can no longer be worked", which blames the target. The server log gets one warning for the species.
  Source: the fourth finding of the independent review in
  [pull request #22](https://github.com/HonorCodes/cobblemon-dynamics/pull/22).
- **Quick Work and claim mods.** In the build of this report, Quick Work makes no block click, because the server traces
  the owner's view. Claim mods that only cancel block clicks, such as FTB Chunks and Open Parties and Claims, therefore do
  not see Quick Work. Before an order starts, Quick Work asks `ServerPlayer.mayInteract`, which checks spawn protection
  and the world border, and a server can set `quickWork.enabled = false`. The approver ruled on 2026-11-11 that Quick
  Work must respect claims (R64). The [land claims plan](../plans/2026-11-11-phase-2-quick-work-claims.md) adds the
  checks of FTB Chunks and Open Parties and Claims (CD-QWK-26 to CD-QWK-35), and its last task runs this report again.
  COHO can then set `quickWork.enabled = true`. Source:
  [Amendment 1 of ADR-0013](../adr/0013-quick-work-for-party-pokemon.md#amendment-1-land-claims-2026-11-11) and input
  R64 of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md).
- **Pasture work areas and land claims.** The workers of a Work Mode pasture do not ask claim mods. The work area of a
  pasture can reach into the land claim of another player, and its workers then work the machines there, as in Phase 1.
  The Work Leash obeys a cancelled click (CD-SEC-11), so a binding still needs the binder's own click on the block. By the
  coordinator's ruling of 2026-11-11, under the approver's delegation, the work areas of pastures stay out of the scope of
  the land claim work. The spec sheet lists them among its non-goals. Source: ruling 6 of input R64 of the
  [spec sheet](../plans/2026-11-09-v1-spec-sheet.md).
- **CD-QWK-21 has no GameTest.** Cobblemon 1.8.4 clears a battle id that names no live battle on the entity's next tick,
  and a test battle against a fake owner ends in one tick. By the coordinator's ruling of 2026-11-11, under the approver's
  delegation, the unit tests of the trace range rule and the end rule in `QuickWorkRulesTest` check the row. Check 59 of
  the manual checklist adds the Demo, a battle that ends a running order in the play-test. The requirement of the row is
  unchanged. Source: the corrections list of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md), and Step 1 of
  [M8 Task 7](../plans/2026-11-10-phase-2-m8-art-gate.md#task-7-the-end-of-phase-pass-the-checklist-and-the-guide).
- **CD-SEC-04's boundary.** Cobblemon 1.8.4 keeps a player's pasture link only while the player is closer than 10.0
  blocks. `WorkModeService.request` accepts a request from exactly 10.0 blocks, which the CD-SEC-04 GameTest checks. A
  toggle from the panel at exactly 10.0 blocks still fails, because Cobblemon has dropped the link. That is stricter than
  the row, never looser. Source: [the multiplayer audit](multiplayer-audit.md#rulings-of-2026-11-11), ruling 4 and
  row 13, and the CD-SEC-04 line of the [rehydration note](../plans/2026-11-09-phase-1-rehydration.md).
- **The `cdHeal17` timeout.** `HealerTests.cdHeal17SpentHealerGoesToBedOrRestsInPlace` timed out once at 1,200 ticks with
  no failed assertion, then passed in two runs in a row. The test does not reach the code that M8 Task 7 changed, and a
  healer sees only the allies of its own pasture. Watch the test in the gate run. Source: Step 1 of
  [M8 Task 7](../plans/2026-11-10-phase-2-m8-art-gate.md#task-7-the-end-of-phase-pass-the-checklist-and-the-guide).
- **Dependabot alerts.** On 2026-11-11, Dependabot shows 30 open alerts on `settings.gradle`. They name libraries of
  Minecraft and NeoForge, such as guava, log4j, lz4, netty, jline, plexus-utils and commons-lang3. The `Dependency graph`
  job submits these libraries with the compile classpath, and they have development scope. The mod ships none of them.
  Only `jarJar` libraries get runtime scope, and `build.gradle` declares none. Dependency review fails only on runtime
  scope. Source: `.github/workflows/security.yml`, and the M1 row of the
  [rehydration note](../plans/2026-11-09-phase-1-rehydration.md).

## Play-test note

The approver writes this section. For each check, record pass, fail with what you saw, or blocked with the reason. The
verdict on each asset goes into the [art review record](art-review.md), not here.

- Date: pending
- Approver: pending
- Launcher and instance: pending
- Result: pending

The table has one line for each check of the [manual checklist](manual-checklist.md) that the Phase 2 play-test covers.

| Check | Row | Result | What you saw |
| --- | --- | --- | --- |
| 6 | CD-UI-01 | pending | |
| 9 | CD-UI-04 | pending | |
| 10 | CD-UI-07 | pending | |
| 14 | CD-ASG-09 | pending | |
| 15 | CD-ASG-14 | pending | |
| 20 | CD-LSH-20 | pending | |
| 22 | CD-UI-06 | pending | |
| 44 | CD-ASG-09 | pending | |
| 45 | CD-BRN-01 | pending | |
| 46 | CD-SUP-02 | pending | |
| 47 | CD-GEN-08 | pending | |
| 48 | CD-GEN-11 | pending | |
| 49 | CD-GEN-09 | pending | |
| 50 | CD-UI-16 | pending | |
| 51 | CD-UI-17 | pending | |
| 52 | CD-FAN-14 | pending | |
| 53 | CD-FAN-16 | pending | |
| 54 | CD-TLW-07 | pending | |
| 55 | CD-ART-02 | pending | |
| 56 | CD-ART-03 | pending | |
| 57 | CD-ART-04 | pending | |
| 58 | CD-ART-07 | pending | |
| 59 | CD-QWK-21 | pending | |

## Art review

The approver's verdicts on the art go into the [art review record](art-review.md), one verdict for each asset at its
current source. `py scripts/art.py check --release` reads the record. Until the record approves every asset, the rows
CD-ART-02, CD-ART-03, CD-ART-04, CD-ART-10 and CD-ART-12 show `art review` in the Rows section.

## How this report is made

Run these commands on one commit, in this order, with `JAVA_HOME` set to a JDK 21 and one JVM at a time. Save the output
of each command to a file.

1. Delete `build/test-results/test`, so that the JUnit XML reports come from this run.
2. Run `./gradlew build runGameTestServer runGameTestServerCore runGameTestServerPerf --console=plain`.
3. Run `py scripts/testpack/testpack.py` with `smoke`, `deps`, `compat`, `reload` and `restart`, one command at a time.
4. Run `py scripts/art.py check --references auto --release`.
5. Run `py scripts/testpack/testpack.py client`.
6. Run `py scripts/gate_report.py --phase 2 --gradle-log <Gradle output> --art-log <art output>`, with one
   `--testpack-log <output>` for each command of step 3.

`scripts/gate_report.py` follows these rules:

- It reads the spec sheet, the JUnit XML reports in `build/test-results/test/` and the GameTest sources. It reads the log
  of each GameTest run: `logs/latest.log` in `run/gametest/`, `run/gametest-core/` and `run/gametest-perf/`. It also
  reads the deferred lists of M5 Task 9 and M8 Task 7, the manual checklist, `scripts/testpack/README.md`, `testpack.py`,
  `build.gradle`, `scripts/art.py` and the play-test note of the [Phase 1 report](phase-1-gate.md). Last, it reads the
  saved output of the Gradle command, of each test pack command and of the art checks.
- The report holds every `MUST` and `MUST NOT` row of the sheet, the Phase 1 rows included, because CD-REL-14 needs them
  all to pass on one commit.
- The GameTest runs write no JUnit-style report. A run's log names only the failed tests and ends with
  `All N required tests passed`. So the script takes the test names from the `@GameTest` methods of each run, and it
  checks that their number equals N.
- A GameTest cites a row when its name, its KDoc or one of its assertion messages cites the row. The assertion messages
  of the private helpers that the test calls count too. The deferred lists of M5 Task 9 and M8 Task 7 add the tests that
  they name for a row.
- The Gradle tasks `verifyJar` and `verifyNoAe2` check CD-PLAT-04, CD-REL-07 and CD-CRY-05. The script reads their
  outcome from the Gradle output.
- The tables of the test pack README give the commands and cases that check CD-CFG-01, CD-CFG-08, CD-CLM-07, CD-WHL-07,
  CD-CRK-08, CD-CMP-06 and CD-CMP-07. The scripted checks of the manual checklist give the cases of the PLAT rows.
- Each `CASE cd-art-NN` line of the art output is the result of the art check of the row CD-ART-NN. CD-ART-02,
  CD-ART-03 and CD-ART-04 have no art case of their own, so they take `cd-art-10`, the check of the review record. While
  the art check of CD-ART-02, CD-ART-03, CD-ART-04, CD-ART-10 or CD-ART-12 fails, the row shows `art review`, not `fail`.
- The script marks CD-REL-04 and CD-REL-15 open after Phase 2, and CD-REL-14 play-test. CD-REL-13 passes when the
  play-test note of the Phase 1 report has a date. These marks come from the plan, not from a test.
- The script refuses to write the report when tracked files differ from the commit. It counts a log that is older than
  the commit as stale, and a stale log gives no pass.

<!-- gate-report rows: written by scripts/gate_report.py; edits inside this block are lost -->

## Rows

The table has one line for each `MUST` and `MUST NOT` row of the spec sheet, in the order of the sheet.

- Tests: `Class` ×n is n unit tests of that class whose names cite the row. `Class.method` is a GameTest whose name, KDoc or assertion messages cite the row, or which the deferred list of M5 Task 9 or M8 Task 7 names for the row. Test pack names a command of `scripts/testpack/testpack.py`, with the cases that the test pack's README or the manual checklist gives for the row. Gradle names a check task of `./gradlew build`. Art check names a case of `py scripts/art.py check --references auto --release`. The play-test note of a report is the record of an earlier gate. Checklist n is check n of `docs/testing/manual-checklist.md`.
- Result: `pass` means that every test of the row passed. `play-test` means that the row's evidence is a Demo or a checklist line, which the approver checks in the play-test. `open (Mn)` means that the last part of the row comes after the Phase 2 gate, in milestone Mn. `art review` means that the art check of the row fails until the approver approves every asset in the review record, `docs/testing/art-review.md`. The section "Rows without a passing test" lists each row with `fail` or `no test`.

| Row | Tests | Result | Commit |
| --- | --- | --- | --- |
| CD-PLAT-01 | `ModMetadataTest`; test pack `deps` (`neoforge-below-floor`, `neoforge-floor`); checklist 1 | pass | `ceec134` |
| CD-PLAT-02 | `ModMetadataTest`; test pack `deps` (`cobblemon-below-floor`); test pack `smoke`; checklist 2 | pass | `ceec134` |
| CD-PLAT-03 | `ModMetadataTest`; test pack `deps` (`kff-floor`); checklist 3 | pass | `ceec134` |
| CD-PLAT-04 | Gradle `verifyJar` | pass | `ceec134` |
| CD-PLAT-05 | `ModMetadataTest`; `RepositoryFilesTest`; `CoreTests.cdCfg10ItemRolesNeedNoCreate`; `CoreTests.cdPlat05LoadsWithoutCreateAndAe2`; test pack `deps` (`ae2-below-floor`, `create-below-floor`, `without-create-ae2`); checklist 4 | pass | `ceec134` |
| CD-PLAT-06 | `ModMetadataTest`; checklist 5 | pass, play-test | `ceec134` |
| CD-PLAT-07 | `ModMetadataTest` | pass | `ceec134` |
| CD-PLAT-08 | `ModMetadataTest` | pass | `ceec134` |
| CD-WMD-01 | `WorkModeTests.cdWmd01NewPastureIsOff` | pass | `ceec134` |
| CD-WMD-02 | `WorkModeTests.cdWmd02WorkModeSurvivesSaveAndLoad` | pass | `ceec134` |
| CD-WMD-03 | `WorkModePolicyTest`; `PayloadTests.cdSec05Wmd03PlacerPayloadsSetTheRequestedState`; `WorkModeTests.cdWmd03PlacerTogglesWithinOneTick` | pass | `ceec134` |
| CD-WMD-04 | `WorkModePolicyTest`; `PayloadTests.cdSec06Wmd04TwoOperatorsInOneTick`; `WorkModeTests.cdWmd04OperatorTogglesAnyPasture` | pass | `ceec134` |
| CD-WMD-05 | `ClaimRegistryTest`; `AssignmentTests.cdWmd05WorkModeOffReleasesEveryClaim` | pass | `ceec134` |
| CD-WMD-06 | `WorkModeTests.cdWmd06OffPastureGivesNoClaims` | pass | `ceec134` |
| CD-WMD-07 | `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding` | pass | `ceec134` |
| CD-ASG-01 | `AssignmentTests.cdAsg01OnlyOwnedHealthyPastured`; `WheelRowTests.cdAsg01OnlyAHealthyPasturedWaterTypeClaimsTheWheel` | pass | `ceec134` |
| CD-ASG-02 | `RoleSelectionTest` | pass | `ceec134` |
| CD-ASG-03 | `AssignerTest`; `AssignmentTests.cdAsg03NearestFurnaceWins` | pass | `ceec134` |
| CD-ASG-04 | `AssignerTest`; `WheelRowTests.cdAsg04AzumarillLeavesTheDryWheelAlone` | pass | `ceec134` |
| CD-ASG-05 | `AssignerTest`; `PumpTests.cdAsg05UnleashedWaterTypeLeavesAnEmptyTankAlone` | pass | `ceec134` |
| CD-ASG-06 | `BlockScanCursorTest`; `ScanSchedulerTest` ×3 | pass | `ceec134` |
| CD-ASG-07 | `BlockScanCursorTest` | pass | `ceec134` |
| CD-ASG-08 | `WorkAreaTest`; `AssignmentTests.cdAsg08HorizontalWorkAreaEdge`; `AssignmentTests.cdAsg08VerticalWorkAreaEdge` | pass | `ceec134` |
| CD-ASG-09 | `ReachTest` ×4; `WorkSpotsTest` ×12; `AssignmentTests.cdAsg08VerticalWorkAreaEdge`; `QuickWorkTests.cdQwk10FurnaceWithNoSpotGivesNoOrder`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack`; `WideWorkerTests.cdAsg09WideWorkerGetsASpotItsBodyFits`; `WorkSpotTests.cdAsg09NoClaimWhileTheNavigationCannotPlan`; `WorkSpotTests.cdAsg09SpotIsBesideAFurnaceOnTheFloor`; `WorkSpotTests.cdAsg09StokerWorksItsFurnaceFromBesideIt`; `WorkSpotTests.cdAsg09UnreachablePocketGivesWayToAReachableSpot`; checklist 14; checklist 44 | pass, play-test | `ceec134` |
| CD-ASG-10 | `BuiltInRoleFilesTest` ×2; `WorkSpotsTest` ×2 | pass | `ceec134` |
| CD-ASG-11 | `ArrivalTest` ×2; `WorkerMachineTest`; `AssignmentTests.cdAsg11WorkingWithinOneTickOfArrival` | pass | `ceec134` |
| CD-ASG-12 | `WorkerMachineTest` ×2; `AssignmentTests.cdAsg12StandbyKeepsClaimAndResumes`; `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `ceec134` |
| CD-ASG-13 | `WorkerMachineTest`; `AssignmentTests.cdAsg13UnreachableTargetTimesOut` | pass | `ceec134` |
| CD-ASG-14 | `WorkSpotsTest`; `WorkerMachineTest`; checklist 15 | pass, play-test | `ceec134` |
| CD-ASG-15 | `AssignmentTests.cdAsg15LeavingThePastureReleasesTheClaim` | pass | `ceec134` |
| CD-ASG-16 | `DefaultPriorityTest` ×2; `RoleSelectionTest` | pass | `ceec134` |
| CD-ASG-17 | `WorkerMachineTest` | pass | `ceec134` |
| CD-ASG-18 | `RestMachineTest` ×2; `WorkerMachineTest` | pass | `ceec134` |
| CD-ASG-19 | `AssignerTest`; `WorkerMachineTest`; `WheelRowTests.cdAsg19WheelDriverMovesToADryWheel` | pass | `ceec134` |
| CD-ASG-20 | `WorkerMachineTest`; `LeashClaimTests.cdAsg20LeashedWorkerStaysOnItsBoundTarget` | pass | `ceec134` |
| CD-CLM-01 | `ClaimRegistryTest` ×2 | pass | `ceec134` |
| CD-CLM-02 | `ClaimRegistryTest` | pass | `ceec134` |
| CD-CLM-03 | `ClaimRegistryTest` | pass | `ceec134` |
| CD-CLM-04 | `InventoryBlocksTest` ×2; `WheelDriverTests.cdClm04LargeWheelIsOneTarget` | pass | `ceec134` |
| CD-CLM-05 | `LeashClaimTests.cdClm05BindTakesTheTargetFromAnAutoWorker` | pass | `ceec134` |
| CD-CLM-06 | `ClaimRegistryTest` ×2; `LeashIndexTest`; `AssignmentTests.cdWmd05WorkModeOffReleasesEveryClaim`; `LeashClaimTests.cdClm06ReservedTargetWaitsForItsWorker`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `ceec134` |
| CD-CLM-07 | `AssignmentTests.cdClm07ClaimsAreNotSaved`; test pack `restart`; checklist 13 | pass, play-test | `ceec134` |
| CD-CLM-08 | `AssignmentTests.cdClm08BrokenReplacedUnloaded` | pass | `ceec134` |
| CD-CLM-09 | `ClaimRegistryTest`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `ceec134` |
| CD-LSH-01 | `LeashTests.cdLsh01SelectsOwnedPasturedPokemon` | pass | `ceec134` |
| CD-LSH-02 | `LeashTests.cdLsh02ClickIsCancelledBeforeCobblemon`; checklist 16 | pass, play-test | `ceec134` |
| CD-LSH-03 | `BindRulesTest`; `LeashTests.cdLsh03BindsFurnaceAndWorksIt`; `WheelRowTests.cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels` | pass | `ceec134` |
| CD-LSH-04 | `LeashTests.cdLsh04FurnaceScreenStaysShut`; checklist 17 | pass, play-test | `ceec134` |
| CD-LSH-05 | `BindRulesTest` ×3; `GeneratorTests.cdLsh05PikachuOnBattery`; `LeashTests.cdLsh05Lsh06RefusalsAndTheBoundMessage` | pass | `ceec134` |
| CD-LSH-06 | `LeashTests.cdLsh05Lsh06RefusalsAndTheBoundMessage`; `WheelRowTests.cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels`; checklist 18 | pass, play-test | `ceec134` |
| CD-LSH-07 | `BindingTest`; `LeashTests.cdLsh07BindingSurvivesRestartAndToggle`; checklist 24 | pass, play-test | `ceec134` |
| CD-LSH-08 | `LeashResetTests.cdLsh08RecallAndTargetLoss` | pass | `ceec134` |
| CD-LSH-09 | `LeashClaimTests.cdLsh09UnloadKeepsTheBinding` | pass | `ceec134` |
| CD-LSH-10 | `LeashTests.cdLsh10SneakClickClearsTheBinding` | pass | `ceec134` |
| CD-LSH-11 | `LeashIndexTest`; `LeashTests.cdLsh11RebindReplacesTheBinding`; `WheelRowTests.cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels` | pass | `ceec134` |
| CD-LSH-12 | `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack`; `RestTests.cdLsh12BindingMadeWhileTiredWaitsUntilFull` | pass | `ceec134` |
| CD-LSH-13 | `OfflineMessagesTest` ×2; `LeashResetTests.cdLsh08RecallAndTargetLoss`; `LeashResetTests.cdLsh13Lsh14EvolutionWithNoFittingRoleClearsTheBinding`; `LeashResetTests.cdLsh24MovingToAnotherPastureClearsTheBinding`; `LeashResetTests.cdLsh25ReleaseClearsTheBinding`; `LeashResetTests.cdLsh26TradeClearsTheBindingData`; `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding`; `LeashTests.cdLsh10SneakClickClearsTheBinding`; `LeashTests.cdLsh11RebindReplacesTheBinding`; checklist 19 | pass, play-test | `ceec134` |
| CD-LSH-14 | `LeashResetTests.cdLsh13Lsh14EvolutionWithNoFittingRoleClearsTheBinding`; `WheelRowTests.cdLsh14EvolutionWithoutWaterClearsTheWheelBinding` | pass | `ceec134` |
| CD-LSH-15 | `WorkLeashRecipeTest`; `LeashTests.cdLsh15RecipeCraftsOneLeash` | pass | `ceec134` |
| CD-LSH-16 | `BindRulesTest` ×2; `BuiltInRoleFilesTest`; `DefaultPriorityTest`; `RoleSelectionTest` ×3; `CreateLeashTests.cdLsh16SlowbroOnABasinBindsAsPump` | pass | `ceec134` |
| CD-LSH-17 | `BindRulesTest` ×2; `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination` | pass | `ceec134` |
| CD-LSH-18 | `BindRulesTest` ×2; `BindingTest`; `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination` | pass | `ceec134` |
| CD-LSH-19 | `PendingSourceTest`; `LeashTests.cdLsh19AirClickAndTimeoutClearThePendingSource` | pass | `ceec134` |
| CD-LSH-20 | `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination`; checklist 20 | pass, play-test | `ceec134` |
| CD-LSH-21 | `LeashClaimTests.cdLsh21CannotReachMessage`; checklist 21 | pass, play-test | `ceec134` |
| CD-LSH-22 | `CreateLeashTests.cdLsh22WrenchPickupClearsBinding` | pass | `ceec134` |
| CD-LSH-23 | `CreateLeashTests.cdLsh23ContraptionClearsBinding` | pass | `ceec134` |
| CD-LSH-24 | `LeashResetTests.cdLsh24MovingToAnotherPastureClearsTheBinding` | pass | `ceec134` |
| CD-LSH-25 | `LeashResetTests.cdLsh25ReleaseClearsTheBinding` | pass | `ceec134` |
| CD-LSH-26 | `LeashResetTests.cdLsh26TradeClearsTheBindingData` | pass | `ceec134` |
| CD-LSH-27 | `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding` | pass | `ceec134` |
| CD-AI-01 | `BrainTests.cdAi01BrainListsWorkActivity` | pass | `ceec134` |
| CD-AI-02 | `BrainTests.cdAi02WorkingWorkerHoldsItsSpot` | pass | `ceec134` |
| CD-AI-03 | `BrainTests.cdAi03ReleaseRestoresActivitiesWithin20Ticks` | pass | `ceec134` |
| CD-AI-04 | `BrainTests.cdAi04RemakeBrainRestoresWorkNextTick` | pass | `ceec134` |
| CD-AI-05 | `LookMathTest` ×3; `BrainTests.cdAi05FacesTheTarget` | pass | `ceec134` |
| CD-AI-06 | `BrainTests.cdAi06NonWorkersKeepCobblemonActivities` | pass | `ceec134` |
| CD-AI-07 | `RestTests.cdAi07NightSleepEndsWhenWorkStarts`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `ceec134` |
| CD-AI-08 | `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `ceec134` |
| CD-AI-09 | `BrainTests.cdAi09LookTaskRunsInsideWork` | pass | `ceec134` |
| CD-FX-01 | `BedrockAnimationsTest` ×2; `BuiltInRoleFilesTest`; `EffectClockTest` ×4; `RoleCodecTest` ×2; `EffectPacketTests.cdFx01AnimationPlaysToItsEndAndTheWorkerIdlesBeforeTheNext`; checklist 43 | pass, play-test | `ceec134` |
| CD-FX-02 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-FX-03 | `BedrockAnimationsTest`; `RoleCodecTest`; `EffectPacketTests.cdFx01AnimationPlaysToItsEndAndTheWorkerIdlesBeforeTheNext` | pass | `ceec134` |
| CD-FX-04 | `BuiltInRoleFilesTest` ×16; `ParticleShotsTest`; `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; `EffectTests.cdFx07FocusLeavesWithWorking`; checklist 30 | pass, play-test | `ceec134` |
| CD-FX-05 | `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; checklist 31 | pass, play-test | `ceec134` |
| CD-FX-06 | `EffectTests.cdFx06FocusIsNeverSaved` | pass | `ceec134` |
| CD-FX-07 | `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; `EffectTests.cdFx07FocusLeavesWithWorking` | pass | `ceec134` |
| CD-FX-08 | `RecipientsTest` ×2 | pass | `ceec134` |
| CD-FX-09 | `BedrockAnimationsTest` ×3; `ParticleFilesTest`; `RoleCodecTest` ×2; `SilenceTest` ×2; `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer`; checklist 35 | pass, play-test | `ceec134` |
| CD-FX-10 | `SleepEffectTest`; `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer`; checklist 25 | pass, play-test | `ceec134` |
| CD-FX-11 | `ParticleCatalogTest`; `ParticleShotsTest`; `EffectTests.cdFx11CatalogReadsCobblemonParticles` | pass | `ceec134` |
| CD-STA-01 | `StaminaMathTest` | pass | `ceec134` |
| CD-STA-02 | `StaminaMathTest` | pass | `ceec134` |
| CD-STA-03 | `StaminaMathTest` | pass | `ceec134` |
| CD-STA-04 | `StaminaStoreTest` ×2; `StaminaStoreTests.cdSta04And05StaminaStartsFullAndFollowsThePokemon`; checklist 26 | pass, play-test | `ceec134` |
| CD-STA-05 | `StaminaStoreTest` ×2; `StaminaStoreTests.cdSta04And05StaminaStartsFullAndFollowsThePokemon` | pass | `ceec134` |
| CD-STA-06 | `StaminaMathTest` ×2 | pass | `ceec134` |
| CD-STA-07 | `StaminaMathTest` ×2 | pass | `ceec134` |
| CD-STA-08 | `StaminaMathTest` ×2 | pass | `ceec134` |
| CD-STA-09 | `AssignerTest`; `RestMachineTest` | pass | `ceec134` |
| CD-STA-10 | `StaminaMathTest` ×2 | pass | `ceec134` |
| CD-STA-11 | `OfflineRecoveryTest` ×3; `StaminaStoreTest`; `StaminaStoreTests.cdSta11PcTimeRecoversAtBaseRate` | pass | `ceec134` |
| CD-STA-12 | `StaminaStoreTest`; `StaminaStoreTests.cdSta12TimeCommandsChangeNothing` | pass | `ceec134` |
| CD-STA-13 | `RestMachineTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack`; `RestTests.cdSta13TiredAutoWorkerFreesItsMachineForTheNextScan` | pass | `ceec134` |
| CD-STA-14 | `RestMachineTest`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `ceec134` |
| CD-STA-15 | `ViewSyncTest` ×5 | pass | `ceec134` |
| CD-BED-01 | `BedContentFilesTest` ×3; `BedTierTest` ×3; `BedContentTests.cdBed01RegistryListsFourBedsWithTheirTiers` | pass | `ceec134` |
| CD-BED-02 | `BedContentFilesTest` ×4; `BedContentTests.cdBed02EachBedCraftsFromTheBedBelow` | pass | `ceec134` |
| CD-BED-03 | `BedTierTest`; `StaminaMathTest` ×2; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `ceec134` |
| CD-BED-04 | `StaminaMathTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `ceec134` |
| CD-BED-05 | `BedSelectorTest` ×3; `RestMachineTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `ceec134` |
| CD-BED-06 | `BedSelectorTest` | pass | `ceec134` |
| CD-BED-07 | `RestTests.cdBed07OnlyABedInsideTheWorkAreaIsClaimed` | pass | `ceec134` |
| CD-BED-08 | `BedSelectorTest`; `RestMachineTest`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `ceec134` |
| CD-BED-09 | `BedSelectorTest`; `RestTests.cdBed09WailordSleepsOnAHayBed` | pass | `ceec134` |
| CD-BED-10 | `RestMachineTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed09WailordSleepsOnAHayBed` | pass | `ceec134` |
| CD-BED-11 | `RestMachineTest`; `RestTests.cdBed11BrokenBedMeansRestInPlace` | pass | `ceec134` |
| CD-BED-12 | `RestMachineTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `ceec134` |
| CD-BED-13 | `RestTests.cdBed13OnlyTiredWorkersOfWorkModePasturesGoToBed` | pass | `ceec134` |
| CD-BED-14 | `RestTests.cdBed14SleeperClaimsABedAfterRestart`; checklist 27 | pass, play-test | `ceec134` |
| CD-HEAL-01 | `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `ceec134` |
| CD-HEAL-02 | `HealTargetsTest` | pass | `ceec134` |
| CD-HEAL-03 | `HealTargetsTest` | pass | `ceec134` |
| CD-HEAL-04 | `HealTransactionTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `ceec134` |
| CD-HEAL-05 | `HealTransactionTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `ceec134` |
| CD-HEAL-06 | `HealTargetsTest`; `HealerTests.cdHeal06OnlyNonFairyAlliesOfTheHealersPasture` | pass | `ceec134` |
| CD-HEAL-07 | `HealTargetsTest`; `HealerTests.cdHeal07NearestTiredAllyFirst` | pass | `ceec134` |
| CD-HEAL-08 | `HealTargetsTest` ×2 | pass | `ceec134` |
| CD-HEAL-09 | `ClaimRegistryTest`; `HealTargetsTest`; `HealerTests.cdHeal09OneHealerPerAlly` | pass | `ceec134` |
| CD-HEAL-10 | `HealTransactionTest` ×2; `RestMachineTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `ceec134` |
| CD-HEAL-11 | `HealTargetsTest`; `RestMachineTest`; `HealerTests.cdHeal11HealCancelsAtNoCost` | pass | `ceec134` |
| CD-HEAL-12 | `StaminaMathTest` ×2 | pass | `ceec134` |
| CD-HEAL-13 | `StaminaMathTest` ×2 | pass | `ceec134` |
| CD-HEAL-14 | `MuseTests.cdHeal14LeashedMuseNeverHealsATiredAlly` | pass | `ceec134` |
| CD-HEAL-15 | `BedrockAnimationsTest`; `HealEffectTest`; `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer`; checklist 28 | pass, play-test | `ceec134` |
| CD-HEAL-16 | `HealEffectTest`; `HealerTests.cdHeal16FloralHealingRunsWithTheHealerAsUser` | pass | `ceec134` |
| CD-HEAL-17 | `RestMachineTest`; `HealerTests.cdHeal17SpentHealerGoesToBedOrRestsInPlace` | pass | `ceec134` |
| CD-WHL-01 | `WheelDriverTests.cdWhl01Whl02SmallAndLargeWheelsTurnAtCreateValues` | pass | `ceec134` |
| CD-WHL-02 | `WheelDriverTests.cdWhl01Whl02SmallAndLargeWheelsTurnAtCreateValues` | pass | `ceec134` |
| CD-WHL-03 | `WheelDriverTests.cdWhl03RealWaterMeansStandby` | pass | `ceec134` |
| CD-WHL-04 | `DriveNbtTest`; `DriveBehaviourTests.cdWhl04NewWheelsStartPositive` | pass | `ceec134` |
| CD-WHL-05 | `FlipRulesTest`; `WheelDriverTests.cdWhl05BinderFlips` | pass | `ceec134` |
| CD-WHL-06 | `WheelDriverTests.cdWhl06TakeoverKeepsDirection` | pass | `ceec134` |
| CD-WHL-07 | `DriveNbtTest`; `CreateFlipTests.cdWhl07Crk08FlipsByBoundWorkersSurviveARestart`; `DriveBehaviourTests.cdWhl07DirectionSurvivesReload`; test pack `restart`; checklist 33 | pass, play-test | `ceec134` |
| CD-WHL-08 | `WheelDriverTests.cdWhl08StopWithinTwoTicks` | pass | `ceec134` |
| CD-WHL-09 | `DriveNbtTest`; `WheelDriverTests.cdWhl09ClientSeesCapacity`; checklist 32 | pass, play-test | `ceec134` |
| CD-WHL-10 | `MixinGateTest` ×2; `MixinStateTest` | pass | `ceec134` |
| CD-GEN-01 | `ChargeCellTests.cdGen08PikachuChargesAChargeCellToFull`; `GeneratorTests.cdGen01CapabilityTargets` | pass | `ceec134` |
| CD-GEN-02 | `FeCurveTest` ×2; `ChargeCellTests.cdGen08PikachuChargesAChargeCellToFull` | pass | `ceec134` |
| CD-GEN-03 | `FeCurveTest` ×3 | pass | `ceec134` |
| CD-GEN-04 | `NearestFacesTest` ×3; `GeneratorTests.cdGen04NearestAcceptingFace` | pass | `ceec134` |
| CD-GEN-05 | `EnergyPortTest` ×2 | pass | `ceec134` |
| CD-GEN-06 | `EnergyPortTest`; `GeneratorTests.cdGen06FullBatteryMeansStandby` | pass | `ceec134` |
| CD-GEN-07 | `GeneratorTests.cdGen07Ae2RatioAtLv50` | pass | `ceec134` |
| CD-GEN-08 | `ChargeCellRulesTest` ×2; `ChargeCellTests.cdGen08PikachuChargesAChargeCellToFull`; checklist 47 | pass, play-test | `ceec134` |
| CD-GEN-09 | `ChargeCellRulesTest` ×7; `ChargeCellTests.cdGen09CellSplitsItsFeFairlyBetweenThreeBatteries`; `ChargeCellTests.cdGen09TwoCellsSettleAtOneLevelAndStop`; checklist 49 | pass, play-test | `ceec134` |
| CD-GEN-10 | `ChargeCellRulesTest` ×2; `ChargeCellTests.cdGen10ComparatorsReadTheCellsFill` | pass | `ceec134` |
| CD-GEN-11 | `ChargeCellRulesTest`; `ChargeCellTests.cdGen11BrokenCellKeepsItsFe`; checklist 48 | pass, play-test | `ceec134` |
| CD-GEN-12 | `ChargeCellTests.cdGen12CellCraftsFromCopperRedstoneAndAThunderStone` | pass | `ceec134` |
| CD-STK-01 | `StokerTests.cdStk01FurnaceSmokerAndBlastFurnaceAreClaimed` | pass | `ceec134` |
| CD-STK-02 | `StokerTests.cdStk02EightIronIn1600Ticks`; `StokerTests.cdStk02SmokerCooksEightBeefIn800Ticks` | pass | `ceec134` |
| CD-STK-03 | `StokerTests.cdStk03CoalIsNeverConsumed` | pass | `ceec134` |
| CD-STK-04 | `StokerTests.cdStk04LitIsSetOneTickAfterWorkingStarts` | pass | `ceec134` |
| CD-STK-05 | `StokerTests.cdStk05FullOutputGivesStandby` | pass | `ceec134` |
| CD-STK-06 | `StokerTests.cdStk06ReleaseLetsAFurnaceWithNoFuelGoOut`; `StokerTests.cdStk06ReleaseLetsVanillaBurnTheCoal` | pass | `ceec134` |
| CD-BRN-01 | `HeatGateTest`; `BurnerTests.cdBrn01Brn06KindledBelowLevel50SmoulderingAfterRelease`; checklist 45 | pass, play-test | `ceec134` |
| CD-BRN-02 | `BurnerTests.cdBrn02EmptyBurnerIsNeverClaimed` | pass | `ceec134` |
| CD-BRN-03 | `BurnerTests.cdBrn03NoBasinMeansStandby` | pass | `ceec134` |
| CD-BRN-04 | `BurnerTests.cdBrn04FuelBurnsDownAsNormal` | pass | `ceec134` |
| CD-BRN-05 | `HeatGateTest`; `BurnerTests.cdBrn05BoilerCountsOneHeatBelowLevel50` | pass | `ceec134` |
| CD-BRN-06 | `BurnerTests.cdBrn01Brn06KindledBelowLevel50SmoulderingAfterRelease` | pass | `ceec134` |
| CD-BRN-07 | `MixinGateTest` | pass | `ceec134` |
| CD-SUP-01 | `HeatGateTest`; `BurnerTests.cdSup01Lv50Seething` | pass | `ceec134` |
| CD-SUP-02 | `DefaultPriorityTest`; `HeatGateTest`; `BurnerTests.cdSup02FireTypeNeverClaimsABlazeBurner`; checklist 46 | pass, play-test | `ceec134` |
| CD-SUP-03 | `BurnerTests.cdSup03Sup04Lv49ThenLv50` | pass | `ceec134` |
| CD-SUP-04 | `HeatGateTest`; `BurnerTests.cdSup03Sup04Lv49ThenLv50` | pass | `ceec134` |
| CD-CRK-01 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-CRK-02 | `CrankClockTest` ×2; `CrankerTests.cdCrk02Bal04CrankHolds32RpmFor600Ticks` | pass | `ceec134` |
| CD-CRK-03 | `CrankerTests.cdCrk03Crk05NewCranksTurnAtCreateValues` | pass | `ceec134` |
| CD-CRK-04 | `CrankerTests.cdCrk04ValveHandleIsNotATarget` | pass | `ceec134` |
| CD-CRK-05 | `DriveNbtTest`; `CrankerTests.cdCrk03Crk05NewCranksTurnAtCreateValues`; `DriveBehaviourTests.cdCrk05NewCrankStartsPositive` | pass | `ceec134` |
| CD-CRK-06 | `CrankerTests.cdCrk06BinderFlips` | pass | `ceec134` |
| CD-CRK-07 | `CrankerTests.cdCrk07NoNetworkStillWorks` | pass | `ceec134` |
| CD-CRK-08 | `DriveNbtTest`; `CreateFlipTests.cdWhl07Crk08FlipsByBoundWorkersSurviveARestart`; `DriveBehaviourTests.cdCrk08DirectionSurvivesReload`; test pack `restart`; checklist 34 | pass, play-test | `ceec134` |
| CD-CRY-01 | `CrystalTenderTests.cdCry01RockTypesClaimBuddingAmethystAndCertus` | pass | `ceec134` |
| CD-CRY-02 | `TickClockTest` ×2; `CrystalTenderTests.cdCry02TenderGrowsRealAmethyst` | pass | `ceec134` |
| CD-CRY-03 | `CrystalTenderTests.cdCry03FullClustersMeanStandby` | pass | `ceec134` |
| CD-CRY-04 | `CoreTests.cdCry04CrystalTenderWithoutAe2` | pass | `ceec134` |
| CD-CRY-05 | Gradle `verifyNoAe2` | pass | `ceec134` |
| CD-BRF-01 | `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder` | pass | `ceec134` |
| CD-BRF-02 | `BrewingRulesTest`; `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews` | pass | `ceec134` |
| CD-BRF-03 | `BrewingRulesTest`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews` | pass | `ceec134` |
| CD-BRF-04 | `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews` | pass | `ceec134` |
| CD-BRF-05 | `BrewingRulesTest`; `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews`; `BrewingTests.cdBrf05ReleaseBeforeABrewLeavesFuel0`; `BrewingTests.cdBrf05ServerStopLeavesNoFuel` | pass | `ceec134` |
| CD-BRW-01 | `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `ceec134` |
| CD-BRW-02 | `BrewingRulesTest` ×2; `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `ceec134` |
| CD-BRW-03 | `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `ceec134` |
| CD-BRW-04 | `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `ceec134` |
| CD-PMP-01 | `FluidPortTest`; `PumpTests.cdPmp01BindsToATankABasinAndACauldron`; `PumpTests.cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull`; `PumpTests.cdPmp03Lv1PumpAdds5000MbIn1000Ticks`; `PumpTests.cdPmp06FiveCauldronsOnRealBlocks` | pass | `ceec134` |
| CD-PMP-02 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-PMP-03 | `FluidPortTest`; `PumpParamsTest` ×2; `PumpTests.cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull`; `PumpTests.cdPmp03Lv1PumpAdds5000MbIn1000Ticks` | pass | `ceec134` |
| CD-PMP-04 | `CauldronFillTest` ×3; `PumpTests.cdPmp01BindsToATankABasinAndACauldron` | pass | `ceec134` |
| CD-PMP-05 | `FluidPortTest`; `PumpTests.cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull` | pass | `ceec134` |
| CD-PMP-06 | `CauldronFillTest`; `PumpTests.cdPmp06FiveCauldronsOnRealBlocks` | pass | `ceec134` |
| CD-MUS-01 | `MuseTests.cdHeal14LeashedMuseNeverHealsATiredAlly`; `MuseTests.cdMus03Lv50MuseGivesATableTheOffersOfEightShelves`; `MuseTests.cdMus05StaminaDrainsOnlyWhileTheMenuIsOpen` | pass | `ceec134` |
| CD-MUS-02 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-MUS-03 | `MuseBonusTest` ×2; `MuseTests.cdMus03Lv50MuseGivesATableTheOffersOfEightShelves` | pass | `ceec134` |
| CD-MUS-04 | `MuseBonusTest` ×2 | pass | `ceec134` |
| CD-MUS-05 | `MuseTests.cdMus03Lv50MuseGivesATableTheOffersOfEightShelves`; `MuseTests.cdMus05StaminaDrainsOnlyWhileTheMenuIsOpen` | pass | `ceec134` |
| CD-MUS-06 | `MixinGateTest` ×2 | pass | `ceec134` |
| CD-TLK-01 | `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination` | pass | `ceec134` |
| CD-TLK-02 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-TLK-03 | `BuiltInRoleFilesTest`; `ItemBatchTest` ×3; `TransferParamsTest` | pass | `ceec134` |
| CD-TLK-04 | `TelekinesisTests.cdTlk04CoalGoesIntoTheFuelSlot` | pass | `ceec134` |
| CD-TLK-05 | `TelekinesisTests.cdTlk05TwoSourcesFeedOneBarrel` | pass | `ceec134` |
| CD-TLK-06 | `ItemBatchTest`; `TelekinesisTests.cdTlk06EmptySourceOrFullDestinationMeansStandby` | pass | `ceec134` |
| CD-TLK-07 | `ItemBatchTest` ×2; `TelekinesisTests.cdTlk07NoItemIsLostOrMade` | pass | `ceec134` |
| CD-TLK-08 | `TelekinesisTests.cdTlk08ItemsReachTheNetworkThroughBothInterfaces` | pass | `ceec134` |
| CD-TLK-09 | `LeashTests.cdSec08FarPendingSourceBindsNothingAndLoadsNoChunk`; `TelekinesisTests.cdTlk09SourceAtOffset9IsRefused` | pass | `ceec134` |
| CD-TLK-10 | `BindRulesTest`; `InventoryBlocksTest` ×2; `TelekinesisTests.cdTlk10SameChestTwiceIsRefused`; `TelekinesisTests.cdTlk10TheBlocksOfOneInventoryAreOneContainer` | pass | `ceec134` |
| CD-TLK-11 | `TelekinesisTests.cdTlk11BrokenDestinationOrReplacedSourceClears` | pass | `ceec134` |
| CD-TLK-12 | `BindRulesTest` ×4; `TransferParamsTest`; `TelekinesisTests.cdTlk12DestinationAt33IsTooFar` | pass | `ceec134` |
| CD-MAG-01 | `MagnetTests.cdMag04OnlyItemsInsideTheWorkArea` | pass | `ceec134` |
| CD-MAG-02 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-MAG-03 | `ItemBatchTest` | pass | `ceec134` |
| CD-MAG-04 | `WorkAreaTest`; `MagnetTests.cdMag04OnlyItemsInsideTheWorkArea` | pass | `ceec134` |
| CD-MAG-05 | `MagnetTests.cdMag05PickupDelayIsRespected` | pass | `ceec134` |
| CD-MAG-06 | `ItemBatchTest`; `MagnetTests.cdMag06PartialInsertLeavesTheRest` | pass | `ceec134` |
| CD-MAG-07 | `ItemBatchTest`; `MagnetTests.cdMag07NoItemsOrAFullChestMeansStandby` | pass | `ceec134` |
| CD-MAG-08 | `QueryClockTest` | pass | `ceec134` |
| CD-FAN-01 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-FAN-02 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-FAN-03 | `FanNbtTest`; `WorkSpotsTest`; `FanCatalystTests.cdFan03Fan15TheWorkerStandsInFrontAndWaitsWithNoWork` | pass | `ceec134` |
| CD-FAN-04 | `BindRulesTest` ×2; `FanCatalystTests.cdFan04StoneInFrontRefusesTheBind` | pass | `ceec134` |
| CD-FAN-05 | `FanTests.cdFan05Bal04BlastingOnABeltKeepsCreatesTime` | pass | `ceec134` |
| CD-FAN-06 | `FanTests.cdFan06SmokingCooksBeefAndLeavesIron` | pass | `ceec134` |
| CD-FAN-07 | `FanTests.cdFan07WaterWashesRedWool` | pass | `ceec134` |
| CD-FAN-08 | `FanTests.cdFan08GhostHauntsSand` | pass | `ceec134` |
| CD-FAN-09 | `FanNbtTest` | pass | `ceec134` |
| CD-FAN-10 | `FanNbtTest`; `FanCatalystTests.cdFan10Fan16Sec10TheBinderFlipsOnlyADrivenFan` | pass | `ceec134` |
| CD-FAN-11 | `FanNbtTest`; `FanTests.cdFan11SmokingSurvivesARestart` | pass | `ceec134` |
| CD-FAN-12 | `FanCatalystTests.cdFan12ThreeWorkersStayPutAt256Rpm`; `FanTests.cdFan12MarkedWorkerIsNeitherPushedNorHaunted` | pass | `ceec134` |
| CD-FAN-13 | `FanTests.cdFan13GhostInWaterStillHaunts` | pass | `ceec134` |
| CD-FAN-14 | `FanNbtTest`; checklist 52 | pass, play-test | `ceec134` |
| CD-FAN-15 | `FanCatalystTests.cdFan03Fan15TheWorkerStandsInFrontAndWaitsWithNoWork` | pass | `ceec134` |
| CD-FAN-16 | `FanCatalystTests.cdFan10Fan16Sec10TheBinderFlipsOnlyADrivenFan`; checklist 53 | pass, play-test | `ceec134` |
| CD-FAN-17 | `MixinGateTest`; `MixinMembersTest` ×2 | pass | `ceec134` |
| CD-TLW-01 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-TLW-02 | `BuiltInRoleFilesTest` | pass | `ceec134` |
| CD-TLW-03 | `BuiltInRoleFilesTest`; `TailwindMathTest` ×2; `TailwindTests.cdTlw06Tlw09Tlw08TheBonusFollowsTheWork` | pass | `ceec134` |
| CD-TLW-04 | `BuiltInRoleFilesTest`; `TailwindMathTest`; `TailwindTests.cdBal04Tlw04TheWindmillNeverPassesSixteenRpm` | pass | `ceec134` |
| CD-TLW-05 | `TailwindTests.cdTlw05SevenSailsNeverAssemble` | pass | `ceec134` |
| CD-TLW-06 | `TailwindTests.cdTlw06Tlw09Tlw08TheBonusFollowsTheWork` | pass | `ceec134` |
| CD-TLW-07 | `TailwindNbtTest`; checklist 54 | pass, play-test | `ceec134` |
| CD-TLW-08 | `TailwindTests.cdTlw06Tlw09Tlw08TheBonusFollowsTheWork` | pass | `ceec134` |
| CD-TLW-09 | `TailwindTests.cdTlw06Tlw09Tlw08TheBonusFollowsTheWork` | pass | `ceec134` |
| CD-TLW-10 | `MixinGateTest` | pass | `ceec134` |
| CD-QWK-01 | `QuickWorkTests.cdQwk01Qwk02Qwk11Qwk12SendOutAtAFurnaceWorksIt` | pass | `ceec134` |
| CD-QWK-02 | `QuickWorkTests.cdQwk01Qwk02Qwk11Qwk12SendOutAtAFurnaceWorksIt` | pass | `ceec134` |
| CD-QWK-03 | `QuickWorkRulesTest` ×3 | pass | `ceec134` |
| CD-QWK-04 | `QuickWorkRulesTest`; `QuickWorkTests.cdQwk04ChestAndCauldronNeedTheLeash` | pass | `ceec134` |
| CD-QWK-05 | `QuickWorkRulesTest`; `QuickWorkTests.cdQwk05FairyTypeGetsNoOrder` | pass | `ceec134` |
| CD-QWK-06 | `QuickWorkRulesTest`; `QuickWorkTests.cdQwk06RefusedOwnerAndSpectatorGetNoOrder` | pass | `ceec134` |
| CD-QWK-07 | `ClaimRegistryTest`; `QuickWorkTests.cdQwk07ClaimedFurnaceGivesNoOrder` | pass | `ceec134` |
| CD-QWK-08 | `QuickWorkTests.cdQwk08TiredPokemonGetsNoOrder` | pass | `ceec134` |
| CD-QWK-09 | `QuickWorkRulesTest`; `QuickWorkTests.cdQwk09EmptyFurnaceGivesNoOrder` | pass | `ceec134` |
| CD-QWK-10 | `QuickWorkTests.cdQwk10FurnaceWithNoSpotGivesNoOrder` | pass | `ceec134` |
| CD-QWK-11 | `QuickWorkTests.cdQwk01Qwk02Qwk11Qwk12SendOutAtAFurnaceWorksIt` | pass | `ceec134` |
| CD-QWK-12 | `QuickWorkTests.cdQwk01Qwk02Qwk11Qwk12SendOutAtAFurnaceWorksIt` | pass | `ceec134` |
| CD-QWK-13 | `QuickWorkRulesTest` ×2; `QuickWorkTests.cdQwk13Qwk14Qwk15LeashEndsTheOrderForGood` | pass | `ceec134` |
| CD-QWK-14 | `QuickWorkTests.cdQwk13Qwk14Qwk15LeashEndsTheOrderForGood` | pass | `ceec134` |
| CD-QWK-15 | `QuickWorkTests.cdQwk13Qwk14Qwk15LeashEndsTheOrderForGood` | pass | `ceec134` |
| CD-QWK-16 | `QuickWorkRulesTest` ×2; `QuickWorkTests.cdQwk16RecallEndsTheOrder` | pass | `ceec134` |
| CD-QWK-17 | `QuickWorkTests.cdQwk17TiredWorkerStops` | pass | `ceec134` |
| CD-QWK-18 | `QuickWorkRulesTest`; `QuickWorkTests.cdQwk18BrokenFurnaceEndsTheOrder` | pass | `ceec134` |
| CD-QWK-19 | `QuickWorkRulesTest`; `QuickWorkTests.cdQwk19StandbyTimeEndsTheOrder` | pass | `ceec134` |
| CD-QWK-20 | `QuickWorkRulesTest`; `QuickWorkTests.cdQwk20OwnerWhoLeavesEndsTheOrder` | pass | `ceec134` |
| CD-QWK-21 | `QuickWorkRulesTest` ×2; checklist 59 | pass, play-test | `ceec134` |
| CD-QWK-22 | `QuickWorkTests.cdQwk22TrappedWorkerTimesOut` | pass | `ceec134` |
| CD-QWK-23 | `QuickWorkSourceTest` | pass | `ceec134` |
| CD-QWK-24 | `QuickWorkSourceTest` | pass | `ceec134` |
| CD-QWK-25 | `QuickWorkRulesTest` | pass | `ceec134` |
| CD-BAL-01 | `BalanceFilesTest` | pass | `ceec134` |
| CD-BAL-02 | `BalanceTest` | pass | `ceec134` |
| CD-BAL-03 | `BalanceTest` ×2 | pass | `ceec134` |
| CD-BAL-04 | `CrankerTests.cdCrk02Bal04CrankHolds32RpmFor600Ticks`; `FanTests.cdFan05Bal04BlastingOnABeltKeepsCreatesTime`; `TailwindTests.cdBal04Tlw04TheWindmillNeverPassesSixteenRpm`; `WheelDriverTests.cdBal04WheelsNeverExceedCreate` | pass | `ceec134` |
| CD-BAL-05 | `BalanceFilesTest`; `TransferParamsTest` | pass | `ceec134` |
| CD-BAL-06 | `WorkModeTests.cdBal06SeventeenthPokemonIsRefused` | pass | `ceec134` |
| CD-BAL-07 | `BalanceFilesTest` | pass | `ceec134` |
| CD-UI-01 | checklist 6 | play-test | `ceec134` |
| CD-UI-02 | checklist 7 | play-test | `ceec134` |
| CD-UI-03 | `WorkModePolicyTest`; checklist 8 | pass, play-test | `ceec134` |
| CD-UI-04 | `PanelLayoutTest`; checklist 9 | pass, play-test | `ceec134` |
| CD-UI-05 | `PanelCadenceTest` ×2 | pass | `ceec134` |
| CD-UI-06 | `HeadBarRangeTest`; checklist 22 | pass, play-test | `ceec134` |
| CD-UI-07 | `StateIconsTest` ×2; checklist 10 | pass, play-test | `ceec134` |
| CD-UI-09 | `LeashIndexTest`; `OverviewRequestsTest`; `LeashTests.cdUi09OverviewHoldsOnlyOwnTargets`; checklist 23 | pass, play-test | `ceec134` |
| CD-UI-11 | `LangTest`; `StaminaBarTest` | pass | `ceec134` |
| CD-UI-12 | `LangTest` ×5; `QuickWorkRulesTest` | pass | `ceec134` |
| CD-UI-13 | `PanelMixinCheckTest` | pass | `ceec134` |
| CD-UI-14 | `ClaimsReportTest` ×2; `CommandTests.cdUi14ClaimsListsTheBedAndTheHealClaim`; `CommandTests.cdUi14ClaimsListsTheWorkClaimAndTheBinding` | pass | `ceec134` |
| CD-UI-15 | `StaminaReportTest` ×4; `CommandTests.cdUi15StaminaShowsValueStateAndRule` | pass | `ceec134` |
| CD-UI-16 | `CreativeTabTests.cdUi16CreativeTabListsEveryItemOfTheMod`; checklist 50 | pass, play-test | `ceec134` |
| CD-UI-17 | checklist 51 | play-test | `ceec134` |
| CD-CMP-01 | `CobbleworkersRestTest` ×2; checklist 36 | pass, play-test | `ceec134` |
| CD-CMP-02 | `CobbleworkersRestTest` ×3 | pass | `ceec134` |
| CD-CMP-03 | checklist 37 | play-test | `ceec134` |
| CD-CMP-04 | checklist 38 | play-test | `ceec134` |
| CD-CMP-05 | `CobblebaseHooksTest` ×3; `MixinStateTest`; checklist 39 | pass, play-test | `ceec134` |
| CD-CMP-06 | test pack `compat` (`compat_cobblebase`) | pass | `ceec134` |
| CD-CMP-07 | test pack `compat` (`compat_cobbleworkers`) | pass | `ceec134` |
| CD-SEC-01 | `WorkModePolicyTest`; `WorkModeTests.cdSec01StrangerCannotToggle` | pass | `ceec134` |
| CD-SEC-02 | `WorkModePolicyTest`; `WorkModeTests.cdSec02OwnerlessPastureAcceptsOperatorsOnly` | pass | `ceec134` |
| CD-SEC-03 | `WorkModePolicyTest` | pass | `ceec134` |
| CD-SEC-04 | `WorkModePolicyTest`; `WorkModeTests.cdSec04DistanceAndDimension` | pass | `ceec134` |
| CD-SEC-05 | `PayloadCodecTest`; `PayloadTests.cdSec05Wmd03PlacerPayloadsSetTheRequestedState`; `WorkModeTests.cdSec05RepeatedOnStaysOn` | pass | `ceec134` |
| CD-SEC-06 | `PayloadCodecTest`; `PayloadTests.cdSec06Wmd04TwoOperatorsInOneTick`; `WorkModeTests.cdSec06TwoOperatorsSameTick` | pass | `ceec134` |
| CD-SEC-07 | `LeashTests.cdSec07OnlyTheOwnerSelects` | pass | `ceec134` |
| CD-SEC-08 | `BindRulesTest` ×2; `LeashTests.cdSec08FarPendingSourceBindsNothingAndLoadsNoChunk` | pass | `ceec134` |
| CD-SEC-09 | `CommandTests.cdSec09LevelZeroPlayerIsRefused` | pass | `ceec134` |
| CD-SEC-10 | `FlipRulesTest` ×2; `CreateFlipTests.cdSec10StrangersAndIdleTargetsNeverFlip`; `FanCatalystTests.cdFan10Fan16Sec10TheBinderFlipsOnlyADrivenFan` | pass | `ceec134` |
| CD-SEC-11 | `PendingSourceTest`; `LeashTests.cdSec11CancelledClickNeverBinds` | pass | `ceec134` |
| CD-PERF-01 | `EngineTimingTest`; `RepositoryFilesTest`; `PerfTests.cdPerf01And02ReferenceWorld` | pass | `ceec134` |
| CD-PERF-02 | `EngineTimingTest`; `PerfTests.cdPerf01And02ReferenceWorld` | pass | `ceec134` |
| CD-PERF-03 | `TargetScanTest` | pass | `ceec134` |
| CD-PERF-04 | `EffectTests.cdPerf04NoFocusOutlivesItsClaim` | pass | `ceec134` |
| CD-PERF-05 | `DisabledHandlersTest`; `LangTest`; `OnceLoggerTest` ×2; `PerfTests.cdPerf01And02ReferenceWorld` | pass | `ceec134` |
| CD-CFG-01 | `ConfigTest` ×2; test pack `restart` | pass | `ceec134` |
| CD-CFG-02 | `ConfigTest` | pass | `ceec134` |
| CD-CFG-03 | `FeCurveTest` ×2; `RoleDataTests.cdCfg03Cfg13DatapackSetsTheGeneratorRates` | pass | `ceec134` |
| CD-CFG-04 | `StaminaMathTest` ×3 | pass | `ceec134` |
| CD-CFG-05 | `RoleSetTest`; `RoleDataTests.cdCfg05DisabledRoleGetsNoClaims` | pass | `ceec134` |
| CD-CFG-06 | `ConfigTest` | pass | `ceec134` |
| CD-CFG-07 | `RoleCodecTest`; `RoleDataTests.cdCfg07DatapackLetsWaterTypesStoke` | pass | `ceec134` |
| CD-CFG-08 | `RoleDataTests.cdCfg08ReloadAppliesChangedTypes`; test pack `reload` | pass | `ceec134` |
| CD-CFG-09 | `RoleCodecTest` | pass | `ceec134` |
| CD-CFG-10 | `RoleSetTest`; `CoreTests.cdCfg10CreateRolesOffWithoutCreate`; `CoreTests.cdCfg10ItemRolesNeedNoCreate` | pass | `ceec134` |
| CD-CFG-11 | `BlockScanCursorTest`; `ConfigTest`; `ScanSchedulerTest` | pass | `ceec134` |
| CD-CFG-12 | `ConfigTest`; `WorkerMachineTest` | pass | `ceec134` |
| CD-CFG-13 | `FeCurveTest`; `PumpParamsTest`; `TransferParamsTest`; `RoleDataTests.cdCfg03Cfg13DatapackSetsTheGeneratorRates` | pass | `ceec134` |
| CD-CFG-14 | `ConfigTest`; `WorkAreaTest` | pass | `ceec134` |
| CD-CFG-15 | `ConfigTest`; `WorkerMachineTest` | pass | `ceec134` |
| CD-CFG-16 | `ConfigTest`; `QuickWorkRulesTest` | pass | `ceec134` |
| CD-CFG-17 | `ConfigTest` | pass | `ceec134` |
| CD-CFG-18 | `ConfigTest`; `QuickWorkRulesTest` | pass | `ceec134` |
| CD-ART-01 | `ArtFilesTest` | pass | `ceec134` |
| CD-ART-02 | art check `cd-art-10`; checklist 55 | art review, play-test | `ceec134` |
| CD-ART-03 | art check `cd-art-10`; checklist 56 | art review, play-test | `ceec134` |
| CD-ART-04 | `StaminaBarTest`; `WorkModeSpritesTest` ×3; art check `cd-art-10`; checklist 57 | art review, play-test | `ceec134` |
| CD-ART-05 | art check `cd-art-05` | pass | `ceec134` |
| CD-ART-06 | `ArtFilesTest`; art check `cd-art-06` | pass | `ceec134` |
| CD-ART-07 | art check `cd-art-07`; checklist 58 | pass, play-test | `ceec134` |
| CD-ART-08 | `ArtFilesTest`; art check `cd-art-08` | pass | `ceec134` |
| CD-ART-09 | art check `cd-art-09` | pass | `ceec134` |
| CD-ART-10 | art check `cd-art-10` | art review | `ceec134` |
| CD-ART-12 | `ReleaseWorkflowTest`; art check `cd-art-12` | art review | `ceec134` |
| CD-ART-13 | `ArtFilesTest`; art check `cd-art-13` | pass | `ceec134` |
| CD-ART-14 | `ModMetadataTest` | pass | `ceec134` |
| CD-REL-01 | `RepositoryFilesTest`; checklist 40 | pass, play-test | `ceec134` |
| CD-REL-02 | `RepositoryFilesTest` | pass | `ceec134` |
| CD-REL-03 | `RepositoryFilesTest` | pass | `ceec134` |
| CD-REL-04 | `ReleaseWorkflowTest`; checklist 42 | pass, open (M9) | `ceec134` |
| CD-REL-05 | `ModMetadataTest`; `ReleaseWorkflowTest` | pass | `ceec134` |
| CD-REL-06 | `ModMetadataTest`; `RepositoryFilesTest` | pass | `ceec134` |
| CD-REL-07 | Gradle `verifyJar` | pass | `ceec134` |
| CD-REL-08 | `PlayerGuideTest` ×4 | pass | `ceec134` |
| CD-REL-09 | `ManualChecklistTest` ×2 | pass | `ceec134` |
| CD-REL-10 | `SmokeTests.cdRel10PinnedModsLoad` | pass | `ceec134` |
| CD-REL-12 | `ReleaseWorkflowTest` ×5; checklist 41 | pass, play-test | `ceec134` |
| CD-REL-13 | the play-test note of `docs/testing/phase-1-gate.md`, dated 2026-11-10 | pass | `ceec134` |
| CD-REL-14 | this report (the run on the commit) and the play-test note | play-test | `ceec134` |
| CD-REL-15 | after the Phase 2 gate (M9) | open (M9) | `ceec134` |

### Deferred rows

The end-of-phase passes of M5 Task 9 and M8 Task 7 gave each row of their deferred lists a test. The table above names the tests of each row. This list gives the result of each row, by the task that deferred it:

- M1 Task 10: CD-FX-04 pass, play-test; CD-FX-05 pass, play-test; CD-FX-06 pass; CD-FX-07 pass; CD-PERF-04 pass.
- M1 Tasks 11 to 13: CD-LSH-05 pass; CD-LSH-06 pass, play-test; CD-LSH-09 pass; CD-LSH-10 pass; CD-LSH-11 pass; CD-LSH-13 pass, play-test; CD-LSH-14 pass; CD-LSH-15 pass; CD-LSH-17 pass; CD-LSH-18 pass; CD-LSH-19 pass; CD-LSH-20 pass, play-test; CD-LSH-21 pass, play-test; CD-LSH-24 pass; CD-LSH-25 pass; CD-LSH-26 pass; CD-LSH-27 pass; CD-WMD-07 pass; CD-CLM-05 pass; CD-CLM-06 pass; CD-ASG-20 pass; CD-SEC-07 pass; CD-SEC-11 pass; CD-UI-14 pass; CD-SEC-09 pass; CD-SEC-05 pass; CD-WMD-03 pass; CD-SEC-06 pass; CD-WMD-04 pass.
- M2 Tasks 1 to 3: CD-STA-11 pass; CD-STA-12 pass; CD-BED-01 pass; CD-BED-02 pass.
- M2 Tasks 4 to 6: CD-STA-13 pass; CD-LSH-12 pass; CD-BED-07 pass; CD-BED-11 pass; CD-BED-13 pass; CD-BED-14 pass, play-test; CD-CLM-06 pass; CD-WMD-05 pass; CD-FX-10 pass, play-test; CD-HEAL-06 pass; CD-HEAL-07 pass; CD-HEAL-09 pass; CD-HEAL-11 pass; CD-HEAL-15 pass, play-test; CD-HEAL-17 pass; CD-UI-15 pass; CD-UI-14 pass; CD-SEC-09 pass.
- M3 Tasks 1 to 3: CD-ASG-01 pass; CD-ASG-04 pass; CD-ASG-19 pass; CD-LSH-03 pass; CD-LSH-06 pass, play-test; CD-LSH-11 pass; CD-LSH-14 pass.
- M3 Tasks 4 to 6: CD-CRK-04 pass; CD-CRK-07 pass; CD-BRN-02 pass; CD-BRN-03 pass; CD-BRN-04 pass; CD-SEC-10 pass.
- M4 Tasks 1 to 2: CD-GEN-04 pass; CD-GEN-07 pass; CD-CFG-03 pass; CD-CFG-13 pass; CD-CFG-05 pass.
- M4 Tasks 3 to 5: CD-BRF-05 pass; CD-PMP-06 pass.
- M5 Tasks 5 to 7: CD-CLM-07 pass, play-test; CD-LSH-07 pass, play-test; CD-WHL-07 pass, play-test; CD-CRK-08 pass, play-test; CD-STA-04 pass, play-test; CD-BED-14 pass, play-test.
- M6 Task 3: CD-TLK-09 pass; CD-TLK-10 pass; CD-TLK-11 pass; CD-TLK-12 pass.
- M6 Task 4: CD-TLK-05 pass; CD-TLK-06 pass.
- M6 Task 5: CD-MAG-07 pass.
- M7 Task 2: CD-FAN-11 pass.
- M7 Task 3: CD-FAN-04 pass.
- Quick Work Task 3: CD-QWK-04 pass; CD-QWK-05 pass; CD-QWK-06 pass; CD-QWK-07 pass; CD-QWK-08 pass; CD-QWK-09 pass; CD-QWK-10 pass; CD-QWK-17 pass; CD-QWK-18 pass; CD-QWK-19 pass; CD-QWK-20 pass; CD-QWK-21 pass, play-test; CD-QWK-22 pass.

### Tests that cite no row

These GameTests cite no row of the spec sheet: `BurnerTests.creativeBlazeBurnerHasNoDemand` (pass), `SmokeTests.smokePastureTethersAPokemon` (pass), `WorkerPushTests.aWalkerPassesAWorkingWorkerInOneRow` (pass), `WorkerPushTests.onlyTwoDirectedWorkersSkipTheirPush` (pass), `WorkerPushTests.workersMeetingHeadOnInOneRowPassAndWork` (pass).

79 of the 517 unit tests cite no row id. They test the helpers that the tests of the rows use.

### Totals

The spec sheet has 372 `MUST` and `MUST NOT` rows. 360 of the 360 rows with automated tests pass. 51 rows have a play-test part, and 6 of them have no automated test. 5 rows wait for the approver's art review (CD-ART-02, CD-ART-03, CD-ART-04, CD-ART-10, CD-ART-12). 2 rows stay open after Phase 2 (CD-REL-04, CD-REL-15). 0 automated rows have no passing test.

### Rows without a passing test

None.

<!-- gate-report rows end -->
