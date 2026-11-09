# M2: Work Stamina, beds and healers

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Read its Global Constraints first. Branch:
`m2-stamina-beds-healers`. Builds on M1 (`WorkEngine`, `WorkerMachine`, `ClaimRegistry`, `BrainBridge`,
`EffectPlayer`, `TestKit`).

**Milestone goal:** workers tire after 3 minutes of work per level, sleep on the nearest free bed of four
tiers or rest in place, wake at 100%, and unleashed Fairy-types heal Tired allies for 25% of their own Work
Stamina.

Before Task 1, check the Cobblemon references of this milestone in the jar: `PokemonEntity.POSE_TYPE`,
`enablePoseTypeRecalculation`, `PoseType.SLEEP`, Cobblemon's action-effect registry (the lookup for
`cobblemon:floralhealing`) and `Pokemon.persistentData`.

---

### Task 1: Work Stamina math

**Rows:** CD-STA-01, CD-STA-02, CD-STA-03, CD-STA-06, CD-STA-07, CD-STA-08, CD-STA-10, CD-HEAL-12,
CD-HEAL-13 (rule choice), CD-BED-03, CD-BED-04, CD-CFG-04, CD-BAL-02, CD-BAL-03.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/RecoveryRule.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/StaminaMath.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/Stamina.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/StaminaMathTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/BalanceTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class RecoveryRule { WORKER, HEALER;
    companion object { fun of(hasFairyType: Boolean, hasMachineBinding: Boolean) = if (hasFairyType && !hasMachineBinding) HEALER else WORKER } }

object StaminaMath {
    const val TICKS_PER_MINUTE = 1_200
    fun capacityTicks(level: Int, s: EngineSettings): Double = s.ticksPerLevel.toDouble() * level
    fun fullRecoveryTicks(level: Int, rule: RecoveryRule, s: EngineSettings): Double   // worker: capacityTicks; healer: formula below
    fun healerMinutes(level: Int, s: EngineSettings): Double =
        s.healerMinutesAtLevel1 - (s.healerMinutesAtLevel1 - s.healerMinutesAtLevel100) * (level - 1) / 99.0
    fun bedFactor(tier: Int?, s: EngineSettings): Int = if (tier == null) 1 else s.bedFactors[tier - 1]
}

/** Work Stamina of one Pokémon, as a fraction of capacity (CD-STA-03). */
class Stamina(var fraction: Double = 1.0, var tired: Boolean = false) {
    fun work(ticks: Long, level: Int, s: EngineSettings)                         // drains; sets tired at 0 (CD-STA-08)
    fun rest(ticks: Long, level: Int, rule: RecoveryRule, bedTier: Int?, s: EngineSettings)   // recovers; clears tired at 1.0 (CD-STA-10)
    val percent: Double get() = fraction * 100
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
class StaminaMathTest {
    private val s = EngineSettings.DEFAULT
    private fun near(expected: Double, actual: Double, tol: Double) = assertEquals(expected, actual, tol)

