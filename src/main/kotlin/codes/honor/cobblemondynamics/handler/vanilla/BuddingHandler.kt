package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.farming.GrowthStall
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.block.GrowableStoneBlock
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BuddingAmethystBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.neoforged.neoforge.common.Tags

/**
 * The Crystal Tender's target: a block of `#cobblemon_dynamics:crystal_tender_targets` (CD-CRY-01, CD-MCH-02), whose default holds
 * `#c:budding_blocks` (budding amethyst and, with AE2, its four budding certus blocks), Cobblemon's budding tumblestones, Unusual End's
 * budding citrine and Quark's unwaxed corundum. The worker gives it extra ticks, as one AE2 Growth Accelerator does, through tags and
 * vanilla calls alone, so no AE2 class is named (CD-CRY-05).
 */
object BuddingHandler : TargetHandler {
    private const val INTERVAL_TICKS = "interval_ticks"
    private const val DEFAULT_INTERVAL_TICKS = 10

    /** The integer properties that count a bud's growth, such as the `stage` of Cobblemon's type gem clusters. */
    private val GROWTH = setOf("stage", "age")

    private val TARGETS: TagKey<Block> = blockTag(id("crystal_tender_targets"))
    private val SELF_GROWING: TagKey<Block> = blockTag(id("crystal/self_growing"))
    private val SCHEDULED_TICK: TagKey<Block> = blockTag(id("crystal/scheduled_tick"))
    private val CRYSTAL_BUDS: TagKey<Block> = blockTag(id("crystal/buds"))
    private val HEAT_SOURCE: TagKey<Block> = blockTag(ResourceLocation.fromNamespaceAndPath("cobblemon", "tumblestone_heat_source"))

    override val id: ResourceLocation = id("budding")

    /** Budding blocks have no block entity, so only a pasture with a Rock-type candidate scans block positions for them. */
    override val scanKind: ScanKind = ScanKind.BLOCK

    /**
     * The tick that this stretch of work started, which the extra ticks count from, and the stall, which lasts as long as the claim. A
     * self-growing target also keeps its last read of a heat source and the tick of that read.
     */
    private class Memo(var since: Long, val stall: GrowthStall) {
        var heatCheckedAt: Long? = null
        var heatSourceNear = false
    }

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean = state.`is`(TARGETS)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockState(canonical).`is`(TARGETS)

    /**
     * CD-CRY-03, CD-MCH-03: the target can still grow, and it has not stalled (CD-MCH-05). A block-ticking chunk has loaded neighbors,
     * so no neighbor read loads a chunk, and a block whose chunk does not tick its blocks waits.
     */
    override fun hasDemand(ctx: WorkContext): Boolean {
        if ((ctx.memo.value as? Memo)?.stall?.isStalled(ctx.now) == true) return false
        if (!ctx.level.shouldTickBlocksAt(ctx.target)) return false
        val target = facts(ctx, ctx.level.getBlockState(ctx.target))
        return BuddingRules.hasDemand(target) {
            Direction.entries.map { budFacts(ctx.level.getBlockState(ctx.target.relative(it)), it, target.scheduledTick) }
        }
    }

    override fun onStart(ctx: WorkContext) {
        memoOf(ctx).since = ctx.now
    }

    /**
     * CD-CRY-02: 1 extra tick every `interval_ticks`, from the tick that the work started, as AE2's accelerator gives it: the block's
     * scheduled tick for a block of the scheduled tick tag, on 1 extra tick in `scheduled_tick_one_in` (CD-MCH-04), and a random tick for
     * every other block. An extra tick that changes no block of the target and of its faces counts toward the stall (CD-MCH-05), as does a
     * scheduled tick that its roll skips, as a failed roll of budding amethyst does.
     */
    override fun applyWork(ctx: WorkContext) {
        val memo = memoOf(ctx)
        if (!TickClock.every(intervalOf(ctx.role.params), memo.since, ctx.now) || !ctx.level.shouldTickBlocksAt(ctx.target)) return
        val state = ctx.level.getBlockState(ctx.target)
        if (!state.`is`(TARGETS)) return
        val scheduled = state.`is`(SCHEDULED_TICK)
        val roll = if (scheduled) ctx.level.random.nextInt(BuddingRules.scheduledTickOneIn(ctx.role.params)) else 0
        val changed = when (BuddingRules.extraTick(CrystalFacts(scheduledTick = scheduled), roll)) {
            ExtraTick.SCHEDULED -> changes(ctx) { state.tick(ctx.level, ctx.target, ctx.level.random) }
            ExtraTick.RANDOM -> changes(ctx) { state.randomTick(ctx.level, ctx.target, ctx.level.random) }
            ExtraTick.NONE -> false
        }
        if (memo.stall.record(changed = changed, now = ctx.now)) ctx.stalled(memo.stall.waitTicks)
    }

