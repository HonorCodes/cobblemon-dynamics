# M6: Telekinesis and the Magnet

Part of the [Phase 2 plan](2026-11-10-phase-2-plan.md). Read its Global Constraints, including the multiplayer rules,
first. Branch: `m6-telekinesis-magnet`. Builds on Phase 1 (M0 to M5), the play-test fixes and the pull request of the
approver's 2026-11-10 changes.

**Milestone goal:** a worker gets only a work spot that it can walk to. Psychic-types move items from one container to
another, and Steel-types collect dropped items into a container, at ceil(64 × L ÷ 100) items per 20-tick batch, with no
item lost or made. The Phase 1 leash rows run on the real Telekinesis role.

Before Task 1:

- Check execution rule 7: `main` holds the CD-REL-13 play-test note, the approver-changes pull request and the fixes of
  the Phase 1 multiplayer review. Add any spec amendment of the plan index that the spec sheet lacks, with its line in the
  corrections list, as the first commit of the branch: `"Amend CD-ART-01, CD-ART-02 and CD-ART-12 for the approver's art
  ruling [rag:2]"`.
- Check the references of this milestone against the jars (rule 3).

Checked with `javap` on 2026-11-10, on the merged NeoForge 21.1.265 jar in `neoformruntime/intermediate_results/` and on
the AE2 19.2.19 jar:

- `Capabilities.ItemHandler.BLOCK` is `BlockCapability<IItemHandler, Direction>`, so Kotlin sees the context as
  `Direction?` (the CLAUDE.md gotcha holds). `IItemHandler` has `getSlots()`, `getStackInSlot(int)`,
  `insertItem(int, ItemStack, boolean)`, `extractItem(int, int, boolean)`, `getSlotLimit(int)` and
  `isItemValid(int, ItemStack)`. `ItemHandlerHelper.insertItemStacked(IItemHandler, ItemStack, boolean)` is public and
  static. `BlockCapabilityCache.create(cap, ServerLevel, BlockPos, context)` and `getCapability()` exist.
- `ItemEntity(Level, double, double, double, ItemStack)`, `getItem()`, `setItem(ItemStack)`, `hasPickUpDelay()`,
  `setPickUpDelay(int)` and `setNoPickUpDelay()` exist. `EntityGetter.getEntitiesOfClass(Class, AABB, Predicate)` exists.
- `PathNavigation.createPath(BlockPos, int)` is public, and `Path.canReach()` exists. The overload that takes a follow
  range is protected, so the public calls plan within the mob's `FOLLOW_RANGE` attribute.
- `ChestBlock.getConnectedDirection` joins a chest whose `TYPE` is `LEFT` to its neighbor at `facing.getClockWise()`, and
  a `RIGHT` one to `getCounterClockWise()`. `GameTestHelper` has `startSequence()`, `succeedWhen`, `onEachTick`,
  `runAfterDelay` and `destroyBlock`, and `GameTestSequence` has `thenExecute`, `thenIdle`, `thenWaitUntil` and
  `thenSucceed`.
- AE2 for the GameTests: `AEBlocks.INTERFACE`, `CABLE_BUS`, `CREATIVE_ENERGY_CELL` and `ME_CHEST`, `AEParts.INTERFACE`
  and `AEParts.GLASS_CABLE`, `AEItems.ITEM_CELL_1K`, `PartHelper.setPart(ServerLevel, BlockPos, Direction, Player,
  IPartItem)`, `MEChestBlockEntity.setCell(ItemStack)` and `getCellInventory(int)`, `MEStorage.getAvailableStacks()`, and
  `AEItemKey.of(ItemLike)`.
- Cobblemon 1.8.4 (from its jar): the `kinesis` timeline lists the animations `kinesis` and `status`, and emits
  `cobblemon:kinesis_actor` and `cobblemon:kinesis_actorspiral` from the user. The file of `kinesis_actor` plays
  `cobblemon:move.kinesis.actor` on creation, so Telekinesis sends only `kinesis_actorspiral` (CD-FX-09).
  `cobblemon:metal_sparks` exists and plays no sound. Alakazam is Psychic (hitbox 1.0 × 1.7), Klink is Steel (0.9 × 1.2)
  and Slowbro is Water and Psychic.

Checked for Task 1 on 2026-11-10, with `javap -c` on the merged NeoForge jar and in the Cobblemon 1.8.4 sources:

- `PokemonEntity.createAttributes()` adds `Attributes.FOLLOW_RANGE` at 32.0, so `getAttributeValue(Attributes.FOLLOW_RANGE)`
  works on every `PokemonEntity`. Its navigation is Cobblemon's `OmniPathNavigation`, whose `createPath(BlockPos, Int)` moves
  an air target of a Pokémon that cannot fly to the first non-solid cell above the floor, which is the spot itself for a
  standable spot. Cobblemon's `FollowWalkTargetTask` walks to a plain `WalkTarget` with `navigation.createPath(target, 0)` and
  tests `path.canReach()`, so the path check asks what the walk asks later.
- `PathNavigation.createPath` returns null, with no search, while `canUpdatePath()` is false. `OmniPathNavigation` makes it false
  for a swimmer that is neither on the ground, in a liquid, flying nor a passenger, so a null path means that the navigation
  cannot plan now, not that the spot has no path. A path that it finds sets the navigation's `targetPos`, `reachRange` and
  stuck timer, and a later call for that target returns the current path while it is not done. The work order of a claim
  stops the old path: `BrainBridge.enterWork` stops every running task, and Cobblemon's `FollowWalkTargetTask.stop` stops the
  navigation.

Inferred (check before the task that uses it):

- `Path.canReach()` with accuracy 0 is true for every spot that a Phase 1 GameTest worker reaches. The whole GameTest suite
  is the check, because a false negative leaves a worker without a claim (Task 1).
- A Create chute with no inventory below it holds one stack and refuses more, and Create's vault, depot and chute drop
  their contents when they break (Task 4).
- Adjacent AE2 devices form one ad-hoc network with no controller. The interface sends its buffer to the ME chest's cell on
  its grid tick (Task 4).
- `ColoredItemDefinition.item(AEColor)` gives the glass cable part item, and `PartHelper.setPart` with a `null` side places
  the cable in the center. `BlockDefinition.block()`, `ItemDefinition.stack()`, `ItemDefinition.asItem()` and
  `KeyCounter.get(AEKey)` exist with these names (Task 4).

`TestKit` additions (shared, no Create class):

```kotlin
/** Selects [entity] with a new Work Leash, clicks [source], then [destination]. Returns the binding that the clicks made. */
fun bindTransfer(
    helper: GameTestHelper,
    player: ServerPlayer,
    entity: PokemonEntity,
    source: BlockPos,
    destination: BlockPos,
    sourceFace: Direction = Direction.UP,
    destinationFace: Direction = Direction.UP,
): Binding? {
    leash(player)
    clickEntity(player, entity)
    clickBlock(helper, player, source, sourceFace)
    clickBlock(helper, player, destination, destinationFace)
    return BindingStore.read(entity.pokemon)
}

/** A double chest whose left half sits at [rel], facing [facing]: one 54-slot item handler. */
fun doubleChest(helper: GameTestHelper, rel: BlockPos, facing: Direction): IItemHandler {
    val chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)
    helper.setBlock(rel, chest.setValue(ChestBlock.TYPE, ChestType.LEFT))
    helper.setBlock(rel.relative(facing.clockWise), chest.setValue(ChestBlock.TYPE, ChestType.RIGHT))
    val handler = items(helper, rel) ?: throw GameTestAssertException("no item handler at the double chest")
    if (handler.slots != 54) throw GameTestAssertException("the double chest has ${handler.slots} slots, not 54")
    return handler
}

/** The unsided item handler of the block at [rel], or null. */
fun items(helper: GameTestHelper, rel: BlockPos): IItemHandler? =
    helper.level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(rel), null)

/** How many of [item] the block at [rel] holds, and how many lie on the ground of the test, as item entities. */
fun countIn(helper: GameTestHelper, rel: BlockPos, item: Item): Int =
    items(helper, rel)?.let { h -> (0 until h.slots).sumOf { h.getStackInSlot(it).takeIf { s -> s.`is`(item) }?.count ?: 0 } } ?: 0

fun countOnTheGround(helper: GameTestHelper, item: Item): Int =
    helper.level.getEntitiesOfClass(ItemEntity::class.java, AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48.0)) {
        it.item.`is`(item)
    }.sumOf { it.item.count }
```

---

### Task 1: Work spots that a path reaches

