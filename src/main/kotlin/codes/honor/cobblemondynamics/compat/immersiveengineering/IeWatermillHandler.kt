package codes.honor.cobblemondynamics.compat.immersiveengineering

import blusunrize.immersiveengineering.api.energy.IRotationAcceptor
import blusunrize.immersiveengineering.common.blocks.wooden.WatermillBlockEntity
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Wheel Driver on the IE watermill (CD-KIN-01). The hook in `mixin/compat/immersiveengineering` gives a driven dry wheel the
 * role file's torque, and IE turns the wheel and its Kinetic Dynamo by it. The server owns a watermill's speed and sends it with the
 * wheel, so clients need no drive.
 */
object IeWatermillHandler : TargetHandler {
    /** R43: the handler makes IE compute a wheel's torque at most this often. IE does it on a neighbour change and every 1,024 ticks. */
    private const val TORQUE_CHECK_TICKS = 20L

    override val id: ResourceLocation = id("ie_watermill")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        masterOf(blockEntity) != null

    /** CD-CLM-04: every block of the ring maps to the master at the axle, for the scan and the leash alike. */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos =
        IeWatermillRule.canonical(pos, masterOf(level.getBlockEntity(pos))?.blockPos)

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        pos == canonical || masterOf(level.getBlockEntity(pos))?.blockPos == canonical

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        (level.getBlockEntity(canonical) as? WatermillBlockEntity)?.isDummy == false

    /**
     * CD-KIN-03: demand exactly while IE finds no torque of real water on the wheel, nothing blocks its paddles, and a rotation
     * acceptor in its row takes the power.
     */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val wheel = wheelAt(ctx) ?: return false
        if (wheel.isBlocked) return false
        val drive = wheel.getData(IeAttachments.WATERMILL_DRIVE.get())
        if (ctx.now >= drive.nextCheck) {
            drive.nextCheck = ctx.now + TORQUE_CHECK_TICKS
            drive.powered = powered(ctx.level, wheel)
            // The hook records IE's own torque on each call.
            wheel.ifScaledTorque
        }
        return IeWatermillRule.hasDemand(drive.realTorque, drive.powered)
    }

    /** CD-KIN-04: IE recomputes the wheel's torque and speed on its next tick. */
    override fun onStart(ctx: WorkContext) {
        val wheel = wheelAt(ctx) ?: return
        wheel.getData(IeAttachments.WATERMILL_DRIVE.get()).drive(ctx.now, WatermillParams.of(ctx.role.params).virtualTorque)
        wheel.setShouldUpdate()
    }

    /**
     * CD-KIN-02: the worker renews its drive on every Working tick. A wheel whose drive was lost while its worker Works, for example
     * by a new block entity, turns again on the next tick.
     */
    override fun applyWork(ctx: WorkContext) {
        val wheel = wheelAt(ctx) ?: return
        val drive = wheel.getExistingDataOrNull(IeAttachments.WATERMILL_DRIVE.get())
        if (drive != null && drive.holds(ctx.now)) drive.renew(ctx.now) else onStart(ctx)
    }

    override fun onStop(ctx: WorkContext) = release(ctx)

    /** Every end of the claim ends the drive, also after a handler that threw, which skips [onStop]. */
    override fun onUnclaim(ctx: WorkContext) = release(ctx)

    /** CD-KIN-04, CD-WHL-05: only a driven wheel flips. Its direction is saved with the wheel, and IE turns it the other way on its next tick. */
    override fun flip(level: ServerLevel, canonical: BlockPos): Boolean {
        val wheel = level.getBlockEntity(canonical) as? WatermillBlockEntity ?: return false
        val drive = wheel.getExistingDataOrNull(IeAttachments.WATERMILL_DRIVE.get())?.takeIf { it.driven } ?: return false
        drive.flip()
        wheel.setChanged()
        wheel.setShouldUpdate()
        return true
    }

    /** The wheel loses its virtual torque at IE's next tick. A wheel in a chunk that no longer ticks keeps nothing: drives are never saved. */
    private fun release(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        val wheel = wheelAt(ctx) ?: return
        if (wheel.getExistingDataOrNull(IeAttachments.WATERMILL_DRIVE.get())?.release() == true) wheel.setShouldUpdate()
    }

    /** CD-KIN-03: a rotation acceptor on either side of the wheel, within its row of 3 wheels. */
    private fun powered(level: ServerLevel, wheel: WatermillBlockEntity): Boolean {
        val facing = wheel.facing
        return listOf(facing, facing.opposite).any { side ->
            IeWatermillRule.reachesAcceptor { distance -> cellAt(level, wheel.blockPos.relative(side, distance), side) }
        }
    }

    /** IE's own reads of `handleUpdate`: the capability on the face toward the wheel, and an unblocked watermill. */
    private fun cellAt(level: ServerLevel, pos: BlockPos, side: Direction): RowCell {
        if (!level.isLoaded(pos)) return RowCell.OTHER
        if (level.getCapability(IRotationAcceptor.CAPABILITY, pos, side.opposite) != null) return RowCell.ROTATION_ACCEPTOR
        val wheel = level.getBlockEntity(pos) as? WatermillBlockEntity ?: return RowCell.OTHER
        return if (wheel.isBlocked) RowCell.OTHER else RowCell.WHEEL
    }

    private fun wheelAt(ctx: WorkContext): WatermillBlockEntity? =
        (ctx.level.getBlockEntity(ctx.target) as? WatermillBlockEntity)?.takeIf { !it.isDummy }

    /** The master of the ring that holds [blockEntity], or null for any other block entity and for a dummy whose master is not loaded. */
    private fun masterOf(blockEntity: BlockEntity?): WatermillBlockEntity? =
        (blockEntity as? WatermillBlockEntity)?.master() as? WatermillBlockEntity
}
