# M4: Generator, Crystal Tender, Brew Stoker, Brewer, Pump and Muse

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m4-vanilla-fe-roles`. Builds on M1 to M3.

**Milestone goal:** the last six v1 roles work on vanilla blocks and NeoForge capabilities, with no AE2
import in the main source set.

Before Task 1, check the vanilla and NeoForge references of this milestone: `Capabilities.EnergyStorage.BLOCK`,
`Capabilities.FluidHandler.BLOCK`, `BlockCapabilityCache.create`, `BrewingStandBlockEntity.fuel` and
`brewTime`, `PotionBrewing.hasMix`, `LayeredCauldronBlock.LEVEL`, and `EnchantmentMenu.lambda$slotsChanged$0`
with its `RandomSource#setSeed(J)V` call.

Checked with `javap` on the merged NeoForge 21.1.265 jar before Task 1. Every reference exists as named.
`Capabilities.EnergyStorage.BLOCK` and `Capabilities.FluidHandler.BLOCK` are `BlockCapability<..., Direction>`,
and Kotlin sees the context type as `Direction?`. `BlockCapabilityCache.create(cap, level, pos, context)`
registers its invalidation listener with the level, which holds listeners through weak references, and
`getCapability()` returns null while the position is not loaded. Lookups go through
`ILevelExtension.getCapability(cap, pos, context)` and `getCapability(cap, pos, state, blockEntity, context)`.
`BrewingStandBlockEntity.fuel` and `brewTime` are package-private, so Task 3 needs its access transformer
lines. `PotionBrewing.hasMix(ItemStack, ItemStack)` and `Level.potionBrewing()` are public,
`LayeredCauldronBlock.LEVEL` is public and `receiveStalactiteDrip` is protected.
`EnchantmentMenu.lambda$slotsChanged$0(ItemStack, Level, BlockPos)` calls `RandomSource.setSeed(J)V` once. For
Task 2: `Tags.Blocks.BUDDING_BLOCKS` and `Tags.Blocks.BUDS` are `c:budding_blocks` and `c:buds`,
`BuddingAmethystBlock.canClusterGrowAtState(BlockState)` is public and static, and
`BlockStateBase.randomTick(ServerLevel, BlockPos, RandomSource)` is public.

`TestKit` additions: `testBattery(helper, rel, capacity = Int.MAX_VALUE, refuse: Set<Direction> = emptySet())`
(a test-mod block with an `EnergyStorage` capability that refuses on the given faces), `ae2Block(id)` (looks
up an AE2 block by id, so the test mod needs no AE2 import for placement), and `gametestCompileOnly` of the
AE2 jar for the two tests that read grid energy.

As built: `ae2Block(id)` is in the shared `TestKit`. `testBattery` is an extension of `TestKit` in
`src/gametest/kotlin/.../test/TestBattery.kt`, because only the full test mod registers the battery block
(`cobblemon_dynamics_test:test_battery`) and its capability. The battery records each real insert in `offers`
(the tick, the FE offered and the FE taken), and `drain()` empties it. On a face in `refuse`, its capability is
present but cannot receive. The `gametestCompileOnly` dependency is not added yet: the two tests that read AE2
energy (CD-GEN-04 and CD-GEN-07) are deferred to the end-of-phase pass, which adds it. AE2's
`ForgeEnergyAdapter.getEnergyStored()` gives the AE buffer in FE, so a charger's buffer can also be read through
its FE capability on a power side, with no AE2 import.

---

### Task 1: Generator

**Rows:** CD-GEN-01, CD-GEN-02, CD-GEN-03, CD-GEN-04, CD-GEN-05, CD-GEN-06, CD-GEN-07, CD-BAL-01, CD-CFG-03,
CD-CFG-13, CD-CFG-05 (GameTest on the Generator), the final part of CD-LSH-05.

**Files:**
- Create: `handler/capability/EnergyPort.kt` (internal interface over `IEnergyStorage`, and `FeTick`),
  `handler/capability/FeCurve.kt` (and `GeneratorParams`), `handler/capability/NearestFaces.kt`,
  `handler/capability/FePushHandler.kt`, `roles/generator.json`
- Modify: `handler/TargetHandler.kt` (`ClaimMemo`), `engine/PastureRuntime.kt`, `engine/WorkEngine.kt`,
  `CobblemonDynamics.kt` (registers the handler)