Known issue (a) of the [Phase 1 gate report](../testing/phase-1-gate.md#known-issues-and-open-decisions): the work spot
search checks that the worker's body fits a spot, but not that a path reaches it. A worker can get a spot that it cannot
walk to, and it recovers only through the 600-tick travel timeout and the 6,000-tick skip.

**Rows:** CD-ASG-09 (a spot needs a path from the worker), CD-ASG-13 and CD-ASG-14 (a target with no reachable spot is
skipped as after a timeout, with no 600-tick wait), CD-LSH-21 (the owner of a leashed worker gets the "cannot reach"
message, through the existing no-spot path).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/engine/Reach.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkSpots.kt` (`find` takes `excluded`, new
  `findReachable` and `MAX_PATH_CHECKS`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (`claim` calls `findReachable` with
  `Reach.of(entity)`)
- Modify: `ARCHITECTURE.md` (section 5, "Work spots": a spot needs a path from the worker)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkSpotsTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/WorkSpotTests.kt`

**Interfaces:**
- Consumes: `WorkSpots.find(grid, area, target: TargetShape, targetCenter, range, from)`, `SpotGrid`, `TargetShape`.
- Produces:

```kotlin
object WorkSpots {
    /** At most this many spots get a path check in one claim, best first. Each check is one run of the worker's pathfinder. */
    const val MAX_PATH_CHECKS = 3

    fun find(
        grid: SpotGrid, area: WorkArea, target: TargetShape, targetCenter: Vec3, range: Double, from: Vec3,
        excluded: Set<Long> = emptySet(),
    ): BlockPos?

    /**
     * The best spot of [find] that [reachable] accepts. It checks at most [maxChecks] spots, best first, and returns null when
     * none of them has a path, so the worker skips the target for `retryWaitTicks`, as after a timeout (CD-ASG-14).
     */
    fun findReachable(
        grid: SpotGrid, area: WorkArea, target: TargetShape, targetCenter: Vec3, range: Double, from: Vec3,
        reachable: (BlockPos) -> Boolean, maxChecks: Int = MAX_PATH_CHECKS,
    ): BlockPos?
}

object Reach {
    /**
     * Whether [entity]'s own navigation finds a whole path to a spot. Cobblemon's pathfinder knows how the species walks, swims
     * or flies. A spot farther than the entity's follow range counts as reachable, because the pathfinder does not plan that
     * far. So does a spot when the navigation gives no path at all, which means that it cannot plan at the moment. The travel
     * timeout (CD-ASG-13) still catches both.
     */
    fun of(entity: PokemonEntity): (BlockPos) -> Boolean
}
```

- [ ] **Step 1: Failing unit tests** in `WorkSpotsTest` (the file's `GridFixture` is a flat floor at y = 0):

```kotlin
private val from = Vec3(1.5, 1.0, 0.5)

@Test
fun `CD-ASG-09 a best spot with no path gives way to the next best spot`() {
    val grid = GridFixture(emptySet(), target)
    val shape = TargetShape.of(target)
    val best = WorkSpots.find(grid, area, shape, center, 6.0, from)!!
    val spot = WorkSpots.findReachable(grid, area, shape, center, 6.0, from, reachable = { it != best })
    assertNotNull(spot)
    assertNotEquals(best, spot)
    assertTrue(shape.isBeside(spot!!.x, spot.y, spot.z), "the next spot $spot is not beside the target")
}

@Test
fun `CD-ASG-14 a target whose three best spots have no path gets no spot`() {
    val checked = mutableListOf<BlockPos>()
    val spot = WorkSpots.findReachable(
        GridFixture(emptySet(), target),
        area,
        TargetShape.of(target),
        center,
        6.0,
        from,
        reachable = {
            checked += it
            false
        },
    )
    assertNull(spot)
    assertEquals(WorkSpots.MAX_PATH_CHECKS, checked.size)
    assertEquals(checked.size, checked.toSet().size, "a spot was checked twice: $checked")
}

@Test
fun `CD-ASG-09 a reachable best spot costs one path check`() {
    val checked = mutableListOf<BlockPos>()
    val grid = GridFixture(emptySet(), target)
    val spot = WorkSpots.findReachable(grid, area, TargetShape.of(target), center, 6.0, from, reachable = {
        checked += it
        true
    })
    assertEquals(WorkSpots.find(grid, area, TargetShape.of(target), center, 6.0, from), spot)
    assertEquals(1, checked.size)
}
```

- [ ] **Step 2: Run them and see them fail:** `./gradlew test --tests "codes.honor.cobblemondynamics.engine.WorkSpotsTest"`.
  Expected: compilation fails, because `findReachable` and `MAX_PATH_CHECKS` do not exist.

- [ ] **Step 3: Write the code.** In `WorkSpots.find`, skip a cell whose `BlockPos.asLong(x, y, z)` is in `excluded` before
  any other check. Then:

```kotlin
fun findReachable(
    grid: SpotGrid, area: WorkArea, target: TargetShape, targetCenter: Vec3, range: Double, from: Vec3,
    reachable: (BlockPos) -> Boolean, maxChecks: Int = MAX_PATH_CHECKS,
): BlockPos? {
    val excluded = HashSet<Long>()
    repeat(maxChecks) {
        val spot = find(grid, area, target, targetCenter, range, from, excluded) ?: return null
        if (reachable(spot)) return spot
        excluded += spot.asLong()
    }
    return null
}
```

```kotlin
object Reach {
    fun of(entity: PokemonEntity): (BlockPos) -> Boolean {
        val range = entity.getAttributeValue(Attributes.FOLLOW_RANGE)
        return { spot ->
            entity.position().distanceTo(Vec3.atBottomCenterOf(spot)) > range || entity.navigation.createPath(spot, 0)?.canReach() ?: true
        }
    }
}
```

  A null path counts as reachable: `createPath` gives null with no search while the navigation cannot plan, for example
  while a swimmer is in the air, and a skip of 6,000 ticks for that reason would be worse than the Phase 1 behavior that the
  travel timeout gives.

  In `WorkEngine.claim`, replace the `WorkSpots.find` call:

```kotlin
WorkSpots.findReachable(grid, area(t, runtime), shape, center, role.definition.range, entity.position(), Reach.of(entity))
```

  The no-spot path stays: the worker skips the target for `retryWaitTicks`, and `claimBound` sends a leashed worker's owner
  "{Pokémon} cannot reach {target}" (CD-LSH-21).

- [ ] **Step 4: Run the unit tests and see them pass.**

- [ ] **Step 5: GameTest** in `WorkSpotTests` (the engine meets Cobblemon's real pathfinder, so rule 6 keeps it):

```kotlin
/**
 * The furnace's best spot, beside it on the pasture's side, sits in a pocket of two cells that walls 2 blocks high close off,
 * with a block on top of the furnace. The pocket's second cell makes the spot open, so the search ranks it first for the
 * Charmander, which is held south of the furnace until it claims. With the path check, the Charmander takes a spot that it
 * can walk to and Works well before the 600-tick timeout (CD-ASG-09).
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 400)
fun cdAsg09UnreachablePocketGivesWayToAReachableSpot(helper: GameTestHelper) {
    val furnaceRel = BlockPos(7, 2, 3)
    TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 16))
    helper.setBlock(furnaceRel.above(), Blocks.STONE)
    val pocket = setOf(BlockPos(7, 2, 4), BlockPos(8, 2, 4))
    for (wall in listOf(BlockPos(6, 2, 4), BlockPos(7, 2, 5), BlockPos(8, 2, 5), BlockPos(9, 2, 4), BlockPos(8, 2, 3))) {
        helper.setBlock(wall, Blocks.STONE)
        helper.setBlock(wall.above(), Blocks.STONE)
    }
    val owner = TestKit.owner(helper)
    val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
    TestKit.workMode(pasture, true)
    val charmander = TestKit.tether(helper, pasture, owner, "charmander level=20")
    val hold = BlockPos(7, 2, 8)
    helper.onEachTick {
        if (TestKit.claimOf(helper, charmander) == null) {
            TestKit.moveTo(helper, charmander, hold)
            return@onEachTick
        }
        val worker = TestKit.workerOf(helper, charmander) ?: throw GameTestAssertException("the Charmander is no worker")
        val spot = spotOf(helper, charmander)
        helper.assertFalse(spot in pocket, "CD-ASG-09: the Charmander got the walled-off spot $spot")
        if (worker.status == WorkerStatus.WORKING) helper.succeed()
    }
}
```

  The hold, as in the file's other two tests, makes the pocket the best spot at claim time: a Charmander that wandered west
  of the furnace before its first claim would rank the open spot west of it first, and the test would pass on the old code.

  Run `./gradlew runGameTestServer` and see the test fail on the old code (the Charmander takes (7, 2, 4) and never Works),
  then pass with Step 3. Every other GameTest still passes, which shows that the path check refuses no spot that a worker
  reached before.

- [ ] **Step 6: Multiplayer check.** Read the code against the rules of the plan index. `Reach` reads only the worker's
  own entity and navigation. Nothing keys on a player.

- [ ] **Step 7: Commit:** `git add -- <the files above>`, then
  `git commit -m "Give a worker only a work spot that its navigation can reach [rag:1]"`.

---

### Task 2: Item batches: rates, conservation and the batch clock

**Rows:** CD-TLK-03, CD-TLK-07 (the batch planner), CD-TLK-12 (the reach rule), CD-MAG-03, CD-MAG-06 (the batch
planner), CD-MAG-08 (the query clock), CD-BAL-05 (the formula).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/item/ItemBatch.kt` (`Lot`, `ItemSource`, `ItemSink`,
  `ItemBatch`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/item/TransferParams.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/item/QueryClock.kt`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/handler/item/ItemBatchTest.kt`, `TransferParamsTest.kt`,
  `QueryClockTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks. `TickClock.every` (Phase 1, `handler/vanilla/TickClock.kt`) times the batches in
  Tasks 4 and 5.
- Produces:

```kotlin
/** One stack as a batch sees it. [K] is `ItemStack` in the game and a plain key in unit tests, which load no registry. */
internal data class Lot<K : Any>(val item: K, val count: Int)

/** Where a batch takes items from: a container's slots, or the dropped items of a work area. */
internal interface ItemSource<K : Any> {
    val slotCount: Int

    /** What [slot] would give, at most [max] items, with no change. Null when it gives nothing. */
    fun peek(slot: Int, max: Int): Lot<K>?

    /** Takes at most [count] items from [slot] and returns what it took. */
    fun take(slot: Int, count: Int): Lot<K>?

    /** Puts [lot] back where [slot] took it from, and returns how many items did not fit. */
    fun putBack(slot: Int, lot: Lot<K>): Int
}

/** Where a batch puts items: a container. It never keeps an object that it gets (CD-TLK-07). */
internal interface ItemSink<K : Any> {
    /** How many items of [lot] it takes. With [simulate], how many it would take, with no change. */
    fun accept(lot: Lot<K>, simulate: Boolean): Int
}

internal object ItemBatch {
    /**
     * Moves at most [limit] items from [source] to [sink], slot by slot. A slot gives only what the sink would take. Items that
     * the sink then refuses go back to their slot, and what does not fit there goes to [spill], so no item is lost or made
     * (CD-TLK-07, CD-MAG-06). Returns the number of items that reached the sink.
     */
    fun <K : Any> move(source: ItemSource<K>, sink: ItemSink<K>, limit: Int, spill: (Lot<K>) -> Unit): Int

    /** True when some slot of [source] holds an item that [sink] would take (CD-TLK-06, CD-MAG-07). */
    fun <K : Any> canMove(source: ItemSource<K>, sink: ItemSink<K>): Boolean
}

/** The numbers of Telekinesis and the Magnet, from a role file's `params` (ARCHITECTURE section 9). */
data class TransferParams(val itemsPerSecondAtLevel100: Int, val destinationRange: Int) {
    /** ceil(rate × L ÷ 100) items per 20-tick batch, with L clamped to 1..100 (CD-TLK-03, CD-MAG-03, CD-BAL-05). */
    fun itemsPerBatch(level: Int): Int

    /** CD-TLK-12: [destination] lies at most [destinationRange] blocks from [pasture] on each axis. */
    fun inReach(pasture: BlockPos, destination: BlockPos): Boolean

    companion object {
        /** R52: one batch every 20 ticks, so the rate per batch is the rate per second. */
        const val BATCH_TICKS = 20

        /** `items_per_second_at_level_100` (default 64) and `destination_range` (default 32). A key with the wrong type takes its default. */
        fun of(params: JsonObject): TransferParams
    }
}

/** When the Magnet looks for dropped items: on its first need, then at most once every [intervalTicks] ticks (CD-MAG-08). */
class QueryClock(private val intervalTicks: Int = TransferParams.BATCH_TICKS) {
    fun isDue(now: Long): Boolean
    fun ran(now: Long)
}
```

- [ ] **Step 1: Failing unit tests.**

```kotlin
/** A container of fixed slots with a limit per slot, as a batch sees it. Its items are strings. */
private class Slots(size: Int, private val limit: Int = 64) :
    ItemSource<String>,
    ItemSink<String> {
    val slots = arrayOfNulls<Lot<String>>(size)
    var refuseOnExecute = false
    var refusePutBack = false
    val total: Int get() = slots.sumOf { it?.count ?: 0 }

    fun fill(item: String, count: Int) = apply {
        var left = count
        for (i in slots.indices) if (left > 0) slots[i] = Lot(item, minOf(limit, left)).also { left -= it.count }
    }

    override val slotCount: Int get() = slots.size

    override fun peek(slot: Int, max: Int): Lot<String>? = slots[slot]?.let { Lot(it.item, minOf(max, it.count)) }

    override fun take(slot: Int, count: Int): Lot<String>? {
        val lot = slots[slot] ?: return null
        val n = minOf(count, lot.count)
        slots[slot] = if (n == lot.count) null else lot.copy(count = lot.count - n)
        return Lot(lot.item, n)
    }

    override fun putBack(slot: Int, lot: Lot<String>): Int {
        val current = slots[slot]
        if (refusePutBack || (current != null && current.item != lot.item)) return lot.count
        val n = minOf(limit - (current?.count ?: 0), lot.count)
        slots[slot] = Lot(lot.item, (current?.count ?: 0) + n)
        return lot.count - n
    }

    override fun accept(lot: Lot<String>, simulate: Boolean): Int {
        if (refuseOnExecute && !simulate) return 0
        var left = lot.count
        val order = slots.indices.sortedBy {
            when {
                slots[it]?.item == lot.item -> 0
                slots[it] == null -> 1
                else -> 2
            }
        }
        for (i in order) {
            val current = slots[i]
            if (left == 0 || (current != null && current.item != lot.item)) continue
            val n = minOf(limit - (current?.count ?: 0), left)
            if (!simulate) slots[i] = Lot(lot.item, (current?.count ?: 0) + n)
            left -= n
        }
        return lot.count - left
    }
}

class ItemBatchTest {
    private val params = TransferParams.of(JsonObject())

    @Test
    fun `CD-TLK-03 a batch from a full source moves the rate of the level`() {
        for ((level, expected) in listOf(1 to 1, 25 to 16, 50 to 32, 100 to 64)) {
            val source = Slots(10).fill("dirt", 640)
            val sink = Slots(40)
            assertEquals(expected, ItemBatch.move(source, sink, params.itemsPerBatch(level)) { fail("spilled $it") }, "Lv$level")
            assertEquals(expected, sink.total, "Lv$level sink")
        }
    }

    @Test
    fun `CD-TLK-03 a Lv50 batch never moves a 33rd item, also across slots`() {
        val source = Slots(10).also {
            it.slots[0] = Lot("dirt", 20)
            it.slots[1] = Lot("dirt", 20)
        }
        val sink = Slots(5)
        assertEquals(32, ItemBatch.move(source, sink, params.itemsPerBatch(50)) { fail("spilled $it") })
        assertEquals(8, source.total)
    }

    @Test
    fun `CD-TLK-03 a batch moves fewer only when a container stops it`() {
        assertEquals(10, ItemBatch.move(Slots(3).fill("dirt", 10), Slots(3), 32) { fail("spilled $it") })
        val nearlyFull = Slots(1).also { it.slots[0] = Lot("dirt", 60) }
        assertEquals(4, ItemBatch.move(Slots(3).fill("dirt", 64), nearlyFull, 32) { fail("spilled $it") })
    }

    @Test
    fun `CD-TLK-07 items that the sink refuses after it accepted go back to their slot`() {
        val source = Slots(2).fill("dirt", 100)
        val sink = Slots(4).also { it.refuseOnExecute = true }
        assertEquals(0, ItemBatch.move(source, sink, 64) { fail("spilled $it") })
        assertEquals(100, source.total)
        assertEquals(0, sink.total)
    }

    @Test
    fun `CD-TLK-07 items that fit nowhere spill, and no item is lost or made`() {
        val source = Slots(2).fill("dirt", 100).also { it.refusePutBack = true }
        val sink = Slots(4).also { it.refuseOnExecute = true }
        val spilled = mutableListOf<Lot<String>>()
        ItemBatch.move(source, sink, 64) { spilled += it }
        assertEquals(100, source.total + sink.total + spilled.sumOf { it.count })
    }

    @Test
    fun `CD-MAG-06 a partial insert leaves the rest on the ground`() {
        val ground = Slots(1).fill("cobblestone", 64)
        val container = Slots(1, limit = 10)
        assertEquals(10, ItemBatch.move(ground, container, params.itemsPerBatch(100)) { fail("spilled $it") })
        assertEquals(54, ground.total)
        assertEquals(10, container.total)
    }

    @Test
    fun `CD-MAG-03 with 200 items on the ground, Lv1 inserts 1, Lv50 32 and Lv100 64`() {
        for ((level, expected) in listOf(1 to 1, 50 to 32, 100 to 64)) {
            val ground = Slots(4).fill("cobblestone", 200)
            assertEquals(expected, ItemBatch.move(ground, Slots(27), params.itemsPerBatch(level)) { fail("spilled $it") }, "Lv$level")
        }
    }

    @Test
    fun `CD-TLK-06 CD-MAG-07 there is something to move only while the source holds what the sink takes`() {
        assertFalse(ItemBatch.canMove(Slots(3), Slots(3)))
        val full = Slots(1).fill("stone", 64)
        assertFalse(ItemBatch.canMove(Slots(3).fill("dirt", 5), full))
        assertTrue(ItemBatch.canMove(Slots(3).fill("dirt", 5), Slots(3)))
    }
}

class TransferParamsTest {
    @Test
    fun `CD-TLK-03 the defaults are 64 items per second at Lv100 and a 32-block destination range`() =
        assertEquals(TransferParams(64, 32), TransferParams.of(JsonObject()))

    @Test
    fun `CD-BAL-05 a level outside 1 to 100 takes the nearest end of the curve`() {
        val params = TransferParams.of(JsonObject())
        assertEquals(1, params.itemsPerBatch(0))
        assertEquals(64, params.itemsPerBatch(100))
        assertEquals(64, params.itemsPerBatch(150))
    }

    @Test
    fun `CD-CFG-13 a pack's rate applies with no cap beyond it`() {
        val params = TransferParams.of(JsonObject().apply { addProperty("items_per_second_at_level_100", 128) })
        assertEquals(128, params.itemsPerBatch(100))
        assertEquals(0, TransferParams.of(JsonObject().apply { addProperty("items_per_second_at_level_100", 0) }).itemsPerBatch(100))
    }

    @Test
    fun `CD-TLK-12 a destination at 32 blocks on an axis is in reach, and one at 33 is not`() {
        val params = TransferParams.of(JsonObject())
        val pasture = BlockPos(0, 64, 0)
        assertTrue(params.inReach(pasture, BlockPos(32, 64, 0)))
        assertFalse(params.inReach(pasture, BlockPos(33, 64, 0)))
        assertTrue(params.inReach(pasture, BlockPos(-32, 96, 32)))
        assertFalse(params.inReach(pasture, BlockPos(0, 31, 0)))
    }
}

class QueryClockTest {
    @Test
    fun `CD-MAG-08 asked on every tick, the clock lets a query run at ticks 0, 20 and 40 only`() {
        val clock = QueryClock()
        val runs = (0L..40L).filter { now -> clock.isDue(now).also { if (it) clock.ran(now) } }
        assertEquals(listOf(0L, 20L, 40L), runs)
    }
}
```

- [ ] **Step 2: Run them and see them fail** to compile: `./gradlew test --tests "codes.honor.cobblemondynamics.handler.item.*"`.

- [ ] **Step 3: Write the code.**

```kotlin
internal object ItemBatch {
    fun <K : Any> move(source: ItemSource<K>, sink: ItemSink<K>, limit: Int, spill: (Lot<K>) -> Unit): Int {
        var moved = 0
        for (slot in 0 until source.slotCount) {
            if (moved >= limit) break
            val offer = source.peek(slot, limit - moved) ?: continue
            val fits = sink.accept(offer, simulate = true).coerceAtMost(offer.count)
            if (fits <= 0) continue
            val taken = source.take(slot, fits) ?: continue
            val accepted = sink.accept(taken, simulate = false).coerceIn(0, taken.count)
            moved += accepted
            val back = taken.count - accepted
            if (back > 0) {
                val left = source.putBack(slot, Lot(taken.item, back))
                if (left > 0) spill(Lot(taken.item, left))
            }
        }
        return moved
    }

    fun <K : Any> canMove(source: ItemSource<K>, sink: ItemSink<K>): Boolean =
        (0 until source.slotCount).any { slot -> source.peek(slot, 1)?.let { sink.accept(it, simulate = true) > 0 } == true }
}
```

```kotlin
data class TransferParams(val itemsPerSecondAtLevel100: Int, val destinationRange: Int) {
    fun itemsPerBatch(level: Int): Int {
        val rate = itemsPerSecondAtLevel100.coerceAtLeast(0).toLong()
        return ((rate * level.coerceIn(1, 100) + 99) / 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun inReach(pasture: BlockPos, destination: BlockPos): Boolean = abs(destination.x - pasture.x) <= destinationRange &&
        abs(destination.y - pasture.y) <= destinationRange && abs(destination.z - pasture.z) <= destinationRange

    companion object {
        const val BATCH_TICKS = 20
        private const val RATE = "items_per_second_at_level_100"
        private const val RANGE = "destination_range"
        private val DEFAULT = TransferParams(64, 32)

        fun of(params: JsonObject): TransferParams = TransferParams(
            int(params, RATE) ?: DEFAULT.itemsPerSecondAtLevel100,
            int(params, RANGE) ?: DEFAULT.destinationRange,
        )

        private fun int(params: JsonObject, key: String): Int? =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    }
}
```

```kotlin
class QueryClock(private val intervalTicks: Int = TransferParams.BATCH_TICKS) {
    private var last: Long? = null

    fun isDue(now: Long): Boolean = last?.let { now - it >= intervalTicks } ?: true

    fun ran(now: Long) {
        last = now
    }
}
```

- [ ] **Step 4: Run the tests and see them pass.** Then `./gradlew build`.

- [ ] **Step 5: Multiplayer check.** The classes are pure and hold no player state. A `QueryClock` lives in one claim's
  memo, never in a static field.

- [ ] **Step 6: Commit:** `git commit -m "Plan item batches that move the role's rate and never lose or make an item"`.

---

### Task 3: Two-target bindings reach the handler, with their own rules

**Rows:** CD-TLK-09, CD-TLK-10, CD-TLK-11, CD-TLK-12, CD-LSH-18 (the destination and both faces reach the handler).

**Files:**
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/handler/TargetHandler.kt` (`destinationInReach`,
  `WorkContext.pasture`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt` (`context` passes the pasture block and the
  binding's destination and faces)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkArea.kt` (`box()`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/leash/BindRules.kt` (`TOO_FAR`, `Input.destinationInReach`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashInteractions.kt` (computes `destinationInReach`)
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/leash/LeashResets.kt` (`DESTINATION_GONE`, the destination check)
- Modify: `src/main/resources/assets/cobblemon_dynamics/lang/en_us.json`
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/leash/BindRulesTest.kt`,
  `src/test/kotlin/codes/honor/cobblemondynamics/engine/WorkAreaTest.kt`

**Interfaces:**
- Consumes: `TransferParams.inReach` (Task 2), `Binding.destination`, `Binding.face`, `Binding.destinationFace`.
- Produces:

```kotlin
interface TargetHandler {
    // ... Phase 1 members ...
    /** CD-TLK-12: whether a two-target binding of [role] may name [destination] for a pasture at [pasture]. */
    fun destinationInReach(role: RoleDefinition, pasture: BlockPos, destination: BlockPos): Boolean = true
}

class WorkContext(
    val level: ServerLevel,
    val target: BlockPos,
    val worker: PokemonEntity,
    val pokemon: Pokemon,
    val role: RoleDefinition,
    val now: Long,
    val destination: BlockPos? = null,
    /** The face that the player clicked on the target, the source of a two-target binding. Null for an auto-assigned worker. */
    val sourceFace: Direction? = null,
    val destinationFace: Direction? = null,
    /** The worker's pasture block, so that a handler finds the work area (CD-MAG-04). */
    val pasture: BlockPos? = null,
    val memo: ClaimMemo = ClaimMemo(),
)

data class WorkArea(...) {
    /** The blocks of the area as one box, for entity queries. */
    fun box(): AABB = AABB(minX.toDouble(), minY.toDouble(), minZ.toDouble(), maxX + 1.0, maxY + 1.0, maxZ + 1.0)
}

object BindRules {
    const val TOO_FAR = "cobblemon_dynamics.leash.too_far"
    data class Input(/* the Phase 1 fields, in their order */, val destinationInReach: Boolean = true)
}

object LeashResets {
    const val DESTINATION_GONE = "destination_gone"
}
```

- [ ] **Step 1: Failing unit tests.** In `BindRulesTest`, add `reach: Boolean = true` to the `input` helper and pass it as
  `destinationInReach = reach`. `PendingSource` takes the dimension of the source click first since the multiplayer review
  (`OVERWORLD` is the test class's own constant). Then:

```kotlin
private val pending = PendingSource(OVERWORLD, BlockPos(1, 0, 0), Direction.UP, 0)

@Test
fun `CD-TLK-12 a destination out of reach is refused at the second click`() =
    assertEquals(BindRules.TOO_FAR, refusal(input(roles = listOf(transfer), pending = pending, reach = false)).messageKey)

@Test
fun `CD-TLK-12 a destination in reach binds`() {
    assertInstanceOf(BindRules.Outcome.Bind::class.java, BindRules.evaluate(input(roles = listOf(transfer), pending = pending), shape))
}

@Test
fun `CD-TLK-12 the reach of a destination is never asked of a one-target role or of a source click`() {
    assertInstanceOf(BindRules.Outcome.Bind::class.java, BindRules.evaluate(input(reach = false), shape))
    assertInstanceOf(
        BindRules.Outcome.AwaitDestination::class.java,
        BindRules.evaluate(input(roles = listOf(transfer), reach = false), shape),
    )
}

@Test
fun `CD-TLK-10 CD-TLK-12 a destination equal to the source is named before its reach`() = assertEquals(
    BindRules.DESTINATION_SAME,
    refusal(input(roles = listOf(transfer), pending = pending, same = true, reach = false)).messageKey,
)
```

  In `WorkAreaTest`:

```kotlin
@Test
fun `CD-MAG-04 the box of the default area holds offset 8 and not offset 9`() {
    val box = WorkArea(BlockPos(0, 64, 0), 8, 6).box()
    assertTrue(box.contains(8.5, 64.5, 0.5))
    assertFalse(box.contains(9.5, 64.5, 0.5))
}
```

- [ ] **Step 2: Run them and see them fail** to compile.

- [ ] **Step 3: Write the code.**

  `BindRules.evaluate` checks the reach right after the check of CD-TLK-10:

```kotlin
if (twoTargets && input.pending != null && input.sameAsPending) return Outcome.Refuse(DESTINATION_SAME)
// CD-TLK-12: the destination may sit outside the work area, but not past the role's range from the pasture block.
if (twoTargets && input.pending != null && !input.destinationInReach) return Outcome.Refuse(TOO_FAR)
```

  `LeashInteractions.onBlockClick` passes, for a click with a pending source:

```kotlin
destinationInReach = pending == null ||
    pasture == null ||
    best == null ||
    guarded(best.handler, false) { best.handler.destinationInReach(best.definition, pasture, canonical) },
```

  `WorkEngine.context` gives a binding's destination and faces only to the binding's own target and role:

```kotlin
private fun context(
    t: Tick,
    slot: PastureRuntime.Slot,
    target: BlockPos,
    role: ActiveRole,
    memo: ClaimMemo = ClaimMemo(),
): WorkContext {
    val binding = slot.binding?.takeIf { it.target == target && it.roleId == role.definition.id }
    return WorkContext(
        t.level,
        target,
        slot.entity,
        slot.pokemon,
        role.definition,
        t.now,
        destination = binding?.destination,
        sourceFace = binding?.face,
        destinationFace = binding?.destinationFace,
        pasture = BlockPos.of(slot.worker.pasture.pos),
        memo = memo,
    )
}
```

  `LeashResets.check`, in the branch where the pasture is loaded, after `recheckRole`:

```kotlin
// CD-TLK-11: a two-target binding also ends when its destination is broken or replaced.
val destination = BindingStore.read(pokemon)?.destination
val handler = RoleRegistry.current.get(ref.roleId)?.handler
if (destination != null &&
    handler != null &&
    level.isLoaded(destination) &&
    !guarded(handler, true) { handler.stillValid(level, destination) }
) {
    return lost(level, target, ref, DESTINATION_GONE, pokemon)
}
```

  A destination whose pasture is not loaded waits, as a target does (CD-LSH-09). Lang keys:

```json
"cobblemon_dynamics.leash.too_far": "Too far from its pasture",
"cobblemon_dynamics.binding.reason.destination_gone": "its destination was broken or replaced",
```

- [ ] **Step 4: Run the tests and see them pass.** `LangTest` checks that both keys resolve.

- [ ] **Step 5: Deferred GameTests.** These rows test bind rules and the reset check, which the unit tests and Phase 1's
  `LeashResetTests` cover with the same code paths:
  - CD-TLK-09 (`cdTlk09SourceAtOffset9IsRefused`: sources at horizontal offsets 9 and 8, each with a destination inside the
    work area): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-TLK-10 (`cdTlk10SameChestTwiceIsRefused`): GameTest deferred (high confidence): verified in the end-of-phase pass.
  - CD-TLK-11 (`cdTlk11BrokenDestinationOrReplacedSourceClears`: break the destination of one binding, replace the source
    of a second binding with stone, and neither binding remains 20 ticks later): GameTest deferred (high confidence):
    verified in the end-of-phase pass.
  - CD-TLK-12 (`cdTlk12DestinationAt33IsTooFar`: destinations at x offsets 32 and 33 from the pasture block, in
    `floor_41x8x41`): GameTest deferred (high confidence): verified in the end-of-phase pass.

- [ ] **Step 6: Multiplayer check.** The rules read the clicking player's own stack and the server's state. The refusal
  goes to the clicking player's action bar. The reset message goes to the binder by UUID, through `Feedback.chat`.

- [ ] **Step 7: Commit:** `git commit -m "Give a two-target binding's destination and faces to its handler, and refuse a destination out of reach"`.

---

### Task 4: Telekinesis

**Rows:** CD-TLK-01, CD-TLK-02, CD-TLK-03, CD-TLK-04, CD-TLK-05, CD-TLK-06, CD-TLK-07, CD-TLK-08, CD-FX-02 (the Kinesis
row), CD-FX-09 (no particle with a sound), CD-ASG-05 (leash only), CD-REL-08 (the guide section).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/item/ItemPorts.kt` (`ItemPorts`, `ItemCapability`)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/item/TelekinesisHandler.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/telekinesis.json`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt` (registers the handler)
- Modify: `docs/player-guide.md` (`### Telekinesis`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/BuiltInRoleFilesTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/TelekinesisTests.kt`
- Modify: `src/gametest/kotlin-shared/codes/honor/cobblemondynamics/test/TestKit.kt` (the additions at the top of this file)

**Interfaces:**
- Consumes: `ItemBatch`, `Lot`, `ItemSource`, `ItemSink`, `TransferParams` (Task 2), `WorkContext.destination`,
  `sourceFace`, `destinationFace`, `TargetHandler.destinationInReach` (Task 3), `TickClock.every` (Phase 1).
- Produces:

```kotlin
/** NeoForge's item handlers as the two ends of a batch. NeoForge 21.9 replaces `IItemHandler`, so only this file names it. */
internal object ItemPorts {
    fun source(handler: IItemHandler): ItemSource<ItemStack>

    /** Inserts a copy every time, because Create's chute and depot keep the stack object that they get. */
    fun sink(handler: IItemHandler): ItemSink<ItemStack>
}

internal object ItemCapability {
    /** CD-TLK-01, CD-MAG-01: an item handler on one of the six faces. AE2's cable parts answer only on their own face. */
    fun exposes(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean
    fun exposes(level: ServerLevel, pos: BlockPos): Boolean
}

object TelekinesisHandler : TargetHandler   // id cobblemon_dynamics:telekinesis
```

`telekinesis.json`:

```json
{
  "schema": 1,
  "handler": "cobblemon_dynamics:telekinesis",
  "types": ["psychic"],
  "auto_assign": false,
  "priority": 0,
  "range": 6.0,
  "params": {
    "items_per_second_at_level_100": 64,
    "destination_range": 32
  },
  "effect": {
    "loop_ticks": 40,
    "idle_ticks": 40,
    "animations": ["kinesis", "status"],
    "actor_particles": ["cobblemon:kinesis_actorspiral"]
  }
}
```

- [ ] **Step 1: Failing unit tests** in `BuiltInRoleFilesTest`:

```kotlin
// roleTable gains:
"telekinesis" to Row("cobblemon_dynamics:telekinesis", setOf("psychic"), false, 0, "kinesis"),

/**
 * Cobblemon's `kinesis` timeline emits `kinesis_actor` and `kinesis_actorspiral` from the user, and nothing on the target. The
 * file of `kinesis_actor` plays `cobblemon:move.kinesis.actor` on creation, and work is silent (CD-FX-09), so the role sends
 * only the spiral.
 */
@Test
fun `CD-FX-04 Telekinesis's Kinesis spirals from the worker, with no sound`() {
    val effect = BuiltInRoles.parse("telekinesis").effect
    val kinesis = MoveTimeline.read("kinesis")
    assertEquals(kinesis.animations, effect.animations)
    assertEquals(kinesis.userParticles - "cobblemon:kinesis_actor", effect.actorParticles.map { it.toString() })
    assertEquals(emptyList<String>(), effect.targetParticles.map { it.toString() })
}

@Test
fun `CD-TLK-02 the Telekinesis role file sets auto_assign to false`() {
    val key = autoAssignKey("telekinesis")
    assertTrue(key != null && key.isJsonPrimitive && key.asJsonPrimitive.isBoolean && !key.asBoolean, "telekinesis.json has auto_assign $key")
}

@Test
fun `CD-TLK-03 the default Telekinesis file moves 1, 16, 32 and 64 items per batch at Lv1, 25, 50 and 100`() {
    val params = TransferParams.of(BuiltInRoles.parse("telekinesis").params)
    assertEquals(listOf(1, 16, 32, 64), listOf(1, 25, 50, 100).map(params::itemsPerBatch))
}
```

  `ParticleFilesTest` (Phase 1) then checks that no particle of the new file plays a sound.

- [ ] **Step 2: Run them and see them fail:** the role file does not exist.

- [ ] **Step 3: Write the code.**

```kotlin
internal object ItemPorts {
    fun source(handler: IItemHandler): ItemSource<ItemStack> = object : ItemSource<ItemStack> {
        override val slotCount: Int get() = handler.slots

        override fun peek(slot: Int, max: Int): Lot<ItemStack>? = handler.extractItem(slot, max, true).toLot()

        override fun take(slot: Int, count: Int): Lot<ItemStack>? = handler.extractItem(slot, count, false).toLot()

        override fun putBack(slot: Int, lot: Lot<ItemStack>): Int = handler.insertItem(slot, lot.stack(), false).count
    }

    fun sink(handler: IItemHandler): ItemSink<ItemStack> = object : ItemSink<ItemStack> {
        override fun accept(lot: Lot<ItemStack>, simulate: Boolean): Int =
            lot.count - ItemHandlerHelper.insertItemStacked(handler, lot.stack(), simulate).count
    }

    /** A new stack of [Lot.count] items: no container ever gets an object that another one holds. */
    fun Lot<ItemStack>.stack(): ItemStack = item.copyWithCount(count)

    private fun ItemStack.toLot(): Lot<ItemStack>? = if (isEmpty) null else Lot(copy(), count)
}

internal object ItemCapability {
    fun exposes(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        Direction.entries.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, it) != null }

    fun exposes(level: ServerLevel, pos: BlockPos): Boolean =
        Direction.entries.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, it) != null }
}
```

```kotlin
/**
 * Telekinesis: a Psychic-type moves items from its bound source to its bound destination, through the faces that the player
 * clicked (CD-TLK-04), in one batch every 20 ticks (R52). Only the source is claimed (CD-TLK-05). The engine gives it the
 * destination and both faces through the work context.
 */
object TelekinesisHandler : TargetHandler {
    override val id: ResourceLocation = id("telekinesis")

    /** Leash only (CD-TLK-02, CD-ASG-05), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE
    override val bindingShape: BindingShape = BindingShape.SOURCE_AND_DESTINATION

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        ItemCapability.exposes(level, pos, state, blockEntity)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = ItemCapability.exposes(level, canonical)

    override fun destinationInReach(role: RoleDefinition, pasture: BlockPos, destination: BlockPos): Boolean =
        TransferParams.of(role.params).inReach(pasture, destination)

    /** CD-TLK-06: the source holds an item that the destination takes. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if (TransferParams.of(ctx.role.params).itemsPerBatch(ctx.pokemon.level) <= 0) return false
        val (source, sink) = ports(ctx) ?: return false
        return ItemBatch.canMove(source, sink)
    }

    override fun onStart(ctx: WorkContext) {
        memo(ctx).startedAt = ctx.now
    }

    /** CD-TLK-03, CD-TLK-07: a batch on the first Working tick and every 20 ticks after it. */
    override fun applyWork(ctx: WorkContext) {
        if (!TickClock.every(TransferParams.BATCH_TICKS, memo(ctx).startedAt, ctx.now)) return
        val (source, sink) = ports(ctx) ?: return
        ItemBatch.move(source, sink, TransferParams.of(ctx.role.params).itemsPerBatch(ctx.pokemon.level)) { lot -> spill(ctx, lot) }
    }

    /** The claim's two capability caches, which NeoForge clears when a block or its capabilities change, and its batch start. */
    private class Memo(var startedAt: Long) {
        var source: BlockCapabilityCache<IItemHandler, Direction?>? = null
        var destination: BlockCapabilityCache<IItemHandler, Direction?>? = null
    }

    private fun memo(ctx: WorkContext): Memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }

    /** A destination that is not loaded takes nothing. */
    private fun ports(ctx: WorkContext): Pair<ItemSource<ItemStack>, ItemSink<ItemStack>>? {
        val destination = ctx.destination ?: return null
        if (!ctx.level.isLoaded(destination)) return null
        val memo = memo(ctx)
        val sourceCache = memo.source ?: BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, ctx.level, ctx.target, ctx.sourceFace)
            .also { memo.source = it }
        val destinationCache = memo.destination ?: BlockCapabilityCache.create(
            Capabilities.ItemHandler.BLOCK, ctx.level, destination, ctx.destinationFace,
        ).also { memo.destination = it }
        val from = sourceCache.capability ?: return null
        val to = destinationCache.capability ?: return null
        return ItemPorts.source(from) to ItemPorts.sink(to)
    }

    /** What fits neither the destination nor its own slot drops at the source, so no item is lost (CD-TLK-07). */
    private fun spill(ctx: WorkContext, lot: Lot<ItemStack>) {
        val at = Vec3.atCenterOf(ctx.target)
        Containers.dropItemStack(ctx.level, at.x, at.y + 0.6, at.z, lot.item.copyWithCount(lot.count))
    }
}
```

  Register it in `CobblemonDynamics`: `HandlerRegistry.register(TelekinesisHandler)`.

- [ ] **Step 4: Run the unit tests and see them pass.**

- [ ] **Step 5: GameTests** in `TelekinesisTests` (`@GameTestHolder(MOD_ID)`, `@PrefixGameTestTemplate(false)`). Its
  `scene(helper, spec)` places a Work Mode pasture at (7, 1, 9) with one Pokémon of `spec` and stands the owner beside it,
  as `LeashTests.scene` does. Each test needs real item handlers or a real worker, so rule 6 keeps it:

```kotlin
/** A chest of coal is the source, and a furnace clicked on its east side is the destination: all coal lands in the fuel slot (CD-TLK-04). */
@GameTest(template = "floor_15x6x15", timeoutTicks = 400)
fun cdTlk04CoalGoesIntoTheFuelSlot(helper: GameTestHelper) {
    val chestRel = BlockPos(5, 2, 4)
    val furnaceRel = BlockPos(9, 2, 4)
    helper.setBlock(chestRel, Blocks.CHEST)
    helper.getBlockEntity<ChestBlockEntity>(chestRel).setItem(0, ItemStack(Items.COAL, 16))
    val furnace = TestKit.furnace(helper, furnaceRel, input = null)
    val scene = scene(helper, "alakazam level=100")
    val binding = TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, furnaceRel, destinationFace = Direction.EAST)
    helper.assertTrue(binding?.destinationFace == Direction.EAST, "the clicks gave $binding")
    helper.succeedWhen {
        helper.assertTrue(furnace.getItem(0).isEmpty && furnace.getItem(2).isEmpty, "CD-TLK-04: coal reached the input or the output slot")
        helper.assertTrue(furnace.getItem(1).`is`(Items.COAL) && furnace.getItem(1).count == 16, "CD-TLK-04: the fuel slot holds ${furnace.getItem(1)}")
    }
}

