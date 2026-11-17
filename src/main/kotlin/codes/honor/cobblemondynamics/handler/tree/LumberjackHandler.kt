package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.farming.DropFace
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.decoration.GlowItemFrame
import net.minecraft.world.entity.decoration.HangingEntity
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.decoration.Painting
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseRailBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import java.util.concurrent.atomic.AtomicLong

/**
 * The Lumberjack's target (ADR-0017): a tree that a Work Leash names, keyed by its trunk base cell. The worker fells the whole tree with
 * its own natural leaves and the blocks that hang on it, drops the loot beside the trunk, and replants a sapling of the drops. The
 * binding then waits on the sapling, as a bound Harvester waits on its crop. Each block goes with `Level.setBlock`, so the fell is silent
 * and no listener of a player's break hears it.
 */
object LumberjackHandler : TargetHandler {
    /** CD-LUM-27: a run ends within a few ticks, and a run that never ends gives its chunks back after this lifespan. */
    private const val TICKET_TICKS = 600

    /** CD-LUM-27: the ticket of a running fell. Each run has its own number, so two runs that share a chunk keep their own tickets. */
    private val FELL_TICKET: TicketType<Long> = TicketType.create("cobblemon_dynamics_fell", Comparator.naturalOrder(), TICKET_TICKS)