    @Test fun `CD-STA-01 capacity is 3,600 ticks per level`() {
        near(3_600.0, StaminaMath.capacityTicks(1, s), 0.0)
        near(180_000.0, StaminaMath.capacityTicks(50, s), 0.0)
        near(360_000.0, StaminaMath.capacityTicks(100, s), 0.0)
    }
    @Test fun `CD-STA-02 a Lv100 legendary has the same capacity`() = near(360_000.0, StaminaMath.capacityTicks(100, s), 0.0)
    @Test fun `CD-STA-03 a level-up keeps the percentage`() {
        val st = Stamina(0.40); st.rest(0, 31, RecoveryRule.WORKER, null, s); near(40.0, st.percent, 1e-9)
    }
    @Test fun `CD-STA-06 drain only while Working, recovery in Standby`() {
        val a = Stamina(1.0); a.work(1_800, 1, s); near(50.0, a.percent, 0.1)
        val b = Stamina(0.5); b.rest(600, 1, RecoveryRule.WORKER, null, s); near(66.7, b.percent, 0.1)
    }
    @Test fun `CD-STA-07 a Lv10 worker recovers half in 15 min and all in 30 min`() {
        val st = Stamina(0.0, tired = true); st.rest(18_000, 10, RecoveryRule.WORKER, null, s); near(50.0, st.percent, 0.1)
        st.rest(18_000, 10, RecoveryRule.WORKER, null, s); near(100.0, st.percent, 0.1)
    }
    @Test fun `CD-STA-08 the last tick of capacity makes the worker Tired`() {
        val st = Stamina(1.0 / 3_600); st.work(1, 1, s); assertTrue(st.tired); near(0.0, st.percent, 1e-9)
    }
    @Test fun `CD-STA-10 a Tired worker at 100% is no longer Tired`() {
        val st = Stamina(0.0, tired = true); st.rest(3_600, 1, RecoveryRule.WORKER, null, s); assertFalse(st.tired)
    }
    @Test fun `CD-HEAL-12 healer recovery is 1,200, 665_5 and 120 min`() {
        near(1_200.0, StaminaMath.healerMinutes(1, s), 0.0)
        near(665.5, StaminaMath.healerMinutes(50, s), 0.5)
        near(120.0, StaminaMath.healerMinutes(100, s), 0.0)
    }
    @Test fun `CD-HEAL-13 a leashed Fairy-type follows the worker rule, 150 min at Lv50`() {
        val rule = RecoveryRule.of(hasFairyType = true, hasMachineBinding = true)
        assertEquals(RecoveryRule.WORKER, rule)
        near(150.0, StaminaMath.fullRecoveryTicks(50, rule, s) / StaminaMath.TICKS_PER_MINUTE, 0.5)
    }
    @Test fun `CD-BED-03 bed tiers divide recovery by 2, 3, 4 and 8`() {
        val worker = StaminaMath.fullRecoveryTicks(100, RecoveryRule.WORKER, s) / StaminaMath.TICKS_PER_MINUTE
        val healer = StaminaMath.fullRecoveryTicks(100, RecoveryRule.HEALER, s) / StaminaMath.TICKS_PER_MINUTE
        listOf(150.0, 100.0, 75.0, 37.5).forEachIndexed { i, m -> near(m, worker / StaminaMath.bedFactor(i + 1, s), m * 0.01) }
        listOf(60.0, 40.0, 30.0, 15.0).forEachIndexed { i, m -> near(m, healer / StaminaMath.bedFactor(i + 1, s), m * 0.01) }
    }
    @Test fun `CD-BED-04 the factor applies only on the bed`() {
        val st = Stamina(0.0, tired = true)
        st.rest(9_000, 10, RecoveryRule.WORKER, bedTier = 1, s); st.rest(9_000, 10, RecoveryRule.WORKER, bedTier = null, s)
        near(75.0, st.percent, 0.1)
    }
    @Test fun `CD-CFG-04 7,200 ticks per level makes Lv1 Tired at 7,200 ticks, not before`() {
        val cfg = s.copy(ticksPerLevel = 7_200)
        val st = Stamina(1.0); st.work(7_199, 1, cfg); assertFalse(st.tired); st.work(2, 1, cfg); assertTrue(st.tired)
        assertEquals(listOf(2, 3, 4, 8), s.bedFactors); assertEquals(1_200, s.healerMinutesAtLevel1); assertEquals(120, s.healerMinutesAtLevel100)
    }
}

class BalanceTest {
    private val s = EngineSettings.DEFAULT
    /** Simulates work until Tired, then rest until 100%, and returns the fraction of ticks spent working. */
    private fun uptime(level: Int, bedTier: Int?, cycles: Int): Double {
        val st = Stamina(); var work = 0L; var total = 0L
        repeat(cycles) {
            while (!st.tired) { st.work(1, level, s); work++; total++ }
            while (st.tired) { st.rest(1, level, RecoveryRule.WORKER, bedTier, s); total++ }
        }
        return work.toDouble() / total
    }
    @Test fun `CD-BAL-03 a Lv50 worker on a Starlit Bed works 88_9 percent of the time`() {
        val u = uptime(50, bedTier = 4, cycles = 10); assertEquals(8.0 / 9.0, u, 0.001); assertTrue(u <= 0.890)
    }
    @Test fun `CD-BAL-02 sixteen Lv100 Generators with no beds average at most 40,000 FE per tick`() {
        val average = 16 * 5_000 * uptime(100, bedTier = null, cycles = 1)
        assertTrue(average <= 40_000.0 + 1e-6, "average $average")
    }
}
```

The BAL-02 simulation runs 720,000 ticks of a tight loop; it takes well under a second.

- [ ] **Step 2: Run them and see them fail** — `./gradlew test --tests "codes.honor.cobblemondynamics.stamina.*"`, unresolved references.
- [ ] **Step 3: Write the code** — `work` subtracts `ticks / capacityTicks`, clamps at 0, sets `tired` at 0;
  `rest` adds `ticks × bedFactor / fullRecoveryTicks`, clamps at 1, clears `tired` at 1. The healer full
  recovery is `healerMinutes(level) × TICKS_PER_MINUTE`.
- [ ] **Step 4: Run them and see them pass** — 13 tests.
- [ ] **Step 5: Commit** — `git commit -m "Add Work Stamina capacity, drain, recovery and bed factors"`.

---

### Task 2: Stamina store, offline recovery and display sync

**Rows:** CD-STA-04, CD-STA-05, CD-STA-11, CD-STA-12, CD-STA-15, CD-UI-05 (cadence rule).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/StaminaStore.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/StaminaView.kt` (entity attachment, synced)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/ViewSync.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsAttachments.kt` (`STAMINA_VIEW`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (drain, recover, flush)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/ViewSyncTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/OfflineRecoveryTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/StaminaStoreTests.kt`

**Interfaces:**
- Produces:

```kotlin
object StaminaStore {      // Pokemon.persistentData -> "cobblemon_dynamics" -> "stamina" {percent, tired, last_game_time}
    fun load(pokemon: Pokemon, now: Long, level: Int, s: EngineSettings): Stamina   // absent -> 100% (CD-STA-05); else offline recovery at base rate
    fun save(pokemon: Pokemon, stamina: Stamina, now: Long)                           // then pokemon.onChange()
}
object OfflineRecovery { fun apply(stamina: Stamina, elapsed: Long, level: Int, rule: RecoveryRule, s: EngineSettings) }  // never a bed factor
data class StaminaView(val percent: Int, val state: Int)    // 0-100 and WorkerStatus ordinal; attachment syncs to clients
object ViewSync {
    fun shouldSync(lastSyncedPercent: Double, nowPercent: Double, lastState: WorkerStatus, nowState: WorkerStatus): Boolean
}
object PanelCadence { fun shouldSend(openedAt: Long, now: Long): Boolean }   // 0, 20, 40 (CD-UI-05)
```

Time always comes from `server.overworld().gameTime`, which `/time` never changes (CD-STA-12). The engine
keeps each worker's `Stamina` in memory, drains it 1 tick per Working tick and recovers it on every other
tick, and flushes it to the PC's own `Pokemon` instance (`tethering.getPokemon()`, so Cobblemon's 20-tick
instance swap never loses it) on each state change, every `flushIntervalTicks`, on entity unload and on
server stop.

