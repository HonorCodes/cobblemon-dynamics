package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.AmethystClusterBlock
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.Tags
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Crystal Tender on real budding blocks. `ServerChunkCache.tickChunks` random-ticks only chunks near a player, and the tests'
 * fake players never join the level, so every bud that grows here grew from a worker's extra random tick.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CrystalTenderTests {
    /** On the stone floor, so that five of its faces are open: up and the four sides. */
    private val raisedRel = BlockPos(7, 2, 3)

    private fun pasture(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        return owner to pasture
    }

    private fun rockruff(helper: GameTestHelper, scene: Pair<ServerPlayer, PokemonPastureBlockEntity>): PokemonEntity =
        TestKit.tether(helper, scene.second, scene.first, "rockruff level=20")

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, rel: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, helper.absolutePos(rel)) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdCry01RockTypesClaimBuddingAmethystAndCertus(helper: GameTestHelper) {
        val scene = pasture(helper)
        val amethyst = BlockPos(4, 1, 3)
        val certus = BlockPos(10, 1, 3)
        helper.setBlock(amethyst, Blocks.BUDDING_AMETHYST)
        helper.setBlock(certus, TestKit.ae2Block("ae2:flawless_budding_quartz"))
        val tenders = List(2) { rockruff(helper, scene) }
        val targets = setOf(amethyst, certus).map { TestKit.keyOf(helper, helper.absolutePos(it)) }.toSet()
        helper.succeedWhen {
            val claimed = tenders.mapNotNull { TestKit.claimOf(helper, it)?.takeIf { c -> c.kind == ClaimKind.WORK }?.key }.toSet()
            helper.assertTrue(claimed == targets, "the Rockruffs hold $claimed, not the budding amethyst and the budding certus")
            for (tender in tenders) {
                val role = TestKit.engine(helper).roleOf(tender.pokemon.uuid)?.definition?.id
                helper.assertTrue(role == id("crystal_tender"), "a Rockruff works its budding block as $role")
            }
        }
    }

    /**
     * Each extra random tick grows a bud with a chance of 1 in 5 times the share of faces that can still grow (vanilla's
     * `BuddingAmethystBlock`), so 5 open faces grow a bud on 1 extra tick in 6. The 61 extra ticks of 600 ticks of work all miss
     * with a chance of about 1 in 70,000.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdCry02TenderGrowsRealAmethyst(helper: GameTestHelper) {
        val scene = pasture(helper)
        helper.setBlock(raisedRel, Blocks.BUDDING_AMETHYST)
        val tender = rockruff(helper, scene)
        var workingSince = -1L
        helper.onEachTick {
            if (workingSince < 0) {
                if (isWorkingOn(helper, tender, raisedRel)) workingSince = helper.tick
                return@onEachTick
            }
            val grown = Direction.entries.map { raisedRel.relative(it) }.filter {
                val state = helper.getBlockState(it)
                state.`is`(Tags.Blocks.BUDS) || state.`is`(Tags.Blocks.CLUSTERS)
            }
            if (grown.isEmpty()) {
                helper.assertTrue(helper.tick - workingSince <= 600, "no bud grew in 600 ticks of the Tender's work")
                return@onEachTick
            }
            val outward = grown.all { helper.getBlockState(it).getValue(AmethystClusterBlock.FACING) == faceOf(it) }
            helper.assertTrue(outward, "a bud at $grown does not face away from the budding amethyst")
            helper.succeed()
        }
    }

    private fun faceOf(rel: BlockPos): Direction = Direction.entries.first { raisedRel.relative(it) == rel }

    /** The Tender claims while a face is open: a block with a cluster on every face has no line of sight to a work spot. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdCry03FullClustersMeanStandby(helper: GameTestHelper) {
        val scene = pasture(helper)
        helper.setBlock(raisedRel, Blocks.BUDDING_AMETHYST)
        val tender = rockruff(helper, scene)
        var step = "work"
        var since = -1L
        var stamina = 0.0
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, tender) ?: return@onEachTick
            when (step) {
                "work" -> if (isWorkingOn(helper, tender, raisedRel)) {
                    for (face in Direction.entries) {
                        helper.setBlock(
                            raisedRel.relative(face),
                            Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, face),
                        )
                    }
                    step = "full"
                    since = helper.tick
                }

                "full" -> if (worker.status == WorkerStatus.STANDBY) {
                    val claim = TestKit.claimOf(helper, tender)
                    helper.assertTrue(
                        claim?.key == TestKit.keyOf(helper, helper.absolutePos(raisedRel)),
                        "the Rockruff left its claim: $claim",
                    )
                    step = "standby"
                    since = helper.tick
                    stamina = worker.stamina.fraction
                } else {
                    helper.assertTrue(helper.tick - since < 20, "the Rockruff is ${worker.status} 20 ticks after the last cluster")
                }

                "standby" -> {
                    helper.assertTrue(worker.status == WorkerStatus.STANDBY, "the Rockruff left Standby: ${worker.status}")
                    helper.assertTrue(worker.stamina.fraction >= stamina, "Work Stamina drained in Standby: ${worker.stamina}")
                    stamina = worker.stamina.fraction
                    if (helper.tick - since >= 40) helper.succeed()
                }
            }
        }
    }
}
