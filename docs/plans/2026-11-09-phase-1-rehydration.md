# Phase 1 rehydration note (2026-11-09)

Read this first after a context compaction or in a new session. It holds the state, the rules and the
next steps for finishing the [Phase 1 plan](2026-11-09-phase-1-plan.md). The plan files hold the design
of every task. This note holds what the plan does not.

## The operator's instructions

- Execute the Phase 1 plan (M0 to M5) milestone by milestone, test-first. Stop at the Phase 1 gate (M5
  Task 9) for the operator's play-test.
- Test in isolation: a clean NeoForge 1.21.1 setup with only the mods a test needs. Spawn and autotest the
  mod. Never use the COHO modpack or any existing instance on this machine.
- Global rules (from `~/.claude/CLAUDE.md`): pull before work; one PR per milestone; batch fixes into one
  push per review round; no AI attribution or `Co-Authored-By` anywhere; stage explicit paths only; a
  commit with a `.md` file needs a ` [rag:N]` subject suffix (hook-enforced); Linear for work a later
  session picks up.

## State at the time of writing

| Item | State |
| --- | --- |
| Plan | Committed on `main` (69e17b6). Index plus files `m0`, `m1` (two parts), `m2`, `m3`, `m4`, `m5`. `py scripts/check_plan_coverage.py` prints `missing: 0` (281 Phase 1 rows). |
| M0 | Branch `m0-scaffold-ci`, PR #1 (https://github.com/HonorCodes/cobblemon-dynamics/pull/1), ready for review. Every check passes: `lint`, `build` (unit tests and GameTests on CI), `CodeQL`, `Dependency review`. No review bot has posted. Merge waits for the operator's go-ahead. |
| CI proof | PR #2 failed on the planted assertion only (run 42716986490), then was closed and its branch deleted. Recorded in PR #1's body (CD-REL-01). |
| M1 | Branch `m1-engine-leash-stoker`, stacked on `m0-scaffold-ci`. A subagent was running M1 part 1 (Tasks 1 to 6) when this note was written. Check `git log --oneline m0-scaffold-ci..m1-engine-leash-stoker` to see which tasks it committed. Task 1 (configs) had started. |
| M2 to M5 | Not started. |
| Repo settings | Dependabot alerts (and with them the dependency graph) were enabled on 2026-11-09 so dependency review can run. |

## Environment

- JDK 21: `export JAVA_HOME="/c/Program Files/Java/jdk-21"` before every Gradle call. The `java` on the
  PATH is Java 8.
- Build: `./gradlew build` (unit tests, `verifyJar`, `verifyNoAe2`), about 30 s warm. GameTests:
  `./gradlew runGameTestServer`, about 30 s; log at `run/gametest/logs/latest.log`; grep `required tests`
  and `failed at`. Redirect Gradle output to `$CLAUDE_JOB_DIR/tmp/*.log` and grep it.
- Python: `py` (3.12). `py -m ruff check scripts` must pass.
- ktlint 1.8.0 (the version super-linter v9 bundles): `"$JAVA_HOME/bin/java" -jar "$CLAUDE_JOB_DIR/tmp/ktlint/ktlint" "src/**/*.kt"`,
  `--format` to fix. If the jar is gone, download it again from
  `https://github.com/pinterest/ktlint/releases/download/1.8.0/ktlint`.
- Super-linter locally (Docker Desktop must be running; start `C:\Program Files\Docker\Docker\Docker Desktop.exe`),
  from PowerShell, never Git Bash (HAR-809):

```powershell
docker run --rm -e RUN_LOCAL=true -e DEFAULT_BRANCH=main -e VALIDATE_ALL_CODEBASE=true -e VALIDATE_KOTLIN=true -e VALIDATE_GITHUB_ACTIONS=true -e VALIDATE_YAML=true -e VALIDATE_JSON=true -e VALIDATE_PYTHON_RUFF=true -e "FILTER_REGEX_EXCLUDE=(^|/)(build|run|\.gradle|\.kotlin|\.idea)/" -e LOG_LEVEL=NOTICE -v "C:\Users\admin\src\cobblemon-dynamics:/tmp/lint" ghcr.io/super-linter/super-linter:slim-v9
```

- Test pack: `py scripts/testpack/testpack.py smoke` (`SMOKE PASS`), `deps` (`DEPS PASS`, six cases),
  `client` (assembles `run/testpack/client/mods/` for the play-test). Add later scenarios as
  `scenario_<name>` functions.
- Cobblemon 1.8.4 sources (read-only reference): `$CLAUDE_JOB_DIR/tmp/cobblemon-src/x/` (extracted
  sources jar). If gone, download `https://maven.impactdev.net/repository/development/com/cobblemon/neoforge/1.8.4+1.21.1/neoforge-1.8.4+1.21.1-sources.jar`
  into a new empty folder and unzip it there.

## How to open a pull request here

The `pr-runner` gate cannot run its super-linter mirror on Windows Git Bash (HAR-809: Git Bash rewrites the
`/tmp/lint` mount paths). Until HAR-809 is fixed:

1. Run ktlint, ruff and the super-linter command above; all must pass.
2. `./gradlew build runGameTestServer` passes on the branch tip.
3. `PR_RUNNER_BYPASS=1 git push -u origin <branch>`, then
   `PR_RUNNER_BYPASS=1 gh pr create --draft --base main --head <branch> --title ... --body-file <file>`.
   Say in the body that the PR was opened by hand because of HAR-809.