- [ ] **Step 1: Write the failing unit tests**

```kotlin
class ViewSyncTest {
    @Test fun `CD-STA-15 0_9 points sends nothing, 1_0 sends one, a state change sends one`() {
        assertFalse(ViewSync.shouldSync(50.0, 50.9, WorkerStatus.WORKING, WorkerStatus.WORKING))
        assertTrue(ViewSync.shouldSync(50.0, 51.0, WorkerStatus.WORKING, WorkerStatus.WORKING))
        assertTrue(ViewSync.shouldSync(50.0, 50.1, WorkerStatus.WORKING, WorkerStatus.STANDBY))
    }
}
class OfflineRecoveryTest {
    @Test fun `CD-STA-11 a Lv10 worker at 0 percent reads 50 percent after 18,000 ticks away`() {
        val st = Stamina(0.0, tired = true)
        OfflineRecovery.apply(st, elapsed = 18_000, level = 10, rule = RecoveryRule.WORKER, s = EngineSettings.DEFAULT)
        assertEquals(50.0, st.percent, 0.1)
    }
}
class PanelCadenceTest {
    @Test fun `CD-UI-05 sends at 0, 20 and 40, never at 19`() =
        assertEquals(listOf(0L, 20L, 40L), (0L..45L).filter { PanelCadence.shouldSend(0, it) })
}
```

