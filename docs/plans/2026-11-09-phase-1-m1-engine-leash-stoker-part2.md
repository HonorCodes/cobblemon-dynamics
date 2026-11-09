# M1 (part 2 of 2): Work Mode, engine runtime, Stoker, Brain, effects, Work Leash

Part of the [Phase 1 plan](2026-11-09-phase-1-plan.md). Part 1: [engine core](2026-11-09-phase-1-m1-engine-leash-stoker.md).
Same branch: `m1-engine-leash-stoker`.

GameTest conventions for this file: every test class is `@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)`
with instance methods, and every scene comes from `TestKit`. A test that changes global state (roles,
config) uses its own `batch`, so no other test runs beside it.

`TestKit` additions in this milestone (write each one the first time a task needs it):

```kotlin
fun engine(helper: GameTestHelper): WorkEngine = WorkEngine.require(helper.level.server)
fun workMode(pasture: PokemonPastureBlockEntity, on: Boolean)          // WorkModeService.apply, no permission check
fun furnace(helper: GameTestHelper, rel: BlockPos, input: ItemStack?, fuel: ItemStack? = null,
            block: Block = Blocks.FURNACE): AbstractFurnaceBlockEntity
fun workerOf(helper: GameTestHelper, entity: PokemonEntity): Worker?
fun claimOf(helper: GameTestHelper, entity: PokemonEntity): Claim?
fun moveTo(helper: GameTestHelper, entity: PokemonEntity, rel: BlockPos)   // teleport inside the roam box
fun operator(helper: GameTestHelper): ServerPlayer                      // mock player opped at level 2
fun leash(player: ServerPlayer): ItemStack                              // puts a Work Leash in the main hand
fun clickEntity(player: ServerPlayer, entity: Entity, sneak: Boolean = false): InteractionResult   // player.interactOn
fun clickBlock(helper: GameTestHelper, player: ServerPlayer, rel: BlockPos, face: Direction = Direction.UP): InteractionResult
    // player.gameMode.useItemOn(player, level, stack, MAIN_HAND, BlockHitResult(center, face, abs, false))
fun simulateRestart(helper: GameTestHelper)                              // WorkEngine.resetRuntimeForTest()
```

`clickEntity` and `clickBlock` go through vanilla's interaction pipeline, so NeoForge's events, Cobblemon's
handlers and `Item#onItemUseFirst` all run as they do for a real click.

---

### Task 7: Pasture adapter and Work Mode

**Rows:** CD-WMD-01, CD-WMD-02, CD-WMD-03, CD-WMD-04, CD-WMD-05, CD-WMD-06, CD-SEC-01, CD-SEC-02,
CD-SEC-03, CD-SEC-04, CD-SEC-05, CD-SEC-06, CD-ASG-15, CD-BAL-06.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsAttachments.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/pasture/WorkModeData.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/pasture/WorkPastureIndex.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/pasture/PastureAdapter.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/pasture/WorkModePolicy.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/pasture/WorkModeService.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/pasture/WorkModePolicyTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/WorkModeTests.kt`

**Interfaces:**
- Produces:

```kotlin
object DynamicsAttachments {
    val WORK_MODE: DeferredHolder<AttachmentType<*>, AttachmentType<WorkModeData>>      // block entity, serialized, default off
    val WORK_PASTURES: DeferredHolder<AttachmentType<*>, AttachmentType<WorkPastureIndex>> // ServerLevel, serialized
}
data class WorkModeData(val enabled: Boolean) { companion object { val CODEC: Codec<WorkModeData>; val OFF: WorkModeData } }
class WorkPastureIndex(positions: MutableSet<Long>) { fun add(pos: BlockPos); fun remove(pos: BlockPos); fun all(): Set<Long> }

/** The one package that reads Cobblemon pasture internals. */
object PastureAdapter {
    fun pastureAt(level: ServerLevel, pos: BlockPos): PokemonPastureBlockEntity?   // bottom half only
    fun isWorkMode(be: PokemonPastureBlockEntity): Boolean
    fun placer(be: PokemonPastureBlockEntity): UUID?                                 // ownerId
    fun tethered(be: PokemonPastureBlockEntity): List<TetheredPokemon>
    fun pastureOf(entity: PokemonEntity): BlockPos?                                  // entity.tethering?.pasturePos
    fun linkedPasture(player: ServerPlayer): BlockPos?                               // PastureLinkManager, same dimension
    fun maxWanderDistance(): Int                                                     // Cobblemon.config.pastureMaxWanderDistance
}
data class TetheredPokemon(val pokemonId: UUID, val ownerId: UUID, val pokemon: Pokemon?, val entity: PokemonEntity?)
// entity: found with level.getEntitiesOfClass(PokemonEntity, roam box) by entity.pokemon.uuid, because
// Pokemon.entity is null for pastured Pokémon (it reads ActivePokemonState only), and Tethering.entityId
// goes stale when the entity reloads.

object WorkModePolicy {
    sealed interface Decision { data object Apply : Decision; data object Ignore : Decision; data class Reject(val reason: String) : Decision }
    fun decide(requester: UUID, isOperator: Boolean, placer: UUID?, distance: Double, sameDimension: Boolean,
               now: Long, lastAccepted: Long?, cooldownTicks: Int): Decision
}

object WorkModeService {
    fun request(player: ServerPlayer, pasturePos: BlockPos, enabled: Boolean): WorkModePolicy.Decision
    fun apply(level: ServerLevel, pasturePos: BlockPos, enabled: Boolean)   // no checks; tests and request() use it
}
```

`WorkModePolicy.decide` order: an operator (permission level 2 or higher) passes the owner check; a
non-operator passes only when `placer != null && placer == requester` (CD-SEC-01, CD-SEC-02,
CD-WMD-03, CD-WMD-04). Then distance: `!sameDimension || distance > 10.0` rejects (CD-SEC-04). Then
rate: `lastAccepted != null && now - lastAccepted < cooldownTicks` ignores (CD-SEC-03). Otherwise
`Apply`. The requested state is applied as given, never flipped (CD-SEC-05). `request` measures the
distance from the player's position to the pasture block center. It keeps `lastAccepted` per player in
memory, applies decisions in arrival order (CD-SEC-06), and after an `Apply` sends the new
`pasture_panel_state` to every player who has that pasture's panel open (Task 13 adds the sender).

`apply(level, pos, enabled)` sets the attachment with `be.setData(WORK_MODE, WorkModeData(enabled))`, adds
or removes `pos` in the level's `WORK_PASTURES`, and calls `WorkEngine.onWorkModeChanged(level, pos, enabled)`.
When `enabled` is false, the engine releases every claim whose `pasture` is that pasture in the same call
(CD-WMD-05) and stops writing Brain memory for its workers.

- [ ] **Step 1: Write the failing policy test**

```kotlin
package codes.honor.cobblemondynamics.pasture

import codes.honor.cobblemondynamics.pasture.WorkModePolicy.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkModePolicyTest {
    private val placer = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private fun decide(who: UUID, op: Boolean = false, owner: UUID? = placer, distance: Double = 3.0,
                       same: Boolean = true, now: Long = 100, last: Long? = null) =
        WorkModePolicy.decide(who, op, owner, distance, same, now, last, cooldownTicks = 10)

    @Test fun `CD-WMD-03 the placer is accepted`() = assertEquals(Decision.Apply, decide(placer))
    @Test fun `CD-WMD-04 an operator is accepted on any pasture`() = assertEquals(Decision.Apply, decide(stranger, op = true))
    @Test fun `CD-SEC-01 a stranger is rejected`() { assertInstanceOf(Decision.Reject::class.java, decide(stranger)) }
    @Test fun `CD-SEC-02 with no placer only operators pass`() {
        assertInstanceOf(Decision.Reject::class.java, decide(stranger, owner = null))
        assertEquals(Decision.Apply, decide(stranger, op = true, owner = null))
    }
    @Test fun `CD-SEC-03 a request 9 ticks after the last accepted one is ignored, 10 ticks after is applied`() {
        assertEquals(Decision.Ignore, decide(placer, now = 109, last = 100))
        assertEquals(Decision.Apply, decide(placer, now = 110, last = 100))
    }
    @Test fun `CD-SEC-04 10_0 blocks passes, 10_1 blocks and another dimension fail`() {
        assertEquals(Decision.Apply, decide(placer, distance = 10.0))
        assertInstanceOf(Decision.Reject::class.java, decide(placer, distance = 10.1))
        assertInstanceOf(Decision.Reject::class.java, decide(placer, same = false))
    }
}
```

`CD-SEC-05` and `CD-SEC-06` are tested on `WorkModeService` in the GameTests below, because they need the
attachment.

- [ ] **Step 2: Run it and see it fail**, then write `WorkModePolicy` and run it again to see it pass.

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.pasture.WorkModePolicyTest"`
Expected: first FAIL (unresolved reference), then PASS (7 tests).

