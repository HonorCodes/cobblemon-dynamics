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
- `helper.absolutePos` counts from the structure block, which sits one block below the template, so relative y = 1
  is the template's stone floor. `TestKit` puts pastures and furnaces at y = 1, into the floor; Pokémon stand at y = 2.
- Vanilla 1.21.1's `GameTestHelper.relativePos` turns a position 180 degrees and gives (-x, y, -z). Use
  `TestKit.relative`.
- A global override (engine settings, day time, Cobblemon's action effects) leaks into the tests that run beside it.
  Give it its own batch and set it in `@BeforeBatch` and `@AfterBatch` methods, one of each per batch name.
- A furnace with both fuel and input burns the fuel on its first tick.
- ktlint strips `->` from a zero-argument lambda, which makes `AttachmentType.builder` ambiguous. Write
  `Supplier { ... }`.
- `Pokemon.entity` is null for a newly pastured Pokémon until its entity has ticked. Find a pastured
  entity in the level by `entity.pokemon.uuid` instead.
- Removing a pastured Pokémon from its PC removes its pasture entity: `PCStore.remove` calls `recall()` on
  the entity's `SentOutState`. `PokemonStore.set` over an occupied slot does the same.
- NeoForge's `Entity.getPersistentData()` creates the tag on its first read, so the entity then saves an empty
  `NeoForgeData`. Check a cheaper condition first on entities that are not ours.
- Cobblemon saves the PC and the party only at `ServerStoppedEvent`. Write anything that must reach
  `persistentData` at shutdown in `ServerStoppingEvent`.
- A `BaseEntityBlock` renders as invisible unless it overrides `getRenderShape` to return `MODEL`. No
  server-side test catches this.
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
- Mixin refuses to load any class from the mixin package (`IllegalClassLoadError`). Duck interfaces and
  state classes such as `MixinState` live outside `codes.honor.cobblemondynamics.mixin`.
- Create attaches block entity behaviours on the first tick or load, so `DriveBehaviour.of` is null in the
  tick that a block is placed. Wait one tick in GameTests.
- A large water wheel builds its ring on the next tick and needs free cells around its centre. With the
  centre on the floor, the wheel breaks itself.
- The core GameTest run (`runGameTestServerCore`) logs to `run/gametest-core/logs/latest.log`. Shared test
  code (`src/gametest/kotlin-shared/`) must not name a Create class.
- Create uses a hand crank or an analog lever for any held item except its wrench, and cancels the click,
  so an item's `onItemUseFirst` never runs there. `ItemUseOverridesMixin` exempts the Work Leash.
- A crank's `turn(false)` gives -32 RPM when the crank faces along a negative axis.
- Navigation steers a mob 1 to 2 blocks wide to a corner of its target cell, not the centre. Compare
  positions with `Arrival.isAt`, never with the spot centre.
- A mechanical piston does not assemble without an extension pole behind it, and does nothing if its speed
  points inward. A west-facing piston extends with positive speed.
- A Create boiler counts heat only with an engine attached. GameTests call `boiler.updateTemperature(tank)`.
- GameTest chunks get no natural random ticks, because `ServerChunkCache.tickChunks` needs a real player
  nearby and fake players never join the level. Random-tick growth in a test comes from the code under test.
- Kotlin sees the context type of NeoForge's `Capabilities.*.BLOCK` as `Direction?`, so type a cache field
  `BlockCapabilityCache<T, Direction?>`.
- A target that has blocks with collision on all six faces has no line of sight from any work spot, so it is
  never claimed. A test that needs one adds the blockers after the claim.
- The test battery block lives in the full test mod only. `TestKit.testBattery` is an extension in
  `src/gametest/kotlin`, so shared test code must not name it.
- A `@ModifyVariable` on an implicit local crashes the game when Mixin finds no single match, whatever
  `defaultRequire` says. `@ModifyArg` cannot capture the target method's arguments, so read state through
  `@Shadow`.
- No vanilla brew can follow itself. A test that needs back-to-back brews uses the test mod's stick recipe.
- On the first Working tick the engine has already done that tick's work. Wait for Working before you
  assert that nothing has happened yet.
- To fake an open menu, set `player.containerMenu` and post `PlayerContainerEvent.Open`. Close it in
  `afterTest`, or the open-menu tracker can mark a later test's table at the same position as open.
- A call that reaches every worker, such as `WorkEngine.stopWorkingHandlers()`, needs its own GameTest batch.
- `./gradlew runClient --args=...` replaces ModDevGradle's own arguments, and the launch fails. Add arguments to
  `neoForge.runs.client.programArguments` (for example through an init script passed with `-I`).
- A fresh `run/client` opens the accessibility onboarding screen, and quick play waits behind it. Write
  `run/client/options.txt` with `onboardAccessibility:false` first.
- Client mixins on Cobblemon GUI classes apply only when a pasture panel first opens, so `MixinState` reads false
  for them until then.
- Mixin refuses to target another mixin's handler. To change what another mod's handler does, hook the methods
  that it calls (see the Cobblebase `@Pseudo` mixins).
- Cobblebase 2.0.1 needs Cloth Config at run time but does not declare it. The test pack pins both.
- A screen gives a click to its first child that takes it, so a widget added in `ScreenEvent.Init.Post` catches
  its own clicks in `MouseButtonPressed.Pre`. `AbstractWidget.render` is final, so refresh a widget in
  `ScreenEvent.Render.Pre`.
- NeoForge's `AFTER_TRANSLUCENT_BLOCKS` render stage has a fresh pose stack, and the view rotation is on the
  model-view stack. Draw relative to the camera.
- A unit test that reads a repository file by path needs that file in the `test` task's inputs in
  `build.gradle`. Otherwise Gradle and the CI build cache reuse a stale pass.
- NeoForge uses a GameTest's namespace as its structure namespace, so a test in its own namespace (the perf
  run) needs its own copy of the structure.
