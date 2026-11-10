# Isolated test pack

`testpack.py` builds clean NeoForge 1.21.1 dedicated servers under `run/testpack/`. Each server
gets only the mods that its scenario needs. The script never reads or changes the COHO modpack or
another existing Minecraft instance.

## Requirements

- Python 3.12. The script uses only the standard library. Run it with `py` on Windows and with
  `python3` on Linux.
- A JDK 21 in `JAVA_HOME`. The script never starts the `java` on the PATH, because that one can be
  older.
- A built mod jar. Run `./gradlew build` (`gradlew.bat build` on Windows) before any command except
  `resolve`.

## Commands

Run each command from the repository root, for example `py scripts/testpack/testpack.py smoke`.

| Command | Result |
|---|---|
| `resolve` | Finds each pin of `mods.json` on Modrinth or on the NeoForge maven, then writes its file name, URL and SHA-512 back to `mods.json`. A version without a NeoForge 1.21.1 release gets `null`. |
| `smoke` | Starts a server with the pinned mods and the built jar. Checks that the mod loads and that RCON answers `list`, then stops the server. Prints `SMOKE PASS` or `SMOKE FAIL: <reason>`. |
| `reload` | Starts the same server and runs `cobblemondynamics claims` over RCON (it must answer that there are no claims). Then it writes the world data pack `cd_reload_probe`, whose Stoker role file names a handler that nobody registers, runs `reload` and waits for the reload to end. The log must say that the Stoker is disabled for that handler (CD-CFG-08) and have no `Skipping role file` line. Prints `RELOAD PASS` or `RELOAD FAIL: <reason>`. |
| `restart` | Starts the same server and checks that `config/cobblemon_dynamics-server.toml` holds every key of the CD-CFG-01 list in `ConfigTest` (CD-CFG-01). It force-loads chunk 0, 0, places a Create water wheel and a hand crank, and sets the turn direction that they save (`cd_direction`) to -1 with `data merge block`. Then it stops and starts the server: both blocks must still read -1 (CD-WHL-07, CD-CRK-08), `cobblemondynamics claims` must report none (CD-CLM-07), and the new log must have no error of ours. Prints `RESTART PASS` or `RESTART FAIL: <reason>`. |
| `deps` | Runs the dependency-range cases below. Prints one `CASE <name>: PASS\|FAIL\|SKIPPED <detail>` line per case, then `DEPS PASS` or `DEPS FAIL`. |
| `compat` | Runs the compatibility cases below. Prints one `CASE <name>: PASS\|FAIL\|SKIPPED <detail>` line per case, then `COMPAT PASS` or `COMPAT FAIL`. |
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
| `neoforge-floor` | NeoForge 21.1.219, the floor of `[21.1.219,)` | The server loads |
| `neoforge-below-floor` | NeoForge 21.1.218 | FML rejects `neoforge` `[21.1.219,)` |
| `cobblemon-below-floor` | Cobblemon 1.8.3 | FML rejects `cobblemon` `[1.8.4,1.9)` |
| `create-below-floor` | Create 6.0.9 | FML rejects `create` `[6.0.10,6.1)` |
| `ae2-below-floor` | The latest AE2 19.1 release | FML rejects `ae2` `[19.2,20)` |
| `without-create-ae2` | No Create, AE2 or GuideME | The server loads |
| `kff-floor` | None, because the pin is Kotlin for Forge 5.10.0, the floor of `[5.10,)` | The server loads |

A case is `SKIPPED` when `resolve` found no release for its version.

## Compatibility cases

Each case adds a neighbour mod from the `compat` pins to the pinned set. The server writes every class
that it loads to `classload.log` in the profile (`-Xlog:class+load`). A case passes when the server starts
and stops, the named class of ours loads, no class with a forbidden prefix loads, no error line names one,
and the log has no `Cobblebase hooks ... did not apply` warning and no mixin failure.

| Case | Added mods | Must load | Must never load |
|---|---|---|---|
| `compat_cobblebase` | Cobblebase 2.0.1 and Cloth Config 15.0.140, which Cobblebase needs at run time | `compat.CobblebaseHooks` | `compat.Cobbleworkers*` (CD-CMP-06) |
| `compat_cobbleworkers` | Cobbleworkers 2.0.7 | `compat.CobbleworkersCompat` | `compat.Cobblebase*` and `mixin.compat.cobblebase.*` (CD-CMP-07) |

Class names are under `codes.honor.cobblemondynamics.`.

## Files

| Path | Content |
|---|---|
| `scripts/testpack/mods.json` | The pins: version, file name, URL and SHA-512 of each download. |
| `run/testpack/cache/` | Downloads. The script deletes a download whose SHA-512 does not match `mods.json`. |
| `run/testpack/base/neoforge-<version>/` | One NeoForge server install for each version. |
| `run/testpack/<profile>/` | One server for each run: `smoke/`, `reload/`, `restart/`, `deps-<case>/` and `compat_<mod>/`. The script makes it again from the base install on each run. |

A profile has `logs/latest.log` and `console.log` (the server stdout and stderr). After a restart, the
first run's files are `logs/before-restart.log` and `before-restart.log`. Profiles use hard
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
- `restart()` stops the server and starts it again in the same folder. The log methods then read the
  new run only.

A dedicated test server has no player, so a scenario cannot pasture a Pokémon. Checks that need a
worker stay in the GameTests.