- [ ] **Step 3: Write the GameTests**

```kotlin
@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class WorkModeTests {
    @GameTest(template = "floor_9x5x9")
    fun cdWmd01NewPastureIsOff(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        helper.assertFalse(PastureAdapter.isWorkMode(pasture), "a new pasture is in Work Mode")
        helper.succeed()
    }

    @GameTest(template = "floor_9x5x9")
    fun cdWmd02WorkModeSurvivesSaveAndLoad(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), TestKit.owner(helper))
        WorkModeService.apply(helper.level, pasture.blockPos, true)
        val tag = pasture.saveWithFullMetadata(helper.level.registryAccess())
        val reloaded = BlockEntity.loadStatic(pasture.blockPos, pasture.blockState, tag, helper.level.registryAccess())
            as PokemonPastureBlockEntity
        helper.assertTrue(PastureAdapter.isWorkMode(reloaded), "Work Mode reads off after a reload")
        helper.succeed()
    }

    @GameTest(template = "floor_9x5x9")
    fun cdWmd03PlacerTogglesWithinOneTick(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(4, 1, 4), owner)
        owner.teleportTo(pasture.blockPos.x + 2.5, pasture.blockPos.y.toDouble(), pasture.blockPos.z + 0.5)
        WorkModeService.request(owner, pasture.blockPos, true)
        helper.runAfterDelay(1) { helper.assertTrue(PastureAdapter.isWorkMode(pasture), "on was not applied") }
        // The second request waits out the 10-tick cooldown of CD-SEC-03.
        helper.runAfterDelay(12) { WorkModeService.request(owner, pasture.blockPos, false) }
        helper.runAfterDelay(13) {
            helper.assertFalse(PastureAdapter.isWorkMode(pasture), "off was not applied")
            helper.succeed()
        }
    }
    // cdWmd04OperatorTogglesAnyPasture: an operator() who is not the placer requests on -> on.
    // cdSec01StrangerCannotToggle: a second owner() requests off on a pasture that is on -> stays on.
    // cdSec02OwnerlessPastureAcceptsOperatorsOnly: set pasture.ownerId = null; a regular player requests on -> off; an operator -> on.
    // cdSec04DistanceAndDimension: players at 10.0 and 10.1 blocks (teleport), and one in the Nether
    //   (owner.teleportTo(server.getLevel(Level.NETHER)...)); only the 10.0 request applies.
    // cdSec05RepeatedOnStaysOn: two "on" requests 10 ticks apart -> on.
    // cdSec06TwoOperatorsSameTick: operator A requests on and operator B requests off in the same tick -> off.

    @GameTest(template = "floor_15x6x15", timeoutTicks = 300)
    fun cdWmd06OffPastureGivesNoClaims(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        helper.runAtTickTime(250) {
            helper.assertTrue(TestKit.claimOf(helper, fire) == null, "a Pokémon in a pasture that is off got a claim")
            helper.succeed()
        }
    }
    // The row says 400 ticks. Use timeoutTicks = 450 and runAtTickTime(400) when writing it.

    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdBal06SeventeenthPokemonIsRefused(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        WorkModeService.apply(helper.level, pasture.blockPos, true)
        repeat(16) { TestKit.tether(helper, pasture, owner, "rattata level=5") }
        helper.assertFalse(pasture.canAddPokemon(owner, TestKit.pokemon("rattata level=5"), 16), "Cobblemon accepted a 17th Pokémon")
        helper.succeed()
    }
}
```

Write the six commented tests in full in the same style before Step 4. `CD-WMD-05` and `CD-ASG-15` need
workers, so their GameTests live in Task 8.

- [ ] **Step 4: Write the attachment, index, adapter and service**

```kotlin
// DynamicsAttachments.kt
object DynamicsAttachments {
    val REGISTER: DeferredRegister<AttachmentType<*>> = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID)
    val WORK_MODE = REGISTER.register("work_mode") { ->
        AttachmentType.builder { -> WorkModeData.OFF }.serialize(WorkModeData.CODEC).build()
    }
    val WORK_PASTURES = REGISTER.register("work_pastures") { ->
        AttachmentType.builder { -> WorkPastureIndex(HashSet()) }.serialize(WorkPastureIndex.CODEC).build()
    }
}
```

Register `DynamicsAttachments.REGISTER.register(modBus)` in the mod constructor. `WorkPastureIndex.CODEC`
is `Codec.LONG.listOf().xmap({ WorkPastureIndex(it.toHashSet()) }, { it.all().toList() })`. After every
mutation of the index, call `level.setData(WORK_PASTURES, index)` again so the level saves it.

- [ ] **Step 5: Run the GameTests**

Run: `./gradlew runGameTestServer`
Expected: exit 0; all `WorkModeTests` pass beside the M0 smoke tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/registry src/main/kotlin/codes/honor/cobblemondynamics/pasture src/test/kotlin/codes/honor/cobblemondynamics/pasture src/gametest
git commit -m "Store Work Mode on the pasture and accept toggles from the placer and operators"
```

---

### Task 8: Engine runtime and the Stoker

**Rows:** CD-STK-01, CD-STK-02, CD-STK-03, CD-STK-04, CD-STK-05, CD-STK-06, CD-STK-07, CD-ASG-01 (furnace
version), CD-ASG-03, CD-ASG-08, CD-ASG-11, CD-ASG-12, CD-ASG-13, CD-ASG-15, CD-WMD-05, CD-CLM-07,
CD-CLM-08, CD-CFG-05, CD-CFG-07, CD-CFG-08, CD-PERF-03.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/EngineEvents.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/PastureRuntime.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/TargetScan.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/LevelSpotGrid.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/vanilla/FurnaceHandler.kt`
- Modify: `src/main/resources/META-INF/accesstransformer.cfg`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/TargetScanTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/StokerTests.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/AssignmentTests.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/RoleDataTests.kt`

**Interfaces:**
- Consumes: Tasks 1 to 7.
- Produces:

```kotlin
class WorkEngine private constructor(val server: MinecraftServer) {
    val claims: ClaimRegistry
    fun worker(pokemonId: UUID): Worker?
    fun entityOf(pokemonId: UUID): PokemonEntity?
    fun tickLevel(level: ServerLevel)                                    // LevelTickEvent.Post
    fun onWorkModeChanged(level: ServerLevel, pasture: BlockPos, enabled: Boolean)
    fun onChunkUnload(level: ServerLevel, chunk: ChunkPos)
    fun resetRuntimeForTest()
    companion object {
        fun start(server: MinecraftServer)     // ServerStartingEvent
        fun stop()                             // ServerStoppedEvent
        fun get(): WorkEngine?
        fun require(server: MinecraftServer): WorkEngine
    }
}

