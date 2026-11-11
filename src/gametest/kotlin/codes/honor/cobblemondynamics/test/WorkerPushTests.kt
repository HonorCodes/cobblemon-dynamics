package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * Workers on one row of cell centres. Minecraft pushes two entities apart along the line between their centres, so two workers that
 * meet head-on in a row push each other straight back, and a walker pushes a Working worker off its spot, which then walks back into
 * it. Without a fix each trip stalls until `travelTimeoutTicks` (600), and CD-ASG-14 then keeps the worker off its target for 6,000
 * ticks (carry-over item of the development notes). The scenes use a corridor one block wide, so no worker can step aside.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class WorkerPushTests {
    private val west = BlockPos(3, 1, 11)
    private val east = BlockPos(19, 1, 11)

    /** A Work Mode pasture south of a corridor along z = 11, from x = 3 to x = 19, walled two blocks high. */
    private fun corridor(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 14), owner)
        TestKit.workMode(pasture, true)
        for (x in 2..20) {
            for (y in 2..3) {
                helper.setBlock(BlockPos(x, y, 10), Blocks.STONE)
                helper.setBlock(BlockPos(x, y, 12), Blocks.STONE)
            }
        }
        for (y in 2..3) {
            helper.setBlock(BlockPos(2, y, 11), Blocks.STONE)
            helper.setBlock(BlockPos(20, y, 11), Blocks.STONE)
        }
        return owner to pasture
    }

    private fun key(helper: GameTestHelper, rel: BlockPos): TargetKey = TestKit.keyOf(helper, helper.absolutePos(rel))

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, rel: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == key(helper, rel) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    private fun where(helper: GameTestHelper, entity: PokemonEntity): String {
        val worker = TestKit.workerOf(helper, entity)
        val rel = TestKit.relative(helper, entity.blockPosition())
        val spot = worker?.spot?.let { TestKit.relative(helper, it) }
        return "${worker?.status} at $rel (x ${"%.2f".format(entity.x)}), spot $spot, claim ${TestKit.claimOf(helper, entity)}"
    }

    /**
     * Charmander A stands at the corridor's west end and B at its east end. Each may take only the furnace at the far end, so A's work
     * spot is at the east end and B's at the west end, and they walk toward each other along the corridor's one row. Both pass and
     * Work within 300 ticks, long before a travel timeout.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 800)
    fun workersMeetingHeadOnInOneRowPassAndWork(helper: GameTestHelper) {
        val (owner, pasture) = corridor(helper)
        val a = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val b = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val westEnd = BlockPos(4, 2, 11)
        val eastEnd = BlockPos(18, 2, 11)
        var placed = false
        var claimedAt = -1L
        helper.onEachTick {
            val workerA = TestKit.workerOf(helper, a) ?: return@onEachTick
            val workerB = TestKit.workerOf(helper, b) ?: return@onEachTick
            if (!placed) {
                workerA.skipUntil[key(helper, west)] = Long.MAX_VALUE
                workerB.skipUntil[key(helper, east)] = Long.MAX_VALUE
                TestKit.furnace(helper, west, ItemStack(Items.RAW_IRON, 64))
                TestKit.furnace(helper, east, ItemStack(Items.RAW_IRON, 64))
                placed = true
            }
            if (claimedAt < 0) {
                if (TestKit.claimOf(helper, a) == null || TestKit.claimOf(helper, b) == null) {
                    TestKit.moveTo(helper, a, westEnd)
                    TestKit.moveTo(helper, b, eastEnd)
                    return@onEachTick
                }
                val spotA = workerA.spot?.let { TestKit.relative(helper, it) }
                val spotB = workerB.spot?.let { TestKit.relative(helper, it) }
                helper.assertTrue(
                    spotA != null && spotA.x >= 14 && spotA.z == 11,
                    "A's spot is not in the corridor's east part: ${where(helper, a)}",
                )
                helper.assertTrue(
                    spotB != null && spotB.x <= 8 && spotB.z == 11,
                    "B's spot is not in the corridor's west part: ${where(helper, b)}",
                )
                claimedAt = helper.tick
                return@onEachTick
            }
            if (isWorkingOn(helper, a, east) && isWorkingOn(helper, b, west)) {
                helper.succeed()
                return@onEachTick
            }
            val waited = helper.tick - claimedAt
            helper.assertTrue(waited < 300, "the workers did not pass: A ${where(helper, a)}; B ${where(helper, b)}")
        }
    }

    /**
     * Two Working Charmanders do not push each other: a push between them leaves both motions as they were. The same Working worker
     * still pushes and is pushed by an Idle pasture mate, a wild Charmander and a player.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun onlyTwoDirectedWorkersSkipTheirPush(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.WORKER_PUSH), "the worker push mixin did not apply")
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val first = TestKit.furnace(helper, BlockPos(3, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val second = TestKit.furnace(helper, BlockPos(11, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val pair = List(2) { TestKit.tether(helper, pasture, owner, "charmander level=10") }
        val wild = TestKit.wild(helper, BlockPos(7, 2, 12), "charmander level=10")
        val player = TestKit.owner(helper, "player")
        TestKit.place(player, wild.x + 2.0, wild.y, wild.z)
        var idle: PokemonEntity? = null
        helper.onEachTick {
            val furnaces = setOf(TestKit.keyOf(helper, first), TestKit.keyOf(helper, second))
            val working = pair.all {
                TestKit.workerOf(helper, it)?.status == WorkerStatus.WORKING &&
                    TestKit.claimOf(helper, it)?.key in furnaces
            }
            if (!working) return@onEachTick
            val mate = idle ?: TestKit.tether(helper, pasture, owner, "squirtle level=10").also { idle = it }
            if (TestKit.workerOf(helper, mate)?.status != WorkerStatus.IDLE) return@onEachTick
            val (a, b) = pair
            fun moves(pusher: Entity, pushed: LivingEntity): Boolean {
                val before = listOf(pusher.deltaMovement, pushed.deltaMovement)
                pushed.push(pusher)
                return listOf(pusher.deltaMovement, pushed.deltaMovement) != before
            }
            helper.assertFalse(moves(a, b), "two Working workers pushed each other")
            helper.assertFalse(moves(b, a), "two Working workers pushed each other")
            for ((what, other) in listOf("an Idle pasture mate" to mate, "a wild Charmander" to wild)) {
                helper.assertTrue(moves(other, a), "$what did not push a Working worker")
                helper.assertTrue(moves(a, other), "a Working worker did not push $what")
            }
            helper.assertTrue(moves(player, a), "a player did not push a Working worker")
            helper.assertTrue(moves(a, player), "a Working worker did not push a player")
            helper.succeed()
        }
    }

    /**
     * Charmander W works a furnace in the corridor's north wall from the middle of the corridor. Charmander A walks from the west end
     * to the furnace at the east end, through W's spot. A passes and Works within 300 ticks, and W keeps Working.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 800)
    fun aWalkerPassesAWorkingWorkerInOneRow(helper: GameTestHelper) {
        val (owner, pasture) = corridor(helper)
        val middle = BlockPos(11, 2, 10)
        val w = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val a = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val westEnd = BlockPos(4, 2, 11)
        var placed = false
        var eastPlaced = false
        var walkingAt = -1L
        helper.onEachTick {
            val workerW = TestKit.workerOf(helper, w) ?: return@onEachTick
            val workerA = TestKit.workerOf(helper, a) ?: return@onEachTick
            if (!placed) {
                workerW.skipUntil[key(helper, east)] = Long.MAX_VALUE
                workerA.skipUntil[key(helper, middle)] = Long.MAX_VALUE
                TestKit.furnace(helper, middle, ItemStack(Items.RAW_IRON, 64))
                placed = true
            }
            if (!isWorkingOn(helper, w, middle)) {
                helper.assertTrue(walkingAt < 0, "W stopped Working while A walked past: ${where(helper, w)}")
                TestKit.moveTo(helper, w, BlockPos(11, 2, 11))
                TestKit.moveTo(helper, a, westEnd)
                return@onEachTick
            }
            if (walkingAt < 0) {
                if (!eastPlaced) TestKit.furnace(helper, east, ItemStack(Items.RAW_IRON, 64))
                eastPlaced = true
                if (TestKit.claimOf(helper, a) == null) {
                    TestKit.moveTo(helper, a, westEnd)
                    return@onEachTick
                }
                val spotA = workerA.spot?.let { TestKit.relative(helper, it) }
                helper.assertTrue(
                    spotA != null && spotA.x >= 14 && spotA.z == 11,
                    "A's spot is not in the corridor's east part: ${where(helper, a)}",
                )
                walkingAt = helper.tick
                return@onEachTick
            }
            if (isWorkingOn(helper, a, east)) {
                helper.succeed()
                return@onEachTick
            }
            val waited = helper.tick - walkingAt
            helper.assertTrue(waited < 300, "A did not pass the Working worker: A ${where(helper, a)}; W ${where(helper, w)}")
        }
    }
}
