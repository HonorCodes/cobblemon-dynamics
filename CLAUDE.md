# Cobblemon Dynamics: agent gotchas

Product facts live in `ARCHITECTURE.md`, the spec sheet and the ADRs. This file holds only what bites
when your hands are on the code.

- Gradle needs JDK 21 through `JAVA_HOME` (`C:\Program Files\Java\jdk-21` on the dev box). The `java` on
  the PATH there is Java 8, so a bare `java` call or a missing `JAVA_HOME` fails with a class-version error.
- The GameTest log is `run/gametest/logs/latest.log`. `./gradlew runGameTestServer` exits non-zero when a
  required test fails; grep the log for `required tests` and `failed at`.
- GameTest owners are NeoForge `FakePlayer`s (`TestKit.owner`). Vanilla's `makeMockServerPlayerInLevel`
  joins the player list, and its connection rejects Cobblemon's login payloads.
- `Pokemon.entity` is null for pastured Pokémon (it reads `ActivePokemonState` only). Find a pastured
  entity in the level by `entity.pokemon.uuid`.
- The isolated test pack (`scripts/testpack/`) builds clean servers under `run/testpack/`. Never point it
  at the COHO modpack or another existing instance.
- Line endings: `.gitattributes` forces LF (CRLF for `.bat`). `gradlew` breaks on Linux CI with CRLF.
- `scripts/check_plan_coverage.py` must print `missing: 0` after any plan edit.
