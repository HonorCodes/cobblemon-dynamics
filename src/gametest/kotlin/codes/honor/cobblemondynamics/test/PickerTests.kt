package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.ApricornBlock
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.LeavesBlock
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The Picker (R95) at real Cobblemon apricorns: who picks, what a pick does, and the leash that never binds it. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class PickerTests {
    /** The leaves of the scene's apricorn tree, 2 blocks above the floor, inside the pasture's work area. */
    private val leaves = BlockPos(7, 3, 3)

    /** A ripe apricorn hangs south of the leaves, and an unripe one north of them. */
    private val ripe = leaves.south()
    private val unripe = leaves.north()

    private fun scene(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val leafState = CobblemonBlocks.APRICORN_LEAVES.defaultBlockState()
        helper.setBlock(leaves, leafState.trySetValue(LeavesBlock.PERSISTENT, true))
        hang(helper, ripe, Direction.NORTH, ApricornBlock.MAX_AGE)
        hang(helper, unripe, Direction.SOUTH, ApricornBlock.MAX_AGE - 1)
        return owner to pasture
    }

    /** A red apricorn at [rel] at [age], which hangs from the leaves on its [facing] side, as Cobblemon's tree places it. */
    private fun hang(helper: GameTestHelper, rel: BlockPos, facing: Direction, age: Int) {
        val state = CobblemonBlocks.RED_APRICORN.defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, facing)
            .setValue(ApricornBlock.AGE, age)
        helper.setBlock(rel, state)
        helper.assertTrue(helper.getBlockState(rel).canSurvive(helper.level, helper.absolutePos(rel)), "the apricorn at $rel does not hang")
    }

    private fun ageAt(helper: GameTestHelper, rel: BlockPos): Int? =
        helper.getBlockState(rel).getOptionalValue(ApricornBlock.AGE).orElse(null)

    /** Fails when the worker of [entity] holds a work claim with another role than the Picker. */
    private fun assertPicker(helper: GameTestHelper, entity: PokemonEntity) {
        if (TestKit.claimOf(helper, entity)?.kind != ClaimKind.WORK) return
        val held = TestKit.engine(helper).roleOf(entity.pokemon.uuid)?.definition?.id
        helper.assertTrue(held == id("picker"), "${entity.pokemon.species.name} works as $held")
    }

    /**
     * CD-PCK-01, CD-PCK-05 to CD-PCK-07: a level 50 Butterfree in the pasture picks the ripe apricorn as an auto Picker. The apricorn goes
     * back to age 0 on its leaves, a red apricorn lies on the ground below it, and the unripe apricorn beside it, inside the area of the
     * pick, keeps its age. GameTest chunks get no random ticks, so nothing else changes an age.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdPck06ButterfreePicksARipeApricorn(helper: GameTestHelper) {
        val scene = scene(helper)
        val butterfree = TestKit.tether(helper, scene.second, scene.first, "butterfree level=50")
        val cell = helper.absolutePos(ripe)
        helper.onEachTick {
            assertPicker(helper, butterfree)
            helper.assertTrue(ageAt(helper, unripe) == ApricornBlock.MAX_AGE - 1, "CD-PCK-05: the unripe apricorn changed its age")
            if (ageAt(helper, ripe) != 0) return@onEachTick
            helper.assertTrue(
                helper.getBlockState(ripe).`is`(CobblemonBlocks.RED_APRICORN),
                "CD-PCK-06: the picked apricorn left its leaves",
            )
            val fallen = helper.level.getEntitiesOfClass(ItemEntity::class.java, helper.bounds.inflate(2.0)) {
                it.item.`is`(CobblemonItems.RED_APRICORN)
            }
            val below = fallen.filter { item ->
                item.onGround() && item.y < cell.y && item.position().distanceToSqr(cell.center.x, item.y, cell.center.z) <= 4.0
            }
            if (below.isEmpty()) return@onEachTick
            helper.succeed()
        }
    }

    /**
     * CD-PCK-02, CD-PCK-03: a level 50 Caterpie, Bug only, and a level 50 Rookidee, Flying only, in the pasture never take the ripe
     * apricorn, which stays ripe through 20 scans.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdPck03NeitherTypeAlonePicks(helper: GameTestHelper) {
        val scene = scene(helper)
        val workers = listOf("caterpie level=50", "rookidee level=50").map { TestKit.tether(helper, scene.second, scene.first, it) }
        helper.onEachTick {
            for (worker in workers) {
                val claim = TestKit.claimOf(helper, worker)
                helper.assertTrue(claim?.kind != ClaimKind.WORK, "CD-PCK-03: the ${worker.pokemon.species.name} took a work claim: $claim")
            }
            helper.assertTrue(ageAt(helper, ripe) == ApricornBlock.MAX_AGE, "CD-PCK-03: the ripe apricorn was picked")
        }
        helper.runAtTickTime(800) { helper.succeed() }
    }

    /**
     * CD-PCK-04: a Work Leash click with a selected Butterfree on the ripe apricorn binds nothing and names the Butterfree in its
     * refusal, and the Butterfree then picks the apricorn as an auto Picker.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdPck04TheLeashNeverBindsAPicker(helper: GameTestHelper) {
        val scene = scene(helper)
        val butterfree = TestKit.tether(helper, scene.second, scene.first, "butterfree level=50")
        val binding = TestKit.bind(helper, scene.first, butterfree, ripe)
        helper.assertTrue(binding == null, "CD-PCK-04: the leash bound the Butterfree: $binding")
        val reply = TestKit.lastMessage(scene.first)
        helper.assertTrue(reply?.endsWith("works here on its own, with no Work Leash") == true, "CD-PCK-04: the click answered '$reply'")
        helper.onEachTick {
            helper.assertTrue(BindingStore.read(butterfree.pokemon) == null, "CD-PCK-04: the Butterfree holds a binding")
            if (ageAt(helper, ripe) == 0) helper.succeed()
        }
    }
}
