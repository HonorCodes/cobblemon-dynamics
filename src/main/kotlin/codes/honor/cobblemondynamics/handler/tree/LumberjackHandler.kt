package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.farming.DropFace
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/**
 * The Lumberjack's target (ADR-0017): a tree that a Work Leash names, keyed by its trunk base cell. The worker fells the whole tree with
 * its own natural leaves and the blocks that hang on it, drops the loot beside the trunk, and replants a sapling of the drops. The
 * binding then waits on the sapling, as a bound Harvester waits on its crop. Each block goes with `Level.setBlock`, so the fell is silent
 * and no listener of a player's break hears it.
 */
object LumberjackHandler : TargetHandler {
    /** How long a claim trusts its last tree search, so that a Working worker does not search the tree on each tick. */
    private const val RECHECK_TICKS = 20L

    override val id: ResourceLocation = id("lumberjack")

    /** CD-LUM-01: a role of the Work Leash only scans nothing. */
    override val scanKind: ScanKind = ScanKind.NONE

    /** CD-LUM-02. The search waits for the bind, which [bindRefusal] checks. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        TreeRules.matches(TreeFactsReader.factsOf(state))

    /** CD-LUM-03: the search runs once from the clicked cell. */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = TreeRules.canonical(TreeFactsReader(level)::facts, pos, maxLogs())

    /** CD-LUM-19, CD-LSH-08: the binding keeps its base cell through the fell and the regrowth. */
    override fun bindingHolds(level: ServerLevel, canonical: BlockPos): Boolean {
        val reader = TreeFactsReader(level)
        return TreeRules.bindingHolds(reader.facts(canonical), reader.facts(canonical.below()))
    }

    /** The claim follows the binding, so a claim on a sapling stays while the worker waits for the tree. */
    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = bindingHolds(level, canonical)

    /** CD-LUM-20: a player never leashes a worker to a tree that it will not fell. */
    override fun bindRefusal(level: ServerLevel, canonical: BlockPos, role: RoleDefinition): String? {
        val params = LumberjackParams.of(role.params)
        val refused = TreeRules.refusesBind(TreeFactsReader(level)::facts, canonical, params.maxLogs, params.maxLeaves)
        return TreeRules.CANNOT_FELL.takeIf { refused }
    }

    /** CD-LUM-10, CD-LUM-13: a tree stands on the base cell, and no claim check refused its fell lately. A started fell completes. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val memo = memoOf(ctx)
        if (memo.run != null) return true
        if (memo.clock.isRefused(ctx.now)) return false
        return memo.tree(ctx) != null
    }

    /** CD-LUM-11: the fell comes T ticks of Working for each log after this start. */
    override fun onStart(ctx: WorkContext) {
        val memo = memoOf(ctx)
        val tree = memo.tree(ctx) ?: return
        memo.clock.start(ctx.now, FellClock.ticksPerLog(ctx.pokemon.level, LumberjackParams.of(ctx.role.params)), tree.logs.size)
    }

    override fun applyWork(ctx: WorkContext) {
        val memo = memoOf(ctx)
        memo.run?.let { run ->
            if (run.step { remove(ctx.level, it, memo.loot) }) end(ctx, memo, run)
            return
        }
        if (!memo.clock.isFellTick(ctx.now)) return
        memo.forgetTree()
        val params = LumberjackParams.of(ctx.role.params)
        val plan = plan(ctx.level, ctx.target, params) ?: return
        // CD-LUM-12, CD-LUM-13: every question in this tick, before any block changes, and one refusal changes nothing.
        if (!FellPlan.allowed(plan, ctx.mayChange, ctx.mayBreak)) {
            memo.clock.refuse(ctx.now, ctx.stalled)
            return
        }
        val run = FellRun(plan, params.blocksPerTick)
        memo.run = run
        if (run.step { remove(ctx.level, it, memo.loot) }) end(ctx, memo, run)
    }

    /** CD-LUM-15: a fell that has started completes, also when the work stops or the claim ends during it. */
    override fun onStop(ctx: WorkContext) = complete(ctx)

    override fun onUnclaim(ctx: WorkContext) = complete(ctx)

    /** The fell of the tree on [base], with its shell, or null when no tree within the role's limits stands there. GameTests read it. */
    fun plan(level: ServerLevel, base: BlockPos, params: LumberjackParams): FellPlan? {
        val reader = TreeFactsReader(level)
        val tree = TreeRules.treeOn(reader::facts, base, params.maxLogs, params.maxLeaves) ?: return null
        val shell = ShellRule.shell(tree, { pos -> standing(level, pos) }) { pos, gone ->
            val state = TreeFactsReader.stateOf(level, pos) ?: return@shell true
            state.canSurvive(HollowLevel(level) { it in tree.cells || it in gone }, pos)
        }
        return FellPlan.of(tree, shell) { reader.facts(it).blockId }
    }

    private fun complete(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: return
        val run = memo.run ?: return
        if (run.finish { remove(ctx.level, it, memo.loot) }) end(ctx, memo, run)
    }

    /** The cells next to [pos] that hold a block, in loaded chunks. */
    private fun standing(level: ServerLevel, pos: BlockPos): List<BlockPos> =
        TreeSearch.faces(pos).filter { TreeFactsReader.stateOf(level, it)?.isAir == false }

