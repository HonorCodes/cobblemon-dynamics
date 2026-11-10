# Cobblemon Dynamics: agent gotchas

Product facts live in `ARCHITECTURE.md`, the spec sheet and the ADRs. This file holds only what bites
when your hands are on the code.

- Gradle needs JDK 21 through `JAVA_HOME` (`C:\Program Files\Java\jdk-21` on the dev box). The `java` on
  the PATH there is Java 8, so a bare `java` call or a missing `JAVA_HOME` fails with a class-version error.
- The GameTest log is `run/gametest/logs/latest.log`. `./gradlew runGameTestServer` exits non-zero when a
  required test fails; grep the log for `required tests` and `failed at`.
- GameTest owners are NeoForge `FakePlayer`s (`TestKit.owner`). Vanilla's `makeMockServerPlayerInLevel`
  joins the player list, and its connection rejects Cobblemon's login payloads.
- `FakePlayer.teleportTo` does nothing: the compiled `FakePlayerNetHandler` drops it, which the sources jar
  does not show. Move a fake player with `moveTo`. Check NeoForge behaviour with `javap` on the merged jar.
- The GameTest server ops players at level 0. `TestKit.operator` gets level 2 by overriding
  `getPermissionLevel()`.
- Tests in one GameTest batch run side by side and share the engine and the claim registry. A test that
  resets the engine or unloads a chunk needs its own batch, and a check reads only its own workers.
- `runAtTickTime` callbacks for the same tick run in hash order, not insertion order.
- A furnace with both fuel and input burns the fuel on its first tick.
- ktlint strips `->` from a zero-argument lambda, which makes `AttachmentType.builder` ambiguous. Write
  `Supplier { ... }`.
- `Pokemon.entity` is null for pastured Pokémon (it reads `ActivePokemonState` only). Find a pastured
  entity in the level by `entity.pokemon.uuid`.
- Brain: `Activity` equality is by name (vanilla villagers already have `work`, so ours is
  `cobblemon_dynamics:work`). A Brain keeps running the old activity's tasks after a switch. Cobblemon
  registers only the memories that task configs declare.
- Tick order: the level tick (Brains run here), then `LevelTickEvent.Post`, then the GameTest ticker.
- Kotlin block comments nest. A `/**` inside a KDoc, for example in a path glob, breaks the file.
- `FakePlayer.openMenu` drops every menu. To check that a block opened its screen, count `openMenu` calls
  in a subclass (`TestKit.MenuCountingPlayer`).
- A new pastured entity sets `ownerUUID` only on its first tick. On the server, read the owner from
  `pokemon.getOwnerUUID()`.
- The GameTest server ticks as fast as it can, so log timestamps are not tick time.
- `/reload` replies before the reload ends. The test pack waits for the next `Loaded N recipes` log line.
- A fake player's first party add makes Cobblemon log harmless ERROR lines about a corrupt or missing
  `pokedex` or `tm_moves` `.old` file.
- The isolated test pack (`scripts/testpack/`) builds clean servers under `run/testpack/`. Never point it
  at the COHO modpack or another existing instance.
- Line endings: `.gitattributes` forces LF (CRLF for `.bat`). `gradlew` breaks on Linux CI with CRLF.
- `scripts/check_plan_coverage.py` must print `missing: 0` after any plan edit.
