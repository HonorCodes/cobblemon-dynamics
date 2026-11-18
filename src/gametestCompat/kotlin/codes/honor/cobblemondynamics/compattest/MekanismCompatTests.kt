package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.compat.mekanismgenerators.MekanismGeneratorsCompat
import codes.honor.cobblemondynamics.compat.mekanismgenerators.WindGeneratorHandler
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import mekanism.api.math.MathUtils
import mekanism.common.tile.TileEntityBoundingBlock
import mekanism.generators.common.config.MekanismGeneratorsConfig
import mekanism.generators.common.tile.TileEntityWindGenerator
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/**
 * Tailwind on the real Wind Generator of Mekanism Generators 10.7.19.85 (ADR-0020). Mekanism stores its own multiplier m every 20 ticks
 * and makes `windGenerationMin` × m J on every tick. m is 0 unless the cell 4 above the generator holds no fluid and sees the sky, and
 * below `windGenerationMinY` (24 by default) it is 1, so the generator works at the GameTest's height, near y = -58, with Mekanism's
 * default config. The test needs the open sky, so it asks for no barrier ceiling.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class MekanismCompatTests {
    private val generatorRel = BlockPos(7, 2, 5)

    /**
     * CD-AIR-02: a Wind Generator under the open sky, placed with its 4 bounding blocks as its block item places it. Its own multiplier
     * m is above 0. While a level 100 Pidgey that the owner bound Works it, Mekanism's next update stores m + (M − m) × 100 ÷ 100, with
     * M = `windGenerationMax` ÷ `windGenerationMin`, through the mixin at the return of `getMultiplier()`. The generator then makes
     * `windGenerationMin` times that on every tick, at least 480 J, into its own buffer.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, skyAccess = true)
    fun cdAir02Lv100PidgeyRaisesTheWindGeneratorToItsMaximum(helper: GameTestHelper) {
        helper.assertTrue(MixinState.applied(MixinState.WIND_GENERATOR), "the Wind Generator mixin did not apply")
        val role = RoleRegistry.current.get(TAILWIND)
        helper.assertTrue(role?.handler === WindGeneratorHandler, "the role $TAILWIND is not active with its handler: $role")
        val generator = windGenerator(helper)
        val owner = TestKit.owner(helper)
        // Cobblemon tethers the Pidgey south of the pasture. A flying Pidgey whose straight line to the generator crosses the pasture
        // hovers over it, so the pasture stands aside.
        val pasture = TestKit.placePasture(helper, BlockPos(10, 1, 10), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val pidgey = TestKit.tether(helper, pasture, owner, "pidgey level=100")
        CompatDiag.failBeforeTimeout(helper, 1_000, pidgey)
        val config = MekanismGeneratorsConfig.generators
        val min = config.windGenerationMin.get()
        val top = config.windGenerationMax.get().toDouble() / min
        var step = "own"
        var since = -1L
        var own = 0.0
        var expected = 0.0
        var start = 0L
        helper.onEachTick {
            when (step) {
                // Mekanism stores the generator's multiplier every 20 ticks, so it holds its own m by now.
                "own" -> if (helper.tick >= OWN_TICKS) {
                    own = generator.currentMultiplier
                    val sky = helper.level.canSeeSky(generator.blockPos.above(4))
                    helper.assertTrue(own > 0.0, "Mekanism's own multiplier is $own, and the cell 4 above sees the sky: $sky")
                    expected = own + (top - own) * PIDGEY_LEVEL / 100.0
                    val binding = TestKit.bind(helper, owner, pidgey, generatorRel)
                    helper.assertTrue(binding?.target == generator.blockPos, "no binding to the Wind Generator: $binding")
                    step = "claim"
                }

                "claim" -> if (isWorkingOn(helper, pidgey, generator.blockPos)) {
                    val grant = generator.getExistingDataOrNull(MekanismGeneratorsCompat.WIND_GRANT.get())?.level
                    helper.assertTrue(grant == PIDGEY_LEVEL, "the Wind Generator holds a grant of level $grant")
                    since = helper.tick
                    step = "update"
                }

                // Mekanism's next update comes within 20 ticks.
                "update" -> if (helper.tick - since > UPDATE_TICKS) {
                    val stored = generator.currentMultiplier
                    helper.assertTrue(
                        abs(stored - expected) <= 1e-9,
                        "CD-AIR-02: Mekanism stores $stored, not $own + ($top − $own) × $PIDGEY_LEVEL ÷ 100 = $expected",
                    )
                    val generation = generator.currentGeneration
                    helper.assertTrue(
                        generation == MathUtils.clampToLong(min * expected),
                        "CD-AIR-02: the generator makes $generation J/t at a multiplier of $stored",
                    )
                    start = generator.energyContainer.energy
                    since = helper.tick
                    step = "sample"
                }

                "sample" -> if (helper.tick - since == SAMPLE_TICKS) {
                    val perTick = (generator.energyContainer.energy - start) / SAMPLE_TICKS
                    helper.assertTrue(perTick >= GENERATOR_J, "CD-AIR-02: the generator makes $perTick J/t, less than $GENERATOR_J")
                    helper.succeed()
                }
            }
        }
    }

    /**
     * A Wind Generator at [generatorRel]: its block, then Mekanism's own `setPlacedBy`, which its block item calls and which places the
     * 4 bounding blocks above it.
     */
    private fun windGenerator(helper: GameTestHelper): TileEntityWindGenerator {
        val block = BuiltInRegistries.BLOCK.getOptional(WIND_GENERATOR).orElseThrow {
            GameTestAssertException("no block $WIND_GENERATOR is registered")
        }
        helper.setBlock(generatorRel, block)
        val pos = helper.absolutePos(generatorRel)
        block.setPlacedBy(helper.level, pos, helper.level.getBlockState(pos), null, ItemStack.EMPTY)
        val generator = helper.level.getBlockEntity(pos) as? TileEntityWindGenerator
            ?: throw GameTestAssertException("no Wind Generator at $generatorRel")
        for (dy in 1..4) {
            val main = (helper.level.getBlockEntity(pos.above(dy)) as? TileEntityBoundingBlock)?.mainPos
            helper.assertTrue(main == pos, "the bounding block $dy above the Wind Generator names $main")
        }
        return generator
    }

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, target: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, target) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    private companion object {
        val TAILWIND: ResourceLocation = id("tailwind_mekanism")
        val WIND_GENERATOR: ResourceLocation = ResourceLocation.fromNamespaceAndPath("mekanismgenerators", "wind_generator")
        const val PIDGEY_LEVEL = 100

        /** CD-AIR-02: `windGenerationMax`, 480 J/t by default, which level 100 reaches. */
        const val GENERATOR_J = 480L
        const val OWN_TICKS = 25L
        const val UPDATE_TICKS = 21L
        const val SAMPLE_TICKS = 20L
    }
}