- [ ] **Step 2: Write the failing GameTests**

```kotlin
// cdSta04SurvivesPcPartyRestartAndSwap: a worker at 37%; release to the PC and tether again; simulateRestart; replace
//   entity.pokemon with the PC instance (entity.pokemon = tethering.getPokemon()); the value is within the recovery
//   that the elapsed ticks allow.
// cdSta05NewWorkerStartsFull: a Pokémon with no Dynamics data joins a Work Mode pasture; it reads 100%.
// cdSta11PcTimeRecoversAtBaseRate: a Lv10 worker at 0% (StaminaStore.save with 0) is released to the PC at game time T;
//   advance by setting last_game_time to T - 18,000 in persistentData before it is tethered again; it reads 50% ± 0.1%.
// cdSta12TimeCommandsChangeNothing: a worker in the PC; run "time set day" and "time add 24000"; StaminaStore.load gives the
//   same value as before the commands.
```

- [ ] **Step 3: Write the code, run everything, see it pass, commit**

Run: `./gradlew build runGameTestServer`. Expected: PASS.
`git commit -m "Persist Work Stamina with the Pokémon and sync a display value to clients"`

---

### Task 3: Bed blocks, recipes and placeholder art

**Rows:** CD-BED-01, CD-BED-02, CD-ART-11 (placeholders are allowed).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedTier.kt` (`HAY(1)`, `WOOL(2)`, `PLUSH(3)`, `STARLIT(4)`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedBlock.kt` (low shape, 0 to 6/16 high)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedBlockEntity.kt` (holds the tier; exists so scans find beds in the block entity map)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsBlocks.kt` (`hay_bed`, `wool_bed`, `plush_bed`, `starlit_bed` and their items)
- Create: `src/main/resources/data/cobblemon_dynamics/recipe/{hay,wool,plush,starlit}_bed.json`
- Create: `src/main/resources/data/cobblemon_dynamics/loot_table/blocks/*_bed.json` (drop itself)
- Create: `src/main/resources/assets/cobblemon_dynamics/{blockstates,models/block,models/item}/*_bed.json`
- Create: placeholder textures `assets/cobblemon_dynamics/textures/block/*_bed_{top,side}.png` with `scripts/placeholder_art.py`
- Modify: `en_us.json` (`block.cobblemon_dynamics.hay_bed` = `Hay Bed`, and the other three)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/bed/BedTierTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/BedRecipeTests.kt`

Recipes, from the spec's Recipes table:

```json
// hay_bed.json
{ "type": "minecraft:crafting_shaped", "category": "misc",
  "pattern": ["HHH", "PPP"],
  "key": { "H": { "item": "minecraft:hay_block" }, "P": { "tag": "minecraft:planks" } },
  "result": { "id": "cobblemon_dynamics:hay_bed", "count": 1 } }
// wool_bed.json
{ "type": "minecraft:crafting_shaped", "category": "misc",
  "pattern": ["IWI", "WBW", "IWI"],
  "key": { "I": { "item": "minecraft:iron_ingot" }, "W": { "tag": "minecraft:wool" }, "B": { "item": "cobblemon_dynamics:hay_bed" } },
  "result": { "id": "cobblemon_dynamics:wool_bed", "count": 1 } }
// plush_bed.json
{ "type": "minecraft:crafting_shaped", "category": "misc",
  "pattern": ["DGD", "GBG", "DGD"],
  "key": { "D": { "item": "minecraft:diamond" }, "G": { "item": "minecraft:gold_ingot" }, "B": { "item": "cobblemon_dynamics:wool_bed" } },
  "result": { "id": "cobblemon_dynamics:plush_bed", "count": 1 } }
