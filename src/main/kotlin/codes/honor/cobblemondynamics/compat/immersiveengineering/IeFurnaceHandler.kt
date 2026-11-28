package codes.honor.cobblemondynamics.compat.immersiveengineering

import blusunrize.immersiveengineering.api.crafting.AlloyRecipe
import blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe
import blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE
import blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockState
import blusunrize.immersiveengineering.common.blocks.multiblocks.logic.AdvBlastFurnaceLogic
import blusunrize.immersiveengineering.common.blocks.multiblocks.logic.AlloySmelterLogic
import blusunrize.immersiveengineering.common.blocks.multiblocks.logic.BlastFurnaceLogic
import blusunrize.immersiveengineering.common.blocks.multiblocks.logic.FurnaceHandler
import codes.honor.cobblemondynamics.compat.immersiveengineering.IeFurnaceRule.Kind
import codes.honor.cobblemondynamics.compat.immersiveengineering.IeFurnaceRule.Slot
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker on the alloy kiln and the crude and improved blast furnaces of Immersive Engineering (CD-FUEL-01, CD-FUEL-02). It
 * reaches each furnace's state through IE's multiblock API and writes the burn time through the state's public view, with no mixin.
 */
object IeFurnaceHandler : TargetHandler {
    /** The output slots of IE's furnace states: the blast furnaces' result and slag, and the kiln's result. */
    private const val BLAST_OUTPUT = 2
    private const val BLAST_SLAG = 3
    private const val KILN_OUTPUT = 3

    /**
     * The two preheater places of an improved blast furnace, in the multiblock's own frame, as IE's private
     * `AdvBlastFurnaceLogic.HEATER_OFFSETS` holds them. IE's `getProcessSpeed` takes the preheaters' energy, so the handler never
     * calls it and counts the preheaters at these places.
     */
    private val PREHEATERS = listOf(BlockPos(-1, 0, 1), BlockPos(3, 0, 1))

    override val id: ResourceLocation = id("ie_furnace")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    /**
     * CD-FUEL-02: an idle worker claims a furnace only while the furnace has demand for that worker, so a worker below the level of the
     * blast furnaces never holds one that a stronger worker could fuel. A worker whose furnace has nothing to smelt moves on.
     */
    override val releasesWhenDone: Boolean = true

    private class Furnace(val kind: Kind, val state: IMultiblockState, val context: IMultiblockContext<*>, val master: BlockPos)

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        furnaceOf(blockEntity) != null

    /** CD-FUEL-03: any block of the multiblock maps to its master, for the scan and the leash alike. */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos =
        IeFurnaceRule.canonical(pos, furnaceOf(level.getBlockEntity(pos))?.master)

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        pos == canonical || furnaceOf(level.getBlockEntity(pos))?.master == canonical

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        furnaceOf(level.getBlockEntity(canonical))?.master == canonical

    /** CD-FUEL-01, CD-FUEL-02: the worker's level admits the furnace, and its recipe can run. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val furnace = furnaceOf(ctx.level.getBlockEntity(ctx.target)) ?: return false
        val minLevel = IeFurnaceParams.of(ctx.role.params).blastFurnaceMinLevel
        return IeFurnaceRule.admits(furnace.kind, ctx.pokemon.level, minLevel) && recipeFits(furnace)
    }

    override fun minLevelFor(ctx: WorkContext): Int {
        val furnace = furnaceOf(ctx.level.getBlockEntity(ctx.target)) ?: return 1
        if (furnace.kind == Kind.ALLOY_KILN || !recipeFits(furnace)) return 1
        return IeFurnaceParams.of(ctx.role.params).blastFurnaceMinLevel
    }

    /**
     * Keeps `BURN_TIME` and `LAST_BURN_TIME` at the furnace's highest process speed + 1, so the flame gauge stays full and IE never
     * takes a fuel item (CD-FUEL-13). A furnace that still burns fuel of its own keeps its own values. IE sets the furnace's active
     * state itself.
     */
    override fun applyWork(ctx: WorkContext) {
        if (!hasDemand(ctx)) return
        val furnace = furnaceOf(ctx.level.getBlockEntity(ctx.target)) ?: return
        val view = viewOf(furnace.state) ?: return
        val value = IeFurnaceRule.burnValue(furnace.kind, preheaters(furnace))
        ctx.memo.value = value
        if (view.get(FurnaceHandler.StateView.BURN_TIME) >= value) return
        view.set(FurnaceHandler.StateView.BURN_TIME, value)
        view.set(FurnaceHandler.StateView.LAST_BURN_TIME, value)
        furnace.context.markMasterDirty()
    }

    /** CD-FUEL-14: the furnace goes back to its own fuel within 2 ticks, also at the speed of its preheaters. */
    override fun onStop(ctx: WorkContext) {
        val written = ctx.memo.value as? Int ?: return
        if (!ctx.level.isLoaded(ctx.target)) return
        val view = furnaceOf(ctx.level.getBlockEntity(ctx.target))?.let { viewOf(it.state) } ?: return
        val burn = view.get(FurnaceHandler.StateView.BURN_TIME)
        val after = IeFurnaceRule.afterStop(burn, written)
        if (after != burn) view.set(FurnaceHandler.StateView.BURN_TIME, after)
    }

    /** The furnace of a master or a dummy, or null for any other block entity and for a dummy whose master is not loaded. */
    private fun furnaceOf(blockEntity: BlockEntity?): Furnace? {
        val helper = (blockEntity as? IMultiblockBE<*>)?.helper ?: return null
        val multiblock = helper.multiblock
        val kind = IeFurnaceRule.kindOf(multiblock.id()) ?: return null
        val context = helper.context ?: return null
        val master = context.level.getBlockEntity(multiblock.masterPosInMB())?.blockPos ?: return null
        val state = helper.state ?: return null
        return Furnace(kind, state, context, master)
    }

    private fun viewOf(state: IMultiblockState): ContainerData? = when (state) {
        is AlloySmelterLogic.State -> state.stateView
        is BlastFurnaceLogic.State -> state.stateView
        is AdvBlastFurnaceLogic.State -> state.stateView
        else -> null
    }

    /** CD-FUEL-01: IE's own recipe for the inputs, and room for each of its outputs by IE's private output check. */
    private fun recipeFits(furnace: Furnace): Boolean {
        val environment = furnace.state as? FurnaceHandler.IFurnaceEnvironment<*> ?: return false
        val outputs = when (val recipe = environment.recipeForInput) {
            is BlastFurnaceRecipe -> listOf(BLAST_OUTPUT to recipe.output.get(), BLAST_SLAG to recipe.slag.get())
            is AlloyRecipe -> listOf(KILN_OUTPUT to recipe.output.get())
            else -> return false
        }
        val inventory = environment.inventory
        return IeFurnaceRule.fits(
            outputs.map { (slot, result) ->
                val held = inventory.getStackInSlot(slot)
                Slot(held.isEmpty, ItemStack.isSameItem(held, result), held.count, result.count, inventory.getSlotLimit(slot))
            },
        )
    }

    private fun preheaters(furnace: Furnace): Int {
        val state = furnace.state as? AdvBlastFurnaceLogic.State ?: return 0
        return PREHEATERS.count { state.getPreheater(furnace.context.level, it) != null }
    }
}
