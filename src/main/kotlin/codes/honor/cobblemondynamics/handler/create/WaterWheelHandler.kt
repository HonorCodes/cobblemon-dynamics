package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelStructuralBlock
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Wheel Driver's target: a Create water wheel or large water wheel (CD-WHL-01). A driven wheel gets its worker's flow through
 * [VirtualFlow], so Create turns it at its own speed and capacity (CD-WHL-02, CD-BAL-04).
 */
object WaterWheelHandler : TargetHandler {
    /** R43: the handler reads a wheel's real flow at most this often. Create recomputes it on its own every 60 ticks. */
    private const val FLOW_CHECK_TICKS = 20L

    override val id: ResourceLocation = id("water_wheel")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    /** The controller holds the block entity. A ring block of a large wheel matches through its controller. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean {
        if (blockEntity is WaterWheelBlockEntity) return true
        val controller = ringMaster(level, pos, state) ?: return false
        return level.getBlockEntity(controller) is WaterWheelBlockEntity
    }

    /** CD-CLM-04: every ring block of a large wheel maps to its controller, for the scan and the leash alike. */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = ringMaster(level, pos, level.getBlockState(pos)) ?: pos

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        pos == canonical || ringMaster(level, pos, level.getBlockState(pos)) == canonical

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is WaterWheelBlockEntity

    /** CD-WHL-03: demand exactly while no real fluid turns the wheel. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val wheel = wheelAt(ctx) ?: return false
        val drive = DriveBehaviour.of(wheel) ?: return false
        if (ctx.now >= drive.nextFlowCheck) {
            drive.nextFlowCheck = ctx.now + FLOW_CHECK_TICKS
            wheel.determineAndApplyFlowScore()
        }
        return (wheel as? RealFlowAccess)?.`cobblemon_dynamics$realFlowScore`() == 0
    }

    override fun onStart(ctx: WorkContext) {
        val wheel = wheelAt(ctx) ?: return
        DriveBehaviour.of(wheel)?.driven = true
        wheel.determineAndApplyFlowScore()
    }

    /** A wheel whose drive was reset while its worker Works, for example by a new block entity, turns again on the next tick. */
    override fun applyWork(ctx: WorkContext) {
        val wheel = wheelAt(ctx) ?: return
        if (DriveBehaviour.of(wheel)?.driven == false) onStart(ctx)
    }

    /**
     * CD-WHL-08: the wheel stops in the same tick. A wheel in a chunk that no longer ticks, as during a chunk unload, keeps the
     * flow that it saves, and Create recomputes it with no drive on its next load.
     */
    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        val wheel = wheelAt(ctx) ?: return
        DriveBehaviour.of(wheel)?.driven = false
        if (ctx.level.shouldTickBlocksAt(ctx.target)) wheel.determineAndApplyFlowScore()
    }

    /** CD-WHL-05: only a driven wheel flips, and its recompute turns it the other way at once. */
    override fun flip(level: ServerLevel, canonical: BlockPos): Boolean {
        val wheel = level.getBlockEntity(canonical) as? WaterWheelBlockEntity ?: return false
        val drive = DriveBehaviour.of(wheel)?.takeIf { it.driven } ?: return false
        drive.flip()
        wheel.determineAndApplyFlowScore()
        return true
    }

    private fun wheelAt(ctx: WorkContext): WaterWheelBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? WaterWheelBlockEntity

    /** The controller of the large wheel whose ring holds the structural block at [pos], or null for any other block. */
    private fun ringMaster(level: BlockGetter, pos: BlockPos, state: BlockState): BlockPos? {
        val block = state.block as? WaterWheelStructuralBlock ?: return null
        return if (block.stillValid(level, pos, state, false)) WaterWheelStructuralBlock.getMaster(level, pos, state) else null
    }
}
