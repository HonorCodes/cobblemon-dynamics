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
fun operator(helper: GameTestHelper): ServerPlayer                      // FakePlayer whose getPermissionLevel() is 2
fun standBeside(player: ServerPlayer, pasture: PokemonPastureBlockEntity) // 2 blocks east of the pasture block
fun place(player: ServerPlayer, x: Double, y: Double, z: Double)        // player.moveTo
fun leash(player: ServerPlayer): ItemStack                              // puts a Work Leash in the main hand
fun clickEntity(player: ServerPlayer, entity: Entity, sneak: Boolean = false): InteractionResult   // player.interactOn
fun clickBlock(helper: GameTestHelper, player: ServerPlayer, rel: BlockPos, face: Direction = Direction.UP): InteractionResult
    // player.gameMode.useItemOn(player, level, stack, MAIN_HAND, BlockHitResult(center, face, abs, false))
fun simulateRestart(helper: GameTestHelper)                              // WorkEngine.resetRuntimeForTest()
```

`clickEntity` and `clickBlock` go through vanilla's interaction pipeline, so NeoForge's events, Cobblemon's
handlers and `Item#onItemUseFirst` all run as they do for a real click.

Every test player is a NeoForge `FakePlayer`. Two facts follow. The GameTest server returns 0 from
`getOperatorUserPermissionLevel()`, so `PlayerList.op(profile)` gives level 0; `operator` is a `FakePlayer`
subclass that overrides `getPermissionLevel()` to return 2. NeoForge's fake connection drops every teleport,
so `ServerPlayer.teleportTo` does not move a fake player; move it with `TestKit.place` (`moveTo`).

---

### Task 7: Pasture adapter and Work Mode

**Rows:** CD-WMD-01, CD-WMD-02, CD-WMD-03, CD-WMD-04, CD-WMD-05, CD-SEC-01, CD-SEC-02,
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
    // level is the pasture's level; a test names it to send a request from another dimension (CD-SEC-04)
    fun request(player: ServerPlayer, pasturePos: BlockPos, enabled: Boolean, level: ServerLevel = player.serverLevel()): WorkModePolicy.Decision
    fun apply(level: ServerLevel, pasturePos: BlockPos, enabled: Boolean)   // no checks; tests and request() use it
    fun reset()                                                             // ServerStoppedEvent: forget every lastAccepted
}
```

`WorkModePolicy.decide` order: an operator (permission level 2 or higher) passes the owner check; a
non-operator passes only when `placer != null && placer == requester` (CD-SEC-01, CD-SEC-02,
CD-WMD-03, CD-WMD-04). Then distance: `!sameDimension || distance > 10.0` rejects (CD-SEC-04). Then
rate: `lastAccepted != null && now - lastAccepted < cooldownTicks` ignores (CD-SEC-03). Otherwise
`Apply`. The requested state is applied as given, never flipped (CD-SEC-05). `request` measures the
distance from the player's position to the pasture block center. It keeps `lastAccepted` per player in
memory, on the level's game time, and clears it on `ServerStoppedEvent`, because the next server runs on
another clock. It applies decisions in arrival order (CD-SEC-06), and after an `Apply` sends the new
`pasture_panel_state` to every player who has that pasture's panel open (Task 13 adds the sender).
`sameDimension` is `player.level() === level`.

`apply(level, pos, enabled)` sets the attachment with `be.setData(WORK_MODE, WorkModeData(enabled))`, adds
or removes `pos` in the level's `WORK_PASTURES`, and (from Task 8, when the engine exists) calls
`WorkEngine.get()?.onWorkModeChanged(level, pos, enabled)`.
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
Expected: first FAIL (unresolved reference), then PASS (6 tests).

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
        TestKit.standBeside(owner, pasture)
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
    // cdSec04DistanceAndDimension: the owner at 10.1 blocks (TestKit.place), then a fake player made in the Nether with
    //   the owner's profile (FakePlayerFactory.get(nether, owner.gameProfile)), whose request names the overworld pasture
    //   (request(..., level = helper.level)), then the owner at 10.0 blocks; only the 10.0 request applies.
    // cdSec05RepeatedOnStaysOn: two "on" requests 10 ticks apart; the second returns Apply -> on.
    // cdSec06TwoOperatorsSameTick: operator A requests on and operator B requests off in the same tick -> off.

    // maxPerPlayer = Int.MAX_VALUE, so only the pasture's own limit of 16 can refuse; the 16th is still accepted.
    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdBal06SeventeenthPokemonIsRefused(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        WorkModeService.apply(helper.level, pasture.blockPos, true)
        repeat(15) { TestKit.tether(helper, pasture, owner, "rattata level=5") }
        helper.assertTrue(pasture.canAddPokemon(owner, TestKit.pokemon("rattata level=5"), Int.MAX_VALUE), "Cobblemon refused a 16th Pokémon")
        TestKit.tether(helper, pasture, owner, "rattata level=5")
        helper.assertFalse(pasture.canAddPokemon(owner, TestKit.pokemon("rattata level=5"), Int.MAX_VALUE), "Cobblemon accepted a 17th Pokémon")
        helper.succeed()
    }
}
```

Write the six commented tests in full in the same style before Step 4. `CD-WMD-05`, `CD-WMD-06` and
`CD-ASG-15` need workers and `TestKit.claimOf`, which reads the engine, so their GameTests live in Task 8.

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

Register `DynamicsAttachments.REGISTER.register(modBus)` in the mod constructor, and
`NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { WorkModeService.reset() }`. `WorkPastureIndex.CODEC`
is `Codec.LONG.listOf().xmap({ WorkPastureIndex(it.toHashSet()) }, { it.all().toList() })`. After every
mutation of the index, call `level.setData(WORK_PASTURES, index)` again so the level saves it. Write the
suppliers as `Supplier { ... }`: ktlint drops the `->` of a zero-argument lambda, and `AttachmentType.builder`
also takes a `Function<IAttachmentHolder, T>`. `WORK_MODE` serializes only when `enabled` is true, so the
pastures that were never in Work Mode carry no attachment.

- [ ] **Step 5: Run the GameTests**

Run: `./gradlew runGameTestServer`
Expected: exit 0; all `WorkModeTests` pass beside the M0 smoke tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/codes/honor/cobblemondynamics/registry src/main/kotlin/codes/honor/cobblemondynamics/pasture src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt src/test/kotlin/codes/honor/cobblemondynamics/pasture src/gametest
git commit -m "Store Work Mode on the pasture and accept toggles from the placer and operators"
```

---

### Task 8: Engine runtime and the Stoker

**Rows:** CD-STK-01, CD-STK-02, CD-STK-03, CD-STK-04, CD-STK-05, CD-STK-06, CD-STK-07, CD-ASG-01 (furnace
version), CD-ASG-03, CD-ASG-08, CD-ASG-11, CD-ASG-12, CD-ASG-13, CD-ASG-15, CD-WMD-05, CD-WMD-06, CD-CLM-07,
CD-CLM-08, CD-CFG-05, CD-CFG-07, CD-CFG-08, CD-PERF-03.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/EngineEvents.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/PastureRuntime.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/TargetScan.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/LevelSpotGrid.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/vanilla/FurnaceHandler.kt`
- Modify: `src/main/resources/META-INF/accesstransformer.cfg`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/Worker.kt` (`target`, `spot`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/role/RoleRegistry.kt` (`applyForTest`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/pasture/WorkModeService.kt` (calls the engine)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/TargetScanTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/StokerTests.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/AssignmentTests.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/RoleDataTests.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/WorkModeTests.kt` (`cdWmd06`), `TestKit.kt`

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
    fun release(pokemonId: UUID)                                         // the release path: onStop if Working, then Idle
    fun resetRuntimeForTest()
    companion object {
        fun start(server: MinecraftServer)     // ServerStartingEvent
        fun stop()                             // ServerStoppedEvent
        fun get(): WorkEngine?
        fun require(server: MinecraftServer): WorkEngine
        fun isArrived(entity: Entity, spot: BlockPos): Boolean   // the step 5 rule; GameTests use it too
    }
}
// Worker gains `var target: BlockPos?` (the canonical target) and `var spot: BlockPos?`, set while it holds a work claim.
// PastureRuntime(level, key, now, scanIntervalTicks) holds one pasture's slots (Worker, entity, Pokemon, ActiveRole?),
// its ScanScheduler and its running TargetScan.

