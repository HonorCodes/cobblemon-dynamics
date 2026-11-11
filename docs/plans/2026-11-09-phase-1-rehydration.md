# Phase 1 rehydration note (2026-11-09)

Read this first after a context compaction, a restart or in a new session. It holds the state, the rules
and the next steps for finishing the [Phase 1 plan](2026-11-09-phase-1-plan.md). The plan files hold the
design of every task. This note holds what the plan does not.

## The operator's instructions

- Execute the Phase 1 plan (M0 to M5) milestone by milestone, test-first. Stop at the Phase 1 gate (M5
  Task 9) for the operator's play-test.
- Test in isolation: a clean NeoForge 1.21.1 setup with only the mods a test needs. Spawn and autotest the
  mod. Never use the COHO modpack or any existing instance on this machine.
- "Go with your recommendations all the way through" (2026-11-09). Make the calls without asking: rule
  on plan and spec corrections, and merge each milestone pull request when CI is green and the self-review
  is done. No review bot is installed on this repository. Record each ruling in the pull request body.
  The exception is the tag `v1.0.0-alpha.1`: the plan asks the operator at the gate, where work stops
  anyway.
- "Avoid game tests for high-confidence code. I'd rather test at the end and fix." (2026-11-09). This is
  execution rule 6 in the plan index. Deferred rows go on the list in M5 Task 9.
- Global rules (from `~/.claude/CLAUDE.md`): pull before work; one PR per milestone; batch fixes into one
  push per review round; no AI attribution or `Co-Authored-By` anywhere; stage explicit paths only; a
  commit with a `.md` file needs a ` [rag:N]` subject suffix (hook-enforced).

## State at the time of writing (Phase 2: M6 done, M7 next)

