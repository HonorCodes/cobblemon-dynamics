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

The gate run of [M8 Task 8](../plans/2026-11-10-phase-2-m8-art-gate.md#task-8-the-phase-2-gate), Step 3, writes this
block.

<!-- gate-report summary end -->

## For the approver

### The play-test client

`py scripts/testpack/testpack.py client` fills the folder `run/testpack/client/mods/` with six jars:

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
7. Copy the six jars of `run/testpack/client/mods/` into the `mods` folder of the instance.
8. Write the line `onboardAccessibility:false` into `options.txt` in the instance folder. Do this before the first start,
   or the game first opens the accessibility screen, which you can also click through.
9. Click **Play**.

The CurseForge app and GDLauncher work the same way: a new Minecraft 1.21.1 instance with NeoForge 21.1.265, with the six
jars in its `mods` folder.

### The review spritesheet and the review record

`py scripts/art.py review` writes the review spritesheet `build/art-review/spritesheet.png`. It also refreshes the rows of
the review record, [`docs/testing/art-review.md`](art-review.md). The sheet shows each sprite at 8x and at 1x, on a light
and on a dark inventory slot: the Work Leash, the creative tab icon, every UI sprite and the Work Stamina bars as the game
draws them. It also shows each block item from above, for reference.

The review record has one row for each asset, with its source and the SHA-256 of that source. Give each row a verdict:
`approved`, or `changes` with a note. A row whose source changes goes back to `pending`. A public tag publishes only when
every row is `approved` at its current SHA-256 (CD-ART-12).

The record also lists your logo files, `art/logo/logo-256.png` and `art/logo/icon-512.png` (CD-ART-05). The sheet does
not show them, so open the two files. They need your approval too, because CD-ART-10 asks for an approval of every asset
of CD-ART-01 to CD-ART-05.

### Steps

1. Read the run summary above. It names the commit of the run, and it holds the test counts and the perf numbers.
2. Set up the client as above.
3. Do steps 7 to 9 of the [play-test setup](manual-checklist.md#client) of the manual checklist: a creative flat world
   with cheats on, no Pokémon spawning, and your test Pokémon.
4. Do the checks of the [play-test note](#play-test-note): check 20, checks 44 to 58, and the Phase 1 checks 6, 9, 10,
   14, 15 and 22, which Phase 2 code touches. The [manual checklist](manual-checklist.md) holds each check.
5. If the checklist has a Quick Work check for CD-QWK-21 at the gate run, do that check too.
6. Open the review spritesheet and the two logo files.
7. Give each row of the review record your verdict.
8. Record each result of the checks in the play-test note: pass, fail with what you saw, or blocked with the reason.

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
- **Quick Work and claim mods.** Quick Work makes no block click, because the server traces the owner's view. Claim mods
  that only cancel block clicks, such as FTB Chunks and Open Parties and Claims, therefore do not see Quick Work. Before
  an order starts, Quick Work asks `ServerPlayer.mayInteract`, which checks spawn protection and the world border. A
  server can set `quickWork.enabled = false`. Compat with the claim APIs of those mods is later work. Source:
  [ADR-0013](../adr/0013-quick-work-for-party-pokemon.md) and design decision 9 of the
  [Quick Work plan](../plans/2026-11-11-phase-2-quick-work.md#design-decisions-for-the-approver).
- **CD-QWK-21.** Its GameTest cannot be built as the row states it. Cobblemon 1.8.4 clears a battle id that names no live
  battle on the entity's next tick, and a test battle against a GameTest fake player ends within 1 tick. The end-of-phase
  pass changes its acceptance test to a unit test of the end rule plus a Demo in the play-test. Source: "Deferred rows" in
  [pull request #22](https://github.com/HonorCodes/cobblemon-dynamics/pull/22), and Step 6 of Task 3 of the
  [Quick Work plan](../plans/2026-11-11-phase-2-quick-work.md).
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

The table has one line for each check of the [manual checklist](manual-checklist.md) that the Phase 2 play-test covers. If
the checklist gets a Quick Work check for CD-QWK-21 before the gate run, the gate run adds its line.

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

The gate run of M8 Task 8, Step 3, writes this block.

<!-- gate-report rows end -->