/** Pure part of a scan pass, so CD-PERF-03 is a unit test. */
interface ScanWorld {
    fun blockEntitiesIn(area: WorkArea): Sequence<Pair<BlockPos, BlockEntity>>
    fun blockState(x: Int, y: Int, z: Int): BlockState
    // TargetHandler.matches and canonical take a ServerLevel, so the world makes those calls for the scan.
    fun match(handler: TargetHandler, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): BlockPos?
}
class TargetScan(area: WorkArea, roles: List<ActiveRole>, workersNeedBlockScan: Boolean, budget: Int) {
    fun startPass(world: ScanWorld)                 // block entities, in one tick
    fun stepBlocks(world: ScanWorld): Boolean       // true when the pass is complete
    val found: List<FoundTarget>
}
data class FoundTarget(val pos: BlockPos, val roles: List<ActiveRole>)
```

Per `LevelTickEvent.Post` on a `ServerLevel`, `tickLevel` runs these steps for that level, in order:

0. Every position in the level's `WORK_PASTURES` gets a `PastureRuntime`, so a restart rebuilds them from the
   saved index; a runtime whose position left the index is removed. Steps 1 and 3 skip a pasture whose chunk
   is not loaded, and nothing in the engine loads a chunk.
1. Pastures due for their 20-tick poll (staggered by `ScanScheduler.phaseFor(pos.asLong(), 20)`; a new
   runtime polls on its first tick): read the
   tethered list through `PastureAdapter.tethered`, add new workers, and release and forget workers that
   left (CD-ASG-15). A worker counts only when its Cobblemon is player-owned, not fainted, not a battle
   clone, its entity is in this level, and `tethering.playerId == pokemon.getOwnerUUID()` (CD-ASG-01).
   A pasture whose block is gone, or whose attachment reads off, leaves the index.
2. Claimed targets: `handler.stillValid(level, pos)` for every work claim in this level, after
   `level.isLoaded(pos)`. An invalid target releases its claim in the same tick (CD-CLM-08). The same
   step releases a work claim whose role is gone from `RoleRegistry.current` or no longer takes the worker
   (a reload, a disabled role or handler), and a work claim that no worker of the engine holds.
3. Scans: each Work Mode pasture whose `ScanScheduler` is due starts a `TargetScan`, with the auto-assign
   roles that a worker of the pasture may take (not leashed, not Tired, no Fairy type). Only a pasture
   with a worker eligible for a `ScanKind.BLOCK` role iterates block positions (CD-PERF-03). A scheduler's
   first start is the pasture's stagger boundary, `phaseFor(pos.asLong(), interval)`.
4. When a scan completes: every Idle worker gets `Assigner.pickForIdle`, and every auto worker in Standby
   for `standbyMoveTicks` gets `Assigner.pickForStandbyMove`. The engine finds the work spot with
   `WorkSpots.find` over `LevelSpotGrid` before it claims; with no spot it claims nothing and skips the
   target for `retryWaitTicks`. A spot that another worker of the level holds is not standable, so two
   workers never share a cell.
5. Every worker with a claim: `arrived` is `Arrival.isAt` (corrected in M3 Task 5): on each of x and z at most
   `max(0.75, entity.bbWidth / 2)` from `spot + (int)(bbWidth + 1) / 2`, the point that navigation steers the mob to
   (`Path.getEntityPosAtNode`; the spot's center for a mob narrower than a block, its corner for one 1 to 2 blocks wide),
   with `|dy| < 1.5`. The first rule, a distance to the spot's center, left a Dragonair 1.1 blocks wide short of its
   spot on about 4 approaches in 10, until its travel timed out. Run `WorkerMachine.onTick`. On entering Working call
   `handler.onStart`, snap the entity to the spot center, and on leaving Working call `handler.onStop`.
   While Working call `handler.applyWork` every tick. While Traveling, set the Brain's `WALK_TARGET` to the
   spot's bottom center (speed 0.4, close enough 0) every tick; Cobblemon's core `follow_walk_target` task
   walks the Cobblemon there, and wandering pauses while a walk target exists. Task 9 replaces this interim
   write with `BrainBridge.write`. A worker whose entity is removed loses its claim and its slot, and joins
   again at the next poll. A worker whose claim another caller released goes to Idle in the same tick.
6. Task 9 writes the Brain memory here, and Task 10 runs the effects here.

A handler call that throws turns the handler off through `RoleRegistry.disableHandler` (one warning,
CD-PERF-05), and the next tick releases the claims of its roles.

`EngineEvents` subscribes `ServerStartingEvent`, `ServerStoppedEvent` (which also runs
`WorkModeService.reset()`), `LevelTickEvent.Post` and `ChunkEvent.Unload` on `NeoForge.EVENT_BUS`.
`onChunkUnload` releases every claim whose target sits in that chunk (CD-CLM-08).

`FurnaceHandler` (`id = cobblemon_dynamics:furnace`, `scanKind = BLOCK_ENTITY`):

- `matches`: `blockEntity is AbstractFurnaceBlockEntity` (CD-STK-01).
- `hasDemand`: the input slot holds an item, `quickCheck` finds a recipe, and the result fits the output
  slot. This repeats vanilla's `canBurn` check in Kotlin, so no access transformer on the patched private
  `canBurn` is needed (CD-STK-05).
- `applyWork`: while `hasDemand`, when `litTime < 2`, set `litTime = 2` and `litDuration = 2`; a furnace that
  burns its own fuel keeps its own values. If the block
  state has `LIT = false`, set it to true with flag 3 (CD-STK-04). Call `setChanged()`. No fuel item is
  touched, because vanilla only burns fuel when its tick brings `litTime` to 0, and the engine works after
  the block entities tick (CD-STK-02, CD-STK-03).
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
        override fun match(handler: TargetHandler, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): BlockPos? =
            error("not used in this test")
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
            if (workingSince < 0 && w != null && TestKit.engine(helper).worker(w.worker)?.status == WorkerStatus.WORKING) workingSince = helper.tick
            if (furnace.getItem(2).count == 8) {
                val took = helper.tick - workingSince
                helper.assertTrue(took in 1_580..1_620, "8 smelts took $took ticks")
                helper.succeed()
            }
        }
    }
    // cdStk02SmokerCooksEightBeefIn800Ticks: same with Blocks.SMOKER and 8 beef, 780..820 ticks.
    // cdStk03CoalIsNeverConsumed: 1 coal goes into the fuel slot on the tick Working starts (vanilla burns a coal that sits
    //   beside input in an unlit furnace before any Stoker arrives); after 8 smelts the fuel slot still holds 1 coal.
    // cdStk04LitIsSetOneTickAfterWorkingStarts: record the tick Working starts; on the next tick the block state's LIT is true.
    // cdStk05FullOutputGivesStandby: output slot set to 64 iron ingots; the worker is in STANDBY within 20 ticks of reaching the spot.
    // cdStk06 is two tests. Once Working, set the worker's skipUntil for the furnace 200 ticks ahead, so that the next scan
    //   cannot claim it again, and call TestKit.engine(helper).release(id). cdStk06ReleaseLetsAFurnaceWithNoFuelGoOut: LIT is
    //   false within 2 ticks. cdStk06ReleaseLetsVanillaBurnTheCoal: 1 coal goes in at Working and is still there 40 ticks
    //   later; after the release the coal is burned within 2 ticks, and the furnace stays lit.
    // cdStk07GaugeIsNotEmptyWhileCooking: while Working, litTime > 0, litDuration > 0 and litTime / litDuration >= 0.5, the
    //   ratio that the menu draws (the fields, through the access transformer; dataAccess is protected).
}
```