/** Pure part of a scan pass, so CD-PERF-03 is a unit test. */
interface ScanWorld {
    fun blockEntitiesIn(area: WorkArea): Sequence<Pair<BlockPos, BlockEntity>>
    fun blockState(x: Int, y: Int, z: Int): BlockState
}
class TargetScan(area: WorkArea, roles: List<ActiveRole>, workersNeedBlockScan: Boolean, budget: Int) {
    fun startPass(world: ScanWorld)                 // block entities, in one tick
    fun stepBlocks(world: ScanWorld): Boolean       // true when the pass is complete
    val found: List<FoundTarget>
}
data class FoundTarget(val pos: BlockPos, val roles: List<ActiveRole>)
```

Per `LevelTickEvent.Post` on a `ServerLevel`, `tickLevel` runs these steps for that level, in order:

1. Pastures due for their 20-tick poll (staggered by `ScanScheduler.phaseFor(pos.asLong(), 20)`): read the
   tethered list through `PastureAdapter.tethered`, add new workers, and release and forget workers that
   left (CD-ASG-15). A worker counts only when its Pokémon is player-owned, not fainted, not a battle
   clone, its entity is in this level, and `tethering.playerId == pokemon.getOwnerUUID()` (CD-ASG-01).
   A pasture whose block is gone leaves the index.
2. Claimed targets: `handler.stillValid(level, pos)` for every claim in this level. An invalid target
   releases its claim in the same tick (CD-CLM-08).
3. Scans: each Work Mode pasture whose `ScanScheduler` is due starts a `TargetScan`. Only a pasture with a
   worker eligible for a `ScanKind.BLOCK` role iterates block positions (CD-PERF-03).
4. When a scan completes: every Idle worker gets `Assigner.pickForIdle`, and every auto worker in Standby
   for `standbyMoveTicks` gets `Assigner.pickForStandbyMove`. A granted claim picks a work spot with
   `WorkSpots.find` over `LevelSpotGrid`. With no spot, the claim is released and the target is skipped
   for `retryWaitTicks`.
5. Every worker with a claim: `arrived` is the entity's horizontal distance to the spot center at most
   `max(0.75, entity.bbWidth / 2)`, with `|dy| < 1.5`. Run `WorkerMachine.onTick`. On entering Working call
   `handler.onStart`, snap the entity to the spot center, and on leaving Working call `handler.onStop`.
   While Working call `handler.applyWork` every tick.
6. Task 9 writes the Brain memory here, and Task 10 runs the effects here.

`EngineEvents` subscribes `ServerStartingEvent`, `ServerStoppedEvent`, `LevelTickEvent.Post` and
`ChunkEvent.Unload` on `NeoForge.EVENT_BUS`. `onChunkUnload` releases every claim whose target sits in that
chunk (CD-CLM-08).

`FurnaceHandler` (`id = cobblemon_dynamics:furnace`, `scanKind = BLOCK_ENTITY`):

- `matches`: `blockEntity is AbstractFurnaceBlockEntity` (CD-STK-01).
- `hasDemand`: the input slot holds an item, `quickCheck` finds a recipe, and the result fits the output
  slot. This repeats vanilla's `canBurn` check in Kotlin, so no access transformer on the patched private
  `canBurn` is needed (CD-STK-05).
- `applyWork`: while `hasDemand`, `litTime = max(litTime, 2)` and `litDuration = litTime`. If the block
  state has `LIT = false`, set it to true with flag 3 (CD-STK-04). Call `setChanged()`. No fuel item is
  touched, because vanilla only burns fuel when the furnace is unlit (CD-STK-02, CD-STK-03).
- `onStop`: nothing. `litTime` is at most 2, so the furnace goes out within 2 ticks and follows vanilla
  fuel rules after that (CD-STK-06).
- `stillValid`: the block entity at the position is still an `AbstractFurnaceBlockEntity`.

CD-STK-07 asks for a 1,600-tick burn duration "so the flame gauge reads as a coal burn", and its test
fails when the gauge reads empty. The gauge is `litTime ÷ litDuration`, so `litDuration = 1600` with
`litTime = 2` reads empty, and a larger `litTime` breaks CD-STK-06. `litDuration = litTime` keeps the gauge
full while the furnace cooks, which passes the row's test. Record this in the pull request.

Access transformer lines:

```text
public net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity litTime # CD-STK-02
public net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity litDuration # CD-STK-07
public net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity quickCheck # CD-STK-05
```

- [ ] **Step 1: Write the failing scan unit test**

```kotlin
class TargetScanTest {
    /** Counts block-state reads; block entities come from a fixed list. */
    private class CountingWorld(private val bes: List<Pair<BlockPos, BlockEntity>>) : ScanWorld {
        var stateReads = 0
        override fun blockEntitiesIn(area: WorkArea) = bes.asSequence()
        override fun blockState(x: Int, y: Int, z: Int): BlockState { stateReads++; error("not used in this test") }
    }

    @Test
    fun `CD-PERF-03 a scan with only Fire-type workers reads no block state`() {
        val world = CountingWorld(emptyList())
        val scan = TargetScan(WorkArea(BlockPos.ZERO, 8, 6), roles = emptyList(), workersNeedBlockScan = false, budget = 512)
        scan.startPass(world)
        while (!scan.stepBlocks(world)) { }
        assertEquals(0, world.stateReads)
    }
}
```

Run: `./gradlew test --tests "codes.honor.cobblemondynamics.engine.TargetScanTest"`
Expected: FAIL (unresolved), then PASS after `TargetScan` exists.

- [ ] **Step 2: Write the failing GameTests**

```kotlin
@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class StokerTests {
    /** A Work Mode pasture at (7,1,7) with one Charmander, and a furnace at `furnaceRel`. */
    private fun scene(helper: GameTestHelper, furnaceRel: BlockPos = BlockPos(7, 1, 3), input: ItemStack? = ItemStack(Items.RAW_IRON, 8),
                      fuel: ItemStack? = null, block: Block = Blocks.FURNACE): Triple<PokemonEntity, AbstractFurnaceBlockEntity, ServerPlayer> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnace = TestKit.furnace(helper, furnaceRel, input, fuel, block)
        return Triple(TestKit.tether(helper, pasture, owner, "charmander level=10"), furnace, owner)
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 400)
    fun cdStk01FurnaceSmokerAndBlastFurnaceAreClaimed(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val furnaces = listOf(
            TestKit.furnace(helper, BlockPos(3, 1, 3), ItemStack(Items.RAW_IRON, 8)),
            TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.BEEF, 8), block = Blocks.SMOKER),
            TestKit.furnace(helper, BlockPos(11, 1, 3), ItemStack(Items.RAW_GOLD, 8), block = Blocks.BLAST_FURNACE),
        )
        repeat(3) { TestKit.tether(helper, pasture, owner, "charmander level=10") }
        helper.succeedWhen {
            val claimed = TestKit.engine(helper).claims.all().map { BlockPos.of((it.key as TargetKey.Block).pos) }.toSet()
            furnaces.forEach { helper.assertTrue(it.blockPos in claimed, "${it.blockState.block} is not claimed") }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_200)
    fun cdStk02EightIronIn1600Ticks(helper: GameTestHelper) {
        val (_, furnace, _) = scene(helper)
        var workingSince = -1L
        helper.onEachTick {
            val w = TestKit.engine(helper).claims.holderOf(TargetKey.block(helper.level.dimension().location(), furnace.blockPos))
            if (workingSince < 0 && w != null && TestKit.engine(helper).worker(w.worker)?.status == WorkerStatus.WORKING) workingSince = helper.tickTime
            if (furnace.getItem(2).count == 8) {
                val took = helper.tickTime - workingSince
                helper.assertTrue(took in 1_580..1_620, "8 smelts took $took ticks")
                helper.succeed()
            }
        }
    }
    // cdStk02SmokerCooksEightBeefIn800Ticks: same with Blocks.SMOKER and 8 beef, 780..820 ticks.
    // cdStk03CoalIsNeverConsumed: fuel = 1 coal; after 8 smelts the fuel slot still holds 1 coal.
    // cdStk04LitIsSetOneTickAfterWorkingStarts: record the tick Working starts; on the next tick the block state's LIT is true.
    // cdStk05FullOutputGivesStandby: output slot set to 64 iron ingots; the worker is in STANDBY within 20 ticks of reaching the spot.
    // cdStk06ReleaseReturnsVanillaRules: once Working, release the claim (TestKit.engine(helper).claims.release(...) through the
    //   engine's release path); with no fuel LIT is false within 2 ticks; in a second furnace with 1 coal the coal is consumed.
    // cdStk07GaugeIsNotEmptyWhileCooking: while Working and cooking, litTime > 0 and litDuration > 0 (dataAccess indices 0 and 1).
}
```

```kotlin
@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class AssignmentTests {
    // cdAsg01OnlyOwnedHealthyPastured: a wild Charmander (TestKit.wild), a party Charmander sent out by the owner
    //   (pokemon.sendOut(level, pos, null)), a pastured Charmander with currentHealth = 0, and a healthy pastured
    //   Charmander, all 3 blocks from a furnace with input, for 400 ticks; only the healthy pastured one holds a claim.
    // cdAsg03NearestFurnaceWins: furnaces with input 3 and 6 blocks from one Idle Charmander (moveTo before Work Mode on);
    //   the claim is on the 3-block furnace.
    // cdAsg08WorkAreaEdges (template floor_23x10x23 and floor_23x17x23): furnaces at horizontal offsets 8 and 9 from the
    //   pasture, then at vertical offsets 6 and 7 (on pillars); the 8 and 6 furnaces get claimed, the 9 and 7 never do.
    // cdAsg11WorkingWithinOneTickOfArrival: track the first tick the worker is within arrival distance; status is WORKING
    //   on that tick or the next.
    // cdAsg12StandbyKeepsClaimAndResumes: remove the input once Working; the claim stays; put input back; WORKING within 20 ticks.
    // cdAsg13UnreachableTargetTimesOut: a furnace boxed in glass on all sides with no standable spot in range, but with a
    //   spot found through a 1-block gap closed after the claim; the claim is held at claimTick + 599 and gone at + 601.
    // cdAsg15LeavingThePastureReleasesTheClaim: once Working, pasture.releasePokemon(uuid); no claim 20 ticks later.
    // cdWmd05WorkModeOffReleasesEveryClaim: two Working Stokers; inject a BED and a HEAL claim for two more workers of the
    //   same pasture with engine.claims.tryClaim; apply Work Mode off; 1 tick later the registry holds none of the four.
    //   M2 Task 4 replaces the injected claims with a real sleeper and a real healer.
    // cdClm07ClaimsAreNotSaved: two Working Stokers; TestKit.simulateRestart(helper); the registry is empty before the next scan.
    // cdClm08BrokenReplacedUnloaded: three Working Stokers; break furnace 1 (destroyBlock), replace furnace 2 with stone,
    //   call EngineEvents.onChunkUnload for furnace 3's chunk; 1 tick later none of the three claims remains.
}