/**
 * 2,000 cobblestone in a double chest. A Lv100 Alakazam moves them into a Create item vault, then a depot, then a chute, one
 * destination at a time, and each destination breaks on a batch tick. After each break, the cobblestone in the chest, in the
 * destination and on the ground still adds up to 2,000 (CD-TLK-07). The depot and the chute keep the stack object that they
 * get, so a missed copy shows up here.
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 1600)
fun cdTlk07NoItemIsLostOrMade(helper: GameTestHelper) {
    val chestRel = BlockPos(3, 2, 3)
    val chest = TestKit.doubleChest(helper, chestRel, Direction.NORTH)
    var left = 2_000
    while (left > 0) {
        val n = minOf(64, left)
        left -= n - ItemHandlerHelper.insertItemStacked(chest, ItemStack(Items.COBBLESTONE, n), false).count
    }
    val destinationRel = BlockPos(10, 2, 3)
    val scene = scene(helper, "alakazam level=100")
    fun total(): Int = TestKit.countIn(helper, chestRel, Items.COBBLESTONE) + TestKit.countIn(helper, destinationRel, Items.COBBLESTONE) +
        TestKit.countOnTheGround(helper, Items.COBBLESTONE)
    var sequence = helper.startSequence()
    for (state in listOf(AllBlocks.ITEM_VAULT.defaultState, AllBlocks.DEPOT.defaultState, AllBlocks.CHUTE.defaultState)) {
        sequence = sequence
            .thenExecute { helper.setBlock(destinationRel, state) }
            .thenIdle(1)
            .thenExecute { TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, destinationRel) ?: throw GameTestAssertException("no binding to ${state.block}") }
            .thenWaitUntil { helper.assertTrue(TestKit.workerOf(helper, scene.worker)?.status == WorkerStatus.WORKING, "the Alakazam does not Work") }
            .thenIdle(3 * TransferParams.BATCH_TICKS)
            .thenExecute { helper.destroyBlock(destinationRel) }
            .thenIdle(2)
            .thenExecute { helper.assertTrue(total() == 2_000, "CD-TLK-07: after the ${state.block} broke, ${total()} cobblestone are left") }
    }
    sequence.thenSucceed()
}

/**
 * A creative energy cell, an ME chest with a 1k item cell and an ME Interface block touch, so they form one network with no
 * controller. 64 coal from a chest reach the cell through the interface block, clicked on its top. Then 64 more reach it
 * through an interface cable part on the south side of a cable bus that touches the energy cell, clicked on the bus's south
 * face (CD-TLK-08).
 */