```kotlin
@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class AssignmentTests {
    // cdAsg01OnlyOwnedHealthyPastured: a wild Charmander (TestKit.wild), a party Charmander sent out by the owner
    //   (pokemon.sendOut(level, pos, null)), a pastured Charmander with currentHealth = 0, and a healthy pastured
    //   Charmander, all 3 blocks from a furnace with input, for 400 ticks; only the healthy pastured one holds a claim.
    // cdAsg03NearestFurnaceWins: furnaces with input 3 and 6 blocks from one Idle Charmander, on opposite sides; moveTo holds
    //   the Charmander at its spot every tick until it claims, because it wanders between the poll and the scan; the claim
    //   is on the 3-block furnace.
    // cdAsg08 is two tests, each with two Charmanders, so a free worker exists for the outer furnace. Horizontal
    //   (floor_23x10x23, pasture at (11,1,11)): furnaces at x offsets +8 and -9. Vertical (floor_23x17x23): furnaces at
    //   y offsets 6 and 7, each on a stone pillar with a one-block stone ledge beside it as its only work spot. Over 300
    //   ticks the 8 and 6 furnaces get claimed, and the 9 and 7 never do.
    // cdAsg11WorkingWithinOneTickOfArrival: track the first tick WorkEngine.isArrived(entity, worker.spot); status is WORKING
    //   on that tick or the next.
    // cdAsg12StandbyKeepsClaimAndResumes: remove the input once Working; the claim stays; put input back; WORKING within 20 ticks.
    // cdAsg13UnreachableTargetTimesOut: moveTo holds the Charmander 9 blocks from the furnace, beyond the Stoker's range, until
    //   it claims, so its work spot lies some blocks away; then glass walls the Charmander in, two blocks high, so it can
    //   never arrive; the claim is held at claim.sinceTick + 599 and gone at + 601 (level game time). A glass box around the
    //   furnace with a gap closed after the claim does not work: the spot found through the gap stays reachable outside the
    //   box, and the worker arrives.
    // cdAsg15LeavingThePastureReleasesTheClaim: once Working, pasture.releasePokemon(uuid); no claim 20 ticks later.
    // cdWmd05WorkModeOffReleasesEveryClaim: two Working Stokers; inject a BED and a HEAL claim for two more workers of the
    //   same pasture with engine.claims.tryClaim; apply Work Mode off; 1 tick later the registry holds none of the four.
    //   M2 Task 4 replaces the injected claims with a real sleeper and a real healer.
    // cdClm07ClaimsAreNotSaved (batch "restart_clm07", because the reset drops the whole engine): two Working Stokers;
    //   TestKit.simulateRestart(helper); the registry is empty, and for 20 ticks every claim is newer than the restart.
    // cdClm08BrokenReplacedUnloaded (batch "unload_clm08", because one chunk can hold parts of two test structures): three
    //   Working Stokers; break furnace 1 (destroyBlock), replace furnace 2 with stone, call EngineEvents.onChunkUnload for
    //   furnace 3's chunk; 1 tick later none of the three claims remains (compare the Claim values, because a new scan
    //   may claim furnace 3 again: its chunk stays loaded).
}

@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class RoleDataTests {
    // Each test restores the state with TestKit.afterTest(helper) { ... }, a GameTestListener on helper.testInfo that runs
    // when the test passes or fails; a finally block would run when the test method returns, before the test ticks.
    // batch = "roles_cfg07": cdCfg07DatapackLetsWaterTypesStoke: RoleRegistry.applyForTest(stoker with types fire + water);
    //   a Squirtle claims a furnace with input; restore the parsed map.
    // batch = "roles_cfg08": cdCfg08ReloadAppliesChangedTypes: applyForTest(stoker with types water only); two furnaces; a
    //   Charmander gets no claim for 200 ticks, and a Squirtle holds one at tick 200; restore.
    // batch = "roles_cfg05": cdCfg05DisabledRoleGetsNoClaims: WorkEngineSettings.current = current.copy(disabledRoles =
    //   setOf(stoker id)), RoleRegistry.rebuild(); a Charmander beside a furnace with input gets no claim for 400 ticks; restore.
}
```

Every test reads only its own workers and targets: the registry and the engine are global, and the tests of one
batch run side by side.

Add to `WorkModeTests` (moved from Task 7, because `TestKit.claimOf` reads the engine):

```kotlin
@GameTest(template = "floor_15x6x15", timeoutTicks = 450)
fun cdWmd06OffPastureGivesNoClaims(helper: GameTestHelper) {
    val owner = TestKit.owner(helper)
    val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
    TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
    val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
    TestKit.moveTo(helper, fire, BlockPos(7, 1, 5))
    helper.onEachTick { helper.assertTrue(TestKit.claimOf(helper, fire) == null, "a Pokémon in a pasture that is off got a claim") }
    helper.runAtTickTime(400) { helper.succeed() }
}
```

`TestKit` gets `engine`, `furnace`, `workerOf`, `claimOf`, `keyOf(helper, pos)`, `moveTo`, `simulateRestart` and
`afterTest`.

Write every commented test in full before Step 3. `RoleRegistry.applyForTest(definitions)` replaces the
parsed map and rebuilds `current`, exactly as `apply` does after a `/reload`, so CD-CFG-07 and CD-CFG-08
exercise the reload path. The test pack's `reload` scenario (M5 Task 6) repeats CD-CFG-08 with a real
data pack and a real `/reload`.

- [ ] **Step 3: Run them and see them fail**

Run: `./gradlew runGameTestServer`
Expected: the new tests fail (no engine, no handler), the M0 and Task 7 tests pass. The tests compile against
a `WorkEngine` with the API above and empty bodies. The two MUST NOT tests, `cdWmd06` and `cdCfg05`, pass
already, because nothing claims.

- [ ] **Step 4: Write the engine, the scan, the spot grid and the handler**

`LevelSpotGrid(level, handler, canonical, taken)` implements `SpotGrid`: standable when the block below has a
full top face (`isFaceSturdy(level, below, Direction.UP)`), the cells at `y` and `y + 1` have empty
collision shapes, and the cell is not in `taken` (the spots of other workers); a cell blocks sight when its
collision shape is not empty and `handler.isPartOfTarget(level, canonical, pos)` is false. A cell in an
unloaded chunk is neither standable nor in the way.