    private fun memoOf(ctx: WorkContext): Memo =
        ctx.memo.value as? Memo ?: Memo(ctx.now, CrystalStall.of(ctx.role.params)).also { ctx.memo.value = it }

    /** Whether [tick] changed a block of the target and of its faces. */
    private inline fun changes(ctx: WorkContext, tick: () -> Unit): Boolean {
        val before = states(ctx.level, ctx.target)
        tick()
        return states(ctx.level, ctx.target) != before
    }

    /** The facts of the target [state] of [ctx]. */
    private fun facts(ctx: WorkContext, state: BlockState): CrystalFacts {
        val selfGrowing = state.`is`(SELF_GROWING)
        val stone = state.block as? GrowableStoneBlock
        return CrystalFacts(
            selfGrowing = selfGrowing,
            heatSourceNear = selfGrowing && heatSourceNear(ctx),
            hasNextStage = stone == null || stone.nextStage != null,
            scheduledTick = state.`is`(SCHEDULED_TICK),
        )
    }

    /**
     * Cobblemon's rule for a self-growing bud reads a heat source in the 27 blocks around it. A claim keeps the result for
     * [BuddingRules.HEAT_CHECK_TICKS] ticks in its memo, so its demand does not read the cube on every tick (R88).
     */
    private fun heatSourceNear(ctx: WorkContext): Boolean {
        val memo = memoOf(ctx)
        if (BuddingRules.heatCheckDue(memo.heatCheckedAt, ctx.now)) {
            val cube = BlockPos.betweenClosed(ctx.target.offset(-1, -1, -1), ctx.target.offset(1, 1, 1))
            memo.heatSourceNear = cube.any { ctx.level.getBlockState(it).`is`(HEAT_SOURCE) }
            memo.heatCheckedAt = ctx.now
        }
        return memo.heatSourceNear
    }

    /**
     * The facts of [neighbor] on [face]: what vanilla's and AE2's budding blocks grow into, air or a water source, and air alone for a
     * [scheduledTick] target, or a bud of `#c:buds` or of `#cobblemon_dynamics:crystal/buds` that faces away from the target. A bud with
     * no facing counts as one that the target grew.
     */
    private fun budFacts(neighbor: BlockState, face: Direction, scheduledTick: Boolean): BudFacts {
        val growth = neighbor.properties.firstOrNull { it is IntegerProperty && it.name in GROWTH } as? IntegerProperty
        return BudFacts(
            open = BuddingRules.isOpen(
                air = neighbor.isAir,
                clusterCanGrow = BuddingAmethystBlock.canClusterGrowAtState(neighbor),
                scheduledTick = scheduledTick,
            ),
            bud = BuddingRules.isBud(inBudsTag = neighbor.`is`(Tags.Blocks.BUDS), inCrystalBuds = neighbor.`is`(CRYSTAL_BUDS)),
            cluster = neighbor.`is`(Tags.Blocks.CLUSTERS),
            facesAway = neighbor.getOptionalValue(BlockStateProperties.FACING).map { it == face }.orElse(true),
            growth = growth?.let { neighbor.getValue(it) },
            growthMax = growth?.possibleValues?.max() ?: 0,
        )
    }

    /** The target and the blocks on its six faces, which an extra tick that grows something changes. */
    private fun states(level: ServerLevel, pos: BlockPos): List<BlockState> =
        listOf(level.getBlockState(pos)) + Direction.entries.map { level.getBlockState(pos.relative(it)) }

    private fun intervalOf(params: JsonObject): Int =
        params.get(INTERVAL_TICKS)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_INTERVAL_TICKS

    private fun blockTag(id: ResourceLocation): TagKey<Block> = TagKey.create(Registries.BLOCK, id)
}
