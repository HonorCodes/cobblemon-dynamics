# Isolated test pack

`testpack.py` builds clean NeoForge 1.21.1 dedicated servers under `run/testpack/`. Each server
gets only the mods that its scenario needs. The script never reads or changes the COHO modpack or
another existing Minecraft instance.

## Requirements

- Python 3.12. The script uses only the standard library. Run it with `py` on Windows and with
  `python3` on Linux.
- A JDK 21 in `JAVA_HOME`. The script never starts the `java` on the PATH, because that one can be
  older.
- A built mod jar. Run `./gradlew build` (`gradlew.bat build` on Windows) before `smoke`, `deps` or
  `client`.

## Commands

Run each command from the repository root, for example `py scripts/testpack/testpack.py smoke`.

| Command | Result |
|---|---|
| `resolve` | Finds each pin of `mods.json` on Modrinth or on the NeoForge maven, then writes its file name, URL and SHA-512 back to `mods.json`. A version without a NeoForge 1.21.1 release gets `null`. |
| `smoke` | Starts a server with the pinned mods and the built jar. Checks that the mod loads and that RCON answers `list`, then stops the server. Prints `SMOKE PASS` or `SMOKE FAIL: <reason>`. |
| `reload` | Starts the same server, runs `cobblemondynamics claims` over RCON (it must answer that there are no claims), runs `reload`, waits for the reload to end, and checks that the log has no `Skipping role file` line. Prints `RELOAD PASS` or `RELOAD FAIL: <reason>`. |
| `deps` | Runs the dependency-range cases below. Prints one `CASE <name>: PASS\|FAIL\|SKIPPED <detail>` line per case, then `DEPS PASS` or `DEPS FAIL`. |
| `client` | Fills `run/testpack/client/mods/` with the pinned mods and the built jar, for a manual play-test in a clean NeoForge 21.1.265 launcher instance. |

The exit code is 0 for a pass and 1 for a failure.

## Dependency-range cases

Each case starts from the pinned set. A rejection case passes when FML logs the line
`Mod ID: '<mod>', Requested by: 'cobblemon_dynamics', Expected range: '<range>'` and the server stops
before it loads a world. FML puts that line under `Missing or unsupported mandatory dependencies:` for
a required dependency, and under `Unsupported installed optional dependencies:` for an optional one.
After a rejection, the server exits with code 0. So the script checks the log, not the exit code.

| Case | Change to the pinned set | Expected result |
|---|---|---|
| `neoforge-below-floor` | NeoForge 21.1.218 | FML rejects `neoforge` `[21.1.219,)` |
| `cobblemon-below-floor` | Cobblemon 1.8.3 | FML rejects `cobblemon` `[1.8.4,1.9)` |
| `create-below-floor` | Create 6.0.9 | FML rejects `create` `[6.0.10,6.1)` |
| `ae2-below-floor` | The latest AE2 19.1 release | FML rejects `ae2` `[19.2,20)` |
| `without-create-ae2` | No Create, AE2 or GuideME | The server loads |
| `kff-floor` | None, because the pin is Kotlin for Forge 5.10.0, the floor of `[5.10,)` | The server loads |

A case is `SKIPPED` when `resolve` found no release for its version.

## Files

| Path | Content |
|---|---|
| `scripts/testpack/mods.json` | The pins: version, file name, URL and SHA-512 of each download. |
| `run/testpack/cache/` | Downloads. The script deletes a download whose SHA-512 does not match `mods.json`. |
| `run/testpack/base/neoforge-<version>/` | One NeoForge server install for each version. |
| `run/testpack/<profile>/` | One server for each run: `smoke/`, `deps-<case>/` and later scenarios. The script makes it again from the base install on each run. |

A profile has `logs/latest.log` and `console.log` (the server stdout and stderr). Profiles use hard
links to the large jars where the file system allows it. Each server listens on 127.0.0.1 only,
with `online-mode=false`, free ports and a random RCON password.

## Change a pin

1. Edit the `version` of the pin in `mods.json`.
2. Run `py scripts/testpack/testpack.py resolve`.
3. Run `smoke` and `deps`.
4. Commit `mods.json`.

## Add a scenario

Add a function `scenario_<name>(server)` to `testpack.py`. The function becomes the command
`<name>`. The script installs a fresh profile with the pinned mods, starts the server, calls the
function and then stops the server. To fail the scenario, raise `TestpackError` with a one-line
reason.

The `Server` object has these methods:

- `wait_log(pattern, timeout, count=1)` waits until `count` lines of `logs/latest.log` match the
  regular expression and returns the last of them. It searches from the first line of the log.
- `find_log(pattern)` returns the first matching line so far, or `None`.
- `count_log(pattern)` returns the number of matching lines so far. Pass it plus one to `wait_log` to
  wait for the next occurrence of a line.
- `rcon(command)` runs a console command over RCON and returns the reply.
- `stop()` sends `stop` over RCON and waits for the process to end.