    private val runs = AtomicLong()
    private val warnings = OnceLogger()

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
        memo.wait.forget()
        val params = LumberjackParams.of(ctx.role.params)
        // CD-LUM-12, CD-LUM-13, CD-LUM-29: every question in this tick, before any block changes. A refusal, or a tree that gets no
        // plan, changes nothing and waits.
        val fell = fell(ctx.level, ctx.target, params)
        if (fell == null || !FellPlan.allowed(fell.plan, ctx.mayChange, ctx.mayBreak)) {
            memo.clock.refuse(ctx.now, ctx.stalled)
            return
        }
        val run = FellRun(fell.plan, params.blocksPerTick, tickets(ctx.level))
        memo.run = run
        // CD-LUM-30: the item frames and paintings come off first, silently, and their items join the drops.
        for (entity in fell.hanging) {
            if (entity.isRemoved) continue
            memo.loot += dropsOf(entity)
            entity.discard()
        }
        if (run.step { remove(ctx.level, it, memo.loot) }) end(ctx, memo, run)
    }

    /** CD-LUM-15: a fell that has started completes, also when the work stops or the claim ends during it. */
    override fun onStop(ctx: WorkContext) = complete(ctx)

    override fun onUnclaim(ctx: WorkContext) = complete(ctx)

    /** The fell of the tree on [base], with its shell, or null when no tree within the role's limits stands there. GameTests read it. */
    fun plan(level: ServerLevel, base: BlockPos, params: LumberjackParams): FellPlan? = fell(level, base, params)?.plan

    /** A fell, and the item frames and paintings that it takes (CD-LUM-30). */
    private class Fell(val plan: FellPlan, val hanging: List<HangingEntity>)

    private fun fell(level: ServerLevel, base: BlockPos, params: LumberjackParams): Fell? {
        val reader = TreeFactsReader(level)
        val tree = TreeRules.treeOn(reader::facts, base, params.maxLogs, params.maxLeaves) ?: return null
        // CD-LUM-29: a block of another mod whose check throws plans no fell of this tree, and never turns the role off.
        val failed: (Throwable) -> Unit = { e ->
            warnings.warn("shell:${e.javaClass.name}") { "The Lumberjack plans no fell at $base, because a block's check fails: $e" }
        }
        val shell = ShellRule.shell(tree, { pos -> standing(level, pos) }, failed) { pos, gone -> stands(level, tree, gone, pos) }
            ?: return null
        val hanging = hangingOn(level, tree.cells + shell)
        val cells = hanging.map { it.pos to BuiltInRegistries.ENTITY_TYPE.getKey(it.type) }
        return Fell(FellPlan.of(tree, shell, cells) { reader.facts(it).blockId }, hanging)
    }

    /**
     * CD-LUM-09: whether the block at [pos] stands once the tree's cells and [gone] are empty. An ascending rail also needs its raised
     * side, which vanilla checks only on a neighbour's update, when it drops the rail where it lies.
     */
    private fun stands(level: ServerLevel, tree: Tree, gone: Set<BlockPos>, pos: BlockPos): Boolean {
        val state = TreeFactsReader.stateOf(level, pos) ?: return true
        val view = HollowLevel(level) { it in tree.cells || it in gone }
        if (!state.canSurvive(view, pos)) return false
        val rail = state.block as? BaseRailBlock ?: return true
        val side = raisedSide(state.getValue(rail.shapeProperty)) ?: return true
        return BaseRailBlock.canSupportRigidBlock(view, pos.relative(side))
    }

    private fun raisedSide(shape: RailShape): Direction? = when (shape) {
        RailShape.ASCENDING_EAST -> Direction.EAST
        RailShape.ASCENDING_WEST -> Direction.WEST
        RailShape.ASCENDING_NORTH -> Direction.NORTH
        RailShape.ASCENDING_SOUTH -> Direction.SOUTH
        else -> null
    }

    /** CD-LUM-30: the item frames, glow item frames and paintings that hang on a cell of [hollow]. */
    private fun hangingOn(level: ServerLevel, hollow: Set<BlockPos>): List<HangingEntity> {
        if (hollow.isEmpty()) return emptyList()
        val box = AABB(
            hollow.minOf { it.x }.toDouble(),
            hollow.minOf { it.y }.toDouble(),
            hollow.minOf { it.z }.toDouble(),
            hollow.maxOf { it.x } + 1.0,
            hollow.maxOf { it.y } + 1.0,
            hollow.maxOf { it.z } + 1.0,
        ).inflate(1.0)
        return level.getEntitiesOfClass(HangingEntity::class.java, box) { it is ItemFrame || it is Painting }
            .filter { entity -> HangingRule.supports(entity.boundingBox, entity.direction).any { it in hollow } }
    }

    /** What a hanging entity drops when it breaks: the frame and its item, or the painting. */
    private fun dropsOf(entity: HangingEntity): List<ItemStack> = when (entity) {
        is GlowItemFrame -> listOf(ItemStack(Items.GLOW_ITEM_FRAME), entity.item.copy())
        is ItemFrame -> listOf(ItemStack(Items.ITEM_FRAME), entity.item.copy())
        else -> listOf(ItemStack(Items.PAINTING))
    }.filterNot { it.isEmpty }

    private fun complete(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: return
        val run = memo.run ?: return
        if (run.finish { remove(ctx.level, it, memo.loot) }) end(ctx, memo, run)
    }

    /** CD-LUM-27: the tickets of one run, which keep each chunk of its plan loaded until its end. */
    private fun tickets(level: ServerLevel): FellRun.Tickets {
        val run = runs.incrementAndGet()
        return FellRun.Tickets(
            add = { chunk -> level.chunkSource.addRegionTicket(FELL_TICKET, ChunkPos(chunk), 0, run) },
            remove = { chunk -> level.chunkSource.removeRegionTicket(FELL_TICKET, ChunkPos(chunk), 0, run) },
        )
    }

    /** The cells next to [pos] that hold a block, in loaded chunks. */
    private fun standing(level: ServerLevel, pos: BlockPos): List<BlockPos> =
        TreeSearch.faces(pos).filter { TreeFactsReader.stateOf(level, it)?.isAir == false }

    /**
     * CD-LUM-06, CD-LUM-14, CD-LUM-16: the loot of the cell as it stands, then the cell's fluid. A cell whose block changed since the plan
     * stays, and so does a leaf that a player placed there since.
     */
    private fun remove(level: ServerLevel, step: FellStep, loot: MutableList<ItemStack>) {
        val state = TreeFactsReader.stateOf(level, step.pos) ?: return
        if (!FellPlan.stillHolds(step, TreeFactsReader.factsOf(state))) return
        loot += lootOf(level, step, state)
        val flags = if (step.shell) Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE else Block.UPDATE_ALL
        level.setBlock(step.pos, state.fluidState.createLegacyBlock(), flags)
        try {
            state.spawnAfterBreak(level, step.pos, ItemStack.EMPTY, true)
        } catch (e: Exception) {
            warnings.warn("break:${step.blockId}") { "The Lumberjack skips the break effects of ${step.blockId}, which fail: $e" }
        }
    }

    /** CD-LUM-29: a block whose loot fails goes with no loot, so the fell still completes and the role stays on. */
    private fun lootOf(level: ServerLevel, step: FellStep, state: BlockState): List<ItemStack> = try {
        Block.getDrops(state, level, step.pos, level.getBlockEntity(step.pos), null, ItemStack.EMPTY).filterNot { it.isEmpty }
    } catch (e: Exception) {
        warnings.warn("loot:${step.blockId}") { "The Lumberjack drops nothing for ${step.blockId}, because its loot fails: $e" }
        emptyList()
    }

    /**
     * CD-LUM-16 to CD-LUM-18: after the last block, the replant takes its saplings from the drops, and the rest pop toward the spot. The
     * run ends and gives its chunks back also when a drop fails (CD-LUM-27, CD-LUM-29).
     */
    private fun end(ctx: WorkContext, memo: Memo, run: FellRun) {
        val level = ctx.level
        val plan = run.plan
        try {
            val drops = FellPlan.merged(memo.loot.map { it to it.count }, same = ItemStack::isSameItemSameComponents)
            val chosen = ReplantRule.choose(
                drops,
                plan.base,
                plan.twoByTwo,
                isSapling = { saplingOf(it) != null },
                survives = { stack, cell -> canPlant(level, stack, cell) },
                needsFour = { stack -> saplingOf(stack)?.defaultBlockState()?.`is`(TreeFactsReader.NEEDS_FOUR) == true },
            )
            // CD-LUM-17: a claim can change during a fell of several ticks, so the replant asks the use question again.
            val replant = ReplantRule.allowedBy(chosen, ctx.mayChange)
            val sapling = replant?.let { saplingOf(it.item) }?.defaultBlockState()
            if (replant != null && sapling != null) for (cell in replant.cells) level.setBlock(cell, sapling, Block.UPDATE_ALL)
            val taken = listOfNotNull(replant?.takeIf { sapling != null }?.let { it.item to it.cells.size })
            val face = DropFace.toward(plan.base, ctx.spot ?: plan.base)
            for ((stack, count) in FellPlan.merged(
                drops,
                taken,
                ItemStack::isSameItemSameComponents,
            )) {
                pop(level, plan.base, face, stack, count)
            }
        } finally {
            memo.run = null
            memo.loot.clear()
            run.release()
        }
    }

    /** [count] items like [stack], in stacks of its largest size. A stack that fails to pop leaves the others alone (CD-LUM-29). */
    private fun pop(level: ServerLevel, base: BlockPos, face: Direction, stack: ItemStack, count: Int) {
        var left = count
        while (left > 0) {
            val size = minOf(left, stack.maxStackSize.coerceAtLeast(1))
            try {
                Block.popResourceFromFace(level, base, face, stack.copyWithCount(size))
            } catch (e: Exception) {
                warnings.warn("drop:${e.javaClass.name}") { "The Lumberjack loses a drop of ${stack.item}, which fails: $e" }
            }
            left -= size
        }
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
        val wait = TreeWait()
        var run: FellRun? = null
        val loot = ArrayList<ItemStack>()

        /** CD-LUM-28: the tree on the claim's target. A base cell with no log of the tag has none, which costs one read. */
        fun tree(ctx: WorkContext): Tree? {
            val base = TreeFactsReader.stateOf(ctx.level, ctx.target)
            return wait.tree(ctx.now, baseIsLog = base != null && base.`is`(TreeFactsReader.TARGETS)) {
                val params = LumberjackParams.of(ctx.role.params)
                TreeRules.treeOn(TreeFactsReader(ctx.level)::facts, ctx.target, params.maxLogs, params.maxLeaves)
            }
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