// starlit_bed.json
{ "type": "minecraft:crafting_shapeless", "category": "misc",
  "ingredients": [ { "item": "cobblemon_dynamics:plush_bed" }, { "item": "minecraft:nether_star" },
    { "item": "cobblemon:rare_candy" }, { "item": "cobblemon:rare_candy" }, { "item": "cobblemon:rare_candy" }, { "item": "cobblemon:rare_candy" } ],
  "result": { "id": "cobblemon_dynamics:starlit_bed", "count": 1 } }
```

- [ ] **Step 1: Failing tests** — `BedTierTest` (`CD-BED-01`: four tiers 1 to 4 with ids `hay_bed`,
  `wool_bed`, `plush_bed`, `starlit_bed`). `BedRecipeTests` (`CD-BED-02`): for each bed, build the exact
  `CraftingInput` and assert the result; for each upgrade, the same grid with the lower bed replaced by
  air (shaped) or removed (shapeless) gives no recipe. Also `cdBed01RegistryListsFourBeds`: the block
  registry holds the four ids and each block entity reports its tier.
- [ ] **Step 2: Run, write the content, run again, commit** —
  `git commit -m "Add the four bed tiers with recipes and placeholder art"`.

---

### Task 4: Tired, beds and rest in the engine

**Rows:** CD-STA-09, CD-STA-13, CD-STA-14, CD-LSH-12, CD-BED-05, CD-BED-06, CD-BED-07, CD-BED-08,
CD-BED-09, CD-BED-10, CD-BED-11, CD-BED-12, CD-BED-13, CD-BED-14, CD-AI-07, CD-AI-08, CD-FX-10,
CD-CLM-09 (GameTest), CD-CLM-06 (sleeping bound worker), CD-WMD-05 (full scene), CD-ASG-18 (full day).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedSelector.kt`
- Modify: `engine/WorkerMachine.kt` (Tired transitions), `engine/WorkEngine.kt`, `ai/WorkTaskConfig.kt`
  (sleep hold and pose), `fx/EffectPlayer.kt` (sleep loop)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/bed/BedSelectorTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/RestMachineTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/RestTests.kt`

**Interfaces:**
- Produces:

```kotlin
data class BedCandidate(val key: TargetKey, val center: Vec3, val tier: Int)
object BedSelector {
    /** Nearest free bed inside the area; on a distance tie the higher tier; then the lowest position. */
    fun pick(from: Vec3, beds: List<BedCandidate>, isFree: (TargetKey) -> Boolean): BedCandidate?
}
// WorkerMachine additions
fun onTired(w: Worker, now: Long, bed: BedCandidate?): MachineEvent    // TO_BED with a bed claim, or RESTING_IN_PLACE
fun onRested(w: Worker, now: Long): MachineEvent                         // leashed -> TRAVELING to the binding, auto -> IDLE
```

Rules: a worker that reaches 0% while Working becomes Tired. An auto worker releases its work claim in the
same tick (CD-STA-13). A leashed worker keeps its binding (CD-CLM-09). Then the worker claims a bed with
`BedSelector.pick` over the beds inside its pasture's work area (CD-BED-05, CD-BED-06, CD-BED-07), or rests
in place at the base rate (CD-BED-08). `TO_BED` becomes `SLEEPING` when the worker is within 0.5 blocks of
the bed center; a bed not reached 600 ticks after the claim gives `RESTING_IN_PLACE`. Sleeping and resting
set the Brain work order to `sleep = true`: the work task snaps the entity to the bed center every tick
(CD-BED-10), sets `enablePoseTypeRecalculation = false` and `POSE_TYPE = PoseType.SLEEP`, and never sets the
battle SLEEP status (CD-AI-07). Waking restores `enablePoseTypeRecalculation = true` (CD-AI-08). Sleep
effects: `cobblemon:sleep_actor` and `cobblemon:sleep_actorresidual` every 40 ticks with no sound
(CD-FX-10). A broken bed moves its sleeper to `RESTING_IN_PLACE` in the same tick (CD-BED-11). At 100%,
the bed claim is released in the same tick (CD-BED-12). Only Tired workers of Work Mode pastures reach a bed
(CD-BED-13). After a restart, a Tired worker claims a bed again on its first poll, within 40 ticks
(CD-BED-14). Bed size never filters a Pokémon (CD-BED-09).

- [ ] **Step 1: Failing unit tests** — `BedSelectorTest`: beds at 3 and 5 blocks gives the 3-block bed
  (CD-BED-05); Hay and Plush beds both at 4.0 gives the Plush Bed (CD-BED-06); a claimed bed is skipped.
  `RestMachineTest`: the five rules above on a fake clock, plus CD-STA-09 (a Tired worker at 99.9% is
  refused a furnace, at 100% it gets it) and the extended CD-ASG-18 day: a worker and a healer through
  24,000 ticks with Tired, bed, sleep and heal phases, exactly one state per tick.
- [ ] **Step 2: Failing GameTests** (`RestTests`), each from its row's acceptance text:

```kotlin
// cdSta13TiredAutoWorkerFreesItsMachine: a Lv1 Charmander set to 1 tick of stamina; 1 tick after Tired its claim is gone,
//   and a second Charmander claims the furnace at the next scan.
// cdSta14LeashedWorkerReturnsAfterRest: a leashed worker at 99.99% Sleeping; at 100% it is TRAVELING within 20 ticks.
// cdLsh12TiredBindingWaits: bind a Tired Pokémon to a furnace; it does not work before 100% and keeps the binding.
// cdBed05NearestBed / cdBed07OnlyInsideArea (beds at offsets 8 and 9) / cdBed08RestInPlaceTimesRecovery: a Lv10 worker at 0%
//   with no bed stays in place, shows PoseType.SLEEP, and reaches 100% at 36,000 ± 20 ticks (use ticksPerLevel = 20 in a
//   config-override batch so the test takes 200 ticks; the row's ratio is what the test checks).
// cdBed09WailordUsesAHayBed: a Tired Wailord claims and sleeps on a Hay Bed.
// cdBed10SleeperStaysOnTheBed: 1,200 ticks within 0.25 blocks of the bed center.
// cdBed11BrokenBedMeansRestInPlace / cdBed12FullSleeperFreesItsBed (recovery, and a heal in Task 5) /
// cdBed13OnlyTiredWorkersSleep (a worker at 50% and a wild Pokémon beside a free bed for 1,200 ticks) /
// cdBed14SleeperClaimsABedAfterRestart (simulateRestart, then on a free bed within 40 ticks).
// cdAi07NoBattleSleepStatus: a worker sleeps and wakes; pokemon.status never reads Cobblemon's sleep status.
// cdAi08PoseRecalculationRestored: after waking, and after the entity is saved and loaded, enablePoseTypeRecalculation is true.
// cdClm09TiredLeashedWorkerGetsABed / cdClm06SleepingBoundWorkerKeepsItsTarget (an Idle Charmander 3 blocks from the
//   bound target for 400 ticks never claims it) / cdWmd05FullScene (two Working, one sleeper, one healer; Work Mode off;
//   1 tick later no claim).
```

  Tests that wait for real recovery use a config batch with `ticksPerLevel = 20` and healer minutes of 1,
  so a full cycle takes a few hundred ticks; each asserts the row's ratio, not its wall time.
- [ ] **Step 3: Write the code, run everything, commit** —
  `git commit -m "Send Tired workers to the nearest free bed and wake them at 100%"`.

---

### Task 5: Healers

**Rows:** CD-HEAL-01, CD-HEAL-02, CD-HEAL-03, CD-HEAL-04, CD-HEAL-05, CD-HEAL-06, CD-HEAL-07, CD-HEAL-08,
CD-HEAL-09 (GameTest), CD-HEAL-10, CD-HEAL-11, CD-HEAL-15, CD-HEAL-16, CD-HEAL-17. CD-HEAL-14 needs a
Fairy machine role, so its GameTest is in M4 Task 5 (the Muse).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/heal/HealTargets.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/heal/HealTransaction.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/HealEffect.kt`
- Modify: `engine/WorkEngine.kt`, `engine/WorkerMachine.kt` (HEALING)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/heal/HealTargetsTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/heal/HealTransactionTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/HealEffectTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/HealerTests.kt`

**Interfaces:**
- Produces:

```kotlin
data class AllyView(val id: UUID, val pos: Vec3, val hasFairyType: Boolean, val tired: Boolean, val fraction: Double)
object HealTargets {
    fun canStart(healerFraction: Double, healerTired: Boolean, s: EngineSettings): Boolean   // >= 0.25 and not Tired
    fun pick(healerId: UUID, healerPos: Vec3, allies: List<AllyView>, isFree: (UUID) -> Boolean, s: EngineSettings): AllyView?
}
object HealTransaction {
    const val RANGE = 2.0
    fun inRange(healerPos: Vec3, allyPos: Vec3): Boolean          // <= 2.0
    fun complete(healer: Stamina, ally: Stamina, s: EngineSettings) // healer -25 points (Tired at 0), ally 100%
}
interface ActionEffectLookup { fun find(id: ResourceLocation): Any? }
object HealEffect { fun choose(lookup: ActionEffectLookup): Choice }   // FloralHealing(effect) or Fallback
```

`pick` drops the healer itself and Fairy-type allies (CD-HEAL-06), takes the nearest Tired ally
(CD-HEAL-07), else the ally with the lowest fraction under `lowStaminaThreshold` (CD-HEAL-08), and skips an
ally that another healer claimed (CD-HEAL-09). The heal claim is a `ClaimKind.HEAL` claim on
`TargetKey.Entity(ally)`. The work order of a healer is an approach order toward the ally. A heal completes
only within 2.0 blocks (CD-HEAL-10). A target that stops qualifying (100%, left the pasture, Fairy form)
cancels the heal at no cost (CD-HEAL-11). A heal that drops the healer to 0% sends it to `TO_BED` or
`RESTING_IN_PLACE` (CD-HEAL-17).

Effects: if Cobblemon's action-effect registry has `cobblemon:floralhealing`, run it with a context whose
user is the healer and whose target is the ally (CD-HEAL-16); otherwise play the healer's `special`
animation (then `cry`) and spawn `cobblemon:heal_circles` and `cobblemon:heal_sparkles` on the ally
(CD-HEAL-15).

- [ ] **Step 1: Failing unit tests**

```kotlin
class HealTargetsTest {
    private val s = EngineSettings.DEFAULT
    private val healer = UUID.randomUUID()
    private fun ally(x: Double, tired: Boolean = false, f: Double = 1.0, fairy: Boolean = false, id: UUID = UUID.randomUUID()) =
        AllyView(id, Vec3(x, 0.0, 0.0), fairy, tired, f)