4. CI runs on pull requests and on `main` only, so pushing a branch with no PR costs nothing.
5. When CI is green, `gh pr ready <n>`. Wait for review threads. Resolve each one. Merge only after the
   operator agrees. Branches are stacked: after merging M0, rebase `m1-engine-leash-stoker` onto `main`.

## How to execute a milestone

Use one general-purpose subagent per milestone part, one at a time, because all work shares one working
tree and Gradle cannot run twice in it. Do not start another Gradle job while a subagent is running.
Review each result: read `git log`, run `./gradlew build runGameTestServer` yourself, and check the
deviations the subagent reports. Suggested parts: M1 part 2 as Tasks 7 to 8, 9 to 10, and 11 to 13; M2
Tasks 1 to 3 and 4 to 6; M3 Tasks 1 to 3 and 4 to 6; M4 Tasks 1 to 2 and 3 to 5; M5 Tasks 1 to 4 and 5 to 9.

Brief template (fill the task range and the milestone file):

> Implement milestone `<M>` Tasks `<a>` to `<b>` in `C:\Users\admin\src\cobblemon-dynamics` on branch
> `<branch>` (already checked out; do not switch branches, push or open PRs). Read the plan index
> (Global Constraints, How to execute, Test architecture), the milestone file's tasks (read large files
> with offset/limit), `CLAUDE.md` and this rehydration note. For each task: write the failing tests as the
> plan gives them, run them and confirm the expected failure, implement, run the task's tests and
> `./gradlew build` (and `runGameTestServer` for GameTest tasks), run ktlint, then commit with explicit
> paths and the plan's commit message (` [rag:1]` when a `.md` is in the commit; no attribution). Where
> the plan is wrong about a real API, fix the code and the plan text in the same commit. Never weaken a
> test that encodes a spec row; if one cannot pass as written, stop and report. Keep logs in files and grep
> them. Report per task: commit hash, tests added and passing, deviations.

## Gotchas found so far

- GameTest owners are NeoForge `FakePlayer`s (`TestKit.owner`). Vanilla's mock player joins the player
  list, and its connection throws on Cobblemon's login payloads.
- `Pokemon.entity` is null for pastured Pokémon. Find the entity with an AABB query by
  `entity.pokemon.uuid` (`TestKit.pasturedEntity`). The `PastureAdapter` in M1 Task 7 must do the same.
- `PokemonPastureBlockEntity.tether(player, pokemon, direction)` needs the Pokémon in the player's PC
  first (`Cobblemon.storage.getPC(player).add(pokemon)`).
- Behaviour files cannot create activities. `cobblemon_dynamics:work` must be registered in code. Custom
  task configs go into `TaskConfig.types` (a public mutable map) and declare memories with
  `behaviourConfigurationContext.addMemories(...)`.
- The mixin config uses `defaultRequire: 0`, so a missing injection point never crashes the game. M3's
  `MixinState` must detect real injections in `postApply`.
- FML logs a rejected dependency under `Missing or unsupported mandatory dependencies:` or
  `Unsupported installed optional dependencies:`, and the server still exits 0.
- `.gitattributes` forces LF. `git commit -- <paths>` on Windows drops a staged executable bit; commit the
  index instead when a mode change is staged.
- Docker Desktop mounts every Windows file as executable, so ruff's `EXE002` fires locally on a script
  without a shebang. Every `scripts/*.py` has a shebang and mode 100755.
- `TestKit` lives in `src/gametest`. M3 Task 1 moves shared helpers to `src/gametest/kotlin-shared/` for
  the core run.

## Decisions recorded in the plan

- CD-STK-07: `litDuration = litTime` keeps the flame gauge full while a 2-tick `litTime` keeps CD-STK-06
  (out within 2 ticks). Mention it in the M1 pull request.
- CD-LSH-16 to CD-LSH-20 and CD-SEC-11 use the test-only role `cobblemon_dynamics:test_transfer` from the
  test mod, because Telekinesis ships in Phase 2.
- GameTests simulate restarts (`WorkEngine.resetRuntimeForTest()` plus NBT round trips) and chunk unloads
  (`EngineEvents.onChunkUnload`). The test pack repeats them for real in M5 Task 6.
- Pushing the tag `v1.0.0-alpha.1` (the CD-REL-12 demo and the play-test build) creates a public GitHub
  pre-release. Ask the operator first.
- Possible M0 follow-up: a Gradle dependency-submission job, so dependency review also sees Gradle
  dependencies. Batch it with the first review-round fix on PR #1, if any.

## References

- Spec: `docs/plans/2026-11-09-v1-spec-sheet.md` (164 KB; grep row ids). Design: `ARCHITECTURE.md`,
  `docs/adr/`, `docs/research/2026-11-09-integration-points.md`.
- Linear: HAR-809 (the pr-runner Windows bug). No Linear row tracks this mod.
- Hindsight initiative page: `kp-d5229d1c2c8d4f00876f3b1a962a3082`.

## Next steps, in order

1. If the M1 part 1 subagent's result arrives, review it: commits, `./gradlew build`, deviations.
   Otherwise read `git log` and `git status` and finish what is missing from Tasks 1 to 6.
2. Ask the operator whether to merge PR #1 (all checks green; no bot reviews yet). After the merge,
   rebase the M1 branch onto `main`.
3. Run M1 part 2 (Tasks 7 to 13) with subagents, then open the M1 PR as described above.
4. Repeat for M2, M3, M4 and M5. Update the plan text whenever reality differs, and keep
   `check_plan_coverage.py` at `missing: 0`.
5. At M5 Task 9, write the gate report, assemble the play-test client with `testpack.py client`, and stop
   for the operator.
