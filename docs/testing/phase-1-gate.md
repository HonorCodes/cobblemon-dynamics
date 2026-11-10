# Phase 1 gate report

This report is the Phase 1 record of CD-REL-13. It names the commit on which every automated check ran. It gives the
result of each Phase 1 `MUST` and `MUST NOT` row of the [spec sheet](../plans/2026-11-09-v1-spec-sheet.md). It also holds
the approver's play-test note. CD-REL-13 completes when the approver records that note, before any M6 work merges.

`scripts/gate_report.py` writes the two generated blocks: the run summary below and the Rows section at the end. The
section [How this report is made](#how-this-report-is-made) gives its inputs and rules. The script keeps the text outside
the two blocks.

<!-- gate-report summary: written by scripts/gate_report.py; edits inside this block are lost -->

## Run summary

`scripts/gate_report.py` wrote this block and the Rows section on 2026-11-10 (UTC). Every check below ran on commit `0cddb7125573401859819d3396c1bbcadaf5df83` (2026-11-10, "Fix two timing races in GameTests: cdAi07NightSleepEndsWhenWorkStarts and cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer").

| Check | Command | Result |
| --- | --- | --- |
| Unit tests | `./gradlew build` | 356 of 356 pass, in 76 classes |
| Jar and source checks | `./gradlew build` | `verifyJar` pass, `verifyNoAe2` pass. The Gradle log ends with BUILD SUCCESSFUL. |
| GameTests, full mod set | `./gradlew runGameTestServer` | 162 of 162 pass. The log counts 162 tests and 0 failures, in 25.65 s. |
| GameTests without Create and AE2 | `./gradlew runGameTestServerCore` | 3 of 3 pass. The log counts 3 tests and 0 failures, in 681.8 ms. |
| GameTests, server time of 64 Working workers | `./gradlew runGameTestServerPerf` | 1 of 1 pass. The log counts 1 tests and 0 failures, in 9.228 s. |
| Test pack | `py scripts/testpack/testpack.py smoke` | pass |
| Test pack | `py scripts/testpack/testpack.py deps` | pass, cases 7 of 7 pass |
| Test pack | `py scripts/testpack/testpack.py compat` | pass, cases 2 of 2 pass |
| Test pack | `py scripts/testpack/testpack.py reload` | pass |
| Test pack | `py scripts/testpack/testpack.py restart` | pass |
| Play-test client | `py scripts/testpack/testpack.py client` | `run/testpack/client/mods/` holds 6 jars: `appliedenergistics2-19.2.19.jar`, `Cobblemon-neoforge-1.8.4+1.21.1.jar`, `cobblemon_dynamics-1.0.0-alpha.1+mc1.21.1.jar`, `create-1.21.1-6.0.10.jar`, `guideme-21.1.1.jar`, `kotlinforforge-5.10.0-all.jar`. Each pinned jar has its SHA-512 from `mods.json`, and the mod jar is the same file as in `build/libs/`. |

The perf run measured the server time of Dynamics per tick, with 64 Working workers over 6,000 ticks. The mean was 0.140 ms (CD-PERF-01), and the p95 was 0.252 ms (CD-PERF-02). The budgets are 1.0 ms for the mean and 2.0 ms for the p95. Dynamics wrote 0 log lines after the warm-up (CD-PERF-05).

The spec sheet has 277 Phase 1 `MUST` and `MUST NOT` rows. 269 of the 269 rows with automated tests pass. 35 rows have a play-test part, and 6 of them have no automated test. 3 rows stay open after Phase 1 (CD-REL-04, CD-REL-14, CD-REL-15). 0 automated rows have no passing test.

<!-- gate-report summary end -->

## For the approver

### The play-test client

`py scripts/testpack/testpack.py client` assembled the client on the commit of the run summary. The client is the folder
`C:\Users\admin\src\cobblemon-dynamics\run\testpack\client\mods\`, which holds six jars:

- `Cobblemon-neoforge-1.8.4+1.21.1.jar`
- `create-1.21.1-6.0.10.jar`
- `appliedenergistics2-19.2.19.jar`
- `guideme-21.1.1.jar`
- `kotlinforforge-5.10.0-all.jar`
- `cobblemon_dynamics-1.0.0-alpha.1+mc1.21.1.jar`, the mod, built from the commit of the run summary

The test pack sets up this `mods` folder only. It installs no Minecraft or NeoForge client, and it makes no launcher
instance. The play-test therefore needs a new instance in a launcher. Never use or change an existing instance, such as
the COHO modpack.

To launch the client with the Modrinth App:

1. In the left bar of the Modrinth App, click the plus button to make a new instance.
2. Set the name to `Cobblemon Dynamics play-test`, the loader to NeoForge and the game version to 1.21.1.
3. In the advanced options, set the NeoForge version to 21.1.265.
4. Create the instance.
5. In the menu of the new instance, open its folder.
6. If the instance folder has no `mods` folder, make one.
7. Copy the six jars of `run/testpack/client/mods/` into the `mods` folder of the instance.
8. Write the line `onboardAccessibility:false` into `options.txt` in the instance folder. Do this before the first start,
   or the game first opens the accessibility screen, which you can also click through.
9. Click **Play**.
10. Make a creative flat world with cheats on, as the play-test setup of the checklist says.

The CurseForge app and GDLauncher work the same way: a new Minecraft 1.21.1 instance with NeoForge 21.1.265, with the six
jars in its `mods` folder.

### Optional jars for the compatibility checks

`run/testpack/cache/` holds the pinned jars of these checks. The `compat` run of this gate used the same files.

| Checks | Jars to add |
| --- | --- |
| CD-CMP-01 (check 36) | `cobbleworkers-neoforge-2.0.7+1.8.3.jar` |
| CD-CMP-03 and CD-CMP-04 (checks 37 and 38) | `cobblebase-neoforge-2.0.1+1.8.0.jar` and `cloth-config-15.0.140-neoforge.jar` |
| CD-CMP-05 (check 39) | `cloth-config-15.0.140-neoforge.jar`, and Cobblebase 1.5.4 from <https://modrinth.com/mod/cobblebase/version/arXwBcAs>, which the cache does not hold |

Add only the jars that a check names. Take them out of the `mods` folder after the check.

### Steps

1. Read the run summary above. It names the commit of the run, and it holds the test counts and the perf numbers.
2. Set up the client as above.
3. For the checks that need a server or a second player, start the server that the `smoke` run left in
   `run/testpack/smoke/`. The section "Server for the server checks" of the checklist tells how.
4. Work through checks 5 to 41 of [the manual checklist](manual-checklist.md). Checks 1 to 4 are scripted, and the run
   summary holds their results.
5. Record check 20 (CD-LSH-20) as blocked, because the Telekinesis role arrives in M6.
6. Do check 41, the CD-REL-12 demo, only after you give the go-ahead for the tags.
7. For the spark run of known issue (d), add the spark mod for NeoForge 1.21.1 to the instance.
8. Stand beside a Work Mode pasture with 8 Working workers, as in check 15.
9. Run `/spark profiler start`.
10. After 5 minutes, run `/spark profiler stop`.
11. Record each result in the [play-test note](#play-test-note): pass, fail with what you saw, or blocked with the
    reason. CD-REL-13 completes when you record the note.

Check 42 (CD-REL-04) belongs to the public release (M9), so the Phase 1 play-test does not include it.

## Known issues and open decisions

- **(a) Unreachable work spots.** The work spot search checks that the worker's body fits a spot, but not that a path
  reaches the spot. So a worker, more often a wide one, can get a spot that it cannot reach. It recovers only through the
  travel timeout: the claim ends after 600 ticks (CD-ASG-13), and the worker skips that target for 6,000 ticks (CD-ASG-14).
- **(b) Tags wait for the approver.** The CD-REL-12 demo pushes `v1.0.0-alpha.1`, then `v1.0.0-beta.1`. Each tag makes a
  public GitHub pre-release, so the demo waits for the approver's go-ahead (check 41 and M5 Task 9). The first public
  release, the `v1.0.0` tag of CD-REL-04, also waits for the approver, after the Phase 2 gate.
- **(c) Publishing secrets.** Before the first public `vX.Y.Z` tag, the approver must set the repository secrets
  `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` and the repository variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`.
  Without them, the publish job of the release workflow fails on purpose, so that a public tag never skips its publish.
- **(d) Work effect packets.** The perf run cannot include the cost of the work effect packets. Fake players never join
  the level, so no effect packet goes out during the measurement. The spark run of the steps above, with a client beside
  the workers, covers that cost.
- **(e) Other open items:**
  - CD-LSH-20 is blocked until M6, which brings the Telekinesis role. In Phase 1, its GameTest with the test role
    `cobblemon_dynamics:test_transfer` is the evidence.
  - CD-CMP-05 (check 39) is blocked if Cobblebase 1.5.4 fails on Cobblemon 1.8.4.
  - CD-SEC-10 and CD-BAL-04 have M7 parts: the fan for CD-SEC-10, and the windmill and the fan for CD-BAL-04. This
    report covers their Phase 1 parts.
  - CD-BAL-05 becomes binding in M6. Until then, its check passes because the Telekinesis and Magnet role files do not
    exist.
  - CD-REL-04, CD-REL-14 and CD-REL-15 stay open after Phase 1 by design: the `v1.0.0` tag (M9), the Phase 2 gate (M8)
    and the approver's pack (M9).

## Play-test note

The approver writes this section. For each check, record pass, fail with what you saw, or blocked with the reason. Also
record the result of the spark run and, after the CD-REL-12 demo, both run URLs, the job conclusions and the site checks.

- Date:
- Approver:
- Launcher and instance:
- Spark run (steps 7 to 10):
- CD-REL-12 demo (check 41):

| Check | Row | Result | What you saw |
| --- | --- | --- | --- |
| 5 | CD-PLAT-06 | | |
| 6 | CD-UI-01 | | |
| 7 | CD-UI-02 | | |
| 8 | CD-UI-03 | | |
| 9 | CD-UI-04 | | |
| 10 | CD-UI-07 | | |
| 11 | CD-UI-08 | | |
| 12 | CD-UI-10 | | |
| 13 | CD-CLM-07 | | |
| 14 | CD-ASG-09 | | |
| 15 | CD-ASG-14 | | |
| 16 | CD-LSH-02 | | |
| 17 | CD-LSH-04 | | |
| 18 | CD-LSH-06 | | |
| 19 | CD-LSH-13 | | |
| 20 | CD-LSH-20 | blocked | The Telekinesis role arrives in M6. |
| 21 | CD-LSH-21 | | |
| 22 | CD-UI-06 | | |
| 23 | CD-UI-09 | | |
| 24 | CD-LSH-07 | | |
| 25 | CD-FX-10 | | |
| 26 | CD-STA-04 | | |
| 27 | CD-BED-14 | | |
| 28 | CD-HEAL-15 | | |
| 29 | CD-STK-07 | | |
| 30 | CD-FX-04 | | |
| 31 | CD-FX-05 | | |
| 32 | CD-WHL-09 | | |
| 33 | CD-WHL-07 | | |
| 34 | CD-CRK-08 | | |
| 35 | CD-FX-09 | | |
| 36 | CD-CMP-01 | | |
| 37 | CD-CMP-03 | | |
| 38 | CD-CMP-04 | | |
| 39 | CD-CMP-05 | | |
| 40 | CD-REL-01 | | |
| 41 | CD-REL-12 | | |

## How this report is made

Run these commands on one commit, in this order, with `JAVA_HOME` set to a JDK 21. Save the output of each command to a
file.

1. Delete `build/test-results/test`, so that the JUnit XML reports come from this run.
2. Run `./gradlew build runGameTestServer runGameTestServerCore runGameTestServerPerf --console=plain`.
3. Run `py scripts/testpack/testpack.py` with `smoke`, `deps`, `compat`, `reload` and `restart`, one command at a time.
4. Run `py scripts/testpack/testpack.py client`.
5. Run `py scripts/gate_report.py --gradle-log <Gradle output>`, with one `--testpack-log <output>` for each command of
   step 3.

`scripts/gate_report.py` follows these rules:

- It reads the spec sheet, the JUnit XML reports in `build/test-results/test/` and the GameTest sources. It reads the log
  of each GameTest run: `logs/latest.log` in `run/gametest/`, `run/gametest-core/` and `run/gametest-perf/`. It also
  reads the deferred list of M5 Task 9, the manual checklist, `scripts/testpack/README.md`, `testpack.py` and
  `build.gradle`. Last, it reads the saved output of the Gradle command and of each test pack command.
- The GameTest runs write no JUnit-style report. A run's log names only the failed tests and ends with
  `All N required tests passed`. So the script takes the test names from the `@GameTest` methods of each run, and it
  checks that their number equals N.
- A GameTest cites a row when its name, its KDoc or one of its assertion messages cites the row. The assertion messages
  of the private helpers that the test calls count too. The deferred list of M5 Task 9 adds the tests that it names for a
  row.
- The Gradle tasks `verifyJar` and `verifyNoAe2` check CD-PLAT-04, CD-REL-07 and CD-CRY-05. The script reads their
  outcome from the Gradle output.
- The tables of the test pack README give the commands and cases that check CD-CFG-01, CD-CFG-08, CD-CLM-07, CD-WHL-07,
  CD-CRK-08, CD-CMP-06 and CD-CMP-07. The scripted checks of the manual checklist give the cases of the PLAT rows.
- The script marks CD-REL-04, CD-REL-14 and CD-REL-15 open after Phase 1, and CD-REL-13 play-test. These marks come from
  the plan, not from a test.
- The script refuses to write the report when tracked files differ from the commit. It counts a log that is older than
  the commit as stale, and a stale log gives no pass.

<!-- gate-report rows: written by scripts/gate_report.py; edits inside this block are lost -->

## Rows

The table has one line for each Phase 1 `MUST` and `MUST NOT` row of the spec sheet, in the order of the sheet.

- Tests: `Class` ×n is n unit tests of that class whose names cite the row. `Class.method` is a GameTest whose name, KDoc or assertion messages cite the row, or which the deferred list of M5 Task 9 names for the row. Test pack names a command of `scripts/testpack/testpack.py`, with the cases that the test pack's README or the manual checklist gives for the row. Gradle names a check task of `./gradlew build`. Checklist n is check n of `docs/testing/manual-checklist.md`.
- Result: `pass` means that every test of the row passed. `play-test` means that the row's evidence is a Demo or a checklist line, which the approver checks in the play-test. `open (Mn)` means that the last part of the row comes after the Phase 1 gate, in milestone Mn. The section "Rows without a passing test" lists each row with `fail` or `no test`.

| Row | Tests | Result | Commit |
| --- | --- | --- | --- |
| CD-PLAT-01 | `ModMetadataTest`; test pack `deps` (`neoforge-below-floor`, `neoforge-floor`); checklist 1 | pass | `0cddb71` |
| CD-PLAT-02 | `ModMetadataTest`; test pack `deps` (`cobblemon-below-floor`); test pack `smoke`; checklist 2 | pass | `0cddb71` |
| CD-PLAT-03 | `ModMetadataTest`; test pack `deps` (`kff-floor`); checklist 3 | pass | `0cddb71` |
| CD-PLAT-04 | Gradle `verifyJar` | pass | `0cddb71` |
| CD-PLAT-05 | `ModMetadataTest`; `RepositoryFilesTest`; `CoreTests.cdPlat05LoadsWithoutCreateAndAe2`; test pack `deps` (`ae2-below-floor`, `create-below-floor`, `without-create-ae2`); checklist 4 | pass | `0cddb71` |
| CD-PLAT-06 | `ModMetadataTest`; checklist 5 | pass, play-test | `0cddb71` |
| CD-PLAT-07 | `ModMetadataTest` | pass | `0cddb71` |
| CD-PLAT-08 | `ModMetadataTest` | pass | `0cddb71` |
| CD-WMD-01 | `WorkModeTests.cdWmd01NewPastureIsOff` | pass | `0cddb71` |
| CD-WMD-02 | `WorkModeTests.cdWmd02WorkModeSurvivesSaveAndLoad` | pass | `0cddb71` |
| CD-WMD-03 | `WorkModePolicyTest`; `PayloadTests.cdSec05Wmd03PlacerPayloadsSetTheRequestedState`; `WorkModeTests.cdWmd03PlacerTogglesWithinOneTick` | pass | `0cddb71` |
| CD-WMD-04 | `WorkModePolicyTest`; `PayloadTests.cdSec06Wmd04TwoOperatorsInOneTick`; `WorkModeTests.cdWmd04OperatorTogglesAnyPasture` | pass | `0cddb71` |
| CD-WMD-05 | `ClaimRegistryTest`; `AssignmentTests.cdWmd05WorkModeOffReleasesEveryClaim` | pass | `0cddb71` |
| CD-WMD-06 | `WorkModeTests.cdWmd06OffPastureGivesNoClaims` | pass | `0cddb71` |
| CD-WMD-07 | `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding` | pass | `0cddb71` |
| CD-ASG-01 | `AssignmentTests.cdAsg01OnlyOwnedHealthyPastured`; `WheelRowTests.cdAsg01OnlyAHealthyPasturedWaterTypeClaimsTheWheel` | pass | `0cddb71` |
| CD-ASG-02 | `RoleSelectionTest` | pass | `0cddb71` |
| CD-ASG-03 | `AssignerTest`; `AssignmentTests.cdAsg03NearestFurnaceWins` | pass | `0cddb71` |
| CD-ASG-04 | `AssignerTest`; `WheelRowTests.cdAsg04AzumarillLeavesTheDryWheelAlone` | pass | `0cddb71` |
| CD-ASG-05 | `AssignerTest`; `PumpTests.cdAsg05UnleashedWaterTypeLeavesAnEmptyTankAlone` | pass | `0cddb71` |
| CD-ASG-06 | `BlockScanCursorTest`; `ScanSchedulerTest` ×3 | pass | `0cddb71` |
| CD-ASG-07 | `BlockScanCursorTest` | pass | `0cddb71` |
| CD-ASG-08 | `WorkAreaTest`; `AssignmentTests.cdAsg08HorizontalWorkAreaEdge`; `AssignmentTests.cdAsg08VerticalWorkAreaEdge` | pass | `0cddb71` |
| CD-ASG-09 | `WorkSpotsTest` ×3; `WideWorkerTests.cdAsg09WideWorkerGetsASpotItsBodyFits`; checklist 14 | pass, play-test | `0cddb71` |
| CD-ASG-10 | `BuiltInRoleFilesTest` ×2; `WorkSpotsTest` ×2 | pass | `0cddb71` |
| CD-ASG-11 | `ArrivalTest` ×2; `WorkerMachineTest`; `AssignmentTests.cdAsg11WorkingWithinOneTickOfArrival` | pass | `0cddb71` |
| CD-ASG-12 | `WorkerMachineTest` ×2; `AssignmentTests.cdAsg12StandbyKeepsClaimAndResumes`; `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `0cddb71` |
| CD-ASG-13 | `WorkerMachineTest`; `AssignmentTests.cdAsg13UnreachableTargetTimesOut` | pass | `0cddb71` |
| CD-ASG-14 | `WorkerMachineTest`; checklist 15 | pass, play-test | `0cddb71` |
| CD-ASG-15 | `AssignmentTests.cdAsg15LeavingThePastureReleasesTheClaim` | pass | `0cddb71` |
| CD-ASG-16 | `DefaultPriorityTest` ×2; `RoleSelectionTest` | pass | `0cddb71` |
| CD-ASG-17 | `WorkerMachineTest` | pass | `0cddb71` |
| CD-ASG-18 | `RestMachineTest` ×2; `WorkerMachineTest` | pass | `0cddb71` |
| CD-ASG-19 | `AssignerTest`; `WorkerMachineTest`; `WheelRowTests.cdAsg19WheelDriverMovesToADryWheel` | pass | `0cddb71` |
| CD-ASG-20 | `WorkerMachineTest`; `LeashClaimTests.cdAsg20LeashedWorkerStaysOnItsBoundTarget` | pass | `0cddb71` |
| CD-CLM-01 | `ClaimRegistryTest` | pass | `0cddb71` |
| CD-CLM-02 | `ClaimRegistryTest` | pass | `0cddb71` |
| CD-CLM-03 | `ClaimRegistryTest` | pass | `0cddb71` |
| CD-CLM-04 | `WheelDriverTests.cdClm04LargeWheelIsOneTarget` | pass | `0cddb71` |
| CD-CLM-05 | `LeashClaimTests.cdClm05BindTakesTheTargetFromAnAutoWorker` | pass | `0cddb71` |
| CD-CLM-06 | `ClaimRegistryTest`; `LeashIndexTest`; `AssignmentTests.cdWmd05WorkModeOffReleasesEveryClaim`; `LeashClaimTests.cdClm06ReservedTargetWaitsForItsWorker`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `0cddb71` |
| CD-CLM-07 | `AssignmentTests.cdClm07ClaimsAreNotSaved`; test pack `restart`; checklist 13 | pass, play-test | `0cddb71` |
| CD-CLM-08 | `AssignmentTests.cdClm08BrokenReplacedUnloaded` | pass | `0cddb71` |
| CD-CLM-09 | `ClaimRegistryTest`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `0cddb71` |
| CD-LSH-01 | `LeashTests.cdLsh01SelectsOwnedPasturedPokemon` | pass | `0cddb71` |
| CD-LSH-02 | `LeashTests.cdLsh02ClickIsCancelledBeforeCobblemon`; checklist 16 | pass, play-test | `0cddb71` |
| CD-LSH-03 | `BindRulesTest`; `LeashTests.cdLsh03BindsFurnaceAndWorksIt`; `WheelRowTests.cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels` | pass | `0cddb71` |
| CD-LSH-04 | `LeashTests.cdLsh04FurnaceScreenStaysShut`; checklist 17 | pass, play-test | `0cddb71` |
| CD-LSH-05 | `BindRulesTest` ×2; `GeneratorTests.cdLsh05PikachuOnBattery`; `LeashTests.cdLsh05Lsh06RefusalsAndTheBoundMessage` | pass | `0cddb71` |
| CD-LSH-06 | `LeashTests.cdLsh05Lsh06RefusalsAndTheBoundMessage`; `WheelRowTests.cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels`; checklist 18 | pass, play-test | `0cddb71` |
| CD-LSH-07 | `BindingTest`; `LeashTests.cdLsh07BindingSurvivesRestartAndToggle`; checklist 24 | pass, play-test | `0cddb71` |
| CD-LSH-08 | `LeashResetTests.cdLsh08RecallAndTargetLoss` | pass | `0cddb71` |
| CD-LSH-09 | `LeashClaimTests.cdLsh09UnloadKeepsTheBinding` | pass | `0cddb71` |
| CD-LSH-10 | `LeashTests.cdLsh10SneakClickClearsTheBinding` | pass | `0cddb71` |
| CD-LSH-11 | `LeashIndexTest`; `LeashTests.cdLsh11RebindReplacesTheBinding`; `WheelRowTests.cdLsh03Lsh06Lsh11VaporeonBindsAndRebindsWheels` | pass | `0cddb71` |
| CD-LSH-12 | `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack`; `RestTests.cdLsh12BindingMadeWhileTiredWaitsUntilFull` | pass | `0cddb71` |
| CD-LSH-13 | `LeashResetTests.cdLsh08RecallAndTargetLoss`; `LeashResetTests.cdLsh13Lsh14EvolutionWithNoFittingRoleClearsTheBinding`; `LeashResetTests.cdLsh24MovingToAnotherPastureClearsTheBinding`; `LeashResetTests.cdLsh25ReleaseClearsTheBinding`; `LeashResetTests.cdLsh26TradeClearsTheBindingData`; `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding`; `LeashTests.cdLsh10SneakClickClearsTheBinding`; `LeashTests.cdLsh11RebindReplacesTheBinding`; checklist 19 | pass, play-test | `0cddb71` |
| CD-LSH-14 | `LeashResetTests.cdLsh13Lsh14EvolutionWithNoFittingRoleClearsTheBinding`; `WheelRowTests.cdLsh14EvolutionWithoutWaterClearsTheWheelBinding` | pass | `0cddb71` |
| CD-LSH-15 | `WorkLeashRecipeTest`; `LeashTests.cdLsh15RecipeCraftsOneLeash` | pass | `0cddb71` |
| CD-LSH-16 | `BindRulesTest`; `RoleSelectionTest` | pass | `0cddb71` |
| CD-LSH-17 | `BindRulesTest` ×2; `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination` | pass | `0cddb71` |
| CD-LSH-18 | `BindRulesTest` ×2; `BindingTest`; `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination` | pass | `0cddb71` |
| CD-LSH-19 | `PendingSourceTest`; `LeashTests.cdLsh19AirClickAndTimeoutClearThePendingSource` | pass | `0cddb71` |
| CD-LSH-20 | `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination`; checklist 20 | pass, play-test | `0cddb71` |
| CD-LSH-21 | `LeashClaimTests.cdLsh21CannotReachMessage`; checklist 21 | pass, play-test | `0cddb71` |
| CD-LSH-22 | `CreateLeashTests.cdLsh22WrenchPickupClearsBinding` | pass | `0cddb71` |
| CD-LSH-23 | `CreateLeashTests.cdLsh23ContraptionClearsBinding` | pass | `0cddb71` |
| CD-LSH-24 | `LeashResetTests.cdLsh24MovingToAnotherPastureClearsTheBinding` | pass | `0cddb71` |
| CD-LSH-25 | `LeashResetTests.cdLsh25ReleaseClearsTheBinding` | pass | `0cddb71` |
| CD-LSH-26 | `LeashResetTests.cdLsh26TradeClearsTheBindingData` | pass | `0cddb71` |
| CD-LSH-27 | `LeashResetTests.cdLsh27Wmd07BreakingThePastureClearsEveryBinding` | pass | `0cddb71` |
| CD-AI-01 | `BrainTests.cdAi01BrainListsWorkActivity` | pass | `0cddb71` |
| CD-AI-02 | `BrainTests.cdAi02WorkingWorkerHoldsItsSpot` | pass | `0cddb71` |
| CD-AI-03 | `BrainTests.cdAi03ReleaseRestoresActivitiesWithin20Ticks` | pass | `0cddb71` |
| CD-AI-04 | `BrainTests.cdAi04RemakeBrainRestoresWorkNextTick` | pass | `0cddb71` |
| CD-AI-05 | `LookMathTest` ×3; `BrainTests.cdAi05FacesTheTarget` | pass | `0cddb71` |
| CD-AI-06 | `BrainTests.cdAi06NonWorkersKeepCobblemonActivities` | pass | `0cddb71` |
| CD-AI-07 | `RestTests.cdAi07NightSleepEndsWhenWorkStarts`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `0cddb71` |
| CD-AI-08 | `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `0cddb71` |
| CD-AI-09 | `BrainTests.cdAi09LookTaskRunsInsideWork` | pass | `0cddb71` |
| CD-FX-01 | `EffectClockTest` | pass | `0cddb71` |
| CD-FX-02 | `BuiltInRoleFilesTest` | pass | `0cddb71` |
| CD-FX-03 | `RoleCodecTest` ×2 | pass | `0cddb71` |
| CD-FX-04 | `BuiltInRoleFilesTest` ×10; `ParticleShotsTest`; `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; `EffectTests.cdFx07FocusLeavesWithWorking`; checklist 30 | pass, play-test | `0cddb71` |
| CD-FX-05 | `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; checklist 31 | pass, play-test | `0cddb71` |
| CD-FX-06 | `EffectTests.cdFx06FocusIsNeverSaved` | pass | `0cddb71` |
| CD-FX-07 | `EffectTests.cdFx04Fx05FocusSitsAtTheTargetCenterAndIsUntouchable`; `EffectTests.cdFx07FocusLeavesWithWorking` | pass | `0cddb71` |
| CD-FX-08 | `RecipientsTest` | pass | `0cddb71` |
| CD-FX-09 | checklist 35 | play-test | `0cddb71` |
| CD-FX-10 | `SleepEffectTest`; `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer`; checklist 25 | pass, play-test | `0cddb71` |
| CD-FX-11 | `ParticleCatalogTest`; `ParticleShotsTest`; `EffectTests.cdFx11CatalogReadsCobblemonParticles` | pass | `0cddb71` |
| CD-STA-01 | `StaminaMathTest` | pass | `0cddb71` |
| CD-STA-02 | `StaminaMathTest` | pass | `0cddb71` |
| CD-STA-03 | `StaminaMathTest` | pass | `0cddb71` |
| CD-STA-04 | `StaminaStoreTest` ×2; `StaminaStoreTests.cdSta04And05StaminaStartsFullAndFollowsThePokemon`; checklist 26 | pass, play-test | `0cddb71` |
| CD-STA-05 | `StaminaStoreTest` ×2; `StaminaStoreTests.cdSta04And05StaminaStartsFullAndFollowsThePokemon` | pass | `0cddb71` |
| CD-STA-06 | `StaminaMathTest` ×2 | pass | `0cddb71` |
| CD-STA-07 | `StaminaMathTest` ×2 | pass | `0cddb71` |
| CD-STA-08 | `StaminaMathTest` ×2 | pass | `0cddb71` |
| CD-STA-09 | `AssignerTest`; `RestMachineTest` | pass | `0cddb71` |
| CD-STA-10 | `StaminaMathTest` ×2 | pass | `0cddb71` |
| CD-STA-11 | `OfflineRecoveryTest` ×3; `StaminaStoreTest`; `StaminaStoreTests.cdSta11PcTimeRecoversAtBaseRate` | pass | `0cddb71` |
| CD-STA-12 | `StaminaStoreTest`; `StaminaStoreTests.cdSta12TimeCommandsChangeNothing` | pass | `0cddb71` |
| CD-STA-13 | `RestMachineTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack`; `RestTests.cdSta13TiredAutoWorkerFreesItsMachineForTheNextScan` | pass | `0cddb71` |
| CD-STA-14 | `RestMachineTest`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `0cddb71` |
| CD-STA-15 | `ViewSyncTest` ×5 | pass | `0cddb71` |
| CD-BED-01 | `BedContentFilesTest` ×3; `BedTierTest` ×3; `BedContentTests.cdBed01RegistryListsFourBedsWithTheirTiers` | pass | `0cddb71` |
| CD-BED-02 | `BedContentFilesTest` ×4; `BedContentTests.cdBed02EachBedCraftsFromTheBedBelow` | pass | `0cddb71` |
| CD-BED-03 | `BedTierTest`; `StaminaMathTest` ×2; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `0cddb71` |
| CD-BED-04 | `StaminaMathTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `0cddb71` |
| CD-BED-05 | `BedSelectorTest` ×3; `RestMachineTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `0cddb71` |
| CD-BED-06 | `BedSelectorTest` | pass | `0cddb71` |
| CD-BED-07 | `RestTests.cdBed07OnlyABedInsideTheWorkAreaIsClaimed` | pass | `0cddb71` |
| CD-BED-08 | `BedSelectorTest`; `RestMachineTest`; `RestTests.cdBed08LeashedWorkerRestsInPlaceAndGoesBack` | pass | `0cddb71` |
| CD-BED-09 | `BedSelectorTest`; `RestTests.cdBed09WailordSleepsOnAHayBed` | pass | `0cddb71` |
| CD-BED-10 | `RestMachineTest`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain`; `RestTests.cdBed09WailordSleepsOnAHayBed` | pass | `0cddb71` |
| CD-BED-11 | `RestMachineTest`; `RestTests.cdBed11BrokenBedMeansRestInPlace` | pass | `0cddb71` |
| CD-BED-12 | `RestMachineTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly`; `RestTests.cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain` | pass | `0cddb71` |
| CD-BED-13 | `RestTests.cdBed13OnlyTiredWorkersOfWorkModePasturesGoToBed` | pass | `0cddb71` |
| CD-BED-14 | `RestTests.cdBed14SleeperClaimsABedAfterRestart`; checklist 27 | pass, play-test | `0cddb71` |
| CD-HEAL-01 | `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `0cddb71` |
| CD-HEAL-02 | `HealTargetsTest` | pass | `0cddb71` |
| CD-HEAL-03 | `HealTargetsTest` | pass | `0cddb71` |
| CD-HEAL-04 | `HealTransactionTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `0cddb71` |
| CD-HEAL-05 | `HealTransactionTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `0cddb71` |
| CD-HEAL-06 | `HealTargetsTest`; `HealerTests.cdHeal06OnlyNonFairyAlliesOfTheHealersPasture` | pass | `0cddb71` |
| CD-HEAL-07 | `HealTargetsTest`; `HealerTests.cdHeal07NearestTiredAllyFirst` | pass | `0cddb71` |
| CD-HEAL-08 | `HealTargetsTest` ×2 | pass | `0cddb71` |
| CD-HEAL-09 | `ClaimRegistryTest`; `HealTargetsTest`; `HealerTests.cdHeal09OneHealerPerAlly` | pass | `0cddb71` |
| CD-HEAL-10 | `HealTransactionTest` ×2; `RestMachineTest`; `HealerTests.cdHeal01HealerHealsASleepingAlly` | pass | `0cddb71` |
| CD-HEAL-11 | `HealTargetsTest`; `RestMachineTest`; `HealerTests.cdHeal11HealCancelsAtNoCost` | pass | `0cddb71` |
| CD-HEAL-12 | `StaminaMathTest` ×2 | pass | `0cddb71` |
| CD-HEAL-13 | `StaminaMathTest` ×2 | pass | `0cddb71` |
| CD-HEAL-14 | `MuseTests.cdHeal14LeashedMuseNeverHealsATiredAlly` | pass | `0cddb71` |
| CD-HEAL-15 | `HealEffectTest`; `EffectPacketTests.cdFx10Heal15SleepAndHealEffectsReachANearbyPlayer`; checklist 28 | pass, play-test | `0cddb71` |
| CD-HEAL-16 | `HealEffectTest`; `HealerTests.cdHeal16FloralHealingRunsWithTheHealerAsUser` | pass | `0cddb71` |
| CD-HEAL-17 | `RestMachineTest`; `HealerTests.cdHeal17SpentHealerGoesToBedOrRestsInPlace` | pass | `0cddb71` |
| CD-WHL-01 | `WheelDriverTests.cdWhl01Whl02SmallAndLargeWheelsTurnAtCreateValues` | pass | `0cddb71` |
| CD-WHL-02 | `WheelDriverTests.cdWhl01Whl02SmallAndLargeWheelsTurnAtCreateValues` | pass | `0cddb71` |
| CD-WHL-03 | `WheelDriverTests.cdWhl03RealWaterMeansStandby` | pass | `0cddb71` |
| CD-WHL-04 | `DriveNbtTest`; `DriveBehaviourTests.cdWhl04NewWheelsStartPositive` | pass | `0cddb71` |
| CD-WHL-05 | `WheelDriverTests.cdWhl05BinderFlips` | pass | `0cddb71` |
| CD-WHL-06 | `WheelDriverTests.cdWhl06TakeoverKeepsDirection` | pass | `0cddb71` |
| CD-WHL-07 | `DriveNbtTest`; `CreateFlipTests.cdWhl07Crk08FlipsByBoundWorkersSurviveARestart`; `DriveBehaviourTests.cdWhl07DirectionSurvivesReload`; test pack `restart`; checklist 33 | pass, play-test | `0cddb71` |
| CD-WHL-08 | `WheelDriverTests.cdWhl08StopWithinTwoTicks` | pass | `0cddb71` |
| CD-WHL-09 | `DriveNbtTest`; `WheelDriverTests.cdWhl09ClientSeesCapacity`; checklist 32 | pass, play-test | `0cddb71` |
| CD-WHL-10 | `MixinGateTest` ×2; `MixinStateTest` | pass | `0cddb71` |
| CD-GEN-01 | `GeneratorTests.cdGen01CapabilityTargets` | pass | `0cddb71` |
| CD-GEN-02 | `FeCurveTest` ×2 | pass | `0cddb71` |
| CD-GEN-03 | `FeCurveTest` ×3 | pass | `0cddb71` |
| CD-GEN-04 | `NearestFacesTest` ×3; `GeneratorTests.cdGen04NearestAcceptingFace` | pass | `0cddb71` |
| CD-GEN-05 | `EnergyPortTest` ×2 | pass | `0cddb71` |
| CD-GEN-06 | `EnergyPortTest`; `GeneratorTests.cdGen06FullBatteryMeansStandby` | pass | `0cddb71` |
| CD-GEN-07 | `GeneratorTests.cdGen07Ae2RatioAtLv50` | pass | `0cddb71` |
| CD-STK-01 | `StokerTests.cdStk01FurnaceSmokerAndBlastFurnaceAreClaimed` | pass | `0cddb71` |
| CD-STK-02 | `StokerTests.cdStk02EightIronIn1600Ticks`; `StokerTests.cdStk02SmokerCooksEightBeefIn800Ticks` | pass | `0cddb71` |
| CD-STK-03 | `StokerTests.cdStk03CoalIsNeverConsumed` | pass | `0cddb71` |
| CD-STK-04 | `StokerTests.cdStk04LitIsSetOneTickAfterWorkingStarts` | pass | `0cddb71` |
| CD-STK-05 | `StokerTests.cdStk05FullOutputGivesStandby` | pass | `0cddb71` |
| CD-STK-06 | `StokerTests.cdStk06ReleaseLetsAFurnaceWithNoFuelGoOut`; `StokerTests.cdStk06ReleaseLetsVanillaBurnTheCoal` | pass | `0cddb71` |
| CD-BRN-01 | `HeatGateTest`; `BurnerTests.cdBrn01Brn06KindledWhileWorkedSmoulderingAfterRelease` | pass | `0cddb71` |
| CD-BRN-02 | `BurnerTests.cdBrn02EmptyBurnerIsNeverClaimed` | pass | `0cddb71` |
| CD-BRN-03 | `BurnerTests.cdBrn03NoBasinMeansStandby` | pass | `0cddb71` |
| CD-BRN-04 | `BurnerTests.cdBrn04FuelBurnsDownAsNormal` | pass | `0cddb71` |
| CD-BRN-05 | `BurnerTests.cdBrn05BoilerCountsOneHeat` | pass | `0cddb71` |
| CD-BRN-06 | `BurnerTests.cdBrn01Brn06KindledWhileWorkedSmoulderingAfterRelease` | pass | `0cddb71` |
| CD-BRN-07 | `MixinGateTest` | pass | `0cddb71` |
| CD-SUP-01 | `HeatGateTest`; `BurnerTests.cdSup01Lv50Seething` | pass | `0cddb71` |
| CD-SUP-02 | `HeatGateTest` | pass | `0cddb71` |
| CD-SUP-03 | `BurnerTests.cdSup03Sup04Lv49ThenLv50` | pass | `0cddb71` |
| CD-SUP-04 | `HeatGateTest`; `BurnerTests.cdSup03Sup04Lv49ThenLv50` | pass | `0cddb71` |
| CD-CRK-01 | `BuiltInRoleFilesTest` | pass | `0cddb71` |
| CD-CRK-02 | `CrankClockTest` ×2; `CrankerTests.cdCrk02Bal04CrankHolds32RpmFor600Ticks` | pass | `0cddb71` |
| CD-CRK-03 | `CrankerTests.cdCrk03Crk05NewCranksTurnAtCreateValues` | pass | `0cddb71` |
| CD-CRK-04 | `CrankerTests.cdCrk04ValveHandleIsNotATarget` | pass | `0cddb71` |
| CD-CRK-05 | `DriveNbtTest`; `CrankerTests.cdCrk03Crk05NewCranksTurnAtCreateValues`; `DriveBehaviourTests.cdCrk05NewCrankStartsPositive` | pass | `0cddb71` |
| CD-CRK-06 | `CrankerTests.cdCrk06BinderFlips` | pass | `0cddb71` |
| CD-CRK-07 | `CrankerTests.cdCrk07NoNetworkStillWorks` | pass | `0cddb71` |
| CD-CRK-08 | `DriveNbtTest`; `CreateFlipTests.cdWhl07Crk08FlipsByBoundWorkersSurviveARestart`; `DriveBehaviourTests.cdCrk08DirectionSurvivesReload`; test pack `restart`; checklist 34 | pass, play-test | `0cddb71` |
| CD-CRY-01 | `CrystalTenderTests.cdCry01RockTypesClaimBuddingAmethystAndCertus` | pass | `0cddb71` |
| CD-CRY-02 | `TickClockTest` ×2; `CrystalTenderTests.cdCry02TenderGrowsRealAmethyst` | pass | `0cddb71` |
| CD-CRY-03 | `CrystalTenderTests.cdCry03FullClustersMeanStandby` | pass | `0cddb71` |
| CD-CRY-04 | `CoreTests.cdCry04CrystalTenderWithoutAe2` | pass | `0cddb71` |
| CD-CRY-05 | Gradle `verifyNoAe2` | pass | `0cddb71` |
| CD-BRF-01 | `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder` | pass | `0cddb71` |
| CD-BRF-02 | `BrewingRulesTest`; `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews` | pass | `0cddb71` |
| CD-BRF-03 | `BrewingRulesTest`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews` | pass | `0cddb71` |
| CD-BRF-04 | `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews` | pass | `0cddb71` |
| CD-BRF-05 | `BrewingRulesTest`; `BrewingTests.cdBrf02BrewStokerBrewsWithNoBlazePowder`; `BrewingTests.cdBrf03BlazePowderStaysThroughThreeBrews`; `BrewingTests.cdBrf05ReleaseBeforeABrewLeavesFuel0`; `BrewingTests.cdBrf05ServerStopLeavesNoFuel` | pass | `0cddb71` |
| CD-BRW-01 | `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `0cddb71` |
| CD-BRW-02 | `BrewingRulesTest` ×2; `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `0cddb71` |
| CD-BRW-03 | `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `0cddb71` |
| CD-BRW-04 | `BrewingTests.cdBrw02WorkedBrewTakes200Ticks` | pass | `0cddb71` |
| CD-PMP-01 | `FluidPortTest`; `PumpTests.cdPmp01BindsToATankABasinAndACauldron`; `PumpTests.cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull`; `PumpTests.cdPmp03Lv1PumpAdds5000MbIn1000Ticks`; `PumpTests.cdPmp06FiveCauldronsOnRealBlocks` | pass | `0cddb71` |
| CD-PMP-02 | `BuiltInRoleFilesTest` | pass | `0cddb71` |
| CD-PMP-03 | `FluidPortTest`; `PumpParamsTest` ×2; `PumpTests.cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull`; `PumpTests.cdPmp03Lv1PumpAdds5000MbIn1000Ticks` | pass | `0cddb71` |
| CD-PMP-04 | `CauldronFillTest` ×3; `PumpTests.cdPmp01BindsToATankABasinAndACauldron` | pass | `0cddb71` |
| CD-PMP-05 | `FluidPortTest`; `PumpTests.cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull` | pass | `0cddb71` |
| CD-PMP-06 | `CauldronFillTest`; `PumpTests.cdPmp06FiveCauldronsOnRealBlocks` | pass | `0cddb71` |
| CD-MUS-01 | `MuseTests.cdHeal14LeashedMuseNeverHealsATiredAlly`; `MuseTests.cdMus03Lv50MuseGivesATableTheOffersOfEightShelves`; `MuseTests.cdMus05StaminaDrainsOnlyWhileTheMenuIsOpen` | pass | `0cddb71` |
| CD-MUS-02 | `BuiltInRoleFilesTest` | pass | `0cddb71` |
| CD-MUS-03 | `MuseBonusTest` ×2; `MuseTests.cdMus03Lv50MuseGivesATableTheOffersOfEightShelves` | pass | `0cddb71` |
| CD-MUS-04 | `MuseBonusTest` ×2 | pass | `0cddb71` |
| CD-MUS-05 | `MuseTests.cdMus03Lv50MuseGivesATableTheOffersOfEightShelves`; `MuseTests.cdMus05StaminaDrainsOnlyWhileTheMenuIsOpen` | pass | `0cddb71` |
| CD-MUS-06 | `MixinGateTest` ×2 | pass | `0cddb71` |
| CD-BAL-01 | `BalanceFilesTest` | pass | `0cddb71` |
| CD-BAL-02 | `BalanceTest` | pass | `0cddb71` |
| CD-BAL-03 | `BalanceTest` ×2 | pass | `0cddb71` |
| CD-BAL-04 | `CrankerTests.cdCrk02Bal04CrankHolds32RpmFor600Ticks`; `WheelDriverTests.cdBal04WheelsNeverExceedCreate` | pass | `0cddb71` |
| CD-BAL-05 | `BalanceFilesTest` | pass | `0cddb71` |
| CD-BAL-06 | `WorkModeTests.cdBal06SeventeenthPokemonIsRefused` | pass | `0cddb71` |
| CD-BAL-07 | `BalanceFilesTest` | pass | `0cddb71` |
| CD-UI-01 | checklist 6 | play-test | `0cddb71` |
| CD-UI-02 | checklist 7 | play-test | `0cddb71` |
| CD-UI-03 | `WorkModePolicyTest`; checklist 8 | pass, play-test | `0cddb71` |
| CD-UI-04 | `PanelLayoutTest`; checklist 9 | pass, play-test | `0cddb71` |
| CD-UI-05 | `PanelCadenceTest` ×2 | pass | `0cddb71` |
| CD-UI-06 | `HeadBarRangeTest`; checklist 22 | pass, play-test | `0cddb71` |
| CD-UI-07 | `StateIconsTest` ×2; checklist 10 | pass, play-test | `0cddb71` |
| CD-UI-09 | `OverviewRequestsTest`; `LeashTests.cdUi09OverviewHoldsOnlyOwnTargets`; checklist 23 | pass, play-test | `0cddb71` |
| CD-UI-11 | `LangTest`; `StaminaBarTest` | pass | `0cddb71` |
| CD-UI-12 | `LangTest` ×5 | pass | `0cddb71` |
| CD-UI-13 | `PanelMixinCheckTest` | pass | `0cddb71` |
| CD-UI-14 | `ClaimsReportTest` ×2; `CommandTests.cdUi14ClaimsListsTheBedAndTheHealClaim`; `CommandTests.cdUi14ClaimsListsTheWorkClaimAndTheBinding` | pass | `0cddb71` |
| CD-UI-15 | `StaminaReportTest` ×4; `CommandTests.cdUi15StaminaShowsValueStateAndRule` | pass | `0cddb71` |
| CD-CMP-01 | `CobbleworkersRestTest` ×2; checklist 36 | pass, play-test | `0cddb71` |
| CD-CMP-02 | `CobbleworkersRestTest` ×3 | pass | `0cddb71` |
| CD-CMP-03 | checklist 37 | play-test | `0cddb71` |
| CD-CMP-04 | checklist 38 | play-test | `0cddb71` |
| CD-CMP-05 | `CobblebaseHooksTest` ×3; `MixinStateTest`; checklist 39 | pass, play-test | `0cddb71` |
| CD-CMP-06 | test pack `compat` (`compat_cobblebase`) | pass | `0cddb71` |
| CD-CMP-07 | test pack `compat` (`compat_cobbleworkers`) | pass | `0cddb71` |
| CD-SEC-01 | `WorkModePolicyTest`; `WorkModeTests.cdSec01StrangerCannotToggle` | pass | `0cddb71` |
| CD-SEC-02 | `WorkModePolicyTest`; `WorkModeTests.cdSec02OwnerlessPastureAcceptsOperatorsOnly` | pass | `0cddb71` |
| CD-SEC-03 | `WorkModePolicyTest` | pass | `0cddb71` |
| CD-SEC-04 | `WorkModePolicyTest`; `WorkModeTests.cdSec04DistanceAndDimension` | pass | `0cddb71` |
| CD-SEC-05 | `PayloadCodecTest`; `PayloadTests.cdSec05Wmd03PlacerPayloadsSetTheRequestedState`; `WorkModeTests.cdSec05RepeatedOnStaysOn` | pass | `0cddb71` |
| CD-SEC-06 | `PayloadCodecTest`; `PayloadTests.cdSec06Wmd04TwoOperatorsInOneTick`; `WorkModeTests.cdSec06TwoOperatorsSameTick` | pass | `0cddb71` |
| CD-SEC-07 | `LeashTests.cdSec07OnlyTheOwnerSelects` | pass | `0cddb71` |
| CD-SEC-08 | `BindRulesTest` | pass | `0cddb71` |
| CD-SEC-09 | `CommandTests.cdSec09LevelZeroPlayerIsRefused` | pass | `0cddb71` |
| CD-SEC-10 | `CreateFlipTests.cdSec10StrangersAndIdleTargetsNeverFlip` | pass | `0cddb71` |
| CD-SEC-11 | `LeashTests.cdSec11CancelledClickNeverBinds` | pass | `0cddb71` |
| CD-PERF-01 | `EngineTimingTest`; `RepositoryFilesTest`; `PerfTests.cdPerf01And02ReferenceWorld` | pass | `0cddb71` |
| CD-PERF-02 | `EngineTimingTest`; `PerfTests.cdPerf01And02ReferenceWorld` | pass | `0cddb71` |
| CD-PERF-03 | `TargetScanTest` | pass | `0cddb71` |
| CD-PERF-04 | `EffectTests.cdPerf04NoFocusOutlivesItsClaim` | pass | `0cddb71` |
| CD-PERF-05 | `LangTest`; `OnceLoggerTest` ×2; `PerfTests.cdPerf01And02ReferenceWorld` | pass | `0cddb71` |
| CD-CFG-01 | `ConfigTest` ×2; test pack `restart` | pass | `0cddb71` |
| CD-CFG-02 | `ConfigTest` | pass | `0cddb71` |
| CD-CFG-03 | `FeCurveTest` ×2; `RoleDataTests.cdCfg03Cfg13DatapackSetsTheGeneratorRates` | pass | `0cddb71` |
| CD-CFG-04 | `StaminaMathTest` ×3 | pass | `0cddb71` |
| CD-CFG-05 | `RoleSetTest`; `RoleDataTests.cdCfg05DisabledRoleGetsNoClaims` | pass | `0cddb71` |
| CD-CFG-06 | `ConfigTest` | pass | `0cddb71` |
| CD-CFG-07 | `RoleCodecTest`; `RoleDataTests.cdCfg07DatapackLetsWaterTypesStoke` | pass | `0cddb71` |
| CD-CFG-08 | `RoleDataTests.cdCfg08ReloadAppliesChangedTypes`; test pack `reload` | pass | `0cddb71` |
| CD-CFG-09 | `RoleCodecTest` | pass | `0cddb71` |
| CD-CFG-10 | `RoleSetTest`; `CoreTests.cdCfg10CreateRolesOffWithoutCreate` | pass | `0cddb71` |
| CD-CFG-11 | `BlockScanCursorTest`; `ConfigTest`; `ScanSchedulerTest` | pass | `0cddb71` |
| CD-CFG-12 | `ConfigTest`; `WorkerMachineTest` | pass | `0cddb71` |
| CD-CFG-13 | `FeCurveTest`; `PumpParamsTest`; `RoleDataTests.cdCfg03Cfg13DatapackSetsTheGeneratorRates` | pass | `0cddb71` |
| CD-CFG-14 | `ConfigTest`; `WorkAreaTest` | pass | `0cddb71` |
| CD-CFG-15 | `ConfigTest`; `WorkerMachineTest` | pass | `0cddb71` |
| CD-REL-01 | `RepositoryFilesTest`; checklist 40 | pass, play-test | `0cddb71` |
| CD-REL-02 | `RepositoryFilesTest` | pass | `0cddb71` |
| CD-REL-03 | `RepositoryFilesTest` | pass | `0cddb71` |
| CD-REL-04 | `ReleaseWorkflowTest`; checklist 42 | pass, open (M9) | `0cddb71` |
| CD-REL-05 | `ModMetadataTest`; `ReleaseWorkflowTest` | pass | `0cddb71` |
| CD-REL-06 | `ModMetadataTest`; `RepositoryFilesTest` | pass | `0cddb71` |
| CD-REL-07 | Gradle `verifyJar` | pass | `0cddb71` |
| CD-REL-08 | `PlayerGuideTest` ×4 | pass | `0cddb71` |
| CD-REL-09 | `ManualChecklistTest` ×2 | pass | `0cddb71` |
| CD-REL-10 | `SmokeTests.cdRel10PinnedModsLoad` | pass | `0cddb71` |
| CD-REL-12 | `ReleaseWorkflowTest` ×5; checklist 41 | pass, play-test | `0cddb71` |
| CD-REL-13 | this report (the run on the commit) and the play-test note | play-test | `0cddb71` |
| CD-REL-14 | after the Phase 1 gate (M8) | open (M8) | `0cddb71` |
| CD-REL-15 | after the Phase 1 gate (M9) | open (M9) | `0cddb71` |

### Deferred rows

The end-of-phase pass of M5 Task 9 gave each row of its deferred list a test. The table above names the tests of each row. This list gives the result of each row, by the task that deferred it:

- M1 Task 10: CD-FX-04 pass, play-test; CD-FX-05 pass, play-test; CD-FX-06 pass; CD-FX-07 pass; CD-PERF-04 pass.
- M1 Tasks 11 to 13: CD-LSH-05 pass; CD-LSH-06 pass, play-test; CD-LSH-09 pass; CD-LSH-10 pass; CD-LSH-11 pass; CD-LSH-13 pass, play-test; CD-LSH-14 pass; CD-LSH-15 pass; CD-LSH-17 pass; CD-LSH-18 pass; CD-LSH-19 pass; CD-LSH-20 pass, play-test; CD-LSH-21 pass, play-test; CD-LSH-24 pass; CD-LSH-25 pass; CD-LSH-26 pass; CD-LSH-27 pass; CD-WMD-07 pass; CD-CLM-05 pass; CD-CLM-06 pass; CD-ASG-20 pass; CD-SEC-07 pass; CD-SEC-11 pass; CD-UI-14 pass; CD-SEC-09 pass; CD-SEC-05 pass; CD-WMD-03 pass; CD-SEC-06 pass; CD-WMD-04 pass.
- M2 Tasks 1 to 3: CD-STA-11 pass; CD-STA-12 pass; CD-BED-01 pass; CD-BED-02 pass.
- M2 Tasks 4 to 6: CD-STA-13 pass; CD-LSH-12 pass; CD-BED-07 pass; CD-BED-11 pass; CD-BED-13 pass; CD-BED-14 pass, play-test; CD-CLM-06 pass; CD-WMD-05 pass; CD-FX-10 pass, play-test; CD-HEAL-06 pass; CD-HEAL-07 pass; CD-HEAL-09 pass; CD-HEAL-11 pass; CD-HEAL-15 pass, play-test; CD-HEAL-17 pass; CD-UI-15 pass; CD-UI-14 pass; CD-SEC-09 pass.
- M3 Tasks 1 to 3: CD-ASG-01 pass; CD-ASG-04 pass; CD-ASG-19 pass; CD-LSH-03 pass; CD-LSH-06 pass, play-test; CD-LSH-11 pass; CD-LSH-14 pass.
- M3 Tasks 4 to 6: CD-CRK-04 pass; CD-CRK-07 pass; CD-BRN-02 pass; CD-BRN-03 pass; CD-BRN-04 pass; CD-SEC-10 pass.
- M4 Tasks 1 to 2: CD-GEN-04 pass; CD-GEN-07 pass; CD-CFG-03 pass; CD-CFG-13 pass; CD-CFG-05 pass.
- M4 Tasks 3 to 5: CD-BRF-05 pass; CD-PMP-06 pass.
- M5 Tasks 5 to 7: CD-CLM-07 pass, play-test; CD-LSH-07 pass, play-test; CD-WHL-07 pass, play-test; CD-CRK-08 pass, play-test; CD-STA-04 pass, play-test; CD-BED-14 pass, play-test.

### Tests that cite no row

These GameTests cite no row of the spec sheet: `BurnerTests.creativeBlazeBurnerHasNoDemand` (pass), `SmokeTests.smokePastureTethersAPokemon` (pass), `WorkerPushTests.aWalkerPassesAWorkingWorkerInOneRow` (pass), `WorkerPushTests.onlyTwoDirectedWorkersSkipTheirPush` (pass), `WorkerPushTests.workersMeetingHeadOnInOneRowPassAndWork` (pass).

56 of the 356 unit tests cite no row id. They test the helpers that the tests of the rows use.

### Totals

The spec sheet has 277 Phase 1 `MUST` and `MUST NOT` rows. 269 of the 269 rows with automated tests pass. 35 rows have a play-test part, and 6 of them have no automated test. 3 rows stay open after Phase 1 (CD-REL-04, CD-REL-14, CD-REL-15). 0 automated rows have no passing test.

### Rows without a passing test

None.

<!-- gate-report rows end -->