Register `FurnaceHandler` in the mod constructor with `HandlerRegistry.register(FurnaceHandler)`, and call
`EngineEvents.register()` there. `WorkModeService.apply` calls `WorkEngine.get()?.onWorkModeChanged(...)`.

- [ ] **Step 5: Run the tests and see them pass**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`; every GameTest passes.

- [ ] **Step 6: Commit**

```bash
git add -- <each new and changed file under src/main, src/test and src/gametest> docs/plans/2026-11-09-phase-1-m1-engine-leash-stoker-part2.md
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
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/ai/LookMath.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/behaviours/pokemon/auto/work.json`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (step 6, the arrival snap, the release path)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/ai/LookMathTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/BrainTests.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/TestKit.kt` (`release`, `activitiesOf`, `tasksOf`,
  `runningTasks`), `StokerTests.kt` (uses `TestKit.release`)

**Interfaces:**
- Produces:

```kotlin
object DynamicsActivities { val WORK: DeferredHolder<Activity, Activity> }                  // cobblemon_dynamics:work
object DynamicsMemories { val WORK_ORDER: DeferredHolder<MemoryModuleType<*>, MemoryModuleType<WorkOrder>> }  // no codec: never saved
data class WorkOrder(val spot: Vec3, val lookAt: Vec3?, val hold: Boolean, val sleep: Boolean = false)
object BrainBridge {
    fun registerTaskTypes()                                   // adds the three ids to TaskConfig.types, in common setup
    fun write(entity: PokemonEntity, order: WorkOrder)        // brain.setMemory every tick (CD-AI-04); enterWork when WORK is not active
    fun clear(entity: PokemonEntity)                          // brain.eraseMemory
    fun isWorking(entity: PokemonEntity): Boolean             // brain.isActive(WORK)
    fun holdsOrder(entity: PokemonEntity): Boolean            // the memory has a value
    fun canHoldOrder(entity: PokemonEntity): Boolean          // the memory is registered; false for a species with baseAI
    fun enterWork(entity: PokemonEntity)                      // setActiveActivityIfPossible(WORK), then brain.stopAll
    fun face(entity: LivingEntity, target: Vec3)              // yRot = yBodyRot = yHeadRot = LookMath.yawTowards(position, target)
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
`BrainBridge.enterWork` when WORK is not active. `SwitchFromWorkTaskConfig` follows
`SwitchFromActionEffectTaskConfig`: `absent(WORK_ORDER)` triggers `updateActivityFromSchedule(dayTime,
gameTime)`, then `useDefaultActivity()` if WORK is still active, because the schedule update runs at most
once in 20 ticks, and sets `enablePoseTypeRecalculation = true` (CD-AI-03, and CD-AI-08 in M2).
`WorkTaskConfig` builds two tasks, which run while `present(WORK_ORDER)` and never time out.
`WorkMoveTask`: when `hold` is false, sets `WALK_TARGET` to the spot (speed 0.4, close enough 0); when
`hold` is true, erases `WALK_TARGET`, sets `PATH_COOLDOWN` for 40 ticks and stops the navigation
(CD-AI-02). `WorkLookTask`: sets `LOOK_TARGET` to a `BlockPosTracker` at `lookAt`, and while `hold` is true
turns the body with `BrainBridge.face` (CD-AI-05, CD-AI-09). The look task is a task of its own, so the work
activity's task list holds it. The body does not turn while the Cobblemon walks: `PokemonMoveControl` moves
the Cobblemon along its `yRot`, so a forced yaw would push it off its path. A task that stops erases the
`WALK_TARGET` or `LOOK_TARGET` that it set. The engine sets `hold = true` once the worker has arrived.

Platform facts that shape the bridge (Minecraft 1.21.1, Cobblemon 1.8.4):

- `Activity.equals` compares names, and vanilla's villagers have an activity named `work`. `WORK` is
  `Activity("cobblemon_dynamics:work")`, so it never equals vanilla's.
- `Brain.setActiveActivityIfPossible` falls back to `useDefaultActivity()` when the Brain lacks the
  activity. `write` calls `enterWork` only when the Brain holds the work order, and the same behaviour file
  adds the memory and the activity.
- A Brain does not stop the running tasks of the activity that it leaves. Cobblemon's idle look task
  (`LookAtTargetSink`, 80 to 160 ticks) would keep running inside the work activity, so `enterWork` calls
  `brain.stopAll(...)`. Core tasks and ours start again on their own memories.
- The Brain ticks inside the entity tick, before `LevelTickEvent.Post`, and GameTest callbacks run after
  every level has ticked. With only the core task, a rebuilt Brain would get the work activity back one
  tick late, so `write` calls `enterWork` itself when WORK is not active (CD-AI-04).
- Cobblemon builds a Brain with only the memories that its task configs declare, so `WorkTaskConfig`
  declares `WORK_ORDER`, `WALK_TARGET`, `LOOK_TARGET` and `PATH_COOLDOWN`.

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
    //   the brain's available activities contain DynamicsActivities.WORK (TestKit.activitiesOf reflects on the private
    //   activityRequirements keys). Also assert that the mod file holds
    //   "data/cobblemon_dynamics/behaviours/pokemon/auto/work.json" (IModFile.findResource) and that Cobblemon loaded
    //   it as an auto behaviour (CobblemonBehaviours.behaviours[cobblemon_dynamics:work] in autoPokemonBehaviours).
    // cdAi02WorkingWorkerHoldsItsSpot: once Working, record the spot; for 600 ticks the entity stays within 0.5 blocks.
    // cdAi03ReleaseRestoresActivitiesWithin20Ticks: release a Working worker's claim with TestKit.release, which keeps the
    //   next scan off the furnace for 200 ticks; 20 ticks later WORK is not active and Cobblemon's IDLE is.
    // cdAi04RemakeBrainRestoresWorkNextTick: call entity.remakeBrain() on a Working worker (the new brain must not have WORK);
    //   1 tick later WORK is active.
    // cdAi05FacesTheTarget: for 600 ticks while Working or Standby, |wrapDegrees(yBodyRot - yawTowards(target))| <= 15. The
    //   worker walks toward the furnace, so on the first Working tick the test turns it 180 degrees and the bridge must
    //   turn it back by the next tick.
    // cdAi06NonWorkersKeepCobblemonActivities: a wild Charmander and one in a pasture that is off, 1,200 ticks; WORK never active,
    //   although both brains list it.
    // cdAi09LookTaskRunsInsideWork: while WORK is active, Cobblemon's idle activity is not active, the work activity's task list
    //   holds a WorkLookTask, no LookAtTargetSink runs (TestKit.runningTasks), the look task runs from the tick after WORK
    //   starts, and cdAi05's check holds, with the same turn.
}
```

Write each in full. Timeouts: the row's tick count plus 200, and 200 more where the worker first travels to its
spot (cdAi02, cdAi05 and cdAi09: 1,000).

- [ ] **Step 3: Write the Brain bridge, register it, and wire the engine**

