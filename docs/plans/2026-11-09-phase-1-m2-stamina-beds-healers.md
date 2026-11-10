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

The test files also hold one-tick-step versions of CD-STA-06/08 (a Lv1 worker is Tired on tick 3,600, not
3,599), CD-STA-07/10 (a Lv10 worker stays Tired until tick 36,000) and CD-CFG-04 (tick 7,200), a Starlit Bed
boundary for CD-BED-03 (37.5 min, not 1 tick less), the HEALER and WORKER cases of `RecoveryRule.of`,
configured healer times and bed factors, healer levels above 100, and CD-BAL-03 for every tier at Lv1 and
Lv10.

- [ ] **Step 2: Run them and see them fail** — `./gradlew test --tests "codes.honor.cobblemondynamics.stamina.*"`, unresolved references.
- [ ] **Step 3: Write the code** — `work` subtracts `ticks / capacityTicks`, clamps at 0, sets `tired` at 0;
  `rest` adds `ticks × bedFactor / fullRecoveryTicks`, clamps at 1, clears `tired` at 1. The healer full
  recovery is `healerMinutes(level) × TICKS_PER_MINUTE`. Both ends snap within 1e-9, because one-tick steps
  add rounding error and the last tick of a drain can leave 1e-13 instead of 0. `healerMinutes` clamps the
  level to 1..100, because Cobblemon's level cap is a config value and the line reaches 0 at level 111.
- [ ] **Step 4: Run them and see them pass** — 22 tests.
- [ ] **Step 5: Commit** — `git commit -m "Add Work Stamina capacity, drain, recovery and bed factors"`.

---

### Task 2: Stamina store, offline recovery and display sync

**Rows:** CD-STA-04, CD-STA-05, CD-STA-11, CD-STA-12, CD-STA-15, CD-UI-05 (cadence rule).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/StaminaStore.kt` (also `OfflineRecovery`, `FlushRule`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/StaminaView.kt` (entity attachment, synced)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/stamina/ViewSync.kt` (also `PanelCadence`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/StreamCodecs.kt` (the `WorkerStatus` ordinal codec that
  `PanelEntry` and `StaminaView` share)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsAttachments.kt` (`STAMINA_VIEW`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (load, drain, recover, flush, view),
  `engine/Worker.kt` (`stamina`, `staminaUntil`; `tired` reads the stamina's flag), `engine/PastureRuntime.kt` (the slot's
  rule, flush and view state), `engine/EngineEvents.kt` (`ServerStoppingEvent` flush)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/net/PanelViewers.kt` (the panel entries carry the real percent)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/ViewSyncTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/OfflineRecoveryTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/PanelCadenceTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/stamina/StaminaStoreTest.kt` (the store on a plain compound)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/StaminaStoreTests.kt`

**Interfaces:**
- Produces:

```kotlin
object StaminaStore {      // Pokemon.persistentData -> "cobblemon_dynamics" -> "stamina" {percent, tired, last_game_time}
    fun load(pokemon: Pokemon, now: Long, s: EngineSettings): Stamina    // absent -> 100% (CD-STA-05); else offline recovery
    fun save(pokemon: Pokemon, stamina: Stamina, savedAt: Long)          // then pokemon.onChange()
    fun ruleOf(pokemon: Pokemon): RecoveryRule                           // the Pokémon's types and binding
    data class Saved(val fraction: Double, val tired: Boolean, val lastGameTime: Long)
    fun read(data: CompoundTag): Saved?                                  // the pure layer, unit-tested on a plain compound
    fun write(data: CompoundTag, stamina: Stamina, savedAt: Long)
    fun restore(data: CompoundTag, now: Long, level: Int, rule: RecoveryRule, s: EngineSettings): Stamina
}
object OfflineRecovery { fun apply(stamina: Stamina, elapsed: Long, level: Int, rule: RecoveryRule, s: EngineSettings) }  // never a bed factor
object FlushRule { fun isDue(flushedStatus: WorkerStatus?, status: WorkerStatus, flushedAt: Long, now: Long, s: EngineSettings): Boolean }
data class StaminaView(val percent: Int, val state: WorkerStatus)   // 0-100 rounded down; the codec sends the state's ordinal
object ViewSync {
    fun shouldSync(lastSyncedPercent: Double, nowPercent: Double, lastState: WorkerStatus, nowState: WorkerStatus): Boolean
}
object PanelCadence { fun shouldSend(openedAt: Long, now: Long): Boolean }   // 0, 20, 40 (CD-UI-05)
```