@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class RoleDataTests {
    // batch = "roles_cfg07": cdCfg07DatapackLetsWaterTypesStoke: RoleRegistry.applyForTest(stoker with types fire + water);
    //   a Squirtle claims a furnace with input; restore the parsed map in a finally block.
    // batch = "roles_cfg08": cdCfg08ReloadAppliesChangedTypes: applyForTest(stoker with types water only); a Charmander
    //   gets no claim for 200 ticks; restore.
    // batch = "roles_cfg05": cdCfg05DisabledRoleGetsNoClaims: WorkEngineSettings.current = DEFAULT.copy(disabledRoles =
    //   setOf(stoker id)), RoleRegistry.rebuild(); a Charmander beside a furnace with input gets no claim for 400 ticks; restore.
}
```

Write every commented test in full before Step 3. `RoleRegistry.applyForTest(definitions)` replaces the
parsed map and rebuilds `current`, exactly as `apply` does after a `/reload`, so CD-CFG-07 and CD-CFG-08
exercise the reload path. The test pack's `reload` scenario (M5 Task 6) repeats CD-CFG-08 with a real
data pack and a real `/reload`.

- [ ] **Step 3: Run them and see them fail**

Run: `./gradlew runGameTestServer`
Expected: the new tests fail (no engine, no handler), the M0 and Task 7 tests pass.

- [ ] **Step 4: Write the engine, the scan, the spot grid and the handler**

`LevelSpotGrid(level, handler, canonical)` implements `SpotGrid`: standable when the block below has a
full top face (`isFaceSturdy(level, below, Direction.UP)`) and the cells at `y` and `y + 1` have empty
collision shapes; a cell blocks sight when its collision shape is not empty and
`handler.isPartOfTarget(level, canonical, pos)` is false.

Register `FurnaceHandler` in the mod constructor with `HandlerRegistry.register(FurnaceHandler)`.

- [ ] **Step 5: Run the tests and see them pass**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`; every GameTest passes.

- [ ] **Step 6: Commit**

```bash
git add src/main src/test src/gametest
git commit -m "Run the work engine per level and keep furnaces lit with the Stoker role"
```

---

### Task 9: Brain bridge

**Rows:** CD-AI-01, CD-AI-02, CD-AI-03, CD-AI-04, CD-AI-05, CD-AI-06, CD-AI-09.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/DynamicsActivities.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/DynamicsMemories.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/WorkOrder.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/SwitchToWorkTaskConfig.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/WorkTaskConfig.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/SwitchFromWorkTaskConfig.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/BrainBridge.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/behaviours/pokemon/auto/work.json`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/ai/LookMathTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/BrainTests.kt`

**Interfaces:**
- Produces:

```kotlin
object DynamicsActivities { val WORK: DeferredHolder<Activity, Activity> }                  // cobblemon_dynamics:work
object DynamicsMemories { val WORK_ORDER: DeferredHolder<MemoryModuleType<*>, MemoryModuleType<WorkOrder>> }  // no codec: never saved
data class WorkOrder(val spot: Vec3, val lookAt: Vec3?, val hold: Boolean, val sleep: Boolean = false)
object BrainBridge {
    fun registerTaskTypes()                                   // adds the three ids to TaskConfig.types, in common setup
    fun write(entity: PokemonEntity, order: WorkOrder)        // brain.setMemory, every tick (CD-AI-04)
    fun clear(entity: PokemonEntity)                          // brain.eraseMemory
    fun isWorking(entity: PokemonEntity): Boolean             // brain.isActive(WORK)
}
object LookMath { fun yawTowards(from: Vec3, to: Vec3): Float }   // degrees, Minecraft convention
```

The behaviour file:

```json
{
  "visible": false,
  "configurations": [
    {
      "type": "add_tasks_to_activity",
      "activity": "minecraft:core",
      "tasksByPriority": { "0": ["cobblemon_dynamics:switch_to_work"] }
    },
    {
      "type": "add_tasks_to_activity",
      "activity": "cobblemon_dynamics:work",
      "tasksByPriority": { "0": ["cobblemon_dynamics:switch_from_work", "cobblemon_dynamics:work"] }
    }
  ]
}
```

`switch_to_work` sits in the core activity, not idle, so a work order also wins over Cobblemon's night
sleep. Machines run at night.

`SwitchToWorkTaskConfig` follows `SwitchToActionEffectTaskConfig`: it declares `WORK_ORDER` with
`behaviourConfigurationContext.addMemories(...)`, and its trigger requires `present(WORK_ORDER)` and calls
`brain.setActiveActivityIfPossible(WORK)` when WORK is not active. `SwitchFromWorkTaskConfig` follows
`SwitchFromActionEffectTaskConfig`: `absent(WORK_ORDER)` triggers `updateActivityFromSchedule(dayTime,
gameTime)` and sets `enablePoseTypeRecalculation = true` (CD-AI-03, and CD-AI-08 in M2). `WorkTaskConfig`
builds one task that, while `present(WORK_ORDER)`: when `hold` is false, sets `WALK_TARGET` to the spot
(speed 0.4, close enough 0); when `hold` is true, erases `WALK_TARGET`, sets `PATH_COOLDOWN` for 40 ticks
and stops the navigation (CD-AI-02); and in both cases sets `LOOK_TARGET` to a `BlockPosTracker` at
`lookAt` and turns the body: `yBodyRot = yHeadRot = yRot = LookMath.yawTowards(position, lookAt)`
(CD-AI-05, CD-AI-09). The engine sets `hold = true` once the worker has arrived.

- [ ] **Step 1: Write the failing unit test**