Register `DynamicsActivities` and `DynamicsMemories` DeferredRegisters on the mod bus. Call
`BrainBridge.registerTaskTypes()` from `FMLCommonSetupEvent` with `event.enqueueWork(Runnable { })`, before any
Cobblemon brain is built. In `WorkEngine` step 6, call `BrainBridge.write` for every worker with a work claim,
with `hold = arrived`, and `BrainBridge.clear` on release (the release path's `clear`, a changed entity at
the poll, and `resetRuntimeForTest`, because a real restart loses the memory too). If `entity.brain` does
not hold the `WORK_ORDER` memory after a write (a species with `baseAI`), log one warning per species, skip
the target for `retryWaitTicks` and release the claim. Scans and the assigner also skip a worker whose
Brain cannot hold the memory (`canHoldOrder`). On arrival, Traveling to Working or to Standby, the engine
snaps the worker to the spot center, zeroes its motion and faces it to the target, so the first tick at the
spot already faces the target. The look task holds it from the next Brain tick.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`; every GameTest passes, including Task 8's tests, which now move through the
work activity instead of the interim `WALK_TARGET` write of Task 8's step 5. Remove that write when
`BrainBridge.write` takes over.

- [ ] **Step 5: Commit**

```bash
git add -- <each new and changed file under src/main, src/test and src/gametest> docs/plans/2026-11-09-phase-1-m1-engine-leash-stoker-part2.md
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
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/DynamicsPayloads.kt` (registration; Task 13 adds its payloads here)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/client/ClientEffects.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt`, `engine/PastureRuntime.kt` (`Slot.focus`),
  `CobblemonDynamics.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/EffectClockTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/RecipientsTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/fx/ParticleCatalogTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/net/WorkSoundPayloadTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/EffectTests.kt`

**Interfaces:**
- Produces:

```kotlin
object EffectClock { fun shouldPlay(startTick: Long, now: Long, loopTicks: Int): Boolean }   // start, start + 40, ...
object Recipients { fun inRange(distanceSqr: Double, radius: Int): Boolean }                   // <= radius², CD-FX-08
fun interface ParticleCatalog { fun contains(id: ResourceLocation): Boolean }   // fun interface: the unit test passes a lambda
class CheckedParticles(private val catalog: ParticleCatalog, private val warn: (String) -> Unit) {
    fun filter(ids: List<ResourceLocation>): List<ResourceLocation>      // drops missing ids, warns once per id
}
object JarParticleCatalog : ParticleCatalog   // "identifier" of each .particle.json under the Cobblemon jar's assets/cobblemon/bedrock/particles
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
per actor particle (source = the worker, targeted entity = the focus entity), one
`SpawnSnowstormParticlePacket(id, focus.position())` per target particle, and, when the role names a sound, a
`WorkSoundPayload`. The client plays that sound only when `ClientConfig.workSounds` is true (CD-FX-09).
The jar has `PlayPosableAnimationPacket(entityId: Int, animation: Set<String>, expressions: List<String>)`:
the names go in as a `LinkedHashSet`, which keeps their order. The Stoker role names no sound, so no sound id
needs checking yet.

Play-test change (2026-11-10): an effect no longer plays every 40 ticks. `BedrockAnimations` (through
`JarAnimationCatalog`) reads Cobblemon's resolvers, posers and animation files from its jar and picks the
first name of the role's list that the worker's model has, with its `animation_length`. The animation
packet names only that animation. The next effect starts when the animation has ended and the role's
`idle_ticks` (default 40) have passed (`EffectClock.periodTicks`). A model with none of the names gets the
particles only, every `loop_ticks`. Work is silent: the animation is the first one with no sound, and the
`work_sound` payload, `ClientEffects.playWorkSound` and `ClientConfig.workSounds` are gone (CD-FX-09).

Platform facts that changed the plan: actor particles aim at the focus entity too, because Cobblemon's move
timelines send them with `targetLocators` and `flamethrower_actor` sets its direction from
`v.target_delta*`. Actor particles use the source locators `special`, `target`, `root`; the targeted locator is
`middle`, and the client falls back to the focus entity's position because the focus entity has no model.
Target particles go out as the position packet at the focus entity, because Cobblemon's client spawns an
entity-sourced particle at a locator of its source entity and uses the targeted entity only as the aim
(`SpawnSnowstormEntityParticleHandler`), so a target particle sent that way bursts on the worker.
`ParticleShots` holds this routing, and `ParticleShotsTest` checks it. Cobblemon's client loads only
`.particle.json` files, so the catalog reads only those. The catalog reads the jar through
`IModFile.findResource`; if it cannot find the folder, it warns once and passes every id, because the client
skips an unknown id with no error.

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

- [ ] **Step 2: Write the failing GameTest**

The approver's ruling of 2026-11-09 limits GameTests to code that the plan cannot trust against the real
platform. Integrated testing happens at the end of Phase 1. One GameTest stays, because it reads the real
Cobblemon jar:

```kotlin
@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)
class EffectTests {
    // cdFx11CatalogReadsCobblemonParticles: every actor and target particle id of the Stoker role is in JarParticleCatalog,
    //   and cobblemon:no_such_particle is not.
}
```

These GameTests are not written:

- CD-FX-04 (`cdFx04FocusSitsAtTargetCenter`: one focus entity within 0.01 of the furnace center while a Stoker works):
  GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-FX-05 (`cdFx05FocusIsInvisibleWithNoCollision`: the property checks, and a player moved with
  `player.move(MoverType.SELF, ...)` through a focus entity in open air, because the target center lies inside the block):
  GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-FX-06 (`cdFx06FocusIsNeverSaved`: with five Working workers, `type.canSerialize()` and `save(CompoundTag())` are false):
  GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-FX-07 (`cdFx07FocusLeavesWithWorking`: one Stoker to Standby by a full output, another released with
  `TestKit.release`; 1 tick later no focus entity remains): GameTest deferred (high confidence): verified in the
  end-of-phase pass.
- CD-PERF-04 (`cdPerf04NoFocusLeak`: 10 furnaces and 10 Charmanders, Work Mode on and off 5 times; on every tick the focus
  entity count is at most the work claim count): GameTest deferred (high confidence): verified in the end-of-phase pass.

- [ ] **Step 3: Write the effects code and wire it into engine step 6**

Register the entity type, its renderer (client only, `EntityRenderersEvent.RegisterRenderers`, behind
`FMLEnvironment.dist.isClient`), and `WorkSoundPayload` in `net/DynamicsPayloads.kt`, which Task 13 extends. The
client handler calls `ClientEffects`, which loads only when a client runs the handler. In step 6 a worker with a
work claim that is Working gets its focus entity (spawned once, again if it was removed) and
`EffectPlayer.tickWorking` with `workingSince = worker.statusSince`; every other worker loses its focus entity in
the same tick. The release path's `clear` and `resetRuntimeForTest` remove it too.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew build runGameTestServer`
Expected: `BUILD SUCCESSFUL`; every GameTest passes.

- [ ] **Step 5: Commit**

```bash
git add -- <each new and changed file under src/main, src/test and src/gametest> docs/plans/2026-11-09-phase-1-m1-engine-leash-stoker-part2.md
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
- Modify: `engine/WorkEngine.kt` (reservations, leashed claims, `onBound`, `onUnbound`), `engine/PastureRuntime.kt`
  (`Slot.binding`, `requestPoll`), `pasture/PastureAdapter.kt`, `registry/DynamicsAttachments.kt` (`leash_index`),
  `CobblemonDynamics.kt`, and in the test mod `DynamicsTestMod.kt` and `TestKit.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/leash/BindRulesTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/leash/PendingSourceTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/leash/BindingTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/leash/WorkLeashRecipeTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/claim/LeashIndexTest.kt`
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
    fun actionBar(playerId: UUID, server: MinecraftServer, message: Component)   // an owner who may be offline (CD-LSH-21)
    fun chat(playerId: UUID, server: MinecraftServer, message: Component)
    val recorded: MutableMap<UUID, MutableList<Component>>     // filled only when `recording` is true (GameTests)
    var recording: Boolean
}
```

