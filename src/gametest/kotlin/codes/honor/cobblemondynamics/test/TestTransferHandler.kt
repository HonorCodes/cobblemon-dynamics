package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The handler of the test-only role `cobblemon_dynamics:test_transfer`, which stands in for Telekinesis until Phase 2: a
 * source-and-destination binding between two containers. It only binds, and does no work.
 */
object TestTransferHandler : TargetHandler {
    override val id: ResourceLocation = id("test_transfer")
    override val scanKind: ScanKind = ScanKind.NONE
    override val bindingShape: BindingShape = BindingShape.SOURCE_AND_DESTINATION

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is Container

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is Container

    override fun hasDemand(ctx: WorkContext): Boolean = false

    override fun applyWork(ctx: WorkContext) {}
}
