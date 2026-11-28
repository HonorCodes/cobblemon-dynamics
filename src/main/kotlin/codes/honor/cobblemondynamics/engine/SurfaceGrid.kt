package codes.honor.cobblemondynamics.engine

import com.cobblemon.mod.common.block.CoinPouchBlock
import com.cobblemon.mod.common.block.DecorativeItemBlock
import com.cobblemon.mod.common.block.GalaricaWreathBlock
import com.cobblemon.mod.common.block.PlaqueBlock
import com.cobblemon.mod.common.block.PokeSnackBlock
import com.cobblemon.mod.common.block.StackableItemBlock
import com.cobblemon.mod.common.block.WallAttachedStackableItemBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CarpetBlock
import net.minecraft.world.level.block.DirtPathBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.MudBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.SoulSandBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.pathfinder.NodeEvaluator
import kotlin.math.floor

data class SurfaceCell(
    val loaded: Boolean = true,
    val isAir: Boolean = true,
    val isSolid: Boolean = false,
    val sturdyTop: Boolean = false,
    val collisionMinY: Double = Double.POSITIVE_INFINITY,
    val collisionMaxY: Double = Double.NEGATIVE_INFINITY,
    val hasFluid: Boolean = false,
    val hazard: Boolean = false,
    val partialFloor: Boolean = false,
    val thinSurface: Boolean = false,
) {
    val hasCollision: Boolean get() = collisionMaxY > Double.NEGATIVE_INFINITY

    val supportsThinAbove: Boolean
        get() = loaded && !hazard && !hasFluid && (sturdyTop || (partialFloor && collisionMaxY >= MIN_SUPPORT_HEIGHT))

    companion object {
        const val MIN_SUPPORT_HEIGHT = 0.875
        val AIR = SurfaceCell()
        val UNLOADED = SurfaceCell(loaded = false)
    }
}

object SurfaceGrid {
    const val DEFAULT_HEADROOM = 2.0
    private const val MAX_THIN_HEIGHT = 0.5
    private const val CLEARANCE_EPSILON = 1e-3

    fun floorTopY(x: Int, y: Int, z: Int, cellAt: (Int, Int, Int) -> SurfaceCell): Double? {
        val here = cellAt(x, y, z)
        if (!here.loaded || here.hasFluid || here.hazard) return null
        val below = cellAt(x, y - 1, z)
        if (!below.loaded || below.hazard) return null
        if (here.hasCollision) {
            if (!here.thinSurface || here.collisionMaxY > MAX_THIN_HEIGHT || !below.supportsThinAbove) return null
            return y + here.collisionMaxY
        }
        if (!here.isAir && here.isSolid) return null
        if (below.thinSurface && below.hasCollision && below.collisionMaxY <= MAX_THIN_HEIGHT && cellAt(x, y - 2, z).supportsThinAbove) {
            return null
        }
        if (below.sturdyTop) {
            val top = if (below.hasCollision) below.collisionMaxY.coerceIn(MAX_THIN_HEIGHT, 1.0) else 1.0
            if (top < 1.0 && below.hasFluid) return null
            return (y - 1) + top
        }
        if (!below.hasFluid && below.partialFloor && below.hasCollision && below.collisionMaxY in MAX_THIN_HEIGHT..1.0) {
            return (y - 1) + below.collisionMaxY
        }
        return null
    }

    fun supportTopY(x: Int, y: Int, z: Int, headroom: Double = DEFAULT_HEADROOM, cellAt: (Int, Int, Int) -> SurfaceCell): Double? {
        val topY = floorTopY(x, y, z, cellAt) ?: return null
        if (headroom <= 0.0) return topY
        val minClearY = topY + CLEARANCE_EPSILON
        val maxClearY = topY + headroom - CLEARANCE_EPSILON
        val topCellY = floor(maxClearY).toInt()
        for (cy in (y + 1)..topCellY) {
            val above = cellAt(x, cy, z)
            if (!above.loaded || above.hasFluid || above.hazard) return null
            if (!above.isAir && above.isSolid && !above.hasCollision) return null
            if (above.hasCollision && cy + above.collisionMinY < maxClearY && cy + above.collisionMaxY > minClearY) return null
        }
        return topY
    }

