package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.BuddingAmethystBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.neoforged.neoforge.common.Tags

/**
 * The Crystal Tender's target: a block in `#c:budding_blocks`, which holds budding amethyst and, with AE2, its four budding certus
 * blocks (CD-CRY-01, CD-CRY-04). The worker gives it extra random ticks, as one AE2 Growth Accelerator does, through the tag alone,
 * so no AE2 class is named (CD-CRY-05).
 */
object BuddingHandler : TargetHandler {
    private const val INTERVAL_TICKS = "interval_ticks"
    private const val DEFAULT_INTERVAL_TICKS = 10

    /** The integer properties that count a bud's growth, such as the `stage` of Cobblemon's type gem clusters. */
    private val GROWTH = setOf("stage", "age")

    override val id: ResourceLocation = id("budding")

    /** Budding blocks have no block entity, so only a pasture with a Rock-type candidate scans block positions for them. */
    override val scanKind: ScanKind = ScanKind.BLOCK

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        state.`is`(Tags.Blocks.BUDDING_BLOCKS)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        level.getBlockState(canonical).`is`(Tags.Blocks.BUDDING_BLOCKS)

    /**
     * CD-CRY-03: some face can still grow a bud. A block-ticking chunk has loaded neighbors, so no neighbor read loads a chunk, and
     * a block whose chunk does not tick its blocks waits.
     */
    override fun hasDemand(ctx: WorkContext): Boolean = ctx.level.shouldTickBlocksAt(ctx.target) &&
        Direction.entries.any { BuddingRules.canGrow(facts(ctx.level.getBlockState(ctx.target.relative(it)), it)) }

    /** The memo holds the tick that this stretch of work started, which the random ticks count from. */
    override fun onStart(ctx: WorkContext) {
        ctx.memo.value = ctx.now
    }

    /** CD-CRY-02: 1 extra random tick every `interval_ticks`, from the tick that the work started, as AE2's accelerator calls it. */
    override fun applyWork(ctx: WorkContext) {
        val since = ctx.memo.value as? Long ?: ctx.now.also { ctx.memo.value = it }
        if (!TickClock.every(intervalOf(ctx.role.params), since, ctx.now) || !ctx.level.shouldTickBlocksAt(ctx.target)) return
        val state = ctx.level.getBlockState(ctx.target)
        if (state.`is`(Tags.Blocks.BUDDING_BLOCKS)) state.randomTick(ctx.level, ctx.target, ctx.level.random)
    }

    /**
     * The facts of [neighbor] on [face]: what vanilla's and AE2's budding blocks grow into, air or a water source, or a bud of
     * `#c:buds` that faces away from the target. A bud with no facing counts as one that the target grew.
     */
    private fun facts(neighbor: BlockState, face: Direction): BudFacts {
        val growth = neighbor.properties.firstOrNull { it is IntegerProperty && it.name in GROWTH } as? IntegerProperty
        return BudFacts(
            open = BuddingAmethystBlock.canClusterGrowAtState(neighbor),
            bud = neighbor.`is`(Tags.Blocks.BUDS),
            cluster = neighbor.`is`(Tags.Blocks.CLUSTERS),
            facesAway = neighbor.getOptionalValue(BlockStateProperties.FACING).map { it == face }.orElse(true),
            growth = growth?.let { neighbor.getValue(it) },
            growthMax = growth?.possibleValues?.max() ?: 0,
        )
    }

    private fun intervalOf(params: JsonObject): Int =
        params.get(INTERVAL_TICKS)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_INTERVAL_TICKS
}
