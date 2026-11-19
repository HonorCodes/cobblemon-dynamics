package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

/**
 * CD-LUM-31: the sapling that a leaf drops by its loot table, also where its name differs from the leaf's, as azalea leaves drop an
 * azalea. The table is rolled as a break with no tool would roll it, at most [ROLLS] times, with one random source of a fixed seed, so
 * the answer is the same on each server (decision 5 of the 1.2.1 plan).
 */
object LeafSaplings {
    /**
     * The rolls of one leaf. A sapling at 1 in 40, as the jungle leaf drops it, misses all of them about 1 time in 400,000, and the name
     * rule then covers it.
     */
    const val ROLLS = 512

    /**
     * The seed of the rolls. One source for all of them, not one seed per roll: the first float of the seeds 1 to 511 lies between 0.72
     * and 0.77, so a roll of each of those seeds would never give a sapling of a chance below 0.72, and the seed 0 takes the level's own
     * random source.
     */
    private const val SEED = 0x5EED_1EAFL

    /** The answer for one leaf block, with the table instance that gave it. A reload makes new instances, which roll again. */
    private class Answer(val table: LootTable, val sapling: Item?)

    private val answers = ConcurrentHashMap<Block, Answer>()
    private val warnings = OnceLogger()

    /**
     * The first item of [leaf]'s rolls that places a block of `#minecraft:saplings`, or null. [origin] is the cell of the roll. A leaf
     * id that names no block, and a roll that fails, give null; a failure logs once for each leaf.
     */
    fun of(level: ServerLevel, leaf: ResourceLocation, origin: BlockPos): Item? {
        val block = BuiltInRegistries.BLOCK.getOptional(leaf).orElse(null) ?: return null
        return try {
            val table = level.server.reloadableRegistries().getLootTable(block.lootTable)
            answers[block]?.takeIf { it.table === table }?.let { return it.sapling }
            Answer(table, roll(level, block, origin, table)).also { answers[block] = it }.sapling
        } catch (e: Exception) {
            failed(leaf, e)
        } catch (e: LinkageError) {
            failed(leaf, e)
        }
    }

    /** Forgets every answer and the tables that they hold, when the server stops, so that no table outlives its session. */
    fun reset() = answers.clear()

    private fun failed(leaf: ResourceLocation, e: Throwable): Item? {
        warnings.warn("roll:$leaf") { "The Lumberjack finds no sapling in the loot table of $leaf, because its roll fails: $e" }
        return null
    }

    private fun roll(level: ServerLevel, block: Block, origin: BlockPos, table: LootTable): Item? {
        if (table === LootTable.EMPTY) return null
        val params = LootParams.Builder(level)
            .withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .create(LootContextParamSets.BLOCK)
        val random = RandomSource.create(SEED)
        repeat(ROLLS) {
            table.getRandomItems(params, random).firstOrNull(::placesSapling)?.let { return it.item }
        }
        return null
    }

    private fun placesSapling(stack: ItemStack): Boolean =
        (stack.item as? BlockItem)?.block?.defaultBlockState()?.`is`(BlockTags.SAPLINGS) == true
}