```kotlin
class LookMathTest {
    @Test fun `facing south is 0 degrees`() = assertEquals(0f, LookMath.yawTowards(Vec3.ZERO, Vec3(0.0, 0.0, 5.0)), 0.01f)
    @Test fun `facing west is 90 degrees`() = assertEquals(90f, LookMath.yawTowards(Vec3.ZERO, Vec3(-5.0, 0.0, 0.0)), 0.01f)
    @Test fun `facing north is 180 degrees`() = assertEquals(180f, Math.abs(LookMath.yawTowards(Vec3.ZERO, Vec3(0.0, 0.0, -5.0))), 0.01f)
}
```

`LookMath.yawTowards` is `(Mth.atan2(to.z - from.z, to.x - from.x) * 180 / PI).toFloat() - 90f`, wrapped with
`Mth.wrapDegrees`.

- [ ] **Step 2: Write the failing GameTests**

```kotlin
@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class BrainTests {
    // cdAi01BrainListsWorkActivity: a newly pastured Charmander in a Work Mode pasture; after entity.remakeBrain()
    //   the brain's available activities contain DynamicsActivities.WORK (Brain#isActive after a work order, or reflect
    //   on activityRequirements keys). Also assert the jar resource
    //   "data/cobblemon_dynamics/behaviours/pokemon/auto/work.json" exists on the class path.
    // cdAi02WorkingWorkerHoldsItsSpot: once Working, record the spot; for 600 ticks the entity stays within 0.5 blocks.
    // cdAi03ReleaseRestoresActivitiesWithin20Ticks: release a Working worker's claim; 20 ticks later WORK is not active.
    // cdAi04RemakeBrainRestoresWorkNextTick: call entity.remakeBrain() on a Working worker; 1 tick later WORK is active.
    // cdAi05FacesTheTarget: for 600 ticks while Working or Standby, |wrapDegrees(yBodyRot - yawTowards(target))| <= 15.
    // cdAi06NonWorkersKeepCobblemonActivities: a wild Charmander and one in a pasture that is off, 1,200 ticks; WORK never active.
    // cdAi09LookTaskRunsInsideWork: while WORK is active, Cobblemon's idle activity is not active, and cdAi05's check holds.
}
```

Write each in full. Timeouts: the row's tick count plus 200.

- [ ] **Step 3: Write the Brain bridge, register it, and wire the engine**

Register `DynamicsActivities` and `DynamicsMemories` DeferredRegisters on the mod bus. Call
`BrainBridge.registerTaskTypes()` from `FMLCommonSetupEvent` with `event.enqueueWork { }`, before any
Pokémon brain is built. In `WorkEngine` step 6, call `BrainBridge.write` for every worker with a claim,
with `hold = arrived`, and `BrainBridge.clear` on release. If `entity.brain` does not hold the
`WORK_ORDER` memory after a write (a species with `baseAI`), log one warning per species and release the
claim.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`; every GameTest passes, including Task 8's tests, which now move through the
Brain instead of teleporting.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test src/gametest
git commit -m "Drive workers through a Brain work activity added by an auto behaviour"
```

---

### Task 10: Effects and the work focus entity

**Rows:** CD-FX-01, CD-FX-02 (Stoker), CD-FX-04, CD-FX-05, CD-FX-06, CD-FX-07, CD-FX-08, CD-FX-09,
CD-FX-11, CD-PERF-04.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/EffectClock.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/Recipients.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/ParticleCatalog.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/EffectPlayer.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/fx/WorkFocusEntity.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsEntities.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/WorkSoundPayload.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/client/ClientEffects.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/EffectClockTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/RecipientsTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/ParticleCatalogTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/FocusEntityTests.kt`

**Interfaces:**
- Produces:

```kotlin
object EffectClock { fun shouldPlay(startTick: Long, now: Long, loopTicks: Int): Boolean }   // start, start + 40, ...
object Recipients { fun inRange(distanceSqr: Double, radius: Int): Boolean }                   // <= radius², CD-FX-08
interface ParticleCatalog { fun contains(id: ResourceLocation): Boolean }
class CheckedParticles(private val catalog: ParticleCatalog, private val warn: (String) -> Unit) {
    fun filter(ids: List<ResourceLocation>): List<ResourceLocation>      // drops missing ids, warns once per id
}
object JarParticleCatalog : ParticleCatalog   // reads "identifier" from Cobblemon's assets/cobblemon/bedrock/particles/**.json
class WorkFocusEntity(type: EntityType<WorkFocusEntity>, level: Level) : Entity(type, level)
object EffectPlayer {
    fun tickWorking(level: ServerLevel, entity: PokemonEntity, focus: WorkFocusEntity, role: RoleDefinition, workingSince: Long, now: Long)
    fun spawnFocus(level: ServerLevel, center: Vec3): WorkFocusEntity
    fun removeFocus(focus: WorkFocusEntity)
}
data class WorkSoundPayload(val entityId: Int, val sound: ResourceLocation) : CustomPacketPayload
```

On each effect tick, `EffectPlayer` sends to every player in the level with
`Recipients.inRange(player.distanceToSqr(entity), effectBroadcastRadius)`:
`PlayPosableAnimationPacket` with `role.effect.animationsWithCry`, one `SpawnSnowstormEntityParticlePacket`
per actor particle (source = the worker), one per target particle (source = the worker, targeted entity =
the focus entity), and, when the role names a sound, a `WorkSoundPayload`. The client plays that sound
only when `ClientConfig.workSounds` is true (CD-FX-09). Check the constructor of
`PlayPosableAnimationPacket` in the Cobblemon jar before writing the call, and check that each sound id in
the role files exists in Cobblemon's `assets/cobblemon/sounds.json`.

`WorkFocusEntity`'s type is built with `EntityType.Builder.of(::WorkFocusEntity, MobCategory.MISC)
.sized(0.1f, 0.1f).noSave().noSummon().fireImmune().clientTrackingRange(8)` (CD-FX-06). The entity sets
`noPhysics = true` and `isInvisible = true`, returns `false` from `isPickable()`, `canBeCollidedWith()` and
`isPushable()`, and has empty save methods (CD-FX-05). The client renderer is `NoopRenderer`. The engine
spawns one focus entity when a worker enters Working and removes it in the same tick that the worker
leaves Working (CD-FX-07, CD-PERF-04).

- [ ] **Step 1: Write the failing unit tests**

```kotlin
class EffectClockTest {
    @Test fun `CD-FX-01 effects start at 0, 40 and 80 and never at 39`() {
        val plays = (0L..100L).filter { EffectClock.shouldPlay(0, it, 40) }
        assertEquals(listOf(0L, 40L, 80L), plays)
    }
}
class RecipientsTest {
    @Test fun `CD-FX-08 64_0 blocks receives and 64_1 does not`() {
        assertTrue(Recipients.inRange(64.0 * 64.0, 64))
        assertFalse(Recipients.inRange(64.1 * 64.1, 64))
    }
}
class ParticleCatalogTest {
    @Test fun `CD-FX-11 a missing particle id warns once and the rest still play`() {
        val missing = ResourceLocation.parse("cobblemon:watergun_spray")
        val present = ResourceLocation.parse("cobblemon:watergun_actor")
        val warnings = mutableListOf<String>()
        val checked = CheckedParticles({ it != missing }, warnings::add)
        repeat(3) { assertEquals(listOf(present), checked.filter(listOf(missing, present))) }
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("cobblemon:watergun_spray"))
    }
}
```

- [ ] **Step 2: Write the failing GameTests**

```kotlin
@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class FocusEntityTests {
    // cdFx04FocusSitsAtTargetCenter: while a Stoker is Working, exactly one WorkFocusEntity exists within 0.01 of the furnace center.
    // cdFx05FocusIsInvisibleWithNoCollision: isInvisible, !isPickable, !canBeCollidedWith; a mock player moved through the
    //   focus position with player.move(MoverType.SELF, ...) ends past it.
    // cdFx06FocusIsNeverSaved: with five Working workers, entity.type.canSerialize() is false and entity.save(CompoundTag()) is false.
    // cdFx07FocusLeavesWithWorking: one Stoker goes to Standby (fill its output), another's claim is released; 1 tick later
    //   no focus entity remains for either.
    // cdPerf04NoFocusLeak: create and release 10 claims 5 times (10 furnaces, 10 Charmanders, toggle Work Mode); on every
    //   tick the focus entity count is at most the WORK claim count.
}
```

- [ ] **Step 3: Write the effects code and wire it into engine step 6**

Register the entity type, its renderer (client only, `EntityRenderersEvent.RegisterRenderers`), and
`WorkSoundPayload` (Task 13 registers payloads; add this one there and use it here).

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`; every GameTest passes.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test src/gametest
git commit -m "Loop each role's move effect at a hidden focus entity on the target"
```

---

### Task 11: Work Leash

**Rows:** CD-LSH-01, CD-LSH-02, CD-LSH-03 (furnace version), CD-LSH-04, CD-LSH-05 (Fire-type version),
CD-LSH-06 (furnace version), CD-LSH-07, CD-LSH-08, CD-LSH-09, CD-LSH-10, CD-LSH-11 (furnace version),
CD-LSH-13, CD-LSH-14 (furnace version), CD-LSH-15, CD-LSH-16, CD-LSH-17, CD-LSH-18, CD-LSH-19, CD-LSH-20,
CD-LSH-21, CD-LSH-24, CD-LSH-25, CD-LSH-26, CD-LSH-27, CD-WMD-07, CD-SEC-07, CD-SEC-08, CD-SEC-11,
CD-CLM-05, CD-CLM-06, CD-ASG-20 (GameTest).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/WorkLeashItem.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashComponents.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/Binding.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/BindingStore.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/BindRules.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/PendingSource.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashInteractions.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashResets.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/claim/LeashIndex.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/Feedback.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsItems.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/recipe/work_leash.json`
- Create: `src/main/resources/assets/cobblemon_dynamics/lang/en_us.json`
- Create: `src/main/resources/assets/cobblemon_dynamics/models/item/work_leash.json`
- Create: `src/main/resources/assets/cobblemon_dynamics/textures/item/work_leash.png` (16 × 16 placeholder drawn by
  `scripts/placeholder_art.py`, CD-ART-11)
