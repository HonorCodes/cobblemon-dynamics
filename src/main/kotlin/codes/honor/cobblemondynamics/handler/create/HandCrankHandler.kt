package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Cranker's target: a Create hand crank (CD-CRK-01). The worker turns it as a player does, through Create's own `turn`, so the
 * crank runs at Create's speed and capacity (CD-CRK-03, CD-BAL-04).
 */
object HandCrankHandler : TargetHandler {
    override val id: ResourceLocation = id("hand_crank")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        crankIn(blockEntity) != null

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = crankIn(level.getBlockEntity(canonical)) != null

    /** CD-CRK-07: a crank always counts as busy, with or without a network behind it. */
    override fun hasDemand(ctx: WorkContext): Boolean = true

    override fun onStart(ctx: WorkContext) {
        val drive = crankAt(ctx)?.let { DriveBehaviour.of(it) } ?: return
        drive.driven = true
        drive.drivenSince = ctx.now
    }

    /** CD-CRK-02: a turn every 5 ticks from the start of the drive. A drive that was reset, by a new block entity, starts again. */
    override fun applyWork(ctx: WorkContext) {
        val crank = crankAt(ctx) ?: return
        val drive = DriveBehaviour.of(crank) ?: return
        if (!drive.driven) onStart(ctx)
        if (CrankClock.shouldTurn(drive.drivenSince, ctx.now)) turn(crank, drive)
    }

    /** Create stops the crank itself 10 ticks after the last turn, as it does when a player lets go. */
    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        crankAt(ctx)?.let { DriveBehaviour.of(it) }?.driven = false
    }

    /** CD-CRK-06: only a driven crank flips, and the reversed turn applies at once. */
    override fun flip(level: ServerLevel, canonical: BlockPos): Boolean {
        val crank = crankIn(level.getBlockEntity(canonical)) ?: return false
        val drive = DriveBehaviour.of(crank)?.takeIf { it.driven } ?: return false
        drive.flip()
        turn(crank, drive)
        return true
    }

    /** CD-CRK-04: a valve handle is a hand crank to Create, but it turns by a set angle only. */
    private fun crankIn(blockEntity: BlockEntity?): HandCrankBlockEntity? =
        (blockEntity as? HandCrankBlockEntity)?.takeUnless { it is ValveHandleBlockEntity }

    private fun crankAt(ctx: WorkContext): HandCrankBlockEntity? = crankIn(ctx.level.getBlockEntity(ctx.target))

    /**
     * Create gives `turn(false)` +32 RPM to a crank whose `FACING` points along a positive axis and −32 RPM along a negative one
     * (`getGeneratedSpeed` through `convertToDirection`), so `back` folds the facing in: the speed's sign is the stored direction
     * on every crank (CD-CRK-05).
     */
    private fun turn(crank: HandCrankBlockEntity, drive: DriveBehaviour) {
        val facing = crank.blockState.getValue(DirectionalKineticBlock.FACING)
        crank.turn(drive.direction * facing.axisDirection.step < 0)
    }
}