    @Test fun `CD-HEAL-02 25_0 percent starts a heal and 24_9 does not`() {
        assertTrue(HealTargets.canStart(0.250, false, s)); assertFalse(HealTargets.canStart(0.249, false, s))
    }
    @Test fun `CD-HEAL-03 a Tired healer at 60 percent does not heal`() = assertFalse(HealTargets.canStart(0.60, true, s))
    @Test fun `CD-HEAL-06 the filter drops the healer and Fairy allies`() {
        val self = ally(0.0, tired = true, id = healer)
        assertNull(HealTargets.pick(healer, Vec3.ZERO, listOf(self, ally(1.0, tired = true, fairy = true)), { true }, s))
    }
    @Test fun `CD-HEAL-07 the nearest Tired ally comes first`() {
        val t3 = ally(3.0, tired = true); val t6 = ally(6.0, tired = true); val low = ally(1.0, f = 0.10)
        assertEquals(t3.id, HealTargets.pick(healer, Vec3.ZERO, listOf(t6, low, t3), { true }, s)!!.id)
    }
    @Test fun `CD-HEAL-08 the lowest ally under 25 percent, and none at 25_0`() {
        val a = ally(1.0, f = 0.249); val b = ally(2.0, f = 0.10)
        assertEquals(b.id, HealTargets.pick(healer, Vec3.ZERO, listOf(a, b), { true }, s)!!.id)
        assertNull(HealTargets.pick(healer, Vec3.ZERO, listOf(ally(1.0, f = 0.25)), { true }, s))
    }
}
class HealTransactionTest {
    private val s = EngineSettings.DEFAULT
    @Test fun `CD-HEAL-04 four heals read 75, 50, 25 and 0, then Tired`() {
        val healer = Stamina(1.0)
        listOf(75.0, 50.0, 25.0, 0.0).forEach { HealTransaction.complete(healer, Stamina(0.0, true), s); assertEquals(it, healer.percent, 1e-9) }
        assertTrue(healer.tired)
    }
    @Test fun `CD-HEAL-05 a Lv1 healer fills a Lv100 ally to 100 percent`() {
        val ally = Stamina(0.0, true); HealTransaction.complete(Stamina(1.0), ally, s)
        assertEquals(100.0, ally.percent, 1e-9); assertFalse(ally.tired)
    }
    @Test fun `CD-HEAL-10 2_0 blocks completes and 2_1 does not`() {
        assertTrue(HealTransaction.inRange(Vec3.ZERO, Vec3(2.0, 0.0, 0.0))); assertFalse(HealTransaction.inRange(Vec3.ZERO, Vec3(2.1, 0.0, 0.0)))
    }
}
class HealEffectTest {
    @Test fun `CD-HEAL-16 Floral Healing runs when the registry has it, the fallback otherwise`() {
        val id = ResourceLocation.parse("cobblemon:floralhealing"); val effect = Any()
        assertEquals(HealEffect.Choice.FloralHealing(effect), HealEffect.choose { if (it == id) effect else null })
        assertEquals(HealEffect.Choice.Fallback, HealEffect.choose { null })
    }
}
```

`HealEffect.Choice.FloralHealing` carries the effect, and the GameTest below checks that the context's
user is the healer and its target the ally.

- [ ] **Step 2: Failing GameTests** — `HealerTests`: CD-HEAL-01 (an unleashed Clefairy heals a Tired Machop
  within 600 ticks), CD-HEAL-06 (a Tired Azumarill in the same pasture and a Tired Machop in a second
  pasture 3 blocks away get no heal), CD-HEAL-07 (allies at 3 and 6 blocks Tired and one at 10% at 1 block:
  the 3-block ally), CD-HEAL-09 (two healers, one Tired ally: one heal, one cost), CD-HEAL-11 (the ally
  leaves the pasture while the healer walks: no cost, no heal), CD-HEAL-15 (Cobblemon 1.8.4 has no Floral
  Healing timeline, so the fallback runs: record the packets that `EffectPlayer` sends through a test
  sink and assert the `special` animation and both heal particles), CD-HEAL-16 (a stub registry with the
  id: the recorded context has user = healer entity and target = ally entity), CD-HEAL-17 (a healer at 25%
  heals once with one free bed and goes `TO_BED`; another with no bed goes `RESTING_IN_PLACE`, each 1 tick
  after its heal).
- [ ] **Step 3: Write the code, run everything, commit** —
  `git commit -m "Let unleashed Fairy-types heal Tired allies for 25% of their Work Stamina"`.

---

### Task 6: Stamina command and the bed and heal claims in the claims command

**Rows:** CD-UI-15, CD-UI-14 (bed and heal claims), CD-SEC-09 (stamina command).

**Files:**
- Modify: `command/DynamicsCommands.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/CommandTests.kt`

`/cobblemondynamics stamina <target>` takes an entity selector of one Pokémon and prints
`<name>: <percent>% Work Stamina, <state>, <worker rule|healer rule>` from lang keys.

- [ ] **Step 1: Failing GameTests** — `cdUi15StaminaShowsValueStateAndRule` (a Lv50 healer at 40%: the output
  holds `40`, its state, and `healer rule`); extend `cdUi14ClaimsListsEveryEntry` with a sleeper's bed claim
  and a healer's heal claim; extend `cdSec09LevelZeroIsRefused` to the stamina command.
- [ ] **Step 2: Write the code, run, commit, push, open the pull request** — `/commit`, then `pr-runner`
  with base `main`. The body lists the rows of Tasks 1 to 6.
