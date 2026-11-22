package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * CD-ASG-27: an unbound level 50 Torterra at its species' size, 2.25 blocks wide, walks past its own pasture block to an oak sapling
 * that it grows as the Cultivator. Cobblemon's pathfinder checks 2 cells across for it, but navigation steers its body 0.625 into a
 * third row, so a path that runs beside the pasture block takes the body straight into the block's face. Before the fix, each scene
 * held the Torterra there until its travel timed out (CD-ASG-13), in 20 of 20 runs. Now the engine steps it sideways, clear of the
 * block, and it Works before the timeout, in 20 of 20 runs (the slowest after 505 ticks).
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class WidePastureTests {
    /** The Torterra walks west, its body's south row along the pasture block's row. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdAsg27TorterraFromTheEastPassesThePasture(helper: GameTestHelper) = run(helper, Vec3(15.5, 2.0, 10.5), BlockPos(6, 2, 10))

    /** The Torterra walks north, its body's east row along the pasture block's row. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdAsg27TorterraFromTheSouthPassesThePasture(helper: GameTestHelper) = run(helper, Vec3(10.5, 2.0, 15.5), BlockPos(10, 2, 6))

    /** The Torterra walks south, 2 blocks west of the pasture block, and its path turns east into it. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdAsg27TorterraFromTheNorthPassesThePasture(helper: GameTestHelper) = run(helper, Vec3(9.5, 2.0, 7.5), BlockPos(9, 2, 15))

    /** The Torterra walks east, 2 blocks north of the pasture block, and its path turns south into it. */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdAsg27TorterraFromTheWestPassesThePasture(helper: GameTestHelper) = run(helper, Vec3(7.5, 2.0, 9.5), BlockPos(15, 2, 9))

    /**
     * The Torterra stands on [startRel] with its wander paused until its claim on the sapling at [saplingRel]. Work Mode comes on only
     * then, so that its claim and its path start there. It must Work before its travel timeout releases the claim.
     */
    private fun run(helper: GameTestHelper, startRel: Vec3, saplingRel: BlockPos) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, PASTURE, owner)
        dry(helper, pasture.blockPos)
        helper.setBlock(saplingRel.below(), Blocks.DIRT)
        helper.setBlock(saplingRel, Blocks.OAK_SAPLING)
        val pokemon = TestKit.pokemon("torterra level=50")
        // Cobblemon rolls each size within about 5%, so the size is fixed at the species' own.
        pokemon.scaleModifier = 1f
        Cobblemon.storage.getPC(owner).add(pokemon)
        if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for the Torterra")
        val torterra = TestKit.pasturedEntity(helper, pasture, pokemon.uuid) ?: throw GameTestAssertException("no Torterra after tether")
        helper.assertTrue(torterra.bbWidth > 2f, "the Torterra is ${torterra.bbWidth} wide, not 2 to 3 blocks")
        val start = helper.absoluteVec(startRel)
        var claimedAt = -1L
        helper.onEachTick {
            if (TestKit.claimOf(helper, torterra) == null) {
                helper.assertTrue(claimedAt < 0, "CD-ASG-27: the Torterra missed its arrival, and its claim timed out")
                BrainBridge.pauseWander(torterra, HOLD_PAUSE_TICKS)
                if (Vec3(torterra.x - start.x, 0.0, torterra.z - start.z).length() >
                    HOLD_DRIFT
                ) {
                    torterra.teleportTo(start.x, start.y, start.z)
                }
                if (helper.tick == WORK_MODE_TICK) TestKit.workMode(pasture, true)
                helper.assertTrue(helper.tick < CLAIM_LIMIT, "the Torterra has no claim $CLAIM_LIMIT ticks into the test")
                return@onEachTick
            }
            val worker = TestKit.workerOf(helper, torterra) ?: throw GameTestAssertException("the Torterra is no worker")
            if (claimedAt < 0) claimedAt = worker.claimTick
            if (worker.status == WorkerStatus.WORKING) helper.succeed()
        }
    }

    /**
     * Cobblemon's default pasture state is waterlogged, which a player's placement on dry land is not, and the water marks the cells
     * around it as beside water for the pathfinder. Both halves of the pasture at [pos] lose the water.
     */
    private fun dry(helper: GameTestHelper, pos: BlockPos) {
        for (half in listOf(pos, pos.above())) {
            val state = helper.level.getBlockState(half)
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                helper.level.setBlock(half, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS)
            }
        }
    }

    private companion object {
        val PASTURE = BlockPos(11, 1, 11)
        const val WORK_MODE_TICK = 3L
        const val CLAIM_LIMIT = 300L
        const val HOLD_DRIFT = 0.02
        const val HOLD_PAUSE_TICKS = 2L
    }
}