@GameTest(template = "floor_15x6x15", timeoutTicks = 800)
fun cdTlk08ItemsReachTheNetworkThroughBothInterfaces(helper: GameTestHelper) {
    val chestRel = BlockPos(3, 2, 3)
    helper.setBlock(chestRel, Blocks.CHEST)
    val chest = helper.getBlockEntity<ChestBlockEntity>(chestRel)
    chest.setItem(0, ItemStack(Items.COAL, 64))
    val interfaceRel = BlockPos(8, 2, 3)
    val cellRel = BlockPos(9, 2, 3)
    val storageRel = BlockPos(10, 2, 3)
    val busRel = BlockPos(9, 2, 4)
    helper.setBlock(interfaceRel, AEBlocks.INTERFACE.block())
    helper.setBlock(cellRel, AEBlocks.CREATIVE_ENERGY_CELL.block())
    helper.setBlock(storageRel, AEBlocks.ME_CHEST.block())
    helper.getBlockEntity<MEChestBlockEntity>(storageRel).setCell(AEItems.ITEM_CELL_1K.stack())
    PartHelper.setPart(helper.level, helper.absolutePos(busRel), null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT))
    PartHelper.setPart(helper.level, helper.absolutePos(busRel), Direction.SOUTH, null, AEParts.INTERFACE.asItem())
    fun stored(): Long = helper.getBlockEntity<MEChestBlockEntity>(storageRel).getCellInventory(0)?.availableStacks
        ?.get(AEItemKey.of(Items.COAL)) ?: 0L
    val scene = scene(helper, "alakazam level=100")
    helper.startSequence()
        .thenExecute { TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, interfaceRel) }
        .thenWaitUntil { helper.assertTrue(stored() == 64L, "CD-TLK-08: the cell holds ${stored()} coal through the interface block") }
        .thenExecute { chest.setItem(0, ItemStack(Items.COAL, 64)) }
        .thenExecute { TestKit.bindTransfer(helper, scene.owner, scene.worker, chestRel, busRel, destinationFace = Direction.SOUTH) }
        .thenWaitUntil { helper.assertTrue(stored() == 128L, "CD-TLK-08: the cell holds ${stored()} coal through the interface part") }
        .thenSucceed()
}
```

  Deferred, because the claim and demand paths are Phase 1 engine code that the tests above run too:
  - CD-TLK-01 is the bind of `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination` (Task 6), from a chest to a barrel.
  - CD-TLK-05 (`cdTlk05TwoSourcesFeedOneBarrel`: one owner's Alakazam and Espeon, with different chests as sources, bind to
    one barrel. Both binds succeed, and the barrel holds no claim): GameTest deferred (high confidence): verified in the
    end-of-phase pass.
  - CD-TLK-06 (`cdTlk06EmptySourceOrFullDestinationMeansStandby`: an empty source, then a destination full of stone while
    the source holds dirt. Standby within 20 ticks in each case): GameTest deferred (high confidence): verified in the
    end-of-phase pass.

- [ ] **Step 6: The guide section.** Add `### Telekinesis` under `## Roles` in `docs/player-guide.md`, in the style of the
  Phase 1 role sections:

```markdown
### Telekinesis

A Psychic-type moves items from one container to another. Telekinesis is leash only, so a worker never starts it on its own.

1. Select the Pokémon with the Work Leash.
2. Right-click the source container on the face to take items from. The source must sit inside the pasture's work area.
3. Right-click the destination container on the face to put items into. The destination may sit anywhere within 32 blocks
   of the pasture block on each axis.

The action bar asks for the destination after the first click. A right-click on air, or 30 seconds, clears the first
click. The worker stands at the source and moves ceil(64 × level ÷ 100) items every second: 1 at level 1, 32 at level 50
and 64 at level 100. Only the source is reserved, so several workers can fill one destination, such as an AE2 ME Interface.
The faces count: a furnace takes fuel through its sides and input through its top. A worker waits in Standby while the
source is empty or the destination is full. The binding clears when the source or the destination is broken or replaced.

Role file parameters: `items_per_second_at_level_100` (64) and `destination_range` (32).
```

- [ ] **Step 7: Run** `./gradlew build` and `./gradlew runGameTestServer`.

- [ ] **Step 8: Multiplayer check.** The handler reads only server state through the work context. The batch runs on the
  server thread in the engine tick. Item entities from a spill go into the level, which every tracking client sees. The memo
  belongs to one claim.

- [ ] **Step 9: Commit:** `git commit -m "Move items between two containers with leashed Psychic-types [rag:1]"`.