- Create: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/TestTransferHandler.kt` and the test role
  `src/gametest/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/test_transfer.json`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/leash/BindRulesTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/leash/PendingSourceTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/LeashTests.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/LeashResetTests.kt`

**Interfaces:**
- Produces:

```kotlin
data class LeashSelection(val pokemonId: UUID, val name: String)           // data component, synced, saved
data class PendingSource(val pos: BlockPos, val face: Direction, val clickedAt: Long) {
    fun expired(now: Long, ttl: Int): Boolean = now - clickedAt >= ttl     // CD-LSH-19: alive at 599, gone at 600
}
data class Binding(val roleId: ResourceLocation, val dimension: ResourceLocation, val pasture: BlockPos,
                   val target: BlockPos, val face: Direction, val binder: UUID,
                   val destination: BlockPos? = null, val destinationFace: Direction? = null)
object BindingStore {                                   // Pokemon.persistentData -> "cobblemon_dynamics" -> "binding"
    fun read(pokemon: Pokemon): Binding?
    fun write(pokemon: Pokemon, binding: Binding)       // then pokemon.onChange()
    fun clear(pokemon: Pokemon)
}
class LeashIndex(entries: MutableMap<Long, Ref>) {      // ServerLevel attachment cobblemon_dynamics:leash_index
    data class Ref(val pokemonId: UUID, val owner: UUID, val pasture: BlockPos, val roleId: ResourceLocation)
    fun get(target: BlockPos): Ref?; fun put(target: BlockPos, ref: Ref); fun remove(target: BlockPos); fun removePokemon(id: UUID)
}

object BindRules {
    sealed interface Outcome {
        data class Bind(val role: RoleDefinition, val canonical: BlockPos) : Outcome
        data class AwaitDestination(val role: RoleDefinition) : Outcome
        data class Refuse(val messageKey: String, val arg: String? = null) : Outcome
    }
    data class Input(
        val selection: LeashSelection?,                 // read from the server-side stack only (CD-SEC-08)
        val pokemonOwnedByPlayer: Boolean,
        val pastureInWorkMode: Boolean,                 // and the Pokémon is still tethered to it
        val fittingRoles: List<RoleDefinition>,         // roles eligible for the types whose handler matches the block
        val canonical: BlockPos,
        val insideWorkArea: Boolean,
        val boundToOther: String?,                      // display name of another Pokémon bound to the target
        val pending: PendingSource?,
        val sameAsPending: Boolean,
    )
    fun evaluate(input: Input, shapeOf: (RoleDefinition) -> BindingShape): Outcome
}
object Feedback {                                       // every message goes through here; tests read `recorded`
    fun actionBar(player: ServerPlayer, message: Component)
    fun chat(playerId: UUID, server: MinecraftServer, message: Component)
    val recorded: MutableMap<UUID, MutableList<Component>>     // filled only when `recording` is true (GameTests)
    var recording: Boolean
}
```

`BindRules.evaluate` checks, in this order: no selection, or not owned → `Refuse("cobblemon_dynamics.leash.no_selection")`
(CD-SEC-08); not in a Work Mode pasture → `"Not in a Work Mode pasture"`; no fitting role → `"No role fits"`;
then `RoleSelection.best` picks the role (CD-LSH-16). A `SOURCE_AND_DESTINATION` role with no pending
source returns `AwaitDestination` (CD-LSH-17). The work-area check applies to the single target or to
the source (`"Outside its pasture's work area"`). A target bound to another Pokémon refuses with
`"Already assigned to {Pokémon}"`. Otherwise `Bind`. Lang keys and values (`en_us.json`):

| Key | Value |
| --- | --- |
| `cobblemon_dynamics.leash.outside_area` | `Outside its pasture's work area` |
| `cobblemon_dynamics.leash.no_role` | `No role fits` |
| `cobblemon_dynamics.leash.already_assigned` | `Already assigned to %s` |
| `cobblemon_dynamics.leash.not_work_mode` | `Not in a Work Mode pasture` |
| `cobblemon_dynamics.leash.no_selection` | `Select a Pokémon first` |
| `cobblemon_dynamics.leash.bound` | `Bound %s to %s` |
| `cobblemon_dynamics.leash.select_destination` | `Select a destination for %s` |
| `cobblemon_dynamics.leash.destination_same` | `Destination must differ from the source` |
| `cobblemon_dynamics.leash.cannot_reach` | `%s cannot reach %s` |
| `cobblemon_dynamics.leash.selected` | `Selected %s` |
| `cobblemon_dynamics.leash.cleared` | `Cleared the binding of %s` |
| `cobblemon_dynamics.binding.lost` | `%s lost its work binding: %s` |
| `cobblemon_dynamics.binding.reason.target_gone` | `its target was broken or replaced` |
| `cobblemon_dynamics.binding.reason.left_pasture` | `it left its pasture` |
| `cobblemon_dynamics.binding.reason.other_pasture` | `it moved to another pasture` |
| `cobblemon_dynamics.binding.reason.released` | `it was released` |
| `cobblemon_dynamics.binding.reason.traded` | `it has a new owner` |
| `cobblemon_dynamics.binding.reason.no_role` | `its new form cannot do the job` |
| `cobblemon_dynamics.binding.reason.pasture_gone` | `its pasture block is gone` |
| `item.cobblemon_dynamics.work_leash` | `Work Leash` |

Interaction wiring:

- Entity clicks: `PlayerInteractEvent.EntityInteract` at `EventPriority.HIGH` on both sides. When the main
  hand holds a Work Leash and the target is a `PokemonEntity` whose owner is the player, cancel with
  `setCancellationResult(InteractionResult.SUCCESS)` on both sides (CD-LSH-02). On the server: a plain
  click selects the Pokémon if it is tethered to a pasture in Work Mode (CD-LSH-01) and shows
  `Selected {Pokémon}`; a sneak click clears its binding (CD-LSH-10). A Pokémon the player does not own,
  or a wild one, is never selected and the event is not cancelled (CD-SEC-07).
