package codes.honor.cobblemondynamics.compat.irons_spellbooks

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Brewer on the Alchemist Cauldron of Iron's Spells 'n Spellbooks: a Poison-type makes each running cook time take twice its steps
 * (CD-MCH-07). The cauldron's mixin does the steps in the cauldron's own tick, and this handler grants them on each tick of work. Only
 * the cauldron takes `CauldronBrewing`, from that mixin.
 */
object AlchemistCauldronHandler : TargetHandler {
    override val id: ResourceLocation = id("alchemist_cauldron")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is CauldronBrewing

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is CauldronBrewing

    /** CD-MCH-07: only while a cook time runs. */
    override fun hasDemand(ctx: WorkContext): Boolean = cauldronAt(ctx)?.`cobblemon_dynamics$cooking`() ?: false

    /** The grant holds through the cauldron's next tick, which comes after this one. */
    override fun applyWork(ctx: WorkContext) {
        cauldronAt(ctx)?.`cobblemon_dynamics$grant`(ctx.now + 1, CauldronParams.of(ctx.role.params).extraStepsPerTick)
    }

    /** In a chunk that is gone, the grant lapses by itself. */
    override fun onStop(ctx: WorkContext) {
        if (ctx.level.isLoaded(ctx.target)) cauldronAt(ctx)?.`cobblemon_dynamics$grant`(Long.MIN_VALUE, 0)
    }

    private fun cauldronAt(ctx: WorkContext): CauldronBrewing? = ctx.level.getBlockEntity(ctx.target) as? CauldronBrewing
}
