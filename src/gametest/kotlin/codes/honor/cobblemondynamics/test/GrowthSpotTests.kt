package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * CD-ASG-24, CD-ASG-25, R94: a worker never ends up inside the tree that grows at its target. The approver's report from COHO
 * (2026-11-19): "it looks like the lumberjacks/tree cultivators get stuck in the tree they grow because they stand on the tree." Each
 * scene checks the worker's body on the tick that the tree grows, after the engine's own work of that tick.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class GrowthSpotTests {
    /** An oak on dirt at [base], 6 logs high, with the natural leaves of `DualTypeTests`, so that its fell drops a sapling. */
    private fun oak(helper: GameTestHelper, base: BlockPos) {
        helper.setBlock(base.below(), Blocks.DIRT)
        val logs = (0 until 6).map { base.above(it) }
        for (log in logs) helper.setBlock(log, Blocks.OAK_LOG)
        fun layer(y: Int, radius: Int, round: Boolean) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val pos = BlockPos(base.x + dx, y, base.z + dz)
                    if ((round && abs(dx) == radius && abs(dz) == radius) || pos in logs) continue
                    helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState())
                }
            }
        }
        for (y in base.y + 3..base.y + 5) layer(y, 3, round = false)
        layer(base.y + 6, 3, round = true)
        layer(base.y + 7, 2, round = true)
    }

    /** Fails the test when the body of [entity] overlaps a block, which the tree at [tree] just filled. */
    private fun assertFree(helper: GameTestHelper, entity: PokemonEntity, tree: BlockPos, row: String) {
        val box = entity.boundingBox.deflate(1e-3)
        val at = TestKit.relative(helper, entity.blockPosition())
        val spot = TestKit.workerOf(helper, entity)?.spot?.let { TestKit.relative(helper, it) }
        helper.assertTrue(
            helper.level.noBlockCollision(entity, box),
            "$row: the ${entity.pokemon.species.name} (${entity.bbWidth} wide, ${entity.bbHeight} high) is inside the tree that grew at " +
                "$tree: it stands at $at, spot $spot, box $box",
        )
    }

    /**
     * CD-ASG-24, CD-LSH-30: a level 50 Nincada, Bug/Ground, at 3 times its size, so 1.2 blocks wide, as a worker wider than a block, bound
     * to an oak. It fells the oak, replants it, and grows the sapling as the Cultivator. From a cell beside the sapling its body reaches
     * into the sapling's cell, where the trunk grows. The new oak must not grow around the Nincada.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 4_000)
    fun cdAsg24NincadaGrowsItsOakFromBesideTheSapling(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val base = BlockPos(5, 2, 9)
        oak(helper, base)
        val nincada = TestKit.tether(helper, pasture, owner, "nincada level=50")
        nincada.pokemon.scaleModifier = 3f
        nincada.refreshDimensions()
        helper.assertTrue(nincada.bbWidth > 1f, "the Nincada is ${nincada.bbWidth} blocks wide")
        val bound = TestKit.bind(helper, owner, nincada, base.above(2))
        helper.assertTrue(bound?.roleId == id("lumberjack"), "CD-LSH-16: the Nincada bound to the oak as ${bound?.roleId}")
        var felled = false
        var replanted = false
        helper.onEachTick {
            val state = helper.getBlockState(base)
            if (!state.`is`(Blocks.OAK_LOG)) felled = true
            if (felled && state.`is`(Blocks.OAK_SAPLING)) replanted = true
            if (replanted && state.`is`(Blocks.OAK_LOG)) {
                assertFree(helper, nincada, base, "CD-ASG-24")
                helper.succeed()
            }
        }
    }

    /**
     * CD-ASG-24, CD-ASG-25: an unbound level 100 Torterra, Grass/Ground, 2.25 blocks wide and 2.4 high, grows a lone oak sapling as an
     * auto Cultivator. From a cell beside the sapling its body reaches into the sapling's cell, where the trunk grows, and the oak's
     * lowest leaves can reach its head wherever its spot is.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 3_000)
    fun cdAsg24TorterraGrowsAnOakAsAnAutoCultivator(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val sapling = BlockPos(6, 2, 9)
        helper.setBlock(sapling.below(), Blocks.DIRT)
        helper.setBlock(sapling, Blocks.OAK_SAPLING)
        val torterra = TestKit.tether(helper, pasture, owner, "torterra level=100")
        // Cobblemon's tether finds room for so wide a body on top of the floor's neighbours, so the test holds it on the floor east of
        // the sapling until its first claim, as `WideWorkerTests` holds its Camerupt.
        val hold = BlockPos(11, 2, 9)
        var claimed = false
        helper.onEachTick {
            if (!claimed && TestKit.claimOf(helper, torterra) == null) TestKit.moveTo(helper, torterra, hold) else claimed = true
            if (helper.getBlockState(sapling).`is`(Blocks.OAK_LOG)) {
                assertFree(helper, torterra, sapling, "CD-ASG-24, CD-ASG-25")
                helper.succeed()
            }
        }
    }

    /**
     * CD-ASG-24: a level 50 Caterpie, Bug, bound as the Lumberjack to a 2 × 2 of dark oak saplings, waits for its tree, and an unbound
     * level 100 Diglett grows the square as the Cultivator. The Caterpie comes from the side of the square's far corner, so before the
     * fix its nearest spot beside the corner was a sapling cell of the square, where the giant trunk grows.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 3_000)
    fun cdAsg24LumberjackWaitsOutsideAGiantTreesSquare(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val corner = BlockPos(7, 2, 9)
        val square = listOf(corner, corner.east(), corner.south(), corner.east().south())
        for (cell in square) {
            helper.setBlock(cell.below(), Blocks.DIRT)
            helper.setBlock(cell, Blocks.DARK_OAK_SAPLING)
        }
        val caterpie = TestKit.tether(helper, pasture, owner, "caterpie level=50")
        val bound = TestKit.bind(helper, owner, caterpie, corner.east().south())
        helper.assertTrue(bound?.roleId == id("lumberjack"), "CD-LSH-16: the Caterpie bound to the saplings as ${bound?.roleId}")
        helper.assertTrue(bound?.target == helper.absolutePos(corner), "CD-LUM-03: the binding names ${bound?.target}")
        val hold = BlockPos(11, 2, 12)
        var held = true
        var diglett: PokemonEntity? = null
        helper.onEachTick {
            if (held && TestKit.claimOf(helper, caterpie) == null) {
                TestKit.moveTo(helper, caterpie, hold)
            } else if (held) {
                held = false
                // The Cultivator comes once the Lumberjack holds its spot, so the order of the two claims is fixed.
                diglett = TestKit.tether(helper, pasture, owner, "diglett level=100")
            }
            if (diglett != null && helper.getBlockState(corner).`is`(Blocks.DARK_OAK_LOG)) {
                assertFree(helper, caterpie, corner, "CD-ASG-24")
                helper.succeed()
            }
        }
    }
}