- CI's super-linter v9.0.0 runs ruff 0.16.8, which sorts modules new in Python 3.11 (such as `tomllib`) as
  third-party, unlike local ruff 0.17. Import such a module inside the function that uses it.
- Two Pokémon that walk toward each other on the same row of cell centres push each other back and never pass,
  unless both are workers that the engine directs: `WorkerPushMixin` skips their push.
- A jar built with `-Prelease_version` stays in `build/libs`, and the test pack installs the newest jar there.
  Delete it before you run the test pack.
- `data merge block` sets a block entity's saved state without a player, and a new folder in
  `world/datapacks/` is enabled by the next `/reload`.
- Wait one tick after `releasePokemon` before you tether the same Pokémon to another pasture, or it loses
  the new tethering.
- GameTest chunks are force-loaded, so a target near its pasture never really unloads in a test.
- A pasture whose only worker is leashed scans for nothing. To test that a leashed worker ignores other
  targets, add an unleashed worker of the same role.
- A trade test sets both offers and both acceptances before `performTrade`, or Cobblemon crashes.
- An empty-hand `TestKit.clickBlock` on a pasture creates Cobblemon's pasture link, which payload tests need.
- Fake players never tick their inventory, so a test calls `inventoryTick` itself.
- `ServerPlayer.getPermissionLevel()` is protected. Use `hasPermissions(n)`.
- To record the packets a player gets, build a `ServerGamePacketListenerImpl` over
  `Connection(PacketFlow.CLIENTBOUND)` and add the player to `ServerLevel.players` by reflection, in its own
  batch (`FakePlayerNetHandler` is private).
- AE2's `IEnergyService.getStoredPower()` is recomputed only every 90 ticks. Read the cell's `IAEPowerStorage`.
- Create's valve handle takes a Work Leash click before the leash sees it.
- A target at y = 1 sits in the floor, which blocks the line of sight from cells more than about 2 blocks away.
  Put such a target at y = 2.
- The Blaze Burner's fuel and creative fields are protected. Set them through its NBT with
  `loadWithComponents`.
- The stamina step recovers before it checks thresholds, so a test value equal to a threshold passes it in one
  tick.
- `/time` changes the day time of every level, so a test that uses it needs its own batch with an
  `@AfterBatch` reset.
- On Windows, Python's `write_text` writes CRLF unless you pass `newline="\n"`. Check for CR characters
  before staging.
- The isolated test pack (`scripts/testpack/`) builds clean servers under `run/testpack/`. Never point it
  at the COHO modpack or another existing instance.
- Line endings: `.gitattributes` forces LF (CRLF for `.bat`). `gradlew` breaks on Linux CI with CRLF.
- `scripts/check_plan_coverage.py` must print `missing: 0` after any plan edit.
