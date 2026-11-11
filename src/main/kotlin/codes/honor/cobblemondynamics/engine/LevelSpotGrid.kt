package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.TargetHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel

/**
 * The [SpotGrid] of a level, for one target and one worker [bodyWidth] wide and [bodyHeight] high. A cell in an unloaded chunk is
 * neither standable nor in the way. [taken] holds the spots of other workers, as `BlockPos.asLong()`, so that two workers never
 * share one.
 */
class LevelSpotGrid(
    private val level: ServerLevel,
    private val handler: TargetHandler,
    private val canonical: BlockPos,
    private val taken: Set<Long> = emptySet(),
    private val bodyWidth: Double = 0.0,
    private val bodyHeight: Double = 0.0,
) : SpotGrid {
    private val cursor = BlockPos.MutableBlockPos()

    override fun isStandable(x: Int, y: Int, z: Int): Boolean = BlockPos.asLong(x, y, z) !in taken && canStand(x, y, z)

    override fun canStand(x: Int, y: Int, z: Int): Boolean {
        if (!level.isLoaded(cursor.set(x, y, z))) return false
        val below = cursor.set(x, y - 1, z)
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false
        return isEmpty(x, y, z) && isEmpty(x, y + 1, z)
    }

    override fun blocksSight(x: Int, y: Int, z: Int): Boolean {
        if (!level.isLoaded(cursor.set(x, y, z)) || level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty) return false
        return !handler.isPartOfTarget(level, canonical, cursor.immutable())
    }

    /** No block, the target's own included, has a collision shape inside the body ([WorkerBody]). */
    override fun fitsBody(x: Int, y: Int, z: Int): Boolean = WorkerBody.fitsInCell(bodyWidth, bodyHeight) ||
        WorkerBody.boxes(BlockPos(x, y, z), bodyWidth, bodyHeight).all { level.noBlockCollision(null, it) }

    private fun isEmpty(x: Int, y: Int, z: Int): Boolean = level.getBlockState(cursor.set(x, y, z)).getCollisionShape(level, cursor).isEmpty
}
