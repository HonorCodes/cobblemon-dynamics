package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * R94: a Cobblemon of two types does the work of each of its roles on its bound target. The tests' fake players never join the level,
 * so no crop or sapling grows by random ticks here: every growth comes from a worker's growth use (CD-CUL-05).
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class DualTypeTests {
    /** The stages of one bound tree: a tree stands, the base holds a sapling after the fell, and a tree stands again. */
    private class Cycle(val base: BlockPos) {
        var felled = 0
        var replanted = false
        var regrown = false
        var wasLog = false
    }

    /**
     * An oak on dirt at [base], 6 logs high, with the 210 natural leaves of `LumberjackTests`, so that its fell drops no sapling about
     * once in 47,000 runs.
     */
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

    /**
     * Follows [cycle] on this tick. A log on the base after a sapling there is the regrowth, and a base that held a log and holds none
     * now is a fell. Returns true once the base was felled, replanted, regrown and felled again.
     */
    private fun follow(helper: GameTestHelper, cycle: Cycle): Boolean {
        val state = helper.getBlockState(cycle.base)
        val log = state.`is`(Blocks.OAK_LOG)
        if (cycle.wasLog && !log) cycle.felled++
        if (state.`is`(Blocks.OAK_SAPLING) && cycle.felled > 0) cycle.replanted = true
        if (log && cycle.replanted) cycle.regrown = true
        cycle.wasLog = log
        return cycle.regrown && cycle.felled >= 2
    }

    private fun assertBound(helper: GameTestHelper, entity: PokemonEntity, role: String, row: String) {
        val binding = BindingStore.read(entity.pokemon)
        helper.assertTrue(binding?.roleId == id(role), "$row: the ${entity.pokemon.species.name} holds the binding $binding")
    }

    /**
     * CD-LSH-30, CD-LSH-31, CD-LSH-33, R94: a level 50 Nincada, Bug/Ground, bound by a click on an oak's base log fells the oak, its
     * Lumberjack replants a sapling from the drops, the Nincada grows that sapling with bone meal as a Cultivator, and it fells the new
     * oak. A second Nincada bound by a click on a lone oak sapling binds as the Cultivator, grows its oak, keeps its binding on the grown
     * tree (CD-LSH-31) and fells it as the Lumberjack. Both bindings keep their own roles throughout (decision 10).
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 6_000)
    fun cdLsh30Lsh31NincadaFellsGrowsAndFells(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val tree = Cycle(BlockPos(5, 2, 9))
        oak(helper, tree.base)
        val sapling = Cycle(BlockPos(17, 2, 9))
        helper.setBlock(sapling.base.below(), Blocks.DIRT)
        helper.setBlock(sapling.base, Blocks.OAK_SAPLING)
        val feller = TestKit.tether(helper, pasture, owner, "nincada level=50")
        val grower = TestKit.tether(helper, pasture, owner, "nincada level=50")
        val first = TestKit.bind(helper, owner, feller, tree.base.above(2))
        helper.assertTrue(first?.roleId == id("lumberjack"), "CD-LSH-16: the Nincada bound to the oak as ${first?.roleId}")
        val second = TestKit.bind(helper, owner, grower, sapling.base)
        helper.assertTrue(second?.roleId == id("cultivator"), "CD-LSH-16: the Nincada bound to the sapling as ${second?.roleId}")
        tree.wasLog = true
        helper.onEachTick {
            assertBound(helper, feller, "lumberjack", "CD-LSH-30")
            assertBound(helper, grower, "cultivator", "CD-LSH-31")
            val treeDone = follow(helper, tree)
            follow(helper, sapling)
            // The sapling's own cycle: grown by the Cultivator, then felled by the Lumberjack, with the binding held on the grown oak.
            val saplingDone = sapling.felled >= 1
            if (treeDone && saplingDone) helper.succeed()
        }
    }

    /**
     * CD-CRP-22, CD-LSH-30, R94: a level 50 Torterra, Grass/Ground, bound to wheat at age 0 binds as the Harvester of its primary type,
     * grows the wheat to age 7 as the Cultivator and harvests it as the Harvester, twice, and keeps its binding's role throughout.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_400)
    fun cdCrp22TorterraGrowsAndHarvests(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        // The Torterra is 2.25 blocks wide, and the tether puts it 5 blocks south of the pasture, so the pasture block stands off its
        // line to the crop.
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 8), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        helper.setBlock(BlockPos(4, 1, 4), Blocks.FARMLAND)
        val crop = BlockPos(4, 2, 4)
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0))
        val torterra = TestKit.tether(helper, pasture, owner, "torterra level=50")
        val bound = TestKit.bind(helper, owner, torterra, crop)
        helper.assertTrue(bound?.roleId == id("harvester"), "CD-CRP-22: the Torterra bound as ${bound?.roleId}")
        var lastAge: Int? = 0
        var harvests = 0
        // What the worker did, for the message of a timeout: each change of its state, role and the crop's age, with its tick.
        val trace = ArrayList<String>()
        var last = ""
        // Cobblemon's tether finds room for so wide a body on top of the floor's neighbours, so the test holds it on the floor until
        // its first claim, as `WideWorkerTests` holds its Camerupt.
        val hold = BlockPos(8, 2, 9)
        helper.onEachTick {
            if (TestKit.claimOf(helper, torterra) == null && harvests == 0 && lastAge == 0) TestKit.moveTo(helper, torterra, hold)
            assertBound(helper, torterra, "harvester", "CD-CRP-22")
            val age = helper.getBlockState(crop).getOptionalValue(CropBlock.AGE).orElse(null)
            val worker = TestKit.workerOf(helper, torterra)
            val role = TestKit.engine(helper).roleOf(torterra.pokemon.uuid)?.definition?.id?.path
            val now = "${worker?.status} $role age=$age"
            val at = TestKit.relative(helper, torterra.blockPosition())
            val spot = worker?.spot?.let { TestKit.relative(helper, it) }
            val skips = worker?.skipUntil?.keys?.map { (it as? TargetKey.Block)?.lane }
            if (now != last && trace.size < 40) trace += "${helper.tick}: $now at $at spot $spot skips $skips"
            last = now
            if (lastAge == 7 && age != 7) harvests++
            lastAge = age
            if (harvests >= 2) helper.succeed()
            if (helper.tick >= 2_390) helper.fail("CD-CRP-22: $harvests harvests in 2,390 ticks: $trace")
        }
    }
}