`BindRules.evaluate` checks, in this order: no selection, or not owned → `Refuse("cobblemon_dynamics.leash.no_selection")`
(CD-SEC-08); not in a Work Mode pasture → `"Not in a Work Mode pasture"`; no fitting role → `"No role fits"`;
then `RoleSelection.best` picks the role (CD-LSH-16). A `SOURCE_AND_DESTINATION` role with no pending
source returns `AwaitDestination` (CD-LSH-17). The work-area check applies to the single target or to
the source (`"Outside its pasture's work area"`). A target bound to another Cobblemon refuses with
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
| `entity.cobblemon_dynamics.work_focus` | `Work Focus` |

Interaction wiring:

- Entity clicks: `PlayerInteractEvent.EntityInteract` at `EventPriority.HIGH` on both sides. When the main
  hand holds a Work Leash and the target is a `PokemonEntity` whose owner is the player, cancel with
  `setCancellationResult(InteractionResult.SUCCESS)` on both sides (CD-LSH-02). On the server: a plain
  click selects the Cobblemon if it is tethered to a pasture in Work Mode (CD-LSH-01) and shows
  `Selected {Pokémon}`; a sneak click clears its binding (CD-LSH-10). A Cobblemon the player does not own,
  or a wild one, is never selected and the event is not cancelled (CD-SEC-07).
- Block clicks: `WorkLeashItem.onItemUseFirst` runs before the block's own interaction. On the server it
  evaluates `BindRules` from server state and returns `InteractionResult.SUCCESS` whenever a selection
  exists, so the block never opens its screen (CD-LSH-04). On the client it returns `SUCCESS` when the stack
  holds a selection. A `RightClickBlock` cancelled by another mod never reaches `onItemUseFirst`
  (CD-SEC-11).
- Right-click on air: `use()` clears the pending source (CD-LSH-19). `inventoryTick` clears an expired one.
- A successful bind writes the `Binding`, puts the target in `LeashIndex`, releases another worker's work
  claim on the target in the same tick (CD-CLM-05), replaces this Cobblemon's old binding (CD-LSH-11),
  clears the selection and pending source, and shows `Bound {Pokémon} to {target}` (CD-LSH-06). The target
  name is `state.block.name`.
- `ClaimRegistry`'s `ReservationView` reads `LeashIndex` of the target's level (CD-CLM-06). A leashed
  worker claims its bound target on its next scan or poll when it is not Tired (CD-STA-14 in M2).
- A leashed worker that misses its arrival shows its owner `{Pokémon} cannot reach {target}` (CD-LSH-21).

Platform facts and choices that changed the plan:

- The server owner check reads the Cobblemon's store (`pokemon.getOwnerUUID()`), because a new entity copies
  its owner only on its first tick. The client reads the entity's synced `ownerUUID`. `Player.interactOn`
  posts the event and returns at once when it is cancelled, before `Entity.interact` (compiled class).
- `LeashSelection` stays `(pokemonId, name)`. At a block click the server finds the Cobblemon in the player's
  PC or party, and its pasture by the level's `work_pastures` index: a pasture in Work Mode whose tethered
  list holds the Cobblemon's current tethering. A refusal with `no_selection` removes the stale selection.
- Both the selection and the pending source are data components on the stack, saved and synced (the spec
  rows test the stack). ARCHITECTURE's in-memory `PendingSelections` is gone.
- With a pending source, only `SOURCE_AND_DESTINATION` roles whose handler matches both the source and the
  clicked block fit; the work-area and assignment checks are the source's. The first click refuses a source
  outside the work area or bound to another Cobblemon, so a refusal never waits for the second click.
- The leash acts in the main hand only, for block clicks too. With no selection the item passes, so the
  block opens as usual. The tooltip names the selected Cobblemon, and the item is in the Tools tab.
- A bind calls `WorkEngine.onBound`: another worker's work claim on the target ends in the same tick
  (CD-CLM-05), and the pasture polls on the next engine tick. Each poll reads the binding into the slot and
  sets `Worker.leashed`; a leashed worker with no claim then claims its bound target and no other. A scan
  never assigns a leashed worker (CD-ASG-20). A leashed worker that finds no work spot gets the
  `cannot_reach` message and waits `retryWaitTicks`.
- `LeashIndex` is a saved level attachment, so a reservation holds after a restart, also while its pasture
  is off (CD-CLM-06).
- `ServerPlayerGameMode.useItemOn` returns the cancellation result of `RightClickBlock` before it calls
  `onItemUseFirst` (compiled class), so CD-SEC-11 holds with no code of ours.

`LeashResets` clears a binding, removes its index entry and, except for the owner's own sneak-click,
sends the binder `"{Pokémon} lost its work binding: {reason}"` in chat (CD-LSH-13) when the causes below
apply. Cobblemon has no pasture events, so one index check every 10 ticks covers every cause that the
events do not. It reads each entry whose pasture or target chunk is loaded, also for a pasture in Work
Mode off:

| Cause | Detection | Row |
| --- | --- | --- |
| Target broken or replaced | the index check: `handler.stillValid` false on a loaded target | CD-LSH-08 |
| Cobblemon recalled to the PC or the party | the index check: the loaded pasture's tethered list lacks it, and it is untethered | CD-LSH-08 |
| Cobblemon tethered to another pasture | the index check: the old pasture lacks it, and it is tethered | CD-LSH-24 |
| Cobblemon released | `CobblemonEvents.POKEMON_RELEASED_EVENT_POST` | CD-LSH-25 |
| New owner | `CobblemonEvents.POKEMON_GAINED` with `playerId != binding.binder` | CD-LSH-26 |
| Pasture block gone | the index check finds no pasture at `binding.pasture` | CD-WMD-07, CD-LSH-27 |
| Evolution leaves no fitting role | `CobblemonEvents.EVOLUTION_COMPLETE` and the index check: no role of the bound role's handler fits the new types | CD-LSH-14 |

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

`BindRulesTest` also checks the order of the checks, the priority pick, the first-click refusals of a source and
`destination_same`. `BindingTest` and `LeashIndexTest` round-trip the codecs that save bindings and reservations
(CD-LSH-07, CD-CLM-06). `WorkLeashRecipeTest` reads the recipe file (CD-LSH-15).

- [ ] **Step 2: Write the failing GameTests**

The approver's ruling of 2026-11-09 (the index's execution rule 6) keeps GameTests where the real platform could take
the click: Cobblemon's own entity interaction, the block's screen, the bound worker's work, and the reset path against
Cobblemon's real PC and party. `TestKit.clickEntity` and `TestKit.clickBlock` go through `Player.interactOn` and
`ServerPlayerGameMode.useItemOn`. The test mod turns `Feedback.recording` on at server start, because tests run side by
side, and the tests read `Feedback.recorded[player.uuid]`.

`LeashTests`:

```kotlin
// cdLsh01SelectsOwnedPasturedPokemon: owner clicks a pastured Vaporeon (Work Mode on); the stack's LeashSelection holds its UUID.
// cdLsh02ClickIsCancelledBeforeCobblemon: plain and sneak clicks with the leash return SUCCESS and never reach a test listener
//   at LOWEST priority, which marks the entity's NeoForge data for every click that is not cancelled. Player.interactOn calls
//   the entity, where Cobblemon's mobInteract opens its wheel or mounts the Pokémon, only after that. A first click with an
//   empty hand is marked, which shows that the marker works. The Pokémon is on no shoulder.
// cdLsh03BindsFurnaceAndWorksIt: select a Charmander, click a furnace with input in its work area; 1 tick later
//   BindingStore.read names the furnace, and then the leashed Charmander holds the work claim and is Working there.
// cdLsh04FurnaceScreenStaysShut: the owner is a fake player that counts opened menus, because NeoForge's FakePlayer drops
//   every menu, so containerMenu never changes. An empty-hand click on the furnace opens one menu; the bind click opens none.
// cdLsh07BindingSurvivesRestartAndToggle: bind, wait until the worker is Working, round-trip the Pokémon through its NBT
//   codec, simulateRestart, Work Mode off, then on; the binding is unchanged on every tick, and the worker is Working at
//   the furnace again.
```

`LeashResetTests`:

```kotlin
// cdLsh08RecallAndTargetLoss: four bound Charmanders: one unpastured to the PC (pasture.releasePokemon), one moved to the
//   party (releasePokemon, then PC remove and party add), one whose furnace is destroyed, one whose furnace becomes stone;
//   20 ticks later none has a binding, none of the furnaces is reserved, and the binder has two "it left its pasture" and
//   two "its target was broken or replaced" chat messages, each naming the Charmander.
```

These GameTests are not written:

- CD-LSH-05 (`cdLsh05RefusalsShowTheirMessage`: a furnace at offset 9 (offset 8 binds), a stone block, a furnace bound to
  another Charmander, whose display name appears in the message, and a selection whose Charmander left the pasture):
  GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-06 (`cdLsh06BoundMessage`: the recorded action bar reads "Bound Charmander to Furnace"): GameTest deferred (high
  confidence): verified in the end-of-phase pass.
- CD-LSH-09 (`cdLsh09UnloadKeepsBinding`: `EngineEvents.onChunkUnload` for the target's chunk; 100 ticks later the binding
  remains and the worker is Working again within 600 ticks): GameTest deferred (high confidence): verified in the
  end-of-phase pass.
- CD-LSH-10 (`cdLsh10SneakClickClears`: sneak-click a bound Charmander; 1 tick later no binding): GameTest deferred (high
  confidence): verified in the end-of-phase pass.
- CD-LSH-11 (`cdLsh11RebindReplaces`: bind to furnace A, then furnace B; A is no longer reserved and the binding names B):
  GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-13 (`cdLsh13LossMessages`: the evolution message, with cdLsh14; cdLsh08 checks the messages for recall and target
  loss): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-14 (`cdLsh14EvolutionWithoutFittingRole`: bind a Charmander, set `pokemon.species` to Squirtle's species and call
  `LeashResets.recheckRole`, which `EVOLUTION_COMPLETE` calls; 20 ticks later no binding): GameTest deferred (high
  confidence): verified in the end-of-phase pass.
- CD-LSH-15 (`cdLsh15RecipeCraftsOneLeash`: a 3 × 3 `CraftingInput` with a Lead and a `cobblemon:poke_ball` in two slots;
  `level.recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level)` assembles one Work Leash): GameTest deferred (high
  confidence): verified in the end-of-phase pass.
- CD-LSH-17 (`cdLsh17PendingSourceOnChest`: select an Alakazam (test_transfer role), click a chest's top face; the stack's
  PendingSource holds the chest position and UP, and no binding exists): GameTest deferred (high confidence): verified in
  the end-of-phase pass.