    /** CD-LUM-14, CD-LUM-16: the loot of the cell as it stands, then the cell's fluid. A cell that changed since the plan stays. */
    private fun remove(level: ServerLevel, step: FellStep, loot: MutableList<ItemStack>) {
        val state = TreeFactsReader.stateOf(level, step.pos) ?: return
        if (BuiltInRegistries.BLOCK.getKey(state.block) != step.blockId) return
        loot += Block.getDrops(state, level, step.pos, level.getBlockEntity(step.pos), null, ItemStack.EMPTY).filterNot { it.isEmpty }
        val flags = if (step.shell) Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE else Block.UPDATE_ALL
        level.setBlock(step.pos, state.fluidState.createLegacyBlock(), flags)
        state.spawnAfterBreak(level, step.pos, ItemStack.EMPTY, true)
    }

    /** CD-LUM-16 to CD-LUM-18: after the last block, the replant takes its saplings from the drops, and the rest pop toward the spot. */
    private fun end(ctx: WorkContext, memo: Memo, run: FellRun) {
        val level = ctx.level
        val plan = run.plan
        val drops = FellPlan.merged(memo.loot.map { it to it.count }, same = ItemStack::isSameItemSameComponents)
        val replant = ReplantRule.choose(
            drops,
            plan.base,
            plan.twoByTwo,
            isSapling = { saplingOf(it) != null },
            survives = { stack, cell -> canPlant(level, stack, cell) },
            needsFour = { stack -> saplingOf(stack)?.defaultBlockState()?.`is`(TreeFactsReader.NEEDS_FOUR) == true },
        )
        val sapling = replant?.let { saplingOf(it.item) }?.defaultBlockState()
        if (replant != null && sapling != null) for (cell in replant.cells) level.setBlock(cell, sapling, Block.UPDATE_ALL)
        val taken = listOfNotNull(replant?.takeIf { sapling != null }?.let { it.item to it.cells.size })
        val face = DropFace.toward(plan.base, ctx.spot ?: plan.base)
        for ((stack, count) in FellPlan.merged(drops, taken, ItemStack::isSameItemSameComponents)) {
            var left = count
            while (left > 0) {
                val size = minOf(left, stack.maxStackSize)
                Block.popResourceFromFace(level, plan.base, face, stack.copyWithCount(size))
                left -= size
            }
        }
        memo.run = null
        memo.loot.clear()
    }

    /** The sapling that [stack] places, if its block is in `#minecraft:saplings`. */
    private fun saplingOf(stack: ItemStack): Block? =
        (stack.item as? BlockItem)?.block?.takeIf { it.defaultBlockState().`is`(BlockTags.SAPLINGS) }

    /** A sapling goes only into an empty cell where it stands. */
    private fun canPlant(level: ServerLevel, stack: ItemStack, cell: BlockPos): Boolean {
        val block = saplingOf(stack) ?: return false
        return TreeFactsReader.stateOf(level, cell)?.isAir == true && block.defaultBlockState().canSurvive(level, cell)
    }

    /** The largest `max_logs` of the active Lumberjack roles, so that a click finds the base of any tree that one of them fells. */
    private fun maxLogs(): Int = RoleRegistry.current.active.filter { it.handler === this }
        .maxOfOrNull { LumberjackParams.of(it.definition.params).maxLogs } ?: LumberjackParams.DEFAULT.maxLogs

    private fun memoOf(ctx: WorkContext): Memo = ctx.memo.value as? Memo ?: Memo().also { ctx.memo.value = it }

    /** What one claim keeps: the clock, a fell in progress and its loot, and the last tree search. */
    private class Memo {
        val clock = FellClock()
        var run: FellRun? = null
        val loot = ArrayList<ItemStack>()
        private var tree: Tree? = null
        private var checkedAt: Long? = null

        /** The tree on the claim's target. A base cell with no log of the tag has none, which costs one read. */
        fun tree(ctx: WorkContext): Tree? {
            val base = TreeFactsReader.stateOf(ctx.level, ctx.target)
            if (base == null || !base.`is`(TreeFactsReader.TARGETS)) {
                forgetTree()
                return null
            }
            checkedAt?.let { if (ctx.now - it < RECHECK_TICKS) return tree }
            val params = LumberjackParams.of(ctx.role.params)
            tree = TreeRules.treeOn(TreeFactsReader(ctx.level)::facts, ctx.target, params.maxLogs, params.maxLeaves)
            checkedAt = ctx.now
            return tree
        }

        fun forgetTree() {
            tree = null
            checkedAt = null
        }
    }

    /**
     * The level as it reads once the fell has removed the cells that [hollow] names, each of which then holds only its fluid. A cell of a
     * chunk that is not loaded reads as bedrock, so the view never loads a chunk and a block there keeps its support.
     */
    private class HollowLevel(private val level: ServerLevel, private val hollow: (BlockPos) -> Boolean) : LevelReader by level {
        override fun getBlockState(pos: BlockPos): BlockState {
            val state = TreeFactsReader.stateOf(level, pos) ?: return Blocks.BEDROCK.defaultBlockState()
            return if (hollow(pos)) state.fluidState.createLegacyBlock() else state
        }

        override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

        override fun getBlockEntity(pos: BlockPos): BlockEntity? =
            if (hollow(pos) || TreeFactsReader.stateOf(level, pos) == null) null else level.getBlockEntity(pos)

        override fun isEmptyBlock(pos: BlockPos): Boolean = getBlockState(pos).isAir
    }
}
