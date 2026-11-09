# M4: Generator, Crystal Tender, Brew Stoker, Brewer, Pump and Muse

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m4-vanilla-fe-roles`. Builds on M1 to M3.

**Milestone goal:** the last six v1 roles work on vanilla blocks and NeoForge capabilities, with no AE2
import in the main source set.

Before Task 1, check the vanilla and NeoForge references of this milestone: `Capabilities.EnergyStorage.BLOCK`,
`Capabilities.FluidHandler.BLOCK`, `BlockCapabilityCache.create`, `BrewingStandBlockEntity.fuel` and
`brewTime`, `PotionBrewing.hasMix`, `LayeredCauldronBlock.LEVEL`, and `EnchantmentMenu.lambda$slotsChanged$0`
with its `RandomSource#setSeed(J)V` call.

`TestKit` additions: `testBattery(helper, rel, capacity = Int.MAX_VALUE, refuse: Set<Direction> = emptySet())`
(a test-mod block with an `EnergyStorage` capability that refuses on the given faces), `ae2Block(id)` (looks
up an AE2 block by id, so the test mod needs no AE2 import for placement), and `gametestCompileOnly` of the
AE2 jar for the two tests that read grid energy.

---

### Task 1: Generator

**Rows:** CD-GEN-01, CD-GEN-02, CD-GEN-03, CD-GEN-04, CD-GEN-05, CD-GEN-06, CD-GEN-07, CD-BAL-01, CD-CFG-03,
CD-CFG-13, CD-CFG-05 (GameTest on the Generator), the final part of CD-LSH-05.

**Files:**
- Create: `handler/capability/EnergyPort.kt` (internal interface over `IEnergyStorage`), `handler/capability/FeCurve.kt`,
  `handler/capability/FePushHandler.kt`, `roles/generator.json`
- Test: `src/test/kotlin/.../handler/capability/FeCurveTest.kt`, `src/test/kotlin/.../role/BalanceFilesTest.kt`
- Test: `src/gametest/kotlin/.../test/GeneratorTests.kt`

**Interfaces:**

```kotlin
object FeCurve {
    /** round(m × base × (max ÷ base)^((L − 1) ÷ 99)), rounding last (CD-GEN-02). */
    fun fePerTick(level: Int, rare: Boolean, base: Int, max: Int, rareMultiplier: Double): Int
    fun isRare(labels: Set<String>, rareLabels: Set<String>): Boolean     // CD-GEN-03: only the listed labels
}
interface EnergyPort { fun simulate(fe: Int): Int; fun push(fe: Int): Int }   // the 1.21.9 transfer API port point
```

`FePushHandler` (`id = cobblemon_dynamics:fe_push`): `matches` when the block exposes
`Capabilities.EnergyStorage.BLOCK` on some face with `canReceive()` (CD-GEN-01). It tries the face toward
the worker first, then the other five, and caches the first face that receives in a
`BlockCapabilityCache` per target (CD-GEN-04, R41). `applyWork` offers `fePerTick` once and drops what the
target refuses (CD-GEN-05). `hasDemand` is `simulate(1) == 1` (CD-GEN-06). AE2 converts FE itself
(CD-GEN-07). `generator.json` holds `base_fe_per_tick` 10, `max_fe_per_tick` 5000, `rare_multiplier` 2.0
and `rare_labels` `["legendary", "mythical"]` (CD-CFG-03), and the code applies no cap beyond them
(CD-CFG-13).

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

