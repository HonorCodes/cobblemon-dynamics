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

## State at the time of writing (M1 complete)

| Item | State |
| --- | --- |
| Plan | On `main`. Index plus files `m0`, `m1` (two parts), `m2`, `m3`, `m4`, `m5`. `py scripts/check_plan_coverage.py` prints `missing: 0`. |
| M0 | Merged: PR #1, merge commit `0c6f06f`. |
| M1 | Merged: PR #6, merge commit `21f227b`. The `Dependency graph` job reports the compile and runtime classpaths. It gives runtime scope only to `jarJar` libraries, because Minecraft, NeoForge and the required mods supply the rest. |
| M2 | Merged: PR #7, merge commit `000cbd4`. |
| Dependabot | Merged: tomlj 2.2.0 (#4) and JUnit 6.1.3 (#5). Dependabot closed #3, because `dependabot.yml` now keeps Kotlin on 2.2.x and the platform dependencies at their floors. The Gradle 9.9.0 wrapper update returns in a new Dependabot pull request. To re-test a Dependabot pull request against a newer `main`, close and reopen it. |
| M3 | Branch `m3-create-roles`, rebased onto `main` (`183c06b`). Tasks 1 to 3 are committed: 205 unit tests, 69 of 69 GameTests, 2 of 2 in the core run. Tasks 4 to 6 come next. |
| M4 and M5 | Not started. |

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
  The test pack repeats them for real in M5 Task 6.
- Keep the Task 9 GameTests cdAi01 and cdAi06, although rule 6 would not have asked for them.

## Carry-over items

- M2: a worker that Cobblemon put to sleep at night keeps its SLEEP status when it switches to work.
  Handle it with the bed and rest logic (see ADR-0004 on sleep).
- M2 Task 6: `/cobblemondynamics claims` already lists bed and heal claims (built in M1 Task 12). M2 Task 6
  has a note about this.
- Role files: target particles now go out at the work focus and actor particles from the worker (M1, ADR-0007
  amendment). Put each particle of a new role file in the list that matches where Cobblemon emits it, as
  the Wheel Driver's `watergun_spray` (actor) shows.

## References

- Spec: `docs/plans/2026-11-09-v1-spec-sheet.md` (164 KB; grep row ids). Design: `ARCHITECTURE.md`,
  `docs/adr/`, `docs/research/2026-11-09-integration-points.md`.
- Linear: HAR-809 (the pr-runner Windows bug). No Linear row tracks this mod.

## Next steps, in order

1. `git pull` on `main`. If the M1 pull request is still open, finish it: CI green, threads resolved,
   merge with a merge commit.
2. Branch `m2-stamina-beds-healers` from `main` and run M2 in two parts (Tasks 1 to 3, then 4 to 6) with
   the brief template and the carry-over items. Then M3, M4 and M5 the same way, one pull request each.
   Update the plan text whenever reality differs, and keep `check_plan_coverage.py` at `missing: 0`.
3. Before each pull request: build, GameTests twice, ktlint, ruff, super-linter and coverage, all on the
   branch tip.
4. At M5 Task 9, run the end-of-phase pass for the deferred rows, write the gate report, assemble the
   play-test client with `testpack.py client`, and stop for the operator.