- Test: `src/test/kotlin/.../handler/capability/FeCurveTest.kt`, `EnergyPortTest.kt`, `NearestFacesTest.kt`,
  `src/test/kotlin/.../role/BalanceFilesTest.kt`, `BuiltInRoleFilesTest.kt` (the Generator's Role table row)
- Test: `src/gametest/kotlin/.../test/GeneratorTests.kt`, `src/gametest/kotlin/.../test/TestBattery.kt`,
  `DynamicsTestMod.kt` (registers the battery)

**Interfaces, as built:**

```kotlin
object FeCurve {
    /** round(m × base × (max ÷ base)^((L − 1) ÷ 99)), rounding last (CD-GEN-02). L is clamped to 1..100. */
    fun fePerTick(level: Int, rare: Boolean, base: Int, max: Int, rareMultiplier: Double): Int
    fun isRare(labels: Set<String>, rareLabels: Set<String>): Boolean     // CD-GEN-03: only the listed labels, without case
}
data class GeneratorParams(val base: Int, val max: Int, val rareMultiplier: Double, val rareLabels: Set<String>) {
    fun fePerTick(level: Int, labels: Set<String>): Int
    companion object { fun of(params: JsonObject): GeneratorParams }   // the role file's params (CD-CFG-03)
}
internal interface EnergyPort {                                         // the 1.21.9 transfer API port point
    fun simulate(fe: Int): Int
    fun push(fe: Int): Int
    companion object { fun of(storage: IEnergyStorage): EnergyPort }
}
internal object FeTick {
    fun hasDemand(port: EnergyPort, fePerTick: Int): Boolean          // CD-GEN-06: fePerTick > 0 && simulate(1) == 1
    fun work(port: EnergyPort, fePerTick: Int): Int                    // CD-GEN-05: one push of the rate
}
object NearestFaces { fun of(block: BlockPos, from: Vec3): List<Direction> }   // CD-GEN-04: nearest face first
class ClaimMemo { var value: Any? }                                    // WorkContext.memo
```

`FePushHandler` (`id = cobblemon_dynamics:fe_push`, `scanKind = BLOCK_ENTITY`): `matches` when the block exposes
`Capabilities.EnergyStorage.BLOCK` on one of its six faces with `canReceive()` (CD-GEN-01). `stillValid` asks the
same of the target, so a target that stops receiving ends its claim. `hasDemand` is `FeTick.hasDemand`
(CD-GEN-06), and `applyWork` is `FeTick.work`: one offer of `fePerTick`, and what the target refuses is gone
(CD-GEN-05). AE2 converts FE itself (CD-GEN-07). `generator.json` holds `base_fe_per_tick` 10,
`max_fe_per_tick` 5000, `rare_multiplier` 2.0 and `rare_labels` `["legendary", "mythical"]` (CD-CFG-03), and the
code applies no cap beyond them (CD-CFG-13).

Design decisions (the plan gave steps only):

- **Claim memo.** "A `BlockCapabilityCache` per target" lives in a new `WorkContext.memo`, a `ClaimMemo` that
  the engine keeps in `PastureRuntime.Slot`. `claim` gives each new work claim an empty memo, `clear` drops it
  when the claim ends, and the tick and the release's `onStop` pass the slot's memo. A target that the
  assigner only weighs for a worker gets an empty memo of its own. A map in the handler would keep a
  `ServerLevel` alive after an integrated server stops, because a cache holds its level, and it would keep the
  face of the first worker that probed. Tasks 2 to 4 keep their claim state in the memo too: the Crystal
  Tender's start tick, the Brew Stoker's remembered `fuel` and the Pump's accumulator and fluid cache.
- **Face order (CD-GEN-04, R41).** `NearestFaces.of(target, worker.position())`: the face whose center is
  nearest the worker's feet first, which is the largest dot product of the face normal with the way from the
  target's center to the worker. Ties keep `Direction` order. The handler takes the first face whose
  capability receives and keeps it in the memo, in a `BlockCapabilityCache`, while that capability still
  receives. A face that stops receiving, such as an AE2 machine's new front after a turn, makes it try the
  faces again. Only the six real faces count: AE2 answers a `null` side only when all six faces are power
  sides.
- **Scan kind.** `BLOCK_ENTITY`, because FE machines have block entities. A block whose capability needs none
  can still be bound with the Work Leash, because `matches` reads only the capability.
- **Rate.** `GeneratorParams.of(role.params).fePerTick(pokemon.level, pokemon.form.labels)`, read on each tick,
  as the burner roles read their heat. A level outside 1 to 100 takes the nearest end of the curve, so
  `max_fe_per_tick` stays the most that a worker gives under a raised level cap (CD-BAL-01). The result is
  bounded by `Int.MAX_VALUE`, because `IEnergyStorage` takes an int. A key that a pack's file leaves out takes the
  value of the mod's own file. A rate of 0 gives no demand, so such a worker waits in Standby.
- **Effect.** Cobblemon's `thundershock` timeline runs its steps with no entity condition on the user and its
  `is_user == false` steps on the target. So `actor_particles` are `thundershock_actorsparks`,
  `thundershock_actorbolts` and `thundershock_actor`, and `target_particles` are `thundershock_target`,
  `thundershock_targetbolts` and `thundershock_targetimpact`. The animations are `thundershock`, `electric`,
  `special` and `cry`, and the range is 6.0.
- **CD-GEN-05** is a unit test, as the row's acceptance states, with a fake battery behind `EnergyPort`.
  `cdGen06FullBatteryMeansStandby` repeats it on the real capability.

- [ ] **Step 1: Failing unit tests**

```kotlin
class FeCurveTest {
    private fun fe(l: Int, rare: Boolean = false) = FeCurve.fePerTick(l, rare, 10, 5_000, 2.0)
    @Test fun `CD-GEN-02 the curve hits 10, 45, 217, 1041 and 5000`() =
        assertEquals(listOf(10, 45, 217, 1_041, 5_000), listOf(1, 25, 50, 75, 100).map { fe(it) })
    @Test fun `CD-GEN-02 legendary doubles before rounding`() { assertEquals(433, fe(50, true)); assertEquals(10_000, fe(100, true)) }
    @Test fun `CD-GEN-03 ultra_beast and paradox are not rare`() {
        assertFalse(FeCurve.isRare(setOf("ultra_beast"), setOf("legendary", "mythical")))
        assertFalse(FeCurve.isRare(setOf("paradox"), setOf("legendary", "mythical")))
        assertTrue(FeCurve.isRare(setOf("mythical"), setOf("legendary", "mythical")))
    }
}
class BalanceFilesTest {   // reads the built-in role files with RoleCodec
    @Test fun `CD-BAL-01 the default Generator tops out below 15,000 FE per tick`() { /* 2 × 5,000 = 10,000 < 15,000 */ }
    @Test fun `CD-BAL-07 the Crystal Tender interval is at least 10 ticks`() { /* reads crystal_tender.json params.interval_ticks */ }
    @Test fun `CD-BAL-05 Telekinesis and Magnet move at most 64 items per second if their files exist`() { /* Phase 2 files; passes when absent */ }
}
```

Write the three `BalanceFilesTest` bodies in full: parse the file, compute the stated value, assert the
bound. CD-BAL-07's test lands in Task 2 with the file.

As built, `FeCurveTest` also checks the default file through `GeneratorParams` (CD-CFG-03; CD-GEN-03 with a
Lv100 `ultra_beast` and a Lv100 `paradox` Pokémon at 5,000 FE/t), a level-100 rate of 2,500 (CD-CFG-03) and of
20,000 for a Lv100 legendary (40,000, CD-CFG-13), the level clamp and the int bound. `EnergyPortTest` covers
CD-GEN-05 and CD-GEN-06 with a fake battery, `NearestFacesTest` the face order of CD-GEN-04. CD-BAL-01 takes
the highest rate over levels 1 to 100 of every built-in `fe_push` file. CD-BAL-05 reads
`items_per_second_at_level_100` and computes ceil(rate × L ÷ 100) for L up to 100 (ARCHITECTURE section 6).
`BuiltInRoleFilesTest` gets the Generator's Role table row (CD-FX-02), its particle split (CD-FX-04) and the
range of 6 blocks of every ranged role (CD-ASG-10).

- [ ] **Step 2: GameTests** — as built, three, each on NeoForge's real energy capability:

```kotlin
// cdGen01CapabilityTargets: the handler's matches and stillValid, as the scan and the Work Leash call them: the test battery and
//   an AE2 Energy Acceptor (TestKit.ae2Block("ae2:energy_acceptor")) are targets, a battery that refuses on all six faces and
//   the stone floor are not, and the Generator role is active with FePushHandler (CD-GEN-01).
// cdGen06FullBatteryMeansStandby: an auto-assigned Lv50 Pikachu fills a 2,000 FE battery (nine offers of 217, then one that
//   it takes 47 of); Standby within 20 ticks, Work Stamina never drops and no offer reaches the full battery for 40 ticks
//   (CD-GEN-06); once drained, the next offer is 217 and it is taken whole (CD-GEN-05). Every offer is exactly 217, one per
//   tick (CD-GEN-02).
// cdLsh05PikachuOnBattery: a Lv50 Pikachu bound to a battery; a Voltorb's binding to it is refused with
//   "Already assigned to Pikachu" and gets no binding, and the Pikachu works the battery at 217 FE per tick.
```

  - CD-GEN-04 (`cdGen04NearestAcceptingFace`: an AE2 Charger whose front faces the worker: its buffer rises):
    GameTest deferred (high confidence): verified in the end-of-phase pass. The face order is `NearestFacesTest`,
    and the probe and the push are the same capability calls that the three GameTests run.
  - CD-GEN-07 (`cdGen07Ae2RatioAtLv50`: an Energy Acceptor beside an `ae2:energy_cell`; the grid's stored energy
    rises by 108.5 ± 1 AE per tick): GameTest deferred (high confidence): verified in the end-of-phase pass. The
    handler converts nothing, and `cdGen01CapabilityTargets` shows that the acceptor is a target.
  - CD-CFG-03 (`cdCfg03DatapackSetsLevel100Rate`: batch, `RoleRegistry.applyForTest` with `max_fe_per_tick`
    2,500; a Lv100 Generator gives 2,500): GameTest deferred (high confidence): verified in the end-of-phase pass.
    `FeCurveTest` reads the params, and M1's `cdCfg07` and `cdCfg08` test the reload path.
  - CD-CFG-13 (`cdCfg13NoHiddenCap`: batch, 20,000; a Lv100 legendary gives 40,000 to a battery with no receive
    limit): GameTest deferred (high confidence): verified in the end-of-phase pass. `FeCurveTest` gives 40,000,
    and `FeTick.work` offers the whole rate.
  - CD-CFG-05 (`cdCfg05DisabledGeneratorGetsNoClaims`: batch, `roles.disabled` holds the Generator; an
    Electric-type beside a battery 400 ticks: no claim): GameTest deferred (high confidence): verified in the
    end-of-phase pass. Disabling works by role id, which M1's `cdCfg05DisabledRoleGetsNoClaims` tests on the
    Stoker.
- [ ] **Step 3: Write the code, run, commit** — `git commit -m "Generate Forge Energy with Electric-types on an exponential level curve"`.

---

### Task 2: Crystal Tender

**Rows:** CD-CRY-01, CD-CRY-02, CD-CRY-03, CD-CRY-04, CD-CRY-05 (the guard runs in `check`), CD-BAL-07.

**Files:** `handler/vanilla/BuddingHandler.kt` (`scanKind = BLOCK`), `handler/vanilla/TickClock.kt`,
`roles/crystal_tender.json` (types `["rock"]`, params `{ "interval_ticks": 10 }`, effect Rock Throw),
`src/test/kotlin/.../handler/vanilla/TickClockTest.kt`, `src/gametest/kotlin/.../test/CrystalTenderTests.kt`,
`src/gametestCore/kotlin/.../coretest/CoreTests.kt` (CD-CRY-04). As built, also `CobblemonDynamics.kt` (registers
the handler), `BalanceFilesTest.kt` (CD-BAL-07) and `BuiltInRoleFilesTest.kt` (the Crystal Tender's Role table
row and its particles).

`BuddingHandler` matches blocks in `TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:budding_blocks"))`
(CD-CRY-01), calls `state.randomTick(level, pos, level.random)` when `TickClock.every(10)` fires (CD-CRY-02),
and has demand while some face's neighbor is air, water, or in `c:buds` (a bud that can still grow)
(CD-CRY-03). No AE2 class is named.

As built (`id = cobblemon_dynamics:budding`):

```kotlin
object TickClock {
    /** On the tick [since] and every [intervalTicks] ticks after it (CD-CRY-02). An interval below 1 counts as 1. */
    fun every(intervalTicks: Int, since: Long, now: Long): Boolean
}
```

- **Tag.** `matches` and `stillValid` test `Tags.Blocks.BUDDING_BLOCKS`, NeoForge's constant for
  `c:budding_blocks`. AE2's budding certus loses a tier on some growths and ends as a quartz block, which leaves
  the tag, so `stillValid` ends that claim.
- **Random ticks (CD-CRY-02).** `onStart` puts the tick that the work starts in the claim's memo (Task 1), and
  `applyWork` gives the target one `randomTick` when `TickClock.every(interval_ticks, start, now)` fires: on the
  first tick of the work and every 10 ticks after it. Like AE2's accelerator, it skips `isRandomlyTicking`.
  Standby checks demand every 20 ticks, so a worker that stops at a full block and starts again keeps to the
  rate. `interval_ticks` defaults to 10 when a pack's file leaves it out.
- **Demand (CD-CRY-03).** Vanilla's `BuddingAmethystBlock` and AE2's `BuddingCertusQuartzBlock` grow the same way
  (checked with `javap`): a new bud into a cell for which `BuddingAmethystBlock.canClusterGrowAtState` holds (air,
  or a water source), and the next stage of a bud whose `FACING` is the face's direction. So a face can still
  grow when its neighbor passes `canClusterGrowAtState`, or is in `Tags.Blocks.BUDS` and faces away from the
  block (a bud with no `FACING` counts). A cluster is full grown.
- **Loaded neighbors.** `hasDemand` and the random tick run only while `level.shouldTickBlocksAt(target)`. A
  block-ticking chunk has its neighbors loaded, so neither reads a chunk into memory, and a target whose chunk
  does not tick its blocks waits in Standby.
- **Effect.** Cobblemon's `rockthrow` timeline emits one particle, `rockthrow_actor`, from the user toward the
  target. `rockthrow_target` and `rockthrow_targetstars` are events of that particle, so `target_particles` is
  empty. The animations are `rockthrow`, `rock`, `physical` and `cry`, and the range is 6.0.

- [ ] **Step 1: Failing tests** — `TickClockTest` (ticks 0, 10, 20, none at 9). GameTests: Rock-type claims
  budding amethyst and AE2 flawless budding certus (`ae2:flawless_budding_quartz` by id); six full amethyst
  clusters around a budding block give Standby within 20 ticks. Core run: without AE2, a Rock-type claims
  budding amethyst (CD-CRY-04). `BalanceFilesTest` CD-BAL-07 body.

  As built, the GameTests use a Lv20 Rockruff, a pure Rock-type:

```kotlin
// cdCry01RockTypesClaimBuddingAmethystAndCertus: two Rockruffs hold Crystal Tender claims on a budding amethyst and on
//   ae2:flawless_budding_quartz, both in the floor (CD-CRY-01).
// cdCry02TenderGrowsRealAmethyst: a budding amethyst on the floor, 5 faces open; within 600 ticks of Working a bud of #c:buds
//   (or a cluster) grows on a face, facing away from it (CD-CRY-02 on the real block). ServerChunkCache.tickChunks random-ticks
//   only chunks near a player and the fake players never join, so the growth is the worker's. A miss has a chance of about
//   1 in 70,000.
// cdCry03FullClustersMeanStandby: once the Tender Works, a full amethyst cluster goes on each of the 6 faces: Standby within
//   20 ticks, the claim stays and Work Stamina does not drain (CD-CRY-03). A block with a cluster on every face has no line of
//   sight to any work spot, so the clusters go on after the claim.
// CoreTests.cdCry04CrystalTenderWithoutAe2: without AE2, the role is active and a Rockruff claims a budding amethyst (CD-CRY-04).
```

  `BalanceFilesTest`: every built-in `budding` file has `interval_ticks` of at least 10, and `crystal_tender.json`
  has 10 (CD-BAL-07). No GameTest of this task is deferred.
- [ ] **Step 2: Write the code, run all three test runs, commit** — `git commit -m "Tend budding blocks with Rock-types at one Growth Accelerator's rate"`.

---

### Task 3: Brew Stoker and Brewer

**Rows:** CD-BRF-01, CD-BRF-02, CD-BRF-03, CD-BRF-04, CD-BRF-05, CD-BRW-01, CD-BRW-02, CD-BRW-03, CD-BRW-04,
the final part of CD-ASG-16, CD-FX-02 (every Phase 1 role file).

**Files:** `handler/vanilla/BrewingFuelHandler.kt`, `handler/vanilla/BrewingSpeedHandler.kt`,
`roles/brew_stoker.json` (types `["fire"]`, priority 10, effect Ember), `roles/brewer.json` (types `["poison"]`,
priority 0, effect Poison Gas), access transformer lines for `BrewingStandBlockEntity` `fuel` and `brewTime`,
`src/test/kotlin/.../role/DefaultPriorityTest.kt`, `src/gametest/kotlin/.../test/BrewingTests.kt`.

Brew Stoker: while Working and an ingredient can brew one of the bottles (`level.potionBrewing().hasMix`),
keep `fuel >= 1` (CD-BRF-02); vanilla only reloads fuel from blaze powder at 0, so the powder stays
(CD-BRF-03); demand as stated (CD-BRF-04); remember `fuel` in `onStart` and, in `onStop`, lower it back to
that value if it is higher (CD-BRF-05). Brewer: while a brew runs (`brewTime > 0`), lower `brewTime` by one
extra step per tick and never below 1, so a brew takes 200 ticks (CD-BRW-02); demand while `brewTime > 0`
(CD-BRW-03); never touch `fuel` (CD-BRW-04).

As built (`BrewingFuelHandler` is `cobblemon_dynamics:brewing_fuel`, `BrewingSpeedHandler` is
`cobblemon_dynamics:brewing_speed`, both `scanKind = BLOCK_ENTITY`; also `handler/vanilla/BrewingRules.kt`,
`src/test/kotlin/.../handler/vanilla/BrewingRulesTest.kt`, the handler registrations in `CobblemonDynamics.kt`, and a brewing
recipe in `DynamicsTestMod.kt`):

```kotlin
object BrewingRules {
    const val VANILLA_BREW_TICKS = 400
    fun fuelWhileWorking(fuel: Int, canBrew: Boolean): Int   // CD-BRF-02: max(fuel, 1) while it can brew
    fun fuelAfterStop(fuel: Int, atStart: Int): Int          // CD-BRF-05: min(fuel, atStart)
    fun brewTimeWhileWorking(brewTime: Int): Int             // CD-BRW-02: brewTime − 1, never below 1
}
```

Design decisions (the plan gave steps only):

- **Vanilla's stand, checked with `javap` on the merged jar.** `serverTick` first turns a blaze powder into 20 fuel
  whenever `fuel <= 0`, whether or not anything can brew. A running brew counts `brewTime` down by 1 and ends at 0.
  A stand that does not brew starts one when it can brew and `fuel > 0`: `fuel--` and `brewTime = 400`. Block
  entities tick before the work engine, so the handlers write after the stand's own step of each tick.
- **Can brew.** The handler repeats vanilla's private `isBrewable` with public calls, as NeoForge compiles it: an
  ingredient that `level.potionBrewing().isIngredient` accepts and that `hasMix(bottle, ingredient)` brews with one
  of the three bottles.
- **Fuel.** `applyWork` raises the fuel to 1 only while the stand can brew, and leaves the stand's own fuel as it
  is, as the Stoker leaves a burning furnace's `litTime`. `onStart` keeps the fuel of that moment in the claim's
  memo, and `onStop` lowers the fuel to it. A stopped Brew Stoker whose chunk is gone writes nothing, because a
  read would load the chunk.
- **Blaze powder between brews (a consequence of the rows).** No vanilla brew can follow itself: after a brew,
  nothing can brew until the player changes the stand. The worker then enters Standby (CD-BRF-04) and gives the
  stand its own fuel back (CD-BRF-05). When that fuel is 0, vanilla turns a blaze powder in the fuel slot into
  20 fuel on its next tick. So the powder stays only while the worker Works (CD-BRF-03). `cdBrf03` uses a brew
  that can follow itself, which the test mod registers through `RegisterBrewingRecipesEvent`: water and a stick
  give water.
- **Brew time.** The Brewer's step comes after the stand's step, so a brew that the Brewer works from its first
  tick ends 200 ticks after it starts (`BrewingRulesTest` simulates vanilla's countdown). The Brewer has demand
  only while a brew runs, and a worker in Standby checks for demand every 20 ticks (CD-ASG-12), so it starts on a
  new brew up to 19 ticks late, and that brew takes up to 210 ticks. `cdBrw02` starts its brew on the tick of the
  Brewer's Standby check.
- **Effects.** Cobblemon's `ember` timeline sends `ember_actor` from the user to the target and plays `ember_target`
  on the target only (`is_user == false`). `poisongas` does the same with `poisongas_actor` and `poisongas_target`.
  So `actor_particles` and `target_particles` hold one of each. The animations are `ember`, `fire`, `spray`,
  `special` and `cry`, and `poisongas`, `spray`, `special` and `cry`. The range is 6.0.
- **CD-ASG-16.** `DefaultPriorityTest` gives `Assigner.pickForIdle` each target with every default role whose
  handler matches it, as a scan does: `burner_heat` for a Blaze Burner, and `brewing_fuel` and `brewing_speed` for a
  stand. A pure Fire-type on the burner and a pure Poison-type on the stand get the other role.

- [ ] **Step 1: Failing tests** — `DefaultPriorityTest` (CD-ASG-16 with the default files: Turtonator
  `fire`+`dragon` on a burner gets the Superheater; Salazzle `poison`+`fire` on a stand gets the Brew Stoker);
  extend `BuiltInRoleFilesTest` to all eleven machine roles from the Role table (CD-FX-02). GameTests: one per
  BRF and BRW row, from the acceptance text (3 water bottles and nether wart with no powder completes; 1
  powder stays after 3 brews; removing the ingredient gives Standby within 20 ticks; fuel 0 worked then
  released reads 0; a worked brew takes 198 to 202 ticks; equal fuel after one brew each with and without a
  Brewer).

  As built, `BrewingRulesTest` covers the four rules (CD-BRF-02, CD-BRF-03, CD-BRF-05, CD-BRW-02) on a model of
  vanilla's `serverTick`. `BuiltInRoleFilesTest` gets the two Role table rows (CD-FX-02) and both particle splits
  (CD-FX-04), and Tasks 4 and 5 add the Pump and the Muse. Three GameTests, each on a real stand:

```kotlin
// cdBrf02BrewStokerBrewsWithNoBlazePowder: a Lv10 Charmander claims a stand with 3 water bottles, a nether wart and no powder as
//   the Brew Stoker (CD-BRF-01); the stand holds fuel 1 or more on every Working tick, and 3 awkward potions brew (CD-BRF-02);
//   with the wart used up, Standby within 20 ticks (CD-BRF-04) and fuel 0 (CD-BRF-05).
// cdBrf03BlazePowderStaysThroughThreeBrews: 3 water bottles and 4 sticks; 1 blaze powder goes in once the Brew Stoker Works; the
//   worker Works and the stand holds fuel 1 or more through 3 brews, and the powder count stays 1 (CD-BRF-02, CD-BRF-03); then the
//   last stick goes: Standby within 20 ticks (CD-BRF-04), fuel 0 (CD-BRF-05).
// cdBrw02WorkedBrewTakes200Ticks: a Lv20 Ekans claims the stand in its work area as the Brewer (CD-BRW-01) and waits in Standby
//   with no brew (CD-BRW-03); both stands hold fuel 20; the brew that it works from its first tick takes 198 to 202 ticks
//   (CD-BRW-02), then Standby within 20 ticks (CD-BRW-03); the stand outside the work area takes 400; both end with equal fuel
//   (CD-BRW-04).
```

  - CD-BRF-05 (the row's literal case: a stand at fuel 0 is worked, then released before a brew starts): GameTest
    deferred (high confidence): verified in the end-of-phase pass. The release path calls the same `onStop` with
    the claim's memo as Standby does, which `cdBrf02` and `cdBrf03` check.
  - CD-BRF-05 at a server stop: before the levels save, `WorkEngine.stopWorkingHandlers()` calls `onStop` for every
    Working worker. Without it, each stop during work saved the stand with one unit of the worker's fuel, which is one
    free brew. `cdBrf05ServerStopLeavesNoFuel` (own batch `server_stop`, because the call reaches every worker) checks
    it. The fix came after review, in its own commit.
- [ ] **Step 2: Write the code, run, commit** — `git commit -m "Fuel and speed up brewing stands with Fire- and Poison-types"`.

---

### Task 4: Pump

**Rows:** CD-PMP-01, CD-PMP-02, CD-PMP-03, CD-PMP-04, CD-PMP-05, CD-PMP-06, the final part of CD-ASG-05.

**Files:** `handler/capability/FluidPort.kt`, `handler/capability/FluidFillHandler.kt`,
`handler/vanilla/CauldronFill.kt`, `roles/pump.json` (types `["water"]`, `auto_assign` false, params
`{ "mb_per_level": 5 }`, effect Water Gun), `src/test/kotlin/.../handler/vanilla/CauldronFillTest.kt`,
`src/gametest/kotlin/.../test/PumpTests.kt`.

`FluidFillHandler` (`scanKind = NONE`, leash only): targets blocks whose `FluidHandler.BLOCK` accepts a
simulated 1 mB water fill, and vanilla cauldrons (CD-PMP-01). It offers `5 × L` mB per tick (CD-PMP-03).
Demand: a simulated 1 mB fill succeeds (CD-PMP-05). Cauldrons go through `CauldronFill`: an accumulator per
target raises the level one step per 333 mB by setting the block state, as `receiveStalactiteDrip` does
(CD-PMP-04); demand while empty or water below level 3, never for lava or powder snow (CD-PMP-06).

As built (`id = cobblemon_dynamics:fluid_fill`; also `handler/capability/PumpParams.kt`, `FluidPortTest.kt` and
`PumpParamsTest.kt`, the handler registration in `CobblemonDynamics.kt`, and the Pump's Role table row, particles and
flag in `BuiltInRoleFilesTest.kt`):

```kotlin
internal interface FluidPort {                                  // water only; the 1.21.9 transfer API port point
    fun simulate(mb: Int): Int
    fun fill(mb: Int): Int
    fun holdsWater(): Boolean
    companion object { fun of(handler: IFluidHandler): FluidPort }
}
internal object FluidTick {
    fun acceptsWater(port: FluidPort): Boolean               // CD-PMP-01: simulate(1) == 1, or a tank holds water already
    fun hasDemand(port: FluidPort, mbPerTick: Int): Boolean  // CD-PMP-05: mbPerTick > 0 && simulate(1) == 1
    fun work(port: FluidPort, mbPerTick: Int): Int           // CD-PMP-03: one fill of the rate
}
data class PumpParams(val mbPerLevel: Double) {
    fun mbPerTick(level: Int): Int                           // round(mb_per_level × L), inside an int
    companion object { fun of(params: JsonObject): PumpParams }
}
object CauldronFill {
    const val MB_PER_LEVEL = 333
    const val FULL_LEVEL = 3
    enum class Contents { EMPTY, WATER, LAVA, POWDER_SNOW }
    data class Fill(val level: Int, val storedMb: Int)
    fun hasDemand(contents: Contents, level: Int): Boolean        // CD-PMP-06
    fun deliver(level: Int, storedMb: Int, offeredMb: Int): Fill  // CD-PMP-04
}
```

Design decisions (the plan gave steps only):

- **Targets.** `matches` takes vanilla's four cauldrons, and any block whose `FluidHandler.BLOCK` on one of its six
  faces takes a simulated 1 mB of water or already holds water in a tank. The plan named the simulated fill only,
  which refuses the bind to a water tank that happens to be full. A tank of another fluid takes neither, so its bind
  is refused. `stillValid` asks only for a fluid capability on some face, or a cauldron, so a tank that fills up or
  changes its fluid keeps its binding and has no demand (CD-PMP-05), and a cauldron that changes between
  `minecraft:cauldron` and `minecraft:water_cauldron` stays the target.
- **Cauldrons never use the capability.** NeoForge's wrapper fills an empty cauldron only with a whole 1,000 mB, so
  the handler checks the four vanilla cauldron blocks first. The block decides the contents, not the `LEVEL`
  property, because the powder snow cauldron has one too.
- **Face order.** As the Generator does (Task 1): the claim keeps, in its memo, a `BlockCapabilityCache` of the face
  nearest the worker that takes water, and tries the faces again when that face stops taking it. For a cauldron, the
  memo holds the mB delivered toward its next level instead.
- **Rate.** `PumpParams.of(role.params).mbPerTick(pokemon.level)` on each tick, 5 × L with the default file. The
  rate is linear, so it has no level clamp, unlike the Generator's curve: a Lv150 Pump under a raised level cap gives
  750 mB per tick (CD-CFG-13). A `mb_per_level` that a pack's file leaves out takes 5. One fill per tick, and what the
  target refuses is gone, as with FE.
- **Cauldron fill.** `CauldronFill.deliver` adds the tick's offer to the claim's stored mB and raises the level once per
  333 mB, up to 3. A cauldron that fills keeps nothing. The new level goes in with `setBlockAndUpdate` and the
  `BLOCK_CHANGE` game event, as `LayeredCauldronBlock.receiveStalactiteDrip` does (checked with `javap`). Its drip
  sound (level event 1047) stays out, because work sounds play only for clients that turn them on (ADR-0007).
- **Effect.** The Pump casts the Wheel Driver's Water Gun, with the same split: `watergun_spray` and `watergun_actor`
  from the worker, `watergun_target` and `watergun_targetfoam` at the target.

- [ ] **Step 1: Failing tests** — `CauldronFillTest` (50 mB per tick reaches level 3 at tick 20; the five
  demand cases). GameTests: bind to a Create fluid tank, a basin and a cauldron (CD-PMP-01); a Lv1 Pump adds
  5,000 ± 50 mB in 1,000 ticks and a Lv100 Pump 500 mB per tick (CD-PMP-03); a full tank gives Standby within
  20 ticks (CD-PMP-05); an unleashed Water-type beside an empty tank 400 ticks gets no claim (CD-ASG-05);
  the role file has `"auto_assign": false` (CD-PMP-02, in `BuiltInRoleFilesTest`).

  As built, `CauldronFillTest` also checks levels 1 and 2 at ticks 7 and 14, a 500 mB fill in 2 ticks, and that a
  full cauldron keeps nothing. `FluidPortTest` covers CD-PMP-01, CD-PMP-03 and CD-PMP-05 with a fake tank, and
  `PumpParamsTest` the rate of the default file. Four GameTests, through NeoForge's real fluid capability, with
  Create's blocks placed by id (`TestKit.ae2Block`), so no test names a Create class:

```kotlin
// cdPmp01BindsToATankABasinAndACauldron: a Lv10 Squirtle bound to create:fluid_tank, then create:basin, then a cauldron; each bind
//   gives a Pump binding (CD-PMP-01); the empty cauldron is full after 19 to 21 ticks of work (CD-PMP-04).
// cdPmp03Lv1PumpAdds5000MbIn1000Ticks: the tank holds 5 × n mB after n ticks of work, and 5,000 ± 50 after 1,000 (CD-PMP-03).
// cdPmp03Lv100PumpGives500MbPerTickUntilTheTankIsFull: 500 mB on each tick of work up to the tank's 8,000 mB (CD-PMP-03); then
//   Standby within 20 ticks, on its claim (CD-PMP-05).
// cdAsg05UnleashedWaterTypeLeavesAnEmptyTankAlone: an unleashed Squirtle 2 blocks from an empty Create fluid tank holds no claim and
//   stays Idle for 400 ticks (CD-ASG-05).
```

  - CD-PMP-06 (the five cauldrons of the row: an empty one and water at level 2 have demand, water at level 3, lava
    and powder snow have none): GameTest deferred (high confidence): verified in the end-of-phase pass.
    `CauldronFillTest` holds the five cases, and the handler maps the four cauldron blocks by block, not by `LEVEL`.
- [ ] **Step 2: Write the code, run, commit** — `git commit -m "Pump water into tanks and cauldrons with leashed Water-types"`.

---

### Task 5: Muse

**Rows:** CD-MUS-01, CD-MUS-02, CD-MUS-03, CD-MUS-04, CD-MUS-05, CD-MUS-06, CD-HEAL-14.

**Files:** `mixin/vanilla/EnchantmentMenuMixin.java`, `mixin/vanilla/EnchantmentMenuAccess.java` (accessor for
`access`), `handler/vanilla/MuseBonus.kt`, `handler/vanilla/EnchantingHandler.kt`, `roles/muse.json` (types
`["fairy"]`, `auto_assign` false, effect Fairy Wind), `src/test/kotlin/.../handler/vanilla/MuseBonusTest.kt`,
`src/gametest/kotlin/.../test/MuseTests.kt`.

The mixin adds the Muse's bookshelves to the count that the menu's offers use. As planned, a `@ModifyVariable` on the
power before vanilla seeds its random source:

```java
@ModifyVariable(method = "lambda$slotsChanged$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;setSeed(J)V"))
private float cobblemon_dynamics$shelves(float power, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos table) {
    return MuseBonus.capped(power, MuseBonus.of(level, table));
}
```

That hook crashed the game at startup: Mixin's local analysis finds no `float` local at the `setSeed` call ("Found 0
candidate variables but exactly 1 is required"), and a `@ModifyVariable` with an implicit local throws an
`InvalidInjectionException` whatever `defaultRequire` says. The lambda's local variable table holds `j` (slot 5) up to
the method's end, so the analysis most likely drops it at the `chop` frame after the bookshelf loop, whose iterator
has no table entry. As built, a `@ModifyArg` on the count that the lambda passes to `getEnchantmentCost` for each of the
three offers, with the table read from the menu's own `access` field:

```java
@Shadow @Final private ContainerLevelAccess access;

@ModifyArg(method = "lambda$slotsChanged$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"),
        index = 2)
private int cobblemon_dynamics$shelves(int bookshelves) {
    return MuseBonus.capped(bookshelves, access.evaluate(MuseBonus::of, 0));
}
```

`MuseBonus.forLevel(L) = ceil(15 × L ÷ 100)` (CD-MUS-03), `capped(real, bonus) = min(15, real + bonus)`
(CD-MUS-04). The handler has demand only while some player's open `EnchantmentMenu` belongs to that table
(CD-MUS-05; track menus with `PlayerContainerEvent.Open` and `Close`). A missing mixin disables the role
through `MixinGate` (CD-MUS-06).

As built (`EnchantingHandler` is `cobblemon_dynamics:enchanting`, `scanKind = NONE`; also `handler/vanilla/EnchantingMenus.kt`,
the two mixins in `cobblemon_dynamics.mixins.json`, `MixinGate.REQUIRED`, the registrations in `CobblemonDynamics.kt`, and
the Muse's Role table row, particles and flag in `BuiltInRoleFilesTest.kt`):

```kotlin
object MuseBonus {
    const val MAX_BOOKSHELVES = 15
    fun forLevel(level: Int, atLevel100: Double): Int          // CD-MUS-03: ceil(atLevel100 × L ÷ 100), at most 15
    fun atLevel100(params: JsonObject): Double                 // the role file's bookshelves_at_level_100, default 15
    @JvmStatic fun capped(real: Int, bonus: Int): Int          // CD-MUS-04
    @JvmStatic fun of(level: Level, table: BlockPos): Int      // the Working Muse's bookshelves at the table, else 0
}
object EnchantingMenus {                                       // CD-MUS-05: open enchanting menus by table
    fun menusAt(level: ServerLevel, table: BlockPos): List<EnchantmentMenu>
    fun isOpenAt(level: ServerLevel, table: BlockPos): Boolean
    fun refresh(level: ServerLevel, table: BlockPos)           // the open menus compute their offers again
}
```

Design decisions (the plan gave steps only):

- **The count, not the power before the seed.** Vanilla seeds the menu's random source with the player's enchantment
  seed before the first offer, and `getEnchantmentCost` draws `nextInt(8)` and `nextInt(count + 1)` whatever the count,
  so the offers equal those of a table with that many shelves, as the plan wanted. The `@ModifyArg` names the lambda
  with its full descriptor, so a lambda of another shape is no target, and `defaultRequire: 0` then injects nothing:
  `MixinState` sees no call to the handler and `MixinGate` turns the Muse off with one warning (CD-MUS-06). Mixin's own
  `@ModifyArg` cannot capture the target's arguments (checked with `javap` on Mixin 0.16.4), so the handler reads the
  table through `@Shadow access`, with no MixinExtras `@Local`. NeoForge's `EnchantmentLevelSetEvent` still gets the
  table's own count.
- **The cap.** `capped` adds the Muse's count only up to 15, never lowers a count that other mods' shelves raised above
  15, and returns the count unchanged when no Muse Works at the table. `forLevel` stops at 15 too. The formula's number
  is the role file's `bookshelves_at_level_100` (ARCHITECTURE section 9), 15 by default, with no level clamp.
- **Which Muse.** `MuseBonus.of` finds the work claim on the table in the claim registry, and counts it only while its
  worker is Working with a role of `EnchantingHandler`. It runs inside `guarded`, so a failure turns the handler off with
  one warning (CD-PERF-05) and the menu keeps vanilla's count.
- **Open menus (CD-MUS-05).** `EnchantingMenus` keeps one entry per player from `PlayerContainerEvent.Open`, with the
  table from the menu's `access` (the accessor mixin `EnchantmentMenuAccess`; Mixin lets other code load an accessor). An
  entry ends on `Close`, on logout and at server stop, and an entry whose player is removed, or whose `containerMenu` is
  another menu, ends when the next demand check sees it. `ServerPlayer.openMenu` sets `containerMenu` before it posts
  `Open`.
- **Offers follow the Muse.** The handler's `onStart` and `onStop` make the table's open menus compute their offers again
  (`slotsChanged` on the enchanting slots), so a pickaxe that the player put in before the Muse began gets the Muse's
  offers. The engine sets Working before `onStart`, and releases the claim or leaves Working before `onStop`, so the
  first sees the Muse and the second does not. The seed stays, so only the count changes the offers. `applyWork` does
  nothing: the stamina drain while Working is the Muse's cost.
- **Leash only.** `scanKind = NONE`: `auto_assign` is false, and a Fairy-type never auto-assigns anyway (CD-ASG-04).
  `matches` and `stillValid` take `minecraft:enchanting_table` only.
- **CD-HEAL-14.** The engine already gives a bound Fairy-type the worker rule (`RecoveryRule.of`, M2), and `assignHeals`
  skips any worker that does not follow the healer rule. The GameTest checks it with a real Muse binding.
- **Effect.** Cobblemon's `fairywind` timeline sends `fairywind_actor` from the user to the target, and plays
  `fairywind_target` and `fairywind_targethit` on the target only. The animations are `fairywind`, `special` and `cry`,
  and the range is 6.0.

- [ ] **Step 1: Failing tests** — `MuseBonusTest` (Lv1 1, Lv7 2, Lv50 8, Lv100 15; with a Lv50 Muse, 12, 7 and
  6 real shelves count 15, 15 and 14); `MixinGateTest` extended for the Muse. GameTests: bind a Clefairy to a
  table (CD-MUS-01); no menu 600 ticks then a menu open 600 ticks: stamina drains only while open (CD-MUS-05);
  a leashed Clefairy Muse and a Tired ally share a pasture for 1,200 ticks: no heal (CD-HEAL-14).
  `BuiltInRoleFilesTest` checks `"auto_assign": false` (CD-MUS-02).

  As built, `MuseBonusTest` also checks that no Muse leaves the count as it is, that a Muse never lowers a count above
  15, and the role file's formula. `MixinGateTest` starts from the real flag, which is false in a unit test because no
  mixin applies there (CD-MUS-06), and keeps the Muse when its flag is set. Three GameTests, each with a Lv50 Clefairy
  bound by the Work Leash, which checks CD-MUS-01 each time. A test opens a menu as `ServerPlayer.openMenu` does, because
  a fake player drops every menu:

```kotlin
// cdMus03Lv50MuseGivesATableTheOffersOfEightShelves: a pickaxe goes into the menu of the Muse's table, which has no shelves,
//   while the Muse waits; the Muse Works within 20 ticks (CD-MUS-05), and the menu's costs and clues then equal, for the same
//   player, those of a table with 8 real bookshelves (CD-MUS-03 on a real table).
// cdMus05StaminaDrainsOnlyWhileTheMenuIsOpen: Standby and no drop of Work Stamina for 600 ticks with no menu; then a menu stays
//   open for 600 ticks: Working within 20 ticks, and Work Stamina drops on every tick after that (CD-MUS-05).
// cdHeal14LeashedMuseNeverHealsATiredAlly: a Tired Lv10 Machop shares the pasture for 1,200 ticks; the Muse follows the worker
//   rule, never takes a heal claim and is never Healing, and the Machop stays Tired (CD-HEAL-14).
```

  No GameTest of this task is deferred.
- [ ] **Step 2: Write the code, run all runs, commit, push, open the pull request** —
  `./gradlew build runGameTestServer runGameTestServerCore`; `/commit`; `pr-runner` with base `main`. As built, the
  commit message is "Add bookshelf power to enchanting tables with leashed Fairy-types".
