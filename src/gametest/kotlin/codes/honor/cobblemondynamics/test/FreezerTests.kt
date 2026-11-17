package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Freezer on real water (ADR-0019). GameTest chunks get no random ticks, so no ice here melts and no water here freezes on its own:
 * every ice block comes from the Freezer.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class FreezerTests {
    /** The cells of the floor at x in [xs] and z in [zs]. */
    private fun cells(xs: IntRange, zs: IntRange): List<BlockPos> = xs.flatMap { x -> zs.map { z -> BlockPos(x, 1, z) } }

    /**
     * A water source in the floor at each of [cells]. The layer under the template's floor is air, so stone goes under each source
     * first, and no water flows.
     */
    private fun water(helper: GameTestHelper, cells: List<BlockPos>) {
        for (cell in cells) helper.setBlock(cell.below(), Blocks.STONE)
        for (cell in cells) helper.setBlock(cell, Blocks.WATER)
    }

    private fun isWater(helper: GameTestHelper, rel: BlockPos): Boolean = helper.getBlockState(rel).`is`(Blocks.WATER)

    private fun isIce(helper: GameTestHelper, rel: BlockPos): Boolean = helper.getBlockState(rel).`is`(Blocks.ICE)

    /** The test claim provider refuses the absolute position of [rel] in [set] until the test ends. */
    private fun refuse(helper: GameTestHelper, rel: BlockPos, set: MutableSet<BlockPos>) {
        val absolute = helper.absolutePos(rel)
        set += absolute
        TestKit.afterTest(helper) { set -= absolute }
    }

    /**
     * CD-FRZ-12, CD-FRZ-03, CD-FRZ-05: a level 100 Snom beside a pond of 9 × 9 water sources ices it over within 6,000 ticks, works
     * from the new ice, and never holds a claim on ice for more than 20 ticks. CD-FRZ-06: a farm channel beside farmland stays water.
     * CD-FRZ-10, CD-FRZ-11: in a pond of 3 × 3, the test claim provider refuses the use question of one source and the break question of
     * another. The Snom asks about both, as the stall of each shows, and both stay water while the rest of that pond freezes.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = TIMEOUT_TICKS, batch = BATCH)
    fun cdFrz12FreezerIcesOverAPond(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        // The work area runs from 1 to 21, so it holds the pond's shore on all four sides.
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        val pond = cells(2..10, 2..10)
        val small = cells(16..18, 16..18)
        val channel = cells(16..19, 4..4)
        for (cell in cells(16..19, 3..3)) helper.setBlock(cell, Blocks.FARMLAND)
        water(helper, pond + small + channel)
        val refusedUse = BlockPos(17, 1, 17)
        val refusedBreak = BlockPos(16, 1, 18)
        val refused = listOf(refusedUse, refusedBreak)
        refuse(helper, refusedUse, TestLandClaims.refused)
        refuse(helper, refusedBreak, TestLandClaims.refusedBreak)
        val freezes = pond + small - refused.toSet()
        val snom = TestKit.tether(helper, pasture, owner, "snom level=100")
        var onIce = 0
        var fromIce = false
        var claims = 0
        var lastClaim: BlockPos? = null
        // The state of the scene just before the timeout, so that a failure says how far the Snom got.
        helper.runAtTickTime(TIMEOUT_TICKS - 10L) {
            val worker = TestKit.workerOf(helper, snom)
            val role = TestKit.engine(helper).roleOf(snom.pokemon.uuid)?.definition?.id
            val stalls = refused.map { cell -> worker?.isStalled(TestKit.keyOf(helper, helper.absolutePos(cell)), helper.level.gameTime) }
            helper.fail(
                "the pond has ${pond.count { isIce(helper, it) }} of ${pond.size} cells frozen and the small pond " +
                    "${small.count { isIce(helper, it) }} of ${small.size}; the Snom is ${worker?.status} as $role at " +
                    "${TestKit.relative(helper, snom.blockPosition())}, spot ${worker?.spot?.let { TestKit.relative(helper, it) }}, " +
                    "after $claims claims, the last on $lastClaim; stalls on the refused cells $stalls; worked from the ice $fromIce",
            )
        }
        helper.onEachTick {
            for (cell in channel) helper.assertTrue(isWater(helper, cell), "CD-FRZ-06: the farm channel froze at $cell")
            for (cell in refused) helper.assertTrue(isWater(helper, cell), "CD-FRZ-10: the refused source at $cell froze")
            val worker = TestKit.workerOf(helper, snom) ?: return@onEachTick
            val held = (TestKit.claimOf(helper, snom)?.key as? TargetKey.Block)?.let { TestKit.relative(helper, BlockPos.of(it.pos)) }
            if (held != null && held != lastClaim) {
                claims++
                lastClaim = held
            }
            onIce = if (held != null && isIce(helper, held)) onIce + 1 else 0
            helper.assertTrue(onIce <= 20, "CD-FRZ-12: the Snom held its claim on ice for $onIce ticks")
            val spot = worker.spot
            if (worker.status == WorkerStatus.WORKING && spot != null && helper.level.getBlockState(spot.below()).`is`(Blocks.ICE)) {
                fromIce = true
            }
            if (freezes.any { !isIce(helper, it) }) return@onEachTick
            val now = helper.level.gameTime
            for (cell in refused) {
                // The pond froze around the refused cells, so the Snom asked about each one before it froze its own cell there.
                val asked = worker.isStalled(TestKit.keyOf(helper, helper.absolutePos(cell)), now)
                helper.assertTrue(asked, "CD-FRZ-11: the Snom has no stall on the refused source at $cell")
            }
            helper.assertTrue(fromIce, "CD-FRZ-12: the Snom never worked from the ice")
            helper.succeed()
        }
    }

    /** A pond of 9 × 9 beside a pasture needs more than the default work radius of 8, so the batch has a radius of 10. */
    @BeforeBatch(batch = BATCH)
    fun wideArea(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        val current = WorkEngineSettings.current
        saved = current
        WorkEngineSettings.current = current.copy(horizontalRadius = RADIUS)
    }

    @AfterBatch(batch = BATCH)
    fun normalArea(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        WorkEngineSettings.current = saved ?: EngineSettings.DEFAULT
        saved = null
    }

    companion object {
        const val BATCH = "freezer"
        private const val RADIUS = 10

        /** CD-FRZ-12: every source of the pond is ice within 6,000 ticks. */
        private const val TIMEOUT_TICKS = 6_000

        /** The settings before the batch. Batches run one after another, so one slot is enough. */
        private var saved: EngineSettings? = null
    }
}