- CD-LSH-18 (`cdLsh18SecondClickBindsDestination`: then click a barrel's north face; the binding holds source, destination
  and both faces): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-19 (`cdLsh19AirClickClears`: with a pending source, `player.gameMode.useItem(...)` on air clears it at once):
  GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-20 (`cdLsh20SelectDestinationMessage`: the recorded action bar reads "Select a destination for Alakazam"):
  GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-21 (`cdLsh21CannotReachMessage`: a bound furnace boxed in glass; the owner's recorded action bar reads "Charmander
  cannot reach Furnace" within 601 ticks of the claim): GameTest deferred (high confidence): verified in the end-of-phase
  pass.
- CD-LSH-24 (`cdLsh24MovedToAnotherPasture`: release from pasture A and tether to pasture B; 20 ticks later no binding and
  the old target is not reserved): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-25 (`cdLsh25ReleasedPokemon`: Cobblemon's release path, `pc.remove` plus the `POKEMON_RELEASED_EVENT_POST` post;
  20 ticks later the old target is not reserved): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-26 (`cdLsh26TradeClearsData`: a Cobblemon whose persistentData holds a binding made by owner A; post `POKEMON_GAINED`
  for owner B; the binding data is gone): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-LSH-27 and CD-WMD-07 (`cdLsh27PastureBroken`: bind three workers, destroy the pasture block; 20 ticks later no binding
  remains and no target is reserved): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-CLM-05 (`cdClm05BindTakesOverAutoClaim`: auto worker A holds a furnace; bind worker B; 1 tick later A has no claim; 20
  ticks later B holds the work claim): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-CLM-06 (`cdClm06ReservedTargetStaysReserved`: bound worker in Standby elsewhere (Work Mode off on its pasture), an Idle
  Charmander in a second Work Mode pasture 3 blocks from the target for 400 ticks; the target gets no claim): GameTest
  deferred (high confidence): verified in the end-of-phase pass.
- CD-ASG-20 (`cdAsg20LeashedWorkerStaysOnItsTarget`: a leashed Stoker in Standby at an empty furnace, an unclaimed furnace
  with input 3 blocks away, 400 ticks; it never claims the second furnace and keeps its binding): GameTest deferred (high
  confidence): verified in the end-of-phase pass.
- CD-SEC-07 (`cdSec07NotYourPokemon`: player B clicks player A's pastured Cobblemon and a wild Cobblemon; neither is
  selected): GameTest deferred (high confidence): verified in the end-of-phase pass.
- CD-SEC-11 (`cdSec11CancelledClickNeverBinds`: a test listener cancels RightClickBlock on a chest; click it with a selected
  Alakazam; no pending source and no binding): GameTest deferred (high confidence): verified in the end-of-phase pass.

The test mod registers `TestTransferHandler` and holds the role file `test_transfer.json`, so the end-of-phase pass can
write the source-and-destination tests.

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
git add -- <each new and changed file under src/main, src/test and src/gametest> scripts/placeholder_art.py ARCHITECTURE.md \
  docs/plans/2026-11-09-phase-1-m1-engine-leash-stoker-part2.md docs/plans/2026-11-09-phase-1-m5-ui-compat-gate.md
git commit -m "Add the Work Leash: selection, bindings, refusals, resets and the recipe"
```

---

### Task 12: Operator command for claims

**Rows:** CD-UI-14 (work claims and bindings), CD-SEC-09.

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/command/DynamicsCommands.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/command/ClaimsReport.kt` (the entries and their order, pure)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/command/ClaimsReportTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/CommandTests.kt` (deferred, see Step 1)

**Interfaces:**
- Produces: `/cobblemondynamics claims`, registered in `RegisterCommandsEvent`, with
  `.requires { it.hasPermission(2) }` on the root literal. Each output line is one entry:
  `claim <kind> <target> <worker name> (<worker uuid>)` or `binding <target> <worker name> (<uuid>)`, where
  `<target>` is `dimension x y z` for blocks and the ally's name for entities. The lines come from lang
  keys `cobblemon_dynamics.command.claims.claim` and `cobblemon_dynamics.command.claims.binding`; the kind
  from `cobblemon_dynamics.command.claims.kind.<work|bed|heal>`. Claims come first, by kind and target, then
  bindings, read from every level's `leash_index`. With no entry the command prints
  `cobblemon_dynamics.command.claims.none`. It returns the entry count. It lists every claim kind, so M2 adds
  no code for the bed and heal claim lines.

- [ ] **Step 1: Write the failing tests**

`ClaimsReportTest` checks the block target text (`minecraft:the_nether 12 64 -3`) and that every claim and binding
gives one line in that order (CD-UI-14). These GameTests are not written:

- CD-UI-14 (`cdUi14ClaimsListsEveryEntry`: a Working Stoker (work claim) and a bound Charmander (binding); run the
  command through `server.commands.performPrefixedCommand(operatorSource, "cobblemondynamics claims")` with a
  CommandSource that records messages; the output holds one claim line and one binding line, each with the target and
  the worker name): GameTest deferred (high confidence): verified in the end-of-phase pass. Task 13's test pack
  `reload` scenario runs the command from the console of a real server.
- CD-SEC-09 (`cdSec09LevelZeroIsRefused`: a level-0 mock player runs both "cobblemondynamics claims" and
  "cobblemondynamics stamina"; both fail, `performPrefixedCommand` returns 0 and the recorded output holds no claim
  line): GameTest deferred (high confidence): verified in the end-of-phase pass.

- [ ] **Step 2: Write the command, run the tests, see them pass, and commit**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -- <each new and changed file under src/main and src/test> docs/plans/2026-11-09-phase-1-m1-engine-leash-stoker-part2.md \
  docs/plans/2026-11-09-phase-1-m2-stamina-beds-healers.md docs/plans/2026-11-09-phase-1-m5-ui-compat-gate.md
git commit -m "Add the /cobblemondynamics claims operator command"
```

---

### Task 13: Payloads, milestone checks and the pull request

**Rows:** CD-SEC-05 and CD-SEC-06 (the payload path), CD-WMD-03 and CD-WMD-04 (the payload path).

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/net/DynamicsPayloads.kt` (registration; Task 10 created it with `work_sound`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/WorkModeTogglePayload.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/PasturePanelPayloads.kt` (`open`, `close`, `state`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/LeashOverviewPayloads.kt` (`request`, `overview`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/net/PanelViewers.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/client/ClientState.kt`
- Modify: `pasture/WorkModeService.kt` (`apply` sends the new state to the viewers), `pasture/WorkModePolicy.kt`
  (`mayToggle`), `pasture/PastureAdapter.kt` (`tetheredIds`), `engine/EngineEvents.kt` (viewers leave on logout
  and server stop), `scripts/testpack/testpack.py` and its `README.md` (the `reload` scenario)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/net/PayloadCodecTest.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/pasture/WorkModePolicyTest.kt` (`mayToggle`)
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/PayloadTests.kt` (deferred, see Step 2)

**Interfaces:**
- Produces: payload ids from ARCHITECTURE section 7. `WorkModeTogglePayload(enabled: Boolean)`;
  `PasturePanelStatePayload(workMode: Boolean, canToggle: Boolean, entries: List<PanelEntry>)` with
  `PanelEntry(pokemonId: UUID, percent: Int, state: WorkerStatus)`; `LeashOverviewPayload(entries: List<Pair<Int, BlockPos>>)`.
  The payloads with no fields (`pasture_panel_open`, `pasture_panel_close`, `leash_overview_request`) are Kotlin
  `data object`s with `StreamCodec.unit`. `PanelViewers` maps a pasture key to the players with its panel open and
  keeps the last state sent to each player (`lastSent`), which the GameTests read because fake players drop packets.
  The server handler of the toggle resolves the pasture with `PastureAdapter.linkedPasture(player)` and calls
  `WorkModeService.request`. Cobblemon keeps that link only while the player is closer than 10.0 blocks
  (`PastureLinkManager.kt:37`), while the service itself accepts exactly 10.0, so a toggle from exactly 10.0 blocks
  finds no pasture. `WorkModeService.apply` sends the new state to every viewer of the pasture after each change,
  in arrival order (CD-SEC-06), and a refused or ignored toggle sends the requester the current state. `canToggle`
  is `WorkModePolicy.mayToggle`: the placer or an operator (CD-UI-03). Until M2 adds Work Stamina, every entry's
  `percent` is 100 and its state is the engine's worker state, or Idle. The overview reply holds the requester's
  own bindings in their level, with entity id -1 for a worker whose entity is not loaded. Client handlers store the
  last state in `ClientState` (M5 draws it). Registration uses `RegisterPayloadHandlersEvent` with
  `registrar.playToServer` and `registrar.playToClient`; handlers run on the main thread, NeoForge's default. Every
  payload is required, so a client without the mod cannot join (CD-PLAT-06).

- [ ] **Step 1: Write the codec round-trip unit test** for each payload with its plain `StreamCodec<ByteBuf, ...>`
  form, which every payload here can use because none carries registry data; for `PasturePanelStatePayload` use two
  entries with different states. The test also checks that the decode reads every byte.

- [ ] **Step 2: Write the GameTests**

These GameTests are not written:

- CD-SEC-05 and CD-WMD-03, the payload path (`cdSec05DuplicateOnPayloadStaysOn`: handle `WorkModeTogglePayload(true)`
  twice, 10 ticks apart, for the linked placer; Work Mode stays on): GameTest deferred (high confidence): verified in
  the end-of-phase pass.
- CD-SEC-06 and CD-WMD-04, the payload path (`cdSec06LastPayloadWinsAndViewersSeeIt`: two operators linked to the
  pasture send on, then off, in one tick; Work Mode is off and `PanelViewers.lastSent` for each viewer says off):
  GameTest deferred (high confidence): verified in the end-of-phase pass.

The handlers call `WorkModeService.request`, whose rows Task 7's GameTests already check; the codec test checks that
the payload carries the requested state.

- [ ] **Step 3: Run everything**

Run: `./gradlew build runGameTestServer && py scripts/check_plan_coverage.py`
Expected: `BUILD SUCCESSFUL`, every GameTest passes, `missing: 0`.

- [ ] **Step 4: Run the test pack**

Run: `./gradlew build && py scripts/testpack/testpack.py smoke` and add a `reload` scenario to the script:
start the server with the M1 jar, run `cobblemondynamics claims` from the console over RCON (it must answer),
run `reload`, and check that the log holds no `Skipping role file` line.
Expected: `SMOKE PASS`, `RELOAD PASS`. Pasturing a Cobblemon needs a player, so worker scenarios stay in the
GameTests; the approver's play-test covers them in a real client. The command must answer
`No claims and no leash bindings`. The reload ends on the server thread after the command replies, so the scenario
waits for vanilla's next `Loaded N recipes` line (`Server.count_log` and `wait_log(..., count=)`) before it reads the
log.

- [ ] **Step 5: Commit, open the pull request, resolve reviews, merge**

Use `/commit`, then dispatch `pr-runner` (base `main`). The pull request body lists every row of Tasks 1 to
13, the rows whose final part lands in M3 or M4 (index table), and the CD-STK-07 note from Task 8.