| Item | State |
| --- | --- |
| Plan | On `main`. Index plus files `m0`, `m1` (two parts), `m2`, `m3`, `m4`, `m5`. `py scripts/check_plan_coverage.py` prints `missing: 0`. |
| M0 | Merged: PR #1, merge commit `0c6f06f`. |
| M1 | Merged: PR #6, merge commit `21f227b`. The `Dependency graph` job reports the compile and runtime classpaths. It gives runtime scope only to `jarJar` libraries, because Minecraft, NeoForge and the required mods supply the rest. |
| M2 | Merged: PR #7, merge commit `000cbd4`. |
| Dependabot | Merged: tomlj 2.2.0 (#4), JUnit 6.1.3 (#5), and Gradle 9.9.0 with Kotlin 2.2.21 (#8). `dependabot.yml` keeps Kotlin on 2.2.x and the platform dependencies at their floors. To re-test a Dependabot pull request against a newer `main`, close and reopen it. |
| M3 | Merged: PR #9, merge commit `e001192`. |
| M4 | Merged: PR #10, merge commit `f6adcec`. |
| M5 | Merged: PR #11, merge commit `4aad0dd`. The gate report `docs/testing/phase-1-gate.md` is for the run on commit `0cddb71`: all 269 rows with automated tests pass, 35 rows have a play-test part, and 3 rows stay open after Phase 1. |
| Play-test | The operator plays in the CurseForge instance `C:\Users\admin\curseforge\minecraft\Instances\Cobblemon Dynamics Play-test` (world `test1`, with the `cdtest` kit datapack: `/function cdtest:help`). The instance came from `run/testpack/cobblemon-dynamics-playtest-curseforge.zip`. Round 1 (2026-11-10) found five issues: the heal crash, a too-flashy Flamethrower, cut-off animation loops, work cries and workers standing on top of their targets. PR #12 (`69e4399`) fixed all five, and the instance runs that jar. After each session, read the instance's `logs/latest.log` and `crash-reports/`. Copy a new jar into its `mods/` only while the game is closed. |
| Shared machine | The COHO-2 session runs large COHO server and client tests on this PC. It asks over cross-session messages to hold JVM runs when free memory is low. Run JVMs one at a time. Run `./gradlew --stop` when idle, and cap the GameTest JVM at 3 GB with an init script passed through `-I`. Never raise a heap setting. |
| Phase 1 close | The approver play-tested Phase 1 GREEN (CD-REL-13, recorded in the gate report). Their rulings then merged: PR #13 (`62b75ca`) removes the Burner Keeper and adds the Charge Cell (50,000 FE) and the creative tab. PR #15 (`89918aa`) holds the 9 multiplayer audit fixes. The approver ruled "no multiplayer game tests", so the code is checked by reading it. |
| Phase 2 | The plan is `docs/plans/2026-11-10-phase-2-plan.md` with M6, M7 and M8 files (PR #14, `1dccc06`). It holds rulings on its open questions and the approver's art scope: new pixel art only for items that are never placed, and blocks keep art that represents their 3D object. M6 (Telekinesis, Magnet, spots with a path) is on branch `m6-telekinesis-magnet`: 445 unit tests, 182 GameTests and 4 core tests, its pull request open. Next: M7 in two parts, then M8 and the Phase 2 gate with the approver's art review and play-test. |

## Environment

- JDK 21: `export JAVA_HOME="/c/Program Files/Java/jdk-21"` before every Gradle call. The `java` on the
  PATH is Java 8.
- Build: `./gradlew build` (unit tests, `verifyJar`, `verifyNoAe2`). GameTests: `./gradlew runGameTestServer`,
  2 to 5 minutes with 44 tests; log at `run/gametest/logs/latest.log`; look for `required tests` and
  `failed at`. Redirect Gradle output to `$CLAUDE_JOB_DIR/tmp/*.log` and search it with the Grep tool. A
  hook blocks Bash `grep` on logs over 25 KB.
- Python: `py` (3.12). `py -m ruff check scripts` must pass.
- ktlint 1.8.0 (the version super-linter v9 bundles): `"$JAVA_HOME/bin/java" -jar "$CLAUDE_JOB_DIR/tmp/ktlint/ktlint" "src/**/*.kt"`,
  `--format` to fix. If the jar is gone, download it again from
  `https://github.com/pinterest/ktlint/releases/download/1.8.0/ktlint`. A new session has a new
  `$CLAUDE_JOB_DIR`. The old one is `C:\Users\admin\.claude\jobs\257dc33d\tmp`.
- Super-linter locally (Docker Desktop must be running; start `C:\Program Files\Docker\Docker\Docker Desktop.exe`),
  from PowerShell, never Git Bash (HAR-809):

```powershell
docker run --rm -e RUN_LOCAL=true -e DEFAULT_BRANCH=main -e VALIDATE_ALL_CODEBASE=true -e VALIDATE_KOTLIN=true -e VALIDATE_GITHUB_ACTIONS=true -e VALIDATE_YAML=true -e VALIDATE_JSON=true -e VALIDATE_PYTHON_RUFF=true -e "FILTER_REGEX_EXCLUDE=(^|/)(build|run|\.gradle|\.kotlin|\.idea)/" -e LOG_LEVEL=NOTICE -v "C:\Users\admin\src\cobblemon-dynamics:/tmp/lint" ghcr.io/super-linter/super-linter:slim-v9
```

- Test pack: `py scripts/testpack/testpack.py smoke` (`SMOKE PASS`), `deps` (`DEPS PASS`, seven cases),
  `client` (assembles `run/testpack/client/mods/` for the play-test). Add later scenarios as
  `scenario_<name>` functions.
- Cobblemon 1.8.4 sources (read-only reference): `C:\Users\admin\.claude\jobs\257dc33d\tmp\cobblemon-src\x\`.
  If gone, download `https://maven.impactdev.net/repository/development/com/cobblemon/neoforge/1.8.4+1.21.1/neoforge-1.8.4+1.21.1-sources.jar`
  into a new empty folder and unzip it there. For NeoForge, FML and Minecraft, read the compiled classes
  with `javap`. The jars are in `C:\Users\admin\.gradle\caches`. The sources jars can differ from the
  compiled classes.

## How to open a pull request here

The `pr-runner` gate cannot run its super-linter mirror on Windows Git Bash (HAR-809: Git Bash rewrites the
`/tmp/lint` mount paths). Until HAR-809 is fixed:

1. Run ktlint, ruff and the super-linter command above; all must pass.
2. `./gradlew build runGameTestServer` passes on the branch tip.
3. `PR_RUNNER_BYPASS=1 git push -u origin <branch>`, then
   `PR_RUNNER_BYPASS=1 gh pr create --draft --base main --head <branch> --title ... --body-file <file>`.
   Say in the body that the PR was opened by hand because of HAR-809.
4. CI runs on pull requests and on `main` only, so pushing a branch with no PR costs nothing.
5. When CI is green, `gh pr ready <n>`, query the review threads again, and merge with a merge commit
   (`gh pr merge <n> --merge`). Do not pass `--delete-branch` while a stacked branch exists locally.

## How to execute a milestone

Use one general-purpose subagent per milestone part, one at a time, because all work shares one working
tree and Gradle cannot run twice in it. Do not start another Gradle job, and do not leave uncommitted edits,
while a subagent runs. Review each result: read `git log`, run `./gradlew build runGameTestServer`
yourself, and check the deviations the subagent reports. Suggested parts: M1 Tasks 11 to 13 as one part;
M2 Tasks 1 to 3 and 4 to 6; M3 Tasks 1 to 3 and 4 to 6; M4 Tasks 1 to 2 and 3 to 5; M5 Tasks 1 to 4 and
5 to 9.

Brief template (fill the task range and the milestone file):

> Implement milestone `<M>` Tasks `<a>` to `<b>` in `C:\Users\admin\src\cobblemon-dynamics` on branch
> `<branch>` (already checked out; do not switch branches, rebase, push or open PRs). Read the plan index
> (Global Constraints, How to execute including rule 6 on test scope, Test architecture), the milestone
> file's tasks (read large files with offset/limit, at most 500 lines), `CLAUDE.md` and this rehydration
> note. For each task: write the failing unit tests, run them and confirm the expected failure,
> implement, run them and `./gradlew build`, run ktlint, then commit with explicit paths and the plan's
> commit message (` [rag:1]` when a `.md` is in the commit; no attribution). Write a GameTest only where
> behaviour against real Cobblemon, NeoForge, Minecraft or Create is uncertain. Mark every other planned
> GameTest row "GameTest deferred (high confidence): verified in the end-of-phase pass" in the task text,
> and list it in the report. Run `runGameTestServer` once at the end of the part, twice in a row. Where the
> plan is wrong about a real API, fix the code and the plan text in the same commit. Never weaken a test
> that encodes a spec row; if one cannot pass as written, stop and report. If a spec row contradicts the
> platform, do not edit the spec; report it with evidence. Keep logs in files. Leave the tree clean.
> Report per task: commit hash, tests added and passing, deviations, deferred rows, and new gotchas.

## Gotchas

The repository's `CLAUDE.md` holds them; add new ones there.

## Decisions recorded in the plan

- CD-STK-07: `litDuration = litTime` keeps the flame gauge full, while a 2-tick `litTime` keeps CD-STK-06
  (out within 2 ticks). Mention it in the M1 pull request.
- CD-CFG-01: the server config lives in `config/`, because NeoForge 21.1 writes server configs there (FML
  `ConfigTracker.resolveBasePath`). A copy in a world's `serverconfig/` overrides it. The spec sheet
  records the correction under its status line.
- CD-SEC-04: the Task 13 toggle handler resolves the pasture through `PastureAdapter.linkedPasture(player)`,
  as Cobblemon's own panel does. Cobblemon keeps the link only while the player is closer than 10.0 blocks
  (`PastureLinkManager.kt:37`). `WorkModeService.request` accepts exactly 10.0, which the CD-SEC-04
  GameTest checks. A panel request at exactly 10.0 blocks therefore fails, because Cobblemon has dropped
  the link. State this boundary in the M1 pull request.
- ADR-0004 has an amendment from M1 Task 9: the switch task is at core priority, and `BrainBridge.write`
  switches the activity and stops the old tasks.
- CD-LSH-16 to CD-LSH-20 and CD-SEC-11 use the test-only role `cobblemon_dynamics:test_transfer` from the
  test mod, because Telekinesis ships in Phase 2.
- GameTests simulate restarts (`WorkEngine.resetRuntimeForTest()` plus NBT round trips) and chunk unloads.
  The test pack's `restart` and `reload` scenarios (M5 Task 6) repeat for real the checks that need no pastured
  worker, because a dedicated test server has no player.
- Keep the Task 9 GameTests cdAi01 and cdAi06, although rule 6 would not have asked for them.

## Carry-over items

- Done in the end-of-phase pass (M5): a work spot was one cell, so a wide worker's body could overlap the blocks
  beside it (CD-ASG-09). A Camerupt on real blocks got a spot in a gap one block wide. The spot check now needs
  room for the worker's body on the spot's center and where navigation stops it
  (`WideWorkerTests.cdAsg09WideWorkerGetsASpotItsBodyFits`). The play-test still checks it (manual checklist 14).
- Done in the end-of-phase pass (M5): a worker worked a creative Blaze Burner, which gains nothing
  from it. `hasDemand` is now false for a creative burner (`BurnerTests.creativeBlazeBurnerHasNoDemand`).
- Done in the end-of-phase pass (M5): two Pokémon that walk toward each other on the same row of cell centres
  push each other straight back, because Minecraft pushes along the line between their centres. Navigation
  follows cell centres and the engine snaps a Working worker to its spot's centre, so a walk that pushed a
  Working worker off its spot stalled until the travel timeout (600 ticks), and CD-ASG-14 then skipped the
  target for 6,000 ticks. The perf world (M5 Task 6) showed it in 5 of 13 runs. The approver's delegate ruled
  that Work Mode workers must not deadlock this way. `WorkerPushMixin` now skips the push between two workers
  that the engine directs (any state but Idle); players and every other entity still push workers
  (`WorkerPushTests`). The play-test still checks a crowded pasture (manual checklist 15).
- Role files: target particles now go out at the work focus and actor particles from the worker (M1, ADR-0007
  amendment). Put each particle of a new role file in the list that matches where Cobblemon emits it, as
  the Wheel Driver's `watergun_spray` (actor) shows.

## References

- Spec: `docs/plans/2026-11-09-v1-spec-sheet.md` (164 KB; grep row ids). Design: `ARCHITECTURE.md`,
  `docs/adr/`, `docs/research/2026-11-09-integration-points.md`.
- Linear: HAR-809 (the pr-runner Windows bug). No Linear row tracks this mod.

## Next steps, in order

Phase 1 has reached its gate. The work is stopped for the operator's play-test (M5 Task 9 Step 5).

1. `git pull` on `main`. If the M5 pull request is still open, finish it: CI green, threads resolved,
   merge with a merge commit.
2. Wait for the operator's play-test note. Record it in the play-test section of
   `docs/testing/phase-1-gate.md`. CD-REL-13 completes then.
3. Fix what the play-test finds, test first, in a follow-up pull request before any M6 work merges.
4. Ask the operator before the CD-REL-12 demo, which pushes `v1.0.0-alpha.1` and then `v1.0.0-beta.1`.
   Each tag creates a public GitHub pre-release.
5. Phase 2 (M6 to M8: Telekinesis, Magnet, Fan, Tailwind and the final art) needs its own plan, written
   with `superpowers:writing-plans` from the spec rows in the TLK, MAG, FAN, TLW and ART areas. Start it
   with the known issues in the gate report, first the work spot reachability check.
6. The Phase 2 plan is [`2026-11-10-phase-2-plan.md`](2026-11-10-phase-2-plan.md). Start M6 when its execution rule 7 holds.