---

### Task 5: The Magnet

**Rows:** CD-MAG-01, CD-MAG-02, CD-MAG-03, CD-MAG-04, CD-MAG-05, CD-MAG-06, CD-MAG-07, CD-MAG-08, CD-BAL-05 (binding),
CD-FX-02 (the Magnet row), CD-CFG-10 and CD-PLAT-05 (the item roles need no Create), CD-REL-08 (the guide section).

**Files:**
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/item/DroppedItems.kt`
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/handler/item/MagnetHandler.kt`
- Create: `src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/magnet.json`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt` (registers the handler)
- Modify: `docs/player-guide.md` (`### Magnet`)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/role/BalanceFilesTest.kt`, `BuiltInRoleFilesTest.kt`
- Test: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/MagnetTests.kt`
- Test: `src/gametestCore/kotlin/codes/honor/cobblemondynamics/coretest/CoreTests.kt`

**Interfaces:**
- Consumes: `ItemBatch`, `TransferParams`, `QueryClock` (Task 2), `WorkContext.pasture`, `WorkContext.sourceFace`,
  `WorkArea.box()` (Task 3), `ItemPorts.sink`, `ItemCapability` (Task 4).
- Produces:

```kotlin
/** The dropped items of a work area as an [ItemSource]: one slot for each item entity (CD-MAG-04, CD-MAG-05). */
internal class DroppedItems(private val entities: List<ItemEntity>) : ItemSource<ItemStack> {
    companion object {
        /** An item that a player may not pick up yet keeps its delay: the Magnet waits too (CD-MAG-05). */
        fun collectable(entity: ItemEntity): Boolean
        fun query(level: ServerLevel, area: WorkArea): List<ItemEntity>
    }
}

