package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity
import net.minecraft.world.level.block.state.BlockState

/** The Brewer's target: a brewing stand, whose running brew the worker makes 2× as fast (CD-BRW-01). It never writes the fuel. */
object BrewingSpeedHandler : TargetHandler {
    override val id: ResourceLocation = id("brewing_speed")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is BrewingStandBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is BrewingStandBlockEntity

    /** CD-BRW-03: only while a brew runs. */
    override fun hasDemand(ctx: WorkContext): Boolean = (standAt(ctx)?.brewTime ?: 0) > 0

    /** CD-BRW-02, CD-BRW-04: one extra step of the brew per tick, after the stand's own step. */
    override fun applyWork(ctx: WorkContext) {
        val stand = standAt(ctx) ?: return
        val brewTime = BrewingRules.brewTimeWhileWorking(stand.brewTime)
        if (brewTime == stand.brewTime) return
        stand.brewTime = brewTime
        stand.setChanged()
    }

    private fun standAt(ctx: WorkContext): BrewingStandBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? BrewingStandBlockEntity
}