- Block clicks: `WorkLeashItem.onItemUseFirst` runs before the block's own interaction. On the server it
  evaluates `BindRules` from server state and returns `InteractionResult.SUCCESS` whenever a selection
  exists, so the block never opens its screen (CD-LSH-04). On the client it returns `SUCCESS` when the stack
  holds a selection. A `RightClickBlock` cancelled by another mod never reaches `onItemUseFirst`
  (CD-SEC-11).
- Right-click on air: `use()` clears the pending source (CD-LSH-19). `inventoryTick` clears an expired one.
- A successful bind writes the `Binding`, puts the target in `LeashIndex`, releases another worker's work
  claim on the target in the same tick (CD-CLM-05), replaces this Pokémon's old binding (CD-LSH-11),
  clears the selection and pending source, and shows `Bound {Pokémon} to {target}` (CD-LSH-06). The target
  name is `state.block.name`.
- `ClaimRegistry`'s `ReservationView` reads `LeashIndex` of the target's level (CD-CLM-06). A leashed
  worker claims its bound target on its next scan or poll when it is not Tired (CD-STA-14 in M2).
- A leashed worker that misses its arrival shows its owner `{Pokémon} cannot reach {target}` (CD-LSH-21).

`LeashResets` clears a binding, removes its index entry and, except for the owner's own sneak-click,
sends the binder `"{Pokémon} lost its work binding: {reason}"` in chat (CD-LSH-13) when:

| Cause | Detection | Row |
| --- | --- | --- |
| Target broken or replaced | every 10 ticks, `handler.stillValid` for index entries in loaded chunks | CD-LSH-08 |
| Pokémon recalled to the PC or the party | the 20-tick pasture poll no longer sees it tethered | CD-LSH-08 |
| Pokémon tethered to another pasture | the poll sees it under another pasture position | CD-LSH-24 |
| Pokémon released | `CobblemonEvents.POKEMON_RELEASED_EVENT_POST` | CD-LSH-25 |
| New owner | `CobblemonEvents.POKEMON_GAINED` with `playerId != binding.binder` | CD-LSH-26 |
| Pasture block gone | the poll or index check finds no pasture at `binding.pasture` | CD-WMD-07, CD-LSH-27 |
| Evolution leaves no fitting role | `CobblemonEvents.EVOLUTION_COMPLETE`, then the role check | CD-LSH-14 |

A target in an unloaded chunk keeps its binding (CD-LSH-09).

- [ ] **Step 1: Write the failing unit tests**

```kotlin
class PendingSourceTest {
    @Test fun `CD-LSH-19 a pending source lives 599 ticks and expires at 600`() {
        val p = PendingSource(BlockPos.ZERO, Direction.UP, clickedAt = 1_000)
        assertFalse(p.expired(1_599, 600))
        assertTrue(p.expired(1_600, 600))
    }
}

class BindRulesTest {
    private val stoker = role("stoker", setOf("fire"))
    private val transfer = role("test_transfer", setOf("psychic"), autoAssign = false)
    private val shape: (RoleDefinition) -> BindingShape = { if (it == transfer) BindingShape.SOURCE_AND_DESTINATION else BindingShape.SINGLE }
    private fun input(selection: LeashSelection? = LeashSelection(UUID.randomUUID(), "Charmander"), owned: Boolean = true,
                      workMode: Boolean = true, roles: List<RoleDefinition> = listOf(stoker), inside: Boolean = true,
                      boundTo: String? = null, pending: PendingSource? = null, same: Boolean = false) =
        BindRules.Input(selection, owned, workMode, roles, BlockPos.ZERO, inside, boundTo, pending, same)

    @Test fun `CD-SEC-08 no server-side selection never binds`() =
        assertInstanceOf(BindRules.Outcome.Refuse::class.java, BindRules.evaluate(input(selection = null), shape))
    @Test fun `CD-LSH-05 each broken rule refuses with its message`() {
        assertEquals("cobblemon_dynamics.leash.outside_area", (BindRules.evaluate(input(inside = false), shape) as BindRules.Outcome.Refuse).messageKey)
        assertEquals("cobblemon_dynamics.leash.no_role", (BindRules.evaluate(input(roles = emptyList()), shape) as BindRules.Outcome.Refuse).messageKey)
        val assigned = BindRules.evaluate(input(boundTo = "Pikachu"), shape) as BindRules.Outcome.Refuse
        assertEquals("cobblemon_dynamics.leash.already_assigned", assigned.messageKey); assertEquals("Pikachu", assigned.arg)
        assertEquals("cobblemon_dynamics.leash.not_work_mode", (BindRules.evaluate(input(workMode = false), shape) as BindRules.Outcome.Refuse).messageKey)
    }
    @Test fun `CD-LSH-03 every rule passing binds`() = assertInstanceOf(BindRules.Outcome.Bind::class.java, BindRules.evaluate(input(), shape))
    @Test fun `CD-LSH-17 a source-and-destination role waits for the destination`() =
        assertInstanceOf(BindRules.Outcome.AwaitDestination::class.java, BindRules.evaluate(input(roles = listOf(transfer)), shape))
    @Test fun `CD-LSH-18 the second click binds source and destination`() =
        assertInstanceOf(BindRules.Outcome.Bind::class.java,
            BindRules.evaluate(input(roles = listOf(transfer), pending = PendingSource(BlockPos(1, 0, 0), Direction.UP, 0)), shape))
}
```

- [ ] **Step 2: Write the failing GameTests**

`LeashTests` (each test sets `Feedback.recording = true` and reads `Feedback.recorded[player.uuid]`):

```kotlin
// cdLsh01SelectsOwnedPasturedPokemon: owner clicks a pastured Vaporeon (Work Mode on); the stack's LeashSelection holds its UUID.
// cdLsh02ClickIsCancelledBeforeCobblemon: plain and sneak clicks return SUCCESS from clickEntity and the Pokémon is not
//   on the player's shoulder and no interaction GUI packet is recorded (Cobblemon opens its wheel from mobInteract, which
//   never runs when the event is cancelled; assert mobInteract did not run with a test-only flag on the entity's
//   persistentData set by a NeoForge EntityInteract listener at LOWEST priority).
// cdLsh03BindsFurnace: select a Charmander, click a furnace in its work area; 1 tick later BindingStore.read is set.
// cdLsh04FurnaceScreenStaysShut: after the bind click, owner.containerMenu is still owner.inventoryMenu.
// cdLsh05RefusalsShowTheirMessage: a furnace at offset 9 (offset 8 binds), a stone block, a furnace bound to another
//   Charmander (its display name appears in the message), and a selection whose Charmander left the pasture.
// cdLsh06BoundMessage: the recorded action bar reads "Bound Charmander to Furnace".
// cdLsh07BindingSurvivesRestartAndToggle: bind, simulateRestart, Work Mode off then on; the binding is present after each step.
// cdLsh10SneakClickClears: sneak-click a bound Charmander; 1 tick later no binding.
// cdLsh11RebindReplaces: bind to furnace A, then furnace B; A is no longer reserved and the binding names B.
// cdLsh15RecipeCraftsOneLeash: CraftingInput of size 3x3 with a Lead and a cobblemon:poke_ball in two slots;
//   level.recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level) assembles one Work Leash.
// cdLsh17PendingSourceOnChest: select an Alakazam (test_transfer role), click a chest's top face; the stack's PendingSource
//   holds the chest position and UP, and no binding exists.
// cdLsh18SecondClickBindsDestination: then click a barrel's north face; the binding holds source, destination and both faces.
// cdLsh19AirClickClears: with a pending source, player.gameMode.useItem(...) on air clears it at once.
// cdLsh20SelectDestinationMessage: the recorded action bar reads "Select a destination for Alakazam".
// cdLsh21CannotReachMessage: a bound furnace boxed in glass; the owner's recorded action bar reads
//   "Charmander cannot reach Furnace" within 601 ticks of the claim.
// cdClm05BindTakesOverAutoClaim: auto worker A holds a furnace; bind worker B; 1 tick later A has no claim; 20 ticks later
//   B holds the work claim.
// cdClm06ReservedTargetStaysReserved: bound worker in Standby elsewhere (Work Mode off on its pasture), an Idle Charmander in a
//   second Work Mode pasture 3 blocks from the target for 400 ticks; the target gets no claim.
// cdAsg20LeashedWorkerStaysOnItsTarget: a leashed Stoker in Standby at an empty furnace, an unclaimed furnace with input 3
//   blocks away, 400 ticks; it never claims the second furnace and keeps its binding.
// cdSec07NotYourPokemon: player B clicks player A's pastured Pokémon and a wild Pokémon; neither is selected.
// cdSec11CancelledClickNeverBinds: a test listener cancels RightClickBlock on a chest; click it with a selected Alakazam;
//   no pending source and no binding.
```