object MagnetHandler : TargetHandler   // id cobblemon_dynamics:magnet
```

`magnet.json`:

```json
{
  "schema": 1,
  "handler": "cobblemon_dynamics:magnet",
  "types": ["steel"],
  "auto_assign": false,
  "priority": 0,
  "range": 6.0,
  "params": {
    "items_per_second_at_level_100": 64
  },
  "effect": {
    "loop_ticks": 40,
    "idle_ticks": 40,
    "animations": ["special"],
    "target_particles": ["cobblemon:metal_sparks"]
  }
}
```

- [ ] **Step 1: Failing unit tests.** `BalanceFilesTest` replaces its Phase 1 check with the binding one:

```kotlin
/** One batch every 20 ticks, so the items per batch are the items per second (R52). Both files must exist. */
@Test
fun `CD-BAL-05 Telekinesis and Magnet move at most 64 items per second`() {
    for (name in listOf("telekinesis", "magnet")) {
        val params = TransferParams.of(BuiltInRoles.parse(name).params)
        val top = (1..100).maxOf(params::itemsPerBatch)
        assertTrue(top <= 64, "$name.json lets one worker move $top items per second")
        assertEquals(64, params.itemsPerBatch(100), name)
    }
}
```

  `BuiltInRoleFilesTest` gains the row `"magnet" to Row("cobblemon_dynamics:magnet", setOf("steel"), false, 0, "special")`
  and two tests:

```kotlin
@Test
fun `CD-MAG-02 the Magnet role file sets auto_assign to false`() {
    val key = autoAssignKey("magnet")
    assertTrue(key != null && key.isJsonPrimitive && key.asJsonPrimitive.isBoolean && !key.asBoolean, "magnet.json has auto_assign $key")
}

/** The Magnet plays its `special` pose and Cobblemon's metal sparks at its container. It names no move timeline. */
@Test
fun `CD-FX-04 the Magnet sparks at its container`() {
    val effect = BuiltInRoles.parse("magnet").effect
    assertEquals(listOf("special"), effect.animations)
    assertEquals(emptyList<String>(), effect.actorParticles.map { it.toString() })
    assertEquals(listOf("cobblemon:metal_sparks"), effect.targetParticles.map { it.toString() })
}
```

- [ ] **Step 2: Run them and see them fail:** `magnet.json` does not exist, so `BuiltInRoles.parse("magnet")` throws.

- [ ] **Step 3: Write the code.**

```kotlin
internal class DroppedItems(private val entities: List<ItemEntity>) : ItemSource<ItemStack> {
    override val slotCount: Int get() = entities.size

    override fun peek(slot: Int, max: Int): Lot<ItemStack>? =
        entities[slot].takeIf(::collectable)?.item?.let { Lot(it.copy(), minOf(max, it.count)) }

    override fun take(slot: Int, count: Int): Lot<ItemStack>? {
        val entity = entities[slot].takeIf(::collectable) ?: return null
        val stack = entity.item
        val n = minOf(count, stack.count)
        if (n == stack.count) entity.discard() else entity.item = stack.copyWithCount(stack.count - n)
        return Lot(stack.copyWithCount(n), n)
    }

    /** A batch puts back only what it took from the same entity, so the stack never grows past its count before the batch. */
    override fun putBack(slot: Int, lot: Lot<ItemStack>): Int {
        val entity = entities[slot]
        if (entity.isAlive) {
            entity.item = entity.item.copyWithCount(entity.item.count + lot.count)
        } else {
            entity.level().addFreshEntity(ItemEntity(entity.level(), entity.x, entity.y, entity.z, lot.item.copyWithCount(lot.count)))
        }
        return 0
    }

    companion object {
        fun collectable(entity: ItemEntity): Boolean = entity.isAlive && !entity.hasPickUpDelay() && !entity.item.isEmpty

        fun query(level: ServerLevel, area: WorkArea): List<ItemEntity> =
            level.getEntitiesOfClass(ItemEntity::class.java, area.box()) { collectable(it) && area.contains(it.blockPosition()) }
    }
}
```

```kotlin
/**
 * The Magnet: a Steel-type collects dropped items from its pasture's whole work area into its bound container, in one batch
 * every 20 ticks (R52). It looks for items at most once every 20 ticks (CD-MAG-08) and keeps what it found in the claim's memo.
 */
object MagnetHandler : TargetHandler {
    override val id: ResourceLocation = id("magnet")

    /** Leash only (CD-MAG-02, CD-ASG-05). */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        ItemCapability.exposes(level, pos, state, blockEntity)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = ItemCapability.exposes(level, canonical)

    /** CD-MAG-07: a dropped item in range that the container takes. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if (TransferParams.of(ctx.role.params).itemsPerBatch(ctx.pokemon.level) <= 0) return false
        val sink = sink(ctx) ?: return false
        return ItemBatch.canMove(DroppedItems(drops(ctx)), sink)
    }

    override fun onStart(ctx: WorkContext) {
        memo(ctx).startedAt = ctx.now
    }

    /** CD-MAG-03, CD-MAG-06. */
    override fun applyWork(ctx: WorkContext) {
        if (!TickClock.every(TransferParams.BATCH_TICKS, memo(ctx).startedAt, ctx.now)) return
        val sink = sink(ctx) ?: return
        val limit = TransferParams.of(ctx.role.params).itemsPerBatch(ctx.pokemon.level)
        ItemBatch.move(DroppedItems(drops(ctx)), sink, limit) { lot ->
            val at = Vec3.atCenterOf(ctx.target)
            Containers.dropItemStack(ctx.level, at.x, at.y + 0.6, at.z, lot.item.copyWithCount(lot.count))
        }
    }

    private class Memo(var startedAt: Long) {
        val clock = QueryClock()
        var found: List<ItemEntity> = emptyList()
        var face: BlockCapabilityCache<IItemHandler, Direction?>? = null
    }

    private fun memo(ctx: WorkContext): Memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }

    /** CD-MAG-04, CD-MAG-08: one query for the work area, at most once every 20 ticks. */
    private fun drops(ctx: WorkContext): List<ItemEntity> {
        val memo = memo(ctx)
        val pasture = ctx.pasture ?: return emptyList()
        if (memo.clock.isDue(ctx.now)) {
            memo.found = DroppedItems.query(ctx.level, WorkArea.of(pasture, WorkEngineSettings.current, PastureAdapter.maxWanderDistance()))
            memo.clock.ran(ctx.now)
        }
        return memo.found
    }

    /** Through the face that the player clicked. */
    private fun sink(ctx: WorkContext): ItemSink<ItemStack>? {
        val memo = memo(ctx)
        val cache = memo.face ?: BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, ctx.level, ctx.target, ctx.sourceFace)
            .also { memo.face = it }
        return cache.capability?.let(ItemPorts::sink)
    }
}
```

  Register it in `CobblemonDynamics`: `HandlerRegistry.register(MagnetHandler)`.

- [ ] **Step 4: Run the unit tests and see them pass.**

- [ ] **Step 5: GameTests** in `MagnetTests` (real item entities, their pickup delay and a real entity query, so rule 6
  keeps them). Each binds a Klink to a chest with `TestKit.bind`, which is the bind of CD-MAG-01. The template is
  `floor_23x10x23`, with the pasture at (11, 1, 11) and the chest at (11, 2, 8):

```kotlin
/** Items at horizontal offsets 8 and 9 from the pasture block: the Lv100 Klink takes the first and leaves the second (CD-MAG-01, CD-MAG-04). */
@GameTest(template = "floor_23x10x23", timeoutTicks = 200)
fun cdMag04OnlyItemsInsideTheWorkArea(helper: GameTestHelper) {
    val scene = scene(helper, "klink level=100")
    helper.assertTrue(TestKit.bind(helper, scene.owner, scene.worker, chestRel) != null, "CD-MAG-01: the bind to a chest was refused")
    val inside = drop(helper, BlockPos(19, 2, 11), ItemStack(Items.DIRT))
    val outside = drop(helper, BlockPos(20, 2, 11), ItemStack(Items.DIRT))
    helper.runAfterDelay(120) {
        helper.assertTrue(!inside.isAlive, "CD-MAG-04: the item at offset 8 is still on the ground")
        helper.assertTrue(outside.isAlive && outside.item.count == 1, "CD-MAG-04: the item at offset 9 was taken")
        helper.assertTrue(TestKit.countIn(helper, chestRel, Items.DIRT) == 1, "the chest holds ${TestKit.countIn(helper, chestRel, Items.DIRT)} dirt")
        helper.succeed()
    }
}