`load` reads the level and the rule from the Pokémon, so it takes no level. A damaged entry counts as none.
`last_game_time` is the first tick that the saved value does not include yet: the engine keeps
`Worker.staminaUntil`, applies each tick once (`now + 1 - staminaUntil` ticks per engine tick), and saves
`staminaUntil`, so a flush at any point of a tick, followed by a load, neither loses nor repeats a tick.

Time always comes from game time, which `/time` never changes (CD-STA-12): `TimeCommand` calls only
`ServerLevel.setDayTime` (checked with `javap`), and `level.gameTime` is the overworld's in every dimension
(`DerivedLevelData.getGameTime` delegates). The engine keeps each worker's `Stamina` in memory, drains it 1
tick per Working tick and recovers it on every other tick, and flushes it to the PC's own `Pokemon` instance
(`tethering.getPokemon()`, which each poll reads again, so Cobblemon's 20-tick instance swap never loses it)
on each state change, every `flushIntervalTicks`, when the worker leaves (which covers entity unload) and on
`ServerStoppingEvent`, before Cobblemon saves its stores on `ServerStoppedEvent`. The GameTest restart
(`resetRuntimeForTest`) flushes first, as a real stop does. A Pokémon that stops working loses its
`stamina_view`, so its head bar goes. The panel entries carry the live percent of a worker, and the saved
percent with offline recovery of any other tethered Pokémon; the 20-tick panel send itself is M5 Task 2.

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

The test files also hold: a drain also syncs, a point read back through rounding still syncs, the view rounds
down and its codec keeps every state (CD-STA-15); a healer's time away and a negative time away
(CD-STA-11); and `StaminaStoreTest` on a plain `persistentData` compound written and read as binary NBT: the
value, the flag and the time survive a save, the entry sits beside the binding (CD-STA-04), no entry or a
damaged one reads 100% (CD-STA-05), 18,000 ticks later reads 50% (CD-STA-11), only game time moves the value
(CD-STA-12), and the ADR-0008 flush cadence.

- [ ] **Step 2: Write the failing GameTests**

```kotlin
// cdSta04And05StaminaStartsFullAndFollowsThePokemon (own batch, because it resets the engine): a new Lv10 Charmander with no
//   Dynamics data joins a Work Mode pasture and reads 100% with the view (100, IDLE) (CD-STA-05); it is set to 37%, released
//   to the PC, moved to the party and back, and tethered again; simulateRestart; the PC's instance is replaced by an NBT
//   round trip of itself, as a restart's reload builds new instances; entity.pokemon = the new instance, which is Cobblemon's
//   20-tick swap (it runs only for an owner in the level's player list, which a fake player never is). At each step the value
//   is at least 37% and at most 37% plus the base-rate recovery of the ticks since it was set (CD-STA-04). On its last tick
//   the test fails with the step it waits at.
```

`PCStore.remove` recalls the Pokémon, and a pastured Pokémon is "sent out": Cobblemon's `PokemonServerDelegate.tick`
gives every `PokemonEntity`'s Pokémon a `SentOutState(entity)`, whose `recall()` discards the entity. The test therefore
sets the old instance to `InactivePokemonState` before it removes it from the PC; a real reload drops the old store and
removes nothing.

The other two planned GameTests:

- CD-STA-11 (`cdSta11PcTimeRecoversAtBaseRate`: a Lv10 worker at 0% is released to the PC at game time T, its
  `last_game_time` is set to T - 18,000 before it is tethered again, and it reads 50% ± 0.1%): GameTest
  deferred (high confidence): verified in the end-of-phase pass. The join path through the PC is the one that
  cdSta04And05 runs, and `OfflineRecoveryTest` and `StaminaStoreTest` check the value.
- CD-STA-12 (`cdSta12TimeCommandsChangeNothing`: a worker in the PC; `time set day` and `time add 24000`;
  `StaminaStore.load` gives the same value as before): GameTest deferred (high confidence): verified in the
  end-of-phase pass. `TimeCommand` calls only `setDayTime`, and `StaminaStoreTest` shows that only game time
  moves the value.

- [ ] **Step 3: Write the code, run everything, see it pass, commit**

Run: `./gradlew build`, then `./gradlew runGameTestServer` at the end of the part (Tasks 1 to 3), twice.
Expected: PASS.
`git commit -m "Persist Work Stamina with the Pokémon and sync a display value to clients"`

---

### Task 3: Bed blocks, recipes and placeholder art

**Rows:** CD-BED-01, CD-BED-02, CD-ART-11 (placeholders are allowed).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedTier.kt` (`HAY(1)`, `WOOL(2)`, `PLUSH(3)`, `STARLIT(4)`;
  `tier`, `id`, `of(tier)`, and a `StringRepresentable` codec for the block codec)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedBlock.kt` (low shape, 0 to 6/16 high, `HEIGHT = 6/16`; a
  `BaseEntityBlock` that overrides `getRenderShape` to `MODEL`, because the base class renders nothing)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedBlockEntity.kt` (holds the tier; exists so scans find beds in the block entity map)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsBlocks.kt` (`hay_bed`, `wool_bed`, `plush_bed`, `starlit_bed`, their
  items in the Functional Blocks tab, and the block entity type `cobblemon_dynamics:bed`)
- Modify: `CobblemonDynamics.kt` (`DynamicsBlocks.register`)
- Create: `src/main/resources/data/cobblemon_dynamics/recipe/{hay,wool,plush,starlit}_bed.json`
- Create: `src/main/resources/data/cobblemon_dynamics/loot_table/blocks/*_bed.json` (drop itself)
- Create: `src/main/resources/assets/cobblemon_dynamics/{blockstates,models/block,models/item}/*_bed.json`, and
  `models/block/template_bed.json`, the one 16 × 6 × 16 element with `#top` and `#side` that each bed's block model takes
  as its parent
- Create: placeholder textures `assets/cobblemon_dynamics/textures/block/*_bed_{top,side}.png` with `scripts/placeholder_art.py`
- Modify: `en_us.json` (`block.cobblemon_dynamics.hay_bed` = `Hay Bed`, and the other three)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/bed/BedTierTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/bed/BedContentFilesTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/BedRecipeTests.kt` (deferred, see Step 1)

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
  `wool_bed`, `plush_bed`, `starlit_bed`, their factors 2, 3, 4 and 8, and the tier codec).
  `BedContentFilesTest` reads the files as the Recipes table states them, the way `WorkLeashRecipeTest` reads
  the leash recipe (`CD-BED-02`: the Hay Bed pattern and inputs; each shaped upgrade has the lower bed as its
  one center input and exactly three keys, so a grid with air in the center matches nothing; the Starlit Bed
  ingredients), and checks the loot tables (each bed drops itself), the lang names, the blockstate, block and
  item models (the template is 6/16 high, like `BedBlock.HEIGHT`) and the eight 16 × 16 PNGs (CD-ART-11).
  - CD-BED-02 (`BedRecipeTests`: for each bed, build the exact `CraftingInput` and assert the result; for each
    upgrade, the same grid with the lower bed replaced by air (shaped) or removed (shapeless) gives no recipe):
    GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-BED-01 (`cdBed01RegistryListsFourBeds`: the block registry holds the four ids and each block entity
    reports its tier): GameTest deferred (high confidence): verified in the end-of-phase pass.
- [ ] **Step 2: Run, write the content, run again, commit** —
  `git commit -m "Add the four bed tiers with recipes and placeholder art"`.

---

### Task 4: Tired, beds and rest in the engine

**Rows:** CD-STA-09, CD-STA-13, CD-STA-14, CD-LSH-12, CD-BED-05, CD-BED-06, CD-BED-07, CD-BED-08,
CD-BED-09, CD-BED-10, CD-BED-11, CD-BED-12, CD-BED-13, CD-BED-14, CD-AI-07, CD-AI-08, CD-FX-10,
CD-CLM-09 (GameTest), CD-CLM-06 (sleeping bound worker), CD-WMD-05 (full scene), CD-ASG-18 (full day).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/bed/BedSelector.kt` (also `BedCandidate`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/SleepEffect.kt`
- Modify: `engine/WorkerStatus.kt` (`isResting`), `engine/Worker.kt` (`bed`, `restSpot`), `engine/WorkerMachine.kt` (Tired
  transitions), `engine/WorkEngine.kt`, `engine/EngineEvents.kt` (the pose restore on `EntityJoinLevelEvent`),
  `ai/BrainBridge.kt` (pose, wake, the end of Cobblemon's night sleep), `ai/WorkOrder.kt`, `ai/WorkTaskConfig.kt` (sleep
  hold and pose), `ai/SwitchFromWorkTaskConfig.kt`, `fx/EffectPlayer.kt` (sleep loop)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/bed/BedSelectorTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/RestMachineTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/SleepEffectTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/RestTests.kt`, `TestKit.relative`

**Interfaces:**
- Produces:

```kotlin
data class BedCandidate(val key: TargetKey, val center: Vec3, val tier: Int) {
    companion object { fun sleepPoint(pos: BlockPos): Vec3 }               // the bed's top, at the center of its block
}
object BedSelector {
    /** Nearest free bed; on a distance tie the higher tier; then the lowest position. The caller passes the work area's beds. */
    fun pick(from: Vec3, beds: List<BedCandidate>, isFree: (TargetKey) -> Boolean): BedCandidate?
    fun isAt(pos: Vec3, width: Double, center: Vec3): Boolean              // within max(0.5, width / 2) across, 1.5 up or down
}
// WorkerMachine additions, with MachineEvent.ToBed(bed), RestInPlace, FellAsleep, LostBed and Rested(toBinding)
fun onTired(w: Worker, now: Long, bed: BedCandidate?): MachineEvent      // TO_BED on the bed that the engine claimed, or RESTING_IN_PLACE
fun onRestTick(w: Worker, now: Long, atBed: Boolean, bedPresent: Boolean): MachineEvent?   // SLEEPING, or RESTING_IN_PLACE when the bed is lost
fun onRested(w: Worker, now: Long): MachineEvent                         // IDLE; the engine then claims a leashed worker's bound target
object SleepEffect { const val LOOP_TICKS = 40; val PARTICLES: List<ResourceLocation>; val LOCATORS: List<String> }
```

Rules, as built: the engine drains Work Stamina in its stamina step, after the worker's tick. A worker that
the step makes Tired goes to rest in the same step: the release path ends its work claim, with the handler's
`onStop` (CD-STA-13), and a leash binding stays (CD-CLM-09). The engine then reads the beds of the pasture's
work area from the chunks' block entity maps, picks one with `BedSelector.pick` and claims it (CD-BED-05,
CD-BED-06, CD-BED-07), or the worker rests in place at the base rate where it stands (CD-BED-08). The bed
lookup happens at that moment, not in the scan pass, so a Tired worker that joins after a restart also takes
a bed on its first tick (CD-BED-14). `TO_BED` becomes `SLEEPING` when `BedSelector.isAt` is true: within
0.5 blocks of the sleep point, or within half the Pokémon's width when it is wider. Navigation stops a wide
Pokémon that far from its last path node, as it does at a work spot, so this is what lets any size of
Pokémon use a bed (CD-BED-09); the engine then lays it on the sleep point. A bed that is not reached
`travelTimeoutTicks` (600) after the claim, or that is no longer a bed block, gives `RESTING_IN_PLACE` in the
same tick, and the bed's factor stops (CD-BED-11). Sleeping and resting set the Brain work order to
`sleep = true`: the work task puts the entity back on its sleep point every tick (CD-BED-10), sets
`enablePoseTypeRecalculation = false` and `POSE_TYPE = PoseType.SLEEP`, and never sets the battle SLEEP
status (CD-AI-07). The bridge marks its pose in the entity's NeoForge data (`cobblemon_dynamics:posed`). Any
order without `sleep`, the end of the order, and a release restore `enablePoseTypeRecalculation = true` for a
marked entity; so does `EntityJoinLevelEvent`, for an entity that was saved while it slept (CD-AI-08). Sleep
effects: `cobblemon:sleep_actor` and `cobblemon:sleep_actorresidual` on the `middle` locator, as Cobblemon's
own sleep timeline plays them, every 40 ticks with no sound (CD-FX-10). A worker that the stamina step fills
gets up in the same step: its bed claim ends (CD-BED-12), and a leashed worker claims its bound target, so it
is `TRAVELING` in that tick (CD-STA-14). Only Tired workers of Work Mode pastures reach a bed (CD-BED-13).

Carry-over from M1: Cobblemon's night sleep (`go_to_sleep`, in the idle activity) gives a pastured Pokémon the
battle SLEEP status and the `pokemon_sleeping` memory, and only its `wake_up` task, in Cobblemon's sleeping
activity, clears them. A work order replaces that activity, so `BrainBridge.enterWork` wakes the Pokémon as
`wake_up` does: it clears the SLEEP status (outside battle) and the memory.

- [ ] **Step 1: Failing unit tests** — `BedSelectorTest`: beds at 3 and 5 blocks gives the 3-block bed, also
  against a higher tier (CD-BED-05); Hay and Plush beds both at 4.0 gives the Plush Bed (CD-BED-06); a
  claimed bed is skipped; no free bed gives none; a full tie goes to the lowest position; the arrival radius
  for a small and a wide Pokémon (CD-BED-09). `RestMachineTest`: the rules above on a fake clock (to bed,
  rest in place, asleep on arrival, the 600-tick bed timeout, a broken bed under a walker and a sleeper, the
  wake of an auto and a leashed worker), CD-STA-09 (a Tired worker at 99.9% is refused a furnace, at 100% it
  gets it) and a CD-ASG-18 day of a worker through 24,000 ticks with work, bed, sleep and rest-in-place
  phases, one state per tick, with the Tired flag set exactly in the rest states. Task 5 adds the healer's
  day. `SleepEffectTest`: the two particles, the locator and the 40-tick loop (CD-FX-10).
- [ ] **Step 2: GameTests** (`RestTests`), for what depends on Cobblemon's navigation, pose and sleep code:

```kotlin
// cdBed05TiredWorkerSleepsOnTheNearestBedAndWorksAgain: a Lv1 Charmander works a furnace and is left one tick of Work Stamina.
//   When Tired it holds a bed claim on the Hay Bed 3 blocks away and not the Plush Bed 5 blocks away, in the same tick
//   (CD-STA-13, CD-BED-05). It walks there through the Brain, stays within 0.25 blocks of the sleep point in the sleep pose
//   (CD-BED-10, about 1,780 ticks), and never has the battle SLEEP status (CD-AI-07). It is full on the tick that the base rate
//   on the way and twice the base rate on the bed give (CD-BED-03, CD-BED-04). On that tick the bed claim is gone (CD-BED-12)
//   and pose recalculation is on, also in an entity loaded from its saved NBT (CD-AI-08); the next scan sends it back to work.
// cdBed08LeashedWorkerRestsInPlaceAndGoesBack (batch stamina_fast: ticksPerLevel = 20 and healer minutes of 1, set by
//   @BeforeBatch and put back by @AfterBatch): a leashed Lv10 Charmander with no bed works 200 ticks, rests in place within
//   0.25 blocks of where it stood in the sleep pose, keeps its binding while its furnace stays reserved (CD-BED-08, CD-LSH-12,
//   CD-CLM-06, CD-CLM-09), is full 200 ± 1 ticks later (the row's 36,000 at 3,600 ticks per level), and is TRAVELING to its
//   furnace on that tick (CD-STA-14).
// cdBed09WailordSleepsOnAHayBed: a Wailord, 7.76 blocks wide, at 0% claims the one Hay Bed and lies on it (CD-BED-09).
// cdAi07NightSleepEndsWhenWorkStarts (batch night_sleep at day time 18,000, put back to 6,000): Cobblemon's own go_to_sleep
//   puts a drowsy Charmander to sleep; a furnace then appears, and from the tick of its claim the worker has no SLEEP status,
//   no sleeping memory and, a tick later, no sleep pose, and it works (the carry-over).
```

  The other planned GameTests, each: GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-STA-13 (`cdSta13TiredAutoWorkerFreesItsMachine`, the second Charmander that claims the furnace at the
    next scan): the release in the same tick is in `cdBed05`, and the scan is M1's assignment.
  - CD-LSH-12 (`cdLsh12TiredBindingWaits`, a binding made while the Pokémon is Tired): `claimBound` skips a
    Tired worker, and `cdBed08` keeps a binding through a rest.
  - CD-BED-07 (`cdBed07OnlyInsideArea`, beds at offsets 8 and 9): the bed lookup reads the work area through
    the same `WorkArea.contains` as the CD-ASG-08 tests.
  - CD-BED-11 (`cdBed11BrokenBedMeansRestInPlace`), CD-BED-13 (`cdBed13OnlyTiredWorkersSleep`) and CD-BED-14
    (`cdBed14SleeperClaimsABedAfterRestart`): engine rules with unit tests on the machine.
  - CD-CLM-06 (`cdClm06SleepingBoundWorkerKeepsItsTarget`, the Work Mode off and second pasture parts) and
    CD-WMD-05 (`cdWmd05FullScene`): the registry's reservation and pasture release have unit tests.
  - CD-FX-10: a demo row; the effect loop has a unit test, and the play-test checks the particles.
- [ ] **Step 3: Write the code, run everything, commit** —
  `git commit -m "Send Tired workers to the nearest free bed and wake them at 100%"`.

---

### Task 5: Healers

**Rows:** CD-HEAL-01, CD-HEAL-02, CD-HEAL-03, CD-HEAL-04, CD-HEAL-05, CD-HEAL-06, CD-HEAL-07, CD-HEAL-08,
CD-HEAL-09 (GameTest), CD-HEAL-10, CD-HEAL-11, CD-HEAL-15, CD-HEAL-16, CD-HEAL-17. CD-HEAL-14 needs a
Fairy machine role, so its GameTest is in M4 Task 5 (the Muse).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/heal/HealTargets.kt` (also `AllyView`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/heal/HealTransaction.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/HealEffect.kt` (also `ActionEffectLookup`)
- Modify: `engine/WorkEngine.kt`, `engine/WorkerMachine.kt` (HEALING), `stamina/Stamina.kt` (`spend`, `fill`),
  `fx/EffectPlayer.kt` (`playHeal`, the Floral Healing context)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/heal/HealTargetsTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/heal/HealTransactionTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/HealEffectTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/RestMachineTest.kt` (the healer's transitions and day)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/HealerTests.kt`

**Interfaces:**
- Produces:

```kotlin
data class AllyView(val id: UUID, val pos: Vec3, val hasFairyType: Boolean, val tired: Boolean, val fraction: Double)
object HealTargets {
    fun canStart(healerFraction: Double, healerTired: Boolean, s: EngineSettings): Boolean   // >= healCost (0.25) and not Tired
    fun qualifies(ally: AllyView?, s: EngineSettings): Boolean   // in the pasture, no Fairy type, Tired or under lowStaminaThreshold
    fun pick(healerId: UUID, healerPos: Vec3, allies: List<AllyView>, isFree: (UUID) -> Boolean, s: EngineSettings): AllyView?
}
object HealTransaction {
    const val RANGE = 2.0
    fun inRange(healerPos: Vec3, allyPos: Vec3): Boolean          // <= 2.0
    fun nearestPoint(allyBox: AABB, from: Vec3): Vec3             // the point of the ally's body nearest to the healer
    fun complete(healer: Stamina, ally: Stamina, s: EngineSettings) // healer -25 points (Tired at 0), ally 100%
}
fun interface ActionEffectLookup { fun find(id: ResourceLocation): Any? }
object HealEffect { fun choose(lookup: ActionEffectLookup): Choice }   // FloralHealing(effect) or Fallback
// WorkerMachine additions, with MachineEvent.Healed and HealCancelled(timedOut)
fun onHeal(w: Worker, now: Long)                                                          // HEALING
fun onHealTick(w: Worker, now: Long, qualifies: Boolean, inRange: Boolean): MachineEvent? // Healed or HealCancelled; IDLE
```

Rules, as built: after each scan pass, every Idle healer of the pasture (a Fairy-type with no machine
binding, so its slot follows the healer rule) with `canStart` picks an ally from the Pokémon of its own
pasture. `pick` drops the healer itself and Fairy-type allies (CD-HEAL-06), takes the nearest Tired ally
(CD-HEAL-07), else the ally with the lowest fraction under `lowStaminaThreshold` (CD-HEAL-08), and skips an
ally that another healer claimed (CD-HEAL-09). The heal claim is a `ClaimKind.HEAL` claim on
`TargetKey.Entity(ally's Pokémon UUID)`. The work order of a healer is an approach order to the ally's
position, looking at the ally. Each tick the heal checks `qualifies` again: an ally at 100%, gone from the
pasture or now a Fairy-type, and a healer that got a binding, cancel the heal at no cost (CD-HEAL-11). A
heal completes within 2.0 blocks of the ally's body (`nearestPoint` of its bounding box), because a healer
stops at the body of a wide ally, whose center can lie far beyond 2 blocks (CD-HEAL-10). An ally not reached
`travelTimeoutTicks` after the claim cancels the heal too, and the healer skips that ally for
`retryWaitTicks`. The completed heal leaves the healer Idle; its stamina step then sends a healer at 0% to
`TO_BED` or `RESTING_IN_PLACE` in the same tick (CD-HEAL-17), and the ally's stamina step gets the ally up
(CD-BED-12).

A healer recovers between heals, so one that starts a heal some ticks after it reached 25% keeps that
recovery and is not Tired after the heal. CD-HEAL-17 happens when a heal starts at exactly 25%.

Effects: if Cobblemon's action-effect registry (`ActionEffects.actionEffects`) has `cobblemon:floralhealing`,
run it with a context whose user is the healer and whose target is the ally, as `UsersProvider` and
`TargetsProvider` and as the `user` and `target` queries; the context answers the battle-only queries
`missed`, `failed`, `hurt` and `target_fainted` with 0 (CD-HEAL-16). Otherwise play the healer's `special`
animation (then `cry`) and spawn `cobblemon:heal_circles` and `cobblemon:heal_sparkles` on the ally's
`middle` locator (CD-HEAL-15).

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

The test files also hold: a configured `lowStaminaThreshold`, a claimed ally skipped for the next one
(CD-HEAL-09), the `qualifies` cases (CD-HEAL-11), a wide ally reached at its body (CD-HEAL-10), the fallback's
animations and particles (CD-HEAL-15), and in `RestMachineTest` the heal on arrival, the cancel when the target
stops qualifying and after 600 ticks, CD-HEAL-17 with and without a free bed, and the CD-ASG-18 day of a worker
and a Lv50 healer at 75%: three heals, one state per tick each, and the Tired flag only in the rest states.

- [ ] **Step 2: GameTests** — `HealerTests`, for the Brain and Cobblemon's action effects:
  - `cdHeal01HealerHealsASleepingAlly`: an unleashed Lv50 Clefairy and a Lv10 Machop that is Tired and sleeps on
    a Hay Bed 7 blocks away. The Clefairy takes a heal claim on the Machop, walks to it and heals it within
    600 ticks, within 2 blocks of its body (CD-HEAL-01, CD-HEAL-10); it reads 75% and the Machop 100%
    (CD-HEAL-04, CD-HEAL-05), and 1 tick later the Machop is Idle with no bed claim (CD-BED-12).
  - `cdHeal16FloralHealingRunsWithTheHealerAsUser` (batch `floral_healing`, whose `@BeforeBatch` puts a
    recording `cobblemon:floralhealing` timeline in `ActionEffects.actionEffects`, because Cobblemon 1.8.4 has
    none, and whose `@AfterBatch` removes it): the heal runs the timeline once, with the Clefairy as the
    only user and the Machop as the only target (CD-HEAL-16).

  The other planned GameTests, each: GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-HEAL-06 (a Tired Azumarill in the same pasture and a Tired Machop in a second pasture 3 blocks away),
    CD-HEAL-07 (allies at 3 and 6 blocks and one at 10% at 1 block) and CD-HEAL-09 (two healers, one Tired
    ally): `pick` and the claim registry have unit tests, and the engine passes them the pasture's own slots.
  - CD-HEAL-11 (the ally leaves the pasture while the healer walks): `qualifies` and `onHealTick` have unit
    tests.
  - CD-HEAL-15: a demo row. Fake players receive no packets, so the play-test checks the healer's pose and the
    sparkles; the fallback's animations and particles have a unit test.
  - CD-HEAL-17 (healers at 25% with and without a free bed): the stamina step's rest path is the one that
    `cdBed05` and `cdBed08` run, and the transition has a unit test.
- [ ] **Step 3: Write the code, run everything, commit** —
  `git commit -m "Let unleashed Fairy-types heal Tired allies for 25% of their Work Stamina"`.

---

### Task 6: Stamina command and the bed and heal claims in the claims command

**Rows:** CD-UI-15, CD-UI-14 (bed and heal claims), CD-SEC-09 (stamina command).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/command/StaminaReport.kt` (the line's parts and lang keys)
- Modify: `command/DynamicsCommands.kt`, `engine/WorkEngine.kt` (`ruleOf`), `assets/cobblemon_dynamics/lang/en_us.json`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/command/StaminaReportTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/CommandTests.kt` (deferred, see Step 1)

`/cobblemondynamics stamina <target>` takes an entity selector of one Pokémon and prints
`<name>: <percent>% Work Stamina, <state>, <worker rule|healer rule>` from lang keys
(`cobblemon_dynamics.command.stamina`, `cobblemon_dynamics.state.<state>` and `cobblemon_dynamics.rule.<rule>`). The
percent rounds down to one decimal, so a Tired Pokémon never reads 100.0. A worker shows the engine's live value,
state and rule. Any other Pokémon shows its saved value plus the recovery of its time away, as the engine would
load it, with the state `not in Work Mode` and the rule from its types and binding. A target that is not a
Pokémon gets `cobblemon_dynamics.command.stamina.not_pokemon`. The subcommand sits under the root literal, which
needs permission level 2 (CD-SEC-09).

The claims command from M1 Task 12 already lists every claim kind with its lang key. A heal claim's key holds
the ally's Pokémon UUID, not an entity UUID, so the command now names the ally through the engine.

- [ ] **Step 1: Failing unit tests** — `StaminaReportTest`: a healer at 40% gives `40.0`, its state key and the
  healer rule key; a Pokémon that is not a worker gets the `none` state; the value rounds down to one decimal;
  and every key of the command has its English text, with `healer rule` and `worker rule` (CD-UI-15).
  - CD-UI-15 (`cdUi15StaminaShowsValueStateAndRule`: a Lv50 healer at 40%; the output holds `40`, its state,
    and `healer rule`): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-UI-14 (`cdUi14ClaimsListsEveryEntry` with a sleeper's bed claim and a healer's heal claim): GameTest
    deferred (high confidence): verified in the end-of-phase pass. M1 deferred the command's GameTest too.
  - CD-SEC-09 (`cdSec09LevelZeroIsRefused` for the stamina command): GameTest deferred (high confidence):
    verified in the end-of-phase pass. The permission is on the root literal, as for the claims command.
- [ ] **Step 2: Write the code, run, commit, push, open the pull request** — `/commit`, then `pr-runner`
  with base `main`. The body lists the rows of Tasks 1 to 6.
  `git commit -m "Add the stamina command and name the heal targets in the claims command"`.