`LeashResetTests`:

```kotlin
// cdLsh08RecallAndTargetLoss: four bound Charmanders: one unpastured to the PC (pasture.releasePokemon), one moved to the
//   party (releasePokemon then party.add), one whose furnace is destroyed, one whose furnace is replaced with stone;
//   20 ticks later none has a binding, and each binder has a recorded chat message with the name and the reason.
// cdLsh09UnloadKeepsBinding: EngineEvents.onChunkUnload for the target's chunk; 100 ticks later the binding remains and the
//   worker is Working again within 600 ticks.
// cdLsh13LossMessages: covered by cdLsh08 and cdLsh14 assertions on the chat text.
// cdLsh14EvolutionWithoutFittingRole: bind a Charmander, set pokemon.species to Squirtle's species (a form with no Fire
//   type) and fire the role re-check; 20 ticks later no binding.
// cdLsh24MovedToAnotherPasture: release from pasture A and tether to pasture B; 20 ticks later no binding and the old
//   target is not reserved.
// cdLsh25ReleasedPokemon: call Cobblemon's release path (party.remove plus the POKEMON_RELEASED_EVENT_POST post); 20 ticks
//   later the old target is not reserved.
// cdLsh26TradeClearsData: build a Pokémon whose persistentData holds a binding made by owner A; post POKEMON_GAINED for owner B;
//   the binding data is gone. (Unit-level, inside a GameTest so Cobblemon's event bus is live.)
// cdLsh27PastureBroken (also CD-WMD-07): bind three workers, destroy the pasture block; 20 ticks later no binding remains
//   and no target is reserved.
```

Write every test in full.

- [ ] **Step 3: Write the leash code, the recipe and the placeholder texture**

```json
// src/main/resources/data/cobblemon_dynamics/recipe/work_leash.json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    { "item": "minecraft:lead" },
    { "item": "cobblemon:poke_ball" }
  ],
  "result": { "id": "cobblemon_dynamics:work_leash", "count": 1 }
}
```

`scripts/placeholder_art.py` writes flat-color 16 × 16 PNGs with a 1-pixel darker border, from a table of
`(path, rgb)` pairs, with the standard library only (zlib and struct). Every placeholder carries the
comment "placeholder, replaced in M8" in the table. The Work Leash placeholder is brown with a red-and-white
2 × 2 corner.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`; every GameTest passes.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test src/gametest scripts/placeholder_art.py
git commit -m "Add the Work Leash: selection, bindings, refusals, resets and the recipe"
```

---

### Task 12: Operator command for claims

**Rows:** CD-UI-14 (work claims and bindings), CD-SEC-09.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/command/DynamicsCommands.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/CommandTests.kt`

**Interfaces:**
- Produces: `/cobblemondynamics claims`, registered in `RegisterCommandsEvent`, with
  `.requires { it.hasPermission(2) }` on the root literal. Each output line is one entry:
  `claim <kind> <target> <worker name> (<worker uuid>)` or `binding <target> <worker name> (<uuid>)`, where
  `<target>` is `dimension x y z` for blocks and the ally's name for entities. The lines come from lang
  keys `cobblemon_dynamics.command.claims.claim` and `cobblemon_dynamics.command.claims.binding`.

- [ ] **Step 1: Write the failing GameTests**

```kotlin
// cdUi14ClaimsListsEveryEntry: a Working Stoker (work claim) and a bound Charmander (binding); run the command through
//   server.commands.performPrefixedCommand(operatorSource, "cobblemondynamics claims") with a CommandSource that records
//   messages; the output holds one claim line and one binding line, each with the target and the worker name.
//   M2 Task 6 adds the bed and heal claim lines.
// cdSec09LevelZeroIsRefused: a level-0 mock player runs both "cobblemondynamics claims" and "cobblemondynamics stamina";
//   both fail (performPrefixedCommand returns 0 and the recorded output holds no claim line).
```

- [ ] **Step 2: Write the command, run the tests, see them pass, and commit**

Run: `./gradlew runGameTestServer`
Expected: PASS.

```bash
git add src/main src/gametest
git commit -m "Add the /cobblemondynamics claims operator command"
```

---

### Task 13: Payloads, milestone checks and the pull request

**Rows:** CD-SEC-05 and CD-SEC-06 (the payload path), CD-WMD-03 and CD-WMD-04 (the payload path).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/DynamicsPayloads.kt` (registration)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/WorkModeTogglePayload.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/PasturePanelPayloads.kt` (`open`, `close`, `state`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/LeashOverviewPayloads.kt` (`request`, `overview`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/PanelViewers.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/client/ClientState.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/net/PayloadCodecTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/PayloadTests.kt`

**Interfaces:**
- Produces: payload ids from ARCHITECTURE section 7. `WorkModeTogglePayload(enabled: Boolean)`;
  `PasturePanelStatePayload(workMode: Boolean, canToggle: Boolean, entries: List<PanelEntry>)` with
  `PanelEntry(pokemonId: UUID, percent: Int, state: WorkerStatus)`; `LeashOverviewPayload(entries: List<Pair<Int, BlockPos>>)`.
  `PanelViewers` maps a pasture key to the players with its panel open. The server handler of the toggle
  resolves the pasture with `PastureAdapter.linkedPasture(player)` and calls `WorkModeService.request`.
  Client handlers store the last state in `ClientState` (M5 draws it). Registration uses
  `RegisterPayloadHandlersEvent` with `registrar.playToServer` and `registrar.playToClient`. Every payload
  is required, so a client without the mod cannot join (CD-PLAT-06).

- [ ] **Step 1: Write the codec round-trip unit test** for each payload with a
  `RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)`; for `PasturePanelStatePayload` use two
  entries with different states. If `RegistryAccess.EMPTY` needs a bootstrap, use the payload's plain
  `StreamCodec<ByteBuf, ...>` form, which every payload here can use because none carries registry data.

- [ ] **Step 2: Write the GameTests**

```kotlin
// cdSec05DuplicateOnPayloadStaysOn: handle WorkModeTogglePayload(true) twice, 10 ticks apart, for the linked placer -> on.
// cdSec06LastPayloadWinsAndViewersSeeIt: two operators linked to the pasture send on, then off, in one tick; Work Mode is
//   off and PanelViewers' recorded last state for each viewer says off.
```

- [ ] **Step 3: Run everything**

Run: `./gradlew build runGameTestServer && py scripts/check_plan_coverage.py`
Expected: `BUILD SUCCESSFUL`, every GameTest passes, `missing: 0`.

- [ ] **Step 4: Run the test pack**

Run: `./gradlew build && py scripts/testpack/testpack.py smoke` and add a `reload` scenario to the script:
start the server with the M1 jar, run `cobblemondynamics claims` from the console over RCON (it must answer),
run `reload`, and check that the log holds no `Skipping role file` line.
Expected: `SMOKE PASS`, `RELOAD PASS`. Pasturing a Pokémon needs a player, so worker scenarios stay in the
GameTests; the approver's play-test covers them in a real client.

- [ ] **Step 5: Commit, open the pull request, resolve reviews, merge**

Use `/commit`, then dispatch `pr-runner` (base `main`). The pull request body lists every row of Tasks 1 to
13, the rows whose final part lands in M3 or M4 (index table), and the CD-STK-07 note from Task 8.