/** A drop with a 40-tick pickup delay stays on the ground while the delay lasts, and the Magnet takes it within 40 ticks after (CD-MAG-05). */
@GameTest(template = "floor_23x10x23", timeoutTicks = 300)
fun cdMag05PickupDelayIsRespected(helper: GameTestHelper) {
    val scene = scene(helper, "klink level=100")
    TestKit.bind(helper, scene.owner, scene.worker, chestRel)
    helper.startSequence()
        .thenWaitUntil { helper.assertTrue(TestKit.workerOf(helper, scene.worker)?.status == WorkerStatus.STANDBY, "not in Standby yet") }
        .thenExecute {
            val item = drop(helper, BlockPos(13, 2, 11), ItemStack(Items.DIRT)).apply { setPickUpDelay(40) }
            val droppedAt = helper.level.gameTime
            helper.onEachTick {
                val age = helper.level.gameTime - droppedAt
                if (age < 40) helper.assertTrue(item.isAlive, "CD-MAG-05: the Magnet took the item $age ticks after the drop")
                if (age == 80L) {
                    helper.assertFalse(item.isAlive, "CD-MAG-05: the item is still on the ground 40 ticks after its delay ended")
                    helper.succeed()
                }
            }
        }
}

/** A chest with room for 10 cobblestone, 64 cobblestone on the ground and a Lv100 Klink: the chest gains 10, and 54 stay on the ground (CD-MAG-06). */
@GameTest(template = "floor_23x10x23", timeoutTicks = 200)
fun cdMag06PartialInsertLeavesTheRest(helper: GameTestHelper) {
    val scene = scene(helper, "klink level=100")
    val chest = helper.getBlockEntity<ChestBlockEntity>(chestRel)
    for (slot in 0 until 26) chest.setItem(slot, ItemStack(Items.DIRT, 64))
    chest.setItem(26, ItemStack(Items.COBBLESTONE, 54))
    TestKit.bind(helper, scene.owner, scene.worker, chestRel)
    val stack = drop(helper, BlockPos(13, 2, 11), ItemStack(Items.COBBLESTONE, 64))
    helper.runAfterDelay(100) {
        helper.assertTrue(TestKit.countIn(helper, chestRel, Items.COBBLESTONE) == 64, "CD-MAG-06: the chest gained more or less than 10")
        helper.assertTrue(stack.isAlive && stack.item.count == 54, "CD-MAG-06: the ground stack holds ${stack.item.count}")
        helper.succeed()
    }
}
```

  `scene` places the chest at `chestRel` = (11, 2, 8), beside the pasture. `drop(helper, rel, stack)` spawns an `ItemEntity`
  at the bottom center of `rel` with no motion and no pickup delay, and returns it. Deferred:
  - CD-MAG-07 (`cdMag07NoItemsOrAFullChestMeansStandby`: no items on the ground, then a full chest with items on the
    ground. Standby within 20 ticks in each case): GameTest deferred (high confidence): verified in the end-of-phase pass.

  In `CoreTests` (the run without Create and AE2):

```kotlin
/** Telekinesis and the Magnet name no Create class, so they work in a pack without Create (CD-PLAT-05, CD-CFG-10). */
@GameTest(template = "floor_9x5x9")
fun cdCfg10ItemRolesNeedNoCreate(helper: GameTestHelper) {
    for (name in listOf("telekinesis", "magnet")) helper.assertTrue(RoleRegistry.current.get(id(name)) != null, "$name is off without Create")
    helper.succeed()
}
```

- [ ] **Step 6: The guide section.** Add `### Magnet` under `## Roles` in `docs/player-guide.md`:

```markdown
### Magnet

A Steel-type collects dropped items into a container. The Magnet is leash only: select the Pokémon with the Work Leash, then
right-click the container. The worker stands at the container and collects from the whole work area of its pasture,
ceil(64 × level ÷ 100) items every second: 1 at level 1, 32 at level 50 and 64 at level 100. It leaves an item alone while
the item still has a pickup delay, so a drop that a player just threw stays theirs for a moment. What does not fit stays on
the ground. A worker waits in Standby while no item lies in range or the container is full.

Role file parameter: `items_per_second_at_level_100` (64).
```

- [ ] **Step 7: Run** `./gradlew build`, `./gradlew runGameTestServer` and `./gradlew runGameTestServerCore`.

- [ ] **Step 8: Multiplayer check.** The query reads the level on the server thread. A taken item is discarded or shrunk on
  the server, and tracking clients see the entity change. Two Magnets of two players that share a work area read their own
  memo, and `DroppedItems.take` checks `collectable` again, so a second worker never takes an item that the first one
  already took.

- [ ] **Step 9: Commit:** `git commit -m "Collect dropped items into a container with leashed Steel-types [rag:1]"`.

---

### Task 6: The Phase 1 leash rows on the real Telekinesis role

**Rows:** CD-LSH-16, CD-LSH-17, CD-LSH-18, CD-LSH-19, CD-LSH-20, CD-SEC-11, CD-TLK-01 (the chest-to-barrel bind).

**Files:**
- Delete: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/TestTransferHandler.kt`
- Delete: `src/gametest/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/test_transfer.json`
- Modify: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/DynamicsTestMod.kt` (no `TestTransferHandler`)
- Modify: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/LeashTests.kt` (role id `telekinesis`, KDoc)
- Modify: `src/gametest/kotlin/codes/honor/cobblemondynamics/test/create/CreateLeashTests.kt` (CD-LSH-16 on a basin)
- Modify: `src/test/kotlin/codes/honor/cobblemondynamics/leash/BindingTest.kt`, `BindRulesTest.kt` (role id `telekinesis`)
- Modify: `src/test/kotlin/codes/honor/cobblemondynamics/role/DefaultPriorityTest.kt` (CD-LSH-16 with the default files)
- Modify: `docs/testing/manual-checklist.md` (check 20 runs in the Phase 2 play-test)

**Interfaces:**
- Consumes: `telekinesis.json` and `TelekinesisHandler` (Task 4), `pump.json` (Phase 1).
- Produces: no test names `test_transfer` any more.

- [ ] **Step 1: Failing unit test** in `DefaultPriorityTest`:

```kotlin
/** Both leash-only roles fit a Create basin, which exposes a fluid and an item handler. Both have priority 0, and `pump` sorts first. */
@Test
fun `CD-LSH-16 with the default files a Slowbro on a basin gets the Pump, not Telekinesis`() {
    val pump = BuiltInRoles.parse("pump")
    val telekinesis = BuiltInRoles.parse("telekinesis")
    assertEquals(pump.priority, telekinesis.priority)
    val slowbro = listOf("water", "psychic")
    assertEquals(pump, RoleSelection.best(listOf(telekinesis, pump).filter { RoleSelection.isEligible(it, slowbro) }))
}
```

- [ ] **Step 2: Run it.** It passes once Task 4's file exists, so check that it fails when `telekinesis.json` has
  `"priority": 1`, then restore the file.

- [ ] **Step 3: Switch the tests to the real role.**
  - Delete the two test-role files and the `HandlerRegistry.register(TestTransferHandler)` line.
  - In `LeashTests.cdLsh17Lsh18Lsh20SourceThenDestination`, check `binding?.roleId == id("telekinesis")`, and say in the
    KDoc that the real Telekinesis role binds a chest to a barrel (CD-TLK-01, CD-LSH-17, CD-LSH-18, CD-LSH-20).
  - In the KDoc of `cdLsh19AirClickAndTimeoutClearThePendingSource` and `cdSec11CancelledClickNeverBinds`, name the real
    role. Their assertions stay as they are.
  - In `BindingTest` and `BindRulesTest`, name the role `telekinesis`.
  - In the manual checklist, check 20 loses its `(Phase 2)` mark and runs in the Phase 2 play-test.

- [ ] **Step 4: GameTest** in `CreateLeashTests` (both handlers meet a real Create basin, so rule 6 keeps it):

```kotlin
/** A Water- and Psychic-type Slowbro clicks a Create basin, which both the Pump and Telekinesis match: the Pump binds with one click (CD-LSH-16). */
@GameTest(template = "floor_15x6x15", timeoutTicks = 100)
fun cdLsh16SlowbroOnABasinBindsAsPump(helper: GameTestHelper) {
    val basinRel = BlockPos(5, 2, 4)
    CreateKit.basin(helper, basinRel)
    val (owner, pasture) = owner(helper)
    val slowbro = TestKit.tether(helper, pasture, owner, "slowbro level=30")
    helper.runAfterDelay(1) {
        val binding = TestKit.bind(helper, owner, slowbro, basinRel)
        helper.assertTrue(binding?.roleId == id("pump") && binding.destination == null, "CD-LSH-16: the click gave $binding")
        helper.succeed()
    }
}
```

- [ ] **Step 5: Run** `./gradlew build`, then `./gradlew runGameTestServer` twice in a row. Search the code for
  `test_transfer`: no file may name it.

- [ ] **Step 6: Multiplayer check.** The selection and the pending source live on each player's own Work Leash stack, as
  data components, and the server reads only the clicking player's stack (CD-SEC-08). Two players with pending sources at
  the same time therefore never see each other's.

- [ ] **Step 7: Commit, push, open the pull request.** `git commit -m "Run the leash rows on the real Telekinesis role and delete the test role [rag:1]"`.
  Then follow the rehydration note's pull request steps (HAR-809): ktlint, ruff, the super-linter command,
  `./gradlew build runGameTestServer runGameTestServerCore runGameTestServerPerf`, `py scripts/check_plan_coverage.py --phase 2`,
  then push the branch and open the draft pull request by hand. The body lists the deferred rows of Tasks 3 to 5 and every
  deviation from this file.