    fun supportTopY(level: Level, x: Int, y: Int, z: Int, headroom: Double = DEFAULT_HEADROOM): Double? {
        val cursor = BlockPos.MutableBlockPos()
        return supportTopY(x, y, z, headroom) { cx, cy, cz ->
            if (!level.isLoaded(cursor.set(cx, cy, cz))) SurfaceCell.UNLOADED else cellOf(level, cursor, level.getBlockState(cursor))
        }
    }

    fun supportTopY(level: Level, pos: BlockPos, headroom: Double = DEFAULT_HEADROOM): Double? =
        supportTopY(level, pos.x, pos.y, pos.z, headroom)

    fun floorTopY(level: Level, pos: BlockPos): Double? {
        val cursor = BlockPos.MutableBlockPos()
        return floorTopY(pos.x, pos.y, pos.z) { cx, cy, cz ->
            if (!level.isLoaded(cursor.set(cx, cy, cz))) SurfaceCell.UNLOADED else cellOf(level, cursor, level.getBlockState(cursor))
        }
    }

    fun cellOf(level: Level, pos: BlockPos, state: BlockState): SurfaceCell {
        if (state.isAir) return SurfaceCell.AIR
        val hasFluid = !state.fluidState.isEmpty
        val shape = state.getCollisionShape(level, pos)
        val empty = shape.isEmpty
        val minY = if (empty) Double.POSITIVE_INFINITY else shape.min(Direction.Axis.Y)
        val maxY = if (empty) Double.NEGATIVE_INFINITY else shape.max(Direction.Axis.Y)
        val block = state.block
        val pathType = state.getBlockPathType(level, pos, null)
        val hazard = NodeEvaluator.isBurningBlock(state) ||
            state.`is`(BlockTags.FIRE) ||
            state.`is`(BlockTags.CAMPFIRES) ||
            state.`is`(Blocks.MAGMA_BLOCK) ||
            state.`is`(Blocks.CACTUS) ||
            state.`is`(Blocks.SWEET_BERRY_BUSH) ||
            state.`is`(Blocks.WITHER_ROSE) ||
            state.`is`(Blocks.POINTED_DRIPSTONE) ||
            state.`is`(Blocks.POWDER_SNOW) ||
            state.`is`(Blocks.HONEY_BLOCK) ||
            (pathType != null && !Unstick.isSafeFoot(pathType))
        val fenceLike = state.`is`(BlockTags.FENCES) || state.`is`(BlockTags.WALLS) || block is FenceGateBlock
        val sturdyTop = !fenceLike && state.isFaceSturdy(level, pos, Direction.UP)
        val placedLeaf = (block is LeavesBlock || state.`is`(BlockTags.LEAVES)) &&
            state.getOptionalValue(LeavesBlock.PERSISTENT).orElse(true)
        val partialFloor = !fenceLike && !empty && (
            block is FarmBlock ||
                block is DirtPathBlock ||
                block is MudBlock ||
                block is SoulSandBlock ||
                placedLeaf ||
                (block is SlabBlock && maxY == MAX_THIN_HEIGHT) ||
                (block is SnowLayerBlock && maxY > MAX_THIN_HEIGHT)
            )
        val cobblemonPlaceable = block is DecorativeItemBlock ||
            block is StackableItemBlock ||
            block is WallAttachedStackableItemBlock ||
            block is CoinPouchBlock ||
            block is PlaqueBlock ||
            block is GalaricaWreathBlock ||
            block is PokeSnackBlock
        val thinSurface = !fenceLike && !empty && maxY <= MAX_THIN_HEIGHT && (
            block is CarpetBlock ||
                state.`is`(BlockTags.WOOL_CARPETS) ||
                state.`is`(Blocks.MOSS_CARPET) ||
                block is SnowLayerBlock ||
                block is SlabBlock ||
                cobblemonPlaceable
            )
        return SurfaceCell(
            loaded = true,
            isAir = false,
            isSolid = state.isSolid,
            sturdyTop = sturdyTop,
            collisionMinY = minY,
            collisionMaxY = maxY,
            hasFluid = hasFluid,
            hazard = hazard,
            partialFloor = partialFloor,
            thinSurface = thinSurface,
        )
    }
}
