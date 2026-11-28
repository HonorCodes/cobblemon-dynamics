package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.TargetHandler
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import kotlin.math.max

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
    private val surfaceCache = Long2DoubleOpenHashMap().apply { defaultReturnValue(Double.NaN) }
    private val headroom = max(SurfaceGrid.DEFAULT_HEADROOM, bodyHeight)

    override fun isStandable(x: Int, y: Int, z: Int): Boolean = BlockPos.asLong(x, y, z) !in taken && canStand(x, y, z)

    override fun canStand(x: Int, y: Int, z: Int): Boolean = supportTopY(x, y, z) != null

    override fun supportTopY(x: Int, y: Int, z: Int): Double? {
        val packed = BlockPos.asLong(x, y, z)
        val cached = surfaceCache.get(packed)
        if (!cached.isNaN()) return if (cached == NO_SURFACE) null else cached
        val resolved = SurfaceGrid.supportTopY(level, x, y, z, headroom)
        surfaceCache.put(packed, resolved ?: NO_SURFACE)
        return resolved
    }

    override fun blocksSight(x: Int, y: Int, z: Int): Boolean {
        if (!level.isLoaded(cursor.set(x, y, z)) || level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty) return false
        return !handler.isPartOfTarget(level, canonical, cursor.immutable())
    }

    override fun clipSight(x: Int, y: Int, z: Int, from: Vec3, to: Vec3): Boolean {
        if (!level.isLoaded(cursor.set(x, y, z))) return false
        val shape = level.getBlockState(cursor).getCollisionShape(level, cursor)
        if (shape.isEmpty) return false
        val pos = cursor.immutable()
        if (handler.isPartOfTarget(level, canonical, pos)) return false
        return shape.clip(from, to, pos) != null
    }

    /** No block, the target's own included, has a collision shape inside the body ([WorkerBody]). */
    override fun fitsBody(x: Int, y: Int, z: Int): Boolean {
        val topY = supportTopY(x, y, z) ?: return false
        if (topY == y.toDouble() && WorkerBody.fitsInCell(bodyWidth, bodyHeight)) return true
        val width = if (bodyWidth > 0.0) bodyWidth else DEFAULT_BODY_WIDTH
        val height = if (bodyHeight > 0.0) bodyHeight else SurfaceGrid.DEFAULT_HEADROOM
        return WorkerBody.boxes(BlockPos(x, y, z), width, height, topY).all { level.noBlockCollision(null, it) }
    }

    private companion object {
        const val NO_SURFACE = Double.NEGATIVE_INFINITY
        const val DEFAULT_BODY_WIDTH = 0.6
    }
}