- [ ] **Step 2: Failing GameTests** — `cdGen01CapabilityTargets` (an AE2 Energy Acceptor and a test battery
  are targets, a battery that refuses on all faces is not), `cdGen04NearestAcceptingFace` (an AE2 Charger
  whose front faces the worker: its buffer rises), `cdGen05NoHiddenBuffer` (a full battery refuses 217; once
  emptied, the next tick offers 217), `cdGen06FullBatteryMeansStandby` (Standby within 20 ticks, no drain),
  `cdGen07Ae2RatioAtLv50` (an Energy Acceptor beside an `ae2:energy_cell`; the grid's stored energy rises by
  108.5 ± 1 AE per tick), `cdCfg03DatapackSetsLevel100Rate` (batch: `RoleRegistry.applyForTest` with
  `max_fe_per_tick` 2,500; a Lv100 Generator gives 2,500), `cdCfg13NoHiddenCap` (batch: 20,000; a Lv100
  legendary gives 40,000 to a battery with no receive limit), `cdCfg05DisabledGeneratorGetsNoClaims` (batch:
  `roles.disabled` holds the Generator; an Electric-type beside a battery 400 ticks: no claim),
  `cdLsh05PikachuOnBattery` (the row's literal "a target bound to Pikachu" case).
- [ ] **Step 3: Write the code, run, commit** — `git commit -m "Generate Forge Energy with Electric-types on an exponential level curve"`.

---

### Task 2: Crystal Tender

**Rows:** CD-CRY-01, CD-CRY-02, CD-CRY-03, CD-CRY-04, CD-CRY-05 (the guard runs in `check`), CD-BAL-07.

**Files:** `handler/vanilla/BuddingHandler.kt` (`scanKind = BLOCK`), `handler/vanilla/TickClock.kt`,
`roles/crystal_tender.json` (types `["rock"]`, params `{ "interval_ticks": 10 }`, effect Rock Throw),
`src/test/kotlin/.../handler/vanilla/TickClockTest.kt`, `src/gametest/kotlin/.../test/CrystalTenderTests.kt`,
`src/gametestCore/kotlin/.../coretest/CoreTests.kt` (CD-CRY-04).

`BuddingHandler` matches blocks in `TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:budding_blocks"))`
(CD-CRY-01), calls `state.randomTick(level, pos, level.random)` when `TickClock.every(10)` fires (CD-CRY-02),
and has demand while some face's neighbor is air, water, or in `c:buds` (a bud that can still grow)
(CD-CRY-03). No AE2 class is named.

- [ ] **Step 1: Failing tests** — `TickClockTest` (ticks 0, 10, 20, none at 9). GameTests: Rock-type claims
  budding amethyst and AE2 flawless budding certus (`ae2:flawless_budding_quartz` by id); six full amethyst
  clusters around a budding block give Standby within 20 ticks. Core run: without AE2, a Rock-type claims
  budding amethyst (CD-CRY-04). `BalanceFilesTest` CD-BAL-07 body.
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

- [ ] **Step 1: Failing tests** — `DefaultPriorityTest` (CD-ASG-16 with the default files: Turtonator
  `fire`+`dragon` on a burner gets the Superheater; Salazzle `poison`+`fire` on a stand gets the Brew Stoker);
  extend `BuiltInRoleFilesTest` to all eleven machine roles from the Role table (CD-FX-02). GameTests: one per
  BRF and BRW row, from the acceptance text (3 water bottles and nether wart with no powder completes; 1
  powder stays after 3 brews; removing the ingredient gives Standby within 20 ticks; fuel 0 worked then
  released reads 0; a worked brew takes 198 to 202 ticks; equal fuel after one brew each with and without a
  Brewer).
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

- [ ] **Step 1: Failing tests** — `CauldronFillTest` (50 mB per tick reaches level 3 at tick 20; the five
  demand cases). GameTests: bind to a Create fluid tank, a basin and a cauldron (CD-PMP-01); a Lv1 Pump adds
  5,000 ± 50 mB in 1,000 ticks and a Lv100 Pump 500 mB per tick (CD-PMP-03); a full tank gives Standby within
  20 ticks (CD-PMP-05); an unleashed Water-type beside an empty tank 400 ticks gets no claim (CD-ASG-05);
  the role file has `"auto_assign": false` (CD-PMP-02, in `BuiltInRoleFilesTest`).
- [ ] **Step 2: Write the code, run, commit** — `git commit -m "Pump water into tanks and cauldrons with leashed Water-types"`.

---

### Task 5: Muse

**Rows:** CD-MUS-01, CD-MUS-02, CD-MUS-03, CD-MUS-04, CD-MUS-05, CD-MUS-06, CD-HEAL-14.

**Files:** `mixin/vanilla/EnchantmentMenuMixin.java`, `mixin/vanilla/EnchantmentMenuAccess.java` (accessor for
`access`), `handler/vanilla/MuseBonus.kt`, `handler/vanilla/EnchantingHandler.kt`, `roles/muse.json` (types
`["fairy"]`, `auto_assign` false, effect Fairy Wind), `src/test/kotlin/.../handler/vanilla/MuseBonusTest.kt`,
`src/gametest/kotlin/.../test/MuseTests.kt`.

The mixin adds `MuseBonus.of(level, tablePos)` to the power before vanilla seeds its random source:

```java
@ModifyVariable(method = "lambda$slotsChanged$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;setSeed(J)V"))
private float cobblemon_dynamics$shelves(float power, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos table) {
    return MuseBonus.capped(power, MuseBonus.of(level, table));
}
```

`MuseBonus.forLevel(L) = ceil(15 × L ÷ 100)` (CD-MUS-03), `capped(real, bonus) = min(15, real + bonus)`
(CD-MUS-04). The handler has demand only while some player's open `EnchantmentMenu` belongs to that table
(CD-MUS-05; track menus with `PlayerContainerEvent.Open` and `Close`). A missing mixin disables the role
through `MixinGate` (CD-MUS-06).

- [ ] **Step 1: Failing tests** — `MuseBonusTest` (Lv1 1, Lv7 2, Lv50 8, Lv100 15; with a Lv50 Muse, 12, 7 and
  6 real shelves count 15, 15 and 14); `MixinGateTest` extended for the Muse. GameTests: bind a Clefairy to a
  table (CD-MUS-01); no menu 600 ticks then a menu open 600 ticks: stamina drains only while open (CD-MUS-05);
  a leashed Clefairy Muse and a Tired ally share a pasture for 1,200 ticks: no heal (CD-HEAL-14).
  `BuiltInRoleFilesTest` checks `"auto_assign": false` (CD-MUS-02).
- [ ] **Step 2: Write the code, run all runs, commit, push, open the pull request** —
  `./gradlew build runGameTestServer runGameTestServerCore`; `/commit`; `pr-runner` with base `main`.
