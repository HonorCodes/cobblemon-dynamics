package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.stamina.StaminaMath
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.BonemealEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** Every `BonemealEvent` of the server, with its game tick, so that a test reads the Cultivator's growth uses (CD-CUL-05). */
object BonemealUses {
    data class Use(val pos: BlockPos, val tick: Long, val player: Player?)

    val uses: MutableList<Use> = CopyOnWriteArrayList()

    fun register() {
        NeoForge.EVENT_BUS.addListener<BonemealEvent> { uses += Use(it.pos.immutable(), it.level.gameTime, it.player) }
    }
}

/**
 * The farming roles on vanilla crops (ADR-0015). `ServerChunkCache.tickChunks` random-ticks only chunks near a player, and the tests'
 * fake players never join the level, so every crop that grows here grew from a Cultivator's growth use.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class FarmingTests {
    private fun scene(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        return owner to pasture
    }

    /** Farmland in the floor at [soil], and [crop] above it. Returns the crop's position. */
    private fun plant(helper: GameTestHelper, soil: BlockPos, crop: BlockState): BlockPos {
        helper.setBlock(soil, Blocks.FARMLAND)
        helper.setBlock(soil.above(), crop)
        return soil.above()
    }

    private fun wheat(age: Int): BlockState = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age)

    private fun ageAt(helper: GameTestHelper, rel: BlockPos): Int? = helper.getBlockState(rel).getOptionalValue(CropBlock.AGE).orElse(null)

    /**
     * CD-CRP-10, CD-CUL-01, CD-CUL-03, CD-CUL-05, CD-CUL-06: a level 50 Diglett grows three wheat crops from age 0, one after the
     * other, and never waits at a fully grown crop. At level 50 a use comes every 80 ticks and adds 2 to 5 ages, so each crop takes 2
     * to 4 uses.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_600)
    fun cdCrp10CultivatorGoesCropToCrop(helper: GameTestHelper) {
        val scene = scene(helper)
        val crops = listOf(4, 5, 6).map { x -> plant(helper, BlockPos(x, 1, 3), wheat(0)) }
        val cells = crops.map(helper::absolutePos).toSet()
        val diglett = TestKit.tether(helper, scene.second, scene.first, "diglett level=50")
        val firstTick = helper.level.gameTime
        val stamina = HashMap<Long, Double>()
        val working = HashSet<Long>()
        var onGrown = 0
        helper.onEachTick {
            val now = helper.level.gameTime
            val worker = TestKit.workerOf(helper, diglett) ?: return@onEachTick
            stamina[now] = worker.stamina.fraction
            if (worker.status == WorkerStatus.WORKING) working += now
            val held = (TestKit.claimOf(helper, diglett)?.key as? TargetKey.Block)?.let { BlockPos.of(it.pos) }?.takeIf { it in cells }
            if (held != null) {
                val role = TestKit.engine(helper).roleOf(diglett.pokemon.uuid)?.definition?.id
                helper.assertTrue(role == id("cultivator"), "the Diglett works a crop as $role")
                onGrown = if (ageAt(helper, TestKit.relative(helper, held)) == 7) onGrown + 1 else 0
                helper.assertTrue(onGrown <= 20, "the Diglett held its claim on fully grown wheat for $onGrown ticks")
            } else {
                onGrown = 0
            }
            if (crops.any { ageAt(helper, it) != 7 }) return@onEachTick
            val uses = BonemealUses.uses.filter { it.pos in cells && it.tick >= firstTick }
            helper.assertTrue(uses.isNotEmpty() && uses.all { it.player == null }, "a growth use named a player, or none posted")
            // CD-CUL-06: from the tick before the first use to the tick of the second use at the same crop.
            val first = uses.first()
            val second = uses.firstOrNull { it.pos == first.pos && it.tick > first.tick }
            helper.assertTrue(second != null, "the first crop took one use from age 0")
            val span = first.tick..second!!.tick
            helper.assertTrue(span.all { it in working }, "the Diglett stopped working between its first two uses")
            val drained = (stamina.getValue(first.tick - 1) - stamina.getValue(second.tick)) *
                StaminaMath.capacityTicks(diglett.pokemon.level, WorkEngineSettings.current)
            val expected = (second.tick - first.tick + 1) + 200.0
            helper.assertTrue(abs(drained - expected) <= 1.0, "two uses drained $drained ticks with the Working ticks, not $expected")
            helper.succeed()
        }
    }
}
