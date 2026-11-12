# Phase 3 gate report

This report is the Phase 3 record of CD-REL-14 for Cobblemon Dynamics `1.0.0`, built from the branch `m9-claims`. It
lists the checks that ran, with their results, and the checks that the approver deferred. CD-REL-14 needs every `MUST`
and `MUST NOT` row to pass on a named commit. The approver's ruling deferred most runs to live testing on the COHO
server, so this record does not show that. `scripts/gate_report.py` has no Phase 3 and needs the outputs of the
deferred runs, so this record is hand-written.

## The ruling

The approver, 2026-11-11:

"do we really need the game tests? i thought we were pretty confident in these fixes and we'll be testing them actively in the server"

After this question, the coordinator cut the runs of the `m9-claims` build to `./gradlew build` and
`./gradlew runGameTestServerFtbChunks`. The FTB Chunks run stayed, because the UUID rule of FTB Chunks decides pasture
work on the live COHO server. The rest of the gate waits for live testing on the COHO server.

## Runs

All times are local time (UTC-6) on 2026-11-11. The runs of Tasks 1 to 3 came before Task 9 and before the fixes below.

| Check | Command | Time and commit | Result |
| --- | --- | --- | --- |
| Unit tests | `./gradlew build` | 19:43, `5e765a0` | 568 of 568 pass |
| GameTests with FTB Chunks | `./gradlew runGameTestServerFtbChunks` | 19:44, `5e765a0` | 10 of 10 required tests pass |
| GameTests, full mod set | `./gradlew runGameTestServer` | 17:17, Tasks 1 to 3 | 234 of 234 required tests pass |
| GameTests without Create and AE2 | `./gradlew runGameTestServerCore` | 17:17, Tasks 1 to 3 | 4 of 4 required tests pass |
| Server time of 64 Working workers | `./gradlew runGameTestServerPerf` | 17:18, Tasks 1 to 3 | Pass, with a mean of 0.153 ms per tick (CD-PERF-01) |
| GameTests, full mod set | `./gradlew runGameTestServer` | 19:38, Task 9 | 245 tests ran, and 1 required test failed (CD-QWK-36) |

- **FTB Chunks.** The first run passed 9 of 10 required tests, because the pasture check read the mode of FTB Teams by
  its lower-case name. Commit `5e765a0` maps the mode by its constant, and the second run passed 10 of 10.
- **CD-QWK-36.** The full runs at 19:34 and 19:38 each failed one required test,
  `cdQwk36FairyAtAClosedEnchantingTableWaitsAndWorks`. In the 19:38 run, the engine released the claim of the Muse's
  Quick Work order, so the order ended. Commit `73baeb1` keeps the claim of a Quick Work order whose role opts in. The
  ruling deferred the GameTest run after this fix, so the test has not passed yet.
- **After the runs.** Commits `f6ed4f0` and `3638085` change the release workflow and `ReleaseWorkflowTest` (R69,
  CD-REL-04 and CD-REL-11). The release commit, which holds this report, sets `mod_version=1.0.0`.

## Deferred

These checks of Task 8, Step 4 did not run on the release commit. They wait for live testing on the COHO server.

- `./gradlew runGameTestServer`, `./gradlew runGameTestServerCore` and `./gradlew runGameTestServerPerf`, with the
  GameTest of CD-QWK-36.
- `./gradlew runGameTestServerOpac` and `./gradlew runGameTestServerMineColonies`.
- The test pack: `py scripts/testpack/testpack.py` with `smoke`, `deps`, `compat`, `reload`, `restart` and `client`.
- `py scripts/art.py check --references auto --release`. The release run of a public tag still runs
  `scripts/art.py check --release`, without the copied-pixel scan (CD-ART-12).
- `py scripts/gate_report.py --phase 3`, which reads the outputs of the runs above.

## After the tag

- Check 42 of the [manual checklist](manual-checklist.md) (CD-REL-04) runs on the `v1.0.0` tag, after the approver's
  go-ahead. By R69 the tag needs no publishing settings, so known issue (c) of the [Phase 1 report](phase-1-gate.md)
  no longer applies.
- CD-REL-15 completes when the next release of the approver's pack names Cobblemon Dynamics `1.0.0`.

## Open items

- The play-test note of `1.0.0-beta.3` (Task 8, Step 1) is not in the [Phase 2 report](phase-2-gate.md) yet.
  CD-REL-14 needs the note, dated before the `v1.0.0` tag.
- The plan records decisions 12 (Modrinth) and 17 (a play-test of the `1.0.0` candidate) as pending the approver.
