package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** A handler with an id only. Unit tests never have a world, so every world call fails. */
class FakeHandler(override val id: ResourceLocation) : TargetHandler {
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean = error("not used")

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = error("not used")

    override fun hasDemand(ctx: WorkContext): Boolean = error("not used")

    override fun applyWork(ctx: WorkContext): Unit = error("not used")
}
